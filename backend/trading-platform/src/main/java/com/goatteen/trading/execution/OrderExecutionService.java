package com.goatteen.trading.execution;

import com.goatteen.trading.account.Account;
import com.goatteen.trading.account.AccountRepository;
import com.goatteen.trading.audit.OrderStatusHistory;
import com.goatteen.trading.audit.OrderStatusHistoryRepository;
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
import com.goatteen.trading.audit.PositionHistory;
import com.goatteen.trading.audit.PositionHistoryRepository;
import java.util.Optional;

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
                                .findByIdForUpdate(orderId)
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
        public Fill executeOrder(Long orderId)
                        throws OrderExecutionException {

                return executeOrder(orderId, null);
        }

        @Transactional
        public Fill executeOrder(
                        Long orderId,
                        String idempotencyKey)
                        throws OrderExecutionException {

                String normalizedKey = idempotencyKey == null ||
                                idempotencyKey.isBlank()
                                                ? null
                                                : idempotencyKey.trim();

                /*
                 * 1. Persistent idempotency-key lookup.
                 *
                 * If this exact execution was already completed,
                 * return the original Fill.
                 */
                if (normalizedKey != null) {

                        Optional<Fill> existingByKey = fillRepository.findByIdempotencyKey(
                                        normalizedKey);

                        if (existingByKey.isPresent()) {

                                Fill existingFill = existingByKey.get();

                                Long existingOrderId = existingFill
                                                .getOrder()
                                                .getId();

                                if (!existingOrderId.equals(orderId)) {

                                        throw new OrderExecutionException(
                                                        "Idempotency key has already been used for a different order");
                                }

                                return existingFill;
                        }
                }

                /*
                 * 2. Lock the order.
                 *
                 * This prevents two execution attempts from
                 * settling the same order concurrently.
                 */
                Order order = orderRepository
                                .findByIdForUpdate(orderId)
                                .orElseThrow(
                                                () -> new OrderExecutionException(
                                                                "Order not found"));

                /*
                 * 3. Check whether this order already has a Fill.
                 */
                Optional<Fill> existingByOrder = fillRepository.findByOrderId(orderId);

                if (existingByOrder.isPresent()) {

                        Fill existingFill = existingByOrder.get();

                        /*
                         * Same idempotency key:
                         * return the original result.
                         */
                        if (normalizedKey != null &&
                                        normalizedKey.equals(
                                                        existingFill.getIdempotencyKey())) {

                                return existingFill;
                        }

                        /*
                         * Different key attempting to execute
                         * an already-settled order.
                         */
                        throw new OrderExecutionException(
                                        "Duplicate execution detected: Fill already exists for this order");
                }

                /*
                 * 4. The only executable state is ACCEPTED.
                 */
                if (order.getStatus() != OrderStatus.ACCEPTED) {

                        throw new OrderExecutionException(
                                        "Order is not in ACCEPTED state");
                }

                /*
                 * 5. Get the current quote.
                 */
                Quote quote = quoteRepository
                                .findTopByInstrumentIdOrderByQuotedAtDesc(
                                                order.getInstrument().getId())
                                .orElseThrow(
                                                () -> new OrderExecutionException(
                                                                "No market quote available for instrument"));

                BigDecimal executionPrice = order.getSide() == OrderSide.BUY
                                ? quote.getAskPrice()
                                : quote.getBidPrice();

                /*
                 * 6. Load account.
                 */
                Account account = accountRepository
                                .findById(
                                                order.getAccount().getId())
                                .orElseThrow(
                                                () -> new OrderExecutionException(
                                                                "Account not found"));

                /*
                 * 7. Currency check.
                 */
                if (!account.getCurrency()
                                .equalsIgnoreCase(
                                                order.getInstrument().getCurrency())) {

                        throw new OrderExecutionException(
                                        "Currency conversion is not supported yet");
                }

                /*
                 * 8. Create the Fill BEFORE modifying cash/position.
                 *
                 * saveAndFlush() forces the database uniqueness
                 * constraint to be checked immediately.
                 */
                Fill fill = new Fill();

                fill.setOrder(order);

                fill.setQuote(quote);

                fill.setFillPrice(
                                executionPrice);

                fill.setFillQuantity(
                                order.getQuantity());

                fill.setExecutedAt(
                                LocalDateTime.now());

                fill.setIdempotencyKey(
                                normalizedKey);

                fill = fillRepository.saveAndFlush(
                                fill);

                /*
                 * 9. Cash settlement.
                 */
                CashTransaction cashTransaction = updateCashBalance(
                                account,
                                order,
                                executionPrice);

                cashTransaction.setFill(fill);

                cashTransactionRepository.save(
                                cashTransaction);

                /*
                 * 10. Position settlement.
                 */
                updatePosition(
                                account,
                                order,
                                fill);

                /*
                 * 11. Mark order FILLED.
                 */
                order.setStatus(
                                OrderStatus.FILLED);

                order.setCompletedAt(
                                LocalDateTime.now());

                orderRepository.save(
                                order);

                /*
                 * 12. Persist status history.
                 */
                recordStatusChange(
                                order,
                                OrderStatus.FILLED,
                                "Order filled at " + executionPrice);

                return fill;
        }

        @Transactional
        public void rejectOrder(Long orderId, String reason) throws OrderExecutionException {
                Order order = orderRepository.findByIdForUpdate(orderId)
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

        private void updatePosition(
                        Account account,
                        Order order, Fill fill)
                        throws OrderExecutionException {

                Position position = positionRepository
                                .findByAccountIdAndInstrumentId(
                                                account.getId(),
                                                order.getInstrument().getId())
                                .orElse(null);

                BigDecimal quantityBefore;

                /*
                 * BUY:
                 * Create a position when the account does not
                 * already own the instrument.
                 */
                if (order.getSide() == OrderSide.BUY) {

                        if (position == null) {

                                position = new Position();

                                position.setAccount(account);
                                position.setInstrument(
                                                order.getInstrument());

                                position.setQuantity(
                                                BigDecimal.ZERO);
                        }

                        quantityBefore = position.getQuantity();

                        position.setQuantity(
                                        quantityBefore.add(
                                                        order.getQuantity()));
                }

                /*
                 * SELL:
                 * Re-check the holding at execution time.
                 *
                 * Validation occurred before acceptance, so the
                 * position may have changed before execution.
                 */
                else {

                        if (position == null ||
                                        position.getQuantity()
                                                        .compareTo(
                                                                        order.getQuantity()) < 0) {

                                throw new OrderExecutionException(
                                                "Insufficient holding at execution time");
                        }

                        quantityBefore = position.getQuantity();

                        position.setQuantity(
                                        quantityBefore.subtract(
                                                        order.getQuantity()));
                }

                /*
                 * Defensive check in addition to the database
                 * CHECK constraint.
                 */
                if (position.getQuantity()
                                .compareTo(
                                                BigDecimal.ZERO) < 0) {

                        throw new OrderExecutionException(
                                        "Position cannot become negative");
                }

                position.setUpdatedAt(
                                LocalDateTime.now());

                positionRepository.save(
                                position);

                PositionHistory history = new PositionHistory(
                                account,
                                order.getInstrument(),
                                fill,
                                quantityBefore,
                                position.getQuantity());

                positionHistoryRepository.save(
                                history);
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
