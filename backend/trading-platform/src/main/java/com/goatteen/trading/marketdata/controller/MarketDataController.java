package com.goatteen.trading.marketdata.controller;

import com.goatteen.trading.marketdata.MarketDataService;
import com.goatteen.trading.marketdata.dto.LatestQuoteResponse;
import com.goatteen.trading.marketdata.dto.MarketInstrumentResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/market")
public class MarketDataController {

    private final MarketDataService marketDataService;

    public MarketDataController(
            MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @GetMapping("/instruments")
    public ResponseEntity<List<MarketInstrumentResponse>> getTradableInstruments() {

        return ResponseEntity.ok(
                marketDataService
                        .getTradableInstruments());
    }

    @GetMapping("/instruments/{instrumentId}/quote")
    public ResponseEntity<LatestQuoteResponse> getLatestQuote(
            @PathVariable Long instrumentId) {

        try {

            return ResponseEntity.ok(
                    marketDataService
                            .getLatestQuote(
                                    instrumentId));

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .notFound()
                    .build();

        } catch (IllegalStateException e) {

            return ResponseEntity
                    .noContent()
                    .build();
        }
    }
}