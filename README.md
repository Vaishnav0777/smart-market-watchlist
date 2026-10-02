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
    +-- MockMarketDataProvider          synthetic fixtures, default
    +-- UpstoxMarketDataProvider        Upstox V3 REST quotes, optional
```

`MarketDataService` depends on the interface. The mock stays the provider for local development and tests. Upstox is used only when it is explicitly enabled and an access token is present. PostgreSQL stores users, instruments, watchlists, observations, checks, portfolios, and positions. Flyway creates the schema and Hibernate validates it. Redis is defined for local development and the backend does not connect to it yet. See [docs/architecture.md](docs/architecture.md).

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

The optional real feed is Upstox's current V3 full market-quote REST API, `GET /v3/market-quote/quotes`. It is off unless `UPSTOX_ENABLED=true` and `UPSTOX_ACCESS_TOKEN` is set in the environment. The token is not written into source, `.env.example`, logs, or the frontend. This repository does not grant exchange licensing or redistribution rights. Use an access token your own Upstox app is allowed to use, and do not commit it.

Upstox instruments are addressed by an external key such as `NSE_EQ|INE002A01018`, stored on `instrument_provider_keys` for an instrument we already have. The internal symbol is unchanged. No Upstox key is seeded. A symbol with no stored key has no Upstox quote. WebSocket streaming is not implemented.

Each quote has a last traded price, previous close, open, high, low, and cumulative volume. Three times stay separate. `marketTimestamp` is when the trade represented by the last price occurred. For Upstox that is `last_trade_time`. `observedAt` is when this backend accepted the provider response. The Upstox field `timestamp` is when Upstox generated the snapshot. It is not stored as either of those times. `quality` is `REAL_TIME`, `DELAYED`, `END_OF_DAY`, `STALE`, or `UNKNOWN`. A fresh Upstox snapshot is `REAL_TIME`, because V3 documents it as taken from the exchange at request time and does not mark it delayed. It is `STALE` only when that snapshot `timestamp` is older than `UPSTOX_STALE_AFTER` (default 15 minutes) before `observedAt`. An old last trade with a fresh snapshot stays `REAL_TIME`. The mock catalog is end-of-day sample data, source `MOCK`, and `synthetic: true`. Change detection compares stored observations with the quotes that interface returns, so it does not depend on the mock catalog.

A check is a row on `watchlist_checks`, saved by `POST /api/v1/watchlists/{id}/checks` for the signed-in owner. It records the quotes and membership at that moment. `GET /api/v1/watchlists/{id}/changes` reads the latest check and does not move it. With no check, the response is a first check and an empty change list. After a check, a move is reported only when it passes `app.changes`: 2% price, 2x volume, 1% gap, or 2% from the session open. The same frozen quote on the next check is "no material change." The text describes what already happened. It does not predict a price or recommend a trade.

## Current functionality

Implemented:

- Registration, login, logout, short-lived JWT access tokens, and rotating refresh sessions
- Refresh token in an HttpOnly `SameSite=Lax` cookie (`smw_refresh`). It is not returned in JSON and is not stored in `sessionStorage`
- Authenticated watchlists: create, rename, delete, add, remove, and reorder items
- Synthetic quotes through `MockMarketDataProvider`, which remains the default
- Optional Upstox V3 REST quotes when enabled with an environment access token
- Instrument search that resolves provider listings to persisted identities
- Market observations and meaningful-change detection since an explicit check. The first check stores no invented history. A later check reports only moves past the configured thresholds, or says there is no material change.
- Portfolio create, rename, delete, and position add, edit, and remove for the signed-in owner
- Portfolio analytics for invested amount, value, profit and loss, allocation, and quote quality
- A stateless personal assistant that answers from those stored figures and from watchlist changes. It does not call an external model
- Next.js screens for sign-in, dashboard, watchlists, search, instrument detail, portfolio, assistant, and account
- Flyway schema migrations and integration tests
- Docker Compose services for PostgreSQL and Redis

Not implemented yet:

- Upstox WebSocket streaming, polling, and a seeded instrument-key catalog
- Redis client usage
- Broker integration
- An external language-model provider. `AssistantModel` is ready for one; V1 does not enable it
- Price prediction and trade recommendations
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
| `UPSTOX_ENABLED` | Backend | `false` by default. Set `true` to request Upstox V3 quotes instead of the mock |
| `UPSTOX_BASE_URL` | Backend | Upstox API origin. Default `https://api.upstox.com` |
| `UPSTOX_ACCESS_TOKEN` | Backend | Bearer token for your own Upstox app. Leave empty in examples. Never commit a real token |
| `UPSTOX_STALE_AFTER` | Backend | Age of the Upstox snapshot `timestamp` before a quote is `STALE`. Default `PT15M`. This is not the age of the last trade |
| `NEXT_PUBLIC_API_BASE_URL` | Frontend | Optional absolute API origin. Leave empty to use the Next.js proxy |
| `API_PROXY_TARGET` | Frontend server | Proxy destination for `/api`, default `http://localhost:8080` |

