import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import {
  AccountResponse,
  AccountService,
  HoldingsResponse,
  OrderResponse,
} from '../core/services/account';
import { MarketDataService, MarketInstrumentResponse } from '../core/services/market-data';
import { OrderService } from '../core/services/order';
import { TradingChatbotComponent } from './trading-chatbot.component';

describe('TradingChatbotComponent', () => {
  let fixture: ComponentFixture<TradingChatbotComponent>;
  let component: TradingChatbotComponent;
  const accounts = { getHoldings: vi.fn() };
  const market = { getInstruments: vi.fn() };
  const orders = { placeOrder: vi.fn() };
  const account: AccountResponse = {
    accountId: 10,
    accountNumber: 'ACC-10',
    currency: 'GBP',
    cashBalance: 10000,
    firstName: 'Test',
    lastName: 'User',
  };
  const holdings: HoldingsResponse = {
    accountId: 10,
    currency: 'GBP',
    cashBalance: 10000,
    positions: [
      { instrumentId: 1, symbol: 'AAPL', instrumentName: 'Apple', quantity: 8 },
      { instrumentId: 2, symbol: 'MSFT', instrumentName: 'Microsoft', quantity: 0 },
    ],
  };
  const stock = (): MarketInstrumentResponse => ({
    instrumentId: 1,
    symbol: 'AAPL',
    name: 'Apple',
    instrumentClass: 'EQUITY',
    currency: 'USD',
    exchange: 'NASDAQ',
    countryCode: 'US',
    tradable: true,
    latestQuote: {
      quoteId: 1,
      instrumentId: 1,
      symbol: 'AAPL',
      bidPrice: 100,
      askPrice: 101,
      lastPrice: 100.5,
      quotedAt: new Date().toISOString(),
    },
  });
  const order: OrderResponse = {
    id: 77,
    side: 'BUY',
    quantity: 5,
    status: 'FILLED',
    rejectionReason: null,
    fillPrice: 102,
    fillCurrency: 'USD',
    accountCurrency: 'GBP',
    fxRate: 0.75,
    settlementAmount: -382.5,
    submittedAt: new Date().toISOString(),
    filledAt: new Date().toISOString(),
  };
  const storageKey = 'leap_pending_chat_order_10';
  const send = (text: string) => {
    component.message = text;
    component.send();
    fixture.detectChanges();
  };
  const transcript = () =>
    component
      .messages()
      .map((message) => message.text)
      .join('\n');

  beforeEach(async () => {
    sessionStorage.clear();
    accounts.getHoldings.mockReset().mockReturnValue(of(holdings));
    market.getInstruments.mockReset().mockReturnValue(of([stock()]));
    orders.placeOrder.mockReset().mockReturnValue(of(order));
    await TestBed.configureTestingModule({
      imports: [TradingChatbotComponent],
      providers: [
        { provide: AccountService, useValue: accounts },
        { provide: MarketDataService, useValue: market },
        { provide: OrderService, useValue: orders },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(TradingChatbotComponent);
    fixture.componentRef.setInput('account', account);
    component = fixture.componentInstance;
    fixture.detectChanges();
    component.isOpen.set(true);
    fixture.detectChanges();
  });
  afterEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  it('asks for quantity when given only a ticker and waits for explicit confirmation', () => {
    send('buy AAPL');
    expect(transcript()).toContain('How many shares');
    expect(market.getInstruments).not.toHaveBeenCalled();
    send('5');
    expect(component.preview()?.request).toEqual({ instrumentId: 1, side: 'BUY', quantity: 5 });
    expect(orders.placeOrder).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('505.00 USD');
    expect(fixture.nativeElement.textContent).toContain('account currency, GBP');
    send('confirm');
    expect(orders.placeOrder).toHaveBeenCalledWith(
      { instrumentId: 1, side: 'BUY', quantity: 5 },
      expect.any(String),
    );
    expect(transcript()).toContain('bought 5 shares of AAPL at 102 USD');
    expect(transcript()).toContain('382.5 GBP');
    expect(component.pending()).toBeNull();
    expect(sessionStorage.getItem(storageKey)).toBeNull();
  });

  it('uses the bid for a sale and refreshes the parent after a persisted result', () => {
    orders.placeOrder.mockReturnValue(of({ ...order, side: 'SELL', quantity: 2 }));
    const changed = vi.fn();
    component.orderChanged.subscribe(changed);
    send('sell AAPL 2');
    expect(component.preview()?.unitPrice).toBe(100);
    expect(orders.placeOrder).not.toHaveBeenCalled();
    component.confirmOrder();
    expect(transcript()).toContain('sold 2 shares of AAPL');
    expect(changed).toHaveBeenCalledWith(expect.objectContaining({ id: 77, side: 'SELL' }));
  });

  it('cancels a draft without sending an order and does not act on negated or compound trades', () => {
    send('buy 5 AAPL');
    send('cancel');
    send('confirm');
    send('do not buy 5 AAPL');
    send('buy 5 AAPL and sell 2 MSFT');
    expect(component.preview()).toBeNull();
    expect(orders.placeOrder).not.toHaveBeenCalled();
  });

  it('fetches holdings every time, excludes closed positions, and renders the account cash', () => {
    send('show my holdings');
    const root: HTMLElement = fixture.nativeElement;
    expect(root.querySelector('table')?.textContent).toContain('AAPL');
    expect(root.querySelector('table')?.textContent).not.toContain('MSFT');
    expect(root.textContent).toContain('10,000.00 GBP');
    expect(root.textContent).toContain('Account snapshot');
    send('how many AAPL shares do I own?');
    expect(accounts.getHoldings).toHaveBeenCalledTimes(2);
    expect(component.messages().at(-1)?.holdings?.positions).toHaveLength(1);
    expect(orders.placeOrder).not.toHaveBeenCalled();
  });

  it('reports an empty ticker holding without inventing a position', () => {
    send('show my holdings for TSLA');
    expect(transcript()).toContain('no holdings in TSLA');
    expect(component.messages().at(-1)?.holdings?.positions).toEqual([]);
  });

  it('does not display a holdings response belonging to another account', () => {
    accounts.getHoldings.mockReturnValue(of({ ...holdings, accountId: 99 }));
    send('show my holdings');
    expect(component.messages().at(-1)?.holdings).toBeUndefined();
    expect(transcript()).toContain('could not be verified');
  });

  it('handles unavailable holdings without inventing a balance or position', () => {
    accounts.getHoldings.mockReturnValue(throwError(() => ({ status: 503 })));
    send('show my holdings');
    expect(component.messages().at(-1)?.holdings).toBeUndefined();
    expect(transcript()).toContain('could not load your holdings');
  });

  it.each(['unknown', 'ambiguous', 'not tradable', 'crypto', 'no quote', 'stale quote'])(
    'blocks a trade with %s',
    (scenario) => {
      const instrument = stock();
      if (scenario === 'not tradable') instrument.tradable = false;
      if (scenario === 'crypto') instrument.instrumentClass = 'CRYPTO';
      if (scenario === 'no quote') instrument.latestQuote = null;
      if (scenario === 'stale quote')
        instrument.latestQuote!.quotedAt = new Date(Date.now() - 60000).toISOString();
      market.getInstruments.mockReturnValue(
        of(
          scenario === 'unknown'
            ? []
            : scenario === 'ambiguous'
              ? [instrument, { ...instrument, instrumentId: 2, exchange: 'OTHER' }]
              : [instrument],
        ),
      );
      send('buy 5 AAPL');
      component.confirmOrder();
      expect(component.preview()).toBeNull();
      expect(orders.placeOrder).not.toHaveBeenCalled();
    },
  );

  it('refreshes an expired preview and requires a new confirmation', () => {
    send('buy 5 AAPL');
    component.preview.update((value) => ({
      ...value!,
      quotedAt: new Date(Date.now() - 11000).toISOString(),
    }));
    component.confirmOrder();
    expect(orders.placeOrder).not.toHaveBeenCalled();
    expect(market.getInstruments).toHaveBeenCalledTimes(2);
    component.confirmOrder();
    expect(orders.placeOrder).toHaveBeenCalledTimes(1);
  });

  it('locks repeated clicks while the trade is in flight', () => {
    const request = new Subject<OrderResponse>();
    orders.placeOrder.mockReturnValue(request);
    send('buy 5 AAPL');
    component.confirmOrder();
    component.confirmOrder();
    send('buy 2 AAPL');
    expect(orders.placeOrder).toHaveBeenCalledTimes(1);
    request.next(order);
    request.complete();
    expect(component.busy()).toBe(false);
  });

  it.each([0, 409, 500, 503])('reuses the same key and payload after failure %s', (status) => {
    orders.placeOrder
      .mockReturnValueOnce(throwError(() => ({ status })))
      .mockReturnValueOnce(of(order));
    send('buy 5 AAPL');
    component.confirmOrder();
    const firstCall = orders.placeOrder.mock.calls[0];
    expect(component.pending()).not.toBeNull();
    send('buy 2 AAPL');
    send('cancel');
    expect(orders.placeOrder).toHaveBeenCalledTimes(1);
    send('show my holdings'); // Read-only questions remain available while the trade is uncertain.
    component.confirmOrder();
    expect(orders.placeOrder.mock.calls[1]).toEqual(firstCall);
    expect(component.pending()).toBeNull();
  });

  it('restores an uncertain order after reload without automatically executing it', () => {
    orders.placeOrder.mockReturnValueOnce(throwError(() => ({ status: 0 })));
    send('buy 5 AAPL');
    component.confirmOrder();
    const firstCall = orders.placeOrder.mock.calls[0];
    fixture.destroy();
    fixture = TestBed.createComponent(TradingChatbotComponent);
    fixture.componentRef.setInput('account', account);
    component = fixture.componentInstance;
    fixture.detectChanges();
    expect(component.pending()).not.toBeNull();
    expect(component.isOpen()).toBe(true);
    expect(orders.placeOrder).toHaveBeenCalledTimes(1);
    market.getInstruments.mockReturnValue(of([])); // A retry does not depend on a new quote or symbol lookup.
    component.confirmOrder();
    expect(orders.placeOrder.mock.calls[1]).toEqual(firstCall);
  });

  it.each(['Insufficient cash balance', 'Insufficient holding of this instrument'])(
    'reports a rejected order: %s',
    (reason) => {
      orders.placeOrder.mockReturnValue(
        throwError(() => ({
          status: 400,
          error: { ...order, status: 'REJECTED', rejectionReason: reason, fillPrice: null },
        })),
      );
      send('buy 5 AAPL');
      component.confirmOrder();
      expect(transcript()).toContain('was rejected');
      expect(transcript()).not.toContain('Order #77 filled');
      expect(component.pending()).toBeNull();
      expect(sessionStorage.getItem(storageKey)).toBeNull();
    },
  );

  it('never presents an accepted but unfinished order as filled', () => {
    orders.placeOrder.mockReturnValue(of({ ...order, status: 'ACCEPTED', fillPrice: null }));
    send('buy 5 AAPL');
    component.confirmOrder();
    expect(transcript()).toContain('still processing');
    expect(transcript()).not.toContain('Order #77 filled');
    expect(component.pending()).not.toBeNull();
  });

  it('fails closed when session storage cannot save a confirmed request', () => {
    send('buy 5 AAPL');
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    component.confirmOrder();
    expect(orders.placeOrder).not.toHaveBeenCalled();
    expect(transcript()).toContain('Enable session storage');
  });

  it('clears the conversation and draft on account change and ignores a late read response', () => {
    send('buy 5 AAPL');
    const response = new Subject<HoldingsResponse>();
    accounts.getHoldings.mockReturnValue(response);
    send('show my holdings');
    fixture.componentRef.setInput('account', { ...account, accountId: 99 });
    fixture.detectChanges();
    response.next(holdings);
    response.complete();
    fixture.detectChanges();
    expect(component.preview()).toBeNull();
    expect(component.pending()).toBeNull();
    expect(component.messages().some((message) => message.role === 'user')).toBe(false);
    expect(component.messages().some((message) => message.holdings)).toBe(false);
  });

  it('retains the draft when background polling updates the same account', () => {
    send('buy 5 AAPL');
    fixture.componentRef.setInput('account', { ...account, cashBalance: 500 });
    fixture.detectChanges();
    expect(component.preview()?.request.quantity).toBe(5);
  });

  it('has a labelled composer and keeps user markup as plain text', () => {
    send('<img src=x onerror=alert(1)>');
    const root: HTMLElement = fixture.nativeElement;
    expect(root.querySelector('label[for="trading-chat-message"]')).not.toBeNull();
    expect(root.querySelector('[role="log"]')).not.toBeNull();
    expect(root.querySelector('img')).toBeNull();
    expect(root.textContent).toContain('<img src=x onerror=alert(1)>');
  });
});
