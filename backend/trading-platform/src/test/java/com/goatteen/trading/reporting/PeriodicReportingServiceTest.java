
package com.goatteen.trading.reporting;

import com.goatteen.trading.reporting.metrics.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PeriodicReportingServiceTest {

    @Mock
    private TradingActivityMetricsService tradingActivityService;

    @Mock
    private InstrumentMetricsService instrumentMetricsService;

    @Mock
    private ClientMetricsService clientMetricsService;

    private PeriodicReportingService reportingService;

    @BeforeEach
    void setUp() {
        reportingService = new PeriodicReportingService(
                tradingActivityService,
                instrumentMetricsService,
                clientMetricsService
        );
    }

    @Test
    void generatesDailyReport() {
        PeriodicReport report = reportingService.generate(
                ReportingPeriod.DAILY,
                LocalDate.of(2026, 10, 12)
        );

        assertEquals(ReportingPeriod.DAILY, report.period());
        assertEquals(LocalDateTime.of(2026, 10, 12, 0, 0),
                report.startDate());
        assertEquals(LocalDateTime.of(2026, 10, 13, 0, 0),
                report.endDate());

        verifyMetricsCalled(report.startDate(), report.endDate());
    }

    @Test
    void generatesWeeklyReportStartingMonday() {
        PeriodicReport report = reportingService.generate(
                ReportingPeriod.WEEKLY,
                LocalDate.of(2026, 10, 14)
        );

        assertEquals(LocalDateTime.of(2026, 10, 12, 0, 0),
                report.startDate());
        assertEquals(LocalDateTime.of(2026, 10, 19, 0, 0),
                report.endDate());

        verifyMetricsCalled(report.startDate(), report.endDate());
    }

    @Test
    void generatesMonthlyReport() {
        PeriodicReport report = reportingService.generate(
                ReportingPeriod.MONTHLY,
                LocalDate.of(2026, 10, 15)
        );

        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0),
                report.startDate());
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0),
                report.endDate());

        verifyMetricsCalled(report.startDate(), report.endDate());
    }

    @Test
    void handlesYearBoundary() {
        PeriodicReport report = reportingService.generate(
                ReportingPeriod.MONTHLY,
                LocalDate.of(2026, 12, 31)
        );

        assertEquals(LocalDateTime.of(2026, 12, 1, 0, 0),
                report.startDate());
        assertEquals(LocalDateTime.of(2027, 1, 1, 0, 0),
                report.endDate());
    }

    @Test
    void rejectsMissingInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> reportingService.generate(
                        null, LocalDate.of(2026, 10, 12)));

        assertThrows(IllegalArgumentException.class,
                () -> reportingService.generate(
                        ReportingPeriod.DAILY, null));

        verifyNoInteractions(
                tradingActivityService,
                instrumentMetricsService,
                clientMetricsService
        );
    }

    private void verifyMetricsCalled(
            LocalDateTime start,
            LocalDateTime end) {

        verify(tradingActivityService).calculate(start, end);
        verify(instrumentMetricsService).calculate(start, end);
        verify(clientMetricsService).calculate(start, end);
    }
}
