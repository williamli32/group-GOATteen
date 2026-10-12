
package com.goatteen.trading.reporting;

import com.goatteen.trading.order.Order;
import com.goatteen.trading.order.OrderRepository;
import com.goatteen.trading.reporting.sync.OrderFactBackfillService;
import com.goatteen.trading.reporting.sync.OrderFactSyncService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderFactBackfillServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderFactSyncService orderFactSyncService;

    private OrderFactBackfillService backfillService;

    @BeforeEach
    void setUp() {
        backfillService = new OrderFactBackfillService(
                orderRepository,
                orderFactSyncService
        );
    }

    @Test
    void backfillSynchronizesExistingOrders() {
        Order firstOrder = mock(Order.class);
        Order secondOrder = mock(Order.class);

        when(firstOrder.getId()).thenReturn(1L);
        when(secondOrder.getId()).thenReturn(2L);

        var pageable = PageRequest.of(
                0, 100, Sort.by(Sort.Direction.ASC, "id"));

        when(orderRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(
                        List.of(firstOrder, secondOrder)));

        long result = backfillService.backfill();

        assertEquals(2L, result);

        verify(orderFactSyncService).syncOrderById(1L);
        verify(orderFactSyncService).syncOrderById(2L);
    }

    @Test
    void backfillHandlesMultiplePages() {
        Order firstOrder = mock(Order.class);
        Order secondOrder = mock(Order.class);

        when(firstOrder.getId()).thenReturn(10L);
        when(secondOrder.getId()).thenReturn(20L);

        var firstPage = PageRequest.of(
                0, 100, Sort.by(Sort.Direction.ASC, "id"));

        var secondPage = PageRequest.of(
                1, 100, Sort.by(Sort.Direction.ASC, "id"));

        when(orderRepository.findAll(firstPage))
                .thenReturn(new PageImpl<>(
                        List.of(firstOrder), firstPage, 101));

        when(orderRepository.findAll(secondPage))
                .thenReturn(new PageImpl<>(
                        List.of(secondOrder), secondPage, 101));

        long result = backfillService.backfill();

        assertEquals(2L, result);

        verify(orderFactSyncService).syncOrderById(10L);
        verify(orderFactSyncService).syncOrderById(20L);
        verify(orderRepository).findAll(firstPage);
        verify(orderRepository).findAll(secondPage);
    }
}
