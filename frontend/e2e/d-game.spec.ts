import { expect, test } from '@playwright/test';
import {
  chat,
  cleanup,
  createPrivateRoom,
  drawStroke,
  inkPixels,
  joinByCode,
  nearMiss,
  newPlayer,
  pickWord,
  quickSettings,
  say,
  startGame,
} from './helpers';

test.afterEach(cleanup);

/** Strips accents the way the server compares guesses. */
const plain = (w: string) => w.normalize('NFD').replace(/\p{M}/gu, '');

test('[D16–22, D20, D25] a full 2-player round', async ({ browser }) => {
  const host = await newPlayer(browser, 'draw');
  const roomId = await createPrivateRoom(host);
  const guest = await newPlayer(browser, 'gues');
  await joinByCode(guest, roomId);
  await quickSettings(host, roomId, { totalRounds: 1, drawingTimeSec: 90, hints: false });

  await test.step('[D16] drawer gets the word picker at once; guesser sees "picking a word"', async () => {
    await startGame(host);
    await expect(host.page.getByRole('heading', { name: 'Your turn to draw!' })).toBeVisible({ timeout: 5_000 });
    await guest.page.waitForURL(/\/game\//);
    await expect(guest.page.getByText(/is picking a word/)).toBeVisible();
  });

  const word = await pickWord(host);
  const letters = [...word].filter((c) => /[\p{L}\p{N}]/u.test(c)).length;

  await test.step('[D17] guesser sees the right number of blanks and a running timer', async () => {
    await expect(guest.page.getByLabel(`${letters} letters`)).toBeVisible();
    const timer = guest.page.getByRole('timer');
    const t1 = Number((await timer.getAttribute('aria-label'))!.match(/\d+/)![0]);
    await guest.page.waitForTimeout(2_500);
    const t2 = Number((await timer.getAttribute('aria-label'))!.match(/\d+/)![0]);
    expect(t2).toBeLessThan(t1);
  });

  await test.step('[D18] strokes, undo and clear reach the guesser', async () => {
    expect(await inkPixels(guest.page)).toBe(0);
    await drawStroke(host);
    await expect.poll(() => inkPixels(guest.page)).toBeGreaterThan(20);
    await host.page.getByRole('button', { name: 'Undo' }).click();
    await expect.poll(() => inkPixels(guest.page)).toBe(0);
    await drawStroke(host);
    await expect.poll(() => inkPixels(guest.page)).toBeGreaterThan(20);
    await host.page.getByRole('button', { name: 'Clear the board' }).click();
    await expect.poll(() => inkPixels(guest.page)).toBe(0);
  });

  await test.step('[D19] a near-miss gets a private "close!" hint', async () => {
    const miss = nearMiss(word);
    await say(guest, miss);
    await expect(chat(guest).getByText(`"${miss}" is close!`)).toBeVisible();
    await expect(chat(host).getByText('is close!')).toHaveCount(0);
  });

  await test.step('[D21] the drawer cannot say the word (even without accents)', async () => {
    await say(host, `it is a ${plain(word).toUpperCase()}`);
    await expect(chat(host).getByText("you can't say the word")).toBeVisible();
    await expect(chat(guest).getByText(new RegExp(plain(word), 'i'))).toHaveCount(0);
  });

  await test.step('[D22] swear words are starred out for others', async () => {
    await say(guest, 'what the fuck');
    await expect(chat(host).getByText('what the ****')).toBeVisible();
    await expect(chat(host).getByText('fuck')).toHaveCount(0);
  });

  await test.step('[D20] correct guess scores, shows a check, never leaks into chat', async () => {
    await say(guest, word);
    await expect(chat(guest).getByText('You guessed the word!')).toBeVisible();
    await expect(guest.page.getByText('You got it!')).toBeVisible();
    await expect(chat(host).getByText(`${guest.name} guessed the word!`)).toBeVisible();
    await expect(guest.page.getByLabel('guessed')).toBeVisible();
    await expect(chat(host).locator('p', { hasText: new RegExp(`^${guest.name} ${word}$`) })).toHaveCount(0);
  });

  await test.step('[D25] game over shows the ranking and returns to the lobby', async () => {
    await expect(guest.page.getByRole('heading', { name: /win/ })).toBeVisible({ timeout: 20_000 });
    const ranking = guest.page.locator('.fixed ol li');
    await expect(ranking).toHaveCount(2);
    await expect(ranking.first()).toContainText(guest.name); // the guesser scored more
    await guest.page.getByRole('button', { name: 'Back to the lobby' }).click();
    await expect(guest.page).toHaveURL(/\/$/);
  });
});

test('[D23, D24] rate limit and letter hints', async ({ browser }) => {
  const host = await newPlayer(browser, 'draw');
  const roomId = await createPrivateRoom(host);
  const guest = await newPlayer(browser, 'gues');
  await joinByCode(guest, roomId);
  await quickSettings(host, roomId, { totalRounds: 1, drawingTimeSec: 40, hints: true });
  await startGame(host);
  await pickWord(host);
  await guest.page.waitForURL(/\/game\//);
  const blanks = guest.page.getByLabel(/\d+ letters/).locator('xpath=..');

  await test.step('[D23] sending messages too fast triggers "Slow down"', async () => {
    for (let i = 0; i < 8; i++) await say(guest, `zz${i}`);
    await expect(chat(guest).getByText(/Slow down/)).toHaveCount(1);
  });

  await test.step('[D24] a letter is revealed during the round', async () => {
    await expect(blanks).toBeVisible();
    expect(await blanks.textContent()).not.toMatch(/\p{L}/u);
    await expect
      .poll(async () => /\p{L}/u.test((await blanks.textContent()) ?? ''), { timeout: 35_000, intervals: [1_000] })
      .toBe(true);
  });
});

test('[D26, D27] join a running game from Open games; refresh restores the board', async ({ browser }) => {
  const host = await newPlayer(browser, 'draw');
  const roomId = await createPrivateRoom(host);
  const guest = await newPlayer(browser, 'gues');
  await joinByCode(guest, roomId);
  await quickSettings(host, roomId, { totalRounds: 2, drawingTimeSec: 90, isPrivate: false });
  await startGame(host);
  await pickWord(host);
  await drawStroke(host);

  const late = await newPlayer(browser, 'late');
  await test.step('[D26] a newcomer jumps into the game in progress', async () => {
    const card = late.page.locator('li', { hasText: `${host.name}'s room` });
    await expect(card).toBeVisible({ timeout: 90_000 });
    await expect(card.getByText(/Round 1\/2/)).toBeVisible();
    await card.getByRole('button', { name: 'Jump in' }).click();
    await late.page.waitForURL(new RegExp(`/game/${roomId}`));
    await expect(late.page.getByLabel(/\d+ letters/)).toBeVisible();
    await expect(late.page.locator('li', { hasText: late.name })).toBeVisible();
    await expect.poll(() => inkPixels(late.page)).toBeGreaterThan(20); // existing drawing replayed
    await expect(host.page.locator('li', { hasText: late.name })).toBeVisible();
  });

  await test.step('[D27] refreshing mid-game restores the round and board', async () => {
    await guest.page.reload();
    await expect(guest.page).toHaveURL(new RegExp(`/game/${roomId}`));
    await expect(guest.page.getByLabel(/\d+ letters/)).toBeVisible();
    await expect.poll(() => inkPixels(guest.page)).toBeGreaterThan(20);
    await expect(guest.page.getByText(/Round 1 of 2/)).toBeVisible();
  });
});
