package com.goatteen.trading.marketdata;

import com.goatteen.trading.instrument.Instrument;
import com.goatteen.trading.instrument.InstrumentRepository;
import com.goatteen.trading.marketdata.dto.LatestQuoteResponse;
import com.goatteen.trading.marketdata.dto.MarketInstrumentResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MarketDataService {

    private final InstrumentRepository instrumentRepository;
    private final QuoteRepository quoteRepository;

    public MarketDataService(
            InstrumentRepository instrumentRepository,
            QuoteRepository quoteRepository) {
        this.instrumentRepository = instrumentRepository;

        this.quoteRepository = quoteRepository;
    }

    @Transactional(readOnly = true)
    public List<MarketInstrumentResponse> getTradableInstruments() {

        return instrumentRepository
                .findByTradableTrueOrderBySymbolAsc()
                .stream()
                .map(this::toInstrumentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public LatestQuoteResponse getLatestQuote(
            Long instrumentId) {

        Instrument instrument = instrumentRepository
                .findById(instrumentId)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Instrument not found"));

        Quote quote = quoteRepository
                .findTopByInstrumentIdOrderByQuotedAtDesc(
                        instrumentId)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "No market quote available for instrument"));

        return toQuoteResponse(
                instrument,
                quote);
    }

    private MarketInstrumentResponse toInstrumentResponse(
            Instrument instrument) {

        LatestQuoteResponse latestQuote = quoteRepository
                .findTopByInstrumentIdOrderByQuotedAtDesc(
                        instrument.getId())
                .map(
                        quote -> toQuoteResponse(
                                instrument,
                                quote))
                .orElse(null);

        return new MarketInstrumentResponse(
                instrument.getId(),
                instrument.getSymbol(),
                instrument.getName(),
                instrument.getInstrumentClass(),
                instrument.getCurrency(),
                instrument.isTradable(),
                latestQuote);
    }

    private LatestQuoteResponse toQuoteResponse(
            Instrument instrument,
            Quote quote) {

        return new LatestQuoteResponse(
                quote.getId(),
                instrument.getId(),
                instrument.getSymbol(),
                quote.getBidPrice(),
                quote.getAskPrice(),
                quote.getLastPrice(),
                quote.getQuotedAt());
    }
}