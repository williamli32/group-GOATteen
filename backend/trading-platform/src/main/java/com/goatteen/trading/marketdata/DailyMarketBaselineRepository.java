package com.goatteen.trading.marketdata;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class DailyMarketBaselineRepository {
    public record Baseline(Long instrumentId, LocalDate tradingDay, BigDecimal price, Instant observedAt) { }

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public DailyMarketBaselineRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    public void record(List<Long> quoteIds, OffsetDateTime observedAt) {
        if (quoteIds.isEmpty()) return;
        // Read the stored prices so the reference has exactly the same decimal
        // precision as execution quotes. First writer wins, including on restart.
        named.update("""
                INSERT INTO daily_market_baselines(instrument_id,trading_day,reference_price,first_observed_at)
                SELECT DISTINCT ON (q.instrument_id) q.instrument_id,:day,q.last_price,:observedAt
                FROM market_quotes q
                WHERE q.id IN (:quoteIds) AND q.last_price > 0
                ORDER BY q.instrument_id,q.quoted_at,q.id
                ON CONFLICT (instrument_id,trading_day) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("day", Date.valueOf(observedAt.withOffsetSameInstant(ZoneOffset.UTC).toLocalDate()))
                .addValue("observedAt", observedAt)
                .addValue("quoteIds", quoteIds));
    }

    public Map<Long, Baseline> findForDay(LocalDate day) {
        List<Baseline> rows = jdbc.query("""
                SELECT instrument_id,trading_day,reference_price,first_observed_at
                FROM daily_market_baselines WHERE trading_day=?
                """, (row, index) -> new Baseline(
                row.getLong("instrument_id"), row.getDate("trading_day").toLocalDate(),
                row.getBigDecimal("reference_price"), row.getTimestamp("first_observed_at").toInstant()),
                Date.valueOf(day));
        Map<Long, Baseline> result = new LinkedHashMap<>();
        rows.forEach(row -> result.put(row.instrumentId(), row));
        return result;
    }
}
