import { expect, test } from '@playwright/test';
import { BASE_URL, api, cleanup, createPrivateRoom, joinByCode, nameInput, newPlayer } from './helpers';

test.afterEach(cleanup);

test('[C9] private room shows its code and copies the invite link', async ({ browser }) => {
  const host = await newPlayer(browser);
  const roomId = await createPrivateRoom(host);
  await expect(host.page.getByText(roomId)).toBeVisible();
  await expect(host.page.getByText('Private room')).toBeVisible();
  await host.page.getByRole('button', { name: /Invite link/ }).click();
  await expect(host.page.getByRole('button', { name: /Link copied/ })).toBeVisible();
  const clip = await host.page.evaluate(() => navigator.clipboard.readText());
  expect(clip).toBe(`${new URL(BASE_URL).origin}/rooms/${roomId}`);
});

test('[C10] private rooms are hidden from Open games until made public', async ({ browser }) => {
  const host = await newPlayer(browser, 'host');
  await createPrivateRoom(host);
  const roomName = `${host.name}'s room`;
  const other = await newPlayer(browser, 'look');
  const list = other.page.locator('section').filter({ has: other.page.getByRole('heading', { name: /Open games/ }) });
  await expect(list).toBeVisible({ timeout: 90_000 });
  await other.page.waitForTimeout(6_000); // one refresh cycle
  await expect(list.getByText(roomName)).toHaveCount(0);
  await host.page.getByRole('switch', { name: 'Public room' }).click();
  await expect(host.page.getByRole('switch', { name: 'Public room' })).toHaveAttribute('aria-checked', 'true');
  await expect(list.getByText(roomName)).toBeVisible({ timeout: 12_000 });
  await expect(list.locator('li', { hasText: roomName }).getByRole('button', { name: 'Join' })).toBeVisible();
});

test('[C11] host settings sync live; other players cannot edit', async ({ browser }) => {
  const host = await newPlayer(browser, 'host');
  const roomId = await createPrivateRoom(host);
  const guest = await newPlayer(browser, 'gst');
  await joinByCode(guest, roomId);
  await expect(guest.page.getByText('Only the host can change these.')).toBeVisible();
  await expect(guest.page.getByRole('switch', { name: 'Letter hints' })).toBeDisabled();

  await host.page.getByRole('button', { name: 'Increase' }).first().click(); // rounds 3 -> 4
  await expect(guest.page.getByText('4 rounds')).toBeVisible();
  await host.page.getByRole('button', { name: '120s' }).click();
  await expect(guest.page.getByText('120s to draw')).toBeVisible();
  await host.page.locator('dd select').selectOption('de');
  await expect(guest.page.getByText('Deutsch words')).toBeVisible();
  await host.page.getByRole('switch', { name: 'Letter hints' }).click();
  await expect(guest.page.getByRole('switch', { name: 'Letter hints' })).toHaveAttribute('aria-checked', 'false');
  await host.page.getByRole('switch', { name: 'Custom words' }).click();
  await expect(guest.page.getByRole('heading', { name: 'Custom words' })).toBeVisible();
});

test('[C12] Start needs 2 players; bots can be added and removed', async ({ browser }) => {
  const host = await newPlayer(browser);
  await createPrivateRoom(host);
  const start = host.page.getByRole('button', { name: /Start game/ });
  await expect(start).toBeDisabled();
  await host.page.getByRole('button', { name: /Add a bot/ }).click();
  const bot = host.page.getByRole('button', { name: /^Remove .*\(bot\)$/ });
  await expect(bot).toHaveCount(1);
  await expect(start).toBeEnabled();
  await bot.click();
  await expect(bot).toHaveCount(0);
  await expect(start).toBeDisabled();
});

test('[C13] invite link asks a new visitor for a name, then joins', async ({ browser }) => {
  const host = await newPlayer(browser, 'host');
  const roomId = await createPrivateRoom(host);
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  try {
    await page.goto(`/rooms/${roomId}`);
    await expect(page).toHaveURL(new RegExp(`join=${roomId}`));
    await expect(page.getByText(`invited to room ${roomId}`)).toBeVisible();
    await nameInput(page).fill('Invitee');
    await page.getByRole('button', { name: /Join room/ }).click();
    await page.waitForURL(new RegExp(`/rooms/${roomId}`));
    await expect(host.page.getByText('Invitee')).toBeVisible();
  } finally {
    const s = await page.evaluate(() => JSON.parse(localStorage.getItem('st_guest') ?? 'null'));
    if (s) await api(`/rooms/${roomId}/leave`, s.playerId).catch(() => {});
    await ctx.close();
  }
});

test('[C14] when the host leaves, the other player becomes host', async ({ browser }) => {
  const host = await newPlayer(browser, 'host');
  const roomId = await createPrivateRoom(host);
  const guest = await newPlayer(browser, 'gst');
  await joinByCode(guest, roomId);
  await expect(guest.page.getByText('Waiting for the host…')).toBeVisible();
  await host.page.getByRole('button', { name: /Leave room/ }).click();
  await expect(host.page).toHaveURL(/\/$/);
  await expect(guest.page.getByRole('button', { name: /Start game/ })).toBeVisible();
  await expect(guest.page.getByLabel('host')).toBeVisible();
});

test('[C15] host kicks a player, who cannot rejoin', async ({ browser }) => {
  const host = await newPlayer(browser, 'host');
  const roomId = await createPrivateRoom(host);
  const guest = await newPlayer(browser, 'gst');
  await joinByCode(guest, roomId);
  host.page.once('dialog', (d) => d.accept());
  await host.page.getByRole('button', { name: `Kick ${guest.name}` }).click();
  await expect(guest.page).toHaveURL(/kicked=1/);
  await expect(guest.page.getByRole('alert')).toContainText('removed');
  // (the "… was kicked" notice still mentions the name, so check the player list itself)
  await expect(host.page.locator('section li', { hasText: guest.name })).toHaveCount(0);
  await guest.page.getByLabel('Room code').fill(roomId);
  await guest.page.getByRole('button', { name: 'Join', exact: true }).click();
  await expect(guest.page.getByRole('alert')).toContainText('removed from this room');
});

test('[C15b] a player kicked while offline lands in the lobby when they reconnect', async ({ browser }) => {
  const host = await newPlayer(browser, 'host');
  const roomId = await createPrivateRoom(host);
  const guest = await newPlayer(browser, 'gst');
  await joinByCode(guest, roomId);
  await expect(host.page.locator('section li', { hasText: guest.name })).toBeVisible();
  await guest.context.setOffline(true); // their socket drops, so the "kicked" push can't reach them
  host.page.once('dialog', (d) => d.accept());
  await host.page.getByRole('button', { name: `Kick ${guest.name}` }).click();
  await expect(host.page.locator('section li', { hasText: guest.name })).toHaveCount(0);
  await guest.context.setOffline(false);
  await expect(guest.page).toHaveURL(/kicked=1/, { timeout: 40_000 });
  await expect(guest.page.getByRole('alert')).toContainText('removed');
  await expect(guest.page.getByText('reconnecting')).toHaveCount(0);
});
