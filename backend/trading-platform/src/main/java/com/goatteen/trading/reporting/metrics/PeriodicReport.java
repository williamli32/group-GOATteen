
package com.goatteen.trading.reporting.metrics;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reporting summary for a daily, weekly, or monthly period.
 */
public record PeriodicReport(
        ReportingPeriod period,
        LocalDateTime startDate,
        LocalDateTime endDate,
        TradingActivityMetrics tradingActivity,
        List<InstrumentMetrics> instruments,
        List<ClientMetrics> clients) {
}
