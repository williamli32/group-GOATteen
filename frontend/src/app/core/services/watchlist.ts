import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MarketInstrumentResponse } from './market-data';

@Injectable({ providedIn: 'root' })
export class WatchlistService {
  private readonly http = inject(HttpClient);
  private readonly url = environment.apiUrl + '/watchlist';

  getWatchlist(): Observable<MarketInstrumentResponse[]> {
    return this.http.get<MarketInstrumentResponse[]>(this.url);
  }

  add(instrumentId: number): Observable<void> {
    return this.http.put<void>(this.url + '/' + instrumentId, null);
  }

  remove(instrumentId: number): Observable<void> {
    return this.http.delete<void>(this.url + '/' + instrumentId);
  }
}
