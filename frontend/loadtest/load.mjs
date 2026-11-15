/**
 * Load test: simulates full rooms of real players against the backend.
 *
 *   node loadtest/load.mjs --players 200 --seconds 30 [--api http://localhost:8080] [--room 4]
 *
 * Each room: 1 drawer + (room-1) guessers. Everyone joins over REST, opens a STOMP
 * WebSocket like the browser does, the host starts a game, the drawer sends ~4 strokes/s
 * (~40 points each) and every guesser chats every 2 s. Receivers timestamp what arrives,
 * so we get end-to-end delivery latency for drawing and chat, plus REST timings.
 * Prints one JSON line of results at the end.
 */
import { Client } from '@stomp/stompjs';

const arg = (k, d) => {
  const i = process.argv.indexOf(`--${k}`);
  return i > 0 ? process.argv[i + 1] : d;
};
const API = arg('api', 'http://localhost:8080');
const PLAYERS = Number(arg('players', 40));
const SECONDS = Number(arg('seconds', 30));
const ROOM = Number(arg('room', 4));
const STROKES_PER_SEC = Number(arg('strokes', 4));
const WS = API.replace(/^http/, 'ws') + '/ws/websocket';

const rest = []; // ms
const restErrors = [];
const strokeLat = [];
const chatLat = [];
let wsFailures = 0;
let strokesSent = 0;
let strokesExpected = 0;
let strokesGot = 0;
let chatsSent = 0;
let chatsExpected = 0;
let chatsGot = 0;

