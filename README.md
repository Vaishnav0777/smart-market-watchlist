# Smart Market Watchlist

Smart Market Watchlist is an Indian-market watchlist. A person can keep the instruments they follow, come back later, and see what observably changed since they last checked.

It is not a stock-prediction system. It does not provide live prices, trading signals, or investment advice.

## Current architecture

The system is a modular monolith: one Next.js frontend and one Spring Boot backend in the same repository. The backend is a single process split into domain packages. It is not a microservice system.

```
frontend/
backend/
  com.smartwatch
    common         auditing, errors, health
    marketdata     instruments, quotes, MarketDataProvider, observations
    user           registration, login, JWT access tokens, refresh sessions
    watchlist      watchlists, items, check cursor, change API
    change         meaningful-change detection
    portfolio      stored portfolios and positions, read API
infrastructure/    Docker Compose for PostgreSQL and Redis
docs/
```

Market data enters through the `MarketDataProvider` interface:

```
MarketDataProvider
    |
    +-- MockMarketDataProvider          synthetic fixtures, implemented
    +-- LicensedMarketDataProvider      future, not implemented
```

`MarketDataService` depends on the interface. A licensed provider can replace the mock later without changing the detector or the frontend. PostgreSQL stores users, instruments, watchlists, observations, checks, portfolios, and positions. Flyway creates the schema and Hibernate validates it. Redis is defined for local development and the backend does not connect to it yet. See [docs/architecture.md](docs/architecture.md).

The frontend calls the backend REST API. It does not keep a copy of the instrument catalog and it does not run a second change detector. Change text and severity come from the backend.

## Technology stack

| Area | Choice |
| --- | --- |
| Frontend | Next.js 16, App Router, TypeScript, Tailwind CSS, ESLint |
| Backend | Java 21, Spring Boot 4.1.1, Maven |
| API | REST, JWT access tokens, rotating refresh tokens |
| Database | PostgreSQL 17, Flyway migrations, Spring Data JPA |
| Cache | Redis 8, local service only |
| Local services | Docker Compose |

## Synthetic-data policy

Development uses only synthetic market data.

`MockMarketDataProvider` holds a small frozen catalog for RELIANCE, TCS, INFY, HDFCBANK, ICICIBANK, SBIN, ITC, BHARTIARTL, LT, and MARUTI. The numbers are invented fixtures. They share one fixed sample timestamp, `2024-06-03T15:30:00+05:30`. Every quote is marked `synthetic: true`.

These values must never be presented as real market data. They are not current or historical NSE or BSE prices.

The project does not scrape Groww, Zerodha, NSE, or BSE. It does not call unofficial or undocumented market APIs, and it does not ship a copied market dataset.

A real Indian market-data feed needs a provider the project is allowed to use. Until that provider is chosen, the only implementation is the mock, and it stays the default for tests. A later provider implements the same `MarketDataProvider` interface. `UPSTOX` is a reserved source name. This project does not call Upstox, and it does not read an Upstox API key.

Each quote has a last traded price, previous close, open, high, low, and cumulative volume. `marketTimestamp` is the provider's quote time. `observedAt` is when this backend received the quote. Those times are stored separately. `quality` says whether the provider marked the quote real-time, delayed, end-of-day, stale, or unknown. The mock catalog is end-of-day sample data, source `MOCK`, and `synthetic: true`. Change detection compares stored observations with the quotes that interface returns, so it does not depend on the mock catalog.

A check is a row on `watchlist_checks`, saved by `POST /api/v1/watchlists/{id}/checks` for the signed-in owner. It records the quotes and membership at that moment. `GET /api/v1/watchlists/{id}/changes` reads the latest check and does not move it. With no check, the response is a first check and an empty change list. After a check, a move is reported only when it passes `app.changes`: 2% price, 2x volume, 1% gap, or 2% from the session open. The same frozen quote on the next check is "no material change." The text describes what already happened. It does not predict a price or recommend a trade.

## Current functionality

Implemented:

