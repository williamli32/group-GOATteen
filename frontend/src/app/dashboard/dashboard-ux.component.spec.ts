import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { NEVER, of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { DashboardComponent } from './dashboard.component';
import { AccountService, OrderResponse } from '../core/services/account';
import { MarketDataService, MarketInstrumentResponse } from '../core/services/market-data';
import { OrderService } from '../core/services/order';
import { WatchlistService } from '../core/services/watchlist';
import { FxRateService, FxQuote } from '../core/services/fx-rate';
import { PriceAlertsService } from '../core/services/price-alerts';
import { Auth } from '../core/auth/auth';

describe('Dashboard trading recovery', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let component: DashboardComponent;
  let instrument: MarketInstrumentResponse;
  const market = { getInstruments: vi.fn() };
  const fx = { quote: vi.fn() };
  const orders = { placeOrder: vi.fn() };
  const account = {
    getMyAccount: vi.fn(), getHoldings: vi.fn(), getBlotter: vi.fn(), getPortfolioHistory: vi.fn()
  };

  function filledOrder(id = 10): OrderResponse {
    return {
      id, side: 'BUY', quantity: 2, status: 'FILLED', rejectionReason: null,
      fillPrice: 75.1, fillCurrency: 'GBP', accountCurrency: 'GBP',
      fxRate: 1, settlementAmount: -150.2,
      submittedAt: '2026-10-21T12:00:00', filledAt: '2026-10-21T12:00:01'
    };
  }

  beforeEach(async () => {
    sessionStorage.removeItem('leap_pending_order_idempotency_key');
    instrument = {
      instrumentId: 1, symbol: 'VOD.L', name: 'Vodafone',
      instrumentClass: 'EQUITY', exchange: 'LSE', countryCode: 'GB',
      currency: 'GBP', tradable: true, dayChange: null,
      latestQuote: {
        quoteId: 1, instrumentId: 1, symbol: 'VOD.L',
        bidPrice: 75, askPrice: 75.1, lastPrice: 75.05,
        quotedAt: new Date().toISOString()
      }
    };
    market.getInstruments.mockReset().mockReturnValue(of([instrument]));
    fx.quote.mockReset().mockImplementation((from: string, to: string) =>
      of({ from, to, rate: 1, asOf: '2026-10-21', provider: 'TEST' }));
    orders.placeOrder.mockReset();
    account.getMyAccount.mockReset().mockReturnValue(of({ accountId: 1, accountNumber: 'REVIEW',
      firstName: 'Review', lastName: 'Customer', cashBalance: 2000, currency: 'GBP' }));
    account.getHoldings.mockReset().mockReturnValue(of({
      accountId: 1, cashBalance: 2000, currency: 'GBP', positions: []
    }));
    account.getBlotter.mockReset().mockReturnValue(of([]));
    account.getPortfolioHistory.mockReset().mockReturnValue(of([]));
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideRouter([]),
        { provide: AccountService, useValue: account },
        { provide: MarketDataService, useValue: market },
        { provide: FxRateService, useValue: fx },
        { provide: OrderService, useValue: orders },
        { provide: WatchlistService, useValue: { getWatchlist: () => of([]) } },
        { provide: PriceAlertsService, useValue: {
          list: () => of([]), notifications: () => of([]), noticeCleared$: NEVER
        } },
        { provide: Auth, useValue: { clearToken: vi.fn(), logoutSession: () => of(void 0) } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    component.selectTab('orders');
  });

  afterEach(() => {
    fixture?.destroy();
    vi.useRealTimers();
    sessionStorage.removeItem('leap_pending_order_idempotency_key');
  });

  it('opens the exact holding from its symbol or name without submitting or replacing a pending order', () => {
    const holding = { instrumentId: 2, symbol: 'HELD', instrumentName: 'Held company', quantity: 3 };
    const live = { ...instrument, instrumentId: 2, symbol: 'HELD', name: 'Held company',
      latestQuote: { ...instrument.latestQuote!, instrumentId: 2, symbol: 'HELD' } };
    component.marketInstruments.set([instrument, live]);
    component.holdings.set({ accountId: 1, currency: 'GBP', cashBalance: 2000, positions: [holding] });
    component.setMarketSearch('Does not match this holding');
    component.selectTab('holdings'); fixture.detectChanges();
    const symbol = fixture.nativeElement.querySelector('.positions-table .symbol-cell button') as HTMLButtonElement;
    symbol.click(); fixture.detectChanges();
    expect(component.activeTab()).toBe('orders');
    expect(component.selectedInstrument()?.instrumentId).toBe(2);
    expect(document.activeElement?.id).toBe('dashboard-tab-orders');
    expect(fixture.nativeElement.querySelector('#dashboard-panel-orders app-instrument-price-chart .instrument-symbol').textContent).toContain('HELD');
    expect(fixture.nativeElement.querySelector('#dashboard-panel-orders app-instrument-price-chart .live-price').textContent).toContain('GBP');
    component.selectTab('holdings'); fixture.detectChanges();
    const name = fixture.nativeElement.querySelector('.positions-table td[data-label="Instrument"] button') as HTMLButtonElement;
    name.click(); fixture.detectChanges();
    expect(component.activeTab()).toBe('orders');
    expect(component.selectedInstrument()?.latestQuote).toEqual(live.latestQuote);
    expect(orders.placeOrder).not.toHaveBeenCalled();
    component.selectTab('holdings');
    component.orderSubmitting.set(true); fixture.detectChanges();
    expect(symbol.disabled).toBe(true);
    component.openHolding({ instrumentId: 1 });
    expect(component.activeTab()).toBe('holdings');
    expect(component.selectedInstrument()?.instrumentId).toBe(2);
    component.orderSubmitting.set(false);
    component.marketInstruments.set([instrument]); fixture.detectChanges();
    expect(name.disabled).toBe(true);
    expect(fixture.nativeElement.querySelector('.positions-table').textContent).toContain('Market details unavailable');
  });

  it('keeps an actionable rejection in the ticket after the popup closes', () => {
    orders.placeOrder.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 400, error: { ...filledOrder(), status: 'REJECTED',
        rejectionReason: 'Insufficient cash balance', fillPrice: null,
        fxRate: null, settlementAmount: null, filledAt: null }
    })));
    component.orderQuantity = 2;
    component.submitOrder();
    for (const notification of component.notificationService.notifications())
      component.notificationService.dismiss(notification.id);
    fixture.detectChanges();
    const feedback = fixture.nativeElement.querySelector('.order-panel [role="alert"]');
    expect(feedback.textContent).toContain('not have enough available cash');
    expect(component.orderOutcomeUnconfirmed()).toBe(false);
    expect(component.orderQuantity).toBe(2);
  });

  it('keeps the confirmed fill visible after the popup closes', () => {
    const filled = filledOrder();
    orders.placeOrder.mockReturnValue(of(filled));
    account.getBlotter.mockReturnValue(of([filled]));
    component.orderQuantity = 2;
    component.submitOrder();
    for (const notification of component.notificationService.notifications())
      component.notificationService.dismiss(notification.id);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.order-success').textContent).toContain('Order #10 filled.');
  });

  it('confirms a lost response using the original order and key, even without a quote', async () => {
    orders.placeOrder.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 0 })));
    component.orderQuantity = 2;
    component.submitOrder();
    const [originalRequest, originalKey] = orders.placeOrder.mock.calls[0];
    expect(component.orderErrorMessage()).not.toMatch(/\brejected\b/i);
    expect(component.orderOutcomeUnconfirmed()).toBe(true);
    component.selectInstrument({ ...instrument, instrumentId: 2, symbol: 'OTHER' });
    component.setOrderSide('SELL');
    expect(component.selectedInstrument()?.instrumentId).toBe(1);
    expect(component.orderSide).toBe('BUY');
    expect(component.orderQuantity).toBe(2);

    market.getInstruments.mockReturnValue(of([]));
    component.loadMarketData(false);
    expect(component.selectedInstrument()?.latestQuote).toBeNull();
    fixture.detectChanges();
    await fixture.whenStable();
    const confirm = fixture.nativeElement.querySelector('.submit-btn') as HTMLButtonElement;
    expect(confirm.textContent).toContain('Confirm previous order');
    expect(confirm.disabled).toBe(false);
    expect((fixture.nativeElement.querySelector('#orderQuantity') as HTMLInputElement).disabled).toBe(true);

    const response = new Subject<OrderResponse>();
    orders.placeOrder.mockReturnValueOnce(response);
    component.submitOrder();
    component.submitOrder();
    expect(orders.placeOrder).toHaveBeenCalledTimes(2);
    expect(orders.placeOrder.mock.calls[1]).toEqual([originalRequest, originalKey]);
    const filled = filledOrder();
    account.getBlotter.mockReturnValue(of([filled]));
    response.next(filled);
    response.complete();
    expect(component.orderOutcomeUnconfirmed()).toBe(false);
    expect(component.orderQuantity).toBeNull();

    market.getInstruments.mockReturnValue(of([instrument]));
    component.loadMarketData(false);
    orders.placeOrder.mockReturnValueOnce(of(filledOrder(11)));
    component.orderQuantity = 2;
    component.submitOrder();
    expect(orders.placeOrder.mock.calls[2][1]).not.toBe(originalKey);
  });

  it.each([409, 503])('treats HTTP %i as unconfirmed and offers confirmation', status => {
    orders.placeOrder.mockReturnValue(throwError(() => new HttpErrorResponse({ status })));
    component.orderQuantity = 2;
    component.submitOrder();
    fixture.detectChanges();
    expect(component.orderQuantity).toBe(2);
    expect(component.orderOutcomeUnconfirmed()).toBe(true);
    expect(component.orderErrorMessage()).not.toMatch(/\brejected\b/i);
    expect(fixture.nativeElement.querySelector('.submit-btn').textContent).toContain('Confirm previous order');
  });

  it('recovers a failed FX estimate on the next refresh for the same instrument', () => {
    const usd = { ...instrument, currency: 'USD', symbol: 'AAPL' };
    market.getInstruments.mockReturnValue(of([usd]));
    fx.quote.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503 })))
      .mockReturnValue(of({ from: 'USD', to: 'GBP', rate: 0.8, asOf: '2026-10-21', provider: 'TEST' }));
    component.selectInstrument(usd);
    expect(component.fxQuote()).toBeNull();
    expect(component.fxErrorMessage()).toContain('Retry');
    component.loadMarketData(false);
    expect(component.fxQuote()?.rate).toBe(0.8);
    expect(component.fxErrorMessage()).toBe('');
  });

  it('offers an FX retry and prevents overlapping requests for the same pair', () => {
    const usd = { ...instrument, currency: 'USD', symbol: 'AAPL' };
    const response = new Subject<FxQuote>();
    fx.quote.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503 })))
      .mockReturnValueOnce(response);
    component.selectInstrument(usd);
    fixture.detectChanges();
    const retry = fixture.nativeElement.querySelector('.estimate-warning button') as HTMLButtonElement;
    retry.click();
    component.refreshFxQuote(true);
    expect(fx.quote.mock.calls.filter(([from]) => from === 'USD')).toHaveLength(2);
    expect(component.fxLoading()).toBe(true);
    response.next({ from: 'USD', to: 'GBP', rate: 0.8, asOf: '2026-10-21', provider: 'TEST' });
    response.complete();
    expect(component.fxLoading()).toBe(false);
    expect(component.fxQuote()?.rate).toBe(0.8);
  });

  it('does not let an old FX response overwrite the newly selected currency pair', () => {
    const oldResponse = new Subject<FxQuote>();
    fx.quote.mockReturnValueOnce(oldResponse).mockReturnValueOnce(of({
      from: 'INR', to: 'GBP', rate: 0.009, asOf: '2026-10-21', provider: 'TEST'
    }));
    component.selectInstrument({ ...instrument, currency: 'USD' });
    component.selectInstrument({ ...instrument, currency: 'INR' });
    oldResponse.next({ from: 'USD', to: 'GBP', rate: 0.8, asOf: '2026-10-21', provider: 'TEST' });
    oldResponse.complete();
    expect(component.fxQuote()?.from).toBe('INR');
    expect(component.fxQuote()?.rate).toBe(0.009);
  });

  it('shows stale prices and failed refreshes beside the estimate, then clears the warning on recovery', () => {
    instrument.latestQuote!.quotedAt = new Date(Date.now() - 60_000).toISOString();
    market.getInstruments.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    component.loadMarketData(false);
    fixture.detectChanges();
    const quoteStatus = () => fixture.nativeElement.querySelector('.order-panel .quote-status');
    expect(quoteStatus().textContent).toContain('Stale quote');
    expect(quoteStatus().textContent).toContain('Market refresh failed');
    expect(quoteStatus().querySelector('button').textContent).toContain('Refresh prices');
    market.getInstruments.mockReturnValue(of([{ ...instrument,
      latestQuote: { ...instrument.latestQuote!, quotedAt: new Date().toISOString() } }]));
    component.loadMarketData(false);
    fixture.detectChanges();
    expect(quoteStatus().textContent).not.toMatch(/Stale quote|Market refresh failed/);
    expect(quoteStatus().textContent).toContain('Quote updated');
  });

  it('ages the visible quote while all background refreshes fail', async () => {
    component.ngOnDestroy();
    vi.useFakeTimers();
    instrument.latestQuote!.quotedAt = new Date().toISOString();
    component.ngOnInit();
    fixture.detectChanges();
    market.getInstruments.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    account.getMyAccount.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    await vi.advanceTimersByTimeAsync(12_002);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.order-panel .quote-status').textContent).toContain('Stale quote');
  });

  it('values USD holdings in a GBP account and exposes missing valuations without inventing zero', () => {
    component.account.set({ accountId: 1, accountNumber: 'GBP', firstName: 'Test', lastName: 'User', cashBalance: 2000, currency: 'GBP' });
    component.holdings.set({ accountId: 1, cashBalance: 2000, currency: 'GBP', positions: [{ instrumentId: 1, symbol: 'AAPL', instrumentName: 'Apple', quantity: 1 }] });
    fx.quote.mockReturnValue(of({ from: 'USD', to: 'GBP', rate: 0.8, asOf: '2026-10-22', provider: 'TEST' }));
    component.marketInstruments.set([{ ...instrument, currency: 'USD', latestQuote: { ...instrument.latestQuote!, lastPrice: 100 } }]);
    fixture.detectChanges();
    expect(component.portfolioValue()).toBe(2080);
    component.marketInstruments.set([]); fixture.detectChanges();
    expect(component.portfolioValue()).toBeNull();
    expect(component.portfolioValuationNote()).toContain('AAPL');
  });

  it('keeps the trading dashboard available when only history fails and offers a working range retry', () => {
    fixture.destroy();
    account.getPortfolioHistory.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    fixture = TestBed.createComponent(DashboardComponent); component = fixture.componentInstance;
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.dashboard-tabs')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.history-error')).toBeNull();
    (Array.from(fixture.nativeElement.querySelectorAll('.portfolio-value-controls button')) as HTMLButtonElement[])
      .find(button => button.textContent?.trim() === '5D')!.click(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.history-error').textContent).toContain('Could not load');
    account.getPortfolioHistory.mockClear();
    component.selectPortfolioHistoryRange(30);
    account.getPortfolioHistory.mockReturnValue(of([{ date: '2026-10-22', value: 2080, cashValue: 2000, holdingsValue: 80 }]));
    component.selectPortfolioHistoryRange(30); fixture.detectChanges();
    expect(account.getPortfolioHistory).toHaveBeenCalledTimes(2);
    expect(component.portfolioHistoryRequestFailed()).toBe(false);
    expect(component.portfolioAppliedDays()).toBe(30);
  });

  it('keeps the applied range and a visible warning when a new history range fails', () => {
    component.selectTab('overview');
    component.portfolioValueHistory.set([{ timestamp: Date.now(), value: 2000 }]);
    account.getPortfolioHistory.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    (Array.from(fixture.nativeElement.querySelectorAll('.portfolio-value-controls button')) as HTMLButtonElement[])
      .find(button => button.textContent?.trim() === '1M')!.click(); fixture.detectChanges();
    expect(component.portfolioAppliedDays()).toBe(5);
    const panel = fixture.nativeElement.querySelector('.portfolio-value-panel');
    expect(panel.querySelector('.history-error').textContent).toContain('Still showing 5D');
    expect(panel.querySelector('.portfolio-range-caption').textContent).toContain('5D');
    expect(component.portfolioValueHistory()[0].value).toBe(2000);
  });

  it('preserves a missing historical valuation as a gap rather than coercing null to zero', () => {
    account.getPortfolioHistory.mockReturnValue(of([{ date: '2026-10-22', value: null, cashValue: 2000, holdingsValue: null, unavailable: ['AAPL'] }]));
    component.refreshPortfolioHistory();
    expect(component.portfolioValueHistory()[0].value).toBeNull();
    expect(component.portfolioValueHistory()[0].unavailable).toEqual(['AAPL']);
  });

  it('samples the complete portfolio on new stock quotes and moves the live line without a history request', () => {
    const start = component.quoteClock();
    component.holdings.set({ accountId: 1, currency: 'GBP', cashBalance: 2000,
      positions: [{ instrumentId: 1, symbol: 'VOD.L', instrumentName: 'Vodafone', quantity: 2 }] });
    fixture.detectChanges();
    const first = component.portfolioLiveHistory()!.points.at(-1)!;
    expect(first.value).toBeCloseTo(2150.1);
    const calls = account.getPortfolioHistory.mock.calls.length;
    component.quoteClock.set(start + 3000);
    instrument = { ...instrument, latestQuote: { ...instrument.latestQuote!, quoteId: 2,
      lastPrice: 77.05, quotedAt: new Date(start + 3000).toISOString() } };
    market.getInstruments.mockReturnValue(of([instrument]));
    component.loadMarketData(false); fixture.detectChanges();
    const points = component.portfolioLiveHistory()!.points;
    expect(points.at(-1)?.value).toBeCloseTo(2154.1);
    expect(points).toHaveLength(2);
    expect(account.getPortfolioHistory.mock.calls.length).toBe(calls);
    component.selectTab('overview'); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.value-line')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.portfolio-range-caption').textContent).toContain('Live');
  });

  it('uses account-currency FX for live observations and includes cash changes', () => {
    const start = component.quoteClock();
    component.holdings.set({ accountId: 1, currency: 'GBP', cashBalance: 2000,
      positions: [{ instrumentId: 1, symbol: 'AAPL', instrumentName: 'Apple', quantity: 2 }] });
    fx.quote.mockReturnValue(of({ from: 'USD', to: 'GBP', rate: .8, asOf: '2026-10-27', provider: 'TEST' }));
    component.marketInstruments.set([{ ...instrument, currency: 'USD',
      latestQuote: { ...instrument.latestQuote!, lastPrice: 100 } }]);
    fixture.detectChanges();
    expect(component.portfolioLiveHistory()?.points.at(-1)?.value).toBe(2160);
    component.quoteClock.set(start + 3000);
    component.account.update(value => ({ ...value!, cashBalance: 2100 }));
    component.holdings.update(value => ({ ...value!, cashBalance: 2100 }));
    fixture.detectChanges();
    expect(component.portfolioLiveHistory()?.points.at(-1)?.value).toBe(2260);
  });

  it('records a gap for missing or stale held-stock prices and resumes with a line break', () => {
    const start = component.quoteClock();
    component.holdings.set({ accountId: 1, currency: 'GBP', cashBalance: 2000,
      positions: [{ instrumentId: 1, symbol: 'VOD.L', instrumentName: 'Vodafone', quantity: 2 }] });
    fixture.detectChanges();
    component.quoteClock.set(start + 15000); fixture.detectChanges();
    expect(component.portfolioLiveHistory()?.points.at(-1)?.value).toBeNull();
    expect(component.portfolioLiveHistory()?.points.at(-1)?.unavailable).toContain('Current stock prices');
    component.quoteClock.set(start + 18000);
    component.marketInstruments.set([{ ...instrument, latestQuote: { ...instrument.latestQuote!, quoteId: 2,
      lastPrice: 80, quotedAt: new Date(start + 18000).toISOString() } }]);
    fixture.detectChanges();
    expect(component.portfolioLiveHistory()?.points.at(-1)?.value).toBe(2160);
    expect(component.portfolioLiveHistory()?.points.at(-1)?.breakBefore).toBe(true);
  });

  it('pauses live values when an account refresh fails and never mixes account identities', () => {
    account.getHoldings.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    component.loadDashboard(false); fixture.detectChanges();
    expect(component.portfolioLiveHistory()?.points.at(-1)?.value).toBeNull();
    component.account.update(value => ({ ...value!, accountId: 99 })); fixture.detectChanges();
    expect(component.portfolioLiveHistory()).toBeNull();
  });

  it.each([1, 2])('refreshes positions after selling %s of two shares and hides only fully closed holdings', quantity => {
    const holding = { instrumentId: 1, symbol: 'VOD.L', instrumentName: 'Vodafone', quantity: 2 };
    account.getHoldings.mockReturnValue(of({ accountId: 1, currency: 'GBP', cashBalance: 2000, positions: [holding] }));
    component.loadDashboard(false); component.selectTab('holdings'); fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.position-row')).toHaveLength(1);
    const closed = { ...holding, quantity: 2 - quantity };
    const result = { ...filledOrder(), side: 'SELL', quantity, fillPrice: 75, settlementAmount: 75 * quantity };
    account.getHoldings.mockReturnValue(of({ accountId: 1, currency: 'GBP', cashBalance: 2000 + 75 * quantity, positions: [closed] }));
    account.getMyAccount.mockReturnValue(of({ accountId: 1, accountNumber: 'REVIEW', firstName: 'Test', lastName: 'User',
      cashBalance: 2000 + 75 * quantity, currency: 'GBP' }));
    account.getBlotter.mockReturnValue(of([result])); orders.placeOrder.mockReturnValue(of(result));
    component.orderSide = 'SELL'; component.orderQuantity = quantity; component.submitOrder(); fixture.detectChanges();
    expect(component.openPositions()).toHaveLength(quantity === 2 ? 0 : 1);
    expect(fixture.nativeElement.querySelectorAll('.position-row')).toHaveLength(quantity === 2 ? 0 : 1);
    expect(fixture.nativeElement.querySelector('.positions-section .panel-heading').textContent).toContain(`${quantity === 2 ? 0 : 1} instruments`);
    expect(component.orders()[0].side).toBe('SELL');
    if (quantity === 2) expect(fixture.nativeElement.querySelector('.positions-table').textContent).toContain('No positions currently held');
  });

  it('keeps other open positions visible, updates the overview count, and shows a closed stock again after repurchase', () => {
    const closed = { instrumentId: 1, symbol: 'VOD.L', instrumentName: 'Vodafone', quantity: 0 };
    const active = { instrumentId: 2, symbol: 'AAPL', instrumentName: 'Apple', quantity: 3 };
    component.holdings.set({ accountId: 1, currency: 'GBP', cashBalance: 2000, positions: [closed, active] });
    component.selectTab('holdings'); fixture.detectChanges();
    const table: HTMLElement = fixture.nativeElement.querySelector('.positions-table');
    expect(table.textContent).not.toContain('VOD.L'); expect(table.textContent).toContain('AAPL');
    component.selectTab('overview'); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.account-summary-grid').textContent).toContain('1 instruments');
    component.holdings.update(value => ({ ...value!, positions: [{ ...closed, quantity: 1 }, active] }));
    component.selectTab('holdings'); fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.position-row')).toHaveLength(2);
    expect(fixture.nativeElement.querySelector('.positions-table').textContent).toContain('VOD.L');
  });
});
