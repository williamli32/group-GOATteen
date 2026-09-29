package com.goatteen.trading.execution;

import com.goatteen.trading.account.Account;
import com.goatteen.trading.account.AccountRepository;
import com.goatteen.trading.audit.OrderStatusHistory;
import com.goatteen.trading.audit.OrderStatusHistoryRepository;
import com.goatteen.trading.audit.PositionHistory;
import com.goatteen.trading.audit.PositionHistoryRepository;
import com.goatteen.trading.instrument.Instrument;
import com.goatteen.trading.marketdata.Quote;
import com.goatteen.trading.marketdata.QuoteRepository;
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
import java.time.LocalDateTime;

@Service
public class OrderExecutionService {

    private final OrderRepository orderRepository;
    private final FillRepository fillRepository;
    private final QuoteRepository quoteRepository;
    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final PositionHistoryRepository positionHistoryRepository;

    public OrderExecutionService(OrderRepository orderRepository, FillRepository fillRepository,
            QuoteRepository quoteRepository, AccountRepository accountRepository,
            PositionRepository positionRepository, CashTransactionRepository cashTransactionRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            PositionHistoryRepository positionHistoryRepository) {
        this.orderRepository = orderRepository;
        this.fillRepository = fillRepository;
        this.quoteRepository = quoteRepository;
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.cashTransactionRepository = cashTransactionRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.positionHistoryRepository = positionHistoryRepository;
    }

    @Transactional
    public void submitOrder(Order order) {

        order.setStatus(
                OrderStatus.SUBMITTED);

        order.setSubmittedAt(
                LocalDateTime.now());

        Order saved = orderRepository.save(order);

        recordStatusChange(
                saved,
                OrderStatus.SUBMITTED,
                "Order submitted");
    }

    @Transactional
    public void acceptOrder(
            Long orderId) throws OrderExecutionException {

        Order order = orderRepository
                .findById(orderId)
                .orElseThrow(
                        () -> new OrderExecutionException(
                                "Order not found"));

        if (order.getStatus() != OrderStatus.SUBMITTED) {

            throw new OrderExecutionException(
                    "Order is not in SUBMITTED state");
        }

        order.setStatus(
                OrderStatus.ACCEPTED);

        orderRepository.save(order);

        recordStatusChange(
                order,
                OrderStatus.ACCEPTED,
                "Order accepted");
    }

    @Transactional
    public void executeOrder(Long orderId) throws OrderExecutionException {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderExecutionException("Order not found"));

        if (order.getStatus() != OrderStatus.ACCEPTED) {
            throw new OrderExecutionException("Order is not in ACCEPTED state");
        }

        // Fetch current quote (BR-08: price against current market quote)
        Quote quote = quoteRepository.findTopByInstrumentIdOrderByQuotedAtDesc(order.getInstrument().getId())
                .orElseThrow(() -> new OrderExecutionException("No market quote available for instrument"));

        // Determine execution price based on side (BR-08)
        BigDecimal executionPrice = order.getSide() == OrderSide.BUY ? quote.getAskPrice() : quote.getBidPrice();

        // Atomically update account, position, and record fill (BR-09)
        Account account = accountRepository.findById(order.getAccount().getId())
                .orElseThrow(() -> new OrderExecutionException("Account not found"));

        // Refresh to get latest version for optimistic locking
        account = accountRepository.findById(account.getId()).orElseThrow();

        // Update cash and positions atomically
        /*
         * Re-check currency at execution time as well.
         * Validation may have happened earlier and LEAP does
         * not support FX conversion yet.
         */
        if (!account.getCurrency()
                .equalsIgnoreCase(
                        order.getInstrument().getCurrency())) {

            throw new OrderExecutionException(
                    "Currency conversion is not supported yet");
        }

        /*
         * Cash and position mutations happen inside the
         * executeOrder transaction.
         */
        CashTransaction cashTransaction = updateCashBalance(
                account,
                order,
                executionPrice);

        // Record the fill (BR-08, BR-09, BR-14)
        Fill fill = new Fill();
        fill.setOrder(order);
        fill.setQuote(quote);
        fill.setFillPrice(executionPrice);
        fill.setFillQuantity(order.getQuantity());
        fill.setExecutedAt(LocalDateTime.now());
        fillRepository.save(fill);
        cashTransaction.setFill(fill);

