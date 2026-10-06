package com.goatteen.trading.audit;

import com.goatteen.trading.portfolio.CashTransaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TradeAuditResponse(
        OrderDetails order,
        AccountDetails account,
        InstrumentDetails instrument,
        String currentStatus,
        List<StatusHistoryEntry> statusHistory,
        String rejectionReason,
        LocalDateTime completedAt,
        ExecutionDetails execution,
        CashImpact cashImpact,
        PositionImpact positionImpact,
        boolean fullyReconstructable) {

    public static TradeAuditResponse from(
            TradeAuditTrail audit) {

        ExecutionDetails execution = null;

        if (audit.getFillId() != null) {

            execution = new ExecutionDetails(
                    audit.getFillId(),
                    audit.getFillQuantity(),
                    audit.getFillPrice(),
                    audit.getExecutedAt(),
                    audit.getQuoteId(),
                    audit.getBidPrice(),
                    audit.getAskPrice(),
                    audit.getQuotedAt());
        }

        List<StatusHistoryEntry> statusHistory = audit.getStatusHistory()
                .stream()
                .map(history -> new StatusHistoryEntry(
                        history.getId(),
                        history.getStatus().name(),
                        history.getChangedAt(),
                        history.getNote()))
                .toList();

        List<CashTransactionEntry> transactions = audit.getCashTransactions()
                .stream()
                .map(CashTransactionEntry::from)
                .toList();

        CashImpact cashImpact = new CashImpact(
                transactions,
                audit.getCashMovement(),
                audit.getBalanceAfter());

        PositionImpact positionImpact = audit.getQuantityBefore() == null
                ? null
                : new PositionImpact(
                        audit.getQuantityBefore(),
                        audit.getQuantityAfter(),
                        audit.getQuantityChange(),
                        audit.getPositionRecordedAt());

        return new TradeAuditResponse(
                new OrderDetails(
                        audit.getOrderId(),
                        audit.getOrderSide(),
                        audit.getOrderQuantity(),
                        audit.getSubmittedAt()),

                new AccountDetails(
                        audit.getAccountId()),

                new InstrumentDetails(
                        audit.getInstrumentId()),

                audit.getCurrentStatus().name(),

                statusHistory,

                audit.getRejectionReason(),

                audit.getCompletedAt(),

                execution,

                cashImpact,

                positionImpact,

                audit.isFullyReconstructable());
    }

    public record OrderDetails(
            Long orderId,
            String side,
            BigDecimal quantity,
            LocalDateTime submittedAt) {
    }

    public record AccountDetails(
            Long accountId) {
    }

    public record InstrumentDetails(
            Long instrumentId) {
    }

    public record StatusHistoryEntry(
            Long historyId,
            String status,
            LocalDateTime changedAt,
            String note) {
    }

    public record ExecutionDetails(
            Long fillId,
            BigDecimal fillQuantity,
            BigDecimal executionPrice,
            LocalDateTime executedAt,
            Long quoteId,
            BigDecimal bidPrice,
            BigDecimal askPrice,
            LocalDateTime quotedAt) {
    }

    public record CashImpact(
            List<CashTransactionEntry> transactions,
            BigDecimal totalMovement,
            BigDecimal balanceAfter) {
    }

    public record CashTransactionEntry(
            Long transactionId,
            Long accountId,
            Long fillId,
            BigDecimal amount,
            BigDecimal balanceAfter,
            LocalDateTime createdAt,
            String description) {

        static CashTransactionEntry from(
                CashTransaction transaction) {

            return new CashTransactionEntry(
                    transaction.getId(),

                    transaction.getAccount() == null
                            ? null
                            : transaction.getAccount().getId(),

                    transaction.getFill() == null
                            ? null
                            : transaction.getFill().getId(),

                    transaction.getAmount(),

                    transaction.getBalanceAfter(),

                    transaction.getCreatedAt(),

                    transaction.getDescription());
        }
    }

    public record PositionImpact(
            BigDecimal quantityBefore,
            BigDecimal quantityAfter,
            BigDecimal quantityChange,
            LocalDateTime recordedAt) {
    }
}