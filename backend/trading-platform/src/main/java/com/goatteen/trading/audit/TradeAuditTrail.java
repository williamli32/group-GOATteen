package com.goatteen.trading.audit;

import com.goatteen.trading.execution.Fill;
import com.goatteen.trading.order.Order;
import com.goatteen.trading.order.OrderStatus;
import com.goatteen.trading.portfolio.CashTransaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
<<<<<<< HEAD
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
=======
 * Complete persisted audit trail for a trade.
 *
 * Contains all information necessary to reconstruct a trade from
 * database state without relying on application logs or frontend state.
 */
public class TradeAuditTrail {

    // Order information
>>>>>>> 29270f9221c18597faef1b200a17581cbc923fd4
    private final Long orderId;
    private final Long accountId;
    private final Long instrumentId;
    private final String orderSide;
    private final BigDecimal orderQuantity;
    private final LocalDateTime submittedAt;

<<<<<<< HEAD
    // Status Timeline
=======
    // Lifecycle
>>>>>>> 29270f9221c18597faef1b200a17581cbc923fd4
    private final OrderStatus currentStatus;
    private final List<OrderStatusHistory> statusHistory;
    private final String rejectionReason;
    private final LocalDateTime completedAt;

<<<<<<< HEAD
    // Execution Details (if FILLED)
=======
    // Execution
>>>>>>> 29270f9221c18597faef1b200a17581cbc923fd4
    private final Long fillId;
    private final BigDecimal fillPrice;
    private final BigDecimal fillQuantity;
    private final LocalDateTime executedAt;
<<<<<<< HEAD
=======

    // Quote
>>>>>>> 29270f9221c18597faef1b200a17581cbc923fd4
    private final Long quoteId;
    private final BigDecimal bidPrice;
    private final BigDecimal askPrice;
    private final LocalDateTime quotedAt;

<<<<<<< HEAD
    // Cash Impact
=======
    // Cash impact
>>>>>>> 29270f9221c18597faef1b200a17581cbc923fd4
    private final List<CashTransaction> cashTransactions;
    private final BigDecimal cashMovement;
    private final BigDecimal balanceAfter;

<<<<<<< HEAD
    // Position Impact
    private final BigDecimal quantityBefore;
    private final BigDecimal quantityAfter;
=======
    // Position impact
    private final BigDecimal quantityBefore;
    private final BigDecimal quantityAfter;
    private final BigDecimal quantityChange;
    private final LocalDateTime positionRecordedAt;
>>>>>>> 29270f9221c18597faef1b200a17581cbc923fd4

