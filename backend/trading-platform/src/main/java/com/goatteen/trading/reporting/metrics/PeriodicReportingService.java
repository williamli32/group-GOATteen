
package com.goatteen.trading.reporting.metrics;

import org.springframework.stereotype.Service;

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

        switch (period) {
            case DAILY -> {
                start = date;
                end = date.plusDays(1);
            }
            case WEEKLY -> {
                start = date.with(
                        TemporalAdjusters.previousOrSame(
                                DayOfWeek.MONDAY));
                end = start.plusWeeks(1);
            }
            case MONTHLY -> {
                start = date.withDayOfMonth(1);
                end = start.plusMonths(1);
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported reporting period");
        }

        LocalDateTime startDate = start.atStartOfDay();
        LocalDateTime endDate = end.atStartOfDay();

        return new PeriodicReport(
                period,
                startDate,
                endDate,
                tradingActivityService.calculate(startDate, endDate),
                instrumentMetricsService.calculate(startDate, endDate),
                clientMetricsService.calculate(startDate, endDate));
    }

    private BigDecimal percentChange(
            BigDecimal current,
            BigDecimal previous) {

        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return current
                .subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(
                        previous,
                        2,
                        RoundingMode.HALF_UP);
    }
}
