package com.goatteen.trading.marketdata;

import com.goatteen.trading.support.DatabaseIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class DailyMarketBaselineIntegrationTest extends DatabaseIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired DailyMarketBaselineRepository baselines;
    @Autowired MarketDataService market;
    @Autowired QuoteRepository quotes;
    @Autowired PlatformTransactionManager transactions;
    private long instrument;

    @BeforeEach
    void createInstrument() {
        instrument = fixtures.instrument(jdbc.queryForObject("""
                INSERT INTO instruments(symbol,name,instrument_class,exchange,country_code,currency)
                VALUES (?,'Daily change fixture','EQUITY','NASDAQ','US','USD') RETURNING id
                """, Long.class, "DAY" + UUID.randomUUID().toString().substring(0, 10)));
    }

    @Test
    void keepsTheFirstReferenceAfterRepeatedTicksCleanupAndARepositoryRecreation() {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        long first = quote("100", LocalDateTime.now().minusMinutes(10));
        baselines.record(List.of(first), now);
        long latest = quote("110", LocalDateTime.now());
        baselines.record(List.of(latest), now.plusSeconds(3));
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                quotes.deleteUnusedHistoricalQuotesBefore(LocalDateTime.now().minusMinutes(2)));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM market_quotes WHERE id=?", Integer.class, first));
        var restored = new DailyMarketBaselineRepository(jdbc).findForDay(now.toLocalDate()).get(instrument);
        assertEquals(0, new BigDecimal("100").compareTo(restored.price()));
        var result = market.getTradableInstruments().stream()
                .filter(item -> item.instrumentId().equals(instrument)).findFirst().orElseThrow();
        assertNotNull(result.dayChange());
        assertEquals(new BigDecimal("10.00"), result.dayChange().percent());
    }

    @Test
    void startsANewReferenceForEachUtcDayAndDoesNotReuseYesterdaysChange() {
        var today = LocalDate.now(ZoneOffset.UTC);
        long first = quote("100", LocalDateTime.now().minusMinutes(1));
        baselines.record(List.of(first), today.minusDays(1).atTime(23, 59).atOffset(ZoneOffset.UTC));
        var before = market.getTradableInstruments().stream()
                .filter(item -> item.instrumentId().equals(instrument)).findFirst().orElseThrow();
        assertNull(before.dayChange());
        long next = quote("120", LocalDateTime.now());
        baselines.record(List.of(next), today.atStartOfDay().atOffset(ZoneOffset.UTC));
        assertEquals(2, jdbc.queryForObject(
                "SELECT count(*) FROM daily_market_baselines WHERE instrument_id=?", Integer.class, instrument));
        var current = baselines.findForDay(today).get(instrument);
        assertEquals(0, new BigDecimal("120").compareTo(current.price()));
    }

    @Test
    void usesTheUtcDateRegardlessOfTheSuppliedOffsetAndDoesNotCreateRowsForMissingQuotes() {
        long first = quote("99", LocalDateTime.now());
        var observed = OffsetDateTime.parse("2026-10-20T01:00:00+05:00");
        baselines.record(List.of(first), observed);
        assertTrue(baselines.findForDay(LocalDate.of(2026, 10, 19)).containsKey(instrument));
        assertFalse(baselines.findForDay(LocalDate.of(2026, 10, 20)).containsKey(instrument));
        baselines.record(List.of(), observed);
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM daily_market_baselines WHERE instrument_id=?", Integer.class, instrument));
    }

    private long quote(String price, LocalDateTime time) {
        return jdbc.queryForObject("""
                INSERT INTO market_quotes(instrument_id,bid_price,ask_price,last_price,quoted_at)
                VALUES (?,?,?,?,?) RETURNING id
                """, Long.class, instrument, new BigDecimal(price), new BigDecimal(price),
                new BigDecimal(price), Timestamp.valueOf(time));
    }
}
