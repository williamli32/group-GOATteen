package com.goatteen.trading.audit;

import com.goatteen.trading.execution.Fill;
import com.goatteen.trading.execution.FillRepository;
import com.goatteen.trading.marketdata.Quote;
import com.goatteen.trading.order.Order;
import com.goatteen.trading.order.OrderRepository;
import com.goatteen.trading.order.OrderSide;
import com.goatteen.trading.order.OrderStatus;
import com.goatteen.trading.portfolio.CashTransaction;
import com.goatteen.trading.portfolio.CashTransactionRepository;
import com.goatteen.trading.portfolio.Position;
import com.goatteen.trading.portfolio.PositionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class TradeReconstructionService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final FillRepository fillRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final PositionHistoryRepository positionHistoryRepository;
    private final PositionRepository positionRepository;

    public TradeReconstructionService(
            OrderRepository orderRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            FillRepository fillRepository,
            CashTransactionRepository cashTransactionRepository,
            PositionHistoryRepository positionHistoryRepository,
            PositionRepository positionRepository) {

        this.orderRepository = orderRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.fillRepository = fillRepository;
        this.cashTransactionRepository = cashTransactionRepository;
        this.positionHistoryRepository = positionHistoryRepository;
        this.positionRepository = positionRepository;
    }

    /**
     * Reconstruct a completed or rejected trade entirely from persisted data.
     */
    public TradeAuditTrail reconstructTrade(Long orderId) {

        Order order = orderRepository
                .findById(orderId)
                .orElseThrow(() -> new TradeReconstructionException(
                        "Order not found: " + orderId));

        validateOrderBasics(order);

        List<OrderStatusHistory> statusHistory = orderStatusHistoryRepository
                .findByOrderIdOrderByChangedAtAscIdAsc(orderId);

        validateLifecycle(order, statusHistory);

        Fill fill = fillRepository
                .findByOrderId(orderId)
                .orElse(null);

        List<CashTransaction> cashTransactions = List.of();

        PositionHistory positionHistory = null;

        if (order.getStatus() == OrderStatus.FILLED) {

            if (fill == null) {
                throw new TradeReconstructionException(
                        "Filled order has no Fill: " + orderId);
            }

            validateFill(order, fill);

            cashTransactions = cashTransactionRepository
                    .findByFillIdOrderByCreatedAtAsc(
                            fill.getId());

            validateCashSettlement(
                    order,
                    fill,
                    cashTransactions);

            validateCurrentCashBalance(order);

            positionHistory = positionHistoryRepository
                    .findByFillId(fill.getId())
                    .orElseThrow(() -> new TradeReconstructionException(
                            "Filled order has no position history: "
                                    + orderId));

            validatePositionHistory(
                    order,
                    fill,
                    positionHistory);

            validateCurrentPosition(order);

        } else if (order.getStatus() == OrderStatus.REJECTED) {

            if (fill != null) {
                throw new TradeReconstructionException(
                        "Rejected order has a Fill: " + orderId);
            }

            if (order.getRejectionReason() == null
                    || order.getRejectionReason().isBlank()) {

                throw new TradeReconstructionException(
                        "Rejected order has no rejection reason: "
                                + orderId);
            }

            if (order.getCompletedAt() == null) {

                throw new TradeReconstructionException(
                        "Rejected order has no completed_at timestamp: "
                                + orderId);
            }
        }

        TradeAuditTrail audit = new TradeAuditTrail(
                order,
                statusHistory,
                fill,
                cashTransactions,
                positionHistory);

        if (!audit.isFullyReconstructable()) {

            throw new TradeReconstructionException(
                    "Trade audit trail is incomplete: "
                            + orderId);
        }

        return audit;
    }

    /**
     * Reconstruct and return an API-safe DTO.
     */
    public TradeAuditResponse reconstructTradeResponse(
            Long orderId) {

        return TradeAuditResponse.from(
                reconstructTrade(orderId));
    }

    /**
     * Verify persisted trade integrity without exposing the underlying
     * reconstruction exception to callers.
     */
    public TradeIntegrityReport verifyTradeIntegrity(
            Long orderId) {

        try {

            TradeAuditTrail audit = reconstructTrade(orderId);

            return new TradeIntegrityReport(
                    orderId,
                    audit.isFullyReconstructable(),
                    "Trade integrity verified");

        } catch (TradeReconstructionException e) {

            return new TradeIntegrityReport(
                    orderId,
                    false,
                    e.getMessage());
        }
    }

    private void validateOrderBasics(Order order) {

        if (order.getId() == null) {
            throw new TradeReconstructionException(
                    "Persisted order has no ID");
        }

        if (order.getAccount() == null) {
            throw new TradeReconstructionException(
                    "Order has no account: " + order.getId());
        }

        if (order.getInstrument() == null) {
            throw new TradeReconstructionException(
                    "Order has no instrument: " + order.getId());
        }

        if (order.getSide() == null) {
            throw new TradeReconstructionException(
                    "Order has no side: " + order.getId());
        }

        if (order.getQuantity() == null
                || order.getQuantity()
                        .compareTo(BigDecimal.ZERO) <= 0) {

            throw new TradeReconstructionException(
                    "Order has invalid quantity: "
                            + order.getId());
        }

        if (order.getSubmittedAt() == null) {
            throw new TradeReconstructionException(
                    "Order has no submitted_at timestamp: "
                            + order.getId());
        }

        if (order.getStatus() == null) {
            throw new TradeReconstructionException(
                    "Order has no status: "
                            + order.getId());
        }
    }

    private void validateLifecycle(
            Order order,
            List<OrderStatusHistory> history) {

        if (history == null || history.isEmpty()) {

            throw new TradeReconstructionException(
                    "No status history found for order: "
                            + order.getId());
        }

        for (OrderStatusHistory entry : history) {

            if (entry.getOrder() == null
                    || entry.getOrder().getId() == null
                    || !order.getId()
                            .equals(entry.getOrder().getId())) {

                throw new TradeReconstructionException(
                        "Status history entry is linked to a different order: "
                                + entry.getId());
            }

            if (entry.getStatus() == null) {

                throw new TradeReconstructionException(
                        "Status history entry has no status: "
                                + entry.getId());
            }

            if (entry.getChangedAt() == null) {

                throw new TradeReconstructionException(
                        "Status history entry has no timestamp: "
                                + entry.getId());
            }
        }

        List<OrderStatus> actualStatuses = history.stream()
                .map(OrderStatusHistory::getStatus)
                .toList();

        List<OrderStatus> expectedStatuses;

        if (order.getStatus() == OrderStatus.FILLED) {

            expectedStatuses = List.of(
                    OrderStatus.SUBMITTED,
                    OrderStatus.ACCEPTED,
                    OrderStatus.FILLED);

        } else if (order.getStatus() == OrderStatus.REJECTED) {

            expectedStatuses = List.of(
                    OrderStatus.SUBMITTED,
                    OrderStatus.REJECTED);

        } else {

            throw new TradeReconstructionException(
                    "Order is not in a completed state: "
                            + order.getStatus());
        }

        if (!expectedStatuses.equals(actualStatuses)) {

            throw new TradeReconstructionException(
                    "Invalid persisted order-status lifecycle for order "
                            + order.getId()
                            + ": expected "
                            + expectedStatuses
                            + " but found "
                            + actualStatuses);
        }

        if (order.getCompletedAt() == null) {

            throw new TradeReconstructionException(
                    "Completed order has no completed_at timestamp: "
                            + order.getId());
        }
    }

    private void validateFill(
            Order order,
            Fill fill) {

        if (fill.getId() == null) {

            throw new TradeReconstructionException(
                    "Fill has no ID for order: "
                            + order.getId());
        }

        if (fill.getOrder() == null
                || fill.getOrder().getId() == null
                || !order.getId()
                        .equals(fill.getOrder().getId())) {

            throw new TradeReconstructionException(
                    "Fill is linked to a different order: "
                            + order.getId());
        }

        if (fill.getFillQuantity() == null) {

            throw new TradeReconstructionException(
                    "Fill has no quantity: "
                            + fill.getId());
        }

        if (fill.getFillQuantity()
                .compareTo(order.getQuantity()) != 0) {

            throw new TradeReconstructionException(
                    "Fill quantity does not match order quantity: "
                            + fill.getId());
        }

        if (fill.getFillPrice() == null) {

            throw new TradeReconstructionException(
                    "Fill has no execution price: "
                            + fill.getId());
        }

        if (fill.getExecutedAt() == null) {

            throw new TradeReconstructionException(
                    "Fill has no execution timestamp: "
                            + fill.getId());
        }

        Quote quote = fill.getQuote();

        if (quote == null) {

            throw new TradeReconstructionException(
                    "Fill has no persisted quote: "
                            + fill.getId());
        }

        if (quote.getId() == null) {

            throw new TradeReconstructionException(
                    "Quote has no ID for fill: "
                            + fill.getId());
        }

        if (quote.getInstrument() == null
                || quote.getInstrument().getId() == null
                || !order.getInstrument()
                        .getId()
                        .equals(quote.getInstrument().getId())) {

            throw new TradeReconstructionException(
                    "Fill quote belongs to a different instrument: "
                            + fill.getId());
        }

        if (quote.getBidPrice() == null
                || quote.getAskPrice() == null
                || quote.getQuotedAt() == null) {

            throw new TradeReconstructionException(
                    "Persisted quote is incomplete: "
                            + quote.getId());
        }

        BigDecimal expectedExecutionPrice = order.getSide() == OrderSide.BUY
                ? quote.getAskPrice()
                : quote.getBidPrice();

        if (fill.getFillPrice()
                .compareTo(expectedExecutionPrice) != 0) {

            throw new TradeReconstructionException(
                    "Fill price does not match the persisted quote: "
                            + fill.getId());
        }
    }

    private void validateCashSettlement(
            Order order,
            Fill fill,
            List<CashTransaction> transactions) {

        if (transactions == null
                || transactions.isEmpty()) {

            throw new TradeReconstructionException(
                    "No cash transaction found for fill: "
                            + fill.getId());
        }

        /*
         * One execution creates one cash settlement in the current
         * execution model. Multiple or zero transactions would make
         * reconstruction ambiguous.
         */
        if (transactions.size() != 1) {

            throw new TradeReconstructionException(
                    "Expected exactly one cash transaction for fill "
                            + fill.getId()
                            + " but found "
                            + transactions.size());
        }

        CashTransaction transaction = transactions.get(0);

        if (transaction.getAccount() == null
                || transaction.getAccount().getId() == null
                || !order.getAccount()
                        .getId()
                        .equals(transaction.getAccount().getId())) {

            throw new TradeReconstructionException(
                    "Cash transaction belongs to a different account: "
                            + transaction.getId());
        }

        if (transaction.getFill() == null
                || transaction.getFill().getId() == null
                || !fill.getId()
                        .equals(transaction.getFill().getId())) {

            throw new TradeReconstructionException(
                    "Cash transaction is not linked to the fill: "
                            + transaction.getId());
        }
        if (transaction.getAmount() == null) {
            throw new TradeReconstructionException(
                    "Cash transaction has no amount: "
                            + transaction.getId());
        }

        if (transaction.getBalanceBefore() == null) {
            throw new TradeReconstructionException(
                    "Cash transaction has no starting balance: "
                            + transaction.getId());
        }

        if (transaction.getBalanceAfter() == null) {
            throw new TradeReconstructionException(
                    "Cash transaction has no resulting balance: "
                            + transaction.getId());
        }

        BigDecimal calculatedBalanceAfter = transaction.getBalanceBefore()
                .add(transaction.getAmount());

        if (calculatedBalanceAfter.compareTo(transaction.getBalanceAfter()) != 0) {
            throw new TradeReconstructionException(
                    "Cash transaction balance is inconsistent: "
                            + transaction.getId());
        }
        if (transaction.getCreatedAt() == null) {

            throw new TradeReconstructionException(
                    "Cash transaction has no timestamp: "
                            + transaction.getId());
        }

        BigDecimal expectedCashMovement = fill.getFillPrice()
                .multiply(fill.getFillQuantity())
                .setScale(2, RoundingMode.HALF_UP);
        if (order.getSide() == OrderSide.BUY) {
            expectedCashMovement = expectedCashMovement.negate();
        }

        if (transaction.getAmount()
                .compareTo(expectedCashMovement) != 0) {

            throw new TradeReconstructionException(
                    "Cash transaction amount does not match execution: "
                            + transaction.getId());
        }
    }

    private void validatePositionHistory(
            Order order,
            Fill fill,
            PositionHistory history) {

        if (history.getAccount() == null
                || history.getAccount().getId() == null
                || !order.getAccount()
                        .getId()
                        .equals(history.getAccount().getId())) {

            throw new TradeReconstructionException(
                    "Position history belongs to a different account: "
                            + history.getId());
        }

        if (history.getInstrument() == null
                || history.getInstrument().getId() == null
                || !order.getInstrument()
                        .getId()
                        .equals(history.getInstrument().getId())) {

            throw new TradeReconstructionException(
                    "Position history belongs to a different instrument: "
                            + history.getId());
        }

        if (history.getFill() == null
                || history.getFill().getId() == null
                || !fill.getId()
                        .equals(history.getFill().getId())) {

            throw new TradeReconstructionException(
                    "Position history is not linked to the fill: "
                            + history.getId());
        }

        if (history.getQuantityBefore() == null
                || history.getQuantityAfter() == null
                || history.getQuantityChange() == null) {

            throw new TradeReconstructionException(
                    "Position history is incomplete: "
                            + history.getId());
        }

        if (history.getRecordedAt() == null) {

            throw new TradeReconstructionException(
                    "Position history has no timestamp: "
                            + history.getId());
        }

        BigDecimal calculatedChange = history.getQuantityAfter()
                .subtract(history.getQuantityBefore());

        if (history.getQuantityChange()
                .compareTo(calculatedChange) != 0) {

            throw new TradeReconstructionException(
                    "Position history quantity change is inconsistent: "
                            + history.getId());
        }

        BigDecimal expectedChange = order.getSide() == OrderSide.BUY
                ? order.getQuantity()
                : order.getQuantity().negate();

        if (history.getQuantityChange()
                .compareTo(expectedChange) != 0) {

            throw new TradeReconstructionException(
                    "Position history change does not match order quantity: "
                            + history.getId());
        }

        if (history.getQuantityBefore()
                .compareTo(BigDecimal.ZERO) < 0
                || history.getQuantityAfter()
                        .compareTo(BigDecimal.ZERO) < 0) {

            throw new TradeReconstructionException(
                    "Position history contains a negative quantity: "
                            + history.getId());
        }
    }

    private void validateCurrentPosition(Order order) {

        PositionHistory latestHistory = positionHistoryRepository
                .findFirstByAccountIdAndInstrumentIdOrderByRecordedAtDescIdDesc(
                        order.getAccount().getId(),
                        order.getInstrument().getId())
                .orElseThrow(() -> new TradeReconstructionException(
                        "No position history found for current position: "
                                + order.getId()));

        Position position = positionRepository
                .findByAccountIdAndInstrumentId(
                        order.getAccount().getId(),
                        order.getInstrument().getId())
                .orElseThrow(() -> new TradeReconstructionException(
                        "Current position not found for order: "
                                + order.getId()));

        if (latestHistory.getQuantityAfter() == null) {
            throw new TradeReconstructionException(
                    "Latest position history has no resulting quantity: "
                            + latestHistory.getId());
        }

        if (position.getQuantity() == null) {
            throw new TradeReconstructionException(
                    "Current position has no quantity: "
                            + position.getId());
        }

        if (latestHistory.getQuantityAfter()
                .compareTo(position.getQuantity()) != 0) {

            throw new TradeReconstructionException(
                    "Current position does not match latest position history for order: "
                            + order.getId());
        }
    }

    private void validateCurrentCashBalance(Order order) {

        CashTransaction latestTransaction = cashTransactionRepository
                .findFirstByAccountIdOrderByCreatedAtDescIdDesc(
                        order.getAccount().getId())
                .orElseThrow(() -> new TradeReconstructionException(
                        "No cash transaction found for account: "
                                + order.getAccount().getId()));

        if (latestTransaction.getBalanceAfter() == null) {
            throw new TradeReconstructionException(
                    "Latest cash transaction has no resulting balance: "
                            + latestTransaction.getId());
        }

        if (order.getAccount().getCashBalance() == null) {
            throw new TradeReconstructionException(
                    "Account has no current cash balance: "
                            + order.getAccount().getId());
        }

        if (latestTransaction.getBalanceAfter()
                .compareTo(order.getAccount().getCashBalance()) != 0) {

            throw new TradeReconstructionException(
                    "Current account cash balance does not match latest cash transaction for account: "
                            + order.getAccount().getId());
        }
    }

    public static class TradeReconstructionException
            extends RuntimeException {

        public TradeReconstructionException(String message) {
            super(message);
        }
    }
}