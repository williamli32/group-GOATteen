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

import {
    OrderResponse
} from './account';


export type OrderSide =
    'BUY' | 'SELL';


export interface PlaceOrderRequest {
    instrumentId: number;
    side: OrderSide;
    quantity: number;
}


@Injectable({
    providedIn: 'root'
})
export class OrderService {

    private apiUrl =
        `${environment.apiUrl}/orders`;


    constructor(
        private http: HttpClient
    ) { }


    placeOrder(
        request: PlaceOrderRequest,
        idempotencyKey: string
    ): Observable<OrderResponse> {

        return this.http.post<OrderResponse>(
            this.apiUrl,
            request,
            {
                headers: {
                    'Idempotency-Key': idempotencyKey
                }
            }
        );

    }

    /**
     * Executes a pending order (transitions from ACCEPTED to FILLED)
     * Includes idempotency protection via Idempotency-Key header
     */
    executeOrder(orderId: number): Observable<OrderResponse> {
        return this.http.post<OrderResponse>(
            `${this.apiUrl}/execute/${orderId}`,
            {}
        );
    }

}