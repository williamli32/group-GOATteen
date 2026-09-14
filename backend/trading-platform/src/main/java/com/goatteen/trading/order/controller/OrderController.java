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
    public ResponseEntity<?> placeOrder(@RequestBody PlaceOrderRequest request) {
        try {
            Long userId = currentUserService.getCurrentUserId();

            Account account = accountOwnershipService
                    .getCurrentUserAccount(userId);

            Instrument instrument = instrumentRepository.findById(request.getInstrumentId())
                    .orElseThrow(() -> new IllegalArgumentException("Instrument not found"));

            // Validate order (BR-05)
            ValidationResult validation = validationService.validate(account, instrument, request.getSide(),
                    request.getQuantity());
            if (!validation.isAccepted()) {
                return ResponseEntity.badRequest().body("Order rejected: " + validation.getRejectionReason());
            }

            // Create and submit order (BR-04, BR-06)
            Order order = new Order();
            order.setAccount(account);
            order.setInstrument(instrument);
            order.setSide(request.getSide());
            order.setQuantity(request.getQuantity());
            order.setStatus(com.goatteen.trading.order.OrderStatus.SUBMITTED);

            executionService.submitOrder(order);

            // Auto-execute order (simplified: in production, this might be async)
            Order saved = orderRepository.findById(order.getId()).orElseThrow();
            try {
                executionService.executeOrder(saved.getId());
            } catch (OrderExecutionService.OrderExecutionException e) {
                try {
                    executionService.rejectOrder(saved.getId(), e.getMessage());
                } catch (OrderExecutionService.OrderExecutionException ex) {
                    // Ignore rejection errors for now
                }
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Order rejected: " + e.getMessage());
            }

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(toOrderResponse(orderRepository.findById(saved.getId()).orElseThrow()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
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
