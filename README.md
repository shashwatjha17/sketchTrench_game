# SketchTrench

A modern real-time multiplayer drawing-and-guessing game inspired by Skribbl.io. Guests pick a nickname, join or create rooms, take turns sketching randomly assigned words, and race to guess each other's drawings for points.

## Stack

- **Backend** — Java 21, Spring Boot 3.5 (Web, Security, WebSocket/STOMP), Lombok. All sessions, rooms, and games live in memory — no database.
- **Frontend** — React 18, TypeScript, Vite, Tailwind CSS, @stomp/stompjs + sockjs-client.
- **Ops** — Docker Compose for the full stack, GitHub Actions CI.

## Project structure

```
├── backend/     # Spring Boot game server (REST + WebSocket)
├── frontend/    # React SPA
├── docs/        # Architecture + game rules
└── docker-compose.yml
```

## Quick start

### Option A — everything in Docker

```bash
docker compose up -d --build
# Frontend:  http://localhost:8081
# Backend:   http://localhost:8080  (Swagger UI at /swagger-ui.html)
```

### Option B — local dev

Prereqs: Java 21, Node 20.

```bash
cd backend && ./mvnw spring-boot:run     # :8080
cd frontend && npm install && npm run dev # :5173, proxies /api and /ws to backend
```

Create a guest profile in the UI, then create a room in the lobby. No accounts required.

## Tests

```bash
cd backend && ./mvnw test
cd frontend && npm run build   # typecheck + production build
```

## Docs

- [Architecture & API](docs/README.md)

## License

MIT — see [LICENSE](LICENSE).
