package com.goatteen.trading.reporting.metrics;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessInsightsService {
    // Bound daily-series work and the associated comparison window.
    public static final long MAX_WINDOW_DAYS = 366;
    private final TradingActivityMetricsService activityService;
    private final InstrumentMetricsService instrumentService;
    private final ActivityTrendService trendService;

    public BusinessInsightsService(
            TradingActivityMetricsService activityService,
            InstrumentMetricsService instrumentService,
            ActivityTrendService trendService) {
        this.activityService = activityService;
        this.instrumentService = instrumentService;
        this.trendService = trendService;
    }

    /** One repeatable-read snapshot for current/baseline aggregates and rankings. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public BusinessInsights calculate(ReportingFilters filters) {
        if (filters.currency() == null) {
            throw new IllegalArgumentException("currency is required; monetary insights cannot mix currencies");
        }
        ReportingWindow window = new ReportingWindow(
                filters.fromDate().toLocalDate(), filters.toDate().minusNanos(1).toLocalDate());
        if (!window.fromInclusive().equals(filters.fromDate())
                || !window.toExclusive().equals(filters.toDate())) {
            throw new IllegalArgumentException("Insights require whole-day reporting boundaries");
        }
        if (window.days() > MAX_WINDOW_DAYS) {
            throw new IllegalArgumentException("Insights reporting windows cannot exceed 366 days");
        }
        ReportingWindow oldWindow = window.previous();
        ReportingFilters baseline = new ReportingFilters(
                oldWindow.fromInclusive(), oldWindow.toExclusive(),
                filters.instrumentId(), filters.side(), filters.status(), filters.clientId(),
                filters.accountId(), filters.currency());

        return BusinessInsightsCalculator.calculate(
                window, filters.currency(), activityService.calculate(filters), activityService.calculate(baseline),
                instrumentService.calculate(filters), instrumentService.calculate(baseline),
                trendService.calculate(filters));
    }
}
