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
     * Checks if this exact settlement attempt has been done before.
     * @param idempotencyKey The idempotency key (typically a UUID)
     * @return Optional containing the key record if found
     */
    Optional<SettlementIdempotencyKey> findByIdempotencyKey(String idempotencyKey);

    /**
     * Find idempotency key for a specific order and key
     * 
     * More specific than findByIdempotencyKey.
     * Used to verify: "Did THIS order settle with THIS key before?"
    
     * @param orderId The order ID
     * @param idempotencyKey The idempotency key
     * @return Optional containing the key record if found for this order and key
     */
    Optional<SettlementIdempotencyKey> findByOrderIdAndIdempotencyKey(Long orderId, String idempotencyKey);
}