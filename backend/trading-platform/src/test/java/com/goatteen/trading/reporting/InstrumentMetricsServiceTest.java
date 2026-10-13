
package com.goatteen.trading.reporting;

import com.goatteen.trading.reporting.data.TradeFactRepository;
import com.goatteen.trading.reporting.metrics.InstrumentMetrics;
import com.goatteen.trading.reporting.metrics.InstrumentMetricsService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstrumentMetricsServiceTest {

    @Mock
    private TradeFactRepository tradeFactRepository;

    private InstrumentMetricsService metricsService;

    private final LocalDateTime start =
            LocalDateTime.of(2026, 10, 1, 0, 0);

    private final LocalDateTime end =
            LocalDateTime.of(2026, 11, 1, 0, 0);

    @BeforeEach
    void setUp() {
        metricsService =
                new InstrumentMetricsService(tradeFactRepository);
    }

    @Test
    void calculatesInstrumentMetrics() {

        Object[] appleRow = {
                1L,
                "AAPL",
                "USD",
                5L,
                new BigDecimal("100"),
                new BigDecimal("15000.00")
        };

        Object[] microsoftRow = {
                2L,
                "MSFT",
                "USD",
                3L,
                new BigDecimal("50"),
                new BigDecimal("20000.00")
        };

        when(tradeFactRepository.summarizeByInstrument(start, end, null, null, null, null, null))
                .thenReturn(List.<Object[]>of(appleRow, microsoftRow));

        List<InstrumentMetrics> result =
                metricsService.calculate(start, end);

        assertEquals(2, result.size());

        assertEquals("AAPL", result.get(0).instrumentSymbol());
        assertEquals(5L, result.get(0).fillCount());
        assertEquals(
                new BigDecimal("100"),
                result.get(0).executedQuantity());
        assertEquals(
                new BigDecimal("15000.00"),
                result.get(0).tradedValue());

        assertEquals("MSFT", result.get(1).instrumentSymbol());
        assertEquals(3L, result.get(1).fillCount());
    }

    @Test
    void returnsEmptyListWhenThereAreNoTrades() {

        when(tradeFactRepository.summarizeByInstrument(start, end, null, null, null, null, null))
                .thenReturn(List.of());

        List<InstrumentMetrics> result =
                metricsService.calculate(start, end);

        assertTrue(result.isEmpty());
    }

    @Test
    void rejectsInvalidDateRange() {

        assertThrows(
                IllegalArgumentException.class,
                () -> metricsService.calculate(end, start));

        assertThrows(
                IllegalArgumentException.class,
                () -> metricsService.calculate(start, start));

        verifyNoInteractions(tradeFactRepository);
    }
}