`NEXT_PUBLIC_*` is visible in the browser. Do not put `JWT_SECRET`, database passwords, or tokens there.

Leave `NEXT_PUBLIC_API_BASE_URL` empty in local development. The browser then calls `/api` on the Next.js server, which proxies to the backend, so the refresh cookie stays on the same site. The access token lives only in memory and is replaced by `POST /api/v1/auth/refresh` after a reload. With `UPSTOX_ENABLED` left false, market prices stay synthetic. The application does not predict prices.

To call Upstox locally, export a token only in your shell or gitignored `.env`. Do not put it in source control:

```bash
export UPSTOX_ENABLED=true
export UPSTOX_ACCESS_TOKEN="the token from your Upstox app"
```

Insert one `instrument_provider_keys` row per instrument (`provider = UPSTOX`, `external_instrument_key` from the Upstox instrument master) before expecting a quote. If Upstox is enabled and the token is blank, the API fails to start rather than serving mock prices as if they were live.

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

## Production deployment

PostgreSQL is the only required persistent service. The backend and the frontend are two separate processes. Redis is not used. Leave `UPSTOX_ENABLED=false`. Prices stay on the synthetic mock until an Upstox application is approved. Do not commit `.env` files, database passwords, `JWT_SECRET`, or an Upstox token.

Set these for the backend. `backend/.env.example` lists placeholders.

| Variable | Required | Production note |
| --- | --- | --- |
| `DATABASE_URL` | Yes | JDBC URL of the PostgreSQL instance |
| `DATABASE_USERNAME` | Yes | Database user |
| `DATABASE_PASSWORD` | Yes | Database password |
| `JWT_SECRET` | Yes | At least 32 bytes. Generate a new value. Do not reuse the example |
| `COOKIE_SECURE` | Yes on HTTPS | `true` when the site is served over HTTPS |
| `CORS_ALLOWED_ORIGINS` | Yes | Frontend origin. The Next.js rewrite forwards the browser `Origin`, so this must match the site even when the browser calls `/api` on the frontend |
| `UPSTOX_ENABLED` | Yes | `false` for this deployment |
| `UPSTOX_ACCESS_TOKEN` | No | Leave empty while Upstox is disabled |

Set these for the frontend before `npm run build` or the image build. `API_PROXY_TARGET` is read then and is not sent to the browser.

| Variable | Required | Production note |
| --- | --- | --- |
| `API_PROXY_TARGET` | Yes | Backend origin, for example `http://api:8080` |
| `NEXT_PUBLIC_API_BASE_URL` | No | Leave empty so the browser calls this frontend at `/api` and the refresh cookie stays on that origin |

Build and run the API:

```bash
docker build -t smartwatch-api backend
docker run --rm -p 8080:8080 \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/smartwatch \
  -e DATABASE_USERNAME=smartwatch \
  -e DATABASE_PASSWORD=change-me \
  -e JWT_SECRET=replace-with-at-least-32-bytes-of-random-data \
  -e COOKIE_SECURE=false \
  -e CORS_ALLOWED_ORIGINS=http://localhost:3000 \
  -e UPSTOX_ENABLED=false \
  smartwatch-api
```

Use `COOKIE_SECURE=true` and the real frontend origin when the site is served over HTTPS.

Build and run the frontend. Replace the proxy target with a URL the image can reach at build time:

```bash
docker build -t smartwatch-web --build-arg API_PROXY_TARGET=http://host.docker.internal:8080 frontend
docker run --rm -p 3000:3000 smartwatch-web
```

Without Docker, from the repository after exporting the same variables:

```bash
cd backend && ./mvnw -DskipTests package && java -jar target/smartwatch-*.jar
cd frontend && npm ci && npm run build && npm run start
```

### Production verification

- Register an account and sign in.
- Create a watchlist, open it, and run a meaningful-change check.
- Create a portfolio, add a holding, and open its analytics.
- Ask the assistant a stored-data question, such as total value, and a buy or prediction question, which must be declined.
- Sign out and confirm a personal page requires sign-in again.
- Sign in as a second user and confirm that user's portfolio id returns not found.

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
