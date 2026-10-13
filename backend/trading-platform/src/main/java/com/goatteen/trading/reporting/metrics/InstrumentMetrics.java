
package com.goatteen.trading.reporting.metrics;

import java.math.BigDecimal;

/**
 * Executed trading activity for one instrument and currency.
 */
public record InstrumentMetrics(
        Long instrumentId,
        String instrumentSymbol,
        String currency,
        long fillCount,
        BigDecimal executedQuantity,
        BigDecimal tradedValue) {
}
