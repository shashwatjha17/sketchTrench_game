# Architecture

## Overview

```
Browser (React SPA) ── REST /api/* ──▶ Spring Boot backend (in-memory)
       │
       └────── SockJS + STOMP /ws/* ──▶ backend STOMP broker
```

- **REST** handles guest sessions, reconnect, rooms, word picking, and game state queries.
- **WebSocket** carries the live game: room events, drawing strokes, chat, round/score state.
- **In-memory STOMP broker** (`/topic/...`, `/queue/user`) — one game server, no broker dependency.

There is no persistence. Sessions are identified by `X-Player-Id` (REST header) or the STOMP
CONNECT `X-Player-Id` header; a reconnect token in the player's browser lets them rejoin a
disconnect. Everything is lost when the server restarts.

## Guest flow

| Piece | What it does |
|-------|--------------|
| `guest` | `GuestService` (session/reconnect/room/game engine), `GuestController` (REST), `GuestMessageHandler` (STOMP), `GuestChannelInterceptor` (identity), `PlayerSessionManager`/`RoomManager`/`WebSocketSessionManager` (in-memory state), `WordPool` (static words). |
| `config` | Permissive `SecurityConfig` (CORS only), `WebSocketConfig` (STOMP endpoints), `SchedulerConfig` (game timer pool). |
| `exception` | `ApiException` + `GlobalExceptionHandler` for uniform error JSON. |

## Game rules

- Rooms hold 2–8 players. Each round one player is the **drawer**; the rest guess.
- Drawer picks a word from 3 options; auto-picks the first after 20s.
- First correct guess earns the most points; later guessers earn fewer; the drawer earns a bonus when all guessers get it.
- Timer per round (`drawingTimeSec`, default 80s). Rounds rotate until `totalRounds` complete.
- A disconnected player gets 60s to reconnect before being removed and the round reassigned.

## Frontend

- `src/api.ts` — typed REST client.
- `src/ws.ts` — shared STOMP connection (one socket, playerId in CONNECT headers).
- `src/guest.tsx` — session create/resume over localStorage.
- `src/pages/*` — Lobby, Room, Game.
- `src/components/Canvas.tsx` — shared blackboard (strokes over STOMP) or local doodle pad.

## Deployment

- `docker compose up -d --build` builds both images; nginx serves the SPA and proxies `/api` + `/ws` to the backend.
- Backend config is environment-driven (`CORS_ALLOWED_ORIGINS`, `PORT`).
- CI (GitHub Actions) compiles and tests the backend and builds the frontend on every push/PR.
