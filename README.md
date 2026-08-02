# SketchTrench

A modern real-time multiplayer drawing-and-guessing game inspired by Skribbl.io, built on a Java-first stack. Players create or join rooms, take turns sketching randomly assigned words, and race to guess each other's drawings for points, XP, and ELO.

## Stack

- **Backend** — Java 21, Spring Boot 3.5 (Web, Data JPA, Security, Validation), STOMP over SockJS WebSocket, PostgreSQL 17 (Flyway migrations), Redis (rate limiting, fail-open), Lombok.
- **Frontend** — React 18, TypeScript, Vite, Tailwind CSS, @stomp/stompjs + sockjs-client.
- **Ops** — Docker Compose for the full stack, GitHub Actions CI.

## Project structure

```
├── backend/     # Spring Boot game server (REST + WebSocket)
├── frontend/    # React SPA
├── docs/        # Architecture + game rules
├── postman/     # Postman collection
└── docker-compose.yml
```

## Quick start

### Option A — everything in Docker

```bash
cp .env.example .env        # fill in JWT_SECRET for anything but local dev
docker compose up -d --build
# Frontend:  http://localhost:8081
# Backend:   http://localhost:8080  (Swagger UI at /swagger-ui.html)
```

### Option B — local dev

Prereqs: Java 21, Node 20, PostgreSQL 17 (or `docker compose up -d postgres redis`).

```bash
cp .env.example .env
cd backend && ./mvnw spring-boot:run     # :8080
cd frontend && npm install && npm run dev # :5173, proxies /api and /ws to backend
```

Register two users in the UI (or hit `/api/auth/register`), then create a room in the lobby.

## Tests

```bash
cd backend && ./mvnw test
```

Unit tests cover game logic (normalization, word matching, chat filter) and progression (XP/levels, ELO). Integration tests (Testcontainers + real Postgres) run automatically when Docker is available and skip otherwise.

## Docs

- [Architecture & API](docs/README.md)

## License

MIT — see [LICENSE](LICENSE).
