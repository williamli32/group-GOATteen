import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
    HttpTestingController,
    provideHttpClientTesting
} from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import { ReportingService } from './reporting';

describe('ReportingService', () => {
    let service: ReportingService;
    let httpTesting: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                ReportingService,
                provideHttpClient(),
                provideHttpClientTesting()
            ]
        });

        service = TestBed.inject(ReportingService);
        httpTesting = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpTesting.verify());

    it('requests insights with every filter preserved', () => {
        service.getInsights({
            fromDate: '2026-10-01', toDate: '2026-10-16',
            instrumentId: 7, side: 'SELL', status: 'FILLED',
            clientId: 42, accountId: 43, currency: 'USD'
        }).subscribe();
        const request = httpTesting.expectOne(
            environment.apiUrl + '/internal/reporting/insights'
            + '?fromDate=2026-10-01&toDate=2026-10-16&instrumentId=7&side=SELL'
            + '&status=FILLED&clientId=42&accountId=43&currency=USD'
        );
        expect(request.request.method).toBe('GET');
        request.flush({});
    });

    it('requests instrument metrics through the internal reporting API', () => {
        service.getInstruments({
            fromDate: '2026-09-01',
            toDate: '2026-09-30',
            instrumentId: 1,
            side: 'BUY',
            status: 'FILLED',
            clientId: 2,
            accountId: 3,
            currency: 'USD'
        }).subscribe();

        const request = httpTesting.expectOne(
            `${environment.apiUrl}/internal/reporting/instruments` +
            '?fromDate=2026-09-01&toDate=2026-09-30&instrumentId=1&side=BUY' +
            '&status=FILLED&clientId=2&accountId=3&currency=USD'
        );

        expect(request.request.method).toBe('GET');
        request.flush([]);
    });

    it('requests reporting summary', () => {

        service.getSummary({
            fromDate: '2026-09-01',
            toDate: '2026-09-30'
        }).subscribe();

        const request = httpTesting.expectOne(
            `${environment.apiUrl}/internal/reporting/summary` +
            '?fromDate=2026-09-01&toDate=2026-09-30'
        );

        expect(request.request.method).toBe('GET');

        request.flush({
            totalOrders: 0,
            filledOrders: 0,
            rejectedOrders: 0,
            activeClients: 0,
            fillRate: 0,
            rejectionRate: 0,
            totalExecutedQuantity: 0,
            totalTradedValue: 0,
            buyFills: 0,
            buyQuantity: 0,
            buyTradedValue: 0,
            sellFills: 0,
            sellQuantity: 0,
            sellTradedValue: 0
        });
    });

    it('requests reporting activity', () => {

        service.getActivity({
            fromDate: '2026-09-01',
            toDate: '2026-09-30'
        }).subscribe();

        const request = httpTesting.expectOne(
            `${environment.apiUrl}/internal/reporting/activity` +
            '?fromDate=2026-09-01&toDate=2026-09-30'
        );

        expect(request.request.method).toBe('GET');
        request.flush([]);
    });

    it('requests client metrics', () => {

        service.getClients({
            fromDate: '2026-09-01',
            toDate: '2026-09-30'
        }).subscribe();

        const request = httpTesting.expectOne(
            `${environment.apiUrl}/internal/reporting/clients` +
            '?fromDate=2026-09-01&toDate=2026-09-30'
        );

        expect(request.request.method).toBe('GET');
        request.flush([]);
    });

    it('requests reporting trends', () => {

        service.getTrends({
            fromDate: '2026-09-01',
            toDate: '2026-09-30'
        }).subscribe();

        const request = httpTesting.expectOne(
            `${environment.apiUrl}/internal/reporting/trends` +
            '?fromDate=2026-09-01&toDate=2026-09-30'
        );

        expect(request.request.method).toBe('GET');
        request.flush([]);
    });
});
