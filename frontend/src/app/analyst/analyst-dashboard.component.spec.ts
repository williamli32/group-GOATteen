import {
    ComponentFixture,
    TestBed
} from '@angular/core/testing';

import {
    of,
    Subject,
    throwError
} from 'rxjs';

import {
    vi
} from 'vitest';

import {
    AnalystDashboardComponent
} from './analyst-dashboard.component';

import {
    ReportingService,
    BusinessInsights
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
        getInsights: ReturnType<typeof vi.fn>;
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

    const insight = {
        currentWindow: { fromDate: '2026-10-01', toDate: '2026-10-16' },
        previousWindow: { fromDate: '2026-09-15', toDate: '2026-09-30' },
        currency: 'GBP', current: summary,
        previous: { ...summary, buyFills: 1, sellFills: 1 },
        currentFillCount: 7, previousFillCount: 2, volumeChangePercent: 250,
        buySharePercent: 57.14, sellSharePercent: 42.86,
        mostActiveInstrument: null, fastestGrowingInstrument: null,
        newlyActiveInstruments: [], highestVolumeDay: null,
        rejectionRateChangePercentagePoints: 0, rejectionTrend: []
    } satisfies BusinessInsights;

    function showRejectionTrend(
        points: BusinessInsights['rejectionTrend'],
        fromDate = '2026-10-01',
        toDate = '2026-10-03'
    ): HTMLElement {
        component.insights.set({
            ...insight,
            currentWindow: { fromDate, toDate },
            current: {
                ...summary,
                totalOrders: points.reduce((total, point) => total + point.totalOrders, 0),
                rejectedOrders: points.reduce((total, point) => total + point.rejectedOrders, 0)
            },
            rejectionTrend: points
        });
        fixture.detectChanges();
        return fixture.nativeElement as HTMLElement;
    }


    beforeEach(async () => {

        reportingService = {
            getInsights: vi.fn(filters => of({
                ...insight,
                currentWindow: { fromDate: filters.fromDate, toDate: filters.toDate },
                currency: filters.currency ?? 'GBP'
            })),

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
                    .getInsights
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

    it('uses the server baseline even when the chart only contains one month', () => {
        component.insights.set(insight);
        component.trends.set([]);
        const stats = component.metricStats('trades');
        expect(stats.current).toBe(7);
        expect(stats.previous).toBe(2);
        expect(stats.change).toBe(250);
        expect(stats.previousLabel).toBe('2026-09-15 to 2026-09-30');
    });

    it('does not invent a percentage or an increase without a prior baseline', () => {
        component.insights.set({
            ...insight, previous: { ...summary, buyFills: 0, sellFills: 0 }
        });
        expect(component.metricStats('trades').change).toBeNull();
        expect(component.metricInterpretation('trades')).not.toContain('increased');
        component.insights.set(insight);
        expect(component.metricInterpretation('rejected')).toContain('unchanged');
    });

    it('loads insights through the same applied filters as the other reporting datasets', () => {
        component.fromDate.set('2026-10-01');
        component.toDate.set('2026-10-16');
        component.clientId.set(42);
        component.applyFilters();
        expect(reportingService.getInsights).toHaveBeenLastCalledWith(
            expect.objectContaining({ fromDate: '2026-10-01', toDate: '2026-10-16', clientId: 42, currency: 'GBP' })
        );
    });

    it('breaks the rejection-rate line across days without orders', () => {
        component.insights.set({
            ...insight,
            rejectionTrend: [
                { date: '2026-10-01', totalOrders: 2, rejectedOrders: 1, rejectionRate: 50 },
                { date: '2026-10-02', totalOrders: 0, rejectedOrders: 0, rejectionRate: null },
                { date: '2026-10-03', totalOrders: 1, rejectedOrders: 0, rejectionRate: 0 }
            ]
        });
        expect(component.rejectionTrendPath().match(/M/g)?.length).toBe(2);
        expect(component.rejectionTrendPath()).not.toContain('L');
    });

    it('plots sparse observations at their calendar dates within the complete reporting window', () => {
        const host = showRejectionTrend([
            { date: '2026-10-03', totalOrders: 4, rejectedOrders: 1, rejectionRate: 25 }
        ], '2026-10-01', '2026-10-05');

        expect(component.rejectionChart().dayCount).toBe(5);
        expect(component.rejectionChart().points[0]).toMatchObject({ x: 50, y: 75 });
        const marker = host.querySelector<HTMLButtonElement>('.rejection-trend-point')!;
        expect(marker.style.left).toBe('50%');
        expect(marker.style.top).toBe('75%');
        expect(host.querySelector('.rejection-single-point')?.textContent).toContain('single point');
        expect(host.querySelector('.rejection-trend-coverage')?.textContent).toContain('1 of 5');
        expect(component.rejectionTrendPath()).toBe('M50,75');
    });

    it('does not bridge omitted calendar dates or depend on the API array order', () => {
        showRejectionTrend([
            { date: '2026-10-03', totalOrders: 2, rejectedOrders: 1, rejectionRate: 50 },
            { date: '2026-10-01', totalOrders: 4, rejectedOrders: 1, rejectionRate: 25 }
        ]);

        expect(component.rejectionChart().points.map(point => point.date)).toEqual([
            '2026-10-01', '2026-10-03'
        ]);
        expect(component.rejectionTrendPath()).toBe('M0,75 M100,50');
        expect(component.rejectionTrendPath()).not.toContain('L');
    });

    it('keeps a genuine zero rejection rate visible rather than treating it as missing data', () => {
        const host = showRejectionTrend([
            { date: '2026-10-01', totalOrders: 3, rejectedOrders: 0, rejectionRate: 0 },
            { date: '2026-10-02', totalOrders: 2, rejectedOrders: 0, rejectionRate: 0 }
        ], '2026-10-01', '2026-10-02');

        expect(component.rejectionTrendPath()).toBe('M0,100 L100,100');
        expect(host.querySelectorAll('.rejection-trend-point').length).toBe(2);
        expect(host.querySelector('.rejection-trend-empty')).toBeNull();
        expect(host.querySelector('.rejection-trend-point')?.getAttribute('aria-label')).toContain('0%');
    });

    it('centres the point and date label for a single-day reporting window', () => {
        const host = showRejectionTrend([
            { date: '2026-10-16', totalOrders: 4, rejectedOrders: 1, rejectionRate: 25 }
        ], '2026-10-16', '2026-10-16');

        expect(component.rejectionChart().ticks).toEqual([
            { date: '2026-10-16', x: 50, secondary: false }
        ]);
        expect(component.rejectionTrendPath()).toBe('M50,75');
        expect(host.querySelector<HTMLElement>('.rejection-x-tick')?.style.left).toBe('50%');
        expect(host.querySelectorAll('.rejection-trend-point').length).toBe(1);
    });

    it('shows a clear empty state for a period with no submitted orders', () => {
        const host = showRejectionTrend([
            { date: '2026-10-01', totalOrders: 0, rejectedOrders: 0, rejectionRate: null },
            { date: '2026-10-02', totalOrders: 0, rejectedOrders: 0, rejectionRate: null },
            { date: '2026-10-03', totalOrders: 0, rejectedOrders: 0, rejectionRate: null }
        ]);

        expect(host.querySelector('.rejection-trend-empty')?.textContent).toContain('No submitted orders');
        expect(host.querySelector('.rejection-trend-svg')).toBeNull();
        expect(host.querySelectorAll('.rejection-trend-point').length).toBe(0);
        expect(component.rejectionTrendPath()).toBe('');
    });

    it('does not claim there were no orders when only the daily data is unavailable', () => {
        component.insights.set(insight);
        fixture.detectChanges();
        const host = fixture.nativeElement as HTMLElement;

        expect(host.querySelector('.rejection-trend-empty')?.textContent).toContain('No daily rejection-rate data');
        expect(host.querySelector('.rejection-trend-empty')?.textContent).not.toContain('No submitted orders');
    });

    it('aligns percentage ticks and endpoint date labels with a full-width responsive plot', () => {
        const host = showRejectionTrend([
            { date: '2026-10-01', totalOrders: 2, rejectedOrders: 1, rejectionRate: 50 },
            { date: '2026-10-31', totalOrders: 4, rejectedOrders: 1, rejectionRate: 25 }
        ], '2026-10-01', '2026-10-31');

        expect(Array.from(host.querySelectorAll('.rejection-y-tick'), tick => tick.textContent?.trim()))
            .toEqual(['100%', '75%', '50%', '25%', '0%']);
        expect(host.querySelector('svg.rejection-trend-svg')?.getAttribute('preserveAspectRatio')).toBe('none');
        expect(host.querySelector('.rejection-trend-line')?.getAttribute('vector-effect')).toBe('non-scaling-stroke');
        const ticks = host.querySelectorAll<HTMLElement>('.rejection-x-tick');
        expect(ticks.length).toBe(5);
        expect(ticks[0].style.left).toBe('0%');
        expect(ticks[0].textContent).toContain('Oct 1');
        expect(ticks[4].style.left).toBe('100%');
        expect(ticks[4].textContent).toContain('Oct 31');
        expect(host.querySelectorAll('.rejection-tick-secondary').length).toBe(2);
    });

    it('provides focusable points with the date, rate and source counts', () => {
        const host = showRejectionTrend([
            { date: '2026-10-01', totalOrders: 8, rejectedOrders: 1, rejectionRate: 12.5 }
        ]);
        const marker = host.querySelector<HTMLButtonElement>('.rejection-trend-point')!;

        expect(marker.type).toBe('button');
        expect(marker.tabIndex).toBe(0);
        expect(marker.getAttribute('aria-label')).toContain('2026-10-01: 12.5%');
        expect(marker.getAttribute('aria-label')).toContain('1 of 8 orders rejected');
        expect(marker.querySelector('.rejection-point-tooltip')?.textContent).toContain('12.5% rejection rate');
        expect(marker.querySelector('.rejection-point-tooltip')?.textContent).toContain('1 of 8 orders rejected');
    });

    it('keeps zero and one-hundred-percent observations on their correct plot boundaries', () => {
        const host = showRejectionTrend([
            { date: '2026-10-01', totalOrders: 2, rejectedOrders: 2, rejectionRate: 100 },
            { date: '2026-10-02', totalOrders: 2, rejectedOrders: 0, rejectionRate: 0 }
        ], '2026-10-01', '2026-10-02');

        expect(Array.from(host.querySelectorAll<HTMLElement>('.rejection-trend-point'), marker => marker.style.top))
            .toEqual(['0%', '100%']);
        expect(component.rejectionTrendPath()).toBe('M0,0 L100,100');
    });

    it('uses UTC calendar days across leap days and does not join over the missing leap day', () => {
        showRejectionTrend([
            { date: '2024-02-28', totalOrders: 2, rejectedOrders: 1, rejectionRate: 50 },
            { date: '2024-03-01', totalOrders: 4, rejectedOrders: 1, rejectionRate: 25 }
        ], '2024-02-28', '2024-03-01');

        expect(component.rejectionChart().dayCount).toBe(3);
        expect(component.rejectionChart().ticks.map(tick => tick.date)).toEqual([
            '2024-02-28', '2024-02-29', '2024-03-01'
        ]);
        expect(component.rejectionTrendPath()).toBe('M0,50 M100,75');
    });

    it('renders the business-insight section with the real comparison window', () => {
        component.insights.set({
            ...insight,
            mostActiveInstrument: {
                instrumentId: 7, symbol: 'AAPL', fillCount: 7, previousFillCount: 2,
                changePercent: 250, newlyActive: false
            },
            fastestGrowingInstrument: {
                instrumentId: 7, symbol: 'AAPL', fillCount: 7, previousFillCount: 2,
                changePercent: 250, newlyActive: false
            },
            highestVolumeDay: { date: '2026-10-12', fillCount: 4 }
        });
        fixture.detectChanges();
        const section = fixture.nativeElement.querySelector('.business-insights');
        expect(section.textContent).toContain('2026-09-15');
        expect(section.textContent).toContain('AAPL');
        expect(section.textContent).toContain('Fastest-growing instrument');
        expect(section.textContent).toContain('Highest-volume trading day');
        expect(section.textContent).toContain('Rejection rate over time');
    });


    it(
        'shows an internal access error when reporting is forbidden',
        () => {

            reportingService
                .getInsights
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
                .getInsights
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
                .getInsights
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
                    'reporting service is temporarily unavailable'
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
                    .getInsights
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
        'offers the three supported reporting currencies even if a market has no loaded instruments',
        () => {

            expect(
                component.currencyOptions()
            )
                .toEqual([
                    'USD',
                    'GBP',
                    'INR'
                ]);

        }
    );

    it('keeps the supported reporting status options', () => {
        const statusSelect = Array.from(fixture.nativeElement.querySelectorAll('select') as NodeListOf<HTMLSelectElement>)
            .find((select: HTMLSelectElement) => Array.from(select.options)
                .some((option: HTMLOptionElement) => option.textContent?.trim() === 'All statuses'))!;
        expect(Array.from(statusSelect.options).map((option: HTMLOptionElement) => option.textContent?.trim())).toEqual([
            'All statuses', 'FILLED', 'REJECTED'
        ]);
    });


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
        'plots daily values on the applied calendar with whole-number count ticks',
        () => {

            component.appliedFilters.set({ fromDate: '2026-10-01', toDate: '2026-10-02', currency: 'GBP' });
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
                        1000,

                    buyFills:
                        5,

                    sellFills:
                        3
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
                        2500,

                    buyFills:
                        9,

                    sellFills:
                        6
                }

            ]);


            expect(
                component
                    .dailyCharts().orders.path
            )
                .toBe(
                    'M0,106 L600,32.5'
                );


            expect(
                component
                    .dailyCharts().value.area
            )
                .toBe(
                    'M0,190 L0,122.8 L600,22 L600,190 Z'
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
                    '0 33.33333333333333%'
                );


            expect(style)
                .toContain(
                    '33.33333333333333% 100%'
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


    it(
        'renders the Market Pulse cards from the reporting summary',
        () => {

            const cards = fixture.nativeElement.querySelectorAll(
                '.metric-card'
            );

            expect(cards.length).toBe(7);
            expect(fixture.nativeElement.textContent).toContain('Total successful trades');
            expect(fixture.nativeElement.textContent).toContain('Executed quantity');
            expect(fixture.nativeElement.textContent).toContain('Active clients');
            expect(fixture.nativeElement.textContent).toContain('3');
        }
    );


    it(
        'opens and closes a Market Pulse detail modal',
        () => {

            component.toggleMetric('trades');
            fixture.detectChanges();

            expect(component.expandedMetric()).toBe('trades');
            expect(fixture.nativeElement.querySelector('.metric-modal')).toBeTruthy();
            expect(fixture.nativeElement.textContent).toContain('Total successful trades');

            component.toggleMetric('trades');
            fixture.detectChanges();

            expect(component.expandedMetric()).toBeNull();
            expect(fixture.nativeElement.querySelector('.metric-modal')).toBeNull();
        }
    );


    it(
        'keeps the active-client dialog aligned with its summary card rather than the union of order and fill activity rows',
        () => {

            component.clients.set([
                {
                    clientId: 1,
                    clientName: 'Alice Smith',
                    currency: 'GBP',
                    orderCount: 4,
                    fillCount: 2,
                    executedQuantity: 10,
                    tradedValue: 100
                },
                {
                    clientId: 1,
                    clientName: 'Alice Smith',
                    currency: 'USD',
                    orderCount: 3,
                    fillCount: 1,
                    executedQuantity: 5,
                    tradedValue: 50
                },
                {
                    clientId: 2,
                    clientName: 'Bob Jones',
                    currency: 'GBP',
                    orderCount: 2,
                    fillCount: 1,
                    executedQuantity: 4,
                    tradedValue: 40
                }
            ]);

            expect(component.mostActiveClients().length).toBe(2);
            expect(component.metricStats('clients').current).toBe(3);
            component.toggleMetric('clients');
            fixture.detectChanges();
            expect(fixture.nativeElement.querySelector('.metric-modal-stats strong').textContent).toBe('3');
            expect(component.metricStats('clients').previous).toBe(3);
        }
    );


    it(
        'shows BUY and SELL counts together for a hovered historical date',
        () => {

            component.trends.set([
                {
                    date: '2026-10-01',
                    totalOrders: 5,
                    filledOrders: 4,
                    rejectedOrders: 1,
                    activeClients: 2,
                    executedQuantity: 20,
                    tradedValue: 1000,
                    buyFills: 3,
                    sellFills: 1
                }
            ]);

            component.showBuySellDateDialog(0, new MouseEvent('mouseenter', {
                clientX: 240,
                clientY: 180
            }));

            const dialog = component.graphDialog();

            expect(dialog?.date).toContain('1 Oct');
            expect(dialog?.series).toEqual([
                expect.objectContaining({ label: 'BUY fills', value: '3' }),
                expect.objectContaining({ label: 'SELL fills', value: '1' })
            ]);
            expect(dialog!.x).toBeGreaterThan(0);
            expect(dialog!.x).toBeLessThan(window.innerWidth);
            expect(dialog!.y).toBeGreaterThan(0);
            expect(dialog!.y).toBeLessThan(window.innerHeight);
        }
    );

    it('keeps loaded currencies and dates until a new filter set succeeds', () => {
        const original = component.appliedFilters();
        component.selectCurrency('USD');
        component.fromDate.set('2026-01-01');
        component.toDate.set('2026-01-31');
        fixture.detectChanges();

        const host = fixture.nativeElement as HTMLElement;
        expect(component.appliedFilters()).toEqual(original);
        expect(host.querySelector('.period-label')?.textContent).toContain(original.fromDate);
        expect(host.querySelector('.accent-gold')?.textContent).toContain('£12,500.00');
        expect(component.metricValue('value', 12500)).toContain('£');
        expect(reportingService.getInsights).toHaveBeenCalledTimes(1);

        component.applyFilters();
        fixture.detectChanges();
        expect(component.appliedFilters()).toMatchObject({
            currency: 'USD', fromDate: '2026-01-01', toDate: '2026-01-31'
        });
        expect(host.querySelector('.value-line-chart .chart-y-axis b')?.textContent).toBe('USD');
        expect(host.querySelector('.accent-gold')?.textContent).toContain('$12,500.00');
    });

    it('preserves applied filter metadata when the next load fails', () => {
        const original = component.appliedFilters();
        reportingService.getInsights.mockReturnValue(throwError(() => ({ status: 500 })));
        component.selectCurrency('USD');
        component.applyFilters();
        expect(component.appliedFilters()).toEqual(original);
        expect(component.reportedCurrency()).toBe('GBP');
    });

    it('uses one summary snapshot for cards and comparison dialogs', () => {
        reportingService.getSummary.mockReturnValue(of({ ...summary, buyFills: 999 }));
        reportingService.getInsights.mockReturnValue(of({
            ...insight, current: { ...summary, buyFills: 45, sellFills: 1 }
        }));
        component.applyFilters();
        expect(reportingService.getSummary).not.toHaveBeenCalled();
        expect(component.totalTrades()).toBe(46);
        expect(component.metricStats('trades').current).toBe(46);
    });

    it('cancels an earlier filter load when Reset starts a newer request', () => {
        const first = new Subject<BusinessInsights>();
        const reset = new Subject<BusinessInsights>();
        reportingService.getInsights.mockReturnValueOnce(first).mockReturnValueOnce(reset);
        component.selectCurrency('USD');
        component.applyFilters();
        expect(first.observed).toBe(true);
        component.resetFilters();
        expect(first.observed).toBe(false);

        reset.next(insight);
        reset.complete();
        first.next({ ...insight, currency: 'USD', current: { ...summary, buyFills: 999 } });
        first.complete();
        expect(component.loading()).toBe(false);
        expect(component.reportedCurrency()).toBe('GBP');
        expect(component.insights()?.currency).toBe('GBP');
        expect(component.totalTrades()).toBe(7);
    });

    it('keeps nonzero fractional quantities visible in the card and its dialog', () => {
        reportingService.getInsights.mockReturnValue(of({
            ...insight, current: { ...summary, totalExecutedQuantity: 0.004 }
        }));
        component.applyFilters();
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.accent-cyan').textContent).toContain('0.004');
        expect(component.metricValue('quantity', component.metricStats('quantity').current)).toBe('0.004');
        expect(component.changeLabel(12.34567)).toBe('+12.35%');
    });

    it('limits every daily chart to five aligned ticks for a 366-day range', () => {
        component.appliedFilters.set({ fromDate: '2024-01-01', toDate: '2024-12-31', currency: 'GBP' });
        component.trends.set(Array.from({ length: 366 }, (_, index) => ({
            date: new Date(Date.UTC(2024, 0, 1) + index * 86_400_000).toISOString().slice(0, 10),
            totalOrders: 1, filledOrders: 1, rejectedOrders: 0, activeClients: 1,
            executedQuantity: 1, tradedValue: 10, buyFills: 1, sellFills: 0
        })));
        fixture.detectChanges();
        const host = fixture.nativeElement as HTMLElement;
        const axes = host.querySelectorAll('.chart-labels');
        expect(axes.length).toBe(3);
        for (const axis of axes) {
            const ticks = axis.querySelectorAll<HTMLElement>('small');
            expect(ticks.length).toBe(5);
            expect(ticks[0].style.left).toBe('0%');
            expect(ticks[4].style.left).toBe('100%');
        }
        expect(host.querySelectorAll('.history-label').length).toBe(5);
        expect(component.trendTicks()[0].date).toBe('2024-01-01');
        expect(component.trendTicks()[4].date).toBe('2024-12-31');
    });

    it('centres the axis label for a single-day reporting window', () => {
        component.appliedFilters.set({ fromDate: '2026-10-01', toDate: '2026-10-01', currency: 'GBP' });
        component.trends.set([{
            date: '2026-10-01', totalOrders: 1, filledOrders: 1, rejectedOrders: 0,
            activeClients: 1, executedQuantity: 1, tradedValue: 10, buyFills: 1, sellFills: 0
        }]);
        expect(component.trendTicks()).toEqual([{ date: '2026-10-01', x: 50, secondary: false }]);
    });

    it('focuses the metric dialog, traps Tab, and restores focus on Escape', () => {
        const card = fixture.nativeElement.querySelector('.metric-card') as HTMLButtonElement;
        expect(card.tagName).toBe('BUTTON');
        card.focus();
        card.click();
        fixture.detectChanges();
        const close = fixture.nativeElement.querySelector('.metric-modal-close') as HTMLButtonElement;
        expect(document.activeElement).toBe(close);
        const tab = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true });
        close.dispatchEvent(tab);
        expect(tab.defaultPrevented).toBe(true);
        expect(document.activeElement).toBe(close);
        close.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
        fixture.detectChanges();
        expect(component.expandedMetric()).toBeNull();
        expect(document.activeElement).toBe(card);
    });
    it('distinguishes draft filters from applied results and refreshes the applied scope', () => {
        const applied = { ...component.appliedFilters() };
        component.currency.set('USD');
        fixture.detectChanges();
        const host = fixture.nativeElement as HTMLElement;
        expect(host.querySelector('.draft-notice')?.textContent).toContain('not been applied');
        expect(host.querySelector('.applied-scope')?.textContent).toContain('GBP');
        component.refreshApplied();
        expect(reportingService.getInsights).toHaveBeenLastCalledWith(applied);
        expect(component.currency()).toBe('USD');
        component.discardDraft();
        fixture.detectChanges();
        expect(component.currency()).toBe('GBP');
        expect(host.querySelector('.draft-notice')).toBeNull();
    });

    it('keeps successful data visible after a failed apply and retries that exact request despite later draft edits', () => {
        reportingService.getInsights.mockReturnValueOnce(throwError(() => ({ status: 503 })));
        component.currency.set('USD');
        component.applyFilters();
        component.currency.set('INR');
        fixture.detectChanges();
        const host = fixture.nativeElement as HTMLElement;
        expect(host.querySelector('.error-state')?.textContent).toContain('Retry');
        expect(host.querySelectorAll('.metric-card').length).toBeGreaterThan(0);
        expect(host.querySelector('.applied-scope')?.textContent).toContain('GBP');
        expect(host.textContent).not.toContain('backend logs');
        component.retryReporting();
        expect(reportingService.getInsights).toHaveBeenLastCalledWith(expect.objectContaining({ currency: 'USD' }));
        expect(component.reportedCurrency()).toBe('USD');
        expect(component.currency()).toBe('INR');
    });

    it('validates a reversed date range before querying and retains the applied results', () => {
        const calls = reportingService.getInsights.mock.calls.length;
        component.fromDate.set('2026-10-20');
        component.toDate.set('2026-10-01');
        component.applyFilters();
        fixture.detectChanges();
        expect(reportingService.getInsights).toHaveBeenCalledTimes(calls);
        expect(fixture.nativeElement.querySelector('.filter-error').textContent).toContain('start date');
        expect(component.summary()?.filledOrders).toBe(7);
    });

    it('places sparse observations by calendar date, preserves explicit zeros, and exposes point details by keyboard', () => {
        component.appliedFilters.set({ fromDate: '2026-10-01', toDate: '2026-10-06', currency: 'GBP' });
        component.trends.set([1, 3, 6].map(day => ({
            date: '2026-10-0' + day, totalOrders: 2, filledOrders: day === 3 ? 0 : 2,
            rejectedOrders: 0, activeClients: 1, executedQuantity: 1, tradedValue: 10, buyFills: 1, sellFills: 1
        })));
        fixture.detectChanges();
        expect(component.dailyCharts().orders.points.map(point => point.x)).toEqual([0, 240, 600]);
        expect(component.dailyCharts().orders.path).toBe('M0,22 M240,190 M600,22');
        component.trends.update(rows => [...rows, { ...rows[0], date: '2026-09-30', buyFills: 999 }]);
        expect(component.buySellAxisLabels()).toEqual([2, 1, 0]);
        const host = fixture.nativeElement as HTMLElement;
        expect(host.querySelector('.chart-note')?.textContent).toContain('Gaps');
        const zero = host.querySelectorAll<SVGElement>('.orders-point')[1];
        zero.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
        fixture.detectChanges();
        expect(component.graphDialog()?.series[0].value).toBe('0');
        expect(host.querySelector('.chart-hover-dialog')?.textContent).toContain('2026');
    });

    it('shows one sparse observation at its date rather than implying a continuous trend', () => {
        component.appliedFilters.set({ fromDate: '2026-10-01', toDate: '2026-10-05', currency: 'GBP' });
        component.trends.set([{
            date: '2026-10-02', totalOrders: 1, filledOrders: 1, rejectedOrders: 0,
            activeClients: 1, executedQuantity: 1, tradedValue: 0.00001, buyFills: 1, sellFills: 0
        }]);
        fixture.detectChanges();
        expect(component.dailyCharts().orders.points[0].x).toBe(150);
        expect(component.dailyCharts().orders.path).not.toContain('L');
        expect(component.chartNote('orders')).toContain('One daily observation');
        expect(component.dailyCharts().orders.labels).toEqual([2, 1, 0]);
        expect(component.formatMoney(0.00001, 'USD')).toContain('0.00001');
    });

    it('handles a fully zero-filled reporting period with explicit empty states', () => {
        component.appliedFilters.set({ fromDate: '2026-10-01', toDate: '2026-10-03', currency: 'GBP' });
        component.trends.set([1, 2, 3].map(day => ({
            date: '2026-10-0' + day, totalOrders: 0, filledOrders: 0, rejectedOrders: 0,
            activeClients: 0, executedQuantity: 0, tradedValue: 0, buyFills: 0, sellFills: 0
        })));
        fixture.detectChanges();
        const host = fixture.nativeElement as HTMLElement;
        expect(host.querySelector('.line-chart svg')).toBeNull();
        expect(host.querySelector('.buy-sell-history')?.textContent).toContain('No BUY or SELL fills');
        expect(host.querySelector('.client-line-chart')?.textContent).toContain('No client activity');
        expect(host.innerHTML).not.toContain('NaN');
    });

    it('makes every instrument, client and activity row reachable through the table pagers', () => {
        component.instruments.set(Array.from({ length: 23 }, (_, i) => ({ instrumentId: i + 1, instrumentSymbol: 'Stock' + (i + 1), currency: 'GBP', fillCount: 23 - i, executedQuantity: 1, tradedValue: 100 - i })));
        component.clients.set(Array.from({ length: 13 }, (_, i) => ({ clientId: i + 1, clientName: 'Client' + (i + 1), currency: 'GBP', orderCount: 13 - i, fillCount: 1, executedQuantity: 1, tradedValue: 10 })));
        component.activity.set(Array.from({ length: 21 }, (_, i) => ({ orderId: i + 1, fillId: i + 1, accountId: 1, clientId: 1, instrumentId: 1, instrumentSymbol: 'Stock1', side: 'BUY', status: 'FILLED', orderQuantity: 1, executionQuantity: 1, executionPrice: 10, tradedValue: 10, occurredAt: '2026-10-01T12:00:00' })));
        fixture.detectChanges();
        const pagers = fixture.nativeElement.querySelectorAll('app-result-pagination') as NodeListOf<HTMLElement>;
        expect(pagers.length).toBe(4);
        for (const pager of pagers) {
            const next = [...pager.querySelectorAll('button')].find(button => button.textContent?.trim() === 'Next')!;
            expect(next.disabled).toBe(false);
            next.click();
        }
        fixture.detectChanges();
        expect(component.instrumentRows('instruments')[0].instrumentId).toBe(11);
        expect(component.instrumentRows('values')[0].instrumentId).toBe(11);
        expect(component.clientRows().map(row => row.clientId)).toEqual([11, 12, 13]);
        expect(component.activityRows()[0].orderId).toBe(11);
        component.changePageSize(25);
        expect(component.pages().activity).toBe(1);
        expect(component.activityRows().length).toBe(21);
    });

    it('restores applied filters and table pages for the same user without saving draft edits or result rows', () => {
        localStorage.setItem('accessToken', 'test.' + btoa(JSON.stringify({ sub: 'priya-goal4' })) + '.signature');
        try {
            const instruments = Array.from({ length: 21 }, (_, i) => ({ instrumentId: i + 1, instrumentSymbol: 'PrivateStock' + i, currency: 'INR', fillCount: 1, executedQuantity: 1, tradedValue: 10 }));
            reportingService.getInstruments.mockReturnValue(of(instruments));
            component.currency.set('INR');
            component.applyFilters();
            component.changePage('instruments', 2);
            component.selectReportView('data');
            component.side.set('SELL');
            fixture.destroy();
            fixture = TestBed.createComponent(AnalystDashboardComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            expect(reportingService.getInsights).toHaveBeenLastCalledWith(expect.objectContaining({ currency: 'INR', side: undefined }));
            expect(component.pages().instruments).toBe(2);
            expect(component.activeView()).toBe('data');
            const key = Object.keys(sessionStorage).find(key => key.includes('priya-goal4'))!;
            expect(sessionStorage.getItem(key)).not.toContain('PrivateStock');
            localStorage.setItem('accessToken', 'test.' + btoa(JSON.stringify({ sub: 'different-user' })) + '.signature');
            const other = TestBed.createComponent(AnalystDashboardComponent);
            expect(other.componentInstance.currency()).toBe('GBP');
            other.destroy();
        } finally {
            localStorage.removeItem('accessToken');
            sessionStorage.clear();
        }
    });

    it('switches report views without applying draft filters or resetting result pages', () => {
        component.instruments.set(Array.from({ length: 21 }, (_, i) => ({ instrumentId: i + 1, instrumentSymbol: 'Stock' + i, currency: 'GBP', fillCount: 1, executedQuantity: 1, tradedValue: 10 })));
        component.changePage('instruments', 2);
        component.currency.set('INR');
        const callsBefore = reportingService.getInsights.mock.calls.length;
        const buttons = [...fixture.nativeElement.querySelectorAll('.report-views button')] as HTMLButtonElement[];
        const viewIds = ['overview', 'insights', 'trends', 'data'];
        for (const [index, button] of buttons.entries()) {
            button.click();
            fixture.detectChanges();
            expect(component.activeView()).toBe(viewIds[index]);
            expect(button.getAttribute('aria-pressed')).toBe('true');
            const panels = [...fixture.nativeElement.querySelectorAll('[id^="reports-view-"]')] as HTMLElement[];
            expect(panels.filter(panel => !panel.hidden).map(panel => panel.id)).toEqual(['reports-view-' + viewIds[index]]);
        }
        expect(component.pages().instruments).toBe(2);
        expect(component.currency()).toBe('INR');
        expect(component.appliedFilters().currency).toBe('GBP');
        expect(reportingService.getInsights).toHaveBeenCalledTimes(callsBefore);
    });

});
