package com.goatteen.trading.settlement;

import com.goatteen.trading.order.Order;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Settlement Idempotency Key Entity
 * 
 * Prevents duplicate settlement execution across application restarts.
 * 
 * When a settlement is executed, we store the idempotency key and result.
 * If the same settlement request arrives again (even after app crash/restart),
 * we can detect it and return the cached result without re-executing.
 * 
 * Example:
 *   Client: settlementService.execute(orderId=123, key="abc-def-ghi")
 *   └─ Settlement executes, store result with key
 *   └─ App crashes
 *   Client retries: settlementService.execute(orderId=123, key="abc-def-ghi")
 *   └─ Key exists! Return cached result (no re-execution)
 * 
 * Note: The key should be a UUID or similar unique identifier provided by the client
 * or generated consistently for each settlement attempt.
 */
@Entity
@Table(
    name = "settlement_idempotency_keys",
    indexes = {
        @Index(name = "idx_order_key", columnList = "order_id, idempotency_key"),
        @Index(name = "idx_key_created", columnList = "idempotency_key, created_at")
    },
    uniqueConstraints = {
        /**
         * CRITICAL: Each idempotency key can only exist once in the database.
         * This prevents duplicate executions at the database level.
         */
        @UniqueConstraint(
            name = "uk_idempotency_key",
            columnNames = "idempotency_key"
        )
    }
)
public class SettlementIdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Reference to the order being settled
     * Allows us to query all idempotency attempts for an order
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /**
     * The idempotency key (typically a UUID)
     * UNIQUE constraint ensures no duplicate executions
     */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    /**
     * Was this execution successful or did it fail?
     * Values: SUCCESS or FAILURE
     */
    @Column(nullable = false)
    private String status;  // SUCCESS or FAILURE

    /**
     * The result of the settlement execution stored as JSON
     * 
     * Example of SUCCESS result:
     * {
     *   "settlementId": 999,
     *   "orderId": 123,
     *   "status": "COMPLETED",
     *   "totalAmount": 15000.00,
     *   "timestamp": "2024-09-30T10:00:05Z"
     * }
     * 
     * Example of FAILURE result:
     * {
     *   "error": "Account locked",
     *   "errorCode": "ACCOUNT_LOCKED",
     *   "timestamp": "2024-09-30T10:00:05Z"
     * }
     */
    @Column(name = "result_json", columnDefinition = "LONGTEXT")
    private String resultJson;

    /**
     * When this idempotency key was first recorded
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // ============= CONSTRUCTORS =============

    protected SettlementIdempotencyKey() {
        // JPA requires no-arg constructor
    }

    /**
     * Create a new idempotency key record for a successful settlement
     */
    public SettlementIdempotencyKey(Order order, String idempotencyKey, String resultJson) {
        this.order = order;
        this.idempotencyKey = idempotencyKey;
        this.status = "SUCCESS";
        this.resultJson = resultJson;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Static factory method for successful execution
     */
    public static SettlementIdempotencyKey ofSuccess(Order order, String idempotencyKey, String resultJson) {
        SettlementIdempotencyKey key = new SettlementIdempotencyKey(order, idempotencyKey, resultJson);
        key.status = "SUCCESS";
        return key;
    }

    /**
     * Static factory method for failed execution
     */
    public static SettlementIdempotencyKey ofFailure(Order order, String idempotencyKey, String errorJson) {
        SettlementIdempotencyKey key = new SettlementIdempotencyKey(order, idempotencyKey, errorJson);
        key.status = "FAILURE";
        return key;
    }

    // ============= GETTERS =============

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getStatus() {
        return status;
    }

    public String getResultJson() {
        return resultJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // ============= HELPER METHODS =============

    /**
     * Check if this idempotency key represents a successful execution
     */
    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }

    /**
     * Check if this idempotency key represents a failed execution
     */
    public boolean isFailure() {
        return "FAILURE".equals(status);
    }

    /**
     * Get how old this key is (in milliseconds)
     */
    public long getAgeMillis() {
        return java.time.temporal.ChronoUnit.MILLIS.between(createdAt, LocalDateTime.now());
    }
}