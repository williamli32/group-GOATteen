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