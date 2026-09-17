package com.goatteen.trading.marketdata;

import com.goatteen.trading.marketdata.dto.SimulatedMarketData;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;

@Component
public class SimulatedMarketDataClient {

    private final RestClient restClient;

    public SimulatedMarketDataClient(
            RestClient.Builder restClientBuilder,

            @Value("${app.market-simulator.base-url:http://localhost:8081}") String baseUrl) {

        this.restClient = restClientBuilder
                .baseUrl(
                        baseUrl)
                .build();
    }

    public List<SimulatedMarketData> getAllMarketData() {

        SimulatedMarketData[] response = restClient
                .get()

                .uri(
                        "/api/market-data")

                .retrieve()

                .body(
                        SimulatedMarketData[].class);

        if (response == null) {
            return List.of();
        }

        return Arrays.asList(
                response);
    }
}