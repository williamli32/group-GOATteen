
package com.goatteen.trading.reporting.metrics;

import com.goatteen.trading.order.OrderStatus;
import com.goatteen.trading.reporting.data.OrderFactRepository;
import com.goatteen.trading.reporting.data.TradeFactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
public class TradingActivityMetricsService {

        private final OrderFactRepository orderFactRepository;
        private final TradeFactRepository tradeFactRepository;

        public TradingActivityMetricsService(
                        OrderFactRepository orderFactRepository,
                        TradeFactRepository tradeFactRepository) {
                this.orderFactRepository = orderFactRepository;
                this.tradeFactRepository = tradeFactRepository;
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

                long activeClients = orderFactRepository
                                .countActiveClients(startDate, endDate);

                Object[] execution = tradeFactRepository.summarizeExecutionActivity(
                                startDate,
                                endDate);

                BigDecimal totalExecutedQuantity = decimal(execution[0]);

                BigDecimal totalTradedValue = decimal(execution[1]);

                long buyFills = ((Number) execution[2]).longValue();

                BigDecimal buyQuantity = decimal(execution[3]);

                BigDecimal buyTradedValue = decimal(execution[4]);

                long sellFills = ((Number) execution[5]).longValue();

                BigDecimal sellQuantity = decimal(execution[6]);

                BigDecimal sellTradedValue = decimal(execution[7]);

                return new TradingActivityMetrics(
                                totalOrders,
                                filledOrders,
                                rejectedOrders,
                                activeClients,

                                percentage(filledOrders, totalOrders),
                                percentage(rejectedOrders, totalOrders),

                                totalExecutedQuantity,
                                totalTradedValue,

                                buyFills,
                                buyQuantity,
                                buyTradedValue,

                                sellFills,
                                sellQuantity,
                                sellTradedValue);
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
                                                RoundingMode.HALF_UP);
        }

        private BigDecimal decimal(Object value) {

                if (value == null) {
                        return BigDecimal.ZERO;
                }

                if (value instanceof BigDecimal bigDecimal) {
                        return bigDecimal;
                }

                return new BigDecimal(value.toString());
        }
}
