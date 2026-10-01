package com.goatteen.trading.audit;

import com.goatteen.trading.account.Account;
import com.goatteen.trading.account.AccountOwnershipService;
import com.goatteen.trading.auth.service.CurrentUserService;
import com.goatteen.trading.order.Order;
import com.goatteen.trading.order.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeAuditControllerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TradeReconstructionService tradeReconstructionService;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private AccountOwnershipService accountOwnershipService;

    @Mock
    private Account account;

    @Mock
    private Order order;

    private TradeAuditController controller;

    @BeforeEach
    void setUp() {

        controller = new TradeAuditController(
                orderRepository,
                tradeReconstructionService,
                currentUserService,
                accountOwnershipService);
    }

    @Test
    void shouldReturnAuditForOwnedOrder()
            throws Exception {

        when(currentUserService.getCurrentUserId())
                .thenReturn(7L);

        when(accountOwnershipService
                .getCurrentUserAccount(7L))
                .thenReturn(account);

        when(account.getId())
                .thenReturn(10L);

        when(orderRepository
                .findByIdAndAccountId(55L, 10L))
                .thenReturn(Optional.of(order));

        TradeAuditResponse response = new TradeAuditResponse(
                new TradeAuditResponse.OrderDetails(
                        55L,
                        "BUY",
                        new BigDecimal("2"),
                        LocalDateTime.of(
                                2026,
                                9,
                                15,
                                14,
                                0)),

                new TradeAuditResponse.AccountDetails(
                        10L),

                new TradeAuditResponse.InstrumentDetails(
                        1L),

                "FILLED",

                List.of(
                        new TradeAuditResponse.StatusHistoryEntry(
                                1L,
                                "SUBMITTED",
                                LocalDateTime.of(
                                        2026,
                                        9,
                                        15,
                                        14,
                                        0),
                                "Order submitted"),

                        new TradeAuditResponse.StatusHistoryEntry(
                                2L,
                                "ACCEPTED",
                                LocalDateTime.of(
                                        2026,
                                        9,
                                        15,
                                        14,
                                        0,
                                        1),
                                "Order accepted"),

                        new TradeAuditResponse.StatusHistoryEntry(
                                3L,
                                "FILLED",
                                LocalDateTime.of(
                                        2026,
                                        9,
                                        15,
                                        14,
                                        0,
                                        2),
                                "Order filled")),

                null,

                LocalDateTime.of(
                        2026,
                        9,
                        15,
                        14,
                        0,
                        2),

                new TradeAuditResponse.ExecutionDetails(
                        99L,
                        new BigDecimal("2"),
                        new BigDecimal("75.10"),
                        LocalDateTime.of(
                                2026,
                                9,
                                15,
                                14,
                                0,
                                2),
                        77L,
                        new BigDecimal("74.90"),
                        new BigDecimal("75.10"),
                        LocalDateTime.of(
                                2026,
                                9,
                                15,
                                14,
                                0)),

                new TradeAuditResponse.CashImpact(
                        List.of(
                                new TradeAuditResponse.CashTransactionEntry(
                                        111L,
                                        10L,
                                        99L,
                                        new BigDecimal("-150.20"),
                                        new BigDecimal("849.80"),
                                        LocalDateTime.of(
                                                2026,
                                                9,
                                                15,
                                                14,
                                                0,
                                                2),
                                        "BUY 2 @ 75.10")),

                        new BigDecimal("-150.20"),
                        new BigDecimal("849.80")),

                new TradeAuditResponse.PositionImpact(
                        BigDecimal.ZERO,
                        new BigDecimal("2"),
                        new BigDecimal("2"),
                        LocalDateTime.of(
                                2026,
                                9,
                                15,
                                14,
                                0,
                                2)),

                true);

        when(tradeReconstructionService
                .reconstructTradeResponse(55L))
                .thenReturn(response);

        ResponseEntity<?> result = controller.getTradeAudit(55L);

        assertEquals(
                200,
                result.getStatusCode().value());

        assertSame(
                response,
                result.getBody());

        verify(orderRepository)
                .findByIdAndAccountId(55L, 10L);

        verify(tradeReconstructionService)
                .reconstructTradeResponse(55L);
    }

    @Test
    void shouldReturn404ForOrderOutsideAuthenticatedAccount() {

        when(currentUserService.getCurrentUserId())
                .thenReturn(7L);

        when(accountOwnershipService
                .getCurrentUserAccount(7L))
                .thenReturn(account);

        when(account.getId())
                .thenReturn(10L);

        when(orderRepository
                .findByIdAndAccountId(99L, 10L))
                .thenReturn(Optional.empty());

        ResponseEntity<?> result = controller.getTradeAudit(99L);

        assertEquals(
                404,
                result.getStatusCode().value());

        assertNull(result.getBody());

        verify(
                tradeReconstructionService,
                never())
                .reconstructTradeResponse(anyLong());
    }

    @Test
    void shouldReturn500WhenAuditIsIncomplete()
            throws Exception {

        when(currentUserService.getCurrentUserId())
                .thenReturn(7L);

        when(accountOwnershipService
                .getCurrentUserAccount(7L))
                .thenReturn(account);

        when(account.getId())
                .thenReturn(10L);

        when(orderRepository
                .findByIdAndAccountId(55L, 10L))
                .thenReturn(Optional.of(order));

        when(tradeReconstructionService
                .reconstructTradeResponse(55L))
                .thenThrow(
                        new TradeReconstructionService.TradeReconstructionException(
                                "Trade audit trail is incomplete: 55"));

        ResponseEntity<?> result = controller.getTradeAudit(55L);

        assertEquals(
                500,
                result.getStatusCode().value());

        assertEquals(
                "Trade audit trail is incomplete: 55",
                result.getBody());
    }
}