package com.goatteen.trading.audit;

import com.goatteen.trading.account.Account;
import com.goatteen.trading.account.AccountOwnershipService;
import com.goatteen.trading.auth.service.CurrentUserService;
import com.goatteen.trading.order.Order;
import com.goatteen.trading.order.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class TradeAuditController {

    private final OrderRepository orderRepository;
    private final TradeReconstructionService tradeReconstructionService;
    private final CurrentUserService currentUserService;
    private final AccountOwnershipService accountOwnershipService;

    public TradeAuditController(
            OrderRepository orderRepository,
            TradeReconstructionService tradeReconstructionService,
            CurrentUserService currentUserService,
            AccountOwnershipService accountOwnershipService) {

        this.orderRepository = orderRepository;
        this.tradeReconstructionService = tradeReconstructionService;
        this.currentUserService = currentUserService;
        this.accountOwnershipService = accountOwnershipService;
    }

    /**
     * GET /api/orders/{orderId}/audit
     *
     * Reconstructs the complete persisted audit trail for an order.
     */
    @GetMapping("/{orderId}/audit")
    public ResponseEntity<?> getTradeAudit(
            @PathVariable Long orderId) {

        Long userId = currentUserService.getCurrentUserId();

        Account account = accountOwnershipService
                .getCurrentUserAccount(userId);

        /*
         * Important:
         * resolve the order through account_id so a user cannot
         * reconstruct another user's order.
         */
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

        try {

            TradeAuditResponse response = tradeReconstructionService
                    .reconstructTradeResponse(orderId);

            return ResponseEntity.ok(response);

        } catch (TradeReconstructionService.TradeReconstructionException e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.getMessage());
        }
    }
}