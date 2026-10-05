package com.goatteen.trading.recovery;

import com.goatteen.trading.TradingPlatformApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationRestartRecoveryIntegrationTest {

    private static final String TEST_DB = "leap_sprint4_rollback_test";

    private static final String TEST_USER = "leap_sprint4_test";

    private static final String TEST_URL = "jdbc:postgresql://localhost:5432/"
            + TEST_DB;

    @Test
    void shouldRecoverAcceptedOrderAfterRestartWithoutDuplicateSettlement() {

        String password = requireTestPassword();

        String suffix = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        Long orderId;

        Long accountId;

        Long instrumentId;

        /*
         * ---------------------------------------------------------
         * APPLICATION INSTANCE #1
         *
         * Recovery disabled.
         *
         * Create persisted state representing an application
         * that stopped after ACCEPTED but before execution.
         * ---------------------------------------------------------
         */
        try (ConfigurableApplicationContext first = startApplication(
                password,
                false)) {

            JdbcTemplate jdbc = first.getBean(
                    JdbcTemplate.class);

            assertDedicatedDatabase(
                    jdbc);

            Long userId = jdbc.queryForObject(
                    """
                            INSERT INTO users
                                (email, password_hash)
                            VALUES
                                (?, 'test-password-hash')
                            RETURNING id
                            """,
                    Long.class,
                    "restart-"
                            + suffix
                            + "@example.test");

            Long clientId = jdbc.queryForObject(
                    """
                            INSERT INTO clients
                                (user_id,
                                 first_name,
                                 last_name)
                            VALUES
                                (?, 'Restart', 'Test')
                            RETURNING id
                            """,
                    Long.class,
                    userId);

            accountId = jdbc.queryForObject(
                    """
                            INSERT INTO accounts
                                (client_id,
                                 account_number,
                                 cash_balance,
                                 currency)
                            VALUES
                                (?, ?, 1000.00, 'GBP')
                            RETURNING id
                            """,
                    Long.class,
                    clientId,
                    "LEAP-RESTART-"
                            + suffix);

            instrumentId = jdbc.queryForObject(
                    """
                            INSERT INTO instruments
                                (symbol,
                                 name,
                                 instrument_class,
                                 exchange,
                                 country_code,
                                 currency,
                                 tradable)
                            VALUES
                                (?, 'Restart Test Instrument',
                                 'EQUITY',
                                 'LSE',
                                 'GB',
                                 'GBP',
                                 TRUE)
                            RETURNING id
                            """,
                    Long.class,
                    "RST"
                            + suffix);

            jdbc.update(
                    """
                            INSERT INTO market_quotes
                                (instrument_id,
                                 bid_price,
                                 ask_price,
                                 last_price,
                                 quoted_at)
                            VALUES
                                (?, 74.90, 75.10, 75.00,
                                 CURRENT_TIMESTAMP)
                            """,
                    instrumentId);

            orderId = jdbc.queryForObject(
                    """
                            INSERT INTO orders
                                (account_id,
                                 instrument_id,
                                 side,
                                 quantity,
                                 status,
                                 submitted_at,
                                 idempotency_key)
                            VALUES
                                (?, ?, 'BUY', 2,
                                 'ACCEPTED',
                                 CURRENT_TIMESTAMP,
                                 ?)
                            RETURNING id
                            """,
                    Long.class,
                    accountId,
                    instrumentId,
                    "restart-key-"
                            + suffix);

            /*
             * Persist the lifecycle that occurred before
             * the interruption.
             */
            jdbc.update(
                    """
                            INSERT INTO order_status_history
                                (order_id,
                                 status,
                                 changed_at,
                                 note)
                            VALUES
                                (?, 'SUBMITTED',
                                 CURRENT_TIMESTAMP,
                                 'Order submitted')
                            """,
                    orderId);

            jdbc.update(
                    """
                            INSERT INTO order_status_history
                                (order_id,
                                 status,
                                 changed_at,
                                 note)
                            VALUES
                                (?, 'ACCEPTED',
                                 CURRENT_TIMESTAMP,
                                 'Order accepted')
                            """,
                    orderId);

            assertEquals(
                    "ACCEPTED",
                    status(
                            jdbc,
                            orderId));

            assertEquals(
                    0L,
                    count(
                            jdbc,
                            """
                                    SELECT COUNT(*)
                                    FROM fills
                                    WHERE order_id = ?
                                    """,
                            orderId));
        }

        /*
         * first.close() above represents application shutdown.
         *
         * Persisted PostgreSQL data remains.
         */

        /*
         * ---------------------------------------------------------
         * APPLICATION INSTANCE #2
         *
         * Recovery enabled.
         *
         * StartupRecoveryConfig must detect:
         *
         * ACCEPTED + no Fill
         *
         * and safely complete execution.
         * ---------------------------------------------------------
         */
        try (ConfigurableApplicationContext second = startApplication(
                password,
                true)) {

            JdbcTemplate jdbc = second.getBean(
                    JdbcTemplate.class);

            assertDedicatedDatabase(
                    jdbc);

            assertEquals(
                    "FILLED",
                    status(
                            jdbc,
                            orderId));

            assertEquals(
                    1L,
                    count(
                            jdbc,
                            """
                                    SELECT COUNT(*)
                                    FROM fills
                                    WHERE order_id = ?
                                    """,
                            orderId));

            assertEquals(
                    1L,
                    count(
                            jdbc,
                            """
                                    SELECT COUNT(*)
                                    FROM cash_transactions ct
                                    JOIN fills f
                                      ON f.id = ct.fill_id
                                    WHERE f.order_id = ?
                                    """,
                            orderId));

            assertEquals(
                    1L,
                    count(
                            jdbc,
                            """
                                    SELECT COUNT(*)
                                    FROM position_history ph
                                    JOIN fills f
                                      ON f.id = ph.fill_id
                                    WHERE f.order_id = ?
                                    """,
                            orderId));

            assertEquals(
                    1L,
                    count(
                            jdbc,
                            """
                                    SELECT COUNT(*)
                                    FROM order_status_history
                                    WHERE order_id = ?
                                      AND status = 'FILLED'
                                    """,
                            orderId));

            BigDecimal cash = jdbc.queryForObject(
                    """
                            SELECT cash_balance
                            FROM accounts
                            WHERE id = ?
                            """,
                    BigDecimal.class,
                    accountId);

            assertAmount(
                    "849.80",
                    cash);

            BigDecimal position = jdbc.queryForObject(
                    """
                            SELECT quantity
                            FROM positions
                            WHERE account_id = ?
                              AND instrument_id = ?
                            """,
                    BigDecimal.class,
                    accountId,
                    instrumentId);

            assertAmount(
                    "2",
                    position);
        }

        /*
         * ---------------------------------------------------------
         * APPLICATION INSTANCE #3
         *
         * Restart once more.
         *
         * A FILLED order must be verified but NEVER settled again.
         * ---------------------------------------------------------
         */
        try (ConfigurableApplicationContext third = startApplication(
                password,
                true)) {

            JdbcTemplate jdbc = third.getBean(
                    JdbcTemplate.class);

            assertDedicatedDatabase(
                    jdbc);

            assertEquals(
                    "FILLED",
                    status(
                            jdbc,
                            orderId));

            /*
             * Still exactly one Fill.
             */
            assertEquals(
                    1L,
                    count(
                            jdbc,
                            """
                                    SELECT COUNT(*)
                                    FROM fills
                                    WHERE order_id = ?
                                    """,
                            orderId));

            /*
             * Still exactly one cash settlement.
             */
            assertEquals(
                    1L,
                    count(
                            jdbc,
                            """
                                    SELECT COUNT(*)
                                    FROM cash_transactions ct
                                    JOIN fills f
                                      ON f.id = ct.fill_id
                                    WHERE f.order_id = ?
                                    """,
                            orderId));

            /*
             * Still exactly one position-history entry.
             */
            assertEquals(
                    1L,
                    count(
                            jdbc,
                            """
                                    SELECT COUNT(*)
                                    FROM position_history ph
                                    JOIN fills f
                                      ON f.id = ph.fill_id
                                    WHERE f.order_id = ?
                                    """,
                            orderId));

            /*
             * Still exactly one FILLED event.
             */
            assertEquals(
                    1L,
                    count(
                            jdbc,
                            """
                                    SELECT COUNT(*)
                                    FROM order_status_history
                                    WHERE order_id = ?
                                      AND status = 'FILLED'
                                    """,
                            orderId));

            assertAmount(
                    "849.80",
                    jdbc.queryForObject(
                            """
                                    SELECT cash_balance
                                    FROM accounts
                                    WHERE id = ?
                                    """,
                            BigDecimal.class,
                            accountId));

            assertAmount(
                    "2",
                    jdbc.queryForObject(
                            """
                                    SELECT quantity
                                    FROM positions
                                    WHERE account_id = ?
                                      AND instrument_id = ?
                                    """,
                            BigDecimal.class,
                            accountId,
                            instrumentId));
        }
    }

    private ConfigurableApplicationContext startApplication(
            String password,
            boolean recoveryEnabled) {

        return new SpringApplicationBuilder(
                TradingPlatformApplication.class)

                /*
                 * SecurityConfig requires a servlet application
                 * so that HttpSecurity is available.
                 */
                .web(
                        WebApplicationType.SERVLET)

                /*
                 * Load application-test.yaml for the normal
                 * test configuration.
                 */
                .profiles(
                        "test")

                /*
                 * These are passed as command-line properties
                 * so they override application-test.yaml and
                 * any DB_URL / DB_USERNAME / DB_PASSWORD
                 * environment variables.
                 */
                .run(
                        "--server.port=0",

                        "--spring.datasource.url="
                                + TEST_URL,

                        "--spring.datasource.username="
                                + TEST_USER,

                        "--spring.datasource.password="
                                + password,

                        "--spring.jpa.hibernate.ddl-auto=validate",

                        "--spring.flyway.enabled=true",

                        "--app.market-simulator.enabled=false",

                        "--app.recovery.enabled="
                                + recoveryEnabled);
    }

    private String requireTestPassword() {

        String password = System.getenv(
                "LEAP_TEST_DB_PASSWORD");

        if (password == null
                || password.isBlank()) {

            throw new IllegalStateException(
                    "Set LEAP_TEST_DB_PASSWORD before running "
                            + "ApplicationRestartRecoveryIntegrationTest");
        }

        return password;
    }

    private void assertDedicatedDatabase(
            JdbcTemplate jdbc) {

        assertEquals(
                TEST_DB,
                jdbc.queryForObject(
                        "SELECT current_database()",
                        String.class));

        assertEquals(
                TEST_USER,
                jdbc.queryForObject(
                        "SELECT current_user",
                        String.class));
    }

    private String status(
            JdbcTemplate jdbc,
            Long orderId) {

        return jdbc.queryForObject(
                """
                        SELECT status
                        FROM orders
                        WHERE id = ?
                        """,
                String.class,
                orderId);
    }

    private long count(
            JdbcTemplate jdbc,
            String sql,
            Object... args) {

        return jdbc.queryForObject(
                sql,
                Long.class,
                args);
    }

    private void assertAmount(
            String expected,
            BigDecimal actual) {

        assertNotNull(
                actual);

        assertEquals(
                0,
                new BigDecimal(expected)
                        .compareTo(actual));
    }
}