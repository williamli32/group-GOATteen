package com.goatteen.trading.reporting.metrics;

import java.math.BigDecimal;

public record PeriodComparison(
        TradingActivityMetrics previousPeriod,
        BigDecimal orderChangePercent,
        BigDecimal filledOrderChangePercent,
        BigDecimal tradedValueChangePercent) {
}