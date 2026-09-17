package com.goatteen.market.service;

import com.goatteen.market.dto.MarketData;
import com.goatteen.market.loader.MarketDataCsvLoader;

import jakarta.annotation.PostConstruct;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class MockMarketDataService {

    /*
     * Current simulated market state.
     *
     * ConcurrentHashMap is used because scheduled
     * price updates and HTTP requests may access
     * market data at the same time.
     */
    private final Map<String, MarketData> marketDataCache = new ConcurrentHashMap<>();

    private final MarketDataCsvLoader csvLoader;

    public MockMarketDataService(
            MarketDataCsvLoader csvLoader) {

        this.csvLoader = csvLoader;
    }

    /*
     * Load deterministic starting prices from the
     * CSV when the service starts.
     */
    @PostConstruct
    public void loadData() {

        List<MarketData> data = csvLoader.loadMarketData();

        for (MarketData marketData : data) {

            marketDataCache.put(
                    marketData.getSymbol(),
                    marketData);
        }

        System.out.println(
                "Simulated market initialized with "
                        + marketDataCache.size()
                        + " instruments");
    }

    /*
     * Advance the simulated market every 3 seconds.
     *
     * The next price is calculated from the CURRENT
     * price, not from the original CSV price.
     *
     * This creates an evolving random walk:
     *
     * 100.00
     * 100.12
     * 100.05
     * 100.23
     * ...
     */
    @Scheduled(fixedRate = 3000)
    public void updateMarketPrices() {

        marketDataCache.replaceAll(
                (
                        symbol,
                        current) -> generateNextPrice(
                                current));
    }

    public MarketData getMarketData(
            String symbol) {

        MarketData data = marketDataCache.get(
                symbol);

        if (data == null) {
            return null;
        }

        return copyMarketData(
                data);
    }

    public List<MarketData> getMarketDataByMarket(
            String market) {

        return marketDataCache
                .values()
                .stream()

                .filter(
                        data -> data
                                .getAssetType()
                                .equalsIgnoreCase(
                                        market)
                                ||
                                data
                                        .getCountryCode()
                                        .equalsIgnoreCase(
                                                market)
                                ||
                                data
                                        .getMarket()
                                        .equalsIgnoreCase(
                                                market))

                .sorted(
                        Comparator.comparing(
                                MarketData::getSymbol))

                .map(
                        this::copyMarketData)

                .toList();
    }

    public List<MarketData> getAllMarketData() {

        return marketDataCache
                .values()
                .stream()

                .sorted(
                        Comparator.comparing(
                                MarketData::getSymbol))

                .map(
                        this::copyMarketData)

                .toList();
    }

    private MarketData generateNextPrice(
            MarketData current) {

        /*
         * Maximum simulated movement for one tick:
         *
         * +/- 0.25%
         *
         * This is intentionally modest so the UI
         * looks live without prices jumping wildly.
         */
        double movementPercent = ThreadLocalRandom
                .current()
                .nextDouble(
                        -0.0025,
                        0.0025);

        BigDecimal currentPrice = current.getPrice();

        BigDecimal movement = currentPrice
                .multiply(
                        BigDecimal.valueOf(
                                movementPercent));

        BigDecimal nextPrice = currentPrice
                .add(
                        movement)
                .max(
                        new BigDecimal(
                                "0.00000001"))
                .setScale(
                        8,
                        RoundingMode.HALF_UP);

        BigDecimal actualChange = nextPrice
                .subtract(
                        currentPrice)
                .setScale(
                        8,
                        RoundingMode.HALF_UP);

        BigDecimal actualChangePercent;

        if (currentPrice.compareTo(
                BigDecimal.ZERO) == 0) {

            actualChangePercent = BigDecimal.ZERO;

        } else {

            actualChangePercent = actualChange
                    .divide(
                            currentPrice,
                            10,
                            RoundingMode.HALF_UP)
                    .multiply(
                            BigDecimal.valueOf(
                                    100))
                    .setScale(
                            6,
                            RoundingMode.HALF_UP);
        }

        MarketData updated = copyMarketData(
                current);

        updated.setPrice(
                nextPrice);

        updated.setChange(
                actualChange);

        updated.setChangePercent(
                actualChangePercent);

        updated.setHigh(
                current
                        .getHigh()
                        .max(
                                nextPrice));

        updated.setLow(
                current
                        .getLow()
                        .min(
                                nextPrice));

        updated.setTimestamp(
                LocalDateTime.now());

        return updated;
    }

    /*
     * Return snapshots rather than exposing the
     * mutable objects stored inside the simulator.
     */
    private MarketData copyMarketData(
            MarketData source) {

        MarketData copy = new MarketData();

        copy.setAssetType(
                source.getAssetType());

        copy.setSymbol(
                source.getSymbol());

        copy.setExchange(
                source.getExchange());

        copy.setCountryCode(
                source.getCountryCode());

        copy.setCurrency(
                source.getCurrency());

        copy.setMarket(
                source.getMarket());

        copy.setName(
                source.getName());

        copy.setPrice(
                source.getPrice());

        copy.setChange(
                source.getChange());

        copy.setChangePercent(
                source.getChangePercent());

        copy.setHigh(
                source.getHigh());

        copy.setLow(
                source.getLow());

        copy.setVolume(
                source.getVolume());

        copy.setTimestamp(
                source.getTimestamp());

        return copy;
    }
}