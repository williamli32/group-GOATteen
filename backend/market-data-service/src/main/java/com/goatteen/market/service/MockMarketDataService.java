package com.goatteen.market.service;

import com.goatteen.market.dto.MarketData;
import com.goatteen.market.loader.MarketDataCsvLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class MockMarketDataService {

    private final Map<String, MarketData> marketDataCache = new HashMap<>();
    private final ThreadLocalRandom random = ThreadLocalRandom.current();

    @Autowired
    private MarketDataCsvLoader csvLoader;

    public MockMarketDataService() {
    }

    // Initialize cache after the bean is created
    @PostConstruct
    public void loadData() {
        List<MarketData> data = csvLoader.loadMarketData();
        data.forEach(d -> marketDataCache.put(d.getSymbol(), d));
        System.out.println("Market data cache initialized with " + marketDataCache.size() + " records");
    }

    public MarketData getMarketData(String symbol) {
        MarketData data = marketDataCache.get(symbol);
        if (data != null) {
            return generateLiveUpdate(data);
        }
        return null;
    }

    public List<MarketData> getMarketDataByMarket(String market) {
        return marketDataCache.values().stream()
                .filter(d -> d.getMarket().equalsIgnoreCase(market))
                .map(this::generateLiveUpdate)
                .toList();
    }

    public List<MarketData> getAllMarketData() {
        return marketDataCache.values().stream()
                .map(this::generateLiveUpdate)
                .toList();
    }

    /**
     * Simulate live price movement
     */
    private MarketData generateLiveUpdate(MarketData original) {
        // Create a copy with simulated price movement
        MarketData updated = new MarketData(
                original.getSymbol(),
                original.getMarket(),
                original.getName(),
                original.getPrice(),
                original.getChange(),
                original.getChangePercent(),
                original.getHigh(),
                original.getLow(),
                original.getVolume(),
                LocalDateTime.now()
        );

        // Simulate price change (±0.5% to ±2%)
        double changePercent = (random.nextDouble() - 0.5) * 0.04;
        BigDecimal change = updated.getPrice().multiply(BigDecimal.valueOf(changePercent));
        
        updated.setPrice(updated.getPrice().add(change));
        updated.setChange(change);
        updated.setChangePercent(BigDecimal.valueOf(changePercent * 100));
        
        return updated;
    }
}