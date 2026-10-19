import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface OperationsOrder {
    orderId: number;
    clientId: number;
    clientName: string;
    accountId: number;
    accountNumber: string;
    instrumentId: number;
    instrumentSymbol: string;
    side: 'BUY' | 'SELL';
    status: string;
    quantity: number;
    submittedAt: string;
}

export interface TradeAuditResponse {
    order: { orderId: number; side: string; quantity: number; submittedAt: string };
    account: { accountId: number; currency: string };
    instrument: { instrumentId: number; symbol: string; currency: string };
    currentStatus: string;
    statusHistory: Array<{ historyId: number; status: string; changedAt: string; note: string | null }>;
    rejectionReason: string | null;
    completedAt: string | null;
    execution: {
        fillId: number;
        fillQuantity: number;
        executionPrice: number;
        executedAt: string;
        quoteId: number;
        bidPrice: number;
        askPrice: number;
        quotedAt: string;
    } | null;
    cashImpact: {
        transactions: Array<{ transactionId: number; amount: number; balanceAfter: number; createdAt: string; description: string | null }>;
        totalMovement: number;
        balanceAfter: number | null;
    };
    positionImpact: {
        quantityBefore: number;
        quantityAfter: number;
        quantityChange: number;
        recordedAt: string;
    } | null;
    fullyReconstructable: boolean;
}

export interface OperationsAuditResponse {
    evidence: TradeAuditResponse;
    integrity: { orderId: number; valid: boolean; message: string };
    pending: boolean;
}

export interface OperationsFilters {
    orderId?: number;
    clientFirstName?: string;
    clientLastName?: string;
    accountId?: number;
    instrumentSymbol?: string;
    side?: 'BUY' | 'SELL';
    status?: string;
    fromDate?: string;
    toDate?: string;
}

@Injectable({ providedIn: 'root' })
export class OperationsService {
    private readonly baseUrl = `${environment.apiUrl}/internal/operations`;

    constructor(private readonly http: HttpClient) { }

    searchOrders(filters: OperationsFilters): Observable<OperationsOrder[]> {
        let params = new HttpParams();
        Object.entries(filters).forEach(([key, value]) => {
            if (value !== undefined && value !== null && value !== '') {
                params = params.set(key, value);
            }
        });
        return this.http.get<OperationsOrder[]>(`${this.baseUrl}/orders`, { params });
    }

    inspectOrder(orderId: number): Observable<OperationsAuditResponse> {
        return this.http.get<OperationsAuditResponse>(`${this.baseUrl}/orders/${orderId}/audit`);
    }
}
