package com.goatteen.trading.order.controller;

import com.goatteen.trading.account.Account;
import com.goatteen.trading.account.AccountOwnershipService;
import com.goatteen.trading.auth.service.CurrentUserService;
import com.goatteen.trading.execution.Fill;
import com.goatteen.trading.execution.FillRepository;
import com.goatteen.trading.execution.OrderExecutionService;
import com.goatteen.trading.instrument.Instrument;
import com.goatteen.trading.instrument.InstrumentRepository;
import com.goatteen.trading.order.Order;
import com.goatteen.trading.order.OrderRepository;
import com.goatteen.trading.order.OrderSide;
import com.goatteen.trading.order.OrderStatus;
import com.goatteen.trading.order.OrderValidationService;
import com.goatteen.trading.order.OrderValidationService.ValidationResult;
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
        void shouldAcceptAndFillValidOrder() {

                BigDecimal quantity = new BigDecimal("2");
                BigDecimal fillPrice = new BigDecimal("75.10");

                PlaceOrderRequest request = new PlaceOrderRequest(
                                1L,
                                OrderSide.BUY,
                                quantity);

                when(currentUserService.getCurrentUserId())
                                .thenReturn(7L);

                when(accountOwnershipService.getCurrentUserAccount(7L))
                                .thenReturn(account);

                when(instrumentRepository.findById(1L))
                                .thenReturn(Optional.of(instrument));

                when(validationService.validate(
                                account,
                                instrument,
                                OrderSide.BUY,
                                quantity))
                                .thenReturn(OrderValidationService.ValidationResult.accepted());

                // Simulate the database-generated ID assigned by submitOrder().
                doAnswer(invocation -> {
                        Order submitted = invocation.getArgument(0);
                        ReflectionTestUtils.setField(submitted, "id", 55L);
                        return null;
                }).when(executionService).submitOrder(any(Order.class));

                // The controller retrieves the order after execution is complete.
                Order filledOrder = mock(Order.class);

                when(filledOrder.getId())
                                .thenReturn(55L);
                when(filledOrder.getSide())
                                .thenReturn(OrderSide.BUY);
                when(filledOrder.getQuantity())
                                .thenReturn(quantity);
                when(filledOrder.getStatus())
                                .thenReturn(OrderStatus.FILLED);
                when(filledOrder.getSubmittedAt())
                                .thenReturn(LocalDateTime.of(2026, 9, 15, 14, 0));

                when(orderRepository.findById(55L))
                                .thenReturn(Optional.of(filledOrder));

                Fill fill = mock(Fill.class);
                when(fill.getFillPrice())
                                .thenReturn(fillPrice);

                when(fillRepository.findByOrderId(55L))
                                .thenReturn(Optional.of(fill));

                ResponseEntity<?> response = controller.placeOrder(request, "test-idempotency-key-55");

                assertEquals(201, response.getStatusCode().value());
                assertInstanceOf(OrderResponse.class, response.getBody());

                OrderResponse body = (OrderResponse) response.getBody();

                assertNotNull(body);
                assertEquals(55L, body.getId());
                assertEquals(OrderStatus.FILLED, body.getStatus());
                assertEquals(0, fillPrice.compareTo(body.getFillPrice()));

                verify(executionService)
                                .submitOrder(any(Order.class));
                verify(executionService)
                                .acceptOrder(55L);
                verify(executionService)
                                .executeOrder(55L, "test-idempotency-key-55");
                verify(executionService, never())
                                .rejectOrder(anyLong(), any());
                verify(fillRepository)
                                .findByOrderId(55L);
        }

        @Test
        void shouldPersistRejectedOrderWithoutExecutingIt() {

                BigDecimal quantity = BigDecimal.ONE;
                String rejectionReason = "Currency conversion is not supported yet";

                PlaceOrderRequest request = new PlaceOrderRequest(
                                1L,
                                OrderSide.BUY,
                                quantity);

                when(currentUserService.getCurrentUserId())
                                .thenReturn(7L);

                when(accountOwnershipService.getCurrentUserAccount(7L))
                                .thenReturn(account);

                when(instrumentRepository.findById(1L))
                                .thenReturn(Optional.of(instrument));

                when(validationService.validate(
                                account,
                                instrument,
                                OrderSide.BUY,
                                quantity))
                                .thenReturn(
                                                OrderValidationService.ValidationResult.rejected(
                                                                rejectionReason));

                doAnswer(invocation -> {
                        Order submitted = invocation.getArgument(0);
                        ReflectionTestUtils.setField(submitted, "id", 56L);
                        return null;
                }).when(executionService).submitOrder(any(Order.class));

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
                                .thenReturn(LocalDateTime.of(2026, 9, 15, 14, 0));

                when(orderRepository.findById(56L))
                                .thenReturn(Optional.of(rejectedOrder));

                ResponseEntity<?> response = controller.placeOrder(request, "test-idempotency-key-56");

                assertEquals(400, response.getStatusCode().value());
                assertInstanceOf(OrderResponse.class, response.getBody());

                OrderResponse body = (OrderResponse) response.getBody();

                assertNotNull(body);
                assertEquals(56L, body.getId());
                assertEquals(OrderStatus.REJECTED, body.getStatus());
                assertEquals(rejectionReason, body.getRejectionReason());

                verify(executionService)
                                .submitOrder(any(Order.class));
                verify(executionService)
                                .rejectOrder(56L, rejectionReason);
                verify(executionService, never())
                                .acceptOrder(anyLong());
                verify(executionService, never())
                                .executeOrder(anyLong());
        }

        @Test
        void shouldReadOrderOnlyThroughAuthenticatedAccount() {

                when(currentUserService.getCurrentUserId())
                                .thenReturn(7L);

                when(accountOwnershipService.getCurrentUserAccount(7L))
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

                when(orderRepository.findByIdAndAccountId(99L, 10L))
                                .thenReturn(Optional.of(order));

                ResponseEntity<OrderResponse> response = controller.getOrder(99L);

                assertEquals(200, response.getStatusCode().value());
                assertNotNull(response.getBody());

                verify(orderRepository)
                                .findByIdAndAccountId(99L, 10L);
        }

        @Test
        void shouldReturnNotFoundForOrderOutsideAuthenticatedAccount() {

                when(currentUserService.getCurrentUserId())
                                .thenReturn(7L);

                when(accountOwnershipService.getCurrentUserAccount(7L))
                                .thenReturn(account);

                when(account.getId())
                                .thenReturn(10L);

                when(orderRepository.findByIdAndAccountId(99L, 10L))
                                .thenReturn(Optional.empty());

                ResponseEntity<OrderResponse> response = controller.getOrder(99L);

                assertEquals(404, response.getStatusCode().value());
                assertNull(response.getBody());

                verify(orderRepository)
                                .findByIdAndAccountId(99L, 10L);
        }

        @Test
        void shouldReturnExistingFilledOrderForSameIdempotencyKey() {

                BigDecimal quantity = new BigDecimal("2");

                PlaceOrderRequest request = new PlaceOrderRequest(
                                1L,
                                OrderSide.BUY,
                                quantity);

                when(currentUserService.getCurrentUserId())
                                .thenReturn(7L);

                when(accountOwnershipService
                                .getCurrentUserAccount(7L))
                                .thenReturn(account);

                when(account.getId())
                                .thenReturn(10L);

                when(instrumentRepository.findById(1L))
                                .thenReturn(Optional.of(instrument));

                when(instrument.getId())
                                .thenReturn(1L);

                Order existingOrder = mock(Order.class);

                when(existingOrder.getId())
                                .thenReturn(55L);

                when(existingOrder.getAccount())
                                .thenReturn(account);

                when(existingOrder.getInstrument())
                                .thenReturn(instrument);

                when(existingOrder.getSide())
                                .thenReturn(OrderSide.BUY);

                when(existingOrder.getQuantity())
                                .thenReturn(quantity);

                when(existingOrder.getStatus())
                                .thenReturn(OrderStatus.FILLED);

                when(existingOrder.getSubmittedAt())
                                .thenReturn(
                                                LocalDateTime.of(
                                                                2026,
                                                                10,
                                                                1,
                                                                14,
                                                                0));

                when(orderRepository.findByIdempotencyKey(
                                "retry-key-55"))
                                .thenReturn(
                                                Optional.of(existingOrder));

                Fill existingFill = mock(Fill.class);

                when(existingFill.getFillPrice())
                                .thenReturn(
                                                new BigDecimal("75.10"));

                when(fillRepository.findByOrderId(55L))
                                .thenReturn(
                                                Optional.of(existingFill));

                ResponseEntity<?> response = controller.placeOrder(
                                request,
                                "retry-key-55");

                assertEquals(
                                201,
                                response.getStatusCode().value());

                assertInstanceOf(
                                OrderResponse.class,
                                response.getBody());

                OrderResponse body = (OrderResponse) response.getBody();

                assertEquals(
                                55L,
                                body.getId());

                assertEquals(
                                OrderStatus.FILLED,
                                body.getStatus());

                verify(orderRepository)
                                .findByIdempotencyKey(
                                                "retry-key-55");

                verify(executionService,
                                never())
                                .submitOrder(any());

                verify(executionService,
                                never())
                                .acceptOrder(anyLong());

                verify(executionService,
                                never())
                                .executeOrder(
                                                anyLong(),
                                                anyString());

                verify(validationService,
                                never())
                                .validate(
                                                any(),
                                                any(),
                                                any(),
                                                any());
        }

        @Test
        void shouldRejectSameIdempotencyKeyWithDifferentParameters() {

                PlaceOrderRequest originalRequest = new PlaceOrderRequest(
                                1L,
                                OrderSide.BUY,
                                new BigDecimal("2"));

                when(currentUserService.getCurrentUserId())
                                .thenReturn(7L);

                when(accountOwnershipService
                                .getCurrentUserAccount(7L))
                                .thenReturn(account);

                when(account.getId())
                                .thenReturn(10L);

                when(instrumentRepository.findById(1L))
                                .thenReturn(Optional.of(instrument));

                when(instrument.getId())
                                .thenReturn(1L);

                Order existingOrder = mock(Order.class);

                when(existingOrder.getAccount())
                                .thenReturn(account);

                when(existingOrder.getInstrument())
                                .thenReturn(instrument);

                when(existingOrder.getSide())
                                .thenReturn(OrderSide.BUY);

                when(existingOrder.getQuantity())
                                .thenReturn(
                                                new BigDecimal("2"));

                when(orderRepository.findByIdempotencyKey(
                                "already-used-key"))
                                .thenReturn(
                                                Optional.of(existingOrder));

                /*
                 * Same key, but different quantity.
                 */
                PlaceOrderRequest differentRequest = new PlaceOrderRequest(
                                1L,
                                OrderSide.BUY,
                                new BigDecimal("5"));

                ResponseEntity<?> response = controller.placeOrder(
                                differentRequest,
                                "already-used-key");

                assertEquals(
                                422,
                                response.getStatusCode().value());

                assertEquals(
                                "Idempotency key was reused with different order parameters",
                                response.getBody());

                verify(executionService,
                                never())
                                .submitOrder(any());

                verify(executionService,
                                never())
                                .acceptOrder(anyLong());

                verify(executionService,
                                never())
                                .executeOrder(
                                                anyLong(),
                                                anyString());

                verify(validationService,
                                never())
                                .validate(
                                                any(),
                                                any(),
                                                any(),
                                                any());
        }

        @Test
        void shouldRejectBlankIdempotencyKey() {

                PlaceOrderRequest request = new PlaceOrderRequest(
                                1L,
                                OrderSide.BUY,
                                BigDecimal.ONE);

                ResponseEntity<?> response = controller.placeOrder(
                                request,
                                "   ");

                assertEquals(
                                400,
                                response.getStatusCode().value());

                assertEquals(
                                "Idempotency-Key header is required",
                                response.getBody());

                verifyNoInteractions(
                                currentUserService,
                                accountOwnershipService,
                                instrumentRepository,
                                validationService,
                                executionService);
        }
}
