import { test, expect } from 'playwright/test';
import { assertLayout, mockWorkspace, type Role } from './ui-fixtures';

for (const role of ['CLIENT', 'ANALYST', 'OPERATIONS'] as Role[]) {
  test(`${role}: real workspace components, navigation, keyboard and responsive workflow`, async ({
    page,
  }) => {
    await mockWorkspace(page, role);
    await expect(page.locator('.nav-label')).toHaveText(
      role === 'CLIENT'
        ? ['Dashboard']
        : role === 'ANALYST'
          ? ['Dashboard', 'Reports']
          : ['Dashboard', 'Reports', 'Operations'],
    );
    await page.getByRole('link', { name: 'Dashboard', exact: true }).click();
    await expect(
      page.getByRole('heading', { name: 'Account overview', exact: true }),
    ).toBeVisible();
    await assertLayout(page);
    const chart = page.locator('.portfolio-value-chart');
    await chart.focus();
    await page.keyboard.press('End');
    await expect(page.locator('.point-detail')).toContainText('GBP');
    expect(await chart.evaluate((element) => getComputedStyle(element).outlineStyle)).not.toBe(
      'none',
    );

    if (role === 'CLIENT') {
      await page.getByRole('tab', { name: 'Watchlist', exact: true }).click();
      await page.getByLabel('Add an instrument', { exact: true }).selectOption('1');
      await page.getByRole('button', { name: 'Add to watchlist', exact: true }).click();
      await expect(page.locator('.watchlist-items')).toContainText('VOD.L');
      await page.reload();
      await page.getByRole('tab', { name: 'Watchlist', exact: true }).click();
      await expect(page.locator('.watchlist-items')).toContainText('VOD.L');
      await page.getByRole('button', { name: 'Price alerts', exact: true }).click();
      await page.getByLabel('Instrument', { exact: true }).selectOption({
        label: 'VOD.L · LSE · GBP',
      });
      await page.getByLabel(/Target price/).fill('90');
      await page.getByRole('button', { name: 'Create alert', exact: true }).click();
      await expect(page.locator('.notification-history')).toContainText(
        'reached your upper target',
      );
      await assertLayout(page);
      await page.getByRole('tab', { name: 'Orders', exact: true }).click();
      await page.getByLabel('Quantity', { exact: true }).fill('1');
      await page.locator('.order-panel .submit-btn').click();
      await expect(page.locator('.order-success')).toContainText('filled');
      await page.getByRole('tab', { name: 'Holdings', exact: true }).click();
      await expect(page.locator('.positions-table')).toContainText('VOD.L');
      await assertLayout(page);
      await page.getByRole('tab', { name: 'Account overview', exact: true }).click();
      await page.getByRole('button', { name: 'Expand', exact: true }).click();
      await page.getByRole('button', { name: 'Withdraw', exact: true }).click();
      await page.getByLabel('Amount (GBP)', { exact: true }).fill('10');
      const withdraw = page.locator('.funding-controls button');
      await withdraw.click();
      const dialog = page.getByRole('alertdialog');
      await expect(dialog).toBeVisible();
      await expect(dialog.getByRole('button', { name: 'Cancel', exact: true })).toBeFocused();
      await assertLayout(page);
      await dialog.getByRole('button', { name: 'Confirm withdrawal', exact: true }).focus();
      await page.keyboard.press('Tab');
      await expect(dialog.getByRole('button', { name: 'Cancel', exact: true })).toBeFocused();
      await page.keyboard.press('Escape');
      await expect(dialog).not.toBeVisible();
      await expect(withdraw).toBeFocused();
    } else {
      await page.getByRole('link', { name: 'Reports', exact: true }).click();
      await expect(
        page.getByRole('heading', { name: 'Reports & insights', exact: true }),
      ).toBeVisible();
      await expect(
        page.getByRole('combobox', { name: /^Currency\b/ }).locator('option'),
      ).toHaveText(['USD', 'GBP', 'INR']);
      await expect(page.getByRole('combobox', { name: /^Status\b/ }).locator('option')).toHaveText([
        'All statuses',
        'FILLED',
        'REJECTED',
      ]);
      await assertLayout(page);
      await page.locator('.metric-card').first().click();
      const dialog = page.getByRole('dialog');
      await expect(dialog).toBeVisible();
      await assertLayout(page);
      await page.keyboard.press('Escape');
      await expect(dialog).not.toBeVisible();
      await page.getByRole('button', { name: 'Data explorer', exact: true }).click();
      await page
        .locator('app-result-pagination')
        .last()
        .getByRole('button', { name: 'Next', exact: true })
        .click();
      await expect(page.locator('app-result-pagination').last()).toContainText('11–12 of 12');
      await assertLayout(page);
      await page.getByRole('button', { name: 'Activity & trends', exact: true }).click();
      await assertLayout(page);
      if (role === 'OPERATIONS') {
        await page.getByRole('link', { name: 'Operations', exact: true }).click();
        await page.locator('.search-options summary').click();
        await assertLayout(page);
        await page.getByLabel('Order ID', { exact: true }).fill('77');
        await page.getByRole('button', { name: 'Find ID across all dates', exact: true }).click();
        await page.getByRole('button', { name: 'Inspect order 77, VOD.L', exact: true }).click();
        await expect(page.locator('.integrity-ok')).toBeVisible();
        await expect(page.locator('#lifecycle-evidence')).toContainText('SUBMITTED');
        await page.getByRole('button', { name: /Execution & quote/ }).click();
        await expect(page.locator('#execution-evidence')).toContainText('GBP');
        await assertLayout(page);
      }
    }
  });
}

test('portfolio errors retain the applied range and have a usable retry', async ({ page }) => {
  const state = await mockWorkspace(page, 'CLIENT');
  await expect(page.locator('.portfolio-value-chart')).toBeVisible();
  state.historyFailed = true;
  await page.getByRole('button', { name: '1M', exact: true }).click();
  await expect(page.locator('.history-error')).toContainText('Still showing 5D');
  await expect(page.locator('.dashboard-tabs')).toBeVisible();
  state.historyFailed = false;
  await page.getByRole('button', { name: 'Retry history', exact: true }).click();
  await expect(page.locator('.history-error')).toHaveCount(0);
  await expect(page.locator('.portfolio-range-caption')).toContainText('1M');
});

test('signup keeps exactly the three supported account currencies', async ({ page }) => {
  await mockWorkspace(page, 'CLIENT');
  await page.goto('/register');
  await expect(
    page.getByLabel('Account currency', { exact: true }).locator('option:not([disabled])'),
  ).toHaveText(['USD — USD', 'GBP — GBP', 'INR — INR']);
  await expect(page.getByLabel('Account Type', { exact: true }).locator('option')).toHaveText([
    'Customer',
    'Analyst',
    'Operations',
  ]);
  await assertLayout(page);
});

test('light and dark themes retain readable, bounded workspaces after reload', async ({ page }) => {
  await mockWorkspace(page, 'CLIENT');
  const toggle = page.getByRole('button', { name: 'Dark mode', exact: true });
  if ((await toggle.getAttribute('aria-pressed')) !== 'true') await toggle.click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await assertLayout(page);
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await page.getByRole('button', { name: 'Dark mode', exact: true }).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
  await assertLayout(page);
});
