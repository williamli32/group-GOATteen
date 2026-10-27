import { randomUUID } from 'node:crypto';
import { test, expect, type APIRequestContext, type Page } from 'playwright/test';
import { assertLayout, type Role } from './ui-fixtures';

type Actor = { email: string; password: string; token: string; account: { accountId: number } };
const api = process.env['GOAL5_API_URL'] ?? 'http://localhost:8080/api';
const password = 'Goal5-Demo-Account!2026';

test.beforeAll(() => {
  if (process.env['GOAL5_ALLOW_MUTATIONS'] !== 'true')
    throw new Error(
      'Live acceptance creates test accounts and simulated trades. Use a dedicated _test database and set GOAL5_ALLOW_MUTATIONS=true.',
    );
  if (!process.env['GOAL5_ACCESS_CODE'])
    throw new Error('Set GOAL5_ACCESS_CODE to your configured analyst/operations signup code.');
});

async function actor(request: APIRequestContext, role: Role): Promise<Actor> {
  const email = `goal5-${randomUUID()}@example.test`;
  const registered = await request.post(api + '/auth/register', {
    data: {
      firstName: 'GoalFive',
      lastName: role,
      email,
      password,
      role,
      currency: 'GBP',
      accessCode: role === 'CLIENT' ? undefined : process.env['GOAL5_ACCESS_CODE'],
    },
  });
  expect(
    registered.status(),
    'Test-account registration must succeed against the real backend',
  ).toBe(201);
  const login = await request.post(api + '/auth/login', { data: { email, password } });
  expect(login.ok()).toBe(true);
  const token = (await login.json()).accessToken as string;
  const account = await request.get(api + '/accounts/me', {
    headers: { Authorization: 'Bearer ' + token },
  });
  expect(account.ok()).toBe(true);
  return { email, password, token, account: await account.json() };
}
const headers = (user: Actor) => ({ Authorization: 'Bearer ' + user.token });
async function login(page: Page, user: Actor): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Email', { exact: true }).fill(user.email);
  await page.getByLabel('Password', { exact: true }).fill(user.password);
  await page.getByRole('button', { name: 'Sign in to your workspace' }).click();
  await expect(page.locator('.sidebar-nav')).toBeVisible();
}
async function freshInstrument(request: APIRequestContext, user: Actor) {
  const response = await request.get(api + '/market/instruments', { headers: headers(user) });
  expect(response.ok()).toBe(true);
  const markets = (await response.json()) as Array<{
    instrumentId: number;
    symbol: string;
    exchange: string;
    currency: string;
    tradable: boolean;
    latestQuote: null | { quotedAt: string; lastPrice: number; askPrice: number };
  }>;
  const instrument = markets.find(
    (item) =>
      item.tradable &&
      item.currency === 'GBP' &&
      item.latestQuote &&
      item.latestQuote.askPrice > 0 &&
      item.latestQuote.askPrice < 100_000 &&
      Math.abs(
        Date.now() -
          Date.parse(
            /(?:Z|[+-]\d\d:\d\d)$/.test(item.latestQuote.quotedAt)
              ? item.latestQuote.quotedAt
              : item.latestQuote.quotedAt + 'Z',
          ),
      ) < 15_000,
  );
  expect(
    instrument,
    'Start the market-data service and quote synchronizer; a fresh GBP quote is required.',
  ).toBeTruthy();
  return instrument!;
}
async function seedFilledTrade(request: APIRequestContext) {
  const customer = await actor(request, 'CLIENT');
  const funded = await request.post(api + '/accounts/me/funds', {
    headers: headers(customer),
    data: { amount: '1000000.00', idempotencyKey: randomUUID() },
  });
  expect(funded.ok()).toBe(true);
  const instrument = await freshInstrument(request, customer);
  const placed = await request.post(api + '/orders', {
    headers: { ...headers(customer), 'Idempotency-Key': randomUUID() },
    data: { instrumentId: instrument.instrumentId, side: 'BUY', quantity: 1 },
  });
  expect(
    placed.ok(),
    'Fixture order must go through real validation, execution and settlement',
  ).toBe(true);
  await expect
    .poll(async () => {
      const r = await request.get(api + '/accounts/me/blotter', { headers: headers(customer) });
      return (await r.json())[0]?.status;
    })
    .toBe('FILLED');
  const blotter = await request.get(api + '/accounts/me/blotter', { headers: headers(customer) });
  return { customer, instrument, order: (await blotter.json())[0] as { id: number } };
}

