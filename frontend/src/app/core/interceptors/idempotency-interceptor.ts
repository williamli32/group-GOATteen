import { inject } from '@angular/core';
import { HttpInterceptorFn } from '@angular/common/http';
import { IdempotencyService } from '../services/idempotency';

/**
 * Idempotency Interceptor
 * 
 * Automatically adds Idempotency-Key header to POST requests targeting order endpoints.
 * This ensures that duplicate requests (due to network errors, retries, etc.) 
 * are safely deduplicated by the backend.
 * 
 * Applies to:
 * - POST /api/orders (order submission)
 * - POST /api/orders/execute/* (order execution)
 */
export const idempotencyInterceptor: HttpInterceptorFn = (req, next) => {
    
    const idempotencyService = inject(IdempotencyService);

    // Check if this is a POST request to order endpoints
    const isOrderSubmission = req.method === 'POST' && req.url.includes('/api/orders');
    const isOrderExecution = req.method === 'POST' && req.url.includes('/api/orders/execute/');

    if (isOrderSubmission || isOrderExecution) {
        
        // Extract order ID from URL for key storage
        let orderId: number | null = null;
        
        if (isOrderExecution) {
            // URL format: /api/orders/execute/{orderId}
            const match = req.url.match(/\/execute\/(\d+)/);
            if (match) {
                orderId = parseInt(match[1], 10);
            }
        } else if (isOrderSubmission && req.body) {
            // URL format: /api/orders, extract from body if available
            const body = req.body as any;
            if (body.orderId) {
                orderId = body.orderId;
            }
        }

        // Generate or retrieve idempotency key
        let idempotencyKey: string;
        
        if (orderId) {
            // For requests with identifiable order ID, reuse existing key if available
            idempotencyKey = idempotencyService.getOrGenerateKey(orderId);
        } else {
            // For new order submissions without ID, generate fresh key
            idempotencyKey = idempotencyService.generateKey();
        }

        // Clone request and add Idempotency-Key header
        req = req.clone({
            setHeaders: {
                'Idempotency-Key': idempotencyKey
            }
        });
    }

    return next(req);
};
