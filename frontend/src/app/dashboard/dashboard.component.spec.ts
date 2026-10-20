
import {
    ComponentFixture,
    TestBed
} from '@angular/core/testing';

import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { AccountFundingComponent } from './account-funding.component';
import { WatchlistComponent } from './watchlist.component';
import { WatchlistService } from '../core/services/watchlist';
import { DailyPriceChange } from '../core/services/market-data';
import { FxRateService } from '../core/services/fx-rate';
import { PriceAlertsService } from '../core/services/price-alerts';

import {
    HttpErrorResponse
} from '@angular/common/http';

import {
    provideRouter
} from '@angular/router';

import {
    Observable,
    NEVER,
    Subject,
    of,
    throwError
} from 'rxjs';

import {
    DashboardComponent
} from './dashboard.component';

import {
    AccountService,
    AccountResponse,
    AddFundsResponse,
    HoldingsResponse,
    OrderResponse
} from '../core/services/account';

import {
    MarketDataService
} from '../core/services/market-data';

import {
    OrderService,
    PlaceOrderRequest
} from '../core/services/order';

import {
    Auth
} from '../core/auth/auth';


describe('DashboardComponent', () => {

    let component: DashboardComponent;

    let lastIdempotencyKey: string | null;

    let fixture: ComponentFixture<DashboardComponent>;

    let placeOrderCallCount: number;

    let lastPlaceOrderRequest: PlaceOrderRequest | null;

    let placeOrderResult: Observable<OrderResponse>;

    let filledOrder: OrderResponse;

    /*
     * Mutable backend responses let tests simulate
     * account and portfolio changes after execution.
     */
    let mockAccount: AccountResponse;

    let mockHoldings: HoldingsResponse;

    let mockBlotter: OrderResponse[];
    let marketDayChange: DailyPriceChange | null;


    const accountServiceMock = {

        addFunds: vi.fn<(amount: string, idempotencyKey: string) => Observable<AddFundsResponse>>(),

        getMyAccount: () =>
            of(mockAccount),

        getHoldings: () =>
            of(mockHoldings),

        getBlotter: () =>
            of(mockBlotter)

    };


    const marketDataServiceMock = {

        getInstruments: () =>
            of([
                {
                    instrumentId: 1,

                    symbol: 'VOD.L',

                    name: 'Vodafone Group Plc',

                    instrumentClass: 'EQUITY_UK',

                    currency: 'GBP',

                    tradable: true,
                    dayChange: marketDayChange,

                    latestQuote: {
                        quoteId: 100,

                        instrumentId: 1,

                        symbol: 'VOD.L',

                        bidPrice: 75,

                        askPrice: 75.1,

                        lastPrice: 75.05,

                        quotedAt:
                            '2026-09-15T14:00:00'
                    }
                }
            ])



    };


    const orderServiceMock = {

        placeOrder: (
            request: PlaceOrderRequest,
            idempotencyKey: string
        ) => {

            placeOrderCallCount++;

            lastPlaceOrderRequest =
                request;

            lastIdempotencyKey =
                idempotencyKey;

            return placeOrderResult;

        }

    };


    const authMock = {

        clearToken: () => { },

        logoutSession: () =>
            of(void 0)

    };


    beforeEach(async () => {

        accountServiceMock.addFunds.mockReset();
        sessionStorage.removeItem('leap_pending_funding_1');
        marketDayChange = null;
        placeOrderCallCount = 0;

        lastPlaceOrderRequest = null;

        lastIdempotencyKey = null;


        /*
         * Initial account state before trading.
         */
        mockAccount = {
            accountId: 1,

            accountNumber: 'LEAP-TEST123',

            cashBalance: 2000,

            currency: 'GBP',

            firstName: 'Test',

            lastName: 'Trader'
        };


        mockHoldings = {
            accountId: 1,

            cashBalance: 2000,

            currency: 'GBP',

            positions: []
        };


        mockBlotter = [];


        /*
         * Sprint 4 successful execution response.
         *
         * BUY 2 shares at an ask price of £75.10.
         */
        filledOrder = {
            id: 20,

            side: 'BUY',

            quantity: 2,

            status: 'FILLED',

            rejectionReason: null,

            fillPrice: 75.1,

            fillCurrency: 'GBP',
            accountCurrency: 'GBP',
            fxRate: 1,
            settlementAmount: -150.2,

            submittedAt:
                '2026-09-15T14:00:00',

            filledAt:
                '2026-09-15T14:00:01'
        };


        placeOrderResult = of(filledOrder);


        await TestBed
            .configureTestingModule({

                imports: [
                    DashboardComponent
                ],

                providers: [

                    provideRouter([]),

                    {
                        provide: AccountService,
                        useValue: accountServiceMock
                    },

                    {
                        provide: MarketDataService,
                        useValue: marketDataServiceMock
                    },
                    {
                        provide: WatchlistService,
                        useValue: { getWatchlist: () => of([]) }
                    },
                    {
                        provide: FxRateService,
                        useValue: {
                            quote: (from: string, to: string) => of({
                                from, to, rate: 1,
                                asOf: '2026-09-15T14:00:00', provider: 'TEST'
                            })
                        }
                    },
                    {
                        provide: PriceAlertsService,
                        useValue: {
                            list: () => of([]),
                            notifications: () => of([]),
                            noticeCleared$: NEVER
                        }
                    },

                    {
                        provide: OrderService,
                        useValue: orderServiceMock
                    },

                    {
                        provide: Auth,
                        useValue: authMock
                    }

                ]

            })
            .compileComponents();


        fixture = TestBed.createComponent(
            DashboardComponent
        );

        component = fixture.componentInstance;

        fixture.detectChanges();

    });


    it('opens one labelled dashboard panel at a time with matching tab state', () => {
        const root: HTMLElement = fixture.nativeElement;
        const tabs = Array.from(root.querySelectorAll<HTMLButtonElement>('[role="tab"]'));
        expect(tabs.map(tab => tab.textContent?.trim())).toEqual(['Account overview', 'Watchlist', 'Orders', 'Holdings']);
        expect(component.activeTab()).toBe('overview');
        for (const tab of tabs) {
            tab.click();
            fixture.detectChanges();
            const panels = Array.from(root.querySelectorAll<HTMLElement>('[role="tabpanel"]'));
            expect(panels.filter(panel => !panel.hidden).map(panel => panel.id)).toEqual([tab.getAttribute('aria-controls')]);
            expect(root.querySelector('[role="tabpanel"]:not([hidden])')?.getAttribute('aria-labelledby')).toBe(tab.id);
            expect(tab.getAttribute('aria-selected')).toBe('true');
            expect(tabs.filter(item => item.tabIndex === 0)).toEqual([tab]);
        }
    });

    it('supports arrow, Home and End keys with focus following the selected tab', () => {
        const root: HTMLElement = fixture.nativeElement;
        const key = (value: string) => {
            const selected = root.querySelector<HTMLButtonElement>('[role="tab"][aria-selected="true"]')!;
            selected.dispatchEvent(new KeyboardEvent('keydown', { key: value, bubbles: true, cancelable: true }));
            fixture.detectChanges();
            expect(document.activeElement).toBe(root.querySelector('[role="tab"][aria-selected="true"]'));
        };
        key('ArrowLeft');
        expect(component.activeTab()).toBe('holdings');
        key('ArrowRight');
        expect(component.activeTab()).toBe('overview');
        key('End');
        expect(component.activeTab()).toBe('holdings');
        key('Home');
        expect(component.activeTab()).toBe('overview');
        key('ArrowRight');
        expect(component.activeTab()).toBe('watchlist');
    });

    it('opens the order ticket when a market symbol is selected', () => {
        const root: HTMLElement = fixture.nativeElement;
        root.querySelector<HTMLButtonElement>('.instrument-link')!.click();
        fixture.detectChanges();
        expect(component.activeTab()).toBe('orders');
        expect(root.querySelector<HTMLElement>('#dashboard-panel-orders')!.hidden).toBe(false);
        expect(root.querySelector('.ticket-instrument')?.textContent).toContain('VOD.L');
        expect(document.activeElement?.id).toBe('dashboard-tab-orders');
    });

    it('opens the order ticket when a saved watchlist instrument is selected', () => {
        const watchlist = fixture.debugElement.query(By.directive(WatchlistComponent)).componentInstance as WatchlistComponent;
        watchlist.savedInstruments.set(component.marketInstruments());
        component.selectTab('watchlist');
        fixture.detectChanges();
        fixture.nativeElement.querySelector('.instrument-button').click();
        fixture.detectChanges();
        expect(component.activeTab()).toBe('orders');
        expect(component.selectedInstrument()?.symbol).toBe('VOD.L');
    });

    it('preserves order, funding and watchlist drafts when moving between tabs', async () => {
        const funding = fixture.debugElement.query(By.directive(AccountFundingComponent)).componentInstance as AccountFundingComponent;
        const watchlist = fixture.debugElement.query(By.directive(WatchlistComponent)).componentInstance as WatchlistComponent;
        funding.open();
        funding.amount = '10000';
        watchlist.addId = '1';
        component.orderSide = 'SELL';
        component.orderQuantity = 12;
        for (const tab of component.dashboardTabs) component.selectTab(tab.id);
        component.selectTab('orders');
        fixture.detectChanges();
        await fixture.whenStable();
        expect((fixture.nativeElement.querySelector('#orderQuantity') as HTMLInputElement).value).toBe('12');
        expect(fixture.nativeElement.querySelector('.sell-btn').getAttribute('aria-pressed')).toBe('true');
        expect(fixture.debugElement.query(By.directive(AccountFundingComponent)).componentInstance).toBe(funding);
        expect(funding.isOpen()).toBe(true);
        expect(funding.amount).toBe('10000');
        expect(fixture.debugElement.query(By.directive(WatchlistComponent)).componentInstance).toBe(watchlist);
        expect(watchlist.addId).toBe('1');
    });

    it('completes an in-flight order while another tab is open without changing its instrument or resubmitting', () => {
        const response = new Subject<OrderResponse>();
        placeOrderResult = response;
        const instrument = component.selectedInstrument()!;
        component.selectTab('orders');
        component.orderQuantity = 2;
        component.submitOrder();
        component.selectTab('holdings');
        component.selectInstrument({ ...instrument, instrumentId: 99, symbol: 'OTHER' });
        expect(component.activeTab()).toBe('holdings');
        expect(component.selectedInstrument()?.instrumentId).toBe(instrument.instrumentId);
        mockAccount = { ...mockAccount, cashBalance: 1849.8 };
        mockHoldings = { ...mockHoldings, cashBalance: 1849.8, positions: [{ instrumentId: 1, symbol: 'VOD.L', instrumentName: 'Vodafone Group Plc', quantity: 2 }] };
        mockBlotter = [filledOrder];
        response.next(filledOrder);
        response.complete();
        fixture.detectChanges();
        expect(component.activeTab()).toBe('holdings');
        expect(component.holdings()?.positions[0].quantity).toBe(2);
        expect(component.orders()[0].status).toBe('FILLED');
        expect(component.orderSubmitting()).toBe(false);
        expect(placeOrderCallCount).toBe(1);
    });

    it('keeps a funding request alive across tabs and updates the confirmed account balance', () => {
        const response = new Subject<AddFundsResponse>();
        accountServiceMock.addFunds.mockReturnValue(response);
        const funding = fixture.debugElement.query(By.directive(AccountFundingComponent)).componentInstance as AccountFundingComponent;
        funding.open();
        funding.amount = '10000';
        funding.submit();
        component.selectTab('watchlist');
        fixture.detectChanges();
        expect(funding.funding()).toBe(true);
        mockAccount = { ...mockAccount, cashBalance: 12000 };
        mockHoldings = { ...mockHoldings, cashBalance: 12000 };
        response.next({ accountId: 1, transactionId: 99, amount: 10000, currency: 'GBP', cashBalance: 12000, createdAt: '2026-10-19T12:00:00', replayed: false });
        response.complete();
        component.selectTab('overview');
        fixture.detectChanges();
        expect(component.account()?.cashBalance).toBe(12000);
        expect(funding.funding()).toBe(false);
        expect(funding.success()).toContain('10,000.00');
        expect(accountServiceMock.addFunds).toHaveBeenCalledTimes(1);
    });

    it('shows the complete order history and keeps the active tab during background refresh', () => {
        mockBlotter = Array.from({ length: 8 }, (_, index) => ({ ...filledOrder, id: index + 1 }));
        component.selectTab('orders');
        component.loadDashboard(false);
        component.loadMarketData(false);
        fixture.detectChanges();
        expect(component.activeTab()).toBe('orders');
        expect(fixture.nativeElement.querySelectorAll('.order-item')).toHaveLength(8);
        expect(fixture.nativeElement.querySelector('.orders-list').textContent).toContain('ORDER #8');
    });

    it('updates the day percentage beside Currency when market prices refresh', () => {
        marketDayChange = {
            percent: 1.25, referencePrice: 75, tradingDay: '2026-10-19', referenceTime: '2026-10-19T00:00:01Z'
        };
        component.loadMarketData(false);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.market-table thead').textContent).toContain('Day % (UTC)');
        expect(fixture.nativeElement.querySelector('.market-table app-daily-price-change').textContent).toContain('+1.25%');
        marketDayChange = { ...marketDayChange, percent: -0.5 };
        component.loadMarketData(false);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.market-table app-daily-price-change').textContent).toContain('-0.50%');
    });

    it(
        'should select the first market instrument',
        () => {

            expect(
                component.selectedInstrument()?.symbol
            ).toBe('VOD.L');

        }
    );


    it(
        'should use ask price for buy orders and bid price for sell orders',
        () => {

            component.orderSide = 'BUY';

            expect(
                component.currentOrderPrice()
            ).toBe(75.1);


            component.orderSide = 'SELL';

            expect(
                component.currentOrderPrice()
            ).toBe(75);

        }
    );


    it(
        'should automatically refresh cash, holdings and orders every three seconds',
        () => {

            // Stop the real polling intervals created by beforeEach.
            component.ngOnDestroy();

            // Use a simulated clock so the test runs immediately.
            vi.useFakeTimers();

            try {

                // Restart the component with fake timers.
                component.ngOnInit();

                expect(component.account()?.cashBalance)
                    .toBe(2000);

                expect(component.holdings()?.positions)
                    .toEqual([]);

                expect(component.orders())
                    .toEqual([]);

                // Simulate a trade completed elsewhere.
                mockAccount = {
                    ...mockAccount,
                    cashBalance: 1849.8
                };

                mockHoldings = {
                    ...mockHoldings,
                    cashBalance: 1849.8,
                    positions: [{
                        instrumentId: 1,
                        symbol: 'VOD.L',
                        instrumentName: 'Vodafone Group Plc',
                        quantity: 2
                    }]
                };

                mockBlotter = [filledOrder];

                // The dashboard should not update before
                // the three-second polling interval expires.
                vi.advanceTimersByTime(2999);

                expect(component.account()?.cashBalance)
                    .toBe(2000);

                expect(component.orders())
                    .toEqual([]);

                // Trigger the scheduled automatic refresh.
                vi.advanceTimersByTime(1);

                // Updated account balance.
                expect(component.account()?.cashBalance)
                    .toBe(1849.8);

                // Updated position.
                expect(component.holdings()?.positions[0].quantity)
                    .toBe(2);

                // Updated order status, price and time.
                expect(component.orders()[0].status)
                    .toBe('FILLED');

                expect(component.orders()[0].fillPrice)
                    .toBe(75.1);

                expect(component.orders()[0].filledAt)
                    .toBe('2026-09-15T14:00:01');

                // No new order was submitted to trigger
                // these updates.
                expect(placeOrderCallCount).toBe(0);

                // Silent polling must not activate the
                // dashboard's main loading indicator.
                expect(component.loading()).toBe(false);

            } finally {

                component.ngOnDestroy();
                vi.useRealTimers();

            }
        }
    );



    it(
        'should submit the selected instrument order and display FILLED status',
        () => {

            component.orderSide = 'BUY';

            component.orderQuantity = 2;


            /*
             * Simulate the refreshed server blotter.
             */
            mockBlotter = [filledOrder];


            component.submitOrder();


            expect(
                placeOrderCallCount
            ).toBe(1);


            expect(
                lastPlaceOrderRequest
            ).toEqual({
                instrumentId: 1,
                side: 'BUY',
                quantity: 2
            });


            expect(
                component.orders()[0].status
            ).toBe('FILLED');


            expect(
                component.orders()[0].fillPrice
            ).toBe(75.1);


            expect(
                component.orders()[0].filledAt
            ).toBe(
                '2026-09-15T14:00:01'
            );


            expect(
                component.orderMessage()
            ).toBe('Order #20 filled.');


            expect(
                component.orderQuantity
            ).toBeNull();

        }
    );


    /*
     * NEW SPRINT 4 TEST
     *
     * Verify that a successful BUY refreshes:
     *
     * 1. Account cash balance
     * 2. Portfolio holdings
     * 3. Recent orders
     */
    it(
        'should refresh cash, holdings and recent orders after a filled BUY',
        () => {

            /*
             * Initial dashboard state.
             */
            expect(
                component.account()?.cashBalance
            ).toBe(2000);


            expect(
                component.holdings()?.positions
            ).toEqual([]);


            expect(
                component.orders()
            ).toEqual([]);


            /*
             * Simulate the server state after buying
             * two shares at £75.10 each.
             *
             * Total cost = £150.20
             * Remaining cash = £1849.80
             */
            mockAccount = {
                ...mockAccount,

                cashBalance: 1849.8
            };


            mockHoldings = {
                ...mockHoldings,

                cashBalance: 1849.8,

                positions: [
                    {
                        instrumentId: 1,

                        symbol: 'VOD.L',

                        instrumentName:
                            'Vodafone Group Plc',

                        quantity: 2
                    }
                ]
            };


            mockBlotter = [filledOrder];


            /*
             * Submit the BUY order.
             *
             * The mocked backend immediately returns
             * FILLED, after which the dashboard
             * reloads account, holdings and blotter.
             */
            component.orderSide = 'BUY';

            component.orderQuantity = 2;

            component.submitOrder();


            /*
             * Verify the submitted order.
             */
            expect(
                lastPlaceOrderRequest
            ).toEqual({
                instrumentId: 1,

                side: 'BUY',

                quantity: 2
            });


            /*
             * Verify updated cash.
             */
            expect(
                component.account()?.cashBalance
            ).toBe(1849.8);


            /*
             * Verify new position creation.
             */
            const positions =
                component.holdings()?.positions ?? [];


            expect(
                positions.length
            ).toBe(1);


            expect(
                positions[0].instrumentId
            ).toBe(1);


            expect(
                positions[0].symbol
            ).toBe('VOD.L');


            expect(
                positions[0].quantity
            ).toBe(2);


            /*
             * Verify that the holdings response
             * also reflects the updated balance.
             */
            expect(
                component.holdings()?.cashBalance
            ).toBe(1849.8);


            /*
             * Verify the completed trade appears
             * in recent orders.
             */
            expect(
                component.orders().length
            ).toBe(1);


            expect(
                component.orders()[0].id
            ).toBe(20);


            expect(
                component.orders()[0].status
            ).toBe('FILLED');


            expect(
                component.orders()[0].fillPrice
            ).toBe(75.1);


            expect(
                component.orders()[0].filledAt
            ).toBe(
                '2026-09-15T14:00:01'
            );


            /*
             * Update Angular's rendered template
             * and verify the completed order
             * is visible to the client.
             */
            fixture.detectChanges();


            const displayedText: string =
                fixture.nativeElement.textContent;


            expect(
                displayedText
            ).toContain('FILLED');


            expect(
                displayedText
            ).toContain('Filled:');

        }
    );


    it(
        'should not submit a zero quantity',
        () => {

            component.orderQuantity = 0;


            component.submitOrder();


            expect(
                placeOrderCallCount
            ).toBe(0);


            expect(
                component.quantityError()
            ).toBe(
                'Enter a valid quantity greater than 0.'
            );

        }
    );


    it(
        'should add a rejected order to the blotter',
        () => {

            const rejectedOrder: OrderResponse = {
                id: 21,

                side: 'BUY',

                quantity: 1,

                status: 'REJECTED',

                rejectionReason:
                    'Insufficient cash balance',

                fillPrice: null,

                fillCurrency: 'GBP',
                accountCurrency: 'GBP',
                fxRate: null,
                settlementAmount: null,

                submittedAt:
                    '2026-09-15T14:00:00',

                filledAt: null
            };


            placeOrderResult = throwError(
                () =>
                    new HttpErrorResponse({
                        status: 400,

                        error: rejectedOrder
                    })
            );


            component.orderSide = 'BUY';

            component.orderQuantity = 1;


            component.submitOrder();


            expect(
                component.orders()[0].status
            ).toBe('REJECTED');


            expect(
                component.orderErrorMessage()
            ).toBe(
                'Insufficient cash balance'
            );

        }
    );

    it('does not let a poll started before funding overwrite the confirmed balance', () => {
        const old = new Subject<AccountResponse>();
        const refreshed = new Subject<AccountResponse>();
        const spy = vi.spyOn(accountServiceMock, 'getMyAccount')
            .mockReturnValueOnce(old).mockReturnValueOnce(refreshed);
        const previousAccount = { ...mockAccount };
        component.loadDashboard(false);
        mockAccount = { ...mockAccount, cashBalance: 12000 };
        mockHoldings = { ...mockHoldings, cashBalance: 12000 };
        component.onAccountFunded({ accountId: 1, transactionId: 99, amount: 10000, currency: 'GBP',
            cashBalance: 12000, createdAt: '2026-10-18T12:00:00', replayed: false });
        expect(old.observed).toBe(false);
        expect(component.account()?.cashBalance).toBe(12000);
        old.next(previousAccount); old.complete();
        expect(component.account()?.cashBalance).toBe(12000);
        refreshed.next(mockAccount); refreshed.complete();
        expect(component.holdings()?.cashBalance).toBe(12000);
        spy.mockRestore();
    });
});
