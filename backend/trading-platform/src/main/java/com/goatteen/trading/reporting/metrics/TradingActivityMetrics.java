
package com.goatteen.trading.reporting.metrics;

import java.math.BigDecimal;

/**
 * Trading activity summary for a reporting period.
 * Rates are percentages from 0 to 100.
 */
public record TradingActivityMetrics(
        long totalOrders,
        long filledOrders,
        long rejectedOrders,
        BigDecimal fillRate,
        BigDecimal rejectionRate) {
}
