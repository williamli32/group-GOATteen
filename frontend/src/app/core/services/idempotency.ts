import { Injectable } from '@angular/core';

/**
 * IdempotencyService
 * 
 * Manages idempotency keys for order operations.
 * Generates unique keys for new requests and stores them in sessionStorage
 * to enable duplicate detection and retry safety.
 * 
 * Keys are scoped to the browser session (cleared on browser close).
 */
@Injectable({
    providedIn: 'root'
})
export class IdempotencyService {

    private readonly STORAGE_PREFIX = 'idempotency_key_';

    /**
     * Generates a new UUID v4 idempotency key
     */
    generateKey(): string {
        return this.uuidv4();
    }

    /**
     * Stores an idempotency key for a given order ID
     * Keys persist across page refreshes within the same session
     */
    storeKey(orderId: number, key: string): void {
        const storageKey = `${this.STORAGE_PREFIX}${orderId}`;
        sessionStorage.setItem(storageKey, key);
    }

    /**
     * Retrieves stored idempotency key for an order ID
     * Returns null if no key has been generated yet
     */
    getKey(orderId: number): string | null {
        const storageKey = `${this.STORAGE_PREFIX}${orderId}`;
        return sessionStorage.getItem(storageKey);
    }

    /**
     * Gets or generates an idempotency key for an order
     * If a key exists, returns it; otherwise generates and stores a new one
     */
    getOrGenerateKey(orderId: number): string {
        let key = this.getKey(orderId);
        if (!key) {
            key = this.generateKey();
            this.storeKey(orderId, key);
        }
        return key;
    }

    /**
     * Clears stored key for an order (after successful completion)
     */
    clearKey(orderId: number): void {
        const storageKey = `${this.STORAGE_PREFIX}${orderId}`;
        sessionStorage.removeItem(storageKey);
    }

    /**
     * Clears all stored idempotency keys
     */
    clearAllKeys(): void {
        const keys = Object.keys(sessionStorage);
        keys.forEach(key => {
            if (key.startsWith(this.STORAGE_PREFIX)) {
                sessionStorage.removeItem(key);
            }
        });
    }

    /**
     * Generates a RFC 4122 compliant UUID v4
     */
    private uuidv4(): string {
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
            const r = (Math.random() * 16) | 0;
            const v = c === 'x' ? r : (r & 0x3) | 0x8;
            return v.toString(16);
        });
    }
}
