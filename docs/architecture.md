# Architecture

Smart Market Watchlist is a modular monolith. The frontend and backend are separate applications in one repository. The backend is one Spring Boot process with domain packages, not a set of microservices.

```
frontend (Next.js)
        |
backend (Spring Boot)
  common          shared API types, including the health endpoint
  marketdata      quotes and history through MarketDataProvider
  user            reserved
  watchlist       reserved
  portfolio       reserved
        |
MarketDataProvider
  MockMarketDataProvider          synthetic fixtures, implemented
  LicensedMarketDataProvider      future, not implemented
```

`MarketDataService` is the only market-data entry point for the rest of the application. It depends on the `MarketDataProvider` interface. The mock implementation is a Spring component today, so it is injected automatically. A later licensed provider can replace it without changing the service, the domain packages, or the frontend.

PostgreSQL connection settings live in `backend/src/main/resources/application.yml` and come from environment variables. No schema is created yet. Hibernate does not update the database (`ddl-auto: none`). The connection pool is allowed to start while PostgreSQL is down, because persistence is not in use yet.

Redis is defined in Docker Compose for later caching. The backend does not connect to Redis yet.

Authentication, change detection, portfolio analytics, AI, Kafka, and cloud deployment are outside this foundation.
