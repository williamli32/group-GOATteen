
package com.goatteen.trading.reporting;

import com.goatteen.trading.reporting.controller.ReportingController;
import com.goatteen.trading.reporting.metrics.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.access.intercept.aopalliance.MethodSecurityInterceptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportingControllerTest {

    private TradingActivityMetricsService tradingService;
    private InstrumentMetricsService instrumentService;
    private ClientMetricsService clientService;
    private PeriodicReportingService periodicService;

    private ReportingController controller;

    @BeforeEach
    void setUp() {

        tradingService = mock(TradingActivityMetricsService.class);
        instrumentService = mock(InstrumentMetricsService.class);
        clientService = mock(ClientMetricsService.class);
        periodicService = mock(PeriodicReportingService.class);

        controller = new ReportingController(
                tradingService,
                instrumentService,
                clientService,
                periodicService
        );
    }

    @Test
    void tradingEndpointDelegatesToMetricsService() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 1, 0, 0);

        controller.trading(start, end);

        verify(tradingService).calculate(start, end);
    }

    @Test
    void instrumentEndpointDelegatesToMetricsService() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 1, 0, 0);

        controller.instruments(start, end);

        verify(instrumentService).calculate(start, end);
    }

    @Test
    void clientEndpointDelegatesToMetricsService() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 1, 0, 0);

        controller.clients(start, end);

        verify(clientService).calculate(start, end);
    }

    @Test
    void periodicEndpointDelegatesToReportingService() {
        LocalDate date = LocalDate.of(2026, 10, 12);

        controller.periodic(ReportingPeriod.DAILY, date);

        verify(periodicService).generate(
                ReportingPeriod.DAILY, date);
    }

    @Test
    void reportingControllerRequiresAdminOrAnalyst() throws Exception {
        var annotation = ReportingController.class
                .getAnnotation(
                        org.springframework.security.access.prepost.PreAuthorize.class);

        assertNotNull(annotation);
        assertEquals(
                "hasAnyRole('ADMIN', 'ANALYST')",
                annotation.value()
        );
    }
}
