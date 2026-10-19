package com.goatteen.trading.reporting;

import com.goatteen.trading.reporting.metrics.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BusinessInsightsServiceTest {
    @Test
    void fetchesBothWindowsWithoutLosingAnyFilters() {
        var activity = mock(TradingActivityMetricsService.class);
        var instruments = mock(InstrumentMetricsService.class);
        var trends = mock(ActivityTrendService.class);
        var empty = new TradingActivityMetrics(0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO,
                0, BigDecimal.ZERO, BigDecimal.ZERO);
        when(activity.calculate(any(ReportingFilters.class))).thenReturn(empty);
        when(instruments.calculate(any(ReportingFilters.class))).thenReturn(List.of());
        when(trends.calculate(any(ReportingFilters.class))).thenReturn(List.of());
        var service = new BusinessInsightsService(activity, instruments, trends);
        var filters = new ReportingFilters(
                LocalDate.of(2026, 10, 1).atStartOfDay(), LocalDate.of(2026, 10, 17).atStartOfDay(),
                7L, "BUY", "FILLED", 42L, 43L, "USD");
        var result = service.calculate(filters);
        var captured = ArgumentCaptor.forClass(ReportingFilters.class);
        verify(activity, times(2)).calculate(captured.capture());
        ReportingFilters baseline = captured.getAllValues().get(1);
        assertEquals(LocalDate.of(2026, 9, 15).atStartOfDay(), baseline.fromDate());
        assertEquals(filters.fromDate(), baseline.toDate());
        assertEquals(filters.instrumentId(), baseline.instrumentId());
        assertEquals(filters.side(), baseline.side());
        assertEquals(filters.status(), baseline.status());
        assertEquals(filters.clientId(), baseline.clientId());
        assertEquals(filters.accountId(), baseline.accountId());
        assertEquals(filters.currency(), baseline.currency());
        verify(instruments).calculate(baseline);
        assertEquals(0, result.currentFillCount());
    }

    @Test
    void rejectsMixedCurrencyAndUnboundedRequestsBeforeReadingRepositories() {
        var activity = mock(TradingActivityMetricsService.class);
        var instruments = mock(InstrumentMetricsService.class);
        var trends = mock(ActivityTrendService.class);
        var service = new BusinessInsightsService(activity, instruments, trends);
        var start = LocalDate.of(2026, 1, 1).atStartOfDay();
        assertThrows(IllegalArgumentException.class,
                () -> service.calculate(ReportingFilters.forPeriod(start, start.plusDays(1))));
        assertThrows(IllegalArgumentException.class, () -> service.calculate(new ReportingFilters(
                start, start.plusDays(367), null, null, null, null, null, "USD")));
        verifyNoInteractions(activity, instruments, trends);
    }
}
