package com.goatteen.trading.account.controller;

import com.goatteen.trading.account.Account;
import com.goatteen.trading.account.AccountRepository;
import com.goatteen.trading.auth.service.CurrentUserService;
import com.goatteen.trading.account.AccountOwnershipService;
import com.goatteen.trading.account.dto.AccountResponse;
import com.goatteen.trading.execution.Fill;
import com.goatteen.trading.account.dto.AccountResponse;
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
    private final CurrentUserService currentUserService;
    private final AccountOwnershipService accountOwnershipService;

    public AccountController(
            AccountRepository accountRepository,
            PositionRepository positionRepository,
            OrderRepository orderRepository,
            FillRepository fillRepository,
            CurrentUserService currentUserService,
            AccountOwnershipService accountOwnershipService) {

        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.orderRepository = orderRepository;
        this.fillRepository = fillRepository;
        this.currentUserService = currentUserService;
        this.accountOwnershipService = accountOwnershipService;
    }

    @GetMapping("/me/holdings")
    public ResponseEntity<HoldingsResponse> getHoldings() {

        Long userId = currentUserService.getCurrentUserId();

        Account account = accountOwnershipService
                .getCurrentUserAccount(userId);

        Long accountId = account.getId();

        List<Position> positions = positionRepository
                .findByAccountId(accountId);

        List<PositionResponse> positionResponses = positions.stream()
                .map(pos -> new PositionResponse(
                        pos.getInstrument().getId(),
                        pos.getInstrument().getSymbol(),
                        pos.getInstrument().getName(),
                        pos.getQuantity()))
                .toList();

        HoldingsResponse holdings = new HoldingsResponse(
                accountId,
                account.getCashBalance(),
                account.getCurrency(),
                positionResponses);

        return ResponseEntity.ok(holdings);
    }

    @GetMapping("/me/blotter")
    public ResponseEntity<List<OrderResponse>> getBlotter() {

        Long userId = currentUserService.getCurrentUserId();

        Account account = accountOwnershipService
                .getCurrentUserAccount(userId);

        List<OrderResponse> orderResponses = orderRepository
                .findByAccountIdOrderBySubmittedAtDesc(
                        account.getId())
                .stream()
                .map(this::toOrderResponse)
                .toList();

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

    @GetMapping("/me")
    public ResponseEntity<AccountResponse> getMyAccount() {

        Long userId = currentUserService.getCurrentUserId();

        Account account = accountOwnershipService
                .getCurrentUserAccount(userId);

        AccountResponse response = new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getCashBalance(),
                account.getCurrency(),
                account.getClient().getFirstName(),
                account.getClient().getLastName());

        return ResponseEntity.ok(response);
    }
}
