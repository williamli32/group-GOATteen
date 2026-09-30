package com.goatteen.trading.order;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

/**
 * Order Service
 * 
 * Enforces the order state machine - validates that orders follow the correct path.
 * 
 * Valid Order Lifecycle:
 * SUBMITTED → ACCEPTED → FILLED → SETTLING → SETTLED (terminal)
 *                                        ↓
 *                                   SETTLING_FAILED (can retry)
 *                                        ↓
 *                                   SETTLING (retry)
 * 
 * This service prevents invalid transitions like:
 * - Jumping from ACCEPTED directly to SETTLED (skipping FILLED, SETTLING)
 * - Moving backward (e.g., FILLED → ACCEPTED)
 * - Transitioning from SETTLED (terminal state)
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Update order status with state machine validation
     * 
     * Uses pessimistic locking to prevent concurrent updates.
     * Validates transition before applying change.
     * Throws exception if transition is invalid.
     * 
     * @param orderId The order to update
     * @param newStatus The target status
     * @throws InvalidOrderStateException if transition is invalid
     */
    @Transactional
    public void updateOrderStatus(Long orderId, OrderStatus newStatus) {
        // Use pessimistic locking: Lock the row until transaction completes
        Order order = orderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        OrderStatus currentStatus = order.getStatus();

        // Validate transition before applying
        if (!validateTransition(currentStatus, newStatus)) {
            throw new InvalidOrderStateException(
                String.format(
                    "Invalid order state transition from %s to %s (Order ID: %s)",
                    currentStatus, newStatus, orderId
                )
            );
        }

        // Apply the transition
        order.setStatus(newStatus);
        orderRepository.save(order);
    }

    /**
     * Validate if transition from currentStatus to newStatus is allowed
     * 
     * Implements the order state machine rules.
     * 
     * Valid transitions:
     * SUBMITTED → ACCEPTED
     * ACCEPTED → FILLED
     * FILLED → SETTLING
     * SETTLING → SETTLING_FAILED
     * SETTLING → SETTLED
     * SETTLING_FAILED → SETTLING (retry settlement)
     * 
     * All other transitions are invalid.
     * 
     * @param currentStatus The order's current status
     * @param newStatus The target status
     * @return true if transition is valid, false otherwise
     */
    public boolean validateTransition(OrderStatus currentStatus, OrderStatus newStatus) {
        // Null check
        if (currentStatus == null || newStatus == null) {
            return false;
        }

        // Same status is allowed (idempotent operations)
        if (currentStatus == newStatus) {
            return true;
        }

        // Forward-only state machine
        switch (currentStatus) {
            case SUBMITTED:
                return newStatus == OrderStatus.ACCEPTED;

            case ACCEPTED:
                return newStatus == OrderStatus.FILLED;

            case FILLED:
                return newStatus == OrderStatus.SETTLING;

            case SETTLING:
                // From SETTLING, can move to:
                // - SETTLING_FAILED if settlement fails
                // - SETTLED if settlement completes
                return newStatus == OrderStatus.SETTLING_FAILED || newStatus == OrderStatus.SETTLED;

            case SETTLING_FAILED:
                // From SETTLING_FAILED, can retry settlement
                return newStatus == OrderStatus.SETTLING;

            case SETTLED:
                // SETTLED is terminal - no transitions allowed
                return false;

            case REJECTED:
                // REJECTED is terminal - no transitions allowed
                return false;

            default:
                return false;
        }
    }

    /**
     * Check if order has been filled (executed at exchange)
     * 
     * Returns true if order status is FILLED or beyond.
     * Used to determine if order has a corresponding fill record.
     * 
     * @param orderId The order to check
     * @return true if order is FILLED or beyond (SETTLING, SETTLING_FAILED, SETTLED)
     */
    @Transactional(readOnly = true)
    public boolean isOrderFilled(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(
            () -> new IllegalArgumentException("Order not found: " + orderId)
        );
        
        OrderStatus status = order.getStatus();
        return status == OrderStatus.FILLED 
            || status == OrderStatus.SETTLING 
            || status == OrderStatus.SETTLING_FAILED
            || status == OrderStatus.SETTLED;
    }

    /**
     * Check if order has been settled (cash/positions moved)
     * 
     * Returns true only if order is in SETTLED terminal state.
     * Used to determine if settlement is complete.
     * 
     * @param orderId The order to check
     * @return true if order is SETTLED
     */
    @Transactional(readOnly = true)
    public boolean isOrderSettled(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(
            () -> new IllegalArgumentException("Order not found: " + orderId)
        );
        
        return order.getStatus() == OrderStatus.SETTLED;
    }

    /**
     * Check if order settlement can be retried
     * 
     * Returns true if order is in SETTLING_FAILED state.
     * Used by recovery logic to identify orders needing retry.
     * 
     * @param orderId The order to check
     * @return true if order is SETTLING_FAILED
     */
    @Transactional(readOnly = true)
    public boolean canRetrySettlement(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(
            () -> new IllegalArgumentException("Order not found: " + orderId)
        );
        
        return order.getStatus() == OrderStatus.SETTLING_FAILED;
    }

    /**
     * Check if order is in settlement process
     * 
     * Returns true if order is SETTLING or SETTLING_FAILED.
     * Used to identify orders in mid-settlement.
     * 
     * @param orderId The order to check
     * @return true if order is SETTLING or SETTLING_FAILED
     */
    @Transactional(readOnly = true)
    public boolean isOrderSettling(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(
            () -> new IllegalArgumentException("Order not found: " + orderId)
        );
        
        OrderStatus status = order.getStatus();
        return status == OrderStatus.SETTLING || status == OrderStatus.SETTLING_FAILED;
    }
}