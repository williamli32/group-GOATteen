package com.goatteen.trading.reporting.metrics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BusinessInsights(
        ReportingWindow currentWindow,
        ReportingWindow previousWindow,
        String currency,
        TradingActivityMetrics current,
        TradingActivityMetrics previous,
        long currentFillCount,
        long previousFillCount,
        BigDecimal volumeChangePercent,
        BigDecimal buySharePercent,
        BigDecimal sellSharePercent,
        InstrumentInsight mostActiveInstrument,
        InstrumentInsight fastestGrowingInstrument,
        List<InstrumentInsight> newlyActiveInstruments,
        PeakDay highestVolumeDay,
        BigDecimal rejectionRateChangePercentagePoints,
        List<RejectionTrendPoint> rejectionTrend) {

    public record InstrumentInsight(
            Long instrumentId, String symbol, long fillCount, long previousFillCount,
            BigDecimal changePercent, boolean newlyActive) {
    }

    public record PeakDay(LocalDate date, long fillCount) {
    }

    /** A null rate means there were no order submissions that day, not a 0% rate. */
    public record RejectionTrendPoint(
            LocalDate date, long totalOrders, long rejectedOrders, BigDecimal rejectionRate) {
    }
}
