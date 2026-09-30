package com.goatteen.trading.audit;

import com.goatteen.trading.execution.Fill;
import com.goatteen.trading.order.Order;
import com.goatteen.trading.order.OrderRepository;
import com.goatteen.trading.portfolio.CashTransaction;
import com.goatteen.trading.portfolio.CashTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.goatteen.trading.execution.FillRepository;

import java.util.List;

/**
 * Trade Reconstruction Service
 * 
 * Purpose: Enable complete reconstruction of any completed or rejected trade
 * from persisted data alone.
 * 
 * Requirement: Given an orderId, we can reconstruct what happened without
 * relying on
 * application logs or frontend state.
 * 
 * A trade is traceable through:
 * SUBMITTED -> ACCEPTED -> FILLED
 * or:
 * SUBMITTED -> REJECTED
 * 
 * For every executed trade, operations can determine:
 * ✓ Original order (with details: side, quantity)
 * ✓ Account
 * ✓ Instrument
 * ✓ Quote actually used
 * ✓ Execution price and time
 * ✓ Resulting Fill
 * ✓ Cash movement
 * ✓ Resulting balance
 * ✓ Position change (before/after)
 * ✓ Complete order status history
 */
@Service
@Transactional(readOnly = true)
public class TradeReconstructionService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final PositionHistoryRepository positionHistoryRepository;
    private final FillRepository fillRepository;

    public TradeReconstructionService(
            OrderRepository orderRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            CashTransactionRepository cashTransactionRepository,
            PositionHistoryRepository positionHistoryRepository,
            FillRepository fillRepository) {

        this.orderRepository = orderRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;

        this.cashTransactionRepository = cashTransactionRepository;

        this.positionHistoryRepository = positionHistoryRepository;

        this.fillRepository = fillRepository;
    }

    /**
     * Reconstruct complete trade audit trail from orderId.
     * 
     * Returns all information needed to replay the trade without external
     * dependencies:
     * - Order details (account, instrument, side, quantity)
     * - Status history (SUBMITTED -> ACCEPTED -> FILLED or SUBMITTED -> REJECTED)
     * - Quote used (bid/ask/last prices at execution time)
     * - Fill details (execution price, quantity, timestamp)
     * - Cash transactions (amounts, resulting balance)
     * - Position changes (before/after quantities)
     */
    public TradeAuditTrail reconstructTrade(Long orderId) throws TradeReconstructionException {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new TradeReconstructionException("Order not found: " + orderId));

        // Get status history
        List<OrderStatusHistory> statusHistory = orderStatusHistoryRepository.findByOrderIdOrderByChangedAtAsc(orderId);
        if (statusHistory.isEmpty()) {
            throw new TradeReconstructionException("No status history found for order: " + orderId);
        }

        // Get fill (if trade was executed)
        Fill fill = reconstructFillFromOrder(order);

        // Get cash transactions
        List<CashTransaction> cashTransactions = fill != null
                ? cashTransactionRepository.findByFillIdOrderByCreatedAtAsc(fill.getId())
                : List.of();

        // Get position changes
        PositionHistory positionChange = fill != null
                ? positionHistoryRepository.findByFillId(fill.getId()).orElse(null)
                : null;

        return new TradeAuditTrail(order, statusHistory, fill, cashTransactions, positionChange);
    }

    /**
     * Reconstruct all trades for an account within a date range
     */
    public List<TradeAuditTrail> reconstructAccountTrades(Long accountId) {
        List<Order> orders = orderRepository.findByAccountIdOrderBySubmittedAtDesc(accountId);
        return orders.stream()
                .map(order -> {
                    try {
                        return reconstructTrade(order.getId());
                    } catch (TradeReconstructionException e) {
                        // Log and skip incomplete trades
                        return null;
                    }
                })
                .filter(trade -> trade != null)
                .toList();
    }

    /**
     * Verify trade integrity: ensure all components exist and link correctly
     */
    public TradeIntegrityReport verifyTradeIntegrity(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return new TradeIntegrityReport(orderId, false, "Order not found");
        }

        List<OrderStatusHistory> statusHistory = orderStatusHistoryRepository
                .findByOrderIdOrderByChangedAtAsc(orderId);
        if (statusHistory.isEmpty()) {
            return new TradeIntegrityReport(orderId, false, "No status history found");
        }

        // For filled trades, verify fill and related data exist
        Fill fill = reconstructFillFromOrder(order);
        if (fill != null) {
            List<CashTransaction> cashTransactions = cashTransactionRepository
                    .findByFillIdOrderByCreatedAtAsc(fill.getId());
            if (cashTransactions.isEmpty()) {
                return new TradeIntegrityReport(orderId, false, "No cash transactions found for fill");
            }

            PositionHistory positionChange = positionHistoryRepository.findByFillId(fill.getId()).orElse(null);
            if (positionChange == null) {
                return new TradeIntegrityReport(orderId, false, "No position history found for fill");
            }
        }

        return new TradeIntegrityReport(orderId, true, "Trade integrity verified");
    }

    private Fill reconstructFillFromOrder(
            Order order) {

        return fillRepository
                .findByOrderId(
                        order.getId())
                .orElse(null);
    }

    public static class TradeReconstructionException extends Exception {
        public TradeReconstructionException(String message) {
            super(message);
        }
    }
}