package com.goatteen.trading.order.controller;

import com.goatteen.trading.account.AccountOwnershipService;
import com.goatteen.trading.auth.service.CurrentUserService;
import com.goatteen.trading.account.Account;
import com.goatteen.trading.execution.Fill;
import com.goatteen.trading.execution.FillRepository;
import com.goatteen.trading.execution.OrderExecutionService;
import com.goatteen.trading.instrument.Instrument;
import com.goatteen.trading.instrument.InstrumentRepository;
import com.goatteen.trading.order.Order;
import com.goatteen.trading.order.OrderRepository;
import com.goatteen.trading.order.OrderValidationService;
import com.goatteen.trading.order.OrderValidationService.ValidationResult;
import com.goatteen.trading.order.dto.OrderResponse;
import com.goatteen.trading.order.dto.PlaceOrderRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final InstrumentRepository instrumentRepository;
    private final FillRepository fillRepository;
    private final OrderValidationService validationService;
    private final OrderExecutionService executionService;
    private final CurrentUserService currentUserService;
    private final AccountOwnershipService accountOwnershipService;

    public OrderController(
            OrderRepository orderRepository,
            InstrumentRepository instrumentRepository,
            FillRepository fillRepository,
            OrderValidationService validationService,
            OrderExecutionService executionService,
            CurrentUserService currentUserService,
            AccountOwnershipService accountOwnershipService) {
        this.orderRepository = orderRepository;
        this.instrumentRepository = instrumentRepository;
        this.fillRepository = fillRepository;
        this.validationService = validationService;
        this.executionService = executionService;
        this.currentUserService = currentUserService;
        this.accountOwnershipService = accountOwnershipService;
    }

    @PostMapping
    public ResponseEntity<?> placeOrder(
            @Valid @RequestBody PlaceOrderRequest request) {

        try {

            Long userId = currentUserService
                    .getCurrentUserId();

            Account account = accountOwnershipService
                    .getCurrentUserAccount(
                            userId);

            Instrument instrument = instrumentRepository
                    .findById(
                            request.getInstrumentId())
                    .orElseThrow(
                            () -> new IllegalArgumentException(
                                    "Instrument not found"));

            /*
             * Persist the firm order first.
             *
             * At this point no cash, position,
             * or fill changes occur.
             */
            Order order = new Order();

            order.setAccount(
                    account);

            order.setInstrument(
                    instrument);

            order.setSide(
                    request.getSide());

            order.setQuantity(
                    request.getQuantity());

            executionService
                    .submitOrder(order);

            /*
             * Run Sprint 3 business validation.
             */
            ValidationResult validation = validationService.validate(
                    account,
                    instrument,
                    request.getSide(),
                    request.getQuantity());

            /*
             * Business-rule failure:
             * SUBMITTED -> REJECTED
             */
            if (!validation.isAccepted()) {

                executionService
                        .rejectOrder(
                                order.getId(),
                                validation
                                        .getRejectionReason());

                Order rejectedOrder = orderRepository
                        .findById(
                                order.getId())
                        .orElseThrow();

                return ResponseEntity
                        .status(
                                HttpStatus.BAD_REQUEST)
                        .body(
                                toOrderResponse(
                                        rejectedOrder));
            }

            /*
             * Business validation succeeded:
             * SUBMITTED -> ACCEPTED
             *
             * Execution is deliberately deferred
             * to Sprint 4.
             */
            executionService
                    .acceptOrder(
                            order.getId());

            Order acceptedOrder = orderRepository
                    .findById(
                            order.getId())
                    .orElseThrow();

            return ResponseEntity
                    .status(
                            HttpStatus.CREATED)
                    .body(
                            toOrderResponse(
                                    acceptedOrder));

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .status(
                            HttpStatus.BAD_REQUEST)
                    .body(
                            e.getMessage());

        } catch (OrderExecutionService.OrderExecutionException e) {

            return ResponseEntity
                    .status(
                            HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                            e.getMessage());
        }
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable Long orderId) {

        Long userId = currentUserService
                .getCurrentUserId();

        Account account = accountOwnershipService
                .getCurrentUserAccount(userId);

        Order order = orderRepository
                .findByIdAndAccountId(
                        orderId,
                        account.getId())
                .orElse(null);

        if (order == null) {
            return ResponseEntity
                    .notFound()
                    .build();
        }

        return ResponseEntity.ok(
                toOrderResponse(order));
    }

    private OrderResponse toOrderResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setSide(order.getSide());
        response.setQuantity(order.getQuantity());
        response.setStatus(order.getStatus());
        response.setRejectionReason(order.getRejectionReason());
        response.setSubmittedAt(order.getSubmittedAt());
        response.setFilledAt(order.getCompletedAt());

        // Fetch fill price if order is filled
        if (order.getStatus() == com.goatteen.trading.order.OrderStatus.FILLED) {
            Fill fill = fillRepository.findByOrderId(order.getId()).orElse(null);
            if (fill != null) {
                response.setFillPrice(fill.getFillPrice());
            }
        }

        return response;
    }
}
