import {
    Injectable
} from '@angular/core';

import {
    HttpClient
} from '@angular/common/http';

import {
    Observable
} from 'rxjs';


export interface MarketData {
    symbol: string;
    market: string;
    name: string;
    price: number;
    change: number;
    changePercent: number;
    high: number;
    low: number;
    volume: number;
    timestamp: string;
}


@Injectable({
    providedIn: 'root'
})
export class MarketDataMicroserviceClient {

    private apiUrl = 'http://localhost:8081/api/market-data';


    constructor(
        private http: HttpClient
    ) { }


    /**
     * Get all market data from the microservice
     */
    getAllMarketData():
        Observable<MarketData[]> {

        return this.http.get<
            MarketData[]
        >(
            this.apiUrl
        );

    }


    /**
     * Get market data for a specific market/country
     * @param market Country code (e.g., 'US', 'GB', 'JP')
     */
    getMarketDataByMarket(
        market: string
    ): Observable<MarketData[]> {

        return this.http.get<
            MarketData[]
        >(
            `${this.apiUrl}/market/${market}`
        );

    }


    /**
     * Get market data for a specific symbol
     * @param symbol Stock symbol (e.g., 'AAPL', 'MSFT')
     */
    getMarketDataBySymbol(
        symbol: string
    ): Observable<MarketData> {

        return this.http.get<
            MarketData
        >(
            `${this.apiUrl}/symbol/${symbol}`
        );

    }

}
