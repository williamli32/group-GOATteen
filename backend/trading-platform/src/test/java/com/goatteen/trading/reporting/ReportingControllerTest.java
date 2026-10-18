
package com.goatteen.trading.reporting;

import com.goatteen.trading.reporting.controller.ReportingController;
import com.goatteen.trading.reporting.metrics.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportingControllerTest {

    private TradingActivityMetricsService tradingService;
    private InstrumentMetricsService instrumentService;
    private ClientMetricsService clientService;
    private PeriodicReportingService periodicService;
    private ActivityTrendService activityTrendService;

    private ReportingController controller;

    @BeforeEach
    void setUp() {

        tradingService = mock(TradingActivityMetricsService.class);
        instrumentService = mock(InstrumentMetricsService.class);
        clientService = mock(ClientMetricsService.class);
        periodicService = mock(PeriodicReportingService.class);
        activityTrendService = mock(ActivityTrendService.class);

        controller = new ReportingController(
                tradingService,
                instrumentService,
                clientService,
                periodicService,
                activityTrendService);
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
    void reportingControllerRequiresAdminAnalystOrOperations() {
        var annotation = ReportingController.class
                .getAnnotation(
                        org.springframework.security.access.prepost.PreAuthorize.class);

        assertNotNull(annotation);
        assertEquals(
                "hasAnyRole('ADMIN', 'ANALYST', 'OPERATIONS')",
                annotation.value());
    }

    @Test
    void trendsEndpointDelegatesToTrendService() {

        LocalDateTime start = LocalDateTime.of(
                2026, 10, 1, 0, 0);

        LocalDateTime end = LocalDateTime.of(
                2026, 11, 1, 0, 0);

        controller.trends(start, end);

        verify(activityTrendService)
                .calculate(start, end);
    }
}
