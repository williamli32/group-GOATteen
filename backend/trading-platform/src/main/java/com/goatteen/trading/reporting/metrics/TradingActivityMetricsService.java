package com.goatteen.trading.reporting.metrics;

import com.goatteen.trading.order.OrderStatus;
import com.goatteen.trading.reporting.data.OrderFactRepository;
import com.goatteen.trading.reporting.data.TradeFactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
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

        return calculate(
                ReportingFilters.forPeriod(
                        startDate,
                        endDate
                )
        );
    }

    @Transactional(readOnly = true)
    public TradingActivityMetrics calculate(
            ReportingFilters filters) {

        LocalDateTime startDate =
                filters.fromDate();

        LocalDateTime endDate =
                filters.toDate();


        if (
                startDate == null
                        || endDate == null
                        || !startDate.isBefore(endDate)
        ) {

            throw new IllegalArgumentException(
                    "Start date must be before end date"
            );
        }


        long totalOrders =
                orderFactRepository
                        .countOrdersInDateRange(
                                startDate,
                                endDate,
                                filters.instrumentId(),
                                filters.side(),
                                filters.status(),
                                filters.clientId(),
                                filters.accountId(),
                                filters.currency()
                        );


        long filledOrders =
                filters.status() == null
                        || OrderStatus.FILLED
                                .name()
                                .equals(filters.status())

                        ? orderFactRepository
                                .countByStatusAndDateRange(
                                        OrderStatus.FILLED.name(),
                                        startDate,
                                        endDate,
                                        filters.instrumentId(),
                                        filters.side(),
                                        filters.clientId(),
                                        filters.accountId(),
                                        filters.currency()
                                )

                        : 0L;


        long rejectedOrders =
                filters.status() == null
                        || OrderStatus.REJECTED
                                .name()
                                .equals(filters.status())

                        ? orderFactRepository
                                .countByStatusAndDateRange(
                                        OrderStatus.REJECTED.name(),
                                        startDate,
                                        endDate,
                                        filters.instrumentId(),
                                        filters.side(),
                                        filters.clientId(),
                                        filters.accountId(),
                                        filters.currency()
                                )

                        : 0L;


        long activeClients =
                orderFactRepository
                        .countActiveClients(
                                startDate,
                                endDate,
                                filters.instrumentId(),
                                filters.side(),
                                filters.status(),
                                filters.clientId(),
                                filters.accountId(),
                                filters.currency()
                        );


        Object[] executionResult =
                tradeFactRepository
                        .summarizeExecutionActivity(
                                startDate,
                                endDate,
                                filters.instrumentId(),
                                filters.side(),
                                filters.status(),
                                filters.clientId(),
                                filters.accountId(),
                                filters.currency()
                        );


        Object[] execution =
                unwrapExecutionRow(
                        executionResult
                );


        BigDecimal totalExecutedQuantity =
                decimal(
                        execution[0]
                );


        BigDecimal totalTradedValue =
                decimal(
                        execution[1]
                );


        long buyFills =
                ((Number) execution[2])
                        .longValue();


        BigDecimal buyQuantity =
                decimal(
                        execution[3]
                );


        BigDecimal buyTradedValue =
                decimal(
                        execution[4]
                );


        long sellFills =
                ((Number) execution[5])
                        .longValue();


        BigDecimal sellQuantity =
                decimal(
                        execution[6]
                );


        BigDecimal sellTradedValue =
                decimal(
                        execution[7]
                );


        return new TradingActivityMetrics(
                totalOrders,
                filledOrders,
                rejectedOrders,
                activeClients,

                percentage(
                        filledOrders,
                        totalOrders
                ),

                percentage(
                        rejectedOrders,
                        totalOrders
                ),

                totalExecutedQuantity,
                totalTradedValue,

                buyFills,
                buyQuantity,
                buyTradedValue,

                sellFills,
                sellQuantity,
                sellTradedValue
        );
    }


    private BigDecimal percentage(
            long count,
            long total) {

        if (total == 0) {

            return BigDecimal.ZERO
                    .setScale(2);
        }


        return BigDecimal
                .valueOf(count)
                .multiply(
                        BigDecimal.valueOf(100)
                )
                .divide(
                        BigDecimal.valueOf(total),
                        2,
                        RoundingMode.HALF_UP
                );
    }


    private Object[] unwrapExecutionRow(
            Object[] result) {

        if (
                result != null
                        && result.length == 1
                        && result[0] instanceof Object[] nestedRow
        ) {

            return nestedRow;
        }


        return result;
    }


    private BigDecimal decimal(
            Object value) {

        if (value == null) {

            return BigDecimal.ZERO;
        }


        if (
                value
                        instanceof BigDecimal bigDecimal
        ) {

            return bigDecimal;
        }


        try {

            if (
                    value
                            instanceof Number number
            ) {

                return new BigDecimal(
                        number.toString()
                );
            }


            if (
                    value
                            instanceof char[] characters
            ) {

                return new BigDecimal(
                        new String(characters)
                );
            }


            if (
                    value
                            instanceof Character[] characters
            ) {

                StringBuilder text =
                        new StringBuilder(
                                characters.length
                        );


                for (
                        Character character
                        : characters
                ) {

                    text.append(character);
                }


                return new BigDecimal(
                        text.toString()
                );
            }


            if (
                    value
                            instanceof byte[] bytes
            ) {

                return new BigDecimal(
                        new String(
                                bytes,
                                StandardCharsets.UTF_8
                        )
                );
            }


            if (
                    value
                            .getClass()
                            .isArray()
            ) {

                int length =
                        Array.getLength(value);


                StringBuilder text =
                        new StringBuilder(
                                length
                        );


                for (
                        int index = 0;
                        index < length;
                        index++
                ) {

                    text.append(
                            Array.get(
                                    value,
                                    index
                            )
                    );
                }


                return new BigDecimal(
                        text.toString()
                );
            }


            return new BigDecimal(
                    value.toString()
            );

        } catch (
                NumberFormatException exception
        ) {

            throw new IllegalArgumentException(
                    "Invalid numeric value in reporting summary",
                    exception
            );
        }
    }
}