package com.goatteen.trading.fx;

import com.goatteen.trading.order.OrderSide;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class FxRateServiceTest {
    private final FxRateService rates = new FxRateService(RestClient.builder());

    @Test
    void sameCurrencyNeverNeedsExternalRequest() {
        var quote = rates.quote("GBP", "GBP");
        assertEquals(0, BigDecimal.ONE.compareTo(quote.rate()));
        assertEquals("SAME_CURRENCY", quote.provider());
        assertEquals(new BigDecimal("100.00"), rates.settlementAmount(
                new BigDecimal("100"), quote, OrderSide.BUY));
    }

    @Test
    void usdStockSettlesInGbpAtRateApplied() {
        var quote = new FxRateService.FxQuote("USD", "GBP",
                new BigDecimal("0.7502"), LocalDate.of(2026, 10, 19), "TEST");
        assertEquals(new BigDecimal("75.02"), rates.settlementAmount(
                new BigDecimal("100"), quote, OrderSide.BUY));
    }

    @Test
    void buysRoundUpAndSellsRoundDown() {
        var quote = new FxRateService.FxQuote("USD", "GBP",
                new BigDecimal("0.75015"), LocalDate.of(2026, 10, 19), "TEST");
        assertEquals(new BigDecimal("75.02"), rates.settlementAmount(
                new BigDecimal("100"), quote, OrderSide.BUY));
        assertEquals(new BigDecimal("75.01"), rates.settlementAmount(
                new BigDecimal("100"), quote, OrderSide.SELL));
    }
}
