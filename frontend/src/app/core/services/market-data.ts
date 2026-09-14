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
    currency: string;
    tradable: boolean;
    latestQuote: LatestQuoteResponse | null;
}


@Injectable({
    providedIn: 'root'
})
export class MarketDataService {

    private apiUrl =
        `${environment.apiUrl}/market`;


    constructor(
        private http: HttpClient
    ) { }


    getInstruments():
        Observable<MarketInstrumentResponse[]> {

        return this.http.get<
            MarketInstrumentResponse[]
        >(
            `${this.apiUrl}/instruments`
        );

    }


    getLatestQuote(
        instrumentId: number
    ): Observable<LatestQuoteResponse> {

        return this.http.get<
            LatestQuoteResponse
        >(
            `${this.apiUrl}/instruments/${instrumentId}/quote`
        );

    }

}