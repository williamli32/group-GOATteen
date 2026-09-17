package com.goatteen.market.controller;

import com.goatteen.market.dto.MarketData;
import com.goatteen.market.service.MockMarketDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/market-data")
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:54565"})
public class MarketDataController {

    @Autowired
    private MockMarketDataService marketDataService;

    /**
     * Get all market data across all markets
     */
    @GetMapping
    public List<MarketData> getAllMarketData() {
        return marketDataService.getAllMarketData();
    }

    /**
     * Get market data by specific market
     * @param market - US, UK, INDIA, CRYPTO, FOREX
     */
    @GetMapping("/market/{market}")
    public List<MarketData> getMarketDataByMarket(@PathVariable String market) {
        return marketDataService.getMarketDataByMarket(market);
    }

    /**
     * Get specific symbol data
     * @param symbol - e.g., AAPL, BTC, EURUSD
     */
    @GetMapping("/symbol/{symbol}")
    public MarketData getMarketData(@PathVariable String symbol) {
        return marketDataService.getMarketData(symbol);
    }
}