        // Update position AFTER fill is created so we can record position history
        // with fill reference for complete audit trail reconstructability
        updatePosition(
                account,
                order,
                fill);

        // Update order status to FILLED (BR-06, BR-14)
        order.setStatus(OrderStatus.FILLED);
        order.setCompletedAt(LocalDateTime.now());
        orderRepository.save(order);
        recordStatusChange(order, OrderStatus.FILLED, "Order filled at " + executionPrice);
    }

    @Transactional
    public void rejectOrder(Long orderId, String reason) throws OrderExecutionException {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderExecutionException("Order not found"));

        if (order.getStatus() != OrderStatus.SUBMITTED) {
            throw new OrderExecutionException("Order is not in SUBMITTED state");
        }

        order.setStatus(OrderStatus.REJECTED);
        order.setRejectionReason(reason);
        order.setCompletedAt(LocalDateTime.now());
        orderRepository.save(order);
        recordStatusChange(order, OrderStatus.REJECTED, reason);
    }

    private CashTransaction updateCashBalance(Account account, Order order, BigDecimal executionPrice)
            throws OrderExecutionException {
        BigDecimal totalCost = executionPrice.multiply(order.getQuantity());

        if (order.getSide() == OrderSide.BUY) {
            // Deduct cash
            BigDecimal newBalance = account.getCashBalance().subtract(totalCost);
            if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                throw new OrderExecutionException("Insufficient cash after fill");
            }
            account.setCashBalance(newBalance);
        } else {
            // Add cash
            account.setCashBalance(account.getCashBalance().add(totalCost));
        }

        // Final execution check: ensure balance never goes negative before persisting
        if (account.getCashBalance().compareTo(BigDecimal.ZERO) < 0) {
            throw new OrderExecutionException("Fatal: Account balance would be negative - operation aborted");
        }

        accountRepository.save(account);

        // Record cash transaction (BR-09, BR-14)
        CashTransaction transaction = new CashTransaction();
        transaction.setAccount(account);
        transaction.setAmount(order.getSide() == OrderSide.BUY ? totalCost.negate() : totalCost);
        transaction.setBalanceAfter(account.getCashBalance());
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setDescription(order.getSide() + " " + order.getQuantity() + " @ " + executionPrice);
        cashTransactionRepository.save(transaction);
        return transaction;
    }

    private void updatePosition(Account account, Order order, Fill fill) 
            throws OrderExecutionException {
        Position position = positionRepository
                .findByAccountIdAndInstrumentId(
                        account.getId(),
                        order.getInstrument().getId())
                .orElse(null);

        if (position == null || position.getId() == null) {
            position = new Position();
            position.setAccount(account);
            position.setInstrument(order.getInstrument());
            position.setQuantity(BigDecimal.ZERO);
        }

        // Capture position BEFORE for audit trail
        BigDecimal quantityBefore = position.getQuantity();

        BigDecimal newQuantity;
        if (order.getSide() == OrderSide.BUY) {
            newQuantity = position.getQuantity().add(order.getQuantity());
        } else {
            newQuantity = position.getQuantity().subtract(order.getQuantity());
        }

        // Ensure holdings never go negative 
        if (newQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new OrderExecutionException("Fatal: Position holdings would be negative - operation aborted");
        }

        position.setQuantity(newQuantity);
        position.setUpdatedAt(LocalDateTime.now());
        positionRepository.save(position);

        // Record position change for audit trail (Requirement: Make trade fully reconstructable)
        // This enables operations to determine position change from persisted data
        PositionHistory positionHistory = new PositionHistory(
                account,
                order.getInstrument(),
                fill,
                quantityBefore,
                newQuantity
        );
        positionHistoryRepository.save(positionHistory);
    }

    private void recordStatusChange(Order order, OrderStatus status, String note) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setStatus(status);
        history.setChangedAt(LocalDateTime.now());
        history.setNote(note);
        orderStatusHistoryRepository.save(history);
    }

    public static class OrderExecutionException extends RuntimeException {
        public OrderExecutionException(String message) {
            super(message);
        }
    }
}
