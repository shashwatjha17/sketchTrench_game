# Backend

Spring Boot 3.5.x game server for SketchTrench. Everything (sessions, rooms, games) is in-memory on a single instance — no database required.

## Stack

- Java 21
- Maven
- Spring Web, Security, WebSocket (STOMP over SockJS)
- Lombok, DevTools

## Run

```bash
./mvnw spring-boot:run
```

Listens on `:8080` (`PORT` env to override). Set `CORS_ALLOWED_ORIGINS` to the comma-separated origins your frontend is served from (default `http://localhost:5173,http://localhost:3000`).

## Build

```bash
./mvnw clean package
```
