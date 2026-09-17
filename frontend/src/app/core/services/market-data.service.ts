import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class MarketDataService {

  private readonly MARKET_DATA_URL = 'http://localhost:8081/api/market-data';

  constructor(private http: HttpClient) { }

  getAllMarketData(): Observable<any[]> {
    return this.http.get<any[]>(this.MARKET_DATA_URL);
  }

  getMarketByType(market: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.MARKET_DATA_URL}/market/${market}`);
  }

  getSymbolData(symbol: string): Observable<any> {
    return this.http.get<any>(`${this.MARKET_DATA_URL}/symbol/${symbol}`);
  }
}