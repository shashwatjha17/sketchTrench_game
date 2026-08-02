# Architecture

## Overview

```
Browser (React SPA) ── REST /api/* ──▶ Spring Boot backend ──▶ PostgreSQL (Flyway)
       │                                                        │
       └────── SockJS + STOMP /ws/* ──▶ backend broker ────────┘   └── Redis (rate limit, fail-open)
```

- **REST** handles auth, users, rooms, leaderboard, game history.
- **WebSocket** carries the live game: room events, drawing strokes, chat, round/score state.
- **In-memory STOMP broker** (`/topic/rooms.{id}`, `/queue/user`) — one game server, no broker dependency.
- **Redis** rate-limits REST and STOMP message traffic; if Redis is down, the limiter fails open.

## Modules

| Module | What it does |
|--------|--------------|
| `auth` | JWT access (15m) + refresh (7d) tokens; refresh tokens persisted in DB with expiry. |
| `user` | Profiles, stats, achievements, game history. |
| `room` | CRUD rooms, join/leave/ready/kick, validation of player count & host permissions. |
| `game` | Round state machine, word pool + selection, timer, guesses (normalized Levenshtein matching), drawing broadcasts, chat + typing, scoring, game end. |
| `progress` | XP/levels, per-game ELO (K=32, ±100 clamp) for ranked mode. |
| `websocket` | SockJS endpoint, STOMP channel interceptor (token auth), broker config, user presence. |

## Game rules

- Rooms hold 2–8 players. Each round one player is **drawer**; the rest guess.
- Drawer picks a word from 3 options; gets a hint (category); auto-skips after 10s → word auto-picked.
- First correct guess earns the most points; later guessers earn fewer; drawer earns a bonus when all guessers get it.
- Timer per round (`drawingTimeSec`, default 60s). Rounds rotate until `settings.rounds` games complete.
- Correct guesses, participation, and wins award XP → levels. Ranked rooms additionally update ELO.

## Frontend

- `src/api.ts` — typed REST client with a token store + single-flight refresh on 401.
- `src/ws.ts` — shared STOMP connection (one socket, token in CONNECT headers).
- `src/pages/*` — Login/Register, Lobby, Room, Game, Leaderboard, Profile.
- `src/components/Canvas.tsx` — pointer-drawing, broadcasts strokes over STOMP.

## Deployment

- `docker compose up -d --build` builds both images; nginx serves the SPA and proxies `/api` + `/ws` to the backend.
- Backend config is fully environment-driven (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, `PORT`) — see `.env.example`.
- CI (GitHub Actions) compiles and tests the backend and builds the frontend on every push/PR.
