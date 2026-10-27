package com.goatteen.trading.alerts;

import com.goatteen.trading.support.DatabaseIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PriceAlertIntegrationTest extends DatabaseIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired PriceAlertEvaluator evaluator;
    private long owner;
    private long instrument;

    @BeforeEach
    void createFixtures() {
        owner = fixtures.user(jdbc.queryForObject("INSERT INTO users(email,password_hash) VALUES (?, 'test-hash') RETURNING id",
                Long.class, UUID.randomUUID() + "@alerts.test"));
        instrument = fixtures.instrument(jdbc.queryForObject("""
                INSERT INTO instruments(symbol,name,instrument_class,exchange,country_code,currency)
                VALUES (?,'Alert fixture','EQUITY','NASDAQ','US','USD') RETURNING id
                """, Long.class, "PA" + UUID.randomUUID().toString().substring(0, 12)));
    }

    @ParameterizedTest
    @CsvSource({"ABOVE,199,false", "ABOVE,200,true", "ABOVE,201,true",
                "BELOW,199,true", "BELOW,200,true", "BELOW,201,false"})
    void evaluatesBothDirectionsIncludingExactThreshold(String direction, String price, boolean fires) {
        long alert = alert(direction);
        long quote = quote(price, -1);
        evaluator.evaluate();
        assertEquals(fires ? 1 : 0, notices(alert));
        assertEquals(!fires, jdbc.queryForObject("SELECT armed FROM price_alerts WHERE id=?", Boolean.class, alert));
        if (fires) {
            assertEquals(quote, jdbc.queryForObject("SELECT quote_id FROM price_alert_notifications WHERE alert_id=?", Long.class, alert));
            assertEquals(0, new BigDecimal(price).compareTo(jdbc.queryForObject(
                    "SELECT observed_price FROM price_alert_notifications WHERE alert_id=?", BigDecimal.class, alert)));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "stale", "future", "disabled"})
    void neverTriggersFromUnavailableOrUnusableQuotes(String state) {
        long alert = alert("ABOVE");
        if (!state.equals("missing")) quote("210", state.equals("stale") ? -90 : state.equals("future") ? 90 : -1);
        if (state.equals("disabled")) jdbc.update("UPDATE instruments SET tradable=FALSE WHERE id=?", instrument);
        evaluator.evaluate();
        assertEquals(0, notices(alert));
        assertTrue(jdbc.queryForObject("SELECT armed FROM price_alerts WHERE id=?", Boolean.class, alert));
    }

    @Test
    void usesOnlyTheLatestQuoteAndIgnoresPausedAlerts() {
        long alert = alert("ABOVE");
        quote("210", -10);
        quote("190", -1);
        evaluator.evaluate();
        assertEquals(0, notices(alert));
        jdbc.update("UPDATE price_alerts SET enabled=FALSE WHERE id=?", alert);
        quote("220", -1);
        evaluator.evaluate();
        assertEquals(0, notices(alert));
    }

    @Test
    void repeatedTicksDoNotDuplicateAndRecrossingStartsOneNewGeneration() {
        long alert = alert("ABOVE");
        quote("210", -1);
        evaluator.evaluate(); evaluator.evaluate();
        assertEquals(1, notices(alert));
        quote("190", -1);
        evaluator.evaluate(); evaluator.evaluate();
        assertEquals(2, jdbc.queryForObject("SELECT generation FROM price_alerts WHERE id=?", Integer.class, alert));
        assertEquals(1, notices(alert));
        quote("200", -1);
        evaluator.evaluate(); evaluator.evaluate();
        assertEquals(2, notices(alert));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(DISTINCT generation) FROM price_alert_notifications WHERE alert_id=?", Integer.class, alert));
    }

    @Test
    void twoConcurrentEvaluatorsPersistOneNotification() throws Exception {
        long alert = alert("ABOVE"); quote("210", -1);
        var workers = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first = workers.submit(() -> { assertTrue(start.await(5, TimeUnit.SECONDS)); evaluator.evaluate(); return null; });
            var second = workers.submit(() -> { assertTrue(start.await(5, TimeUnit.SECONDS)); evaluator.evaluate(); return null; });
            start.countDown(); first.get(15, TimeUnit.SECONDS); second.get(15, TimeUnit.SECONDS);
            assertEquals(1, notices(alert));
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private long alert(String direction) {
        return jdbc.queryForObject("INSERT INTO price_alerts(user_id,instrument_id,direction,threshold) VALUES (?,?,?,200) RETURNING id",
                Long.class, owner, instrument, direction);
    }
    private long quote(String price, int seconds) {
        return jdbc.queryForObject("""
                INSERT INTO market_quotes(instrument_id,bid_price,ask_price,last_price,quoted_at)
                VALUES (?,?,?,?,?) RETURNING id
                """, Long.class, instrument, new BigDecimal(price), new BigDecimal(price), new BigDecimal(price),
                LocalDateTime.now(ZoneOffset.UTC).plusSeconds(seconds));
    }
    private int notices(long alert) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM price_alert_notifications WHERE alert_id=? AND user_id=?", Integer.class, alert, owner);
    }
}
