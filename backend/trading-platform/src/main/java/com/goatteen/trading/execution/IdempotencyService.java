package com.goatteen.trading.execution;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for tracking idempotent request execution.
 * 
 * Prevents duplicate order execution by storing a mapping of idempotency keys
 * to Fill results. If the same idempotency key is received again, returns the
 * cached fill result instead of creating a new fill.
 * 
 * NOTE: This implementation uses in-memory storage. For production systems that
 * require persistence across service restarts, implement with a database table
 * or distributed cache (Redis).
 */
@Service
public class IdempotencyService {

    /**
     * Map of idempotency key -> Fill ID for fast lookup.
     * ConcurrentHashMap ensures thread-safe access for concurrent requests.
     */
    private final Map<String, Long> idempotencyKeyToFillId = new ConcurrentHashMap<>();

    /**
     * Check if a fill already exists for this idempotency key.
     *
     * @param idempotencyKey Unique key provided by client for this order execution
     * @return Fill ID if fill exists for this key, null otherwise
     */
    public Long getFillIdByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return idempotencyKeyToFillId.get(idempotencyKey);
    }

    /**
     * Record a new fill execution with its idempotency key.
     * Should only be called after a fill is successfully created in the database.
     *
     * @param idempotencyKey Unique key provided by client
     * @param fillId ID of the created fill
     */
    public void recordFillExecution(String idempotencyKey, Long fillId) {
        if (idempotencyKey != null && !idempotencyKey.isBlank() && fillId != null) {
            idempotencyKeyToFillId.put(idempotencyKey, fillId);
        }
    }

    /**
     * Clear all tracked idempotency keys (useful for testing or cleanup).
     */
    public void clearAll() {
        idempotencyKeyToFillId.clear();
    }

    /**
     * Get the number of tracked idempotency keys (useful for monitoring).
     */
    public int getTrackedKeyCount() {
        return idempotencyKeyToFillId.size();
    }
}
