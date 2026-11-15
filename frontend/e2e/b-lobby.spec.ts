import { expect, test } from '@playwright/test';
import { cleanup, createPrivateRoom, nameInput, newPlayer } from './helpers';

test.afterEach(cleanup);

test('[B4] Play / Private / Practice stay disabled until a name is typed', async ({ browser }) => {
  const p = await newPlayer(browser);
  await nameInput(p.page).fill('');
  for (const name of [/^Play$/, /Private/, /Practice/]) {
    await expect(p.page.getByRole('button', { name })).toBeDisabled();
  }
  await nameInput(p.page).fill('Picasso');
  for (const name of [/^Play$/, /Private/, /Practice/]) {
    await expect(p.page.getByRole('button', { name })).toBeEnabled();
  }
});

test('[B5] an offensive nickname is refused', async ({ browser }) => {
  const p = await newPlayer(browser);
  await nameInput(p.page).fill('xXsh1tXx');
  await p.page.getByRole('button', { name: /Private/ }).click();
  await expect(p.page.getByRole('alert')).toContainText("isn't allowed");
  await expect(p.page).toHaveURL(/\/$/);
});

test('[B6] avatar builder changes the avatar', async ({ browser }) => {
  const p = await newPlayer(browser);
  const avatar = p.page.getByRole('img', { name: 'avatar' }).first();
  const snap = () => avatar.innerHTML();
  let before = await snap();
  for (const label of ['Next face', 'Next hair']) {
    await p.page.getByRole('button', { name: label }).click();
    const after = await snap();
    expect(after, label).not.toBe(before);
    before = after;
  }
  await p.page.getByRole('button', { name: 'colour #74c0fc' }).click();
  await expect(avatar.locator('circle').first()).toHaveAttribute('fill', '#74c0fc');
  await p.page.getByRole('button', { name: /Shades/ }).click();
  expect(await snap()).not.toBe(before);
});

test('[B7] refreshing keeps the session and returns to the room', async ({ browser }) => {
  const p = await newPlayer(browser);
  const roomId = await createPrivateRoom(p);
  await p.page.reload();
  await expect(p.page).toHaveURL(new RegExp(`/rooms/${roomId}`));
  await p.page.goto('/');
  await expect(p.page).toHaveURL(new RegExp(`/rooms/${roomId}`)); // lobby resumes into the room
  await expect(p.page.getByText(p.name).first()).toBeVisible();
});

test('[B8] joining an unknown room code shows an error', async ({ browser }) => {
  const p = await newPlayer(browser);
  await p.page.getByLabel('Room code').fill('zzzzzzzz');
  await p.page.getByRole('button', { name: 'Join', exact: true }).click();
  await expect(p.page.getByRole('alert')).toBeVisible();
  await expect(p.page).toHaveURL(/\/$/);
});
