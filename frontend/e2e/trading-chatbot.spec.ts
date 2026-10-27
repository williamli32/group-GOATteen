import { expect, test } from 'playwright/test';
import { assertLayout, mockWorkspace } from './ui-fixtures';

test('chat previews a stock trade, requires confirmation, and fetches updated holdings', async ({
  page,
}) => {
  const state = await mockWorkspace(page, 'CLIENT');
  await page.goto('/dashboard');
  const chat = page.getByRole('region', { name: 'Trading assistant', exact: true });
  await chat.getByRole('button', { name: 'Open chat' }).click();
  const message = chat.getByLabel('Message the trading assistant');
  await message.fill('buy VOD.L');
  await chat.getByRole('button', { name: 'Send', exact: true }).click();
  await expect(chat.getByRole('log')).toContainText('How many shares of VOD.L');
  await message.fill('1');
  await message.press('Enter');
  const preview = chat.getByRole('region', { name: 'Chat order preview' });
  await expect(preview).toContainText('BUY 1 VOD.L');
  expect(state.orders).toHaveLength(0);
  await assertLayout(page);
  await preview.getByRole('button', { name: 'Confirm order', exact: true }).click();
  await expect(chat.getByRole('log')).toContainText('Order #77 filled: bought 1 shares of VOD.L');
  expect(state.orders).toHaveLength(1);
  await chat.getByRole('button', { name: 'My holdings', exact: true }).click();
  await expect(chat.getByRole('table', { name: 'Your holdings snapshot' })).toContainText(
    'Vodafone',
  );
  await expect(chat.getByRole('log')).toContainText('9,900.00 GBP');
  await assertLayout(page);
});

test('chat cancels an unsubmitted draft and never acts on a negated trade', async ({ page }) => {
  const state = await mockWorkspace(page, 'CLIENT');
  await page.goto('/dashboard');
  const chat = page.getByRole('region', { name: 'Trading assistant', exact: true });
  await chat.getByRole('button', { name: 'Open chat' }).click();
  const message = chat.getByLabel('Message the trading assistant');
  await message.fill('buy 2 VOD.L');
  await message.press('Enter');
  await chat.getByRole('button', { name: 'Cancel draft' }).click();
  await expect(chat.getByRole('region', { name: 'Chat order preview' })).toHaveCount(0);
  await message.fill('do not buy 5 VOD.L');
  await message.press('Enter');
  await expect(chat.getByRole('log')).toContainText('could not understand that instruction');
  expect(state.orders).toHaveLength(0);
});
