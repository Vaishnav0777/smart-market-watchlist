# Architecture

Smart Market Watchlist is a modular monolith. The frontend and backend are separate applications in one repository. The backend is one Spring Boot process with domain packages, not a set of microservices.

```
frontend (Next.js)
        |
backend (Spring Boot)
  common          shared persistence base and the health endpoint
  marketdata      instrument identity, quotes, and MarketDataProvider
  user            user identity, without authentication
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

Authentication, change detection, portfolio analytics, AI, Kafka, and cloud deployment are outside this foundation.

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

The synthetic quote catalog in `MockMarketDataProvider` is not copied into PostgreSQL. Instrument rows are created by the application when a user needs them. The migration does not insert demo users or prices.
