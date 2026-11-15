import { expect, test } from '@playwright/test';
import { API, cleanup, newPlayer } from './helpers';

test.afterEach(cleanup);

test('[A1] home page loads with logo and Play, no console errors', async ({ browser }) => {
  const p = await newPlayer(browser);
  await expect(p.page).toHaveTitle(/SketchTrench/);
  await expect(p.page.getByRole('heading', { level: 1 })).toHaveText('SketchTrench');
  await expect(p.page.getByRole('button', { name: 'Play', exact: true })).toBeVisible();
  // (the lobby polls Open games every 5 s, so "network idle" never happens: wait for content)
  await expect(p.page.getByRole('heading', { name: /Open games/ })).toBeVisible({ timeout: 90_000 });
  await p.page.waitForTimeout(1_500);
  expect(p.errors, p.errors.join('\n')).toEqual([]);
});

test('[A2] backend is healthy and the lobby does not show a server error', async ({ browser, request }) => {
  const res = await request.get(`${API}/actuator/health`);
  expect(res.ok()).toBeTruthy();
  expect((await res.json()).status).toBe('UP');
  const p = await newPlayer(browser);
  await expect(p.page.getByRole('heading', { name: /Open games/ })).toBeVisible({ timeout: 90_000 });
  await expect(p.page.getByText("Can't reach the game server")).toHaveCount(0);
});

test('[A3] favicon and link-preview image are served', async ({ request }) => {
  for (const path of ['/favicon.svg', '/og-image.png']) {
    const res = await request.get(path);
    expect(res.status(), path).toBe(200);
    expect((await res.body()).length, path).toBeGreaterThan(100);
  }
});
