package com.goatteen.trading.marketdata.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SimulatedMarketData(

        String assetType,

        String symbol,

        String exchange,

        String countryCode,

        String currency,

        String name,

        BigDecimal price,

        BigDecimal change,

        BigDecimal changePercent,

        BigDecimal high,

        BigDecimal low,

        Long volume,

        LocalDateTime timestamp

) {
}