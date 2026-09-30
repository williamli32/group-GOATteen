package com.goatteen.trading.audit;

import com.goatteen.trading.execution.Fill;
import com.goatteen.trading.order.Order;
import com.goatteen.trading.order.OrderStatus;
import com.goatteen.trading.portfolio.CashTransaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Complete Audit Trail for a Trade
 * 
 * Contains all information necessary to reconstruct a trade from persisted data:
 * - Order: Original request (side, quantity, account, instrument)
 * - Status History: Complete state transition (SUBMITTED -> ACCEPTED -> FILLED or REJECTED)
 * - Quote: Market data used for execution
 * - Fill: Execution details (price, time)
 * - Cash Movements: All account balance changes
 * - Position Changes: Before/after holdings
 */
public class TradeAuditTrail {

    // Order Information
    private final Long orderId;
    private final Long accountId;
    private final Long instrumentId;
    private final String orderSide;
    private final BigDecimal orderQuantity;
    private final LocalDateTime submittedAt;

    // Status Timeline
    private final OrderStatus currentStatus;
    private final List<OrderStatusHistory> statusHistory;
    private final String rejectionReason;
    private final LocalDateTime completedAt;

    // Execution Details (if FILLED)
    private final Long fillId;
    private final BigDecimal fillPrice;
    private final BigDecimal fillQuantity;
    private final LocalDateTime executedAt;
    private final Long quoteId;
    private final BigDecimal bidPrice;
    private final BigDecimal askPrice;
    private final LocalDateTime quotedAt;

    // Cash Impact
    private final List<CashTransaction> cashTransactions;
    private final BigDecimal cashMovement;
    private final BigDecimal balanceAfter;

    // Position Impact
    private final BigDecimal quantityBefore;
    private final BigDecimal quantityAfter;

    public TradeAuditTrail(
            Order order,
            List<OrderStatusHistory> statusHistory,
            Fill fill,
            List<CashTransaction> cashTransactions,
            PositionHistory positionHistory) {

        // Order details
        this.orderId = order.getId();
        this.accountId = order.getAccount().getId();
        this.instrumentId = order.getInstrument().getId();
        this.orderSide = order.getSide().toString();
        this.orderQuantity = order.getQuantity();
        this.submittedAt = order.getSubmittedAt();

        // Status
        this.currentStatus = order.getStatus();
        this.statusHistory = statusHistory;
        this.rejectionReason = order.getRejectionReason();
        this.completedAt = order.getCompletedAt();

        // Fill details (if executed)
        if (fill != null) {
            this.fillId = fill.getId();
            this.fillPrice = fill.getFillPrice();
            this.fillQuantity = fill.getFillQuantity();
            this.executedAt = fill.getExecutedAt();
            this.quoteId = fill.getQuote() != null ? fill.getQuote().getId() : null;
            this.bidPrice = fill.getQuote() != null ? fill.getQuote().getBidPrice() : null;
            this.askPrice = fill.getQuote() != null ? fill.getQuote().getAskPrice() : null;
            this.quotedAt = fill.getQuote() != null ? fill.getQuote().getQuotedAt() : null;
        } else {
            this.fillId = null;
            this.fillPrice = null;
            this.fillQuantity = null;
            this.executedAt = null;
            this.quoteId = null;
            this.bidPrice = null;
            this.askPrice = null;
            this.quotedAt = null;
        }

        // Cash movements
        this.cashTransactions = cashTransactions;
        this.cashMovement = calculateCashMovement(cashTransactions);
        this.balanceAfter = !cashTransactions.isEmpty() ? 
                cashTransactions.get(cashTransactions.size() - 1).getBalanceAfter() : 
                null;

        // Position changes
        if (positionHistory != null) {
            this.quantityBefore = positionHistory.getQuantityBefore();
            this.quantityAfter = positionHistory.getQuantityAfter();
        } else {
            this.quantityBefore = null;
            this.quantityAfter = null;
        }
    }

    private BigDecimal calculateCashMovement(List<CashTransaction> transactions) {
        if (transactions.isEmpty()) return BigDecimal.ZERO;
        return transactions.stream()
                .map(CashTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ==================== Getters ====================

    public Long getOrderId() { return orderId; }
    public Long getAccountId() { return accountId; }
    public Long getInstrumentId() { return instrumentId; }
    public String getOrderSide() { return orderSide; }
    public BigDecimal getOrderQuantity() { return orderQuantity; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }

    public OrderStatus getCurrentStatus() { return currentStatus; }
    public List<OrderStatusHistory> getStatusHistory() { return statusHistory; }
    public String getRejectionReason() { return rejectionReason; }
    public LocalDateTime getCompletedAt() { return completedAt; }

    public Long getFillId() { return fillId; }
    public BigDecimal getFillPrice() { return fillPrice; }
    public BigDecimal getFillQuantity() { return fillQuantity; }
    public LocalDateTime getExecutedAt() { return executedAt; }
    public Long getQuoteId() { return quoteId; }
    public BigDecimal getBidPrice() { return bidPrice; }
    public BigDecimal getAskPrice() { return askPrice; }
    public LocalDateTime getQuotedAt() { return quotedAt; }

    public List<CashTransaction> getCashTransactions() { return cashTransactions; }
    public BigDecimal getCashMovement() { return cashMovement; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }

    public BigDecimal getQuantityBefore() { return quantityBefore; }
    public BigDecimal getQuantityAfter() { return quantityAfter; }

    /**
     * Check if this trade is fully reconstructable
     */
    public boolean isFullyReconstructable() {
        if (currentStatus == OrderStatus.FILLED) {
            return fillId != null && 
                    !cashTransactions.isEmpty() && 
                    quantityBefore != null && 
                    quantityAfter != null;
        } else if (currentStatus == OrderStatus.REJECTED) {
            return rejectionReason != null;
        }
        return true;
    }
}