async function call(method, path, playerId, body) {
  const t = performance.now();
  try {
    const res = await fetch(API + '/api/guest' + path, {
      method,
      headers: { 'Content-Type': 'application/json', ...(playerId ? { 'X-Player-Id': playerId } : {}) },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    rest.push(performance.now() - t);
    if (!res.ok) {
      restErrors.push(`${method} ${path.replace(/[0-9a-f-]{8,}/g, ':id')} ${res.status}`);
      return null;
    }
    const text = await res.text();
    return text ? JSON.parse(text) : {};
  } catch (e) {
    restErrors.push(`${method} ${path} ${e.message}`);
    return null;
  }
}

function connect(playerId) {
  return new Promise((resolve) => {
    const client = new Client({
      webSocketFactory: () => new WebSocket(WS),
      connectHeaders: { 'X-Player-Id': playerId },
      reconnectDelay: 0,
      heartbeatIncoming: 0,
      heartbeatOutgoing: 0,
      onConnect: () => resolve(client),
      onWebSocketError: () => {
        wsFailures++;
        resolve(null);
      },
      onStompError: () => {
        wsFailures++;
        resolve(null);
      },
    });
    client.activate();
    setTimeout(() => resolve(null), 20000);
  });
}

const now = () => Date.now();

async function room(idx) {
  const members = [];
  for (let i = 0; i < ROOM; i++) {
    const s = await call('POST', '/session', null, {
      nickname: `load${idx}_${i}`,
      avatarColor: '#ffd23f',
      avatarExpression: 'happy',
      avatarSunglasses: false,
      avatarWig: 'none',
      language: 'en',
    });
    if (!s) return null;
    members.push({ id: s.playerId, name: `load${idx}_${i}` });
  }
  const host = members[0];
  const info = await call('POST', '/rooms', host.id, {
    name: `load ${idx}`, isPrivate: true, maxPlayers: 12, totalRounds: 1, drawingTimeSec: 240, customWords: false,
  });
  if (!info) return null;
  const roomId = info.roomId;
  for (const m of members.slice(1)) await call('POST', `/rooms/${roomId}/join`, m.id);

  for (const m of members) {
    m.client = await connect(m.id);
    if (!m.client) return null;
    m.client.subscribe(`/topic/drawing/${roomId}`, (msg) => {
      const b = JSON.parse(msg.body);
      if (b.sentAt && m !== host) {
        strokeLat.push(now() - b.sentAt);
        strokesGot++;
      }
    });
    m.client.subscribe(`/topic/game/${roomId}`, (msg) => {
      const b = JSON.parse(msg.body);
      const hit = typeof b.text === 'string' && b.text.match(/^lt (\d+)/);
      if (hit && b.username !== m.name) {
        chatLat.push(now() - Number(hit[1]));
        chatsGot++;
      }
    });
  }
  host.client.subscribe('/user/queue/word-options', (msg) => {
    const opts = JSON.parse(msg.body).options;
    host.client.publish({ destination: `/app/guest/game/${roomId}/word-select`, body: JSON.stringify({ wordId: opts[0].id }) });
  });
  return { roomId, host, members };
}

const pts = () => Array.from({ length: 40 }, (_, i) => ({ x: 100 + i * 5 + Math.random() * 3, y: 200 + Math.sin(i / 4) * 40 }));

function pct(arr, p) {
  if (!arr.length) return null;
  const s = [...arr].sort((a, b) => a - b);
  return Math.round(s[Math.min(s.length - 1, Math.floor((p / 100) * s.length))]);
}

const t0 = performance.now();
const rooms = [];
const nRooms = Math.ceil(PLAYERS / ROOM);
// ramp up in batches of 10 rooms so we measure the game, not a thundering herd
for (let i = 0; i < nRooms; i += 10) {
  const batch = await Promise.all(Array.from({ length: Math.min(10, nRooms - i) }, (_, j) => room(i + j)));
  rooms.push(...batch.filter(Boolean));
}
const setupSec = (performance.now() - t0) / 1000;
const okRooms = rooms.length;

for (const r of rooms) await call('POST', `/rooms/${r.roomId}/start`, r.host.id);
await new Promise((res) => setTimeout(res, 1500)); // word gets picked

const timers = [];
const measureFrom = now();
for (const r of rooms) {
  timers.push(
    setInterval(() => {
      r.host.client.publish({
        destination: `/app/guest/drawing/${r.roomId}`,
        body: JSON.stringify({ type: 'path', points: pts(), color: '#1f1d1a', size: 6, sentAt: now() }),
      });
      strokesSent++;
      strokesExpected += r.members.length - 1;
    }, 1000 / STROKES_PER_SEC),
  );
  r.members.slice(1).forEach((m, k) => {
    setTimeout(() => {
      timers.push(
        setInterval(() => {
          m.client.publish({ destination: `/app/guest/chat/${r.roomId}`, body: JSON.stringify({ words: [`lt ${now()} hello`] }) });
          chatsSent++;
          chatsExpected += r.members.length - 1;
        }, 2000),
      );
    }, k * 300);
  });
}
await new Promise((res) => setTimeout(res, SECONDS * 1000));
timers.forEach(clearInterval);
await new Promise((res) => setTimeout(res, 2000)); // let in-flight messages land

// clean up: leave rooms, close sockets
await Promise.all(rooms.flatMap((r) => r.members.map((m) => call('POST', `/rooms/${r.roomId}/leave`, m.id))));
rooms.forEach((r) => r.members.forEach((m) => m.client?.deactivate()));

const errSummary = {};
for (const e of restErrors) errSummary[e] = (errSummary[e] ?? 0) + 1;
console.log(
  JSON.stringify({
    players: PLAYERS,
    connectedPlayers: okRooms * ROOM,
    rooms: okRooms,
    seconds: SECONDS,
    setupSec: Number(setupSec.toFixed(1)),
    wsFailures,
    rest: { n: rest.length, p50: pct(rest, 50), p95: pct(rest, 95), p99: pct(rest, 99), errors: errSummary },
    stroke: {
      sentPerSec: Math.round(strokesSent / ((now() - measureFrom) / 1000)),
      delivered: strokesExpected ? Number((strokesGot / strokesExpected).toFixed(4)) : null,
      p50: pct(strokeLat, 50), p95: pct(strokeLat, 95), p99: pct(strokeLat, 99), max: pct(strokeLat, 100),
    },
    chat: {
      delivered: chatsExpected ? Number((chatsGot / chatsExpected).toFixed(4)) : null,
      p50: pct(chatLat, 50), p95: pct(chatLat, 95), p99: pct(chatLat, 99),
    },
  }),
);
process.exit(0);
