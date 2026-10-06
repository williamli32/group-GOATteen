package com.goatteen.trading.recovery;

import com.goatteen.trading.audit.TradeIntegrityReport;
import com.goatteen.trading.audit.TradeReconstructionService;
import com.goatteen.trading.execution.Fill;
import com.goatteen.trading.execution.FillRepository;
import com.goatteen.trading.execution.OrderExecutionService;
import com.goatteen.trading.order.Order;
import com.goatteen.trading.order.OrderRepository;
import com.goatteen.trading.order.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RecoveryService {

    private static final Logger logger = LoggerFactory.getLogger(RecoveryService.class);

    private final OrderRepository orderRepository;
    private final FillRepository fillRepository;
    private final OrderExecutionService executionService;
    private final TradeReconstructionService reconstructionService;

    public RecoveryService(
            OrderRepository orderRepository,
            FillRepository fillRepository,
            OrderExecutionService executionService,
            TradeReconstructionService reconstructionService) {

        this.orderRepository = orderRepository;
        this.fillRepository = fillRepository;
        this.executionService = executionService;
        this.reconstructionService = reconstructionService;
    }

    /**
     * Determine the persisted recovery state of an order.
     *
     * This method NEVER guesses and NEVER modifies settlement state.
     */
    public RecoveryAssessment assessOrder(Long orderId) {

        Order order = orderRepository
                .findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Order not found: " + orderId));

        boolean fillExists = fillRepository
                .findByOrderId(orderId)
                .isPresent();

        /*
         * SUBMITTED:
         * Validation/acceptance may not have completed.
         * Never execute automatically.
         */
        if (order.getStatus() == OrderStatus.SUBMITTED) {

            if (fillExists) {
                return ambiguous(
                        orderId,
                        "SUBMITTED order unexpectedly has a Fill");
            }

            return new RecoveryAssessment(
                    orderId,
                    RecoveryState.SUBMITTED_NOT_EXECUTED,
                    false,
                    "Order was submitted but never accepted or executed");
        }

        /*
         * ACCEPTED:
         * No Fill means the execution transaction never committed.
         *
         * This is safe to execute again because settlement is atomic.
         */
        if (order.getStatus() == OrderStatus.ACCEPTED) {

            if (fillExists) {
                return ambiguous(
                        orderId,
                        "ACCEPTED order already has a Fill");
            }

            return new RecoveryAssessment(
                    orderId,
                    RecoveryState.ACCEPTED_NOT_EXECUTED,
                    true,
                    "Order was accepted but execution did not commit");
        }

        /*
         * REJECTED:
         * Rejected orders must never have settlement artifacts.
         */
        if (order.getStatus() == OrderStatus.REJECTED) {

            if (fillExists) {
                return ambiguous(
                        orderId,
                        "REJECTED order unexpectedly has a Fill");
            }

            return new RecoveryAssessment(
                    orderId,
                    RecoveryState.REJECTED,
                    false,
                    "Order was rejected and must not execute");
        }

        /*
         * FILLED:
         * A Fill alone isn't enough.
         *
         * Goal 1 already gives us full trade-integrity verification:
         * Fill
         * Quote
         * Cash movement
         * Resulting balance
         * Position history
         * Status history
         */
        if (order.getStatus() == OrderStatus.FILLED) {

            if (!fillExists) {
                return ambiguous(
                        orderId,
                        "FILLED order has no Fill");
            }

            TradeIntegrityReport integrity = reconstructionService
                    .verifyTradeIntegrity(orderId);

            if (!integrity.isValid()) {

                return ambiguous(
                        orderId,
                        "FILLED order failed trade-integrity verification: "
                                + integrity.getMessage());
            }

            return new RecoveryAssessment(
                    orderId,
                    RecoveryState.FULLY_EXECUTED,
                    false,
                    "Order was fully executed and must not settle again");
        }

        return ambiguous(
                orderId,
                "Unknown order state: "
                        + order.getStatus());
    }

    /**
     * Safely recover one order.
     *
     * Only ACCEPTED + no Fill may be automatically executed.
     *
     * FILLED orders are returned as already complete.
     * Ambiguous orders are never executed.
     */
    public RecoveryAssessment recoverOrder(Long orderId) {

        RecoveryAssessment before = assessOrder(orderId);

        switch (before.state()) {

            case ACCEPTED_NOT_EXECUTED -> {
                return recoverAcceptedOrder(orderId);
            }

            case FULLY_EXECUTED,
                    SUBMITTED_NOT_EXECUTED,
                    REJECTED -> {

                return before;
            }

            case AMBIGUOUS -> {

                logger.error(
                        "Refusing automatic recovery for ambiguous order {}: {}",
                        orderId,
                        before.message());

                return before;
            }

            default -> throw new IllegalStateException(
                    "Unhandled recovery state: "
                            + before.state());
        }
    }

    /**
     * Recover the state left after:
     *
     * ACCEPTED
     * ↓
     * process crashes before executeOrder commits
     *
     * Uses a deterministic persisted key whenever possible.
     */
    private RecoveryAssessment recoverAcceptedOrder(
            Long orderId) {

        Order order = orderRepository
                .findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Order not found: " + orderId));

        String recoveryKey = buildRecoveryKey(order);

        try {

            executionService.executeOrder(
                    orderId,
                    recoveryKey);

        } catch (OrderExecutionService.OrderExecutionException e) {

            /*
             * There may have been a concurrent retry between assessment
             * and execution.
             *
             * Re-read persisted state instead of guessing.
             */
            RecoveryAssessment afterFailure = assessOrder(orderId);

            if (afterFailure.state() == RecoveryState.FULLY_EXECUTED) {

                return afterFailure;
            }

            logger.warn(
                    "Recovery execution failed for order {}: {}",
                    orderId,
                    e.getMessage());

            return afterFailure;
        }

        /*
         * Never assume executeOrder succeeded merely because it returned.
         * Re-read persisted state and verify Goal 1 integrity.
         */
        return assessOrder(orderId);
    }

    /**
     * Run after application startup.
     *
     * ACCEPTED orders are candidates for safe automatic recovery.
     * FILLED orders are checked for deterministic persisted integrity.
     */
    public void recoverOnStartup() {

        logger.info(
                "Starting persisted order recovery scan");

        List<Order> acceptedOrders = orderRepository.findByStatus(
                OrderStatus.ACCEPTED);

        for (Order order : acceptedOrders) {

            try {

                RecoveryAssessment result = recoverOrder(order.getId());

                logger.info(
                        "Recovery result for order {}: {} - {}",
                        result.orderId(),
                        result.state(),
                        result.message());

            } catch (Exception e) {

                /*
                 * One broken order must not prevent recovery
                 * of every other order.
                 */
                logger.error(
                        "Recovery failed for order {}",
                        order.getId(),
                        e);
            }
        }

        /*
         * Verify existing completed executions.
         * Never attempt to execute them again.
         */
        List<Order> filledOrders = orderRepository.findByStatus(
                OrderStatus.FILLED);

        for (Order order : filledOrders) {

            try {

                RecoveryAssessment result = assessOrder(order.getId());

                if (result.state() == RecoveryState.AMBIGUOUS) {

                    logger.error(
                            "Ambiguous completed order {}: {}",
                            order.getId(),
                            result.message());
                }

            } catch (Exception e) {

                logger.error(
                        "Unable to verify filled order {} during recovery",
                        order.getId(),
                        e);
            }
        }

        logger.info(
                "Persisted order recovery scan completed");
    }

    /**
     * The order's original idempotency key is ideal because it survives
     * application restart.
     *
     * Older/manual orders may not have one, so use a deterministic key
     * derived from the persisted order ID.
     *
     * Do NOT generate a random UUID here.
     */
    private String buildRecoveryKey(Order order) {

        if (order.getIdempotencyKey() != null
                && !order.getIdempotencyKey().isBlank()) {

            return order.getIdempotencyKey();
        }

        return "recovery-order-"
                + order.getId();
    }

    private RecoveryAssessment ambiguous(
            Long orderId,
            String message) {

        return new RecoveryAssessment(
                orderId,
                RecoveryState.AMBIGUOUS,
                false,
                message);
    }
}