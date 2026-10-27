import { expect, test } from 'playwright/test';
import { assertLayout, mockWorkspace } from './ui-fixtures';

test('portfolio follows held-stock prices, preserves history controls, and pauses across feed failures', async ({
  page,
}, testInfo) => {
  const state = await mockWorkspace(page, 'CLIENT');
  state.orders.push({
    id: 77,
    side: 'BUY',
    quantity: 1,
    status: 'FILLED',
    rejectionReason: null,
    fillPrice: 100,
    fillCurrency: 'GBP',
    accountCurrency: 'GBP',
    fxRate: 1,
    settlementAmount: -100,
    submittedAt: new Date().toISOString(),
    filledAt: new Date().toISOString(),
  });
  await page.goto('/dashboard');
  const panel = page.getByRole('region', { name: 'Portfolio value chart', exact: true });
  await expect(panel.getByRole('button', { name: 'LIVE', exact: true })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
  await expect(panel.getByRole('heading')).toHaveText('£10,100.00');
  await expect(panel.locator('.value-line')).toHaveCount(1, { timeout: 10000 });
  const before = await panel.locator('.value-line').getAttribute('points');
  state.quotePrice = 110;
  state.quoteId++;
  await expect(panel.getByRole('heading')).toHaveText('£10,110.00', { timeout: 10000 });
  await expect
    .poll(() => panel.locator('.value-line').first().getAttribute('points'))
    .not.toBe(before);
  const coordinates = (await panel.locator('.value-line').first().getAttribute('points'))!;
  const ys = coordinates.split(' ').map((point) => Number(point.split(',')[1]));
  expect(Math.max(...ys) - Math.min(...ys)).toBeGreaterThan(50);
  await assertLayout(page);
  await panel.screenshot({ path: testInfo.outputPath('portfolio-live.png') });

  await panel.getByRole('button', { name: '5D', exact: true }).click();
  await expect(panel.locator('.portfolio-range-caption')).toContainText('daily account values');
  await panel.getByRole('button', { name: 'LIVE', exact: true }).click();
  await expect(panel.locator('.live-status')).toHaveText('Live');

  let failed = true;
  await page.route('**/api/market/instruments', (route) =>
    failed
      ? route.fulfill({
          status: 503,
          headers: {
            'access-control-allow-origin': 'http://localhost:4200',
            'access-control-allow-credentials': 'true',
          },
          json: { message: 'Market feed unavailable' },
        })
      : route.fallback(),
  );
  await expect(panel.locator('.live-status')).toHaveText('Updates paused', { timeout: 10000 });
  failed = false;
  state.quotePrice = 111;
  state.quoteId++;
  await expect(panel.getByRole('heading')).toHaveText('£10,111.00', { timeout: 10000 });
  await expect(panel.locator('.live-status')).toHaveText('Live');
});
