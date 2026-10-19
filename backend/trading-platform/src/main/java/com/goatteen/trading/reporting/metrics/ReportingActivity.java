package com.goatteen.trading.reporting.metrics;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReportingActivity(
        Long orderId,
        Long fillId,
        Long accountId,
        Long clientId,
        Long instrumentId,
        String instrumentSymbol,
        String side,
        String status,
        BigDecimal orderQuantity,
        BigDecimal executionQuantity,
        BigDecimal executionPrice,
        BigDecimal tradedValue,
        LocalDateTime occurredAt) {
}
