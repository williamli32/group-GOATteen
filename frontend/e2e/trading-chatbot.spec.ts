import { expect, test } from 'playwright/test';
import { assertLayout, mockWorkspace } from './ui-fixtures';

test('chat icon stays at the bottom right, opens on click and keeps drafts across minimize and dashboard tabs', async ({
  page,
}, testInfo) => {
  await mockWorkspace(page, 'CLIENT');
  await page.goto('/dashboard');
  const chat = page.getByRole('region', { name: 'Trading assistant', exact: true });
  const launcher = chat.getByRole('button', { name: 'Open chat', exact: true });
  await expect(page.getByRole('dialog', { name: 'Trading assistant', exact: true })).toHaveCount(0);
  const initial = (await launcher.boundingBox())!;
  expect(initial.x + initial.width).toBeLessThanOrEqual(page.viewportSize()!.width - 8);
  expect(initial.x).toBeGreaterThan(page.viewportSize()!.width - 100);
  expect(initial.y).toBeGreaterThan(page.viewportSize()!.height - 100);
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
  const scrolled = (await launcher.boundingBox())!;
  expect(scrolled.x).toBeCloseTo(initial.x, 0);
  expect(scrolled.y).toBeCloseTo(initial.y, 0);
  const scroll = await page.evaluate(() => window.scrollY);
  await launcher.click();
  const panel = page.getByRole('dialog', { name: 'Trading assistant', exact: true });
  await expect(panel).toBeVisible();
  const message = panel.getByLabel('Message the trading assistant');
  await expect(message).toBeFocused();
  expect(await page.evaluate(() => window.scrollY)).toBeCloseTo(scroll, 0);
  await message.fill('help');
  await message.press('Enter');
  await message.fill('an unsent draft');
  const bounds = (await panel.boundingBox())!;
  expect(bounds.x).toBeGreaterThanOrEqual(8);
  expect(bounds.y).toBeGreaterThanOrEqual(8);
  expect(bounds.x + bounds.width).toBeLessThanOrEqual(page.viewportSize()!.width - 8);
  expect(bounds.y + bounds.height).toBeLessThanOrEqual(page.viewportSize()!.height - 60);
  await page.screenshot({ path: testInfo.outputPath('floating-chat-light.png') });
  await message.press('Escape');
  await expect(panel).toHaveCount(0);
  await expect(launcher).toBeFocused();
  await page.getByRole('tab', { name: 'Holdings', exact: true }).click();
  await launcher.click();
  await expect(message).toHaveValue('an unsent draft');
  await expect(panel.getByRole('log')).toContainText('help');
  await panel.getByRole('button', { name: 'Close chat', exact: true }).click();
  await page.getByRole('button', { name: 'Dark mode', exact: true }).click();
  await launcher.click();
  await expect(message).toHaveValue('an unsent draft');
  await page.screenshot({ path: testInfo.outputPath('floating-chat-dark.png') });
  await assertLayout(page);
});

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
