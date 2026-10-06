package com.goatteen.trading.execution;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class OrderExecutionAccountIsolationTest {

    /*
     * Always use the dedicated integration-test database.
     *
     * Never run this test against leap_trading.
     */
    private static final String TEST_DB = "leap_sprint4_rollback_test";

    private static final String TEST_USER = "leap_sprint4_test";

    private static final String TEST_DB_URL = "jdbc:postgresql://localhost:5432/"
            + TEST_DB;

    @DynamicPropertySource
    static void configureDatabase(
            DynamicPropertyRegistry registry) {

        String password = System.getenv(
                "LEAP_TEST_DB_PASSWORD");

        if (password == null
                || password.isBlank()) {

            throw new IllegalStateException(
                    "Set LEAP_TEST_DB_PASSWORD before running "
                            + "OrderExecutionAccountIsolationTest");
        }

        registry.add(
                "spring.datasource.url",
                () -> TEST_DB_URL);

        registry.add(
                "spring.datasource.username",
                () -> TEST_USER);

        registry.add(
                "spring.datasource.password",
                () -> password);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private OrderExecutionService executionService;

    /*
     * Goal 5:
     *
     * Settlement for one account must never alter
     * another account's cash or positions.
     */
    @Test
    @Transactional
    void testAccountIsolation() {

        assertDedicatedTestDatabase();

        String suffix = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        /*
         * ---------------------------------------------------------
         * Arrange
         * ---------------------------------------------------------
         */

        Long account1Id = createAccount(
                "iso1-" + suffix,
                new BigDecimal("10000.00"));

        Long account2Id = createAccount(
                "iso2-" + suffix,
                new BigDecimal("5000.00"));

        Long instrumentId = createInstrument(
                suffix);

        createQuote(
                instrumentId,
                new BigDecimal("150.00"),
                new BigDecimal("150.05"));

        String order1Key = "isolation-order-1-" + suffix;

        String order2Key = "isolation-order-2-" + suffix;

        Long order1Id = jdbc.queryForObject(
                """
                        INSERT INTO orders
                            (
                                account_id,
                                instrument_id,
                                side,
                                quantity,
                                status,
                                submitted_at,
                                idempotency_key
                            )
                        VALUES
                            (
                                ?,
                                ?,
                                'BUY',
                                10,
                                'SUBMITTED',
                                CURRENT_TIMESTAMP,
                                ?
                            )
                        RETURNING id
                        """,
                Long.class,
                account1Id,
                instrumentId,
                order1Key);

        Long order2Id = jdbc.queryForObject(
                """
                        INSERT INTO orders
                            (
                                account_id,
                                instrument_id,
                                side,
                                quantity,
                                status,
                                submitted_at,
                                idempotency_key
                            )
                        VALUES
                            (
                                ?,
                                ?,
                                'BUY',
                                5,
                                'SUBMITTED',
                                CURRENT_TIMESTAMP,
                                ?
                            )
                        RETURNING id
                        """,
                Long.class,
                account2Id,
                instrumentId,
                order2Key);

        /*
         * Orders must be ACCEPTED before execution.
         */
        executionService.acceptOrder(
                order1Id);

        executionService.acceptOrder(
                order2Id);

        BigDecimal initialBalance1 = cashBalance(
                account1Id);

        BigDecimal initialBalance2 = cashBalance(
                account2Id);

        /*
         * ---------------------------------------------------------
         * Execute account 1 order
         * ---------------------------------------------------------
         */

        executionService.executeOrder(
                order1Id,
                order1Key);

        BigDecimal expectedCost1 = new BigDecimal("150.05")
                .multiply(
                        new BigDecimal("10"));

        BigDecimal expectedBalance1 = initialBalance1.subtract(
                expectedCost1);

        assertAmount(
                expectedBalance1,
                cashBalance(
                        account1Id));

        /*
         * Account 2 must still be completely untouched.
         */
        assertAmount(
                initialBalance2,
                cashBalance(
                        account2Id));

        assertEquals(
                0L,
                count(
                        """
                                SELECT COUNT(*)
                                FROM positions
                                WHERE account_id = ?
                                """,
                        account2Id));

        assertEquals(
                0L,
                count(
                        """
                                SELECT COUNT(*)
                                FROM cash_transactions
                                WHERE account_id = ?
                                """,
                        account2Id));

        /*
         * Account 1 should have exactly one settlement.
         */
        assertEquals(
                1L,
                count(
                        """
                                SELECT COUNT(*)
                                FROM fills
                                WHERE order_id = ?
                                """,
                        order1Id));

        assertEquals(
                1L,
                count(
                        """
                                SELECT COUNT(*)
                                FROM cash_transactions
                                WHERE account_id = ?
                                """,
                        account1Id));

        assertAmount(
                new BigDecimal("10"),
                positionQuantity(
                        account1Id,
                        instrumentId));

        /*
         * ---------------------------------------------------------
         * Execute account 2 order
         * ---------------------------------------------------------
         */

        executionService.executeOrder(
                order2Id,
                order2Key);

        BigDecimal expectedCost2 = new BigDecimal("150.05")
                .multiply(
                        new BigDecimal("5"));

        BigDecimal expectedBalance2 = initialBalance2.subtract(
                expectedCost2);

        assertAmount(
                expectedBalance2,
                cashBalance(
                        account2Id));

        assertAmount(
                new BigDecimal("5"),
                positionQuantity(
                        account2Id,
                        instrumentId));

        /*
         * ---------------------------------------------------------
         * Account 1 must remain unchanged after account 2 settles.
         * ---------------------------------------------------------
         */

        assertAmount(
                expectedBalance1,
                cashBalance(
                        account1Id));

        assertAmount(
                new BigDecimal("10"),
                positionQuantity(
                        account1Id,
                        instrumentId));

        assertEquals(
                1L,
                count(
                        """
                                SELECT COUNT(*)
                                FROM cash_transactions
                                WHERE account_id = ?
                                """,
                        account1Id));

        assertEquals(
                1L,
                count(
                        """
                                SELECT COUNT(*)
                                FROM cash_transactions
                                WHERE account_id = ?
                                """,
                        account2Id));
    }

    private void assertDedicatedTestDatabase() {

        assertEquals(
                TEST_DB,
                jdbc.queryForObject(
                        "SELECT current_database()",
                        String.class),
                "Refusing to run against an unexpected database");

        assertEquals(
                TEST_USER,
                jdbc.queryForObject(
                        "SELECT current_user",
                        String.class),
                "Refusing to run with an unexpected database user");
    }

    private Long createAccount(
            String suffix,
            BigDecimal initialCash) {

        Long userId = jdbc.queryForObject(
                """
                        INSERT INTO users
                            (
                                email,
                                password_hash
                            )
                        VALUES
                            (
                                ?,
                                'test-password-hash'
                            )
                        RETURNING id
                        """,
                Long.class,
                "account-"
                        + suffix
                        + "@test.com");

        Long clientId = jdbc.queryForObject(
                """
                        INSERT INTO clients
                            (
                                user_id,
                                first_name,
                                last_name
                            )
                        VALUES
                            (
                                ?,
                                'Test',
                                'Client'
                            )
                        RETURNING id
                        """,
                Long.class,
                userId);

        return jdbc.queryForObject(
                """
                        INSERT INTO accounts
                            (
                                client_id,
                                account_number,
                                cash_balance,
                                currency
                            )
                        VALUES
                            (
                                ?,
                                ?,
                                ?,
                                'USD'
                            )
                        RETURNING id
                        """,
                Long.class,
                clientId,
                "ACC-" + suffix,
                initialCash);
    }

    private Long createInstrument(
            String suffix) {

        return jdbc.queryForObject(
                """
                        INSERT INTO instruments
                            (
                                symbol,
                                name,
                                instrument_class,
                                exchange,
                                country_code,
                                currency,
                                tradable
                            )
                        VALUES
                            (
                                ?,
                                'Test Instrument',
                                'EQUITY',
                                'NYSE',
                                'US',
                                'USD',
                                TRUE
                            )
                        RETURNING id
                        """,
                Long.class,
                "ISO" + suffix);
    }

    private void createQuote(
            Long instrumentId,
            BigDecimal bidPrice,
            BigDecimal askPrice) {

        jdbc.update(
                """
                        INSERT INTO market_quotes
                            (
                                instrument_id,
                                bid_price,
                                ask_price,
                                last_price,
                                quoted_at
                            )
                        VALUES
                            (
                                ?,
                                ?,
                                ?,
                                ?,
                                CURRENT_TIMESTAMP
                            )
                        """,
                instrumentId,
                bidPrice,
                askPrice,
                bidPrice);
    }

    private BigDecimal cashBalance(
            Long accountId) {

        return jdbc.queryForObject(
                """
                        SELECT cash_balance
                        FROM accounts
                        WHERE id = ?
                        """,
                BigDecimal.class,
                accountId);
    }

    private BigDecimal positionQuantity(
            Long accountId,
            Long instrumentId) {

        return jdbc.queryForObject(
                """
                        SELECT quantity
                        FROM positions
                        WHERE account_id = ?
                          AND instrument_id = ?
                        """,
                BigDecimal.class,
                accountId,
                instrumentId);
    }

    private long count(
            String sql,
            Object... arguments) {

        return jdbc.queryForObject(
                sql,
                Long.class,
                arguments);
    }

    private void assertAmount(
            BigDecimal expected,
            BigDecimal actual) {

        assertEquals(
                0,
                expected.compareTo(
                        actual));
    }
}