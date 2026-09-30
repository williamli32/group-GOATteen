package com.goatteen.trading.execution;

import com.goatteen.trading.account.Account;
import com.goatteen.trading.account.AccountRepository;
import com.goatteen.trading.audit.OrderStatusHistory;
import com.goatteen.trading.audit.OrderStatusHistoryRepository;
import com.goatteen.trading.audit.PositionHistoryRepository;
import com.goatteen.trading.instrument.Instrument;
import com.goatteen.trading.instrument.InstrumentClass;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for idempotent order execution protection.
 * 
 * Ensures that:
 * - Duplicate fill detection prevents multiple fills per order
 * - Pessimistic locking is used to prevent concurrent execution
 * - IdempotencyService tracks fills correctly
 */
@ExtendWith(MockitoExtension.class)
class OrderExecutionIdempotencyTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private FillRepository fillRepository;

    @Mock
    private QuoteRepository quoteRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private CashTransactionRepository cashTransactionRepository;

    @Mock
    private OrderStatusHistoryRepository historyRepository;

    @Mock
    private PositionHistoryRepository positionHistoryRepository;

    private IdempotencyService idempotencyService;

    private OrderExecutionService service;

    private Account account;
    private Order order;
    private Quote quote;
    private Instrument instrument;

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService();

        service = new OrderExecutionService(
                orderRepository,
                fillRepository,
                quoteRepository,
                accountRepository,
                positionRepository,
                cashTransactionRepository,
                historyRepository,
                positionHistoryRepository,
                idempotencyService);

        // Setup test account
        account = new Account(null, "LEAP-TEST123", "GBP");
        ReflectionTestUtils.setField(account, "id", 10L);
        account.setCashBalance(new BigDecimal("5000.00"));

        // Setup test instrument using public constructor
        instrument = new Instrument(
                "AAPL",
                "Apple Inc",
                InstrumentClass.EQUITY,
                "NASDAQ",
                "US",
                "GBP");
        ReflectionTestUtils.setField(instrument, "id", 1L);

        // Setup test order
        order = new Order();
        ReflectionTestUtils.setField(order, "id", 55L);
        order.setAccount(account);
        order.setInstrument(instrument);
        order.setSide(OrderSide.BUY);
        order.setQuantity(new BigDecimal("10"));
        order.setStatus(OrderStatus.ACCEPTED);

        // Setup test quote using public constructor
        quote = new Quote(
                instrument,
                new BigDecimal("150.00"),
                new BigDecimal("150.25"),
                new BigDecimal("150.10"),
                LocalDateTime.now());
        ReflectionTestUtils.setField(quote, "id", 1L);
    }

    /**
     * Test: Executing the same order twice throws duplicate execution error
     * 
     * Scenario: Order is executed successfully, then execution is attempted again.
     * Expected: Second execution fails with OrderExecutionException mentioning duplicate.
     */
    @Test
    void testExecuteOrderTwice_ThrowsDuplicateExecution() {
        Long orderId = 55L;

        // First execution: No existing fill
        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));
        when(fillRepository.findByOrderId(orderId))
                .thenReturn(Optional.empty());  // No fill yet
        when(quoteRepository.findTopByInstrumentIdOrderByQuotedAtDesc(1L))
                .thenReturn(Optional.of(quote));
        when(accountRepository.findById(10L))
                .thenReturn(Optional.of(account));
        when(positionRepository.findByAccountIdAndInstrumentId(10L, 1L))
                .thenReturn(Optional.empty());

        // Mock successful first execution
        when(fillRepository.save(any(Fill.class)))
                .thenAnswer(invocation -> {
                    Fill fill = invocation.getArgument(0);
                    ReflectionTestUtils.setField(fill, "id", 100L);
                    return fill;
                });
        when(cashTransactionRepository.save(any(CashTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(positionRepository.save(any(Position.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // First execution should succeed
        service.executeOrder(orderId);
        verify(fillRepository).save(any(Fill.class));

        // Reset mocks for second attempt
        reset(orderRepository, fillRepository);

        // Second execution: Fill already exists
        Fill existingFill = new Fill();
        ReflectionTestUtils.setField(existingFill, "id", 100L);
        existingFill.setOrder(order);

        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));
        when(fillRepository.findByOrderId(orderId))
                .thenReturn(Optional.of(existingFill));  // Fill already exists!

        // Second execution should throw
        OrderExecutionService.OrderExecutionException exception = assertThrows(
                OrderExecutionService.OrderExecutionException.class,
                () -> service.executeOrder(orderId)
        );

        assertTrue(exception.getMessage().contains("Duplicate execution detected"));
        verify(fillRepository, never()).save(any(Fill.class));  // No new fill created
    }

    /**
     * Test: Pessimistic locking is used in acceptOrder
     * 
     * Ensures findByIdForUpdate() is called (which applies pessimistic lock)
     * instead of findById() (no lock).
     */
    @Test
    void testAcceptOrder_UsesPessimisticLocking() {
        Long orderId = 55L;
        order.setStatus(OrderStatus.SUBMITTED);

        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class)))
                .thenReturn(order);

        service.acceptOrder(orderId);

        // Verify pessimistic locking was used
        verify(orderRepository).findByIdForUpdate(orderId);
        // Ensure non-locking findById was NOT called
        verify(orderRepository, never()).findById(orderId);
    }

    /**
     * Test: Pessimistic locking is used in executeOrder
     * 
     * Verifies that findByIdForUpdate() is called for order retrieval.
     */
    @Test
    void testExecuteOrder_UsesPessimisticLocking() {
        Long orderId = 55L;

        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));
        when(fillRepository.findByOrderId(orderId))
                .thenReturn(Optional.empty());
        when(quoteRepository.findTopByInstrumentIdOrderByQuotedAtDesc(1L))
                .thenReturn(Optional.of(quote));
        when(accountRepository.findById(10L))
                .thenReturn(Optional.of(account));
        when(positionRepository.findByAccountIdAndInstrumentId(10L, 1L))
                .thenReturn(Optional.empty());
        when(fillRepository.save(any()))
                .thenAnswer(inv -> {
                    Fill f = inv.getArgument(0);
                    ReflectionTestUtils.setField(f, "id", 100L);
                    return f;
                });
        when(cashTransactionRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(positionRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        service.executeOrder(orderId);

        // Verify pessimistic write lock was acquired
        verify(orderRepository).findByIdForUpdate(orderId);
    }

    /**
     * Test: Pessimistic locking is used in rejectOrder
     * 
     * Ensures findByIdForUpdate() is called to apply pessimistic lock.
     */
    @Test
    void testRejectOrder_UsesPessimisticLocking() {
        Long orderId = 55L;
        order.setStatus(OrderStatus.SUBMITTED);

        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class)))
                .thenReturn(order);

        service.rejectOrder(orderId, "Insufficient funds");

        // Verify pessimistic locking was used
        verify(orderRepository).findByIdForUpdate(orderId);
        verify(orderRepository, never()).findById(orderId);
    }

    /**
     * Test: IdempotencyService records and retrieves fills
     * 
     * Verifies the in-memory tracking of fill executions.
     */
    @Test
    void testIdempotencyService_RecordsAndRetrieves() {
        String idempotencyKey = "req-uuid-12345";
        Long fillId = 100L;

        // Initially no record
        assertNull(idempotencyService.getFillIdByIdempotencyKey(idempotencyKey));

        // Record execution
        idempotencyService.recordFillExecution(idempotencyKey, fillId);

        // Should retrieve same fill ID
        assertEquals(fillId, idempotencyService.getFillIdByIdempotencyKey(idempotencyKey));
    }

    /**
     * Test: IdempotencyService handles null keys gracefully
     * 
     * Ensures service doesn't crash on null or blank idempotency keys.
     */
    @Test
    void testIdempotencyService_HandlesNullKey() {
        // Should not throw
        assertNull(idempotencyService.getFillIdByIdempotencyKey(null));
        assertNull(idempotencyService.getFillIdByIdempotencyKey(""));

        // Recording with null key should not cause issues
        idempotencyService.recordFillExecution(null, 100L);
        idempotencyService.recordFillExecution("", 100L);

        // Tracking count should still be 0
        assertEquals(0, idempotencyService.getTrackedKeyCount());
    }

    /**
     * Test: IdempotencyService tracks multiple fills
     * 
     * Verifies that multiple independent order executions are tracked.
     */
    @Test
    void testIdempotencyService_TracksMultipleFills() {
        // Record 3 different fills
        idempotencyService.recordFillExecution("key-1", 100L);
        idempotencyService.recordFillExecution("key-2", 101L);
        idempotencyService.recordFillExecution("key-3", 102L);

        // Verify all are tracked
        assertEquals(100L, idempotencyService.getFillIdByIdempotencyKey("key-1"));
        assertEquals(101L, idempotencyService.getFillIdByIdempotencyKey("key-2"));
        assertEquals(102L, idempotencyService.getFillIdByIdempotencyKey("key-3"));
        assertEquals(3, idempotencyService.getTrackedKeyCount());
    }

    /**
     * Test: Fill idempotency key field is transient
     * 
     * Verifies that the idempotency key is not persisted to database.
     */
    @Test
    void testFill_IdempotencyKeyIsTransient() {
        Fill fill = new Fill();
        String key = "test-key";

        fill.setIdempotencyKey(key);

        assertEquals(key, fill.getIdempotencyKey());

        // The field should exist but not be mapped to database column
        // (verified by reflection - @Transient annotation)
        try {
            var field = Fill.class.getDeclaredField("idempotencyKey");
            assertTrue(field.isAnnotationPresent(jakarta.persistence.Transient.class),
                    "idempotencyKey should be @Transient");
        } catch (NoSuchFieldException e) {
            fail("idempotencyKey field should exist in Fill class");
        }
    }

    /**
     * Test: Order in terminal state cannot be executed
     * 
     * If order is already FILLED, attempting execution should fail immediately.
     */
    @Test
    void testExecuteOrder_TerminalOrderRejected() {
        Long orderId = 55L;
        order.setStatus(OrderStatus.FILLED);  // Terminal state

        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        // Execution should fail for terminal order
        OrderExecutionService.OrderExecutionException exception = assertThrows(
                OrderExecutionService.OrderExecutionException.class,
                () -> service.executeOrder(orderId)
        );

        // Should fail on status check, before checking for existing fill
        assertTrue(exception.getMessage().contains("ACCEPTED"));
    }
}
