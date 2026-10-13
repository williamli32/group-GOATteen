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

    it('requests instrument metrics through the internal reporting API', () => {
        service.getInstruments({
            fromDate: '2026-09-01',
            toDate: '2026-09-30',
            instrumentId: 1,
            side: 'BUY',
            status: 'FILLED',
            clientId: 2,
            accountId: 3
        }).subscribe();

        const request = httpTesting.expectOne(
            `${environment.apiUrl}/internal/reporting/instruments` +
            '?fromDate=2026-09-01&toDate=2026-09-30&instrumentId=1&side=BUY' +
            '&status=FILLED&clientId=2&accountId=3'
        );

        expect(request.request.method).toBe('GET');
        request.flush([]);
    });
});