- Registration, login, logout, short-lived JWT access tokens, and rotating refresh sessions
- Refresh token in an HttpOnly `SameSite=Lax` cookie (`smw_refresh`). It is not returned in JSON and is not stored in `sessionStorage`
- Authenticated watchlists: create, rename, delete, add, remove, and reorder items
- Synthetic quotes through `MockMarketDataProvider`
- Instrument search that resolves provider listings to persisted identities
- Market observations and meaningful-change detection since an explicit check. The first check stores no invented history. A later check reports only moves past the configured thresholds, or says there is no material change.
- Portfolio create, rename, delete, and position add, edit, and remove for the signed-in owner
- Next.js screens for sign-in, dashboard, watchlists, search, instrument detail, portfolio, and account
- Flyway schema migrations and integration tests
- Docker Compose services for PostgreSQL and Redis

Not implemented yet:

- A licensed or real-time market-data provider
- Redis client usage
- Broker integration
- AI assistant or price prediction
- Kafka
- AWS, Terraform, and CI/CD

## Prerequisites

- Java 21
- Node.js 20 or newer, with npm
- Docker, with Docker Compose

The backend builds with the Maven Wrapper (`./mvnw`). A global Maven install is not required.

## Environment variables

Copy the example file and edit the local placeholders. `.env` is gitignored. Do not commit it.

```bash
cp .env.example .env
```

| Variable | Used by | Purpose |
| --- | --- | --- |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT` | Docker Compose | Local PostgreSQL |
| `REDIS_PASSWORD`, `REDIS_PORT` | Docker Compose | Local Redis, unused by the app |
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | Backend | JDBC connection. Keep the password equal to `POSTGRES_PASSWORD` |
| `JWT_SECRET` | Backend | HMAC key for access tokens. At least 32 bytes. No default |
| `JWT_ACCESS_TOKEN_TTL`, `JWT_REFRESH_TOKEN_TTL` | Backend | Optional token lifetimes |
| `SERVER_PORT` | Backend | API port, default `8080` |
| `CORS_ALLOWED_ORIGINS` | Backend | Browser origin allowed to call the API directly |
| `COOKIE_SECURE` | Backend | Set `true` when the site is served over HTTPS. Leave `false` for local HTTP |
| `NEXT_PUBLIC_API_BASE_URL` | Frontend | Optional absolute API origin. Leave empty to use the Next.js proxy |
| `API_PROXY_TARGET` | Frontend server | Proxy destination for `/api`, default `http://localhost:8080` |

`NEXT_PUBLIC_*` is visible in the browser. Do not put `JWT_SECRET`, database passwords, or tokens there.

Leave `NEXT_PUBLIC_API_BASE_URL` empty in local development. The browser then calls `/api` on the Next.js server, which proxies to the backend, so the refresh cookie stays on the same site. The access token lives only in memory and is replaced by `POST /api/v1/auth/refresh` after a reload. Market prices are still synthetic. The application does not predict prices and is not connected to a live market-data provider.

Spring Boot does not read `.env` on its own. Export it before starting the backend:

```bash
set -a
source .env
set +a
```

## Start PostgreSQL and Redis

From the repository root:

```bash
docker compose up -d
docker compose ps
```

Stop them:

```bash
docker compose down
```

The API requires PostgreSQL and `JWT_SECRET`. Redis is not used by the application yet.

## Start the backend

```bash
cd backend
./mvnw spring-boot:run
```

The API listens on port 8080 unless `SERVER_PORT` is set.

```bash
curl http://localhost:8080/api/v1/health
```

Expected response:

```json
{"status":"UP"}
```

## Start the frontend

```bash
cd frontend
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000). Sign in, then use the dashboard. With `NEXT_PUBLIC_API_BASE_URL` empty, the browser calls `/api` on the Next.js server, which proxies to `API_PROXY_TARGET`.

## Tests

Backend, from `backend/` (Docker must be running for Testcontainers):

```bash
./mvnw clean verify
```

Frontend, from `frontend/`:

```bash
npm run lint
npm run build
```
