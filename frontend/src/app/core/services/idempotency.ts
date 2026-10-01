import { Injectable } from '@angular/core';

@Injectable({
    providedIn: 'root'
})
export class IdempotencyService {

    private readonly storageKey =
        'leap_pending_order_idempotency_key';


    getOrCreateKey(): string {

        let key =
            sessionStorage.getItem(
                this.storageKey
            );

        if (!key) {

            key =
                crypto.randomUUID();

            sessionStorage.setItem(
                this.storageKey,
                key
            );
        }

        return key;
    }


    clearKey(): void {

        sessionStorage.removeItem(
            this.storageKey
        );

    }

}