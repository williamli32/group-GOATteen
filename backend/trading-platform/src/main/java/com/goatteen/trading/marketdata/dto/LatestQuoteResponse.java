package com.goatteen.trading.marketdata.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LatestQuoteResponse(
        Long quoteId,
        Long instrumentId,
        String symbol,
        BigDecimal bidPrice,
        BigDecimal askPrice,
        BigDecimal lastPrice,
        LocalDateTime quotedAt) {
}