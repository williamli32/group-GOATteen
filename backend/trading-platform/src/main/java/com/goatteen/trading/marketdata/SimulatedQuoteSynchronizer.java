package com.goatteen.trading.marketdata;

import com.goatteen.trading.instrument.Instrument;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "app.market-simulator", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SimulatedQuoteSynchronizer {

        private static final Logger logger = LoggerFactory.getLogger(
                        SimulatedQuoteSynchronizer.class);

        /*
         * Trading symbols and simulator symbols are
         * not identical for every asset.
         *
         * The trading application keeps its canonical
         * Sprint 3 symbols while this adapter translates
         * them to the simulator dataset.
         */

        private static final BigDecimal HALF_SPREAD_RATE = new BigDecimal(
                        "0.0001");

        private static final BigDecimal MINIMUM_PRICE = new BigDecimal(
                        "0.00000001");

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

        @Scheduled(fixedDelayString = "${app.market-simulator.sync-ms:3000}",

                        initialDelayString = "${app.market-simulator.initial-delay-ms:1000}")
        @Transactional
        public void synchronizeQuotes() {

                List<SimulatedMarketData> feed;

                try {

                        feed = simulatorClient
                                        .getAllMarketData();

                } catch (RestClientException e) {

                        /*
                         * The trading platform must remain usable
                         * if the simulator is temporarily offline.
                         *
                         * Existing persisted quotes remain available.
                         */
                        logger.warn(
                                        "Unable to reach simulated market-data service: {}",
                                        e.getMessage());

                        return;
                }

                Map<String, SimulatedMarketData> feedBySymbol = new HashMap<>();

                for (SimulatedMarketData marketData : feed) {

                        if (marketData.symbol() != null) {

                                feedBySymbol.put(
                                                marketData
                                                                .symbol()
                                                                .toUpperCase(),

                                                marketData);
                        }
                }

                List<Instrument> instruments = instrumentRepository
                                .findByTradableTrueOrderBySymbolAsc();

                int synchronizedCount = 0;

                for (Instrument instrument : instruments) {

                        SimulatedMarketData marketData = feedBySymbol.get(
                                        instrument
                                                        .getSymbol()
                                                        .toUpperCase());

                        if (marketData == null
                                        ||
                                        marketData.price() == null
                                        ||
                                        marketData.price()
                                                        .compareTo(
                                                                        BigDecimal.ZERO) <= 0) {

                                continue;
                        }

                        Quote quote = createQuote(
                                        instrument,
                                        marketData
                                                        .price());

                        quoteRepository.save(
                                        quote);

                        synchronizedCount++;
                }

                logger.debug(
                                "Stored {} simulated market quotes",
                                synchronizedCount);
        }

        private Quote createQuote(
                        Instrument instrument,
                        BigDecimal lastPrice) {

                BigDecimal normalizedLastPrice = lastPrice
                                .max(
                                                MINIMUM_PRICE)
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