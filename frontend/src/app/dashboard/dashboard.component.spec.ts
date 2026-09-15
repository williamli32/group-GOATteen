import {
    ComponentFixture,
    TestBed
} from '@angular/core/testing';

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

    let component:
        DashboardComponent;

    let fixture:
        ComponentFixture<DashboardComponent>;


    let placeOrderCallCount:
        number;

    let lastPlaceOrderRequest:
        PlaceOrderRequest | null;

    let placeOrderResult:
        Observable<OrderResponse>;


    const accountServiceMock = {

        getMyAccount: () =>
            of({

                accountId: 1,

                accountNumber:
                    'LEAP-TEST123',

                cashBalance: 2000,

                currency: 'GBP',

                firstName: 'Test',

                lastName: 'Trader'

            }),


        getHoldings: () =>
            of({

                accountId: 1,

                cashBalance: 2000,

                currency: 'GBP',

                positions: []

            }),


        getBlotter: () =>
            of([])

    };


    const marketDataServiceMock = {

        getInstruments: () =>
            of([

                {

                    instrumentId: 1,

                    symbol: 'VOD.L',

                    name:
                        'Vodafone Group Plc',

                    instrumentClass:
                        'EQUITY_UK',

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

            lastPlaceOrderRequest =
                request;

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

        lastPlaceOrderRequest =
            null;


        placeOrderResult =
            of({

                id: 20,

                side: 'BUY',

                quantity: 2,

                status: 'ACCEPTED',

                rejectionReason: null,

                fillPrice: null,

                submittedAt:
                    '2026-09-15T14:00:00',

                filledAt: null

            });


        await TestBed
            .configureTestingModule({

                imports: [
                    DashboardComponent
                ],

                providers: [

                    provideRouter([]),

                    {
                        provide:
                            AccountService,

                        useValue:
                            accountServiceMock
                    },

                    {
                        provide:
                            MarketDataService,

                        useValue:
                            marketDataServiceMock
                    },

                    {
                        provide:
                            OrderService,

                        useValue:
                            orderServiceMock
                    },

                    {
                        provide:
                            Auth,

                        useValue:
                            authMock
                    }

                ]

            })
            .compileComponents();


        fixture =
            TestBed.createComponent(
                DashboardComponent
            );

        component =
            fixture.componentInstance;


        fixture.detectChanges();

    });


    it(
        'should select the first market instrument',
        () => {

            expect(
                component
                    .selectedInstrument()
                    ?.symbol
            ).toBe('VOD.L');

        }
    );


    it(
        'should use ask price for buy orders and bid price for sell orders',
        () => {

            component.orderSide =
                'BUY';

            expect(
                component.currentOrderPrice()
            ).toBe(75.1);


            component.orderSide =
                'SELL';

            expect(
                component.currentOrderPrice()
            ).toBe(75);

        }
    );


    it(
        'should submit the selected instrument order',
        () => {

            component.orderSide =
                'BUY';

            component.orderQuantity =
                2;


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
            ).toBe('ACCEPTED');


            expect(
                component.orderMessage()
            ).toBe(
                'Order #20 accepted.'
            );


            expect(
                component.orderQuantity
            ).toBeNull();

        }
    );


    it(
        'should not submit a zero quantity',
        () => {

            component.orderQuantity =
                0;


            component.submitOrder();


            expect(
                placeOrderCallCount
            ).toBe(0);


            expect(
                component.orderErrorMessage()
            ).toBe(
                'Quantity must be greater than zero.'
            );

        }
    );


    it(
        'should add a rejected order to the blotter',
        () => {

            const rejectedOrder:
                OrderResponse = {

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


            placeOrderResult =
                throwError(
                    () =>
                        new HttpErrorResponse({

                            status: 400,

                            error:
                                rejectedOrder

                        })
                );


            component.orderSide =
                'BUY';

            component.orderQuantity =
                1;


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