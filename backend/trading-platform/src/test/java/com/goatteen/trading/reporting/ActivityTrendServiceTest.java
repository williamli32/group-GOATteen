package com.goatteen.trading.reporting;

import com.goatteen.trading.reporting.data.OrderFactRepository;
import com.goatteen.trading.reporting.data.TradeFactRepository;
import com.goatteen.trading.reporting.metrics.ActivityTrendPoint;
import com.goatteen.trading.reporting.metrics.ActivityTrendService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityTrendServiceTest {

    @Mock
    private OrderFactRepository orderFactRepository;

    @Mock
    private TradeFactRepository tradeFactRepository;

    private ActivityTrendService service;

    private final LocalDateTime start = LocalDateTime.of(2026, 10, 10, 0, 0);

    private final LocalDateTime end = LocalDateTime.of(2026, 10, 13, 0, 0);

    @BeforeEach
    void setUp() {

        service = new ActivityTrendService(
                orderFactRepository,
                tradeFactRepository);
    }

    @Test
    void combinesOrderAndExecutionActivityByDate() {

        when(orderFactRepository
                .summarizeDailyActivity(start, end, null, null, null, null, null))
                .thenReturn(List.of(
                        new Object[] {
                                LocalDate.of(2026, 10, 10),
                                10L,
                                8L,
                                2L,
                                5L
                        },
                        new Object[] {
                                LocalDate.of(2026, 10, 11),
                                5L,
                                4L,
                                1L,
                                3L
                        },
                        new Object[] {
                                LocalDate.of(2026, 10, 12),
                                2L,
                                0L,
                                2L,
                                2L
                        }));

        when(tradeFactRepository
                .summarizeDailyExecution(start, end, null, null, null, null, null))
                .thenReturn(List.of(
                        new Object[] {
                                LocalDate.of(2026, 10, 10),
                                new BigDecimal("80"),
                                new BigDecimal("12000.00")
                        },
                        new Object[] {
                                LocalDate.of(2026, 10, 11),
                                new BigDecimal("40"),
                                new BigDecimal("6000.00")
                        }));

        List<ActivityTrendPoint> result = service.calculate(start, end);

        assertEquals(3, result.size());

        ActivityTrendPoint first = result.get(0);

        assertEquals(
                LocalDate.of(2026, 10, 10),
                first.date());

        assertEquals(10L, first.totalOrders());
        assertEquals(8L, first.filledOrders());
        assertEquals(2L, first.rejectedOrders());
        assertEquals(5L, first.activeClients());

        assertEquals(
                new BigDecimal("80"),
                first.executedQuantity());

        assertEquals(
                new BigDecimal("12000.00"),
                first.tradedValue());

        /*
         * Oct 12 has orders but no successful executions.
         */
        ActivityTrendPoint third = result.get(2);

        assertEquals(
                LocalDate.of(2026, 10, 12),
                third.date());

        assertEquals(2L, third.totalOrders());
        assertEquals(0L, third.filledOrders());

        assertEquals(
                BigDecimal.ZERO,
                third.executedQuantity());

        assertEquals(
                BigDecimal.ZERO,
                third.tradedValue());
    }

    @Test
    void returnsEmptyListWhenThereIsNoActivity() {

        when(orderFactRepository
                .summarizeDailyActivity(start, end, null, null, null, null, null))
                .thenReturn(List.of());

        when(tradeFactRepository
                .summarizeDailyExecution(start, end, null, null, null, null, null))
                .thenReturn(List.of());

        assertTrue(
                service.calculate(start, end)
                        .isEmpty());
    }

    @Test
    void rejectsInvalidDateRange() {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.calculate(
                        end,
                        start));

        verifyNoInteractions(
                orderFactRepository,
                tradeFactRepository);
    }
}