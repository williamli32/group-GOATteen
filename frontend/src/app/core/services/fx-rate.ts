// Place at frontend/src/app/core/services/fx-rate.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface FxQuote {
  from: string;
  to: string;
  rate: number;
  asOf: string;
  provider: string;
}

@Injectable({ providedIn: 'root' })
export class FxRateService {
  constructor(private http: HttpClient) {}

  quote(from: string, to: string): Observable<FxQuote> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<FxQuote>(`${environment.apiUrl}/fx/rate`, { params });
  }
}
