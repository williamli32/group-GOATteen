import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AnalystDashboardComponent } from './analyst-dashboard.component';
import { ReportingService } from '../core/services/reporting';
import { MarketDataService } from '../core/services/market-data';

describe('AnalystDashboardComponent', () => {
    let component: AnalystDashboardComponent;
    let fixture: ComponentFixture<AnalystDashboardComponent>;
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
            getSummary: vi.fn(() => of(summary)),
            getTrends: vi.fn(() => of([])),
            getInstruments: vi.fn(() => of([])),
            getClients: vi.fn(() => of([])),
            getActivity: vi.fn(() => of([]))
        };
        marketDataService = {
            getInstruments: vi.fn(() => of([
                {
                    instrumentId: 7,
                    symbol: 'AAPL',
                    name: 'Apple Inc.',
                    instrumentClass: 'EQUITY_US',
                    exchange: 'NASDAQ',
                    countryCode: 'US',
                    currency: 'USD',
                    tradable: true,
                    latestQuote: null
                }
            ]))
        };

        await TestBed.configureTestingModule({
            imports: [AnalystDashboardComponent],
            providers: [
                { provide: ReportingService, useValue: reportingService },
                { provide: MarketDataService, useValue: marketDataService }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(AnalystDashboardComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('loads every reporting dataset with one shared filter set', () => {
        component.fromDate.set('2026-09-01');
        component.toDate.set('2026-09-30');
        component.side.set('BUY');
        component.status.set('FILLED');
        component.clientId.set(42);
        component.applyFilters();

        const expected = expect.objectContaining({
            fromDate: '2026-09-01',
            toDate: '2026-09-30',
            side: 'BUY',
            status: 'FILLED',
            clientId: 42
        });

        expect(reportingService.getSummary).toHaveBeenCalledWith(expected);
        expect(reportingService.getTrends).toHaveBeenCalledWith(expected);
        expect(reportingService.getInstruments).toHaveBeenCalledWith(expected);
        expect(reportingService.getClients).toHaveBeenCalledWith(expected);
        expect(reportingService.getActivity).toHaveBeenCalledWith(expected);
        expect(component.summary()).toEqual(summary);
    });

    it('shows an internal access error when reporting is forbidden', () => {
        reportingService.getSummary.mockReturnValue(throwError(() => ({ status: 403 })));
        component.applyFilters();

        expect(component.errorMessage()).toContain('internal reporting access');
        expect(component.loading()).toBe(false);
    });

    it('explains when the session has expired', () => {
        reportingService.getSummary.mockReturnValue(throwError(() => ({ status: 401 })));
        component.applyFilters();

        expect(component.errorMessage()).toContain('session has expired');
    });

    it('surfaces backend reporting failures', () => {
        reportingService.getSummary.mockReturnValue(throwError(() => ({ status: 500 })));
        component.applyFilters();

        expect(component.errorMessage()).toContain('reporting service returned an error (500)');
    });

    it('resets all optional filters before reloading the page', () => {
        component.instrumentId.set(7);
        component.side.set('SELL');
        component.status.set('REJECTED');
        component.clientId.set(42);

        component.resetFilters();

        expect(component.instrumentId()).toBeUndefined();
        expect(component.side()).toBeUndefined();
        expect(component.status()).toBeUndefined();
        expect(component.clientId()).toBeUndefined();
        expect(reportingService.getSummary).toHaveBeenLastCalledWith(
            expect.objectContaining({
                instrumentId: undefined,
                side: undefined,
                status: undefined,
                clientId: undefined
            })
        );
    });

    it('loads instrument choices independently from reporting results', () => {
        expect(marketDataService.getInstruments).toHaveBeenCalled();
        expect(component.marketInstruments()).toEqual([
            expect.objectContaining({
                instrumentId: 7,
                symbol: 'AAPL'
            })
        ]);
    });

    it('renders empty result state when reporting returns no activity', () => {
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('No instrument data.');
        expect(fixture.nativeElement.textContent).toContain('No recent activity.');
    });
});
