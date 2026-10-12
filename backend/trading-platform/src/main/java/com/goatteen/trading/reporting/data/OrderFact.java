
package com.goatteen.trading.reporting.data;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Order-level reporting data.
 *
 * Unlike TradeFact, which contains executed fills,
 * OrderFact represents every submitted order,
 * including rejected orders.
 */
@Entity
@Table(name = "order_facts", schema = "reporting")
public class OrderFact {

    @Id
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(name = "instrument_symbol")
    private String instrumentSymbol;

    @Column(name = "currency")
    private String currency;

    @Column(name = "side", nullable = false)
    private String side;

    @Column(name = "order_quantity", nullable = false)
    private BigDecimal orderQuantity;

    @Column(name = "final_status", nullable = false)
    private String finalStatus;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "data_synced_at", nullable = false)
    private LocalDateTime dataSyncedAt;

    protected OrderFact() {
        // Required by JPA.
    }

    public OrderFact(
            Long orderId,
            Long accountId,
            Long clientId,
            Long instrumentId,
            String instrumentSymbol,
            String currency,
            String side,
            BigDecimal orderQuantity,
            String finalStatus,
            LocalDateTime submittedAt,
            LocalDateTime completedAt,
            String rejectionReason) {

        this.orderId = orderId;
        this.accountId = accountId;
        this.clientId = clientId;
        this.instrumentId = instrumentId;
        this.instrumentSymbol = instrumentSymbol;
        this.currency = currency;
        this.side = side;
        this.orderQuantity = orderQuantity;
        this.finalStatus = finalStatus;
        this.submittedAt = submittedAt;
        this.completedAt = completedAt;
        this.rejectionReason = rejectionReason;
        this.dataSyncedAt = LocalDateTime.now();
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public Long getClientId() {
        return clientId;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public String getInstrumentSymbol() {
        return instrumentSymbol;
    }

    public String getCurrency() {
        return currency;
    }

    public String getSide() {
        return side;
    }

    public BigDecimal getOrderQuantity() {
        return orderQuantity;
    }

    public String getFinalStatus() {
        return finalStatus;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public LocalDateTime getDataSyncedAt() {
        return dataSyncedAt;
    }

    /**
     * Refresh the reporting record when an order
     * changes status.
     */
    public void updateStatus(
            String finalStatus,
            LocalDateTime completedAt,
            String rejectionReason) {

        this.finalStatus = finalStatus;
        this.completedAt = completedAt;
        this.rejectionReason = rejectionReason;
        this.dataSyncedAt = LocalDateTime.now();
    }
}
