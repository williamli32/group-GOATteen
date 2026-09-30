package com.goatteen.trading.settlement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Settlement Repository
 * 
 * Queries the settlements table to find settlements by order, status, or incomplete ones.
 * Used by SettlementService to track settlement progress and detect abandoned settlements.
 */
@Repository
public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    /**
     * Find settlement by order ID
     * 
     * One-to-one relationship: Each order has at most one settlement
     * Used to check if settlement already exists before creating new one
     * 
     * @param orderId The order ID
     * @return Optional containing the settlement if found
     */
    Optional<Settlement> findByOrderId(Long orderId);

    /**
     * Find all settlements in a given status
     * 
     * Used for monitoring: "How many settlements are SETTLING right now?"
     * Or for cleanup: "Find all FAILED settlements for retry"
     * 
     * @param status The settlement status (e.g., INITIATED, COMPLETED, FAILED)
     * @return List of settlements matching the status
     */
    List<Settlement> findByStatus(SettlementStatus status);

    /**
     * Find all incomplete settlements
     * 
     * CRITICAL: Used on application startup by RecoveryService.
     * Returns settlements that are mid-process and need to be completed.
     * 
     * Incomplete = INITIATED or CASH_DEBITED or POSITION_CREDITED
     * (anything that's not COMPLETED or FAILED)
     * 
     * Example: If app crashed with settlement in CASH_DEBITED state,
     * this query finds it so we can continue with POSITION_CREDITED step.
     * 
     * @return List of incomplete settlements
     */
    @Query("SELECT s FROM Settlement s WHERE s.status IN ('INITIATED', 'CASH_DEBITED', 'POSITION_CREDITED')")
    List<Settlement> findIncompleteSettlements();
}