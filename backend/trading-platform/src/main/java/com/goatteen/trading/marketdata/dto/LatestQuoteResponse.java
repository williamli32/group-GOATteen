package com.goatteen.trading.marketdata.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record LatestQuoteResponse(
        Long quoteId,
        Long instrumentId,
        String symbol,
        BigDecimal bidPrice,
        BigDecimal askPrice,
        BigDecimal lastPrice,
        OffsetDateTime quotedAt) {

    /**
     * Market and watchlist quotes are stored as server-local timestamps.
     * Preserve that constructor contract while returning an explicit offset
     * so browsers compare quote ages consistently in every timezone.
     */
    public LatestQuoteResponse(
            Long quoteId,
            Long instrumentId,
            String symbol,
            BigDecimal bidPrice,
            BigDecimal askPrice,
            BigDecimal lastPrice,
            LocalDateTime quotedAt) {
        this(quoteId, instrumentId, symbol, bidPrice, askPrice, lastPrice,
                quotedAt == null ? null : quotedAt.atZone(ZoneId.systemDefault()).toOffsetDateTime());
    }
}
