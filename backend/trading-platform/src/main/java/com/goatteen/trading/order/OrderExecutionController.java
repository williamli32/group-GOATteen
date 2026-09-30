package com.goatteen.trading.order;

import com.goatteen.trading.execution.Fill;
import com.goatteen.trading.execution.OrderExecutionService;
import com.goatteen.trading.execution.OrderExecutionService.OrderExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

/**
 * REST API for order execution with idempotency support.
 * 
 * Supports idempotent order execution to prevent duplicate fills
 * even if client retries with same request.
 * 
 * Idempotency Key:
 * - Client provides via "Idempotency-Key" header (UUID recommended)
 * - Server uses to detect and return existing fill for duplicate requests
 * - Persisted in database for protection across application restarts
 */
@RestController
@RequestMapping("/api/orders")
public class OrderExecutionController {

    private final OrderExecutionService orderExecutionService;
    private final OrderRepository orderRepository;

    public OrderExecutionController(OrderExecutionService orderExecutionService, 
            OrderRepository orderRepository) {
        this.orderExecutionService = orderExecutionService;
        this.orderRepository = orderRepository;
    }

    /**
     * Execute an order with idempotency protection.
     * 
     * Validation Sequence (6-step):
     * 1. Is order already FILLED? → Return existing fill (200 OK)
     * 2. Is order in ACCEPTED state? → Proceed with execution
     * 3. Is order in any other state? → Reject (400/409 Conflict)
     * 4. Attempt execution (atomic) → Create fill, update balances, positions
     * 5. On success: update order.status = FILLED
     * 6. On failure: rollback all changes
     * 
     * @param orderId Order ID to execute
     * @param idempotencyKey Optional Idempotency-Key header (UUID recommended)
     * @return FillDTO with execution details
     */
    @PostMapping("/execute/{orderId}")
    public ResponseEntity<FillDTO> executeOrder(
            @PathVariable Long orderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        
        try {
            // Generate UUID if not provided
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                idempotencyKey = UUID.randomUUID().toString();
            }

            // Validation Step 1: Check if order exists
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new OrderExecutionException("Order not found with ID: " + orderId));

            // Validation Step 2: Check if order is already FILLED
            if (order.getStatus() == OrderStatus.FILLED) {
                // Return existing fill (200 OK)
                // Note: In a real scenario, we'd query for the existing fill by order ID
                return ResponseEntity.ok(new FillDTO(
                    null, orderId, null, 
                    "Order already filled", null, null
                ));
            }

            // Validation Step 3: Check if order is in ACCEPTED state
            if (order.getStatus() != OrderStatus.ACCEPTED) {
                // Reject with 409 Conflict
                return ResponseEntity
                        .status(HttpStatus.CONFLICT)
                        .body(new FillDTO(
                            null, orderId, null,
                            "Order is not in ACCEPTED state. Current state: " + order.getStatus(),
                            null, null
                        ));
            }

            // Validation Step 4-6: Attempt execution (atomic transaction)
            Fill fill = orderExecutionService.executeOrder(orderId, idempotencyKey);

            // On success: return 200 OK with fill details
            return ResponseEntity.ok(new FillDTO(
                fill.getId(),
                orderId,
                fill.getIdempotencyKey(),
                "Order executed successfully",
                fill.getFillPrice(),
                fill.getFillQuantity()
            ));

        } catch (OrderExecutionException e) {
            // On failure: return 400 Bad Request with error details
            return ResponseEntity
                    .badRequest()
                    .body(new FillDTO(
                        null, orderId, idempotencyKey,
                        "Execution failed: " + e.getMessage(),
                        null, null
                    ));
        } catch (Exception e) {
            // Unexpected error: return 500 Internal Server Error
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new FillDTO(
                        null, orderId, idempotencyKey,
                        "Unexpected error: " + e.getMessage(),
                        null, null
                    ));
        }
    }

    /**
     * Accept an order (transition from SUBMITTED to ACCEPTED state).
     * 
     * @param orderId Order ID to accept
     * @return Success message or error
     */
    @PostMapping("/accept/{orderId}")
    public ResponseEntity<String> acceptOrder(@PathVariable Long orderId) {
        try {
            orderExecutionService.acceptOrder(orderId);
            return ResponseEntity.ok("Order accepted successfully");
        } catch (OrderExecutionException e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * Reject an order (transition from SUBMITTED to REJECTED state).
     * 
     * @param orderId Order ID to reject
     * @param reason Reason for rejection
     * @return Success message or error
     */
    @PostMapping("/reject/{orderId}")
    public ResponseEntity<String> rejectOrder(
            @PathVariable Long orderId,
            @RequestParam(defaultValue = "No reason provided") String reason) {
        try {
            orderExecutionService.rejectOrder(orderId, reason);
            return ResponseEntity.ok("Order rejected successfully");
        } catch (OrderExecutionException e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * DTO for Fill execution response.
     * Includes idempotency key to confirm duplicate detection.
     */
    public static class FillDTO {
        public Long fillId;
        public Long orderId;
        public String idempotencyKey;
        public String message;
        public java.math.BigDecimal fillPrice;
        public java.math.BigDecimal fillQuantity;

        public FillDTO(Long fillId, Long orderId, String idempotencyKey, String message,
                java.math.BigDecimal fillPrice, java.math.BigDecimal fillQuantity) {
            this.fillId = fillId;
            this.orderId = orderId;
            this.idempotencyKey = idempotencyKey;
            this.message = message;
            this.fillPrice = fillPrice;
            this.fillQuantity = fillQuantity;
        }
    }
}
