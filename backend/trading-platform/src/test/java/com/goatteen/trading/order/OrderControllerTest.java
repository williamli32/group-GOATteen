package com.goatteen.trading.order.controller;

import com.goatteen.trading.account.Account;
import com.goatteen.trading.account.AccountOwnershipService;
import com.goatteen.trading.auth.service.CurrentUserService;
import com.goatteen.trading.execution.FillRepository;
import com.goatteen.trading.execution.OrderExecutionService;
import com.goatteen.trading.instrument.Instrument;
import com.goatteen.trading.instrument.InstrumentRepository;
import com.goatteen.trading.order.Order;
import com.goatteen.trading.order.OrderRepository;
import com.goatteen.trading.order.OrderSide;
import com.goatteen.trading.order.OrderStatus;
import com.goatteen.trading.order.OrderValidationService;
import com.goatteen.trading.order.dto.OrderResponse;
import com.goatteen.trading.order.dto.PlaceOrderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private FillRepository fillRepository;

    @Mock
    private OrderValidationService validationService;

    @Mock
    private OrderExecutionService executionService;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private AccountOwnershipService accountOwnershipService;

    @Mock
    private Account account;

    @Mock
    private Instrument instrument;

    private OrderController controller;

    @BeforeEach
    void setUp() {

        controller = new OrderController(
                orderRepository,
                instrumentRepository,
                fillRepository,
                validationService,
                executionService,
                currentUserService,
                accountOwnershipService);

    }

    @Test
    void shouldAcceptValidOrderWithoutExecutingIt()
            throws Exception {

        BigDecimal quantity = new BigDecimal("2");

        PlaceOrderRequest request = new PlaceOrderRequest(
                1L,
                OrderSide.BUY,
                quantity);

        when(currentUserService.getCurrentUserId())
                .thenReturn(7L);

        when(
                accountOwnershipService
                        .getCurrentUserAccount(7L))
                .thenReturn(account);

        when(
                instrumentRepository
                        .findById(1L))
                .thenReturn(
                        Optional.of(instrument));

        when(
                validationService.validate(
                        account,
                        instrument,
                        OrderSide.BUY,
                        quantity))
                .thenReturn(
                        OrderValidationService.ValidationResult
                                .accepted());

        /*
         * Simulate the database-generated ID that
         * submitOrder() would assign to the managed entity.
         */
        doAnswer(invocation -> {

            Order submitted = invocation.getArgument(0);

            ReflectionTestUtils.setField(
                    submitted,
                    "id",
                    55L);

            return null;

        })
                .when(executionService)
                .submitOrder(any(Order.class));

        Order acceptedOrder = mock(Order.class);

        when(acceptedOrder.getId())
                .thenReturn(55L);

        when(acceptedOrder.getSide())
                .thenReturn(OrderSide.BUY);

        when(acceptedOrder.getQuantity())
                .thenReturn(quantity);

        when(acceptedOrder.getStatus())
                .thenReturn(OrderStatus.ACCEPTED);

        when(acceptedOrder.getSubmittedAt())
                .thenReturn(
                        LocalDateTime.of(
                                2026,
                                9,
                                15,
                                14,
                                0));

        when(
                orderRepository.findById(55L))
                .thenReturn(
                        Optional.of(acceptedOrder));

        ResponseEntity<?> response = controller.placeOrder(request);

        assertEquals(
                201,
                response.getStatusCode().value());

        assertInstanceOf(
                OrderResponse.class,
                response.getBody());

        OrderResponse body = (OrderResponse) response.getBody();

        assertNotNull(body);

        assertEquals(
                OrderStatus.ACCEPTED,
                body.getStatus());

        assertEquals(
                55L,
                body.getId());

        verify(executionService)
                .submitOrder(any(Order.class));

        verify(executionService)
                .acceptOrder(55L);

        /*
         * Sprint 3 must never execute or settle
         * the accepted order.
         */
        verify(
                executionService,
                never())
                .executeOrder(anyLong());

        verify(
                executionService,
                never())
                .rejectOrder(
                        anyLong(),
                        any());
    }

    @Test
    void shouldPersistRejectedOrderWithoutExecutingIt()
            throws Exception {

        BigDecimal quantity = BigDecimal.ONE;

        String rejectionReason = "Currency conversion is not supported yet";

        PlaceOrderRequest request = new PlaceOrderRequest(
                1L,
                OrderSide.BUY,
                quantity);

        when(currentUserService.getCurrentUserId())
                .thenReturn(7L);

        when(
                accountOwnershipService
                        .getCurrentUserAccount(7L))
                .thenReturn(account);

        when(
                instrumentRepository
                        .findById(1L))
                .thenReturn(
                        Optional.of(instrument));

        when(
                validationService.validate(
                        account,
                        instrument,
                        OrderSide.BUY,
                        quantity))
                .thenReturn(
                        OrderValidationService.ValidationResult
                                .rejected(
                                        rejectionReason));

        doAnswer(invocation -> {

            Order submitted = invocation.getArgument(0);

            ReflectionTestUtils.setField(
                    submitted,
                    "id",
                    56L);

            return null;

        })
                .when(executionService)
                .submitOrder(any(Order.class));

        Order rejectedOrder = mock(Order.class);

        when(rejectedOrder.getId())
                .thenReturn(56L);

        when(rejectedOrder.getSide())
                .thenReturn(OrderSide.BUY);

        when(rejectedOrder.getQuantity())
                .thenReturn(quantity);

        when(rejectedOrder.getStatus())
                .thenReturn(OrderStatus.REJECTED);

        when(rejectedOrder.getRejectionReason())
                .thenReturn(rejectionReason);

        when(rejectedOrder.getSubmittedAt())
                .thenReturn(
                        LocalDateTime.of(
                                2026,
                                9,
                                15,
                                14,
                                0));

        when(
                orderRepository.findById(56L))
                .thenReturn(
                        Optional.of(rejectedOrder));

        ResponseEntity<?> response = controller.placeOrder(request);

        assertEquals(
                400,
                response.getStatusCode().value());

        assertInstanceOf(
                OrderResponse.class,
                response.getBody());

        OrderResponse body = (OrderResponse) response.getBody();

        assertNotNull(body);

        assertEquals(
                OrderStatus.REJECTED,
                body.getStatus());

        assertEquals(
                rejectionReason,
                body.getRejectionReason());

        verify(executionService)
                .rejectOrder(
                        56L,
                        rejectionReason);

        verify(
                executionService,
                never())
                .acceptOrder(anyLong());

        verify(
                executionService,
                never())
                .executeOrder(anyLong());
    }

    @Test
    void shouldReadOrderOnlyThroughAuthenticatedAccount() {

        when(currentUserService.getCurrentUserId())
                .thenReturn(7L);

        when(
                accountOwnershipService
                        .getCurrentUserAccount(7L))
                .thenReturn(account);

        when(account.getId())
                .thenReturn(10L);

        Order order = mock(Order.class);

        when(order.getId())
                .thenReturn(99L);

        when(order.getSide())
                .thenReturn(OrderSide.BUY);

        when(order.getQuantity())
                .thenReturn(BigDecimal.ONE);

        when(order.getStatus())
                .thenReturn(OrderStatus.ACCEPTED);

        when(
                orderRepository
                        .findByIdAndAccountId(
                                99L,
                                10L))
                .thenReturn(
                        Optional.of(order));

        ResponseEntity<OrderResponse> response = controller.getOrder(99L);

        assertEquals(
                200,
                response.getStatusCode().value());

        assertNotNull(
                response.getBody());

        verify(orderRepository)
                .findByIdAndAccountId(
                        99L,
                        10L);
    }

    @Test
    void shouldReturnNotFoundForOrderOutsideAuthenticatedAccount() {

        when(currentUserService.getCurrentUserId())
                .thenReturn(7L);

        when(
                accountOwnershipService
                        .getCurrentUserAccount(7L))
                .thenReturn(account);

        when(account.getId())
                .thenReturn(10L);

        when(
                orderRepository
                        .findByIdAndAccountId(
                                99L,
                                10L))
                .thenReturn(
                        Optional.empty());

        ResponseEntity<OrderResponse> response = controller.getOrder(99L);

        assertEquals(
                404,
                response.getStatusCode().value());

        assertNull(
                response.getBody());
    }

}