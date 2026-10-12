package com.goatteen.trading.reporting.metrics;

import com.goatteen.trading.reporting.data.OrderFactRepository;
import com.goatteen.trading.reporting.data.TradeFactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class ActivityTrendService {

    private final OrderFactRepository orderFactRepository;
    private final TradeFactRepository tradeFactRepository;

    public ActivityTrendService(
            OrderFactRepository orderFactRepository,
            TradeFactRepository tradeFactRepository) {

        this.orderFactRepository = orderFactRepository;
        this.tradeFactRepository = tradeFactRepository;
    }

    @Transactional(readOnly = true)
    public List<ActivityTrendPoint> calculate(
            LocalDateTime startDate,
            LocalDateTime endDate) {

        if (startDate == null || endDate == null
                || !startDate.isBefore(endDate)) {

            throw new IllegalArgumentException(
                    "Start date must be before end date");
        }

        /*
         * TreeMap keeps the results automatically sorted by date.
         */
        Map<LocalDate, TrendData> trends = new TreeMap<>();

        /*
         * Order activity:
         *
         * date
         * total orders
         * filled orders
         * rejected orders
         * active clients
         */
        List<Object[]> orderRows = orderFactRepository.summarizeDailyActivity(
                startDate,
                endDate);

        for (Object[] row : orderRows) {

            LocalDate date = toLocalDate(row[0]);

            TrendData data = trends.computeIfAbsent(
                    date,
                    ignored -> new TrendData());

            data.totalOrders = toLong(row[1]);

            data.filledOrders = toLong(row[2]);

            data.rejectedOrders = toLong(row[3]);

            data.activeClients = toLong(row[4]);
        }

        /*
         * Execution activity:
         *
         * date
         * executed quantity
         * traded value
         */
        List<Object[]> executionRows = tradeFactRepository.summarizeDailyExecution(
                startDate,
                endDate);

        for (Object[] row : executionRows) {

            LocalDate date = toLocalDate(row[0]);

            TrendData data = trends.computeIfAbsent(
                    date,
                    ignored -> new TrendData());

            data.executedQuantity = toBigDecimal(row[1]);

            data.tradedValue = toBigDecimal(row[2]);
        }

        /*
         * Convert the combined data into API response objects.
         */
        return trends.entrySet()
                .stream()
                .map(entry -> {

                    TrendData data = entry.getValue();

                    return new ActivityTrendPoint(
                            entry.getKey(),
                            data.totalOrders,
                            data.filledOrders,
                            data.rejectedOrders,
                            data.activeClients,
                            data.executedQuantity,
                            data.tradedValue);
                })
                .toList();
    }

    private long toLong(Object value) {

        if (value == null) {
            return 0L;
        }

        return ((Number) value).longValue();
    }

    private BigDecimal toBigDecimal(Object value) {

        if (value == null) {
            return BigDecimal.ZERO;
        }

        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }

        return new BigDecimal(value.toString());
    }

    private LocalDate toLocalDate(Object value) {

        if (value instanceof LocalDate localDate) {
            return localDate;
        }

        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }

        return LocalDate.parse(value.toString());
    }

    /*
     * Internal object used while combining order and execution data.
     */
    private static class TrendData {

        private long totalOrders;
        private long filledOrders;
        private long rejectedOrders;
        private long activeClients;

        private BigDecimal executedQuantity = BigDecimal.ZERO;

        private BigDecimal tradedValue = BigDecimal.ZERO;
    }
}