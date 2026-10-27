package com.goatteen.trading.execution;

import org.springframework.context.ApplicationEventPublisher;

import com.goatteen.trading.account.Account;

import com.goatteen.trading.account.AccountRepository;

import com.goatteen.trading.client.Client;

import com.goatteen.trading.user.User;

import com.goatteen.trading.reporting.event.OrderFilledEvent;

import com.goatteen.trading.audit.OrderStatusHistory;

import com.goatteen.trading.audit.OrderStatusHistoryRepository;

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

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;

import org.mockito.Mock;

import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import java.time.LocalDateTime;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)

class OrderExecutionServiceTest {
        @Mock

        private ApplicationEventPublisher applicationEventPublisher; 

        @Mock

        private OrderRepository orderRepository;

        @Mock

        private FillRepository fillRepository;

        @Mock

        private QuoteRepository quoteRepository;

        @Mock

        private AccountRepository accountRepository;

        @Mock
        private PositionHistoryRepository positionHistoryRepository;

        @Mock

        private PositionRepository positionRepository;

        @Mock

        private CashTransactionRepository cashTransactionRepository;

        @Mock

        private OrderStatusHistoryRepository historyRepository;

        @Mock

        private Instrument instrument;

        @Mock

        private Quote quote;

        private OrderExecutionService service;

        private Account account;

        private Order order;

        @BeforeEach

        void setUp() {

                service = new OrderExecutionService(
                                orderRepository,
                                fillRepository,
                                quoteRepository,
                                accountRepository,
                                positionRepository,
                                cashTransactionRepository,
                                historyRepository,
                                positionHistoryRepository,
                                applicationEventPublisher,
                                new com.goatteen.trading.fx.FxRateService(
                                                org.springframework.web.client.RestClient.builder()));

                Client client = new Client(new User("execution-unit@example.test", "test-hash"),
                                "Execution", "Test");
                ReflectionTestUtils.setField(client, "id", 20L);

                account = new Account(

                                client,

                                "LEAP-TEST123",

                                "GBP");

                ReflectionTestUtils.setField(

                                account, "id", 10L);

                account.setCashBalance(

                                new BigDecimal("1000.00"));

                order = new Order();

                ReflectionTestUtils.setField(

                                order, "id", 55L);

                order.setAccount(account);

                order.setInstrument(instrument);

                order.setSide(OrderSide.BUY);

                order.setQuantity(new BigDecimal("2"));

                order.setStatus(OrderStatus.ACCEPTED);

                order.setSubmittedAt(LocalDateTime.now().minusSeconds(2));
                order.setAcceptedAt(order.getSubmittedAt().plusSeconds(1));

        }

        @Test

        void shouldSettleBuyAtAskAndCreatePositionFillAndLedger() {

                // Arrange

                when(orderRepository.findByIdForUpdate(55L))

                                .thenReturn(Optional.of(order));

                when(instrument.getId())

                                .thenReturn(1L);

                when(instrument.getCurrency())

                                .thenReturn("GBP");

                when(quoteRepository

                                .findTopByInstrumentIdOrderByQuotedAtDesc(1L))

                                .thenReturn(Optional.of(quote));

                when(quote.getAskPrice())

                                .thenReturn(new BigDecimal("75.10"));

                when(quote.getBidPrice()).thenReturn(new BigDecimal("74.90"));
                when(quote.getQuotedAt()).thenReturn(order.getAcceptedAt());

                when(accountRepository.findById(10L))

                                .thenReturn(Optional.of(account));

                // The account has no existing position.

                when(positionRepository

                                .findByAccountIdAndInstrumentId(10L, 1L))

                                .thenReturn(Optional.empty());

                when(fillRepository.saveAndFlush(any(Fill.class)))
                                .thenAnswer(invocation -> {
                                        Fill fill = invocation.getArgument(0);
                                        ReflectionTestUtils.setField(fill, "id", 99L);
                                        return fill;
                                });

                // Act

                service.executeOrder(55L);

                // Assert: BUY 2 at £75.10 costs £150.20.

                assertEquals(

                                0,

                                new BigDecimal("849.80")

                                                .compareTo(account.getCashBalance()));

                // A new position must be created.

                ArgumentCaptor<Position> positionCaptor = ArgumentCaptor.forClass(Position.class);

                verify(positionRepository)

                                .save(positionCaptor.capture());

                Position savedPosition = positionCaptor.getValue();

                assertSame(account, savedPosition.getAccount());

                assertSame(instrument, savedPosition.getInstrument());

                assertEquals(

                                0,

                                new BigDecimal("2")

                                                .compareTo(savedPosition.getQuantity()));

                // Exactly one Fill must be created using this quote.

                ArgumentCaptor<Fill> fillCaptor = ArgumentCaptor.forClass(Fill.class);

                verify(fillRepository, times(1))

                                .saveAndFlush(fillCaptor.capture());

                Fill savedFill = fillCaptor.getValue();

                assertSame(order, savedFill.getOrder());

                assertSame(quote, savedFill.getQuote());

                assertEquals(

                                0,

                                new BigDecimal("75.10")

                                                .compareTo(savedFill.getFillPrice()));

                assertEquals(

                                0,

                                new BigDecimal("2")

                                                .compareTo(savedFill.getFillQuantity()));

                assertNotNull(savedFill.getExecutedAt());

                // Verify the cash ledger entry.

                ArgumentCaptor<CashTransaction> cashCaptor = ArgumentCaptor.forClass(CashTransaction.class);

                verify(cashTransactionRepository)

                                .save(cashCaptor.capture());

                CashTransaction cashEntry = cashCaptor.getValue();

                assertSame(account, cashEntry.getAccount());
                assertSame(savedFill, cashEntry.getFill());

                assertEquals(

                                0,

                                new BigDecimal("-150.20")

                                                .compareTo(cashEntry.getAmount()));

                assertEquals(

                                0,

                                new BigDecimal("849.80")

                                                .compareTo(cashEntry.getBalanceAfter()));

                // Verify order completion.

                assertEquals(OrderStatus.FILLED, order.getStatus());

                assertNotNull(order.getCompletedAt());

                verify(orderRepository).save(order);

                // Verify FILLED status history.

                ArgumentCaptor<OrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);

