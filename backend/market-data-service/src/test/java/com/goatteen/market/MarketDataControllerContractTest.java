package com.goatteen.market;

import com.goatteen.market.controller.MarketDataController;
import com.goatteen.market.dto.MarketData;
import com.goatteen.market.service.MockMarketDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MarketDataController.class)
class MarketDataControllerContractTest {
    @Autowired MockMvc mvc;
    @MockitoBean MockMarketDataService market;

    @Test void quotesKeepIsoTimestampsNumericPricesAndInstrumentCurrency() throws Exception {
        MarketData quote = new MarketData();
        quote.setSymbol("RELIANCE.NS");
        quote.setCurrency("INR");
        quote.setPrice(new BigDecimal("123.45"));
        quote.setTimestamp(LocalDateTime.of(2026, 10, 26, 12, 30, 45));
        when(market.getAllMarketData()).thenReturn(List.of(quote));

        mvc.perform(get("/api/market-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("RELIANCE.NS"))
                .andExpect(jsonPath("$[0].currency").value("INR"))
                .andExpect(jsonPath("$[0].price").value(123.45))
                .andExpect(jsonPath("$[0].timestamp").value("2026-10-26T12:30:45"));
    }
}
