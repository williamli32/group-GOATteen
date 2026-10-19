package com.goatteen.trading.recovery;

import com.goatteen.trading.TradingPlatformApplication;
import com.goatteen.trading.support.TestDatabaseFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationRestartRecoveryIntegrationTest {
    private static final String TEST_DB = "leap_trading_test";

    @Test
    void shouldRecoverAcceptedOrderAfterRestartWithoutDuplicateSettlement() throws InterruptedException {
        // Persist the pre-crash state using JDBC/Flyway only. Starting a full app
        // with recovery disabled adds no coverage: this setup only inserts rows.
        try (ConfigurableApplicationContext database = startDatabaseFixtureContext();
             TestDatabaseFixtures fixtures = new TestDatabaseFixtures(database.getBean(JdbcTemplate.class))) {
            JdbcTemplate jdbc = database.getBean(JdbcTemplate.class);
            assertDedicatedDatabase(jdbc);
            FixtureIds ids = createInterruptedOrder(jdbc, fixtures);
            assertEquals("ACCEPTED", status(jdbc, ids.orderId()));
            assertEquals(0L, count(jdbc, "SELECT COUNT(*) FROM fills WHERE order_id=?", ids.orderId()));

            // First real boot recovers ACCEPTED + no fill. Close it completely,
            // then boot again to prove FILLED is verified without settling twice.
            for (int restart = 0; restart < 2; restart++) {
                try (ConfigurableApplicationContext application = startApplication()) {
                    try {
                        JdbcTemplate restartedJdbc = application.getBean(JdbcTemplate.class);
                        assertDedicatedDatabase(restartedJdbc);
                        assertRecoveredExactlyOnce(restartedJdbc, ids);
                    } finally {
                        TestDatabaseFixtures.awaitReporting(application.getBean(
                                "applicationTaskExecutor", ThreadPoolTaskExecutor.class));
                    }
                }
            }
            // Real app contexts await their async reporting tasks on shutdown.
            // The JDBC context remains available to clean only this fixture.
        }
    }

    private FixtureIds createInterruptedOrder(JdbcTemplate jdbc, TestDatabaseFixtures fixtures) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long userId = jdbc.queryForObject("""
                INSERT INTO users(email,password_hash)
                VALUES (?, 'test-password-hash') RETURNING id
                """, Long.class, "restart-" + suffix + "@example.test");
        fixtures.user(userId);
        Long clientId = jdbc.queryForObject("""
                INSERT INTO clients(user_id,first_name,last_name)
                VALUES (?, 'Restart', 'Test') RETURNING id
                """, Long.class, userId);
        Long accountId = jdbc.queryForObject("""
                INSERT INTO accounts(client_id,account_number,cash_balance,currency)
                VALUES (?, ?, 1000.00, 'GBP') RETURNING id
                """, Long.class, clientId, "LEAP-RESTART-" + suffix);
        Long instrumentId = jdbc.queryForObject("""
                INSERT INTO instruments(symbol,name,instrument_class,exchange,country_code,currency,tradable)
                VALUES (?, 'Restart Test Instrument', 'EQUITY', 'LSE', 'GB', 'GBP', TRUE) RETURNING id
                """, Long.class, "RST" + suffix);
        fixtures.instrument(instrumentId);
        jdbc.update("""
                INSERT INTO market_quotes(instrument_id,bid_price,ask_price,last_price,quoted_at)
                VALUES (?, 74.90, 75.10, 75.00, CURRENT_TIMESTAMP)
                """, instrumentId);
        Long orderId = jdbc.queryForObject("""
                INSERT INTO orders(account_id,instrument_id,side,quantity,status,submitted_at,idempotency_key)
                VALUES (?, ?, 'BUY', 2, 'ACCEPTED', CURRENT_TIMESTAMP, ?) RETURNING id
                """, Long.class, accountId, instrumentId, "restart-key-" + suffix);
        jdbc.update("""
                INSERT INTO order_status_history(order_id,status,changed_at,note)
                VALUES (?, 'SUBMITTED', CURRENT_TIMESTAMP, 'Order submitted'),
                       (?, 'ACCEPTED', CURRENT_TIMESTAMP, 'Order accepted')
                """, orderId, orderId);
        return new FixtureIds(orderId, accountId, instrumentId);
    }

    private void assertRecoveredExactlyOnce(JdbcTemplate jdbc, FixtureIds ids) {
        assertEquals("FILLED", status(jdbc, ids.orderId()));
        assertEquals(1L, count(jdbc, "SELECT COUNT(*) FROM fills WHERE order_id=?", ids.orderId()));
        assertEquals(1L, count(jdbc, """
                SELECT COUNT(*) FROM cash_transactions ct JOIN fills f ON f.id=ct.fill_id WHERE f.order_id=?
                """, ids.orderId()));
        assertEquals(1L, count(jdbc, """
                SELECT COUNT(*) FROM position_history ph JOIN fills f ON f.id=ph.fill_id WHERE f.order_id=?
                """, ids.orderId()));
        assertEquals(1L, count(jdbc, """
                SELECT COUNT(*) FROM order_status_history WHERE order_id=? AND status='FILLED'
                """, ids.orderId()));
        assertAmount("849.80", jdbc.queryForObject(
                "SELECT cash_balance FROM accounts WHERE id=?", BigDecimal.class, ids.accountId()));
        assertAmount("2", jdbc.queryForObject(
                "SELECT quantity FROM positions WHERE account_id=? AND instrument_id=?",
                BigDecimal.class, ids.accountId(), ids.instrumentId()));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class, FlywayAutoConfiguration.class})
    static class DatabaseFixtureConfiguration { }

    private ConfigurableApplicationContext startDatabaseFixtureContext() {
        return new SpringApplicationBuilder(DatabaseFixtureConfiguration.class)
                .web(WebApplicationType.NONE)
                .profiles("test")
                .run("--spring.flyway.enabled=true");
    }

    private ConfigurableApplicationContext startApplication() {
        // SecurityConfig requires a servlet app so that HttpSecurity is available.
        return new SpringApplicationBuilder(TradingPlatformApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles("test")
                .run("--server.port=0",
                        "--spring.jpa.hibernate.ddl-auto=validate",
                        "--spring.flyway.enabled=true",
                        "--app.market-simulator.enabled=false",
                        "--app.reporting.backfill-enabled=false",
                        "--app.recovery.enabled=true");
    }

    private void assertDedicatedDatabase(JdbcTemplate jdbc) {
        assertEquals(TEST_DB, jdbc.queryForObject("SELECT current_database()", String.class));
    }

    private String status(JdbcTemplate jdbc, Long orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, orderId);
    }

    private long count(JdbcTemplate jdbc, String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private record FixtureIds(Long orderId, Long accountId, Long instrumentId) { }
}
