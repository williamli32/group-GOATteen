package com.goatteen.trading.recovery;

import com.goatteen.trading.audit.TradeIntegrityReport;
import com.goatteen.trading.audit.TradeReconstructionService;
import com.goatteen.trading.execution.Fill;
import com.goatteen.trading.execution.FillRepository;
import com.goatteen.trading.execution.OrderExecutionService;
import com.goatteen.trading.order.Order;
import com.goatteen.trading.order.OrderRepository;
import com.goatteen.trading.order.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecoveryServiceIntegrationTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private FillRepository fillRepository;

    @Mock
    private OrderExecutionService executionService;

    @Mock
    private TradeReconstructionService reconstructionService;

    private RecoveryService recoveryService() {
        return new RecoveryService(
                orderRepository,
                fillRepository,
                executionService,
                reconstructionService);
    }

    @Test
    void shouldClassifyAcceptedOrderWithoutFillAsSafeToRetry() {
        Long orderId = 100L;

        Order order = mock(Order.class);

        when(order.getStatus())
                .thenReturn(OrderStatus.ACCEPTED);

        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(order));

        when(fillRepository.findByOrderId(orderId))
                .thenReturn(Optional.empty());

        RecoveryAssessment assessment = recoveryService().assessOrder(orderId);

        assertEquals(
                RecoveryState.ACCEPTED_NOT_EXECUTED,
                assessment.state());

        assertTrue(
                assessment.safeToExecute());

        verifyNoInteractions(executionService);
    }

    @Test
    void shouldRecoverAcceptedOrderAndReassessPersistedState() {
        Long orderId = 101L;

        Order order = mock(Order.class);
        Fill fill = mock(Fill.class);

        AtomicBoolean recovered = new AtomicBoolean(false);

        when(order.getIdempotencyKey())
                .thenReturn("original-key-101");

        when(order.getStatus())
                .thenAnswer(invocation -> recovered.get()
                        ? OrderStatus.FILLED
                        : OrderStatus.ACCEPTED);

        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(order));

        when(fillRepository.findByOrderId(orderId))
                .thenAnswer(invocation -> recovered.get()
                        ? Optional.of(fill)
                        : Optional.empty());

        when(executionService.executeOrder(
                orderId,
                "original-key-101"))
                .thenAnswer(invocation -> {
                    recovered.set(true);
                    return fill;
                });

        when(reconstructionService.verifyTradeIntegrity(orderId))
                .thenReturn(new TradeIntegrityReport(
                        orderId,
                        true,
                        "Trade integrity verified"));

        RecoveryAssessment assessment = recoveryService().recoverOrder(orderId);

        assertEquals(
                RecoveryState.FULLY_EXECUTED,
                assessment.state());

        assertFalse(
                assessment.safeToExecute());

        verify(executionService, times(1))
                .executeOrder(
                        orderId,
                        "original-key-101");
    }

    @Test
    void shouldRefuseRecoveryWhenAcceptedOrderAlreadyHasFill() {
        Long orderId = 102L;

        Order order = mock(Order.class);
        Fill fill = mock(Fill.class);

        when(order.getStatus())
                .thenReturn(OrderStatus.ACCEPTED);

        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(order));

        when(fillRepository.findByOrderId(orderId))
                .thenReturn(Optional.of(fill));

        RecoveryAssessment assessment = recoveryService().recoverOrder(orderId);

        assertEquals(
                RecoveryState.AMBIGUOUS,
                assessment.state());

        assertFalse(
                assessment.safeToExecute());

        verifyNoInteractions(executionService);
    }

    @Test
    void shouldTreatValidFilledOrderAsFullyExecutedAndNeverRetry() {
        Long orderId = 103L;

        Order order = mock(Order.class);
        Fill fill = mock(Fill.class);

        when(order.getStatus())
                .thenReturn(OrderStatus.FILLED);

        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(order));

        when(fillRepository.findByOrderId(orderId))
                .thenReturn(Optional.of(fill));

        when(reconstructionService.verifyTradeIntegrity(orderId))
                .thenReturn(new TradeIntegrityReport(
                        orderId,
                        true,
                        "Trade integrity verified"));

        RecoveryAssessment assessment = recoveryService().recoverOrder(orderId);

        assertEquals(
                RecoveryState.FULLY_EXECUTED,
                assessment.state());

        assertFalse(
                assessment.safeToExecute());

        verifyNoInteractions(executionService);
    }

    @Test
    void shouldRefuseRecoveryWhenFilledOrderFailsIntegrityVerification() {
        Long orderId = 104L;

        Order order = mock(Order.class);
        Fill fill = mock(Fill.class);

        when(order.getStatus())
                .thenReturn(OrderStatus.FILLED);

        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(order));

        when(fillRepository.findByOrderId(orderId))
                .thenReturn(Optional.of(fill));

        when(reconstructionService.verifyTradeIntegrity(orderId))
                .thenReturn(new TradeIntegrityReport(
                        orderId,
                        false,
                        "Missing position history"));

        RecoveryAssessment assessment = recoveryService().recoverOrder(orderId);

        assertEquals(
                RecoveryState.AMBIGUOUS,
                assessment.state());

        assertFalse(
                assessment.safeToExecute());

        verifyNoInteractions(executionService);
    }
}