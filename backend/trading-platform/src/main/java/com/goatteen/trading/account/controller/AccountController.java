package com.goatteen.trading.account.controller;

import com.goatteen.trading.account.Account;
import com.goatteen.trading.account.AccountRepository;
import com.goatteen.trading.execution.Fill;
import com.goatteen.trading.execution.FillRepository;
import com.goatteen.trading.order.Order;
import com.goatteen.trading.order.OrderRepository;
import com.goatteen.trading.order.OrderStatus;
import com.goatteen.trading.order.dto.OrderResponse;
import com.goatteen.trading.portfolio.Position;
import com.goatteen.trading.portfolio.PositionRepository;
import com.goatteen.trading.portfolio.dto.HoldingsResponse;
import com.goatteen.trading.portfolio.dto.PositionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final OrderRepository orderRepository;
    private final FillRepository fillRepository;

    public AccountController(AccountRepository accountRepository, PositionRepository positionRepository,
                            OrderRepository orderRepository, FillRepository fillRepository) {
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.orderRepository = orderRepository;
        this.fillRepository = fillRepository;
    }

    @GetMapping("/{accountId}/holdings")
    public ResponseEntity<HoldingsResponse> getHoldings(@PathVariable Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElse(null);

        if (account == null) {
            return ResponseEntity.notFound().build();
        }

        // BR-10: client views current holdings and cash balance
        List<Position> positions = positionRepository.findByAccountId(accountId);
        List<PositionResponse> positionResponses = positions.stream()
                .map(pos -> new PositionResponse(
                        pos.getInstrument().getId(),
                        pos.getInstrument().getSymbol(),
                        pos.getInstrument().getName(),
                        pos.getQuantity()
                ))
                .collect(Collectors.toList());

        HoldingsResponse holdings = new HoldingsResponse(
                accountId,
                account.getCashBalance(),
                account.getCurrency(),
                positionResponses
        );

        return ResponseEntity.ok(holdings);
    }

    @GetMapping("/{accountId}/blotter")
    public ResponseEntity<?> getBlotter(@PathVariable Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElse(null);

        if (account == null) {
            return ResponseEntity.notFound().build();
        }

        // BR-11: client views chronological history of orders and fills
        List<Order> orders = orderRepository.findByAccountIdOrderBySubmittedAtDesc(accountId);
        List<OrderResponse> orderResponses = orders.stream()
                .map(this::toOrderResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(orderResponses);
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

        if (order.getStatus() == OrderStatus.FILLED) {
            Fill fill = fillRepository.findByOrderId(order.getId()).orElse(null);
            if (fill != null) {
                response.setFillPrice(fill.getFillPrice());
            }
        }

        return response;
    }
}
