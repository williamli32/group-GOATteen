package com.goatteen.trading.marketdata;

import com.goatteen.trading.instrument.Instrument;
import com.goatteen.trading.instrument.InstrumentClass;
import com.goatteen.trading.instrument.InstrumentRepository;

import com.goatteen.trading.marketdata.dto.SimulatedMarketData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import org.springframework.scheduling.annotation.Scheduled;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.time.LocalDateTime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "app.market-simulator", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SimulatedQuoteSynchronizer {

        private static final Logger logger = LoggerFactory.getLogger(
                        SimulatedQuoteSynchronizer.class);

        private static final BigDecimal HALF_SPREAD_RATE = new BigDecimal("0.0001");

        private static final BigDecimal MINIMUM_PRICE = new BigDecimal("0.00000001");

        private final SimulatedMarketDataClient simulatorClient;

        private final InstrumentRepository instrumentRepository;

        private final QuoteRepository quoteRepository;

        public SimulatedQuoteSynchronizer(
                        SimulatedMarketDataClient simulatorClient,
                        InstrumentRepository instrumentRepository,
                        QuoteRepository quoteRepository) {

                this.simulatorClient = simulatorClient;
                this.instrumentRepository = instrumentRepository;
                this.quoteRepository = quoteRepository;
        }

        @Scheduled(fixedDelayString = "${app.market-simulator.sync-ms:3000}", initialDelayString = "${app.market-simulator.initial-delay-ms:1000}")
        @Transactional
        public void synchronizeQuotes() {

                List<SimulatedMarketData> feed;

                try {

                        feed = simulatorClient.getAllMarketData();

                } catch (RestClientException e) {

                        /*
                         * Keep the trading platform usable when the
                         * simulator is temporarily unavailable.
                         */
                        logger.warn(
                                        "Unable to reach simulated market-data service: {}",
                                        e.getMessage());

                        return;
                }

                /*
                 * A symbol alone is not a unique instrument.
                 *
                 * Examples:
                 * RIO|LSE
                 * RIO|ASX
                 *
                 * Therefore the simulator and trading platform
                 * identify instruments by symbol + exchange.
                 */
                Map<String, SimulatedMarketData> feedByInstrument = new HashMap<>();

                for (SimulatedMarketData marketData : feed) {

                        if (!isValidInstrument(marketData)) {
                                continue;
                        }

                        feedByInstrument.put(
                                        instrumentKey(
                                                        marketData.symbol(),
                                                        marketData.exchange()),
                                        marketData);
                }

                synchronizeInstrumentCatalog(
                                feedByInstrument);

                List<Instrument> instruments = instrumentRepository
                                .findByTradableTrueOrderBySymbolAscExchangeAsc();

                List<Quote> quotes = new ArrayList<>();

                for (Instrument instrument : instruments) {

                        SimulatedMarketData marketData = feedByInstrument.get(
                                        instrumentKey(
                                                        instrument.getSymbol(),
                                                        instrument.getExchange()));

                        if (marketData == null
                                        || marketData.price() == null
                                        || marketData.price()
                                                        .compareTo(BigDecimal.ZERO) <= 0) {

                                continue;
                        }

                        quotes.add(
                                        createQuote(
                                                        instrument,
                                                        marketData.price()));
                }

                quoteRepository.saveAll(
                                quotes);

                logger.debug(
                                "Stored {} simulated market quotes for {} instruments",
                                quotes.size(),
                                instruments.size());
        }

        private void synchronizeInstrumentCatalog(
                        Map<String, SimulatedMarketData> feedByInstrument) {

                List<Instrument> existingInstruments = instrumentRepository.findAll();

                Map<String, Instrument> existingByKey = new HashMap<>();

                for (Instrument instrument : existingInstruments) {

                        existingByKey.put(
                                        instrumentKey(
                                                        instrument.getSymbol(),
                                                        instrument.getExchange()),
                                        instrument);
                }

                List<Instrument> missingInstruments = new ArrayList<>();

                for (SimulatedMarketData marketData : feedByInstrument.values()) {

                        String key = instrumentKey(
                                        marketData.symbol(),
                                        marketData.exchange());

                        if (existingByKey.containsKey(key)) {
                                continue;
                        }

                        InstrumentClass instrumentClass = toInstrumentClass(
                                        marketData.assetType());

                        if (instrumentClass == null) {

                                logger.warn(
                                                "Skipping unsupported simulated instrument {}|{} with asset type {}",
                                                marketData.symbol(),
                                                marketData.exchange(),
                                                marketData.assetType());

                                continue;
                        }

                        Instrument instrument = new Instrument(
                                        marketData.symbol(),
                                        instrumentName(marketData),
                                        instrumentClass,
                                        marketData.exchange(),
                                        marketData.countryCode(),
                                        marketData.currency());

                        missingInstruments.add(
                                        instrument);
                }

                if (!missingInstruments.isEmpty()) {

                        instrumentRepository.saveAll(
                                        missingInstruments);

                        logger.info(
                                        "Imported {} simulated instruments into trading catalog",
                                        missingInstruments.size());
                }
        }

        private InstrumentClass toInstrumentClass(
                        String assetType) {

                if (assetType == null) {
                        return null;
                }

                return switch (assetType
                                .trim()
                                .toUpperCase(Locale.ROOT)) {

                        case "EQUITY" ->
                                InstrumentClass.EQUITY;

                        case "FOREX" ->
                                InstrumentClass.FOREX;

                        case "CRYPTO" ->
                                InstrumentClass.CRYPTO;

                        case "COMMODITY" ->
                                InstrumentClass.COMMODITY;

                        default ->
                                null;
                };
        }

        private boolean isValidInstrument(
                        SimulatedMarketData marketData) {

                return marketData != null
                                && marketData.symbol() != null
                                && !marketData.symbol().isBlank()
                                && marketData.exchange() != null
                                && !marketData.exchange().isBlank()
                                && marketData.countryCode() != null
                                && !marketData.countryCode().isBlank()
                                && marketData.currency() != null
                                && !marketData.currency().isBlank();
        }

        private String instrumentName(
                        SimulatedMarketData marketData) {

                if (marketData.name() == null
                                || marketData.name().isBlank()) {

                        return marketData.symbol();
                }

                return marketData.name();
        }

        private String instrumentKey(
                        String symbol,
                        String exchange) {

                return (symbol.trim()
                                + "|"
                                + exchange.trim()).toUpperCase(
                                                Locale.ROOT);
        }

        private Quote createQuote(
                        Instrument instrument,
                        BigDecimal lastPrice) {

                BigDecimal normalizedLastPrice = lastPrice
                                .max(MINIMUM_PRICE)
                                .setScale(
                                                8,
                                                RoundingMode.HALF_UP);

                BigDecimal halfSpread = normalizedLastPrice
                                .multiply(
                                                HALF_SPREAD_RATE)
                                .max(
                                                MINIMUM_PRICE)
                                .setScale(
                                                8,
                                                RoundingMode.HALF_UP);

                BigDecimal bidPrice = normalizedLastPrice
                                .subtract(
                                                halfSpread)
                                .max(
                                                MINIMUM_PRICE)
                                .setScale(
                                                8,
                                                RoundingMode.HALF_UP);

                BigDecimal askPrice = normalizedLastPrice
                                .add(
                                                halfSpread)
                                .setScale(
                                                8,
                                                RoundingMode.HALF_UP);

                return new Quote(
                                instrument,
                                bidPrice,
                                askPrice,
                                normalizedLastPrice,
                                LocalDateTime.now());
        }
}