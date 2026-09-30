package com.goatteen.trading.settlement;

import com.goatteen.trading.order.Order;
import com.goatteen.trading.account.Account;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Settlement Entity
 * 
 * Tracks the settlement process: the conversion of an executed order
 * into actual cash and position changes in the account.
 * 
 * Lifecycle:
 *   INITIATED ──► CASH_DEBITED ──► POSITION_CREDITED ──► COMPLETED
 *                                                              │
 *                                                              ▼
 *                                                           (Terminal)
 * 
 * Each state represents a step that must complete in order.
 * If the system crashes, we can check which step actually completed
 * and resume from the next step.
 */
@Entity
@Table(
    name = "settlements",
    indexes = {
        @Index(name = "idx_order_settlement", columnList = "order_id"),
        @Index(name = "idx_status_created", columnList = "status, created_at")
    }
)
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * One-to-one relationship: Each order has one settlement
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    /**
     * Reference to the account being settled
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    /**
     * How many shares are being settled
     */
    @Column(nullable = false)
    private BigDecimal quantity;

    /**
     * Price per share
     */
    @Column(nullable = false)
    private BigDecimal price;

    /**
     * Total amount: quantity × price
     * This is the cash to be debited from account
     */
    @Column(nullable = false)
    private BigDecimal totalAmount;

    /**
     * Current state of settlement
     * Possible values: INITIATED, CASH_DEBITED, POSITION_CREDITED, COMPLETED, FAILED
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementStatus status;

    /**
     * When settlement was initiated
     */
    @Column(name = "initiated_at", nullable = false)
    private LocalDateTime initiatedAt;

    /**
     * When settlement completed (or failed)
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * Track when this record was created
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Track when this record was last updated
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    

    protected Settlement() {
        // JPA requires no-arg constructor
    }

    /**
     * Create a new settlement for an order
     */
    public Settlement(Order order, Account account, BigDecimal quantity, BigDecimal price) {
        this.order = order;
        this.account = account;
        this.quantity = quantity;
        this.price = price;
        this.totalAmount = quantity.multiply(price);
        this.status = SettlementStatus.INITIATED;
        this.initiatedAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }


    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public Account getAccount() {
        return account;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public SettlementStatus getStatus() {
        return status;
    }

    public LocalDateTime getInitiatedAt() {
        return initiatedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }



    public void setOrder(Order order) {
        this.order = order;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
        this.updatedAt = LocalDateTime.now();
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
        this.updatedAt = LocalDateTime.now();
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
        this.updatedAt = LocalDateTime.now();
    }

    public void setStatus(SettlementStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public void setInitiatedAt(LocalDateTime initiatedAt) {
        this.initiatedAt = initiatedAt;
        this.updatedAt = LocalDateTime.now();
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
        this.updatedAt = LocalDateTime.now();
    }



    /**
     * Check if settlement is complete
     */
    public boolean isCompleted() {
        return status == SettlementStatus.COMPLETED;
    }

    /**
     * Check if settlement is still in progress
     */
    public boolean isInProgress() {
        return status != SettlementStatus.COMPLETED && status != SettlementStatus.FAILED;
    }

    /**
     * Check if settlement failed
     */
    public boolean isFailed() {
        return status == SettlementStatus.FAILED;
    }

    /**
     * Get time elapsed since settlement initiated
     */
    public long getElapsedMillis() {
        LocalDateTime endTime = completedAt != null ? completedAt : LocalDateTime.now();
        return java.time.temporal.ChronoUnit.MILLIS.between(initiatedAt, endTime);
    }
}