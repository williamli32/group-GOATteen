package com.goatteen.trading.marketdata.dto;

import com.goatteen.trading.instrument.InstrumentClass;

public record MarketInstrumentResponse(
                Long instrumentId,
                String symbol,
                String name,
                InstrumentClass instrumentClass,
                String exchange,
                String countryCode,
                String currency,
                boolean tradable,
                LatestQuoteResponse latestQuote) {
}