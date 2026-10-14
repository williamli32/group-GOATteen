package com.goatteen.trading.operations;

import com.goatteen.trading.audit.TradeAuditResponse;
import com.goatteen.trading.audit.TradeIntegrityReport;
import com.goatteen.trading.audit.TradeReconstructionService;
import com.goatteen.trading.order.OrderSide;
import com.goatteen.trading.order.OrderStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/internal/operations")
@PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
public class OperationsController {

    private final OperationsOrderSearchService searchService;
    private final TradeReconstructionService reconstructionService;

    public OperationsController(
            OperationsOrderSearchService searchService,
            TradeReconstructionService reconstructionService) {
        this.searchService = searchService;
        this.reconstructionService = reconstructionService;
    }

    @GetMapping("/orders")
    public List<OperationsOrderResponse> searchOrders(
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) String clientFirstName,
            @RequestParam(required = false) String clientLastName,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String instrumentSymbol,
            @RequestParam(required = false) OrderSide side,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        return searchService.search(
                orderId,
                clientFirstName,
                clientLastName,
                accountId,
                instrumentSymbol,
                side,
                status,
                fromDate == null ? null : fromDate.atStartOfDay(),
                toDate == null ? null : toDate.plusDays(1).atStartOfDay());
    }

    @GetMapping("/orders/{orderId}/audit")
    public OperationsAuditResponse inspectOrder(@PathVariable Long orderId) {
        TradeAuditResponse evidence = reconstructionService
                .inspectAvailableTradeResponse(orderId);
        TradeIntegrityReport integrity = reconstructionService
                .verifyTradeIntegrity(orderId);

        return new OperationsAuditResponse(
                evidence,
                integrity,
                evidence.currentStatus().equals("SUBMITTED")
                        || evidence.currentStatus().equals("ACCEPTED"));
    }
}
