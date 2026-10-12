
package com.goatteen.trading.reporting.metrics;

import com.goatteen.trading.order.OrderStatus;
import com.goatteen.trading.reporting.data.OrderFactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
public class TradingActivityMetricsService {

    private final OrderFactRepository orderFactRepository;

    public TradingActivityMetricsService(
            OrderFactRepository orderFactRepository) {
        this.orderFactRepository = orderFactRepository;
    }

    @Transactional(readOnly = true)
    public TradingActivityMetrics calculate(
            LocalDateTime startDate,
            LocalDateTime endDate) {

        if (startDate == null || endDate == null ||
                !startDate.isBefore(endDate)) {
            throw new IllegalArgumentException(
                    "Start date must be before end date");
        }

        long totalOrders = orderFactRepository
                .countOrdersInDateRange(startDate, endDate);

        long filledOrders = orderFactRepository
                .countByStatusAndDateRange(
                        OrderStatus.FILLED.name(),
                        startDate,
                        endDate);

        long rejectedOrders = orderFactRepository
                .countByStatusAndDateRange(
                        OrderStatus.REJECTED.name(),
                        startDate,
                        endDate);

        return new TradingActivityMetrics(
                totalOrders,
                filledOrders,
                rejectedOrders,
                percentage(filledOrders, totalOrders),
                percentage(rejectedOrders, totalOrders)
        );
    }

    private BigDecimal percentage(long count, long total) {
        if (total == 0) {
            return BigDecimal.ZERO.setScale(2);
        }

        return BigDecimal.valueOf(count)
                .multiply(BigDecimal.valueOf(100))
                .divide(
                        BigDecimal.valueOf(total),
                        2,
                        RoundingMode.HALF_UP
                );
    }
}
