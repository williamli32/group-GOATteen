package com.goatteen.trading.reporting;

import com.goatteen.trading.reporting.metrics.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
                                clientMetricsService);
        }

        @Test
        void generatesDailyReportWithPreviousDayComparison() {

                LocalDateTime currentStart = LocalDateTime.of(2026, 10, 12, 0, 0);

                LocalDateTime currentEnd = LocalDateTime.of(2026, 10, 13, 0, 0);

                LocalDateTime previousStart = LocalDateTime.of(2026, 10, 11, 0, 0);

                TradingActivityMetrics current = metrics(
                                100,
                                75,
                                new BigDecimal("125000"));

                TradingActivityMetrics previous = metrics(
                                80,
                                60,
                                new BigDecimal("100000"));

                when(tradingActivityService.calculate(
                                currentStart,
                                currentEnd))
                                .thenReturn(current);

                when(tradingActivityService.calculate(
                                previousStart,
                                currentStart))
                                .thenReturn(previous);

                when(instrumentMetricsService.calculate(
                                currentStart,
                                currentEnd))
                                .thenReturn(List.of());

                when(clientMetricsService.calculate(
                                currentStart,
                                currentEnd))
                                .thenReturn(List.of());

                PeriodicReport report = reportingService.generate(
                                ReportingPeriod.DAILY,
                                LocalDate.of(2026, 10, 12));

                assertEquals(
                                ReportingPeriod.DAILY,
                                report.period());

                assertEquals(
                                currentStart,
                                report.startDate());

                assertEquals(
                                currentEnd,
                                report.endDate());

                assertNotNull(report.comparison());

                assertEquals(
                                previous,
                                report.comparison()
                                                .previousPeriod());

                assertEquals(
                                new BigDecimal("25.00"),
                                report.comparison()
                                                .orderChangePercent());

                assertEquals(
                                new BigDecimal("25.00"),
                                report.comparison()
                                                .filledOrderChangePercent());

                assertEquals(
                                new BigDecimal("25.00"),
                                report.comparison()
                                                .tradedValueChangePercent());
        }

        @Test
        void comparesWeeklyReportToPreviousWeek() {

                stubZeroResults();

                PeriodicReport report = reportingService.generate(
                                ReportingPeriod.WEEKLY,
                                LocalDate.of(2026, 10, 14));

                assertEquals(
                                LocalDateTime.of(
                                                2026, 10, 12, 0, 0),
                                report.startDate());

                assertEquals(
                                LocalDateTime.of(
                                                2026, 10, 19, 0, 0),
                                report.endDate());

                verify(tradingActivityService)
                                .calculate(
                                                LocalDateTime.of(
                                                                2026, 10, 5, 0, 0),
                                                LocalDateTime.of(
                                                                2026, 10, 12, 0, 0));
        }

        @Test
        void comparesMonthlyReportToPreviousMonth() {

                stubZeroResults();

                PeriodicReport report = reportingService.generate(
                                ReportingPeriod.MONTHLY,
                                LocalDate.of(2026, 10, 15));

                assertEquals(
                                LocalDateTime.of(
                                                2026, 10, 1, 0, 0),
                                report.startDate());

                assertEquals(
                                LocalDateTime.of(
                                                2026, 11, 1, 0, 0),
                                report.endDate());

                verify(tradingActivityService)
                                .calculate(
                                                LocalDateTime.of(
                                                                2026, 9, 1, 0, 0),
                                                LocalDateTime.of(
                                                                2026, 10, 1, 0, 0));
        }

        @Test
        void handlesYearBoundary() {

                stubZeroResults();

                PeriodicReport report = reportingService.generate(
                                ReportingPeriod.MONTHLY,
                                LocalDate.of(2026, 12, 31));

                assertEquals(
                                LocalDateTime.of(
                                                2026, 12, 1, 0, 0),
                                report.startDate());

                assertEquals(
                                LocalDateTime.of(
                                                2027, 1, 1, 0, 0),
                                report.endDate());

                verify(tradingActivityService)
                                .calculate(
                                                LocalDateTime.of(
                                                                2026, 11, 1, 0, 0),
                                                LocalDateTime.of(
                                                                2026, 12, 1, 0, 0));
        }

        @Test
        void rejectsMissingInputs() {

                assertThrows(
                                IllegalArgumentException.class,
                                () -> reportingService.generate(
                                                null,
                                                LocalDate.of(
                                                                2026, 10, 12)));

                assertThrows(
                                IllegalArgumentException.class,
                                () -> reportingService.generate(
                                                ReportingPeriod.DAILY,
                                                null));

                verifyNoInteractions(
                                tradingActivityService,
                                instrumentMetricsService,
                                clientMetricsService);
        }

        @Test
        void reportsUndefinedChangeWhenThePriorPeriodHasNoActivity() {
                stubZeroResults();
                LocalDate date = LocalDate.of(2026, 10, 12);
                when(tradingActivityService.calculate(date.atStartOfDay(), date.plusDays(1).atStartOfDay()))
                                .thenReturn(metrics(1, 1, new BigDecimal("10.00")));
                PeriodicReport report = reportingService.generate(ReportingPeriod.DAILY, date);
                assertNull(report.comparison().orderChangePercent());
                assertNull(report.comparison().filledOrderChangePercent());
                assertNull(report.comparison().tradedValueChangePercent());
        }

        private void stubZeroResults() {

                TradingActivityMetrics zero = metrics(
                                0,
                                0,
                                BigDecimal.ZERO);

                when(tradingActivityService
                                .calculate(any(), any()))
                                .thenReturn(zero);

                when(instrumentMetricsService
                                .calculate(any(), any()))
                                .thenReturn(List.of());

                when(clientMetricsService
                                .calculate(any(), any()))
                                .thenReturn(List.of());
        }

        private TradingActivityMetrics metrics(
                        long totalOrders,
                        long filledOrders,
                        BigDecimal tradedValue) {

                return new TradingActivityMetrics(
                                totalOrders,
                                filledOrders,
                                totalOrders - filledOrders,
                                0,

                                BigDecimal.ZERO,
                                BigDecimal.ZERO,

                                BigDecimal.ZERO,
                                tradedValue,

                                0,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,

                                0,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO);
        }
}