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

The Next.js application is the product UI. It calls the existing REST API through one client. The refresh token is an HttpOnly cookie and is not placed in JavaScript storage. The access token stays in memory. Neither is written into source or `NEXT_PUBLIC_*` variables. Reading a watchlist still does not move the check cursor. The dashboard shows the change response from `GET /api/v1/watchlists/{id}/changes` and records a check only when the user chooses `POST /api/v1/watchlists/{id}/checks`.

Instrument search (`GET /api/v1/instruments?q=`) reads the provider catalog and creates a persisted identity only when that listing does not already exist. It does not store prices and does not accept an arbitrary symbol from the client. `GET /api/v1/market/quotes` returns the current provider quotes without writing them. `GET /api/v1/portfolios` returns stored holdings for the signed-in user and attaches a quote when the exchange and symbol match. It does not create portfolios.

PostgreSQL connection settings live in `backend/src/main/resources/application.yml` and come from environment variables. Flyway owns the schema (`backend/src/main/resources/db/migration`). Hibernate validates that mapping (`ddl-auto: validate`) and does not create or alter tables.

Persisted timestamps are `Instant` values, stored as `timestamptz` and written in UTC. Monetary amounts and quantities use `BigDecimal` mapped to `NUMERIC(19,4)`. Scale 4 covers paise and average prices that are not whole paise. Floating-point columns are not used.

Redis is defined in Docker Compose for later caching. The backend does not connect to Redis yet.

Portfolio analytics, AI, Kafka, and cloud deployment are not implemented. Change detection compares stored observations with the latest quote. It does not predict a future price.

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
| Refresh token | 32 random bytes. The JSON body does not include it. | HttpOnly cookie `smw_refresh`. PostgreSQL stores only the SHA-256 hex digest, plus expiry and revocation. | About 7 days, configurable. |

A successful refresh revokes the presented session and issues a new refresh token. Logout revokes the session and can be repeated. A disabled account is rejected on the next protected request even if the access token has not expired yet. That check is one lookup by primary key.

Passwords are hashed with Spring Security's `BCryptPasswordEncoder`. Login uses the same error for an unknown email and a wrong password. Responses never include `passwordHash` or a token digest.

`JWT_SECRET` comes from the environment and must be at least 32 bytes. Access and refresh lifetimes are `JWT_ACCESS_TOKEN_TTL` and `JWT_REFRESH_TOKEN_TTL`. The signing implementation is Spring Security's Nimbus `JwtEncoder` (`spring-security-oauth2-jose`), using HMAC-SHA256. There is no OAuth authorization server.

Public routes are health, register, login, refresh, and logout. `/api/v1/auth/me`, `/api/v1/watchlists`, and every other route require `Authorization: Bearer`. The API does not use server sessions. The refresh cookie is `SameSite=Lax` and is sent only to `/api/v1/auth`, so a cross-site form post does not carry it. Set `COOKIE_SECURE=true` when the site is served over HTTPS.

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

Reading a watchlist does not record that the user checked it. `GET /api/v1/watchlists/{id}` still does not write a quote or move a cursor.

## Market observations and meaningful changes

```
MarketDataProvider  ->  Quote
        |
        |  only when the user checks
        v
market_observations          watchlist_checks
        \                   /        |
         \                 /         +-- watchlist_check_items
          \               /                    (membership + observation)
           v             v
              ChangeDetector
```

`MarketDataService` is still the only read path onto `MarketDataProvider`. `MockMarketDataProvider` returns the frozen synthetic catalog and identifies itself as source `mock`. A later licensed provider implements the same interface, including `source()`, and the detector does not change.

An observation stores the instrument id, source, observed time, price, previous close, open, day high, day low, volume, currency, and session date. It does not copy the company name or the rest of the instrument identity. Rows are written by `POST /api/v1/watchlists/{id}/checks`. Ordinary quote reads are not stored.

`ChangeDetector` compares the latest quote with the observation captured at a cursor. Thresholds live in `app.changes` (`price-move-percent`, `volume-spike-multiple`, `gap-percent`, `intraday-move-percent`, `high-severity-multiple`). A rule that is missing a reference price, volume, or session boundary does not emit a change and does not treat the missing number as zero.

The change types are observable facts: `PRICE_MOVE`, `VOLUME_SPIKE`, `NEW_DAY_HIGH`, `NEW_DAY_LOW`, `GAP_UP`, `GAP_DOWN`, `LARGE_INTRADAY_MOVE`, `WATCHLIST_ADDED`, and `WATCHLIST_REMOVED`. Explanations say what already happened, such as a percent move since the previous observation. They do not say that a price will rise or that a stock should be bought.

Since-last-check lifecycle:

1. `GET /api/v1/watchlists/{id}/changes` uses the latest check, or `?since=` a check id or an ISO-8601 timestamp. It does not create a check. `firstCheck` is true when no checkpoint exists, and the change list stays empty. A later read with no move past the configured thresholds says there is no material change.
2. The response lists structured changes and counts. Market changes and membership changes are separate types. A price move includes the current value, the reference value, the absolute change, and the percent change.
3. `POST /api/v1/watchlists/{id}/checks` stores the current quotes and the current membership, then returns a new cursor.
4. A later read uses that cursor, so the same move is not reported again until the market differs from the new observation.

Membership added or removed is the difference between the current items and the items stored on the cursor. A timestamp cursor can see items added after that time. It cannot see removals, because a deleted membership is no longer a row unless a check snapshot still has it. Another user's watchlist returns the same not-found response as a missing id.

## Frontend

The Next.js app is a client of these HTTP APIs. Pages for the dashboard, watchlists, instrument detail, and portfolio render the JSON the backend returns. Percentages on a quote versus its own previous close are display arithmetic on those two fields. Meaningful-change sentences, severity, and counts come only from the change API.

The refresh token is an HttpOnly `SameSite=Lax` cookie named `smw_refresh`, scoped to `/api/v1/auth`. The JSON auth response contains only the short-lived access token. The browser keeps that access token in memory. Logout revokes the refresh session and clears the cookie. A normal watchlist read does not acknowledge a check. The "Mark as checked" action is the explicit cursor update.
