package com.goatteen.trading.reporting.metrics;

import com.goatteen.trading.order.OrderStatus;

import java.time.LocalDateTime;

public record ReportingFilters(
        LocalDateTime fromDate,
        LocalDateTime toDate,
        Long instrumentId,
        String side,
        String status,
        Long clientId,
        Long accountId) {

    public ReportingFilters {

        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException(
                    "fromDate and toDate are required");
        }

        if (!fromDate.isBefore(toDate)) {
            throw new IllegalArgumentException(
                    "fromDate must be before toDate");
        }

        side = normalize(side);
        status = normalize(status);

        if (side != null
                && !side.equals("BUY")
                && !side.equals("SELL")) {

            throw new IllegalArgumentException(
                    "side must be BUY or SELL");
        }

        if (status != null) {

            try {
                OrderStatus.valueOf(status);
            } catch (IllegalArgumentException exception) {

                throw new IllegalArgumentException(
                        "Invalid order status: " + status);
            }
        }

        if (instrumentId != null && instrumentId <= 0) {
            throw new IllegalArgumentException(
                    "instrumentId must be positive");
        }

        if (clientId != null && clientId <= 0) {
            throw new IllegalArgumentException(
                    "clientId must be positive");
        }

        if (accountId != null && accountId <= 0) {
            throw new IllegalArgumentException(
                    "accountId must be positive");
        }
    }

    public static ReportingFilters forPeriod(
            LocalDateTime fromDate,
            LocalDateTime toDate) {

        return new ReportingFilters(
                fromDate,
                toDate,
                null,
                null,
                null,
                null,
                null);
    }

    private static String normalize(String value) {

        return value == null || value.isBlank()
                ? null
                : value.trim().toUpperCase();
    }
}