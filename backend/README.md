# Backend

Spring Boot 3.5.x game server for SketchTrench.

## Stack

- Java 21
- Maven
- Spring Web, Data JPA, Security, Validation
- PostgreSQL
- Lombok, DevTools

## Run

```bash
./mvnw spring-boot:run
```

Set database connection via environment variables or `application.properties`:

- `DATABASE_URL` (default: `jdbc:postgresql://localhost:5432/sketchtrench`)
- `DATABASE_USERNAME` (default: `postgres`)
- `DATABASE_PASSWORD` (default: `postgres`)

## Build

```bash
./mvnw clean package
```
