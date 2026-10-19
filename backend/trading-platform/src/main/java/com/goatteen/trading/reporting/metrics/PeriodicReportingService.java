package com.goatteen.trading.reporting.metrics;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

@Service
public class PeriodicReportingService {

    private final TradingActivityMetricsService tradingActivityService;
    private final InstrumentMetricsService instrumentMetricsService;
    private final ClientMetricsService clientMetricsService;

    public PeriodicReportingService(
            TradingActivityMetricsService tradingActivityService,
            InstrumentMetricsService instrumentMetricsService,
            ClientMetricsService clientMetricsService) {

        this.tradingActivityService = tradingActivityService;
        this.instrumentMetricsService = instrumentMetricsService;
        this.clientMetricsService = clientMetricsService;
    }

    public PeriodicReport generate(
            ReportingPeriod period,
            LocalDate date) {

        if (period == null || date == null) {
            throw new IllegalArgumentException(
                    "Period and date are required");
        }

        LocalDate start;
        LocalDate end;
        LocalDate previousStart;

        switch (period) {

            case DAILY -> {
                start = date;
                end = date.plusDays(1);
                previousStart = start.minusDays(1);
            }

            case WEEKLY -> {
                start = date.with(
                        TemporalAdjusters.previousOrSame(
                                DayOfWeek.MONDAY));

                end = start.plusWeeks(1);
                previousStart = start.minusWeeks(1);
            }

            case MONTHLY -> {
                start = date.withDayOfMonth(1);
                end = start.plusMonths(1);
                previousStart = start.minusMonths(1);
            }

            default -> throw new IllegalArgumentException(
                    "Unsupported reporting period");
        }

        LocalDateTime startDate = start.atStartOfDay();

        LocalDateTime endDate = end.atStartOfDay();

        LocalDateTime previousStartDate = previousStart.atStartOfDay();

        LocalDateTime previousEndDate = start.atStartOfDay();

        TradingActivityMetrics current = tradingActivityService.calculate(
                startDate,
                endDate);

        TradingActivityMetrics previous = tradingActivityService.calculate(
                previousStartDate,
                previousEndDate);

        PeriodComparison comparison = new PeriodComparison(
                previous,

                percentChange(
                        BigDecimal.valueOf(
                                current.totalOrders()),
                        BigDecimal.valueOf(
                                previous.totalOrders())),

                percentChange(
                        BigDecimal.valueOf(
                                current.filledOrders()),
                        BigDecimal.valueOf(
                                previous.filledOrders())),

                percentChange(
                        current.totalTradedValue(),
                        previous.totalTradedValue()));

        return new PeriodicReport(
                period,
                startDate,
                endDate,
                current,
                instrumentMetricsService.calculate(
                        startDate,
                        endDate),
                clientMetricsService.calculate(
                        startDate,
                        endDate),
                comparison);
    }

    private BigDecimal percentChange(
            BigDecimal current,
            BigDecimal previous) {
        return BusinessInsightsCalculator.percentChange(current, previous);
    }
}