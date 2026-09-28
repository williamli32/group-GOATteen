package com.goatteen.market.controller;

import com.goatteen.market.dto.HistoricalPrice;
import com.goatteen.market.dto.MarketData;
import com.goatteen.market.service.MockMarketDataService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@CrossOrigin(
        origins = "http://localhost:4200",
        allowedHeaders = "*",
        methods = {
                RequestMethod.GET,
                RequestMethod.OPTIONS
        }
)
@RequestMapping("/api/market-data")
public class MarketDataController {

    private final MockMarketDataService marketDataService;

    public MarketDataController(
            MockMarketDataService marketDataService) {

        this.marketDataService = marketDataService;
    }

    /**
     * Get all market data across all markets.
     */
    @GetMapping
    public List<MarketData> getAllMarketData() {

        return marketDataService.getAllMarketData();
    }

    /**
     * Get market data by specific market.
     *
     * Examples:
     * US, UK, INDIA, CRYPTO, FOREX
     */
    @GetMapping("/market/{market}")
    public List<MarketData> getMarketDataByMarket(
            @PathVariable String market) {

        return marketDataService
                .getMarketDataByMarket(market);
    }

    /**
     * Get current data for one symbol.
     *
     * Examples:
     * AAPL, BTC, EURUSD
     */
    @GetMapping("/symbol/{symbol}")
    public MarketData getMarketData(
            @PathVariable String symbol) {

        return marketDataService
                .getMarketData(symbol);
    }

    /**
     * Get historical OHLCV data for one symbol.
     *
     * Example:
     * /api/market-data/history/AAPL
     */
    @GetMapping("/history/{symbol}")
    public List<HistoricalPrice> getHistoricalData(
            @PathVariable String symbol) {

        return marketDataService
                .getHistoricalData(symbol);
    }
}