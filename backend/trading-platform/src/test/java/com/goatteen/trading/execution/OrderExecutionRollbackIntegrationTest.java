
package com.goatteen.trading.execution;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@ActiveProfiles("test")
class OrderExecutionRollbackIntegrationTest {

    /*
     * Safety: this test connects only to the
     * dedicated local Sprint 4 test database.
     */
    private static final String TEST_DB_NAME = "leap_sprint4_rollback_test";

    private static final String TEST_DB_USER = "leap_sprint4_test";

    private static final String TEST_DB_URL = "jdbc:postgresql://localhost:5432/" + TEST_DB_NAME;

    /*
     * Read the test database password from an
     * environment variable instead of storing
     * it in source code.
     */
    private static String requireTestPassword() {

        String password = System.getenv("LEAP_TEST_DB_PASSWORD");

        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Set LEAP_TEST_DB_PASSWORD before running " +
                            "OrderExecutionRollbackIntegrationTest");
        }

        return password;
    }

    /*
     * Override the application's normal database
     * configuration with the dedicated test database.
     *
     * Flyway will run against this database.
     */
    @DynamicPropertySource
    static void configureDatabase(
            DynamicPropertyRegistry registry) {

        String password = requireTestPassword();

        registry.add(
                "spring.datasource.url",
                () -> TEST_DB_URL);

        registry.add(
                "spring.datasource.username",
                () -> TEST_DB_USER);

        registry.add(
                "spring.datasource.password",
                () -> password);
    }

    @Autowired
    private OrderExecutionService executionService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    /*
     * Only the Fill repository is mocked.
     *
     * All other repositories use real PostgreSQL.
     */
    @MockitoBean
    private FillRepository fillRepository;

    @Test
    void shouldRollBackEntireSettlementWhenFillCreationFails() {

        /*
         * Important:
         *
         * Do not add @Transactional to this test.
         *
         * executeOrder() must manage its own
         * transaction. The checks following the
         * exception must read the database after
         * that transaction has rolled back.
         */

        /*
         * Verify the connection BEFORE inserting
         * anything into the database.
         */
        String actualDatabase = jdbc.queryForObject(
                "SELECT current_database()",
                String.class);

        String actualUser = jdbc.queryForObject(
                "SELECT current_user",
                String.class);

        assertEquals(
                TEST_DB_NAME,
                actualDatabase,
                "Refusing to run against an unexpected database");

        assertEquals(
                TEST_DB_USER,
                actualUser,
                "Refusing to run with an unexpected database user");

        /*
         * Generate unique identifiers so repeated
         * test runs do not conflict.
         */
        String suffix = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        /*
         * Create an isolated test user.
         */
        Long userId = jdbc.queryForObject(
                """
                        INSERT INTO users (
                            email,
                            password_hash
                        )
                        VALUES (?, ?)
                        RETURNING id
                        """,
                Long.class,
                "rollback-" + suffix + "@example.test",
                "test-password-hash");

        /*
         * Create the client's profile.
         */
        Long clientId = jdbc.queryForObject(
                """
                        INSERT INTO clients (
                            user_id,
                            first_name,
                            last_name
                        )
                        VALUES (?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                userId,
                "Rollback",
                "Test");

        /*
         * Create a GBP account with £1,000.
         */
        Long accountId = jdbc.queryForObject(
                """
                        INSERT INTO accounts (
                            client_id,
                            account_number,
                            cash_balance,
                            currency
                        )
                        VALUES (?, ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                clientId,
                "LEAP-ROLLBACK-" + suffix,
                new BigDecimal("1000.00"),
                "GBP");

        /*
         * Create a GBP-denominated instrument.
         */
        Long instrumentId = jdbc.queryForObject(
                """
                        INSERT INTO instruments (
                            symbol,
                            name,
                            instrument_class,
                            exchange,
                            country_code,
                            currency,
                            tradable
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                "T" + suffix,
                "Rollback Test Instrument",
                "EQUITY",
                "LSE",
                "GB",
                "GBP",
                true);

        /*
         * Create a market quote:
         *
         * BID: £74.90
         * ASK: £75.10
         */
        jdbc.update(
                """
                        INSERT INTO market_quotes (
                            instrument_id,
                            bid_price,
                            ask_price,
                            last_price,
                            quoted_at
                        )
                        VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                        """,
                instrumentId,
                new BigDecimal("74.90"),
                new BigDecimal("75.10"),
                new BigDecimal("75.00"));

        /*
         * Create an ACCEPTED BUY order.
         *
         * The transaction being tested begins
         * when executeOrder() is called below.
         */
        Long orderId = jdbc.queryForObject(
                """
                        INSERT INTO orders (
                            account_id,
                            instrument_id,
                            side,
                            quantity,
                            status,
                            submitted_at
                        )
                        VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        RETURNING id
                        """,
                Long.class,
                accountId,
                instrumentId,
                "BUY",
                new BigDecimal("2"),
                "ACCEPTED");

        /*
         * Deliberately fail Fill creation.
         *
         * First flush all preceding cash, ledger
         * and position changes to PostgreSQL.
         *
         * Then throw an exception to test rollback.
         */
        AtomicBoolean changesWereFlushed = new AtomicBoolean(false);

        doAnswer(invocation -> {

            entityManager.flush();

            /*
             * Confirm the updated cash balance
             * is visible inside this transaction.
             *
             * £1,000 - (2 × £75.10) = £849.80.
             */
            BigDecimal cashDuringSettlement = jdbc.queryForObject(
                    """
                            SELECT cash_balance
                            FROM accounts
                            WHERE id = ?
                            """,
                    BigDecimal.class,
                    accountId);

            assertEquals(
                    0,
                    new BigDecimal("849.80")
                            .compareTo(cashDuringSettlement));

            /*
             * Confirm the ledger entry was written.
             */
            Integer ledgerCount = jdbc.queryForObject(
                    """
                            SELECT COUNT(*)
                            FROM cash_transactions
                            WHERE account_id = ?
                            """,
                    Integer.class,
                    accountId);

            assertEquals(1, ledgerCount);

            /*
             * Confirm the new position was written.
             */
            Integer positionCount = jdbc.queryForObject(
                    """
                            SELECT COUNT(*)
                            FROM positions
                            WHERE account_id = ?
                              AND instrument_id = ?
                              AND quantity = 2
                            """,
                    Integer.class,
                    accountId,
                    instrumentId);

            assertEquals(1, positionCount);

            changesWereFlushed.set(true);

            /*
             * Trigger transaction rollback.
             */
            throw new IllegalStateException(
                    "Simulated failure after database flush");

        }).when(fillRepository).save(any(Fill.class));

        /*
         * Execute the trade.
         *
         * The injected failure must propagate out
         * of executeOrder(), triggering rollback.
         */
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> executionService.executeOrder(
                        orderId));

        assertEquals(
                "Simulated failure after database flush",
                exception.getMessage());

        assertTrue(
                changesWereFlushed.get(),
                "Database changes must be flushed before failure");

        /*
         * Verify the database AFTER rollback.
         */

        /*
         * 1. Account cash must return to £1,000.
         */
        BigDecimal finalCash = jdbc.queryForObject(
                """
                        SELECT cash_balance
                        FROM accounts
                        WHERE id = ?
                        """,
                BigDecimal.class,
                accountId);

        assertEquals(
                0,
                new BigDecimal("1000.00")
                        .compareTo(finalCash));

        /*
         * 2. The cash ledger entry must disappear.
         */
        Integer finalLedgerCount = jdbc.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM cash_transactions
                        WHERE account_id = ?
                        """,
                Integer.class,
                accountId);

        assertEquals(0, finalLedgerCount);

        /*
         * 3. The new position must disappear.
         */
        Integer finalPositionCount = jdbc.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM positions
                        WHERE account_id = ?
                          AND instrument_id = ?
                        """,
                Integer.class,
                accountId,
                instrumentId);

        assertEquals(0, finalPositionCount);

        /*
         * 4. No Fill may remain.
         */
        Integer finalFillCount = jdbc.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM fills
                        WHERE order_id = ?
                        """,
                Integer.class,
                orderId);

        assertEquals(0, finalFillCount);

        /*
         * 5. The order must remain ACCEPTED.
         *
         * Submission and acceptance happened
         * before the failed execution transaction.
         */
        String finalOrderStatus = jdbc.queryForObject(
                """
                        SELECT status
                        FROM orders
                        WHERE id = ?
                        """,
                String.class,
                orderId);

        assertEquals(
                "ACCEPTED",
                finalOrderStatus);

        /*
         * 6. No FILLED audit event may remain.
         */
        Integer filledHistoryCount = jdbc.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM order_status_history
                        WHERE order_id = ?
                          AND status = 'FILLED'
                        """,
                Integer.class,
                orderId);

        assertEquals(0, filledHistoryCount);
    }
}
