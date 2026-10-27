import { expect, test } from 'playwright/test';
import { assertLayout, mockWorkspace } from './ui-fixtures';

test('portfolio tooltip follows the pointer, stays on screen and adapts to light and dark themes', async ({
  page,
}, testInfo) => {
  await mockWorkspace(page, 'CLIENT');
  await page.goto('/dashboard');
  const plot = page.locator('.portfolio-value-chart');
  const tooltip = page.getByRole('tooltip');
  const hover = async (edge = false) => {
    await plot.scrollIntoViewIfNeeded();
    const bounds = (await plot.boundingBox())!;
    const x = bounds.x + (edge ? bounds.width - 2 : bounds.width / 2);
    const y = bounds.y + (edge ? bounds.height - 2 : bounds.height / 2);
    await page.mouse.move(x, y);
    await expect(tooltip).toBeVisible();
    return { x, y };
  };
  await hover();
  await expect(tooltip).toContainText('Portfolio value');
  await expect(tooltip).toContainText('£10,000.00');
  await expect(tooltip).toContainText('UTC');
  const light = await tooltip.evaluate((element) => getComputedStyle(element).backgroundColor);
  const before = (await tooltip.boundingBox())!;
  const cursor = await hover(true);
  const after = (await tooltip.boundingBox())!;
  expect(after.x).not.toBe(before.x);
  expect(after.x).toBeGreaterThanOrEqual(11);
  expect(after.y).toBeGreaterThanOrEqual(11);
  expect(after.x + after.width).toBeLessThanOrEqual(page.viewportSize()!.width - 11);
  expect(after.y + after.height).toBeLessThanOrEqual(page.viewportSize()!.height - 11);
  expect(
    Math.min(Math.abs(after.x - cursor.x), Math.abs(after.x + after.width - cursor.x)),
  ).toBeGreaterThanOrEqual(12);
  await page.getByRole('button', { name: 'Dark mode', exact: true }).click();
  await hover(true);
  const dark = await tooltip.evaluate((element) => getComputedStyle(element).backgroundColor);
  expect(dark).not.toBe(light);
  await page.screenshot({ path: testInfo.outputPath('portfolio-tooltip-dark.png') });
  await page.mouse.move(0, 0);
  await expect(tooltip).toHaveCount(0);
  await assertLayout(page);
});

test('selling a complete position removes its row and count, and buying again restores it', async ({
  page,
}) => {
  const state = await mockWorkspace(page, 'CLIENT');
  let quantity = 2;
  let nextId = 200;
  const headers = {
    'access-control-allow-origin': 'http://localhost:4200',
    'access-control-allow-credentials': 'true',
  };
  await page.route('**/api/accounts/me/holdings', (route) =>
    route.request().method() === 'OPTIONS'
      ? route.fallback()
      : route.fulfill({
          headers,
          json: {
            accountId: 1,
            currency: 'GBP',
            cashBalance: state.cash,
            positions: [{ instrumentId: 1, symbol: 'VOD.L', instrumentName: 'Vodafone', quantity }],
          },
        }),
  );
  await page.route('**/api/orders', async (route) => {
    if (route.request().method() !== 'POST') return route.fallback();
    const body = route.request().postDataJSON();
    const selling = body.side === 'SELL';
    quantity += (selling ? -1 : 1) * body.quantity;
    const settlementAmount = (selling ? 99 : -100) * body.quantity;
    state.cash += settlementAmount;
    const order = {
      id: ++nextId,
      side: body.side,
      quantity: body.quantity,
      status: 'FILLED',
      rejectionReason: null,
      fillPrice: selling ? 99 : 100,
      fillCurrency: 'GBP',
      accountCurrency: 'GBP',
      fxRate: 1,
      settlementAmount,
      submittedAt: new Date().toISOString(),
      filledAt: new Date().toISOString(),
    };
    state.orders.push(order);
    return route.fulfill({ status: 201, headers, json: order });
  });
  await page.goto('/dashboard');
  await page.getByRole('tab', { name: 'Holdings', exact: true }).click();
  await expect(page.locator('.position-row')).toHaveCount(1);
  for (const expected of [1, 0]) {
    await page.getByRole('button', { name: 'Open Orders for VOD.L', exact: true }).click();
    await page.getByRole('button', { name: 'SELL', exact: true }).click();
    await page.getByLabel('Quantity', { exact: true }).fill('1');
    await page.locator('.order-panel .submit-btn').click();
    await expect(page.locator('.order-success')).toContainText('filled');
    await page.getByRole('tab', { name: 'Holdings', exact: true }).click();
    await expect(page.locator('.position-row')).toHaveCount(expected ? 1 : 0);
    if (expected)
      await expect(page.locator('.position-row td[data-label="Quantity"]')).toHaveText('1');
  }
  await expect(page.locator('.positions-section .panel-heading')).toContainText('0 instruments');
  await expect(page.locator('.positions-table')).toContainText('No positions currently held.');
  await page.getByRole('tab', { name: 'Orders', exact: true }).click();
  await page.getByRole('button', { name: 'BUY', exact: true }).click();
  await page.getByLabel('Quantity', { exact: true }).fill('1');
  await page.locator('.order-panel .submit-btn').click();
  await expect(page.locator('.order-success')).toContainText('filled');
  await page.getByRole('tab', { name: 'Holdings', exact: true }).click();
  await expect(page.locator('.position-row')).toHaveCount(1);
  await expect(page.locator('.position-row')).toContainText('VOD.L');
  await assertLayout(page);
});
