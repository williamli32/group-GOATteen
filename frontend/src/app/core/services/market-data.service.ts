import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * Interface for market instrument data returned from REST API
 */
export interface MarketInstrumentResponse {
  instrumentId: number;
  symbol: string;
  name: string;
  instrumentClass: string;
  exchange: string;
  currency: string;
  tradable: boolean;
  latestQuote: LatestQuoteResponse;
}

/**
 * Interface for latest quote data
 */
export interface LatestQuoteResponse {
  quoteId: number;
  instrumentId: number;
  bidPrice: number;
  askPrice: number;
  lastPrice: number;
  quotedAt: string;
}

/**
 * Service for fetching market data from the Trading Platform API
 * Provides access to instrument listings and quote information
 */
@Injectable({
  providedIn: 'root'
})
export class MarketDataService {

  private readonly apiUrl = environment.apiUrl || 'http://localhost:8080';

  constructor(private http: HttpClient) { }

  /**
   * Get all tradable market instruments with their latest quotes
   * @returns Observable<MarketInstrumentResponse[]> - List of instruments with latest prices
   */
  getInstruments(): Observable<MarketInstrumentResponse[]> {
    return this.http.get<MarketInstrumentResponse[]>(`${this.apiUrl}/api/market/instruments`);
  }

  /**
   * Get the latest quote for a specific instrument
   * @param instrumentId - The ID of the instrument
   * @returns Observable<LatestQuoteResponse> - Latest price quote for the instrument
   */
  getLatestQuote(instrumentId: number): Observable<LatestQuoteResponse> {
    return this.http.get<LatestQuoteResponse>(`${this.apiUrl}/api/market/instruments/${instrumentId}/quote`);
  }
}
