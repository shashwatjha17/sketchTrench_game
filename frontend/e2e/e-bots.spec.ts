import { expect, test } from '@playwright/test';
import { chat, cleanup, newPlayer, pickWord, quickSettings, roomIdFrom, startGame } from './helpers';

test.afterEach(cleanup);

test('[E28] practice adds 2 bots and they play along', async ({ browser }) => {
  const p = await newPlayer(browser, 'solo');
  await p.page.getByRole('button', { name: /Practice/ }).click();
  await p.page.waitForURL(/\/rooms\//);
  await expect(p.page.getByRole('button', { name: /^Remove .*\(bot\)$/ })).toHaveCount(2);
  await quickSettings(p, roomIdFrom(p.page), { drawingTimeSec: 40, totalRounds: 1 });
  await startGame(p);
  await pickWord(p);
  // bots make wrong guesses and/or guess right during the round
  await expect(
    chat(p).locator('p').filter({ hasText: /\(bot\)/ }).first(),
  ).toBeVisible({ timeout: 45_000 });
});
