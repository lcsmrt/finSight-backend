# finSight Backend

Spring Boot REST API for personal finance management. Built as a learning project to explore backend development with Java.

## Tech Stack

- **Java 17** + **Spring Boot 3.5** — application framework
- **Spring Security** + **JWT** (jjwt) — authentication and authorization
- **Spring Data JPA** + **Hibernate** — data access layer
- **PostgreSQL** — relational database
- **SpringDoc / OpenAPI 3** — API documentation (Swagger UI)
- **Docker** — containerized deployment

> **Schema management:** there is no migration tool yet — the schema is created/updated by Hibernate `spring.jpa.hibernate.ddl-auto=update` at startup. New columns (e.g. `series_id`) are added automatically on boot. Adopting Flyway/Liquibase is tracked in the roadmap (`../.specs/project/ROADMAP.md`, milestone M3).

## Features

- JWT-based authentication (login, profile)
- User registration and profile management
- Financial transaction CRUD with filtering, sorting, and pagination
- Transaction categories with spending limits
- Nubank CSV import
- **Recurring & installment transaction series** — register one commitment (bounded start–end) and the API generates the individual monthly transactions; delete the whole series in one call
- Dashboard summary (monthly trends, category breakdown)

## Project Structure

```
src/main/java/com/lcs/finsight/
  config/           Spring and OpenAPI configuration
  controllers/      REST controllers
  dtos/
    request/        Request DTOs with validation annotations
    response/       Response DTOs
  exceptions/       Domain exceptions and global exception handler
  models/           JPA entities + enums
  repositories/     Spring Data repositories with JPA Specifications
  security/         JWT filter, UserDetails service
  services/         Business logic (incl. RecurringTransactionGenerator)
  specifications/   JPA Specifications for dynamic filtering
  utils/            Shared utilities (date validation, API route constants)
```

## Getting Started

### Prerequisites

- **Java 17** (JDK)
- A reachable **PostgreSQL** database. This project does **not** bundle a local database — point it at your own Postgres instance (e.g. a managed DB reached over an SSH tunnel). The `docker-compose.yml` here builds and runs the **API container** (host networking) for deployment; it does not start a database.

### Configure environment

The app loads variables from a dotenv file in this directory (or you can export them in your shell). Copy the template and fill it in:

```bash
cp .env.example .env
```

Required variables (see `.env.example`):

```
SPRING_DATASOURCE_URL=jdbc:postgresql://HOST:PORT/DATABASE?ssl=true&sslmode=require
SPRING_DATASOURCE_USERNAME=your_db_user
SPRING_DATASOURCE_PASSWORD=your_db_password
JWT_SECRET_KEY=generate_with__openssl_rand_-base64_64
# Optional: SERVER_PORT (default 3000), JWT_EXPIRATION_MS (default 86400000)
```

### Run locally

```bash
./mvnw spring-boot:run
```

The API starts on **http://localhost:3000** (configurable via `SERVER_PORT`).

### API documentation

Swagger UI: **http://localhost:3000/swagger-ui.html**

Recurring/installment endpoints (base `/api/finsight/financial-transaction`):

- `POST /series` — create a series. Body: `{ type, amount, description, categoryId?, mode: "INSTALLMENT" | "RECURRING", startDate, parcelsNumber?, interval?: "MONTHLY", endDate? }`. `amount` is per occurrence; `INSTALLMENT` needs `parcelsNumber`, `RECURRING` needs `interval` + `endDate`. Returns `{ seriesId, count, occurrences[] }`.
- `DELETE /series/{seriesId}` — delete all occurrences of a series.

### Tests

```bash
./mvnw test                                         # full suite — NOTE: the @SpringBootTest context test needs a reachable DB + env vars
./mvnw test -Dtest=RecurringTransactionGeneratorTest   # pure unit test for series generation — needs NO database
```

## Spec-Driven Development

Planning artifacts (specs, design, tasks, codebase map) live in [`../.specs/`](../.specs). See `../.specs/features/recurring-transactions/` for this feature's spec → design → tasks.

## Frontend

See [finSight Frontend](../finsight-frontend) for the React SPA that consumes this API.
