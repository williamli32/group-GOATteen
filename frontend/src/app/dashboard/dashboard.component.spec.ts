
import {
    ComponentFixture,
    TestBed
} from '@angular/core/testing';

import { vi } from 'vitest';

import {
    HttpErrorResponse
} from '@angular/common/http';

import {
    provideRouter
} from '@angular/router';

import {
    Observable,
    of,
    throwError
} from 'rxjs';

import {
    DashboardComponent
} from './dashboard.component';

import {
    AccountService,
    AccountResponse,
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


    const accountServiceMock = {

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
            request: PlaceOrderRequest
        ) => {

            placeOrderCallCount++;

            lastPlaceOrderRequest = request;

            return placeOrderResult;

        }

    };


    const authMock = {

        clearToken: () => { },

        logoutSession: () =>
            of(void 0)

    };


    beforeEach(async () => {

        placeOrderCallCount = 0;

        lastPlaceOrderRequest = null;


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
                    'Currency conversion is not supported yet',

                fillPrice: null,

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
                'Currency conversion is not supported yet'
            );

        }
    );

});
