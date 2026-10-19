package com.goatteen.trading.reporting;

import com.goatteen.trading.reporting.data.OrderFactRepository;
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
        private OrderFactRepository orderFactRepository;

        @Mock
        private TradeFactRepository tradeFactRepository;

        private ClientMetricsService metricsService;

        private final LocalDateTime start = LocalDateTime.of(
                        2026,
                        10,
                        1,
                        0,
                        0);

        private final LocalDateTime end = LocalDateTime.of(
                        2026,
                        11,
                        1,
                        0,
                        0);

        @BeforeEach
        void setUp() {

                metricsService = new ClientMetricsService(
                                orderFactRepository,
                                tradeFactRepository);
        }

        @Test
        void calculatesOrdersAndExecutedActivityPerClient() {

                Object[] client1Orders = {
                                101L,
                                "USD",
                                7L
                };

                Object[] client2Orders = {
                                102L,
                                "EUR",
                                4L
                };

                Object[] client3Orders = {
                                103L,
                                "GBP",
                                2L
                };

                when(
                                orderFactRepository.summarizeOrdersByClient(
                                                start,
                                                end,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null))
                                .thenReturn(
                                                List.of(
                                                                client1Orders,
                                                                client2Orders,
                                                                client3Orders));

                Object[] client1Trades = {
                                101L,
                                "USD",
                                5L,
                                new BigDecimal("100"),
                                new BigDecimal("15000.00")
                };

                Object[] client2Trades = {
                                102L,
                                "EUR",
                                3L,
                                new BigDecimal("50"),
                                new BigDecimal("8000.00")
                };

                when(
                                tradeFactRepository.summarizeByClient(
                                                start,
                                                end,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null))
                                .thenReturn(
                                                List.of(
                                                                client1Trades,
                                                                client2Trades));

                List<ClientMetrics> result = metricsService.calculate(
                                start,
                                end);

                assertEquals(
                                3,
                                result.size());

                ClientMetrics first = result.get(0);

                assertEquals(
                                101L,
                                first.clientId());

                assertEquals(
                                "USD",
                                first.currency());

                assertEquals(
                                7L,
                                first.orderCount());

                assertEquals(
                                5L,
                                first.fillCount());

                assertEquals(
                                new BigDecimal("100"),
                                first.executedQuantity());

                assertEquals(
                                new BigDecimal("15000.00"),
                                first.tradedValue());

                ClientMetrics rejectedOnly = result.get(2);

                assertEquals(
                                103L,
                                rejectedOnly.clientId());

                assertEquals(
                                2L,
                                rejectedOnly.orderCount());

                assertEquals(
                                0L,
                                rejectedOnly.fillCount());

                assertEquals(
                                BigDecimal.ZERO,
                                rejectedOnly.executedQuantity());

                assertEquals(
                                BigDecimal.ZERO,
                                rejectedOnly.tradedValue());
        }

        @Test
        void returnsEmptyListWhenThereIsNoActivity() {

                when(
                                orderFactRepository.summarizeOrdersByClient(
                                                start,
                                                end,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null))
                                .thenReturn(
                                                List.of());

                when(
                                tradeFactRepository.summarizeByClient(
                                                start,
                                                end,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null))
                                .thenReturn(
                                                List.of());

                List<ClientMetrics> result = metricsService.calculate(
                                start,
                                end);

                assertTrue(
                                result.isEmpty());
        }

        @Test
        void rejectsInvalidDateRange() {

                assertThrows(
                                IllegalArgumentException.class,
                                () -> metricsService.calculate(
                                                end,
                                                start));

                verifyNoInteractions(
                                orderFactRepository,
                                tradeFactRepository);
        }
}