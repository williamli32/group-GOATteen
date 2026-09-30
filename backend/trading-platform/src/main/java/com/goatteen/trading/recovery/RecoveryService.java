package com.goatteen.trading.recovery;

import com.goatteen.trading.order.Order;
import com.goatteen.trading.order.OrderRepository;
import com.goatteen.trading.order.OrderStatus;
import com.goatteen.trading.settlement.Settlement;
import com.goatteen.trading.settlement.SettlementRepository;
import com.goatteen.trading.settlement.SettlementStatus;
import com.goatteen.trading.settlement.SettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Recovery Service
 * 
 * Runs on application startup to recover incomplete settlements.
 * 
 * Problem: If app crashes mid-settlement, orders get stuck in SETTLING state
 * Solution: On startup, find incomplete settlements and resume from where we left off
 * 
 * Example Scenario:
 *   Order 123: Status = SETTLING
 *   Settlement 123: Status = CASH_DEBITED (cash withdrawn, but shares not credited yet)
 *   [APP CRASHES]
 *   [APP RESTARTS]
 *   RecoveryService: "Found settlement in CASH_DEBITED state → resume by crediting shares"
 *   Result: Settlement completes successfully
 * 
 * Key Guarantee: Idempotent
 * - Running recovery twice is safe (uses idempotency keys)
 * - Won't double-debit or double-credit
 */
@Service
public class RecoveryService {

    private static final Logger logger = LoggerFactory.getLogger(RecoveryService.class);

    private final SettlementRepository settlementRepository;
    private final OrderRepository orderRepository;
    private final SettlementService settlementService;

    public RecoveryService(
            SettlementRepository settlementRepository,
            OrderRepository orderRepository,
            SettlementService settlementService) {
        this.settlementRepository = settlementRepository;
        this.orderRepository = orderRepository;
        this.settlementService = settlementService;
    }

    /**
     * Main recovery entry point
     * 
     * Called on application startup via StartupRecoveryConfig.
     * Finds and recovers all incomplete settlements.
     * Also logs failed settlements for alerting.
     * 
     * Safe to call multiple times (idempotent).
     */
    @Transactional
    public void recoverIncompleteSettlements() {
        logger.info("=== Starting Recovery Service ===");

        try {
            // Step 1: Recover incomplete settlements
            recoverSettlements();

            // Step 2: Log failed settlements for monitoring
            logFailedSettlements();

            // Step 3: Log orders in failed settlement state
            logFailedOrders();

            logger.info("=== Recovery Service Completed Successfully ===");

        } catch (Exception e) {
            logger.error("Recovery Service encountered an error", e);
            // Don't throw - recovery failure shouldn't crash the app
        }
    }

    /**
     * Recover all incomplete settlements
     * 
     * Finds settlements in mid-process and resumes from last completed step.
     * 
     * Logic:
     *   INITIATED:          → Debit cash, credit shares, complete
     *   CASH_DEBITED:       → Credit shares, complete
     *   POSITION_CREDITED:  → Mark complete
     */
    @Transactional
    private void recoverSettlements() {
        List<Settlement> incompleteSettlements = settlementRepository.findIncompleteSettlements();

        if (incompleteSettlements.isEmpty()) {
            logger.info("No incomplete settlements found");
            return;
        }

        logger.info("Found {} incomplete settlements to recover", incompleteSettlements.size());

        for (Settlement settlement : incompleteSettlements) {
            try {
                recoverSingleSettlement(settlement);
            } catch (Exception e) {
                logger.error(
                    "Failed to recover settlement {} for order {}",
                    settlement.getId(),
                    settlement.getOrder().getId(),
                    e
                );
                // Continue with next settlement instead of failing completely
            }
        }
    }

    /**
     * Recover a single settlement
     * 
     * Determines current step and resumes from there.
     * Uses idempotency keys to ensure no duplicate execution.
     * 
     * @param settlement The incomplete settlement to recover
     */
    @Transactional
    private void recoverSingleSettlement(Settlement settlement) {
        Order order = settlement.getOrder();
        SettlementStatus currentStatus = settlement.getStatus();

        logger.info(
            "Recovering settlement {} (order {}) from status: {}",
            settlement.getId(),
            order.getId(),
            currentStatus
        );

        // Generate unique idempotency key for this recovery attempt
        String recoveryKey = "recovery-" + settlement.getId() + "-" + UUID.randomUUID();

        try {
            // Execute settlement with recovery key
            // This will resume from current step
            settlementService.executeSettlement(order.getId(), recoveryKey);

            logger.info(
                "Successfully recovered settlement {} (order {})",
                settlement.getId(),
                order.getId()
            );

        } catch (Exception e) {
            logger.warn(
                "Recovery failed for settlement {} (order {}): {}",
                settlement.getId(),
                order.getId(),
                e.getMessage()
            );

            // Mark as FAILED for manual review
            settlement.setStatus(SettlementStatus.FAILED);
            settlementRepository.save(settlement);

            throw e;
        }
    }

    /**
     * Log all settlements in FAILED state
     * 
     * Failed settlements need manual review or retry logic.
     * Useful for monitoring and alerting.
     */
    @Transactional(readOnly = true)
    private void logFailedSettlements() {
        List<Settlement> failedSettlements = settlementRepository.findByStatus(
            SettlementStatus.FAILED
        );

        if (!failedSettlements.isEmpty()) {
            logger.warn(
                "Found {} failed settlements requiring manual review",
                failedSettlements.size()
            );
            failedSettlements.forEach(settlement ->
                logger.warn(
                    "  - Settlement {}: Order {}, Account {}",
                    settlement.getId(),
                    settlement.getOrder().getId(),
                    settlement.getAccount().getId()
                )
            );
        }
    }

    /**
     * Log all orders in SETTLING_FAILED state
     * 
     * These orders can be retried.
     * Useful for monitoring and retry logic.
     */
    @Transactional(readOnly = true)
    private void logFailedOrders() {
        List<Order> failedOrders = orderRepository.findByStatus(OrderStatus.SETTLING_FAILED);

        if (!failedOrders.isEmpty()) {
            logger.warn(
                "Found {} orders in SETTLING_FAILED state that can be retried",
                failedOrders.size()
            );
            failedOrders.forEach(order ->
                logger.warn(
                    "  - Order {}: Account {}, Status {}",
                    order.getId(),
                    order.getAccount().getId(),
                    order.getStatus()
                )
            );
        }
    }

    /**
     * Manually retry a specific settlement
     * 
     * Can be called by an admin or automatic retry logic.
     * 
     * @param settlementId The settlement to retry
     */
    @Transactional
    public void retrySingleSettlement(Long settlementId) {
        Settlement settlement = settlementRepository.findById(settlementId)
            .orElseThrow(() -> new IllegalArgumentException("Settlement not found: " + settlementId));

        logger.info("Manually retrying settlement {}", settlementId);

        String retryKey = "manual-retry-" + settlementId + "-" + UUID.randomUUID();
        settlementService.executeSettlement(settlement.getOrder().getId(), retryKey);
    }
}