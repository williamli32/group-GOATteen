package com.goatteen.trading.reporting.metrics;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Inclusive reporting dates; repository queries use [fromInclusive, toExclusive). */
public record ReportingWindow(LocalDate fromDate, LocalDate toDate) {
    public ReportingWindow {
        if (fromDate == null || toDate == null || toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("A valid inclusive reporting window is required");
        }
    }

    public long days() {
        return ChronoUnit.DAYS.between(fromDate, toDate) + 1;
    }

    public LocalDateTime fromInclusive() {
        return fromDate.atStartOfDay();
    }

    public LocalDateTime toExclusive() {
        return toDate.plusDays(1).atStartOfDay();
    }

    public ReportingWindow previous() {
        return new ReportingWindow(fromDate.minusDays(days()), fromDate.minusDays(1));
    }
}