                verify(historyRepository)

                                .save(historyCaptor.capture());

                assertSame(

                                order,

                                historyCaptor.getValue().getOrder());

                assertEquals(

                                OrderStatus.FILLED,

                                historyCaptor.getValue().getStatus());

                assertPublishedFillEvent(savedFill, "BUY", new BigDecimal("75.10"), new BigDecimal("150.20"));

        }

        @Test

        void shouldSettleSellAtBidAndReduceExistingPosition() {

                // Arrange: the account owns 5 shares and sells 2.

                order.setSide(OrderSide.SELL);

                Position existingPosition = new Position();

                existingPosition.setAccount(account);

                existingPosition.setInstrument(instrument);

                existingPosition.setQuantity(new BigDecimal("5"));

                when(orderRepository.findByIdForUpdate(55L))

                                .thenReturn(Optional.of(order));

                when(instrument.getId())

                                .thenReturn(1L);

                when(instrument.getCurrency())

                                .thenReturn("GBP");

                when(quoteRepository

                                .findTopByInstrumentIdOrderByQuotedAtDesc(1L))

                                .thenReturn(Optional.of(quote));

                when(quote.getBidPrice())

                                .thenReturn(new BigDecimal("74.90"));

                when(quote.getAskPrice()).thenReturn(new BigDecimal("75.10"));
                when(quote.getQuotedAt()).thenReturn(order.getAcceptedAt());

                when(accountRepository.findById(10L))

                                .thenReturn(Optional.of(account));

                when(positionRepository

                                .findByAccountIdAndInstrumentId(10L, 1L))

                                .thenReturn(Optional.of(existingPosition));

                when(fillRepository.saveAndFlush(any(Fill.class)))
                                .thenAnswer(invocation -> {
                                        Fill fill = invocation.getArgument(0);
                                        ReflectionTestUtils.setField(fill, "id", 99L);
                                        return fill;
                                });
                // Act

                service.executeOrder(55L);

                // Cash: £1000 + (2 × £74.90) = £1149.80.

                assertEquals(

                                0,

                                new BigDecimal("1149.80")

                                                .compareTo(account.getCashBalance()));

                // Holdings: 5 - 2 = 3 shares.

                assertEquals(

                                0,

                                new BigDecimal("3")

                                                .compareTo(existingPosition.getQuantity()));

                verify(positionRepository, times(1))

                                .save(same(existingPosition));

                assertNotNull(existingPosition.getUpdatedAt());

                // Exactly one Fill must reference the executed order

                // and the quote actually used.

                ArgumentCaptor<Fill> fillCaptor = ArgumentCaptor.forClass(Fill.class);

                verify(fillRepository, times(1))

                                .saveAndFlush(fillCaptor.capture());

                Fill savedFill = fillCaptor.getValue();

                assertSame(order, savedFill.getOrder());

                assertSame(quote, savedFill.getQuote());

                assertEquals(

                                0,

                                new BigDecimal("74.90")

                                                .compareTo(savedFill.getFillPrice()));

                assertEquals(

                                0,

                                new BigDecimal("2")

                                                .compareTo(savedFill.getFillQuantity()));

                assertNotNull(savedFill.getExecutedAt());

                // SELL produces a positive cash ledger entry.

                ArgumentCaptor<CashTransaction> cashCaptor = ArgumentCaptor.forClass(CashTransaction.class);

                verify(cashTransactionRepository, times(1))

                                .save(cashCaptor.capture());

                CashTransaction cashEntry = cashCaptor.getValue();

                assertSame(account, cashEntry.getAccount());
                assertSame(savedFill, cashEntry.getFill());

                assertEquals(

                                0,

                                new BigDecimal("149.80")

                                                .compareTo(cashEntry.getAmount()));

                assertEquals(

                                0,

                                new BigDecimal("1149.80")

                                                .compareTo(cashEntry.getBalanceAfter()));

                // Order and audit history must show FILLED.

                assertEquals(OrderStatus.FILLED, order.getStatus());

                assertNotNull(order.getCompletedAt());

                verify(orderRepository, times(1)).save(order);

                ArgumentCaptor<OrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);

                verify(historyRepository, times(1))

                                .save(historyCaptor.capture());

                assertSame(order, historyCaptor.getValue().getOrder());

                assertEquals(

                                OrderStatus.FILLED,

                                historyCaptor.getValue().getStatus());

                assertPublishedFillEvent(savedFill, "SELL", new BigDecimal("74.90"), new BigDecimal("149.80"));

        }

        private void assertPublishedFillEvent(Fill fill, String side, BigDecimal price, BigDecimal value) {
                ArgumentCaptor<OrderFilledEvent> captor = ArgumentCaptor.forClass(OrderFilledEvent.class);
                verify(applicationEventPublisher, times(1)).publishEvent(captor.capture());
                OrderFilledEvent event = captor.getValue();
                assertAll("Published fill event",
                                () -> assertSame(service, event.getSource()),
                                () -> assertEquals(99L, event.getFillId()),
                                () -> assertEquals(fill.getId(), event.getFillId()),
                                () -> assertEquals(55L, event.getOrderId()),
                                () -> assertEquals(10L, event.getAccountId()),
                                () -> assertEquals(20L, event.getClientId()),
                                () -> assertEquals(1L, event.getInstrumentId()),
                                () -> assertEquals(side, event.getOrderSide()),
                                () -> assertEquals(0, new BigDecimal("2").compareTo(event.getOrderQuantity())),
                                () -> assertEquals(0, new BigDecimal("2").compareTo(event.getExecutionQuantity())),
                                () -> assertEquals(0, price.compareTo(event.getExecutionPrice())),
                                () -> assertEquals(0, value.compareTo(event.calculateTradeValue())),
                                () -> assertEquals(order.getSubmittedAt(), event.getOrderSubmittedAt()),
                                () -> assertEquals(order.getAcceptedAt(), event.getOrderAcceptedAt()),
                                () -> assertEquals(order.getCompletedAt(), event.getOrderFilledAt()),
                                () -> assertEquals("FILLED", event.getOrderStatus()),
                                () -> assertEquals(0, new BigDecimal("74.90").compareTo(event.getBidPrice())),
                                () -> assertEquals(0, new BigDecimal("75.10").compareTo(event.getAskPrice())),
                                () -> assertEquals(order.getAcceptedAt(), event.getQuoteTimestamp()));
        }

        @Test

        void shouldRejectBuyWhenCashIsInsufficientAtExecution() {

                account.setCashBalance(new BigDecimal("100.00"));

                when(orderRepository.findByIdForUpdate(55L))

                                .thenReturn(Optional.of(order));

                when(instrument.getId())

                                .thenReturn(1L);

                when(instrument.getCurrency())

                                .thenReturn("GBP");

                when(quoteRepository

                                .findTopByInstrumentIdOrderByQuotedAtDesc(1L))

                                .thenReturn(Optional.of(quote));

                when(quote.getAskPrice())

                                .thenReturn(new BigDecimal("75.10"));

                when(accountRepository.findById(10L))

                                .thenReturn(Optional.of(account));

                OrderExecutionService.OrderExecutionException exception = assertThrows(

                                OrderExecutionService.OrderExecutionException.class,

                                () -> service.executeOrder(55L));

                assertEquals(

                                "Insufficient cash after fill",

                                exception.getMessage());

                assertEquals(

                                0,

                                new BigDecimal("100.00")

                                                .compareTo(account.getCashBalance()));

                assertEquals(OrderStatus.ACCEPTED, order.getStatus());

                verify(accountRepository, never()).save(any());

                verifyNoInteractions(

                                positionRepository,

                                cashTransactionRepository,

                                fillRepository,

                                historyRepository);

                verify(orderRepository, never()).save(any());

        }

        @Test

        void shouldRejectSellWhenHoldingsAreInsufficientAtExecution() {

                order.setSide(OrderSide.SELL);

                when(orderRepository.findByIdForUpdate(55L))

                                .thenReturn(Optional.of(order));

                when(instrument.getId())

                                .thenReturn(1L);

                when(instrument.getCurrency())

                                .thenReturn("GBP");

                when(quoteRepository

                                .findTopByInstrumentIdOrderByQuotedAtDesc(1L))

                                .thenReturn(Optional.of(quote));

                when(quote.getBidPrice())

                                .thenReturn(new BigDecimal("74.90"));

                when(accountRepository.findById(10L))

                                .thenReturn(Optional.of(account));

                when(positionRepository

                                .findByAccountIdAndInstrumentId(10L, 1L))

                                .thenReturn(Optional.empty());

                OrderExecutionService.OrderExecutionException exception = assertThrows(

                                OrderExecutionService.OrderExecutionException.class,

                                () -> service.executeOrder(55L));

                assertEquals(

                                "Insufficient holding at execution time",

                                exception.getMessage());

                assertEquals(OrderStatus.ACCEPTED, order.getStatus());

                verify(positionRepository, never()).save(any());

                verifyNoInteractions(fillRepository, historyRepository);

                verify(orderRepository, never()).save(any());

        }

        @Test

        void shouldPreventDuplicateExecutionOfFilledOrder() {

                order.setStatus(OrderStatus.FILLED);

                when(orderRepository.findByIdForUpdate(55L))

                                .thenReturn(Optional.of(order));

                OrderExecutionService.OrderExecutionException exception = assertThrows(

                                OrderExecutionService.OrderExecutionException.class,

                                () -> service.executeOrder(55L));

                assertEquals(

                                "Order is not in ACCEPTED state",

                                exception.getMessage());

                assertEquals(OrderStatus.FILLED, order.getStatus());

                verifyNoInteractions(

                                quoteRepository,

                                accountRepository,

                                positionRepository,

                                cashTransactionRepository,

                                fillRepository,

                                historyRepository);

                verify(orderRepository, never()).save(any());

        }

        @Test
        void shouldReturnExistingFillWhenSameIdempotencyKeyIsRetried() {

                Fill existingFill = mock(Fill.class);

                when(existingFill.getOrder())
                                .thenReturn(order);

                when(fillRepository.findByIdempotencyKey(
                                "retry-key-55"))
                                .thenReturn(Optional.of(existingFill));

                Fill result = service.executeOrder(
                                55L,
                                "retry-key-55");

                assertSame(
                                existingFill,
                                result);

                verify(fillRepository)
                                .findByIdempotencyKey(
                                                "retry-key-55");

                verifyNoInteractions(
                                orderRepository,
                                quoteRepository,
                                accountRepository,
                                positionRepository,
                                cashTransactionRepository,
                                historyRepository,
                                positionHistoryRepository);
        }

        @Test
        void shouldRejectIdempotencyKeyUsedByAnotherOrder() {

                Order otherOrder = mock(Order.class);

                when(otherOrder.getId())
                                .thenReturn(999L);

                Fill existingFill = mock(Fill.class);

                when(existingFill.getOrder())
                                .thenReturn(otherOrder);

                when(fillRepository.findByIdempotencyKey(
                                "already-used-key"))
                                .thenReturn(Optional.of(existingFill));

                OrderExecutionService.OrderExecutionException exception = assertThrows(
                                OrderExecutionService.OrderExecutionException.class,
                                () -> service.executeOrder(
                                                55L,
                                                "already-used-key"));

                assertEquals(
                                "Idempotency key has already been used for a different order",
                                exception.getMessage());

                verify(fillRepository)
                                .findByIdempotencyKey(
                                                "already-used-key");

                verifyNoInteractions(
                                orderRepository,
                                quoteRepository,
                                accountRepository,
                                positionRepository,
                                cashTransactionRepository,
                                historyRepository,
                                positionHistoryRepository);
        }

}
