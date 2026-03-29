# finSight Backend

Spring Boot REST API for personal finance management. Built as a learning project to explore backend development with Java.

## Tech Stack

- **Java 21** + **Spring Boot 3** — application framework
- **Spring Security** + **JWT** — authentication and authorization
- **Spring Data JPA** + **Hibernate** — data access layer
- **PostgreSQL** — relational database
- **Flyway** — database migrations
- **SpringDoc / OpenAPI 3** — API documentation (Swagger UI)
- **Docker** — containerized deployment

## Features

- JWT-based authentication (login, profile)
- User registration and profile management
- Financial transaction CRUD with filtering, sorting, and pagination
- Transaction categories with spending limits
- Nubank CSV import
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
  models/           JPA entities
  repositories/     Spring Data repositories with JPA Specifications
  security/         JWT filter, UserDetails service
  services/         Business logic
  specifications/   JPA Specifications for dynamic filtering
  utils/            Shared utilities (date validation, API route constants)
```

## Getting Started

### Prerequisites

- Java 21
- Docker and Docker Compose

### Running locally

```bash
# Start the database
docker-compose up -d

# Run the application
./mvnw spring-boot:run
```

### Environment variables

Copy `.env.example` to `.env` and fill in the required values:

```
DB_URL=jdbc:postgresql://localhost:5432/finsight
DB_USERNAME=postgres
DB_PASSWORD=your_password
JWT_SECRET=your_jwt_secret
```

### API Documentation

Once running, Swagger UI is available at:

```
http://localhost:8080/swagger-ui.html
```

## Frontend

See [finSight Frontend](../finSight-frontend) for the React SPA that consumes this API.
