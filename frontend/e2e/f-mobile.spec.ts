import { devices, expect, test, type Page } from '@playwright/test';
import { cleanup, newPlayer, pickWord, startGame } from './helpers';

test.afterEach(cleanup);

const noSideScroll = (page: Page) =>
  page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1);

test('[F29] phone layout: no sideways scroll; chat sits under the canvas', async ({ browser }) => {
  const { defaultBrowserType: _ignored, ...iphone } = devices['iPhone 13'];
  const p = await newPlayer(browser, 'phone', iphone);
  await expect(p.page.getByRole('heading', { name: /Open games/ })).toBeVisible({ timeout: 90_000 });
  expect(await noSideScroll(p.page), 'lobby').toBe(true);

  await p.page.getByRole('button', { name: /Practice/ }).click();
  await p.page.waitForURL(/\/rooms\//);
  await expect(p.page.getByRole('button', { name: /Start game/ })).toBeEnabled();
  expect(await noSideScroll(p.page), 'room').toBe(true);

  await startGame(p);
  await pickWord(p);
  expect(await noSideScroll(p.page), 'game').toBe(true);
  // the drawer (worst case: the toolbar sits between board and chat) should see the whole
  // board, the tools and the chat box on one screen below the sticky top bar
  const canvas = (await p.page.locator('canvas').boundingBox())!;
  const input = (await p.page.getByLabel('Chat or guess').boundingBox())!;
  const bar = (await p.page.locator('.sticky').first().boundingBox())!;
  const viewport = p.page.viewportSize()!.height;
  expect(input.y).toBeGreaterThan(canvas.y + canvas.height);
  const needed = input.y + input.height - canvas.y;
  expect(needed, `board→chat needs ${Math.round(needed)}px, screen has ${viewport - bar.height}px`).toBeLessThanOrEqual(
    viewport - bar.height,
  );
});
