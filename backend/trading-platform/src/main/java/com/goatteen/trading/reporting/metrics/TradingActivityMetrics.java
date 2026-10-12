package com.goatteen.trading.reporting.metrics;

import java.math.BigDecimal;

/**
 * Overall trading activity for a reporting period.
 */
public record TradingActivityMetrics(
                long totalOrders,
                long filledOrders,
                long rejectedOrders,
                long activeClients,

                BigDecimal fillRate,
                BigDecimal rejectionRate,

                BigDecimal totalExecutedQuantity,
                BigDecimal totalTradedValue,

                long buyFills,
                BigDecimal buyQuantity,
                BigDecimal buyTradedValue,

                long sellFills,
                BigDecimal sellQuantity,
                BigDecimal sellTradedValue) {
}