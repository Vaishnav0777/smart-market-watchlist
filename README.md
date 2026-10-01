# Smart Market Watchlist

Smart Market Watchlist is an Indian-market market intelligence application. It is a portfolio project for building a production-style system in small, verifiable steps.

It is not a stock-prediction system. It does not provide live prices, trading signals, or investment advice.

## The problem it solves

People who follow a list of instruments need somewhere to keep that list, look up market information, come back later and see what meaningfully changed, and understand why something was flagged. They also need a place to review their own portfolio and ask questions about that data.

This repository is the foundation for that product. Watchlists, change detection, portfolio analytics, and the assistant are not built yet.

## Current architecture

The system is a modular monolith: one Next.js frontend and one Spring Boot backend in the same repository. The backend is a single process split into domain packages. It is not a microservice system.

```
frontend/
backend/
  com.smartwatch
    common         auditing, health endpoint
    marketdata
      entity       Instrument
      model        Quote and historical bars
      provider     MarketDataProvider
      repository
      service
      controller   reserved for later HTTP adapters
    user           User identity, no authentication
    watchlist      Watchlist and WatchlistItem
    portfolio      Portfolio and Position
infrastructure/    reserved for later deployment configuration
docs/
```

Market data enters through the `MarketDataProvider` interface:

```
MarketDataProvider
    |
    +-- MockMarketDataProvider          synthetic fixtures, implemented
    +-- LicensedMarketDataProvider      future, not implemented
```

`MarketDataService` depends on the interface. The rest of the application will call the service. A licensed provider can replace the mock later without changing the service, the other domain packages, or the frontend.

PostgreSQL stores users, instruments, watchlists, and portfolios. Flyway creates the schema and Hibernate validates it. Redis is defined for local development and the backend does not connect to it yet. See [docs/architecture.md](docs/architecture.md).

## Technology stack

| Area | Choice |
| --- | --- |
| Frontend | Next.js 16, App Router, TypeScript, Tailwind CSS, ESLint |
| Backend | Java 21, Spring Boot 4.1.1, Maven |
| API | REST |
| Database | PostgreSQL 17, Flyway migrations, Spring Data JPA |
| Cache | Redis 8, local service only |
| Local services | Docker Compose |

Spring Boot dependencies: Spring Web, Spring Validation, Spring Data JPA, Flyway, the PostgreSQL driver, and Spring Boot Actuator.

Planned later, and not present in this repository: authentication, Kafka, AI, AWS, Terraform, and GitHub Actions.

## Synthetic-data policy

Development uses only synthetic market data.

`MockMarketDataProvider` holds a small frozen catalog for RELIANCE, TCS, INFY, HDFCBANK, ICICIBANK, SBIN, ITC, BHARTIARTL, LT, and MARUTI. The numbers are invented fixtures. They share one fixed sample timestamp, `2024-06-03T15:30:00+05:30`. Every quote and historical bar is marked `synthetic: true`.

These values must never be presented as real market data. They are not current or historical NSE or BSE prices.

The project does not scrape Groww, Zerodha, NSE, or BSE. It does not call unofficial or undocumented market APIs, and it does not ship a copied market dataset.

## Why real market-data integration is deferred

A real Indian market-data feed needs a provider the project is allowed to use. That licensing decision is separate from the application structure. Connecting a provider now would either depend on an unverified source or lock the rest of the code to one vendor.

The provider interface is the seam for that later work. Until a licensed provider is chosen, the only implementation is the mock.

## Current project status

Implemented:

- Next.js landing page
- Spring Boot API with `GET /api/v1/health`
- Market-data port, quote models, and synthetic provider
- Persistent users, instruments, watchlists, watchlist items, portfolios, and positions
- Registration, login, JWT access tokens, and rotating refresh sessions
- Authenticated watchlist API with per-user ownership
- Flyway schema migrations and Spring Data repositories
- Docker Compose services for PostgreSQL and Redis
- Maven Wrapper

Not implemented yet:

- Portfolio HTTP API
- Redis client usage
- Meaningful-change detection
- Market or portfolio analytics
- Real-time updates
- AI assistant
- Kafka
- AWS, Terraform, and CI/CD

## Prerequisites

- Java 21
- Node.js 20 or newer, with npm
- Docker, with Docker Compose

The backend builds with the Maven Wrapper (`./mvnw`). A global Maven install is not required.

## Start PostgreSQL and Redis

From the repository root:

```bash
cp .env.example .env
docker compose up -d
```

`.env` is gitignored. The example file contains local placeholders only. Change `POSTGRES_PASSWORD` and `REDIS_PASSWORD` before use, and keep `DATABASE_PASSWORD` equal to `POSTGRES_PASSWORD`.

Check the services:

```bash
docker compose ps
```

Stop them:

```bash
docker compose down
```

The API requires PostgreSQL and `JWT_SECRET`. Redis is not used by the application yet. Export the variables before starting the backend. Spring Boot does not read `.env` on its own:

```bash
set -a
source .env
set +a
```

## Start the backend

```bash
cd backend
./mvnw spring-boot:run
```

The API listens on port 8080 unless `SERVER_PORT` is set.

Health check:

```bash
curl http://localhost:8080/api/v1/health
```

Expected response:

```json
{"status":"UP"}
```

Run the tests:

```bash
cd backend
./mvnw test
```

Integration tests start PostgreSQL with Testcontainers, using the same `postgres:17-alpine` image as Docker Compose. Docker must be running. The tests check unique constraints and decimal persistence against PostgreSQL.

Database passwords and future secrets come from the environment. Do not hardcode them. See `.env.example` and `backend/.env.example`.

## Start the frontend

```bash
cd frontend
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000). The page is a landing screen for this foundation. The product dashboard is not built yet.
