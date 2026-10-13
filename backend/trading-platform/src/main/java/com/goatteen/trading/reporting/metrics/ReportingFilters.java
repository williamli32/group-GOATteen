package com.goatteen.trading.reporting.metrics;

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
        if (fromDate == null || toDate == null || !fromDate.isBefore(toDate)) {
            throw new IllegalArgumentException("fromDate must be before toDate");
        }

        side = normalize(side);
        status = normalize(status);
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
