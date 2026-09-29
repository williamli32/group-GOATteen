package com.goatteen.trading.execution;

import com.goatteen.trading.account.Account;
import com.goatteen.trading.account.AccountRepository;
import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class OrderExecutionAccountIsolationIntegrationTest {

    private static final String TEST_DB = "leap_sprint4_rollback_test";

    private static final String TEST_USER = "leap_sprint4_test";

    @DynamicPropertySource
    static void configureDatabase(
            DynamicPropertyRegistry registry) {

        String password = System.getenv("LEAP_TEST_DB_PASSWORD");

        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "LEAP_TEST_DB_PASSWORD must be set");
        }

        registry.add(
                "spring.datasource.url",
                () -> "jdbc:postgresql://localhost:5432/" + TEST_DB);

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
    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void shouldOnlyUpdateTheAccountThatOwnsTheOrder() {

        // Verify we're connected to the isolated test database.
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

        String suffix = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        // Both accounts own the same instrument,
        // but only Account A will place an order.
        Long accountA = createAccount(
                "a", suffix, new BigDecimal("1000.00"));

        Long accountB = createAccount(
                "b", suffix, new BigDecimal("500.00"));

        Long instrumentId = jdbc.queryForObject(
                """
                        INSERT INTO instruments (
                            symbol, name, instrument_class,
                            exchange, country_code, currency, tradable
                        )
                        VALUES (?, ?, 'EQUITY', 'LSE', 'GB', 'GBP', TRUE)
                        RETURNING id
                        """,
                Long.class,
                "ISO" + suffix,
                "Account Isolation Test Instrument");

        Long quoteId = jdbc.queryForObject(
                """
                        INSERT INTO market_quotes (
                            instrument_id, bid_price, ask_price,
                            last_price, quoted_at
                        )
                        VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                        RETURNING id
                        """,
                Long.class,
                instrumentId,
                new BigDecimal("74.90"),
                new BigDecimal("75.10"),
                new BigDecimal("75.00"));

        // Account B already owns four shares.
        jdbc.update(
                """
                        INSERT INTO positions (
                            account_id, instrument_id, quantity, updated_at
                        )
                        VALUES (?, ?, 4, CURRENT_TIMESTAMP)
                        """,
                accountB,
                instrumentId);

        Long orderId = jdbc.queryForObject(
                """
                        INSERT INTO orders (
                            account_id, instrument_id,
                            side, quantity, status, submitted_at
                        )
                        VALUES (?, ?, 'BUY', 2, 'ACCEPTED', CURRENT_TIMESTAMP)
                        RETURNING id
                        """,
                Long.class,
                accountA,
                instrumentId);

        // Execute Account A's order through the
        // real service and real PostgreSQL repositories.
        executionService.executeOrder(orderId);

        // Account A buys two shares for £150.20.
        assertAmount(
                "849.80",
                cashBalance(accountA));

        assertAmount(
                "2",
                positionQuantity(accountA, instrumentId));

        // Account B's cash and existing holding
        // must remain completely unchanged.
        assertAmount(
                "500.00",
                cashBalance(accountB));

        assertAmount(
                "4",
                positionQuantity(accountB, instrumentId));

        // Only Account A should have a cash ledger entry.
        assertEquals(
                1L,
                count(
                        "SELECT COUNT(*) FROM cash_transactions " +
                                "WHERE account_id = ?",
                        accountA));

        assertEquals(
                0L,
                count(
                        "SELECT COUNT(*) FROM cash_transactions " +
                                "WHERE account_id = ?",
                        accountB));

        // Check Account A's actual ledger amount.
        assertAmount(
                "-150.20",
                jdbc.queryForObject(
                        """
                                SELECT amount
                                FROM cash_transactions
                                WHERE account_id = ?
                                """,
                        BigDecimal.class,
                        accountA));

        // Check that exactly one Fill was created
        // using the market quote selected for execution.
        assertEquals(
                1L,
                count(
                        "SELECT COUNT(*) FROM fills WHERE order_id = ?",
                        orderId));

        Long usedQuoteId = jdbc.queryForObject(
                "SELECT quote_id FROM fills WHERE order_id = ?",
                Long.class,
                orderId);

        assertEquals(quoteId, usedQuoteId);

        // The ledger must reference that Fill.
        Long fillId = jdbc.queryForObject(
                "SELECT id FROM fills WHERE order_id = ?",
                Long.class,
                orderId);

        Long ledgerFillId = jdbc.queryForObject(
                """
                        SELECT fill_id
                        FROM cash_transactions
                        WHERE account_id = ?
                        """,
                Long.class,
                accountA);

        assertEquals(fillId, ledgerFillId);

        // Verify the order and audit trail.
        assertEquals(
                "FILLED",
                jdbc.queryForObject(
                        "SELECT status FROM orders WHERE id = ?",
                        String.class,
                        orderId));

        assertEquals(
                1L,
                count(
                        """
                                SELECT COUNT(*)
                                FROM order_status_history
                                WHERE order_id = ?
                                  AND status = 'FILLED'
                                """,
                        orderId));
    }

    private Long createAccount(
            String label,
            String suffix,
            BigDecimal initialCash) {

        Long userId = jdbc.queryForObject(
                """
                        INSERT INTO users (email, password_hash)
                        VALUES (?, ?)
                        RETURNING id
                        """,
                Long.class,
                "isolation-" + label + "-" + suffix + "@example.test",
                "test-password-hash");

        Long clientId = jdbc.queryForObject(
                """
                        INSERT INTO clients (
                            user_id, first_name, last_name
                        )
                        VALUES (?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                userId,
                "Isolation",
                "Account " + label);

        return jdbc.queryForObject(
                """
                        INSERT INTO accounts (
                            client_id, account_number,
                            cash_balance, currency
                        )
                        VALUES (?, ?, ?, 'GBP')
                        RETURNING id
                        """,
                Long.class,
                clientId,
                "LEAP-ISO-" + label + "-" + suffix,
                initialCash);
    }

    private BigDecimal cashBalance(Long accountId) {
        return jdbc.queryForObject(
                "SELECT cash_balance FROM accounts WHERE id = ?",
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

    private Long count(String sql, Object... arguments) {
        return jdbc.queryForObject(
                sql,
                Long.class,
                arguments);
    }

    private void assertAmount(
            String expected,
            BigDecimal actual) {

        assertNotNull(actual);

        assertEquals(
                0,
                new BigDecimal(expected).compareTo(actual));
    }

    @Test
    void shouldPreventOverspendingDuringConcurrentBuyOrders()
            throws Exception {

        // Ensure this is the dedicated test database.
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

        String suffix = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        // One account with £1,000.
        Long accountId = createAccount(
                "race",
                suffix,
                new BigDecimal("1000.00"));

        Long instrumentId = jdbc.queryForObject(
                """
                        INSERT INTO instruments (
                            symbol, name, instrument_class,
                            exchange, country_code, currency, tradable
                        )
                        VALUES (?, ?, 'EQUITY', 'LSE', 'GB', 'GBP', TRUE)
                        RETURNING id
                        """,
                Long.class,
                "RACE" + suffix,
                "Concurrency Test Instrument");

        // Each BUY will cost £700.
        jdbc.update(
                """
                        INSERT INTO market_quotes (
                            instrument_id, bid_price, ask_price,
                            last_price, quoted_at
                        )
                        VALUES (?, 699.00, 700.00, 699.50, CURRENT_TIMESTAMP)
                        """,
                instrumentId);

        // Give the account one existing share.
        jdbc.update(
                """
                        INSERT INTO positions (
                            account_id, instrument_id,
                            quantity, updated_at
                        )
                        VALUES (?, ?, 1, CURRENT_TIMESTAMP)
                        """,
                accountId,
                instrumentId);

        // Two separate ACCEPTED orders for the same account.
        Long firstOrderId = jdbc.queryForObject(
                """
                        INSERT INTO orders (
                            account_id, instrument_id,
                            side, quantity, status, submitted_at
                        )
                        VALUES (?, ?, 'BUY', 1, 'ACCEPTED', CURRENT_TIMESTAMP)
                        RETURNING id
                        """,
                Long.class,
                accountId,
                instrumentId);

        Long secondOrderId = jdbc.queryForObject(
                """
                        INSERT INTO orders (
                            account_id, instrument_id,
                            side, quantity, status, submitted_at
                        )
                        VALUES (?, ?, 'BUY', 1, 'ACCEPTED', CURRENT_TIMESTAMP)
                        RETURNING id
                        """,
                Long.class,
                accountId,
                instrumentId);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);

        CountDownLatch start = new CountDownLatch(1);

        try {

            Future<Boolean> first = executor.submit(() -> {

                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Timed out waiting to start");
                }

                try {
                    executionService.executeOrder(firstOrderId);
                    return true;
                } catch (RuntimeException exception) {
                    return false;
                }
            });

            Future<Boolean> second = executor.submit(() -> {

                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Timed out waiting to start");
                }

                try {
                    executionService.executeOrder(secondOrderId);
                    return true;
                } catch (RuntimeException exception) {
                    return false;
                }
            });

            // Release both execution attempts together.
            assertTrue(
                    ready.await(10, TimeUnit.SECONDS));

            start.countDown();

            boolean firstSucceeded = first.get(30, TimeUnit.SECONDS);

            boolean secondSucceeded = second.get(30, TimeUnit.SECONDS);

            // Only one £700 purchase can be completed.
            int successCount = (firstSucceeded ? 1 : 0)
                    + (secondSucceeded ? 1 : 0);

            assertEquals(1, successCount);

            // £1,000 - £700 = £300.
            assertAmount(
                    "300.00",
                    cashBalance(accountId));

            // Initial one share plus one successful BUY.
            assertAmount(
                    "2",
                    positionQuantity(accountId, instrumentId));

            // Exactly one Fill.
            assertEquals(
                    1L,
                    count(
                            """
                                    SELECT COUNT(*)
                                    FROM fills
                                    WHERE order_id IN (?, ?)
                                    """,
                            firstOrderId,
                            secondOrderId));

            // Exactly one cash ledger entry.
            assertEquals(
                    1L,
                    count(
                            """
                                    SELECT COUNT(*)
                                    FROM cash_transactions
                                    WHERE account_id = ?
                                    """,
                            accountId));

            // One order FILLED; the other remains ACCEPTED.
            assertEquals(
                    1L,
                    count(
                            """
                                    SELECT COUNT(*)
                                    FROM orders
                                    WHERE id IN (?, ?)
                                      AND status = 'FILLED'
                                    """,
                            firstOrderId,
                            secondOrderId));

            assertEquals(
                    1L,
                    count(
                            """
                                    SELECT COUNT(*)
                                    FROM orders
                                    WHERE id IN (?, ?)
                                      AND status = 'ACCEPTED'
                                    """,
                            firstOrderId,
                            secondOrderId));

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    /**
     * Unlike the concurrent-start test, this test forces both execution
     * transactions to read the same account version BEFORE either may settle.
     * Both orders are affordable sequentially: exactly one failing therefore
     * demonstrates an optimistic-lock conflict, not insufficient cash.
     */
    @Test
    void shouldRejectOneConcurrentBuyAfterBothReadTheSameAccountVersion()
            throws Exception {

        assertEquals(TEST_DB, jdbc.queryForObject(
                "SELECT current_database()", String.class));
        assertEquals(TEST_USER, jdbc.queryForObject(
                "SELECT current_user", String.class));

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long accountId = createAccount(
                "overlap", suffix, new BigDecimal("1000.00"));

        Long instrumentId = jdbc.queryForObject(
                """
                        INSERT INTO instruments (
                            symbol, name, instrument_class,
                            exchange, country_code, currency, tradable
                        )
                        VALUES (?, ?, 'EQUITY', 'LSE', 'GB', 'GBP', TRUE)
                        RETURNING id
                        """,
                Long.class,
                "OVL" + suffix,
                "Controlled Concurrency Test Instrument");

        // £400 per BUY: both could succeed if executed sequentially.
        jdbc.update(
                """
                        INSERT INTO market_quotes (
                            instrument_id, bid_price, ask_price,
                            last_price, quoted_at
                        )
                        VALUES (?, 399.00, 400.00, 399.50, CURRENT_TIMESTAMP)
                        """,
                instrumentId);

        // An existing position avoids competing first-position INSERTs.
        jdbc.update(
                """
                        INSERT INTO positions (
                            account_id, instrument_id, quantity, updated_at
                        )
                        VALUES (?, ?, 1, CURRENT_TIMESTAMP)
                        """,
                accountId, instrumentId);

        Long firstOrderId = jdbc.queryForObject(
                """
                        INSERT INTO orders (
                            account_id, instrument_id, side,
                            quantity, status, submitted_at
                        )
                        VALUES (?, ?, 'BUY', 1, 'ACCEPTED', CURRENT_TIMESTAMP)
                        RETURNING id
                        """,
                Long.class,
                accountId, instrumentId);

        Long secondOrderId = jdbc.queryForObject(
                """
                        INSERT INTO orders (
                            account_id, instrument_id, side,
                            quantity, status, submitted_at
                        )
                        VALUES (?, ?, 'BUY', 1, 'ACCEPTED', CURRENT_TIMESTAMP)
                        RETURNING id
                        """,
                Long.class,
                accountId, instrumentId);

        CountDownLatch bothAccountsRead = new CountDownLatch(2);
        CountDownLatch allowSettlement = new CountDownLatch(1);
        ConcurrentLinkedQueue<Long> versionsRead = new ConcurrentLinkedQueue<>();
        Long initialVersion = jdbc.queryForObject(
                "SELECT version FROM accounts WHERE id = ?",
                Long.class, accountId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<RuntimeException> first = executor.submit(() -> executeAfterReadingAccount(
                    firstOrderId, accountId,
                    ready, start, bothAccountsRead,
                    allowSettlement, versionsRead));

            Future<RuntimeException> second = executor.submit(() -> executeAfterReadingAccount(
                    secondOrderId, accountId,
                    ready, start, bothAccountsRead,
                    allowSettlement, versionsRead));

            assertTrue(ready.await(10, TimeUnit.SECONDS),
                    "Both workers must be ready");
            start.countDown();

            // Both transactions retain their own JPA persistence context.
            // Their first Account reads must finish before either executes.
            assertTrue(bothAccountsRead.await(20, TimeUnit.SECONDS),
                    "Both transactions must read the account before settlement");
            assertEquals(2, versionsRead.size());
            assertTrue(versionsRead.stream().allMatch(initialVersion::equals),
                    "Both transactions must load the same initial version");

            allowSettlement.countDown();

            RuntimeException firstFailure = first.get(45, TimeUnit.SECONDS);
            RuntimeException secondFailure = second.get(45, TimeUnit.SECONDS);

            int successfulExecutions = (firstFailure == null ? 1 : 0)
                    + (secondFailure == null ? 1 : 0);
            assertEquals(1, successfulExecutions,
                    "Only one execution may commit from the shared version");

            RuntimeException failure = firstFailure != null ? firstFailure : secondFailure;
            assertTrue(isOptimisticLockFailure(failure),
                    () -> "Expected an optimistic-lock failure, got: " + failure);

            // The transaction that loses the version race must roll back
            // its cash, position, fill, ledger, order and history updates.
            assertAmount("600.00", cashBalance(accountId));
            assertAmount("2", positionQuantity(accountId, instrumentId));
            assertEquals(1L, count(
                    "SELECT COUNT(*) FROM cash_transactions WHERE account_id = ?",
                    accountId));
            assertEquals(1L, count(
                    "SELECT COUNT(*) FROM fills WHERE order_id IN (?, ?)",
                    firstOrderId, secondOrderId));
            assertEquals(1L, count(
                    """
                            SELECT COUNT(*) FROM orders
                            WHERE id IN (?, ?) AND status = 'FILLED'
                            """,
                    firstOrderId, secondOrderId));
            assertEquals(1L, count(
                    """
                            SELECT COUNT(*) FROM orders
                            WHERE id IN (?, ?) AND status = 'ACCEPTED'
                            """,
                    firstOrderId, secondOrderId));
            assertEquals(1L, count(
                    """
                            SELECT COUNT(*) FROM order_status_history
                            WHERE order_id IN (?, ?) AND status = 'FILLED'
                            """,
                    firstOrderId, secondOrderId));
            assertEquals(initialVersion.longValue() + 1,
                    jdbc.queryForObject(
                            "SELECT version FROM accounts WHERE id = ?",
                            Long.class, accountId).longValue());
        } finally {
            // Release any waiting workers even if an assertion fails.
            allowSettlement.countDown();
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS),
                    "Execution workers must shut down");
        }
    }

    /**
     * Opening an outer transaction gives each worker a separate JPA
     * persistence context. Preloading the Account puts its version into
     * that context. executeOrder() uses REQUIRED propagation and therefore
     * joins this transaction, reusing the Account already read above.
     */
    private RuntimeException executeAfterReadingAccount(
            Long orderId,
            Long accountId,
            CountDownLatch ready,
            CountDownLatch start,
            CountDownLatch bothAccountsRead,
            CountDownLatch allowSettlement,
            ConcurrentLinkedQueue<Long> versionsRead)
            throws InterruptedException {

        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Worker start timed out");
        }

        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);

            transaction.execute(status -> {
                Account loaded = accountRepository.findById(accountId)
                        .orElseThrow();
                versionsRead.add(loaded.getVersion());
                bothAccountsRead.countDown();

                try {
                    if (!allowSettlement.await(25, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Timed out waiting for both account reads");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                            "Interrupted while waiting for settlement",
                            interrupted);
                }

                // Same thread and outer transaction: reuses the loaded
                // Account/version in this transaction's persistence context.
                executionService.executeOrder(orderId);
                return null;
            });
            return null; // Transaction successfully committed.
        } catch (RuntimeException failure) {
            // Includes optimistic-lock failure raised during transaction commit.
            return failure;
        }
    }

    private boolean isOptimisticLockFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof OptimisticLockingFailureException
                    || cause instanceof OptimisticLockException) {
                return true;
            }
        }
        return false;
    }

}
