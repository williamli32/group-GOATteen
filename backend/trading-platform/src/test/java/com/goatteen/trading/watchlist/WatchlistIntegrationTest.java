package com.goatteen.trading.watchlist;

import com.goatteen.trading.support.DatabaseIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class WatchlistIntegrationTest extends DatabaseIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired WatchlistService watchlists;
    private long owner;
    private long otherUser;
    private long firstInstrument;
    private long secondInstrument;

    @BeforeEach
    void createFixtures() {
        owner = user();
        otherUser = user();
        String symbol = "WL" + UUID.randomUUID().toString().substring(0, 12);
        firstInstrument = instrument(symbol, "NASDAQ");
        secondInstrument = instrument(symbol, "LSE");
    }

    @Test
    void savesCommittedMembershipAcrossRepositoryInstancesAndRemovesIdempotently() {
        watchlists.add(owner, firstInstrument);
        assertEquals(firstInstrument, new WatchlistRepository(jdbc).findByUserId(owner).get(0).instrumentId());
        watchlists.remove(owner, firstInstrument);
        watchlists.remove(owner, firstInstrument);
        assertTrue(watchlists.list(owner).isEmpty());
    }

    @Test
    void isolatesOwnersAndDistinguishesTheSameSymbolOnDifferentExchanges() {
        watchlists.add(owner, firstInstrument);
        watchlists.add(owner, secondInstrument);
        watchlists.add(otherUser, secondInstrument);
        assertEquals(List.of("LSE", "NASDAQ"), watchlists.list(owner).stream().map(item -> item.exchange()).toList());
        assertEquals(List.of(secondInstrument), watchlists.list(otherUser).stream().map(item -> item.instrumentId()).toList());
        watchlists.remove(otherUser, firstInstrument);
        assertEquals(2, watchlists.list(owner).size(), "Another user cannot remove the owner's entry");
        watchlists.remove(owner, secondInstrument);
        assertEquals(1, watchlists.list(otherUser).size());
    }

    @Test
    void concurrentAndRepeatedAddsStoreOneEntry() throws Exception {
        var workers = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first = workers.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                watchlists.add(owner, firstInstrument);
                return null;
            });
            var second = workers.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                watchlists.add(owner, firstInstrument);
                return null;
            });
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
            watchlists.add(owner, firstInstrument);
            assertEquals(1, jdbc.queryForObject(
                    "SELECT count(*) FROM watchlist_entries WHERE user_id=? AND instrument_id=?",
                    Integer.class, owner, firstInstrument));
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"USD", "GBP", "INR"})
    void returnsLatestQuoteInTheInstrumentCurrencyAndAllowsMissingQuotes(String currency) {
        jdbc.update("UPDATE instruments SET currency=? WHERE id=?", currency, firstInstrument);
        watchlists.add(owner, firstInstrument);
        watchlists.add(owner, secondInstrument);
        jdbc.update("""
                INSERT INTO market_quotes(instrument_id,bid_price,ask_price,last_price,quoted_at)
                VALUES (?,100,102,101,'2026-10-01 10:00:00'),(?,110,112,111,'2026-10-01 10:00:00')
                """, firstInstrument, firstInstrument);
        // Equal timestamps must have a deterministic latest quote.
        var quoted = watchlists.list(owner).stream()
                .filter(item -> item.instrumentId().equals(firstInstrument)).findFirst().orElseThrow();
        assertEquals(currency, quoted.currency());
        assertEquals(0, new BigDecimal("111").compareTo(quoted.latestQuote().lastPrice()));
        assertNull(watchlists.list(owner).stream()
                .filter(item -> item.instrumentId().equals(secondInstrument)).findFirst().orElseThrow().latestQuote());
    }

    @Test
    void keepsDisabledSavedInstrumentsRemovableAndRejectsInvalidAdditions() {
        watchlists.add(owner, firstInstrument);
        jdbc.update("UPDATE instruments SET tradable=false WHERE id IN (?,?)", firstInstrument, secondInstrument);
        assertFalse(watchlists.list(owner).get(0).tradable());
        assertThrows(IllegalArgumentException.class, () -> watchlists.add(owner, secondInstrument));
        assertThrows(IllegalArgumentException.class, () -> watchlists.add(owner, 0L));
        assertThrows(ResponseStatusException.class, () -> watchlists.add(owner, Long.MAX_VALUE));
        watchlists.remove(owner, firstInstrument);
        assertTrue(watchlists.list(owner).isEmpty());
    }

    @Test
    void changesOnlyWatchlistMembershipAndLeavesTradingStateUntouched() {
        Long client = jdbc.queryForObject(
                "INSERT INTO clients(user_id,first_name,last_name) VALUES (?,'Watchlist','Test') RETURNING id",
                Long.class, owner);
        Long account = jdbc.queryForObject("""
                INSERT INTO accounts(client_id,account_number,cash_balance,currency)
                VALUES (?,?,2500,'GBP') RETURNING id
                """, Long.class, client, "WL-" + UUID.randomUUID());
        watchlists.add(owner, firstInstrument);
        watchlists.list(owner);
        watchlists.remove(owner, firstInstrument);
        assertEquals(0, new BigDecimal("2500").compareTo(jdbc.queryForObject(
                "SELECT cash_balance FROM accounts WHERE id=?", BigDecimal.class, account)));
        for (String table : List.of("orders", "positions", "cash_transactions")) {
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE account_id=?",
                    Integer.class, account));
        }
    }

    private long user() {
        return fixtures.user(jdbc.queryForObject(
                "INSERT INTO users(email,password_hash) VALUES (?, 'watchlist-test-hash') RETURNING id",
                Long.class, UUID.randomUUID() + "@watchlist.test"));
    }

    private long instrument(String symbol, String exchange) {
        return fixtures.instrument(jdbc.queryForObject("""
                INSERT INTO instruments(symbol,name,instrument_class,exchange,country_code,currency)
                VALUES (?,'Watchlist fixture','EQUITY',?,'US','USD') RETURNING id
                """, Long.class, symbol, exchange));
    }
}
