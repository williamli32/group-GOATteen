package com.goatteen.trading.operations;

import com.goatteen.trading.order.OrderRepository;
import com.goatteen.trading.order.OrderSide;
import com.goatteen.trading.order.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class OperationsOrderSearchServiceTest {

    @Test
    void mapsPersistedOrderSearchRows() {
        OrderRepository repository = mock(OrderRepository.class);
        OperationsOrderSearchService service = new OperationsOrderSearchService(repository);
        LocalDateTime from = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 11, 1, 0, 0);

        when(repository.searchForOperations(
                null, null, null, null, null, null, "FILLED", from, to))
                .thenReturn(List.<Object[]>of(new Object[] {
                        42L, 9L, 7L, "LEAP-0468118A2A1F", "Ada Analyst", 3L, "AAPL",
                        OrderSide.BUY, OrderStatus.FILLED, new BigDecimal("10"), from
                }));

        List<OperationsOrderResponse> result = service.search(
                null, null, null, null, null, null, OrderStatus.FILLED, from, to);

        assertEquals(1, result.size());
        assertEquals(42L, result.get(0).orderId());
        assertEquals("Ada Analyst", result.get(0).clientName());
        assertEquals(7L, result.get(0).clientId());
        assertEquals(9L, result.get(0).accountId());
    }
}
