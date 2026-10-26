import { expect, type Page } from 'playwright/test';

export type Role = 'CLIENT' | 'ANALYST' | 'OPERATIONS';
export async function assertLayout(page: Page): Promise<void> {
  await expect(page.locator('main')).toHaveCount(1);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(
    true,
  );
  for (const control of await page.locator('input,select,button[type="submit"]').all()) {
    if (!(await control.isVisible())) continue;
    const box = await control.boundingBox();
    expect(box!.x).toBeGreaterThanOrEqual(-1);
    expect(box!.x + box!.width).toBeLessThanOrEqual(page.viewportSize()!.width + 1);
  }
}

/** HTTP-contract fixtures for browser layout/access checks; not database evidence. */
export async function mockWorkspace(page: Page, role: Role) {
  const date = new Date().toISOString().slice(0, 10);
  const time = () => new Date().toISOString();
  const token =
    'test.' +
    Buffer.from(JSON.stringify({ sub: 'goal5@example.test', roles: [role] })).toString(
      'base64url',
    ) +
    '.signature';
  const market = [
    {
      instrumentId: 1,
      symbol: 'VOD.L',
      name: 'Vodafone',
      exchange: 'LSE',
      instrumentClass: 'EQUITY',
      countryCode: 'GB',
      currency: 'GBP',
      tradable: true,
      latestQuote: {
        quoteId: 99,
        instrumentId: 1,
        symbol: 'VOD.L',
        lastPrice: 100,
        askPrice: 100,
        bidPrice: 99,
        quotedAt: time(),
      },
      dayChange: null,
    },
  ];
  const state = {
    historyFailed: false,
    quotePrice: 100,
    quoteId: 99,
    cash: 10_000,
    watched: [] as number[],
    alerts: [] as Record<string, unknown>[],
    notices: [] as Record<string, unknown>[],
    orders: [] as Record<string, unknown>[],
  };
  const order = {
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
    submittedAt: time(),
    filledAt: time(),
  };
  const summary = {
    totalOrders: 12,
    filledOrders: 12,
    rejectedOrders: 0,
    activeClients: 12,
    totalExecutedQuantity: 12,
    totalTradedValue: 1200,
    buyFills: 12,
    sellFills: 0,
    buyQuantity: 12,
    sellQuantity: 0,
    buyTradedValue: 1200,
    sellTradedValue: 0,
    fillRate: 100,
    rejectionRate: 0,
  };
  const previous = Object.fromEntries(Object.keys(summary).map((key) => [key, 0]));
  const audit = {
    pending: false,
    integrity: { orderId: 77, valid: true, message: 'Trade integrity verified' },
    evidence: {
      order: { orderId: 77, side: 'BUY', quantity: 1, submittedAt: time() },
      account: { accountId: 23, currency: 'GBP' },
      instrument: { instrumentId: 1, symbol: 'VOD.L', currency: 'GBP' },
      currentStatus: 'FILLED',
      statusHistory: ['SUBMITTED', 'ACCEPTED', 'FILLED'].map((status, i) => ({
        historyId: i + 1,
        status,
        changedAt: time(),
        note: 'Recorded lifecycle',
      })),
      rejectionReason: null,
      completedAt: time(),
      execution: {
        fillId: 88,
        fillQuantity: 1,
        executionPrice: 100,
        executedAt: time(),
        quoteId: 99,
        bidPrice: 99,
        askPrice: 100,
        quotedAt: time(),
        fxSourceCurrency: 'GBP',
        fxTargetCurrency: 'GBP',
        fxRate: 1,
        fxRateDate: date,
        fxProvider: 'SAME_CURRENCY',
        cashSettlementAmount: 100,
      },
      cashImpact: {
        transactions: [
          {
            transactionId: 9,
            accountId: 23,
            fillId: 88,
            amount: -100,
            balanceAfter: 9900,
            createdAt: time(),
            description: 'BUY settlement',
          },
        ],
        totalMovement: -100,
        balanceAfter: 9900,
      },
      positionImpact: {
        quantityBefore: 0,
        quantityAfter: 1,
        quantityChange: 1,
        recordedAt: time(),
      },
      fullyReconstructable: true,
    },
  };
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();
    const headers = {
      'access-control-allow-origin': 'http://localhost:4200',
      'access-control-allow-credentials': 'true',
      'access-control-allow-headers':
        request.headers()['access-control-request-headers'] ??
        'authorization,content-type,idempotency-key',
      'access-control-allow-methods': 'GET,POST,PUT,PATCH,DELETE,OPTIONS',
    };
    const reply = (json: unknown, status = 200) => route.fulfill({ status, headers, json });
    const empty = () => route.fulfill({ status: 204, headers });
    if (method === 'OPTIONS') return empty();
    if (path === '/api/auth/csrf') return reply({ headerName: 'X-XSRF-TOKEN', token: 'test-csrf' });
    if (path === '/api/auth/login' || path === '/api/auth/refresh')
      return reply({ accessToken: token });
    if (path === '/api/auth/logout') return empty();
    if (path === '/api/auth/account-currencies')
      return reply(['USD', 'GBP', 'INR'].map((code) => ({ code, name: code, minorUnits: 2 })));
    if (path === '/api/accounts/me')
      return reply({
        accountId: 1,
        accountNumber: 'GOAL5-TEST',
        firstName: 'Goal',
        lastName: 'Five',
        cashBalance: state.cash,
        currency: 'GBP',
      });
    if (path === '/api/accounts/me/holdings')
      return reply({
        accountId: 1,
        currency: 'GBP',
        cashBalance: state.cash,
        positions: state.orders.length
          ? [
              {
                instrumentId: 1,
                symbol: 'VOD.L',
                instrumentName: 'Vodafone',
                quantity: state.orders.length,
              },
            ]
          : [],
      });
    if (path === '/api/accounts/me/blotter') return reply(state.orders);
    if (path === '/api/accounts/me/portfolio-history')
      return state.historyFailed
        ? reply({ message: 'History unavailable' }, 503)
        : reply([
            {
              date,
              value: state.cash + state.orders.length * 100,
              cashValue: state.cash,
              holdingsValue: state.orders.length * 100,
              unavailable: [],
            },
          ]);
    if (path.startsWith('/api/accounts/me/funds')) {
      const body = request.postDataJSON();
      const withdraw = path.endsWith('/withdraw');
      const amount = Number(body.amount) * (withdraw ? -1 : 1);
      state.cash += amount;
      return reply({
        accountId: 1,
        transactionId: 1,
        amount,
        currency: 'GBP',
        cashBalance: state.cash,
        createdAt: time(),
        replayed: false,
      });
    }
    if (path === '/api/market/instruments') {
      market[0].latestQuote.quoteId = state.quoteId;
      market[0].latestQuote.lastPrice = state.quotePrice;
      market[0].latestQuote.askPrice = state.quotePrice;
      market[0].latestQuote.bidPrice = state.quotePrice - 1;
      market[0].latestQuote.quotedAt = time();
      return reply(market);
    }
    if (path.startsWith('/api/fx'))
      return reply({
        from: url.searchParams.get('from') ?? 'GBP',
        to: url.searchParams.get('to') ?? 'GBP',
        rate: 1,
        asOf: date,
        provider: 'TEST',
      });
    if (path === '/api/orders' && method === 'POST') {
      state.cash -= 100;
      state.orders.push(order);
      return reply(order);
    }
    if (path === '/api/watchlist')
      return reply(market.filter((i) => state.watched.includes(i.instrumentId)));
    if (path.startsWith('/api/watchlist/')) {
      const id = Number(path.split('/').pop());
      state.watched =
        method === 'PUT'
          ? [...new Set([...state.watched, id])]
          : state.watched.filter((item) => item !== id);
      return empty();
    }
    if (path === '/api/price-alerts/notifications')
      return method === 'DELETE' ? ((state.notices = []), empty()) : reply(state.notices);
    if (path.startsWith('/api/price-alerts/notifications/')) {
      state.notices = [];
      return empty();
    }
    if (path === '/api/price-alerts' && method === 'POST') {
      const body = request.postDataJSON();
      const alert = {
        ...body,
        id: 3,
        symbol: 'VOD.L',
        currency: 'GBP',
        armed: false,
        generation: 1,
        triggeredAt: time(),
      };
      state.alerts.push(alert);
      state.notices.push({
        id: 4,
        alertId: 3,
        instrumentId: 1,
        symbol: 'VOD.L',
        direction: body.direction,
        threshold: body.threshold,
        observedPrice: 100,
        currency: 'GBP',
        quoteAt: time(),
        createdAt: time(),
        readAt: null,
      });
      return reply(alert, 201);
    }
    if (path === '/api/price-alerts') return reply(state.alerts);
    if (path.startsWith('/api/internal/reporting/')) {
      const kind = path.split('/').pop();
      const fromDate = url.searchParams.get('fromDate') ?? date;
      const toDate = url.searchParams.get('toDate') ?? date;
      const days = Math.round((Date.parse(toDate) - Date.parse(fromDate)) / 86_400_000) + 1;
      const priorTo = new Date(Date.parse(fromDate) - 86_400_000).toISOString().slice(0, 10);
      const priorFrom = new Date(Date.parse(fromDate) - days * 86_400_000)
        .toISOString()
        .slice(0, 10);
      const currency = url.searchParams.get('currency') ?? 'GBP';
      if (kind === 'summary') return reply(date >= fromDate && date <= toDate ? summary : previous);
      const active = {
        instrumentId: 1,
        symbol: 'VOD.L',
        fillCount: 12,
        previousFillCount: 0,
        changePercent: null,
        newlyActive: true,
      };
      if (kind === 'insights')
        return reply({
          currentWindow: { fromDate, toDate },
          previousWindow: { fromDate: priorFrom, toDate: priorTo },
          currency,
          current: summary,
          previous,
          currentFillCount: 12,
          previousFillCount: 0,
          volumeChangePercent: null,
          buySharePercent: 100,
          sellSharePercent: 0,
          mostActiveInstrument: active,
          fastestGrowingInstrument: null,
          newlyActiveInstruments: [active],
          highestVolumeDay: { date, fillCount: 12 },
          rejectionRateChangePercentagePoints: null,
          rejectionTrend: [{ date, totalOrders: 12, rejectedOrders: 0, rejectionRate: 0 }],
        });
      if (kind === 'instruments')
        return reply([
          {
            instrumentId: 1,
            instrumentSymbol: 'VOD.L',
            currency,
            fillCount: 12,
            executedQuantity: 12,
            tradedValue: 1200,
          },
        ]);
      if (kind === 'clients')
        return reply(
          Array.from({ length: 12 }, (_, i) => ({
            clientId: 11 + i,
            clientName: 'Client ' + (i + 1),
            currency,
            orderCount: 1,
            fillCount: 1,
            executedQuantity: 1,
            tradedValue: 100,
          })),
        );
      if (kind === 'activity')
        return reply(
          Array.from({ length: 12 }, (_, i) => ({
            orderId: 77 + i,
            fillId: 88 + i,
            accountId: 23 + i,
            clientId: 11 + i,
            instrumentId: 1,
            instrumentSymbol: 'VOD.L',
            side: 'BUY',
            status: 'FILLED',
            orderQuantity: 1,
            executionQuantity: 1,
            executionPrice: 100,
            tradedValue: 100,
            occurredAt: time(),
          })),
        );
      if (kind === 'trends')
        return reply([{ date, ...summary, executedQuantity: 12, tradedValue: 1200 }]);
      return reply({ message: 'Unmocked reporting contract: ' + kind }, 404);
    }
    if (path.endsWith('/77/audit')) return reply(audit);
    if (path === '/api/internal/operations/orders')
      return reply([
        {
          orderId: 77,
          accountId: 23,
          accountNumber: 'EVIDENCE-23',
          clientId: 11,
          clientName: 'Joanna Test',
          instrumentId: 1,
          instrumentSymbol: 'VOD.L',
          side: 'BUY',
          status: 'FILLED',
          quantity: 1,
          submittedAt: time(),
        },
      ]);
    return reply({ message: 'Unmocked API contract: ' + path }, 404);
  });
  await page.goto('/login');
  await page.getByLabel('Email', { exact: true }).fill('goal5@example.test');
  await page.getByLabel('Password', { exact: true }).fill('Goal5-Test-Password');
  await page.getByRole('button', { name: 'Sign in to your workspace' }).click();
  await expect(page.locator('.sidebar-nav')).toBeVisible();
  return state;
}
