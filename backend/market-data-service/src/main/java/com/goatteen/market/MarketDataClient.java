package com.goatteen.market;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

@Service
public class MarketDataClient {

    private final String MARKET_DATA_URL = "http://localhost:8081/api/market-data";
    
    @Autowired
    private RestTemplate restTemplate;

    public List<Map> getAllMarketData() {
        return restTemplate.getForObject(MARKET_DATA_URL, List.class);
    }

    public List<Map> getMarketDataByMarket(String market) {
        return restTemplate.getForObject(
            MARKET_DATA_URL + "/market/" + market, 
            List.class
        );
    }

    public Map getSymbolData(String symbol) {
        return restTemplate.getForObject(
            MARKET_DATA_URL + "/symbol/" + symbol, 
            Map.class
        );
    }
}