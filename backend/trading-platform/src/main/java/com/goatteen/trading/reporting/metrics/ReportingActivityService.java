package com.goatteen.trading.reporting.metrics;

import com.goatteen.trading.reporting.data.OrderFactRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReportingActivityService {

    private final OrderFactRepository
            orderFactRepository;


    public ReportingActivityService(
            OrderFactRepository orderFactRepository) {

        this.orderFactRepository =
                orderFactRepository;
    }


    @Transactional(readOnly = true)
    public List<ReportingActivity> find(
            ReportingFilters filters) {

        return orderFactRepository
                .findReportingActivity(
                        filters.fromDate(),
                        filters.toDate(),
                        filters.instrumentId(),
                        filters.side(),
                        filters.status(),
                        filters.clientId(),
                        filters.accountId(),
                        filters.currency()
                )
                .stream()
                .map(
                        this::mapActivity
                )
                .toList();
    }


    private ReportingActivity mapActivity(
            Object[] row) {

        return new ReportingActivity(
                toLong(
                        row[0]
                ),

                toNullableLong(
                        row[1]
                ),

                toLong(
                        row[2]
                ),

                toLong(
                        row[3]
                ),

                toLong(
                        row[4]
                ),

                (String) row[5],

                (String) row[6],

                (String) row[7],

                toBigDecimal(
                        row[8]
                ),

                toNullableBigDecimal(
                        row[9]
                ),

                toNullableBigDecimal(
                        row[10]
                ),

                toNullableBigDecimal(
                        row[11]
                ),

                toLocalDateTime(
                        row[12]
                )
        );
    }


    private Long toLong(
            Object value) {

        return ((Number) value)
                .longValue();
    }


    private Long toNullableLong(
            Object value) {

        return value == null
                ? null
                : ((Number) value)
                        .longValue();
    }


    private BigDecimal toBigDecimal(
            Object value) {

        if (
                value
                        instanceof BigDecimal bigDecimal
        ) {

            return bigDecimal;
        }


        return new BigDecimal(
                value.toString()
        );
    }


    private BigDecimal toNullableBigDecimal(
            Object value) {

        if (
                value == null
        ) {

            return null;
        }


        return toBigDecimal(
                value
        );
    }


    private LocalDateTime toLocalDateTime(
            Object value) {

        if (
                value
                        instanceof LocalDateTime dateTime
        ) {

            return dateTime;
        }


        if (
                value
                        instanceof Timestamp timestamp
        ) {

            return timestamp
                    .toLocalDateTime();
        }


        throw new IllegalArgumentException(
                "Unsupported reporting timestamp type"
        );
    }
}