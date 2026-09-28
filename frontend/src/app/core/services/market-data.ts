import {
  Injectable
} from '@angular/core';

import {
  HttpClient
} from '@angular/common/http';

import {
  Observable
} from 'rxjs';

import {
  environment
} from '../../../environments/environment';


export interface LatestQuoteResponse {
  quoteId: number;
  instrumentId: number;
  symbol: string;
  bidPrice: number;
  askPrice: number;
  lastPrice: number;
  quotedAt: string;
}


export interface MarketInstrumentResponse {
  instrumentId: number;
  symbol: string;
  name: string;
  instrumentClass: string;
  exchange: string;
  countryCode: string;
  currency: string;
  tradable: boolean;
  latestQuote: LatestQuoteResponse | null;
}


export interface HistoricalPrice {
  symbol: string;
  date: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}


@Injectable({
  providedIn: 'root'
})
export class MarketDataService {

  /*
   * Existing trading-platform API.
   *
   * Used for:
   * - Tradable instruments.
   * - Current quotes.
   */
  private marketApiUrl =
    `${environment.apiUrl}/market`;

  /*
   * Separate market-data-service API.
   *
   * Used for:
   * - Historical CSV data.
   */
  private historicalApiUrl =
    `${environment.marketDataApiUrl}/market-data`;


  constructor(
    private http: HttpClient
  ) { }


  getInstruments():
    Observable<MarketInstrumentResponse[]> {

    return this.http.get<
      MarketInstrumentResponse[]
    >(
      `${this.marketApiUrl}/instruments`
    );
  }


  getLatestQuote(
    instrumentId: number
  ): Observable<LatestQuoteResponse> {

    return this.http.get<
      LatestQuoteResponse
    >(
      `${this.marketApiUrl}/instruments/${instrumentId}/quote`
    );
  }


  getHistoricalData(
    symbol: string
  ): Observable<HistoricalPrice[]> {

    return this.http.get<
      HistoricalPrice[]
    >(
      `${this.historicalApiUrl}/history/${encodeURIComponent(symbol)}`
    );
  }
}