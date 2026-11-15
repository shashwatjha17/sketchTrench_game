import { expect, type Browser, type BrowserContext, type Page } from '@playwright/test';

export const BASE_URL = process.env.BASE_URL ?? 'http://localhost:5173';
export const LIVE = !BASE_URL.includes('localhost');
/** Backend origin for direct REST calls (setup shortcuts and cleanup). */
export const API = process.env.API_URL ?? (LIVE ? 'https://sketchtrench-backend.onrender.com' : 'http://localhost:8080');

export interface Player {
  context: BrowserContext;
  page: Page;
  name: string;
  /** console errors seen on this page */
  errors: string[];
}

const all: Player[] = [];

/** Short unique nickname (server limit is 20 chars). */
export const nick = (prefix: string) => `${prefix}${Math.random().toString(36).slice(2, 6)}`;

/** Opens a fresh browser identity on the lobby and (optionally) types a nickname. */
export async function newPlayer(
  browser: Browser,
  prefix = 'e2e',
  opts: Parameters<Browser['newContext']>[0] = {},
): Promise<Player> {
  const context = await browser.newContext(opts);
  const page = await context.newPage();
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error') errors.push(m.text());
  });
  page.on('pageerror', (e) => errors.push(String(e)));
  const p: Player = { context, page, name: nick(prefix), errors };
  all.push(p);
  await page.goto('/');
  await nameInput(page).fill(p.name);
  return p;
}

export const nameInput = (page: Page) => page.locator('input[autocomplete=nickname]');

export async function session(page: Page): Promise<{ playerId: string } | null> {
  return page.evaluate(() => JSON.parse(localStorage.getItem('st_guest') ?? 'null'));
}

export const roomIdFrom = (page: Page) => new URL(page.url()).pathname.split('/')[2];

/** Lobby → "Private room" → room page. Returns the room id. */
export async function createPrivateRoom(p: Player): Promise<string> {
  await p.page.getByRole('button', { name: /Private/ }).click();
  await p.page.waitForURL(/\/rooms\//);
  await expect(p.page.getByRole('heading', { name: /room/ })).toBeVisible();
  return roomIdFrom(p.page);
}

/** Lobby → type the code → Join → room page. */
export async function joinByCode(p: Player, roomId: string) {
  await p.page.getByLabel('Room code').fill(roomId);
  await p.page.getByRole('button', { name: 'Join', exact: true }).click();
  await p.page.waitForURL(new RegExp(`/(rooms|game)/${roomId}`));
}

export async function api(path: string, playerId: string, body?: unknown) {
  const res = await fetch(`${API}/api/guest${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Player-Id': playerId },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`${path} -> ${res.status} ${await res.text()}`);
  return res.status === 200 ? res.json().catch(() => null) : null;
}

/** Host shortcut: short games for tests (the UI only offers 45s+; the API allows 30s). */
export async function quickSettings(host: Player, roomId: string, s: Record<string, unknown>) {
  const me = await session(host.page);
  await api(`/rooms/${roomId}/settings`, me!.playerId, s);
}

export async function startGame(host: Player) {
  await host.page.getByRole('button', { name: /Start game/ }).click();
  await host.page.waitForURL(/\/game\//);
}

/** Drawer picks the first offered word; returns it. */
export async function pickWord(drawer: Player): Promise<string> {
  const modal = drawer.page.getByRole('heading', { name: 'Your turn to draw!' });
  await expect(modal).toBeVisible();
  const btn = drawer.page.locator('.fixed button').first();
  const word = (await btn.textContent())!.trim();
  await btn.click();
  await expect(drawer.page.getByText('draw:')).toBeVisible();
  return word;
}

export async function say(p: Player, text: string) {
  const input = p.page.getByLabel('Chat or guess');
  await input.fill(text);
  await input.press('Enter');
}

export const chat = (p: Player) => p.page.locator('section').filter({ has: p.page.getByRole('heading', { name: 'Chat' }) });

/** Drag a stroke across the drawer's board. */
export async function drawStroke(p: Player) {
  const box = (await p.page.locator('canvas').first().boundingBox())!;
  await p.page.mouse.move(box.x + box.width * 0.2, box.y + box.height * 0.3);
  await p.page.mouse.down();
  for (let i = 1; i <= 10; i++) {
    await p.page.mouse.move(box.x + box.width * (0.2 + i * 0.05), box.y + box.height * (0.3 + i * 0.03));
  }
  await p.page.mouse.up();
}

/** Number of non-white pixels on the first canvas (sampled). */
export async function inkPixels(page: Page): Promise<number> {
  return page.evaluate(() => {
    const c = document.querySelector('canvas')!;
    const d = c.getContext('2d')!.getImageData(0, 0, c.width, c.height).data;
    let n = 0;
    for (let i = 0; i < d.length; i += 16) if (d[i] < 200 && d[i + 1] < 200 && d[i + 2] < 200) n++;
    return n;
  });
}

export function nearMiss(word: string) {
  const i = Math.floor(word.length / 2);
  return word.slice(0, i) + word.slice(i + 1);
}

/** Leave rooms over the API and close every browser this test opened. */
export async function cleanup() {
  const players = all.splice(0);
  for (const p of players) {
    try {
      const s = await session(p.page);
      const roomId = s && (await p.page.evaluate(() => location.pathname.split('/')[2]));
      if (s && roomId) await api(`/rooms/${roomId}/leave`, s.playerId).catch(() => {});
    } catch {
      /* page already gone */
    }
    await p.context.close().catch(() => {});
  }
}
