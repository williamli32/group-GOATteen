import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';


export interface AccountResponse {
  accountId: number;
  accountNumber: string;
  cashBalance: number;
  currency: string;
  firstName: string;
  lastName: string;
}

export interface AddFundsResponse {
  accountId: number;
  transactionId: number;
  amount: number;
  currency: string;
  cashBalance: number;
  createdAt: string;
  replayed: boolean;
}


export interface PositionResponse {
  instrumentId: number;
  symbol: string;
  instrumentName: string;
  quantity: number;
}


export interface HoldingsResponse {
  accountId: number;
  cashBalance: number;
  currency: string;
  positions: PositionResponse[];
}


export interface OrderResponse {
  id: number;
  side: string;
  quantity: number;
  status: string;
  rejectionReason: string | null;
  fillPrice: number | null;
  fillCurrency: string;
  accountCurrency: string;
  fxRate: number | null;
  settlementAmount: number | null;
  submittedAt: string;
  filledAt: string | null;
}


@Injectable({
  providedIn: 'root'
})
export class AccountService {

  private apiUrl =
    `${environment.apiUrl}/accounts`;


  constructor(
    private http: HttpClient
  ) {}


  getMyAccount(): Observable<AccountResponse> {

    return this.http.get<AccountResponse>(
      `${this.apiUrl}/me`
    );

  }

  addFunds(amount: string, idempotencyKey: string): Observable<AddFundsResponse> {
    return this.http.post<AddFundsResponse>(`${this.apiUrl}/me/funds`, { amount, idempotencyKey });
  }


  getHoldings(): Observable<HoldingsResponse> {

    return this.http.get<HoldingsResponse>(
      `${this.apiUrl}/me/holdings`
    );

  }


  getBlotter(): Observable<OrderResponse[]> {

    return this.http.get<OrderResponse[]>(
      `${this.apiUrl}/me/blotter`
    );

  }

}
