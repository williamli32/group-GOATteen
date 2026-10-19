package com.goatteen.trading.marketdata;

import com.goatteen.trading.instrument.Instrument;
import com.goatteen.trading.instrument.InstrumentClass;
import com.goatteen.trading.instrument.InstrumentRepository;
import com.goatteen.trading.marketdata.dto.SimulatedMarketData;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SimulatedQuoteSynchronizerTest {
    @Test
    void recordsOnlyPersistedQuoteIdsAfterQuotesHaveBeenFlushed() {
        var client = mock(SimulatedMarketDataClient.class);
        var instruments = mock(InstrumentRepository.class);
        var quotes = mock(QuoteRepository.class);
        var baselines = mock(DailyMarketBaselineRepository.class);
        var instrument = new Instrument("AAPL", "Apple", InstrumentClass.EQUITY, "NASDAQ", "US", "USD");
        ReflectionTestUtils.setField(instrument, "id", 1L);
        when(instruments.findAll()).thenReturn(List.of(instrument));
        when(instruments.findByTradableTrueOrderBySymbolAscExchangeAsc()).thenReturn(List.of(instrument));
        when(client.getAllMarketData()).thenReturn(List.of(new SimulatedMarketData(
                "EQUITY", "AAPL", "NASDAQ", "US", "USD", "Apple", new BigDecimal("100"),
                null, null, null, null, null, null)));
        when(quotes.saveAllAndFlush(anyList())).thenAnswer(call -> {
            List<Quote> saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved.get(0), "id", 10L);
            return saved;
        });
        new SimulatedQuoteSynchronizer(client, instruments, quotes, baselines).synchronizeQuotes();
        var order = inOrder(quotes, baselines);
        order.verify(quotes).saveAllAndFlush(anyList());
        order.verify(baselines).record(eq(List.of(10L)), any(OffsetDateTime.class));
    }

    @Test
    void anUnavailableFeedDoesNotCreateAnOpeningPrice() {
        var client = mock(SimulatedMarketDataClient.class);
        var instruments = mock(InstrumentRepository.class);
        var quotes = mock(QuoteRepository.class);
        var baselines = mock(DailyMarketBaselineRepository.class);
        when(client.getAllMarketData()).thenThrow(new RestClientException("Simulated outage"));
        new SimulatedQuoteSynchronizer(client, instruments, quotes, baselines).synchronizeQuotes();
        verifyNoInteractions(instruments, quotes, baselines);
    }
}
