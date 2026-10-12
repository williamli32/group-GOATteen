
package com.goatteen.trading.reporting;

import com.goatteen.trading.reporting.data.OrderFactRepository;
import com.goatteen.trading.reporting.metrics.TradingActivityMetrics;
import com.goatteen.trading.reporting.metrics.TradingActivityMetricsService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradingActivityMetricsServiceTest {

    @Mock
    private OrderFactRepository orderFactRepository;

    private TradingActivityMetricsService metricsService;

    private final LocalDateTime start =
            LocalDateTime.of(2026, 10, 1, 0, 0);

    private final LocalDateTime end =
            LocalDateTime.of(2026, 11, 1, 0, 0);

    @BeforeEach
    void setUp() {
        metricsService =
                new TradingActivityMetricsService(orderFactRepository);
    }

    @Test
    void calculatesTradingActivityRates() {
        when(orderFactRepository.countOrdersInDateRange(start, end))
                .thenReturn(100L);

        when(orderFactRepository.countByStatusAndDateRange(
                "FILLED", start, end)).thenReturn(75L);

        when(orderFactRepository.countByStatusAndDateRange(
                "REJECTED", start, end)).thenReturn(10L);

        TradingActivityMetrics result =
                metricsService.calculate(start, end);

        assertEquals(100L, result.totalOrders());
        assertEquals(75L, result.filledOrders());
        assertEquals(10L, result.rejectedOrders());

        assertEquals(new BigDecimal("75.00"), result.fillRate());
        assertEquals(new BigDecimal("10.00"), result.rejectionRate());
    }

    @Test
    void returnsZeroRatesWhenThereAreNoOrders() {
        TradingActivityMetrics result =
                metricsService.calculate(start, end);

        assertEquals(0L, result.totalOrders());
        assertEquals(new BigDecimal("0.00"), result.fillRate());
        assertEquals(new BigDecimal("0.00"), result.rejectionRate());
    }

    @Test
    void rejectsInvalidDateRange() {
        assertThrows(IllegalArgumentException.class,
                () -> metricsService.calculate(end, start));

        assertThrows(IllegalArgumentException.class,
                () -> metricsService.calculate(start, start));

        verifyNoInteractions(orderFactRepository);
    }
}
