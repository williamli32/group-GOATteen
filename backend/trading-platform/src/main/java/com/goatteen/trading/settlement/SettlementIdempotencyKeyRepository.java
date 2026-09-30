package com.goatteen.trading.settlement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Settlement Idempotency Key Repository
 * 
 * Queries the settlement_idempotency_keys table to detect duplicate settlement attempts.
 * Prevents re-execution of settlements across application restarts.
 */
@Repository
public interface SettlementIdempotencyKeyRepository extends JpaRepository<SettlementIdempotencyKey, Long> {

    /**
     * Find idempotency key record by the key string
     * 
     * CRITICAL: Checks if this exact settlement attempt has been done before.
     * 
     * Example flow:
     *   1. Client: "Settle order 123 with key 'abc-def-ghi'"
     *   2. We check: findByIdempotencyKey("abc-def-ghi")
     *   3. If found: Return cached result (don't re-execute!)
     *   4. If not found: Execute settlement, store key and result
     * 
     * The UNIQUE constraint on idempotency_key in the database ensures
     * only one record can exist per key (prevents race conditions).
     * 
     * @param idempotencyKey The idempotency key (typically a UUID)
     * @return Optional containing the key record if found
     */
    Optional<SettlementIdempotencyKey> findByIdempotencyKey(String idempotencyKey);

    /**
     * Find idempotency key for a specific order and key
     * 
     * More specific than findByIdempotencyKey.
     * Used to verify: "Did THIS order settle with THIS key before?"
     * 
     * Example:
     *   Order 123 settlement attempt 1: key="first-attempt"  → SUCCESS
     *   Order 123 settlement attempt 2: key="first-attempt"  → Already done, return SUCCESS
     *   Order 123 settlement attempt 3: key="second-attempt" → New key, execute fresh
     * 
     * @param orderId The order ID
     * @param idempotencyKey The idempotency key
     * @return Optional containing the key record if found for this order and key
     */
    Optional<SettlementIdempotencyKey> findByOrderIdAndIdempotencyKey(Long orderId, String idempotencyKey);
}