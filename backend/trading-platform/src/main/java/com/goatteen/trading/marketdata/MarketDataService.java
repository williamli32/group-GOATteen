package com.goatteen.trading.marketdata;

import com.goatteen.trading.instrument.Instrument;
import com.goatteen.trading.instrument.InstrumentRepository;
import com.goatteen.trading.marketdata.dto.LatestQuoteResponse;
import com.goatteen.trading.marketdata.dto.MarketInstrumentResponse;
import com.goatteen.trading.marketdata.dto.DailyPriceChange;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Service
public class MarketDataService {

        private final InstrumentRepository instrumentRepository;
        private final QuoteRepository quoteRepository;
        private final DailyMarketBaselineRepository dailyBaselines;

        public MarketDataService(
                        InstrumentRepository instrumentRepository,
                        QuoteRepository quoteRepository,
                        DailyMarketBaselineRepository dailyBaselines) {
                this.instrumentRepository = instrumentRepository;

                this.quoteRepository = quoteRepository;
                this.dailyBaselines = dailyBaselines;
        }

        @Transactional(readOnly = true)
        public List<MarketInstrumentResponse> getTradableInstruments() {

                var baselines = dailyBaselines.findForDay(LocalDate.now(ZoneOffset.UTC));

                return instrumentRepository
                                .findByTradableTrueOrderBySymbolAscExchangeAsc()
                                .stream()
                                .map(instrument -> toInstrumentResponse(instrument, baselines.get(instrument.getId())))
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
                        Instrument instrument, DailyMarketBaselineRepository.Baseline baseline) {

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
                                instrument.getExchange(),
                                instrument.getCountryCode(),
                                instrument.getCurrency(),
                                instrument.isTradable(),
                                latestQuote,
                                baseline == null || latestQuote == null ? null : DailyPriceChange.calculate(
                                        latestQuote.lastPrice(), baseline.price(), baseline.tradingDay(), baseline.observedAt()));
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
