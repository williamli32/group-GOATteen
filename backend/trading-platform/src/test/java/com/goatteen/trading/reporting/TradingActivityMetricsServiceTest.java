package com.goatteen.trading.reporting;

import com.goatteen.trading.reporting.data.OrderFactRepository;
import com.goatteen.trading.reporting.data.TradeFactRepository;
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

        @Mock
        private TradeFactRepository tradeFactRepository;

        private TradingActivityMetricsService metricsService;

        private final LocalDateTime start = LocalDateTime.of(2026, 10, 1, 0, 0);

        private final LocalDateTime end = LocalDateTime.of(2026, 11, 1, 0, 0);

        @BeforeEach
        void setUp() {

                metricsService = new TradingActivityMetricsService(
                                orderFactRepository,
                                tradeFactRepository);
        }

        @Test
        void calculatesCompleteTradingActivityMetrics() {

                when(orderFactRepository
                                .countOrdersInDateRange(start, end))
                                .thenReturn(100L);

                when(orderFactRepository
                                .countByStatusAndDateRange(
                                                "FILLED",
                                                start,
                                                end))
                                .thenReturn(75L);

                when(orderFactRepository
                                .countByStatusAndDateRange(
                                                "REJECTED",
                                                start,
                                                end))
                                .thenReturn(10L);

                when(orderFactRepository
                                .countActiveClients(start, end))
                                .thenReturn(42L);

                when(tradeFactRepository
                                .summarizeExecutionActivity(start, end))
                                .thenReturn(new Object[] {
                                                new BigDecimal("1000"),
                                                new BigDecimal("250000.00"),

                                                50L,
                                                new BigDecimal("700"),
                                                new BigDecimal("175000.00"),

                                                25L,
                                                new BigDecimal("300"),
                                                new BigDecimal("75000.00")
                                });

                TradingActivityMetrics result = metricsService.calculate(start, end);

                assertEquals(100L, result.totalOrders());
                assertEquals(75L, result.filledOrders());
                assertEquals(10L, result.rejectedOrders());
                assertEquals(42L, result.activeClients());

                assertEquals(
                                new BigDecimal("75.00"),
                                result.fillRate());

                assertEquals(
                                new BigDecimal("10.00"),
                                result.rejectionRate());

                assertEquals(
                                new BigDecimal("1000"),
                                result.totalExecutedQuantity());

                assertEquals(
                                new BigDecimal("250000.00"),
                                result.totalTradedValue());

                assertEquals(50L, result.buyFills());
                assertEquals(
                                new BigDecimal("700"),
                                result.buyQuantity());

                assertEquals(
                                new BigDecimal("175000.00"),
                                result.buyTradedValue());

                assertEquals(25L, result.sellFills());

                assertEquals(
                                new BigDecimal("300"),
                                result.sellQuantity());

                assertEquals(
                                new BigDecimal("75000.00"),
                                result.sellTradedValue());
        }

        @Test
        void returnsZeroMetricsWhenThereIsNoActivity() {

                when(tradeFactRepository
                                .summarizeExecutionActivity(start, end))
                                .thenReturn(new Object[] {
                                                BigDecimal.ZERO,
                                                BigDecimal.ZERO,
                                                0L,
                                                BigDecimal.ZERO,
                                                BigDecimal.ZERO,
                                                0L,
                                                BigDecimal.ZERO,
                                                BigDecimal.ZERO
                                });

                TradingActivityMetrics result = metricsService.calculate(start, end);

                assertEquals(0L, result.totalOrders());
                assertEquals(0L, result.filledOrders());
                assertEquals(0L, result.rejectedOrders());
                assertEquals(0L, result.activeClients());

                assertEquals(
                                new BigDecimal("0.00"),
                                result.fillRate());

                assertEquals(
                                new BigDecimal("0.00"),
                                result.rejectionRate());

                assertEquals(
                                BigDecimal.ZERO,
                                result.totalExecutedQuantity());

                assertEquals(
                                BigDecimal.ZERO,
                                result.totalTradedValue());
        }

        @Test
        void rejectsInvalidDateRange() {

                assertThrows(
                                IllegalArgumentException.class,
                                () -> metricsService.calculate(
                                                end,
                                                start));

                assertThrows(
                                IllegalArgumentException.class,
                                () -> metricsService.calculate(
                                                start,
                                                start));

                verifyNoInteractions(
                                orderFactRepository,
                                tradeFactRepository);
        }
}