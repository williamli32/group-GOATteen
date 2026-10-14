import {
    ComponentFixture,
    TestBed
} from '@angular/core/testing';

import {
    of,
    throwError
} from 'rxjs';

import {
    vi
} from 'vitest';

import {
    AnalystDashboardComponent
} from './analyst-dashboard.component';

import {
    ReportingService
} from '../core/services/reporting';

import {
    MarketDataService
} from '../core/services/market-data';


describe('AnalystDashboardComponent', () => {

    let component: AnalystDashboardComponent;

    let fixture:
        ComponentFixture<AnalystDashboardComponent>;

    let reportingService: {
        getSummary: ReturnType<typeof vi.fn>;
        getTrends: ReturnType<typeof vi.fn>;
        getInstruments: ReturnType<typeof vi.fn>;
        getClients: ReturnType<typeof vi.fn>;
        getActivity: ReturnType<typeof vi.fn>;
    };

    let marketDataService: {
        getInstruments: ReturnType<typeof vi.fn>;
    };


    const summary = {

        totalOrders: 10,

        filledOrders: 7,

        rejectedOrders: 2,

        activeClients: 3,

        fillRate: 70,

        rejectionRate: 20,

        totalExecutedQuantity: 100,

        totalTradedValue: 12500,

        buyFills: 4,

        buyQuantity: 60,

        buyTradedValue: 7500,

        sellFills: 3,

        sellQuantity: 40,

        sellTradedValue: 5000

    };


    beforeEach(async () => {

        reportingService = {

            getSummary:
                vi.fn(() => of(summary)),

            getTrends:
                vi.fn(() => of([])),

            getInstruments:
                vi.fn(() => of([])),

            getClients:
                vi.fn(() => of([])),

            getActivity:
                vi.fn(() => of([]))

        };


        marketDataService = {

            getInstruments:
                vi.fn(() => of([

                    {
                        instrumentId: 7,
                        symbol: 'AAPL',
                        name: 'Apple Inc.',
                        instrumentClass: 'EQUITY_US',
                        exchange: 'NASDAQ',
                        countryCode: 'US',
                        currency: 'GBP',
                        tradable: true,
                        latestQuote: null
                    },

                    {
                        instrumentId: 8,
                        symbol: 'MSFT',
                        name: 'Microsoft Corp.',
                        instrumentClass: 'EQUITY_US',
                        exchange: 'NASDAQ',
                        countryCode: 'US',
                        currency: 'USD',
                        tradable: true,
                        latestQuote: null
                    }

                ]))

        };


        await TestBed
            .configureTestingModule({

                imports: [
                    AnalystDashboardComponent
                ],

                providers: [

                    {
                        provide:
                            ReportingService,

                        useValue:
                            reportingService
                    },

                    {
                        provide:
                            MarketDataService,

                        useValue:
                            marketDataService
                    }

                ]

            })
            .compileComponents();


        fixture =
            TestBed.createComponent(
                AnalystDashboardComponent
            );


        component =
            fixture.componentInstance;


        fixture.detectChanges();

    });


    it(
        'loads every reporting dataset with one shared filter set',
        () => {

            component
                .fromDate
                .set('2026-09-01');

            component
                .toDate
                .set('2026-09-30');

            component
                .instrumentId
                .set(7);

            component
                .side
                .set('BUY');

            component
                .status
                .set('FILLED');

            component
                .clientId
                .set(42);

            component
                .currency
                .set('GBP');


            component.applyFilters();


            const expected =
                expect.objectContaining({

                    fromDate:
                        '2026-09-01',

                    toDate:
                        '2026-09-30',

                    instrumentId:
                        7,

                    side:
                        'BUY',

                    status:
                        'FILLED',

                    clientId:
                        42,

                    currency:
                        'GBP'

                });


            expect(
                reportingService
                    .getSummary
            )
                .toHaveBeenCalledWith(
                    expected
                );


            expect(
                reportingService
                    .getTrends
            )
                .toHaveBeenCalledWith(
                    expected
                );


            expect(
                reportingService
                    .getInstruments
            )
                .toHaveBeenCalledWith(
                    expected
                );


            expect(
                reportingService
                    .getClients
            )
                .toHaveBeenCalledWith(
                    expected
                );


            expect(
                reportingService
                    .getActivity
            )
                .toHaveBeenCalledWith(
                    expected
                );

        }
    );


    it(
        'shows an internal access error when reporting is forbidden',
        () => {

            reportingService
                .getSummary
                .mockReturnValue(
                    throwError(
                        () => ({
                            status: 403
                        })
                    )
                );


            component.applyFilters();


            expect(
                component.errorMessage()
            )
                .toContain(
                    'regular clients are not permitted'
                );


            expect(
                component.loading()
            )
                .toBe(false);

        }
    );


    it(
        'explains when the session has expired',
        () => {

            reportingService
                .getSummary
                .mockReturnValue(
                    throwError(
                        () => ({
                            status: 401
                        })
                    )
                );


            component.applyFilters();


            expect(
                component.errorMessage()
            )
                .toContain(
                    'session has expired'
                );

        }
    );


    it(
        'surfaces backend reporting failures',
        () => {

            reportingService
                .getSummary
                .mockReturnValue(
                    throwError(
                        () => ({
                            status: 500
                        })
                    )
                );


            component.applyFilters();


            expect(
                component.errorMessage()
            )
                .toContain(
                    'reporting service returned an error (500)'
                );

        }
    );


    it(
        'resets all optional filters and the reporting currency',
        () => {

            component
                .instrumentId
                .set(7);

            component
                .side
                .set('SELL');

            component
                .status
                .set('REJECTED');

            component
                .clientId
                .set(42);

            component
                .currency
                .set('USD');


            component.resetFilters();


            expect(
                component.clientId()
            )
                .toBeUndefined();


            expect(
                component.instrumentId()
            )
                .toBeUndefined();


            expect(
                component.side()
            )
                .toBeUndefined();


            expect(
                component.status()
            )
                .toBeUndefined();


            expect(
                component.currency()
            )
                .toBe('GBP');


            expect(
                reportingService
                    .getSummary
            )
                .toHaveBeenLastCalledWith(

                    expect.objectContaining({

                        instrumentId:
                            undefined,

                        side:
                            undefined,

                        status:
                            undefined,

                        clientId:
                            undefined,

                        currency:
                            'GBP'

                    })

                );

        }
    );


    it(
        'loads instrument choices independently from reporting results',
        () => {

            expect(
                marketDataService
                    .getInstruments
            )
                .toHaveBeenCalled();


            expect(
                component
                    .marketInstruments()
            )
                .toEqual([

                    expect.objectContaining({
                        instrumentId: 7,
                        symbol: 'AAPL'
                    }),

                    expect.objectContaining({
                        instrumentId: 8,
                        symbol: 'MSFT'
                    })

                ]);

        }
    );


    it(
        'uses the selected instrument currency',
        () => {

            component
                .selectInstrument('8');


            expect(
                component.instrumentId()
            )
                .toBe(8);


            expect(
                component.currency()
            )
                .toBe('USD');

        }
    );


    it(
        'returns the currencies available from market instruments',
        () => {

            expect(
                component.currencyOptions()
            )
                .toEqual([
                    'GBP',
                    'USD'
                ]);

        }
    );


    it(
        'renders empty result state when reporting returns no activity',
        () => {

            fixture.detectChanges();


            expect(
                fixture
                    .nativeElement
                    .textContent
            )
                .toContain(
                    'No instrument data.'
                );


            expect(
                fixture
                    .nativeElement
                    .textContent
            )
                .toContain(
                    'No recent activity.'
                );

        }
    );


    it(
        'builds line-chart points from daily trend values',
        () => {

            component.trends.set([

                {
                    date:
                        '2026-10-01',

                    totalOrders:
                        10,

                    filledOrders:
                        8,

                    rejectedOrders:
                        2,

                    activeClients:
                        3,

                    executedQuantity:
                        100,

                    tradedValue:
                        1000
                },

                {
                    date:
                        '2026-10-02',

                    totalOrders:
                        20,

                    filledOrders:
                        15,

                    rejectedOrders:
                        5,

                    activeClients:
                        4,

                    executedQuantity:
                        200,

                    tradedValue:
                        2500
                }

            ]);


            expect(
                component
                    .trendPoints('orders')
            )
                .toBe(
                    '0,100.4 600,22'
                );


            expect(
                component
                    .trendAreaPoints('value')
            )
                .toBe(
                    '0,190 0,122.8 600,22 600,190'
                );

        }
    );


    it(
        'scales BUY vs SELL ring segments from fill counts',
        () => {

            component.summary.set({

                ...summary,

                buyFills:
                    2,

                sellFills:
                    4

            });


            const style =
                component
                    .splitRingStyle();


            expect(style)
                .toContain(
                    '#72d8c0 0 33.33333333333333%'
                );


            expect(style)
                .toContain(
                    '#ee7b83 33.33333333333333% 100%'
                );

        }
    );


    it(
        'calculates total trades from buy and sell fills',
        () => {

            component.summary.set({

                ...summary,

                buyFills:
                    8,

                sellFills:
                    5

            });


            expect(
                component.totalTrades()
            )
                .toBe(13);

        }
    );


    it(
        'ranks instruments by traded value',
        () => {

            component.instruments.set([

                {
                    instrumentId:
                        1,

                    instrumentSymbol:
                        'AAPL',

                    currency:
                        'USD',

                    fillCount:
                        10,

                    executedQuantity:
                        100,

                    tradedValue:
                        10000
                },

                {
                    instrumentId:
                        2,

                    instrumentSymbol:
                        'MSFT',

                    currency:
                        'USD',

                    fillCount:
                        4,

                    executedQuantity:
                        50,

                    tradedValue:
                        25000
                }

            ]);


            const result =
                component
                    .highestValueInstruments();


            expect(
                result[0]
                    .instrumentSymbol
            )
                .toBe('MSFT');

        }
    );


    it(
        'combines duplicate client rows into one active-client row',
        () => {

            component.clients.set([

                {
                    clientId:
                        12,

                    clientName:
                        'Joanna Smith',

                    currency:
                        'GBP',

                    orderCount:
                        4,

                    fillCount:
                        3,

                    executedQuantity:
                        10,

                    tradedValue:
                        1000
                },

                {
                    clientId:
                        12,

                    clientName:
                        'Joanna Smith',

                    currency:
                        'USD',

                    orderCount:
                        6,

                    fillCount:
                        5,

                    executedQuantity:
                        20,

                    tradedValue:
                        2500
                }

            ]);


            const result =
                component
                    .mostActiveClients();


            expect(
                result.length
            )
                .toBe(1);


            expect(
                result[0]
                    .clientName
            )
                .toBe(
                    'Joanna Smith'
                );


            expect(
                result[0]
                    .orderCount
            )
                .toBe(10);


            expect(
                result[0]
                    .fillCount
            )
                .toBe(8);


            expect(
                result[0]
                    .values
                    .length
            )
                .toBe(2);

        }
    );


    it(
        'removes duplicate clients from the client filter',
        () => {

            component.clientOptions.set([

                {
                    clientId:
                        12,

                    clientName:
                        'Joanna Smith',

                    currency:
                        'GBP',

                    orderCount:
                        4,

                    fillCount:
                        3,

                    executedQuantity:
                        10,

                    tradedValue:
                        1000
                },

                {
                    clientId:
                        12,

                    clientName:
                        'Joanna Smith',

                    currency:
                        'USD',

                    orderCount:
                        6,

                    fillCount:
                        5,

                    executedQuantity:
                        20,

                    tradedValue:
                        2500
                }

            ]);


            const result =
                component
                    .uniqueClientOptions();


            expect(
                result.length
            )
                .toBe(1);


            expect(
                result[0]
                    .clientId
            )
                .toBe(12);

        }
    );


    it(
        'formats USD values using a dollar symbol',
        () => {

            const formatted =
                component
                    .formatMoney(
                        1250,
                        'USD'
                    );


            expect(
                formatted
            )
                .toContain('$');

        }
    );

});