test('Joanna: fund, persist a watchlist, receive an alert and place a filled trade', async ({
  page,
  request,
}) => {
  const user = await actor(request, 'CLIENT');
  const instrument = await freshInstrument(request, user);
  await login(page, user);
  await expect(page.locator('.nav-label')).toHaveText(['Dashboard']);
  await page.getByRole('button', { name: 'Expand', exact: true }).click();
  await page.getByLabel('Amount (GBP)', { exact: true }).fill('1000000');
  await page.locator('.funding-controls button').click();
  await expect(page.locator('.funding-success')).toContainText('Added GBP');
  await page.getByRole('tab', { name: 'Watchlist', exact: true }).click();
  await page
    .getByLabel('Add an instrument', { exact: true })
    .selectOption(String(instrument.instrumentId));
  await page.getByRole('button', { name: 'Add to watchlist', exact: true }).click();
  await expect(page.locator('.watchlist-items')).toContainText(instrument.symbol);
  await page.reload();
  await page.getByRole('tab', { name: 'Watchlist', exact: true }).click();
  await expect(page.locator('.watchlist-items')).toContainText(instrument.symbol);
  await page.getByRole('button', { name: 'Price alerts', exact: true }).click();
  await page
    .getByLabel('Instrument', { exact: true })
    .selectOption({
      label: `${instrument.symbol} · ${instrument.exchange} · ${instrument.currency}`,
    });
  await page.getByLabel(/Target price/).fill('0.00000001');
  await page.getByRole('button', { name: 'Create alert', exact: true }).click();
  await expect(page.locator('.notification-history')).toContainText(instrument.symbol, {
    timeout: 30_000,
  });
  await page.getByRole('tab', { name: 'Account overview', exact: true }).click();
  await page.getByLabel('Search instruments', { exact: true }).fill(instrument.symbol);
  await page.locator('.market-table .instrument-link').first().click();
  await page.getByRole('tab', { name: 'Orders', exact: true }).click();
  await page.getByLabel('Quantity', { exact: true }).fill('1');
  await page.locator('.order-panel .submit-btn').click();
  await expect(page.locator('.order-item').first()).toContainText('FILLED', { timeout: 20_000 });
  await page.getByRole('tab', { name: 'Holdings', exact: true }).click();
  await expect(page.locator('.positions-table')).toContainText(instrument.symbol);
  await assertLayout(page);
  const other = await actor(request, 'CLIENT');
  const isolated = await request.get(api + '/watchlist', { headers: headers(other) });
  expect(await isolated.json()).toEqual([]);
});

test('Priya: explain a persisted trade using filtered reporting and metric details', async ({
  page,
  request,
}) => {
  const evidence = await seedFilledTrade(request);
  const analyst = await actor(request, 'ANALYST');
  const today = new Date().toISOString().slice(0, 10);
  await expect
    .poll(
      async () => {
        const response = await request.get(api + '/internal/reporting/summary', {
          headers: headers(analyst),
          params: {
            fromDate: today,
            toDate: today,
            currency: 'GBP',
            accountId: evidence.customer.account.accountId,
          },
        });
        return response.ok() ? (await response.json()).filledOrders : 0;
      },
      { timeout: 20_000 },
    )
    .toBe(1);
  await login(page, analyst);
  await expect(page.locator('.nav-label')).toHaveText(['Dashboard', 'Reports']);
  await page.getByLabel('From date', { exact: true }).fill(today);
  await page.getByLabel('To date', { exact: true }).fill(today);
  await page.getByRole('combobox', { name: /^Currency\b/ }).selectOption('GBP');
  await page.getByRole('button', { name: 'Apply filters', exact: true }).click();
  await expect(page.locator('.analyst-shell')).toHaveAttribute('aria-busy', 'false');
  await expect(page.locator('.applied-scope')).toContainText('GBP');
  await page.locator('.metric-card').filter({ hasText: 'Filled orders' }).click();
  await expect(page.getByRole('dialog')).toContainText('FILLED');
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await assertLayout(page);
});

test('David: find an exact persisted order and inspect its lifecycle and settlement evidence', async ({
  page,
  request,
}) => {
  const evidence = await seedFilledTrade(request);
  const operations = await actor(request, 'OPERATIONS');
  await login(page, operations);
  await expect(page.locator('.nav-label')).toHaveText(['Dashboard', 'Reports', 'Operations']);
  await page.getByLabel('Order ID', { exact: true }).fill(String(evidence.order.id));
  await page.getByRole('button', { name: 'Find ID across all dates', exact: true }).click();
  await page
    .getByRole('button', {
      name: `Inspect order ${evidence.order.id}, ${evidence.instrument.symbol}`,
      exact: true,
    })
    .click();
  await expect(page.locator('.integrity-ok')).toBeVisible();
  await expect(page.locator('#lifecycle-evidence')).toContainText('SUBMITTED');
  await expect(page.locator('#lifecycle-evidence')).toContainText('FILLED');
  await page.getByRole('button', { name: /Execution & quote/ }).click();
  await expect(page.locator('#execution-evidence')).toContainText('GBP');
  await page.getByRole('button', { name: /Settlement/ }).click();
  await expect(page.locator('#settlement-evidence')).toContainText('GBP');
  await assertLayout(page);
});
