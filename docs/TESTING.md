# Testing SketchTrench

## What's covered

| Suite | Where | Run |
|---|---|---|
| Backend unit tests (33) | `backend/src/test` | `cd backend && ./mvnw test` |
| End-to-end browser tests (21 tests, 30 cases) | `frontend/e2e` | `cd frontend && npm run e2e` |
| Same, against the live site | `frontend/e2e` | `cd frontend && npm run e2e:live` |
| Load test | `frontend/loadtest/load.mjs` | `cd frontend && npm run load -- --players 200 --seconds 30` |

The browser tests need the app running locally (`npm run dev` in `frontend/`, the backend on `:8080`).
Multiplayer cases open separate browser contexts, so each simulated player has its own identity.
Failures leave a screenshot and a trace in `frontend/test-results/`; `npx playwright show-report e2e-report`
opens the HTML report.

### End-to-end cases

| | Case |
|---|---|
| A1–A3 | Home page loads without console errors · backend healthy · favicon / preview image served |
| B4–B8 | Buttons need a name · offensive names refused · avatar builder · refresh resumes the session · unknown room code |
| C9–C15b | Private room + invite link · public/private listing · settings sync live (read-only for guests) · bots · invite link for new visitors · host hand-over · kick + ban · kicked while offline |
| D16–D27 | Word picker shows at once · blanks + timer · drawing/undo/clear sync · close-guess hint · drawer can't say the word · profanity censored · correct guess · rate limit · letter hints · game over · join a running game · refresh mid-game |
| E28 | Practice bots play along |
| F29 | Phone: no sideways scroll; board, tools and chat input fit on one screen |

### Load test

Simulates rooms of 4 over REST + STOMP WebSockets: the drawer sends ~4 strokes/s (40 points each),
guessers chat every 2 s. It reports REST latency and end-to-end delivery latency for strokes and chat
(time from one player sending to the others receiving). Options: `--api`, `--players`, `--seconds`,
`--room`, `--strokes`.

## Results — 15 Nov 2026

**Local** (MacBook, backend as a single JVM): every message delivered at every step, no failures.

| Players | Rooms | Stroke p50 / p95 / p99 | Chat p50 / p95 / p99 | REST p95 | Peak CPU | Peak memory |
|---:|---:|---|---|---:|---:|---:|
| 40 | 10 | 7 / 15 / 23 ms | 5 / 16 / 23 ms | 45 ms | 0.6 core | 273 MB |
| 100 | 25 | 7 / 17 / 21 ms | 5 / 17 / 23 ms | 24 ms | 0.5 core | 330 MB |
| 200 | 50 | 9 / 21 / 33 ms | 9 / 25 / 31 ms | 49 ms | 0.5 core | 428 MB |
| 400 | 100 | 10 / 24 / 34 ms | 14 / 28 / 41 ms | 67 ms | 1.0 core | 608 MB |
| 800 | 200 | 9 / 36 / 53 ms | 20 / 45 / 119 ms | 155 ms | 2.3 cores | 1,038 MB |

**Live** (Render free plan: 0.1 CPU, 512 MB; measured from a client ~330 ms away by network):

| Players | Stroke p50 / p95 / p99 / max | Notes |
|---:|---|---|
| 20 | 334 / 508 / 733 / 906 ms | Smooth — latency is mostly network distance |
| 40 | 456 / 942 / 2,328 / 4,386 ms | CPU-bound: noticeable lag spikes; test stopped here |

So the free plan comfortably serves **~20 simultaneous players (≈5 rooms)**; beyond ~30 expect lag.
The code itself scales far further (800 players locally), so the limit is the instance size — a paid
Render instance (0.5+ CPU) should handle a few hundred.

**Page load** (Lighthouse, mobile, simulated slow 4G):

| | Performance | Accessibility | Best practices | SEO | First paint |
|---|---:|---:|---:|---:|---:|
| Live site before fixes | 84 | 96 | 82 | 92 | 2.9 s |
| After fixes (local prod build) | 99 | 100 | 96 | 100 | 1.4 s |

## Bugs the tests found (all fixed)

1. **Kicked while offline → endless "reconnecting"** — a room SUBSCRIBE from a non-member got a STOMP
   ERROR, which closes the socket; the client reconnected and resubscribed forever.
2. **Simultaneous leaves crashed (500)** — the room's player map wasn't thread-safe; the same race could
   overfill a room.
3. **Phone layout** — the drawer couldn't see the board, tools and chat input on one screen.
4. **Page load** — render-blocking web fonts, low-contrast grey text, a deprecated `unload` handler from
   `sockjs-client` (also blocked the back/forward cache), missing `robots.txt`.
