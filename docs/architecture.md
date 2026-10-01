# Architecture

Smart Market Watchlist is a modular monolith. The frontend and backend are separate applications in one repository. The backend is one Spring Boot process with domain packages, not a set of microservices.

```
frontend (Next.js)
        |
backend (Spring Boot)
  common          shared persistence base and the health endpoint
  marketdata      instrument identity, quotes, and MarketDataProvider
  user            accounts, password hashes, refresh sessions, JWT access tokens
  watchlist       watchlists and watchlist items
  portfolio       portfolios and positions
        |
MarketDataProvider
  MockMarketDataProvider          synthetic fixtures, implemented
  LicensedMarketDataProvider      future, not implemented
```

`MarketDataService` is the only market-data entry point for the rest of the application. It depends on the `MarketDataProvider` interface. The mock implementation is a Spring component today, so it is injected automatically. A later licensed provider can replace it without changing the service, the domain packages, or the frontend.

PostgreSQL connection settings live in `backend/src/main/resources/application.yml` and come from environment variables. Flyway owns the schema (`backend/src/main/resources/db/migration`). Hibernate validates that mapping (`ddl-auto: validate`) and does not create or alter tables.

Persisted timestamps are `Instant` values, stored as `timestamptz` and written in UTC. Monetary amounts and quantities use `BigDecimal` mapped to `NUMERIC(19,4)`. Scale 4 covers paise and average prices that are not whole paise. Floating-point columns are not used.

Redis is defined in Docker Compose for later caching. The backend does not connect to Redis yet.

Change detection, portfolio analytics, AI, Kafka, and cloud deployment are not implemented. The watchlist HTTP API is in place and does not store the observations a later change summary will compare.

## Core Domain Model

```
User
 ├── Watchlists
 │      └── WatchlistItems
 │             └── Instrument
 │
 └── Portfolios
        └── Positions
               └── Instrument
```

A user can own several watchlists and several portfolios. Watchlist names are unique per user, and portfolio names are unique per user. A watchlist cannot contain the same instrument twice. A portfolio cannot contain two positions for the same instrument. The same symbol on two exchanges is two instruments (`NSE` + `RELIANCE` is not `BSE` + `RELIANCE`).

These four types stay separate:

| Type | Meaning |
| --- | --- |
| Instrument | A stable identity: symbol, company, exchange, sector, and type. No price. |
| Quote | A market observation from `MarketDataProvider`. It is not stored on a position. |
| WatchlistItem | A user's interest in an instrument. It is not a holding. |
| Position | A user's quantity and average buy price. The current price comes from market data. |

The synthetic quote catalog in `MockMarketDataProvider` is not copied into PostgreSQL. Instrument rows are identities only. The watchlist API accepts an instrument id that already exists and does not create an instrument from a symbol the client types. Migrations do not insert demo users or prices.

## Authentication

Authentication stays inside the `user` package of the monolith. It is not a separate service.

```
Client
  |  POST /api/v1/auth/register|login
  |  Authorization: Bearer <access token>
  v
AuthController -> AuthService
                    |-- UserService          BCrypt password hash on users.password_hash
                    |-- RefreshTokenService  opaque token, SHA-256 hash on refresh_sessions
                    |-- JwtTokenService      HMAC-SHA256 JWT, about 15 minutes
```

Access tokens and refresh tokens have different jobs:

| Token | What it is | Where it lives | Lifetime |
| --- | --- | --- | --- |
| Access token | Signed JWT. Claims are the user id (`sub`) and `token_type=access`. | Only with the client. The server does not store it. | About 15 minutes, configurable. |
| Refresh token | 32 random bytes, encoded for the client. | PostgreSQL stores only the SHA-256 hex digest, plus expiry and revocation. | About 7 days, configurable. |

A successful refresh revokes the presented session and issues a new refresh token. Logout revokes the session and can be repeated. A disabled account is rejected on the next protected request even if the access token has not expired yet. That check is one lookup by primary key.

Passwords are hashed with Spring Security's `BCryptPasswordEncoder`. Login uses the same error for an unknown email and a wrong password. Responses never include `passwordHash` or a token digest.

`JWT_SECRET` comes from the environment and must be at least 32 bytes. Access and refresh lifetimes are `JWT_ACCESS_TOKEN_TTL` and `JWT_REFRESH_TOKEN_TTL`. The signing implementation is Spring Security's Nimbus `JwtEncoder` (`spring-security-oauth2-jose`), using HMAC-SHA256. There is no OAuth authorization server.

Public routes are health, register, login, refresh, and logout. `/api/v1/auth/me`, `/api/v1/watchlists`, and every other route require `Authorization: Bearer`. The API does not use server sessions. CSRF is disabled because the browser is not granted a cookie session.

## Watchlist API

Watchlist routes live in the `watchlist` package. Controllers return DTOs. The owner is the user id in the access token. The client cannot submit an owner id.

| Route | Behavior |
| --- | --- |
| `POST /api/v1/watchlists` | Creates a named list for the authenticated user. |
| `GET /api/v1/watchlists` | Lists that user's lists, with item counts, ordered by `createdAt` then id. |
| `GET /api/v1/watchlists/{id}` | Returns the list and its items. Each item includes the persisted instrument and, when the provider has one, a separate quote observation. |
| `PATCH /api/v1/watchlists/{id}` | Renames the list. |
| `DELETE /api/v1/watchlists/{id}` | Deletes the items, then the list. The database foreign key stays `ON DELETE RESTRICT`, so the service removes children in the same transaction. |
| `POST /api/v1/watchlists/{id}/items` | Adds an existing instrument. The same instrument cannot appear twice. |
| `DELETE /api/v1/watchlists/{id}/items/{itemId}` | Removes one membership. |
| `PUT /api/v1/watchlists/{id}/items/order` | Replaces display order in one SQL update. The body must contain each item id of that list exactly once. |

A request for another user's watchlist returns the same `404` as a missing id. The response does not say whether the id exists.

`sort_order` is display position, starting at zero. It is not a market rank. Quotes are read through `MarketDataService` when a list is loaded and are not written onto `watchlist_items`. A quote is attached only when its exchange and symbol match the persisted instrument, and `synthetic` stays on the observation.

Reading a watchlist does not record that the user checked it. Membership already has `addedAt` (`created_at`). A later "since last check" feature can store a check cursor on the watchlist, or in its own table, and compare that cursor with membership changes and fresh observations. The read path must not move that cursor, or the explanation of what changed would disappear on every view.
