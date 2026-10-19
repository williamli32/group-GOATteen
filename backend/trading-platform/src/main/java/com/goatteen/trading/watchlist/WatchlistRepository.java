package com.goatteen.trading.watchlist;

import com.goatteen.trading.instrument.InstrumentClass;
import com.goatteen.trading.marketdata.dto.LatestQuoteResponse;
import com.goatteen.trading.marketdata.dto.MarketInstrumentResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class WatchlistRepository {
    private final JdbcTemplate jdbc;

    public WatchlistRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<MarketInstrumentResponse> findByUserId(Long userId) {
        // Keep saved instruments visible if trading is later disabled. One
        // indexed quote lookup per entry also handles instruments without quotes.
        return jdbc.query("""
                SELECT i.id, i.symbol, i.name, i.instrument_class, i.exchange,
                       i.country_code, i.currency, i.tradable,
                       q.id AS quote_id, q.bid_price, q.ask_price, q.last_price, q.quoted_at
                FROM watchlist_entries w
                JOIN instruments i ON i.id = w.instrument_id
                LEFT JOIN LATERAL (
                    SELECT id, bid_price, ask_price, last_price, quoted_at
                    FROM market_quotes
                    WHERE instrument_id = i.id
                    ORDER BY quoted_at DESC, id DESC
                    LIMIT 1
                ) q ON TRUE
                WHERE w.user_id = ?
                ORDER BY i.symbol, i.exchange, i.id
                """, (row, index) -> {
            Long quoteId = row.getObject("quote_id", Long.class);
            LatestQuoteResponse quote = quoteId == null ? null : new LatestQuoteResponse(
                    quoteId, row.getLong("id"), row.getString("symbol"),
                    row.getBigDecimal("bid_price"), row.getBigDecimal("ask_price"),
                    row.getBigDecimal("last_price"), row.getTimestamp("quoted_at").toLocalDateTime());
            return new MarketInstrumentResponse(
                    row.getLong("id"), row.getString("symbol"), row.getString("name"),
                    InstrumentClass.valueOf(row.getString("instrument_class")),
                    row.getString("exchange"), row.getString("country_code"),
                    row.getString("currency"), row.getBoolean("tradable"), quote);
        }, userId);
    }

    public void addIfAbsent(Long userId, Long instrumentId) {
        // The composite primary key handles duplicate and concurrent retries
        // without aborting the surrounding transaction.
        jdbc.update("""
                INSERT INTO watchlist_entries(user_id, instrument_id)
                VALUES (?, ?)
                ON CONFLICT (user_id, instrument_id) DO NOTHING
                """, userId, instrumentId);
    }

    public void remove(Long userId, Long instrumentId) {
        jdbc.update("DELETE FROM watchlist_entries WHERE user_id = ? AND instrument_id = ?",
                userId, instrumentId);
    }
}