    public TradeAuditTrail(
            Order order,
            List<OrderStatusHistory> statusHistory,
            Fill fill,
            List<CashTransaction> cashTransactions,
            PositionHistory positionHistory) {

<<<<<<< HEAD
        // Order details
=======
        // Order
>>>>>>> 29270f9221c18597faef1b200a17581cbc923fd4
        this.orderId = order.getId();
        this.accountId = order.getAccount().getId();
        this.instrumentId = order.getInstrument().getId();
        this.orderSide = order.getSide().toString();
        this.orderQuantity = order.getQuantity();
        this.submittedAt = order.getSubmittedAt();

<<<<<<< HEAD
        // Status
=======
        // Lifecycle
>>>>>>> 29270f9221c18597faef1b200a17581cbc923fd4
        this.currentStatus = order.getStatus();
        this.statusHistory = statusHistory;
        this.rejectionReason = order.getRejectionReason();
        this.completedAt = order.getCompletedAt();

<<<<<<< HEAD
        // Fill details (if executed)
        if (fill != null) {
=======
        // Execution + quote
        if (fill != null) {

>>>>>>> 29270f9221c18597faef1b200a17581cbc923fd4
            this.fillId = fill.getId();
            this.fillPrice = fill.getFillPrice();
            this.fillQuantity = fill.getFillQuantity();
            this.executedAt = fill.getExecutedAt();
<<<<<<< HEAD
            this.quoteId = fill.getQuote() != null ? fill.getQuote().getId() : null;
            this.bidPrice = fill.getQuote() != null ? fill.getQuote().getBidPrice() : null;
            this.askPrice = fill.getQuote() != null ? fill.getQuote().getAskPrice() : null;
            this.quotedAt = fill.getQuote() != null ? fill.getQuote().getQuotedAt() : null;
        } else {
=======

            if (fill.getQuote() != null) {
                this.quoteId = fill.getQuote().getId();
                this.bidPrice = fill.getQuote().getBidPrice();
                this.askPrice = fill.getQuote().getAskPrice();
                this.quotedAt = fill.getQuote().getQuotedAt();
            } else {
                this.quoteId = null;
                this.bidPrice = null;
                this.askPrice = null;
                this.quotedAt = null;
            }

        } else {

>>>>>>> 29270f9221c18597faef1b200a17581cbc923fd4
            this.fillId = null;
            this.fillPrice = null;
            this.fillQuantity = null;
            this.executedAt = null;
<<<<<<< HEAD
=======

>>>>>>> 29270f9221c18597faef1b200a17581cbc923fd4
            this.quoteId = null;
            this.bidPrice = null;
            this.askPrice = null;
            this.quotedAt = null;
        }

<<<<<<< HEAD
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
=======
        // Cash
        this.cashTransactions = cashTransactions;

        this.cashMovement = calculateCashMovement(
                cashTransactions);

        this.balanceAfter = !cashTransactions.isEmpty()
                ? cashTransactions
                        .get(cashTransactions.size() - 1)
                        .getBalanceAfter()
                : null;

        // Position
        if (positionHistory != null) {

            this.quantityBefore = positionHistory.getQuantityBefore();

            this.quantityAfter = positionHistory.getQuantityAfter();

            this.quantityChange = positionHistory.getQuantityChange();

            this.positionRecordedAt = positionHistory.getRecordedAt();

        } else {

            this.quantityBefore = null;
            this.quantityAfter = null;
            this.quantityChange = null;
            this.positionRecordedAt = null;
        }
    }

    private BigDecimal calculateCashMovement(
            List<CashTransaction> transactions) {

        if (transactions.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return transactions.stream()
                .map(CashTransaction::getAmount)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add);
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public String getOrderSide() {
        return orderSide;
    }

    public BigDecimal getOrderQuantity() {
        return orderQuantity;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public OrderStatus getCurrentStatus() {
        return currentStatus;
    }

    public List<OrderStatusHistory> getStatusHistory() {
        return statusHistory;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public Long getFillId() {
        return fillId;
    }

    public BigDecimal getFillPrice() {
        return fillPrice;
    }

    public BigDecimal getFillQuantity() {
        return fillQuantity;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public Long getQuoteId() {
        return quoteId;
    }

    public BigDecimal getBidPrice() {
        return bidPrice;
    }

    public BigDecimal getAskPrice() {
        return askPrice;
    }

    public LocalDateTime getQuotedAt() {
        return quotedAt;
    }

    public List<CashTransaction> getCashTransactions() {
        return cashTransactions;
    }

    public BigDecimal getCashMovement() {
        return cashMovement;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public BigDecimal getQuantityBefore() {
        return quantityBefore;
    }

    public BigDecimal getQuantityAfter() {
        return quantityAfter;
    }

    public BigDecimal getQuantityChange() {
        return quantityChange;
    }

    public LocalDateTime getPositionRecordedAt() {
        return positionRecordedAt;
    }

    /**
     * Determines whether all required persisted information exists.
     */
    public boolean isFullyReconstructable() {

        if (currentStatus == OrderStatus.FILLED) {

            return fillId != null
                    && fillPrice != null
                    && fillQuantity != null
                    && executedAt != null
                    && quoteId != null
                    && bidPrice != null
                    && askPrice != null
                    && quotedAt != null
                    && !cashTransactions.isEmpty()
                    && cashMovement != null
                    && balanceAfter != null
                    && quantityBefore != null
                    && quantityAfter != null
                    && quantityChange != null
                    && positionRecordedAt != null
                    && completedAt != null;

        }

        if (currentStatus == OrderStatus.REJECTED) {

            return rejectionReason != null
                    && !rejectionReason.isBlank()
                    && completedAt != null;
        }

        return false;
    }
}
>>>>>>> 29270f9221c18597faef1b200a17581cbc923fd4
