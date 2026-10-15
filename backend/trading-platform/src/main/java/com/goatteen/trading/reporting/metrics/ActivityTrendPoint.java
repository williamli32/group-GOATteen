package com.goatteen.trading.reporting.metrics;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ActivityTrendPoint(
        LocalDate date,

        long totalOrders,
        long filledOrders,
        long rejectedOrders,
        long activeClients,

        BigDecimal executedQuantity,
        BigDecimal tradedValue,
        long buyFills,
        long sellFills) {
}