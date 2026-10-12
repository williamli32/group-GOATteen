
package com.goatteen.trading.reporting;

import com.goatteen.trading.reporting.data.TradeFactRepository;
import com.goatteen.trading.reporting.metrics.ClientMetrics;
import com.goatteen.trading.reporting.metrics.ClientMetricsService;

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
class ClientMetricsServiceTest {

    @Mock
    private TradeFactRepository tradeFactRepository;

    private ClientMetricsService metricsService;

    private final LocalDateTime start =
            LocalDateTime.of(2026, 10, 1, 0, 0);

    private final LocalDateTime end =
            LocalDateTime.of(2026, 11, 1, 0, 0);

    @BeforeEach
    void setUp() {
        metricsService =
                new ClientMetricsService(tradeFactRepository);
    }

    @Test
    void calculatesClientMetrics() {

        Object[] usdRow = {
                101L,
                "USD",
                5L,
                new BigDecimal("100"),
                new BigDecimal("15000.00")
        };

        Object[] eurRow = {
                102L,
                "EUR",
                3L,
                new BigDecimal("50"),
                new BigDecimal("8000.00")
        };

        when(tradeFactRepository.summarizeByClient(start, end))
                .thenReturn(List.<Object[]>of(usdRow, eurRow));

        List<ClientMetrics> result =
                metricsService.calculate(start, end);

        assertEquals(2, result.size());

        assertEquals(101L, result.get(0).clientId());
        assertEquals("USD", result.get(0).currency());
        assertEquals(5L, result.get(0).fillCount());
        assertEquals(
                new BigDecimal("100"),
                result.get(0).executedQuantity());
        assertEquals(
                new BigDecimal("15000.00"),
                result.get(0).tradedValue());

        assertEquals(102L, result.get(1).clientId());
        assertEquals("EUR", result.get(1).currency());
        assertEquals(3L, result.get(1).fillCount());
    }

    @Test
    void returnsEmptyListWhenThereAreNoTrades() {

        when(tradeFactRepository.summarizeByClient(start, end))
                .thenReturn(List.of());

        List<ClientMetrics> result =
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
