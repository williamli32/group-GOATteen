package com.goatteen.trading.marketdata.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.goatteen.trading.watchlist.WatchlistRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

class LatestQuoteResponseTest {
    private final TimeZone originalZone = TimeZone.getDefault();
    private final ObjectMapper json = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @AfterEach
    void restoreServerTimezone() {
        TimeZone.setDefault(originalZone);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTC", "America/Chicago", "America/New_York", "Europe/Dublin", "Asia/Kathmandu"})
    void legacyLocalTimestampSerializesAsAnUnambiguousInstant(String zone) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
        LocalDateTime local = LocalDateTime.of(2026, 10, 28, 15, 0, 0);
        LatestQuoteResponse quote = new LatestQuoteResponse(
                7L, 1L, "AAPL", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, local);

        JsonNode document = json.readTree(json.writeValueAsString(quote));
        OffsetDateTime wireTime = OffsetDateTime.parse(document.get("quotedAt").asText());
        assertEquals(local.atZone(ZoneId.of(zone)).toInstant(), wireTime.toInstant());
        assertEquals(quote.quotedAt().toInstant(), wireTime.toInstant());
        assertEquals("AAPL", document.get("symbol").asText());
    }

    @Test
    void explicitOffsetConstructorKeepsTheSuppliedInstant() throws Exception {
        OffsetDateTime time = OffsetDateTime.parse("2026-10-28T10:00:00-05:00");
        LatestQuoteResponse quote = new LatestQuoteResponse(
                7L, 1L, "AAPL", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, time);
        assertEquals(time, quote.quotedAt());
        OffsetDateTime wireTime = OffsetDateTime.parse(
                json.readTree(json.writeValueAsString(quote)).get("quotedAt").asText());
        assertEquals(time.toInstant(), wireTime.toInstant());
    }

    @Test
    void legacyNullTimestampRemainsNull() {
        LatestQuoteResponse quote = new LatestQuoteResponse(
                7L, 1L, "AAPL", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, (LocalDateTime) null);
        assertNull(quote.quotedAt());
    }

    @Test
    void actualWatchlistRowMappingUsesTheCompatibleConstructorAndReturnsUtc() throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        LocalDateTime local = LocalDateTime.of(2026, 10, 28, 15, 0, 0);
        Map<String, Object> columns = Map.ofEntries(
                Map.entry("quote_id", 7L), Map.entry("id", 1L),
                Map.entry("symbol", "AAPL"), Map.entry("name", "Apple"),
                Map.entry("instrument_class", "EQUITY"), Map.entry("exchange", "NASDAQ"),
                Map.entry("country_code", "US"), Map.entry("currency", "USD"),
                Map.entry("tradable", true), Map.entry("bid_price", BigDecimal.TEN),
                Map.entry("ask_price", BigDecimal.TEN), Map.entry("last_price", BigDecimal.TEN),
                Map.entry("quoted_at", Timestamp.valueOf(local)));
        ResultSet row = (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class},
                (proxy, method, arguments) -> {
                    if (arguments != null && arguments.length > 0
                            && arguments[0] instanceof String column && columns.containsKey(column)) {
                        return columns.get(column);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        // Exercise the real watchlist row mapper without a database or JVM agent.
        JdbcTemplate jdbc = new JdbcTemplate() {
            @Override
            public <T> List<T> query(String sql, RowMapper<T> mapper, Object... arguments) {
                assertEquals(42L, arguments[0]);
                try {
                    return List.of(mapper.mapRow(row, 0));
                } catch (SQLException exception) {
                    throw new AssertionError(exception);
                }
            }
        };

        MarketInstrumentResponse instrument = new WatchlistRepository(jdbc).findByUserId(42L).get(0);
        String wireTime = json.readTree(json.writeValueAsString(instrument))
                .get("latestQuote").get("quotedAt").asText();
        assertEquals(OffsetDateTime.parse("2026-10-28T15:00:00Z").toInstant(),
                OffsetDateTime.parse(wireTime).toInstant());
    }
}
