package com.goatteen.trading.audit;

import com.goatteen.trading.account.Account;
import com.goatteen.trading.execution.Fill;
import com.goatteen.trading.instrument.Instrument;
import com.goatteen.trading.order.Order;
import com.goatteen.trading.order.OrderSide;
import com.goatteen.trading.order.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TradeAuditResponseTest {

    private Order order() {
        Account account = mock(Account.class);
        when(account.getId()).thenReturn(10L);
        when(account.getCurrency()).thenReturn("GBP");
        Instrument instrument = mock(Instrument.class);
        when(instrument.getId()).thenReturn(7L);
        when(instrument.getSymbol()).thenReturn("MSFT");
        when(instrument.getCurrency()).thenReturn("USD");
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(2L);
        when(order.getAccount()).thenReturn(account);
        when(order.getInstrument()).thenReturn(instrument);
        when(order.getSide()).thenReturn(OrderSide.BUY);
        when(order.getStatus()).thenReturn(OrderStatus.SUBMITTED);
        when(order.getQuantity()).thenReturn(new BigDecimal("0.004"));
        return order;
    }

    @Test
    void exposesAccountAndInstrumentCurrenciesEvenBeforeExecution() {
        TradeAuditResponse response = TradeAuditResponse.from(
                new TradeAuditTrail(order(), List.of(), null, List.of(), null));

        assertEquals(10L, response.account().accountId());
        assertEquals("GBP", response.account().currency());
        assertEquals(7L, response.instrument().instrumentId());
        assertEquals("MSFT", response.instrument().symbol());
        assertEquals("USD", response.instrument().currency());
        assertNull(response.execution());
    }

    @Test
    void preservesPersistedExecutionPriceAndFractionalQuantity() {
        Fill fill = mock(Fill.class);
        when(fill.getId()).thenReturn(3L);
        when(fill.getFillPrice()).thenReturn(new BigDecimal("0.000123"));
        when(fill.getFillQuantity()).thenReturn(new BigDecimal("0.004"));
        TradeAuditResponse response = TradeAuditResponse.from(
                new TradeAuditTrail(order(), List.of(), fill, List.of(), null));

        assertEquals(new BigDecimal("0.000123"), response.execution().executionPrice());
        assertEquals(new BigDecimal("0.004"), response.execution().fillQuantity());
        assertEquals("USD", response.instrument().currency());
        assertEquals("GBP", response.account().currency());
    }
}
