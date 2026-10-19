import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ReportingFilters {
    fromDate: string;
    toDate: string;
    instrumentId?: number;
    side?: 'BUY' | 'SELL';
    status?: string;
    clientId?: number;
    accountId?: number;
    currency?: string;
}

export interface ReportingSummary {
    totalOrders: number;
    filledOrders: number;
    rejectedOrders: number;
    activeClients: number;
    fillRate: number;
    rejectionRate: number;
    totalExecutedQuantity: number;
    totalTradedValue: number;
    buyFills: number;
    buyQuantity: number;
    buyTradedValue: number;
    sellFills: number;
    sellQuantity: number;
    sellTradedValue: number;
}

export interface ReportingActivity {
    orderId: number;
    fillId: number | null;
    accountId: number;
    clientId: number;
    instrumentId: number;
    instrumentSymbol: string;
    side: 'BUY' | 'SELL';
    status: string;
    orderQuantity: number;
    executionQuantity: number | null;
    executionPrice: number | null;
    tradedValue: number | null;
    occurredAt: string;
}

export interface InstrumentMetrics {
    instrumentId: number;
    instrumentSymbol: string;
    currency: string;
    fillCount: number;
    executedQuantity: number;
    tradedValue: number;
}

export interface ClientMetrics {
    clientId: number;
    clientName: string;
    currency: string;
    orderCount: number;
    fillCount: number;
    executedQuantity: number;
    tradedValue: number;
}

export interface ActivityTrendPoint {
    date: string;
    totalOrders: number;
    filledOrders: number;
    rejectedOrders: number;
    activeClients: number;
    executedQuantity: number;
    tradedValue: number;
    buyFills: number;
    sellFills: number;
}

export interface ReportingWindow {
    fromDate: string;
    toDate: string;
}

export interface InstrumentInsight {
    instrumentId: number;
    symbol: string;
    fillCount: number;
    previousFillCount: number;
    changePercent: number | null;
    newlyActive: boolean;
}

export interface BusinessInsights {
    currentWindow: ReportingWindow;
    previousWindow: ReportingWindow;
    currency: string;
    current: ReportingSummary;
    previous: ReportingSummary;
    currentFillCount: number;
    previousFillCount: number;
    volumeChangePercent: number | null;
    buySharePercent: number | null;
    sellSharePercent: number | null;
    mostActiveInstrument: InstrumentInsight | null;
    fastestGrowingInstrument: InstrumentInsight | null;
    newlyActiveInstruments: InstrumentInsight[];
    highestVolumeDay: { date: string; fillCount: number } | null;
    rejectionRateChangePercentagePoints: number | null;
    rejectionTrend: Array<{
        date: string;
        totalOrders: number;
        rejectedOrders: number;
        rejectionRate: number | null;
    }>;
}

@Injectable({ providedIn: 'root' })
export class ReportingService {
    private readonly reportingUrl = `${environment.apiUrl}/internal/reporting`;

    constructor(private readonly http: HttpClient) { }

    getInsights(filters: ReportingFilters): Observable<BusinessInsights> {
        return this.http.get<BusinessInsights>(this.reportingUrl + '/insights', {
            params: this.toParams(filters)
        });
    }

    getSummary(filters: ReportingFilters): Observable<ReportingSummary> {
        return this.http.get<ReportingSummary>(`${this.reportingUrl}/summary`, {
            params: this.toParams(filters)
        });
    }

    getActivity(filters: ReportingFilters): Observable<ReportingActivity[]> {
        return this.http.get<ReportingActivity[]>(`${this.reportingUrl}/activity`, {
            params: this.toParams(filters)
        });
    }

    getInstruments(filters: ReportingFilters): Observable<InstrumentMetrics[]> {
        return this.http.get<InstrumentMetrics[]>(`${this.reportingUrl}/instruments`, {
            params: this.toParams(filters)
        });
    }

    getClients(filters: ReportingFilters): Observable<ClientMetrics[]> {
        return this.http.get<ClientMetrics[]>(`${this.reportingUrl}/clients`, {
            params: this.toParams(filters)
        });
    }

    getTrends(filters: ReportingFilters): Observable<ActivityTrendPoint[]> {
        return this.http.get<ActivityTrendPoint[]>(`${this.reportingUrl}/trends`, {
            params: this.toParams(filters)
        });
    }

    private toParams(filters: ReportingFilters): HttpParams {
        let params = new HttpParams()
            .set('fromDate', filters.fromDate)
            .set('toDate', filters.toDate);

        if (filters.instrumentId !== undefined) {
            params = params.set('instrumentId', filters.instrumentId);
        }
        if (filters.side !== undefined) {
            params = params.set('side', filters.side);
        }
        if (filters.status !== undefined) {
            params = params.set('status', filters.status);
        }
        if (filters.clientId !== undefined) {
            params = params.set('clientId', filters.clientId);
        }
        if (filters.accountId !== undefined) {
            params = params.set('accountId', filters.accountId);
        }

        if (filters.currency !== undefined) {
            params = params.set('currency', filters.currency);
        }

        return params;
    }
}
