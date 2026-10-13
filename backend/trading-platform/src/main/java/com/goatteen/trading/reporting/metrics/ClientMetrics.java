
package com.goatteen.trading.reporting.metrics;

import java.math.BigDecimal;

/**
 * Executed trading activity for one client and currency.
 */
public record ClientMetrics(
                Long clientId,
                String currency,

                long orderCount,
                long fillCount,

                BigDecimal executedQuantity,
                BigDecimal tradedValue) {
}
