package com.goatteen.trading.execution;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class OrderExecutionConcurrentDuplicateProcessingTest {

    /*
     * Always use the dedicated integration-test database.
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
                            + "OrderExecutionConcurrentDuplicateProcessingTest");
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
     * Simultaneous attempts using the same logical execution
     * must never create duplicate settlement.
     */
    @Test
    void testConcurrentDuplicateProcessingProtection()
            throws InterruptedException {

        assertDedicatedTestDatabase();

        String suffix = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        /*
         * ---------------------------------------------------------
         * Arrange
         * ---------------------------------------------------------
         */

        Long accountId = createAccount(
                suffix,
                new BigDecimal("50000.00"));

        Long instrumentId = createInstrument(
                suffix);

        createQuote(
                instrumentId,
                new BigDecimal("100.00"),
                new BigDecimal("100.05"));

        String sharedIdempotencyKey = "concurrent-" + UUID.randomUUID();

        Long orderId = jdbc.queryForObject(
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
                                100,
                                'SUBMITTED',
                                CURRENT_TIMESTAMP,
                                ?
                            )
                        RETURNING id
                        """,
                Long.class,
                accountId,
                instrumentId,
                sharedIdempotencyKey);

        executionService.acceptOrder(
                orderId);

        /*
         * ---------------------------------------------------------
         * Execute two requests concurrently.
         * ---------------------------------------------------------
         */

        int threadCount = 2;

        CountDownLatch readyLatch = new CountDownLatch(
                threadCount);

        CountDownLatch startLatch = new CountDownLatch(
                1);

        CountDownLatch endLatch = new CountDownLatch(
                threadCount);

        AtomicInteger successCount = new AtomicInteger();

        AtomicInteger safeFailureCount = new AtomicInteger();

        Queue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();

        ExecutorService executor = Executors.newFixedThreadPool(
                threadCount);

        for (int i = 0; i < threadCount; i++) {

            executor.submit(
                    () -> {

                        try {

                            /*
                             * Signal that this worker is ready.
                             */
                            readyLatch.countDown();

                            /*
                             * Both workers wait here so execution
                             * begins as close together as possible.
                             */
                            startLatch.await();

                            executionService.executeOrder(
                                    orderId,
                                    sharedIdempotencyKey);

                            /*
                             * A retry is allowed to return the
                             * already-existing Fill.
                             *
                             * Therefore more than one caller may
                             * return successfully while only one
                             * settlement occurs.
                             */
                            successCount.incrementAndGet();

                        } catch (OrderExecutionService.OrderExecutionException e) {

                            /*
                             * The second concurrent request may:
                             *
                             * 1. find the existing Fill and return it, or
                             *
                             * 2. acquire the order lock after the first
                             * execution completed and safely discover
                             * that the order is no longer ACCEPTED.
                             *
                             * Both outcomes are safe.
                             */
                            if (e.getMessage() != null
                                    && (e.getMessage()
                                            .contains(
                                                    "not in ACCEPTED")
                                            ||
                                            e.getMessage()
                                                    .contains(
                                                            "already"))) {

                                safeFailureCount.incrementAndGet();

                            } else {

                                unexpectedErrors.add(
                                        e);
                            }

                        } catch (Throwable e) {

                            unexpectedErrors.add(
                                    e);

                        } finally {

                            endLatch.countDown();
                        }
                    });
        }

        /*
         * Make sure both workers reached the starting point.
         */
        assertTrue(
                readyLatch.await(
                        5,
                        TimeUnit.SECONDS),
                "Concurrent workers did not become ready in time");

        /*
         * Release both workers.
         */
        startLatch.countDown();

        assertTrue(
                endLatch.await(
                        15,
                        TimeUnit.SECONDS),
                "Concurrent execution did not complete in time");

        executor.shutdown();

        assertTrue(
                executor.awaitTermination(
                        5,
                        TimeUnit.SECONDS),
                "Executor did not terminate cleanly");

        /*
         * ---------------------------------------------------------
         * Verify thread outcomes.
         * ---------------------------------------------------------
         */

        if (!unexpectedErrors.isEmpty()) {

            Throwable first = unexpectedErrors.peek();

            fail(
                    "Unexpected concurrent execution error: "
                            + first.getClass().getSimpleName()
                            + ": "
                            + first.getMessage());
        }

        /*
         * At least one request must successfully execute
         * the trade.
         */
        assertTrue(
                successCount.get() >= 1,
                "At least one execution attempt must succeed");

        /*
         * Every worker must finish with either:
         *
         * - a successful result, or
         * - a safe duplicate/state rejection.
         */
        assertEquals(
                threadCount,
                successCount.get()
                        + safeFailureCount.get(),
                "Every execution attempt should finish deterministically");

        /*
         * ---------------------------------------------------------
         * Verify persisted settlement.
         * ---------------------------------------------------------
         */

        assertEquals(
                "FILLED",
                jdbc.queryForObject(
                        """
                                SELECT status
                                FROM orders
                                WHERE id = ?
                                """,
                        String.class,
                        orderId));

        /*
         * Exactly ONE Fill.
         */
        assertEquals(
                1L,
                count(
                        """
                                SELECT COUNT(*)
                                FROM fills
                                WHERE order_id = ?
                                """,
                        orderId));

        /*
         * Exactly ONE Fill with the shared idempotency key.
         */
        assertEquals(
                1L,
                count(
                        """
                                SELECT COUNT(*)
                                FROM fills
                                WHERE idempotency_key = ?
                                """,
                        sharedIdempotencyKey));

        /*
         * Exactly ONE cash movement associated with this trade.
         */
        assertEquals(
                1L,
                count(
                        """
                                SELECT COUNT(*)
                                FROM cash_transactions ct
                                JOIN fills f
                                  ON f.id = ct.fill_id
                                WHERE f.order_id = ?
                                """,
                        orderId));

        /*
         * Exactly ONE position-history movement.
         */
        assertEquals(
                1L,
                count(
                        """
                                SELECT COUNT(*)
                                FROM position_history ph
                                JOIN fills f
                                  ON f.id = ph.fill_id
                                WHERE f.order_id = ?
                                """,
                        orderId));

        /*
         * Exactly ONE FILLED audit-history event.
         */
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

        /*
         * Cash deducted exactly once.
         *
         * 100 × 100.05 = 10,005
         *
         * 50,000 - 10,005 = 39,995
         */
        BigDecimal expectedCost = new BigDecimal("100.05")
                .multiply(
                        new BigDecimal("100"));

        BigDecimal expectedBalance = new BigDecimal("50000.00")
                .subtract(
                        expectedCost);

        assertAmount(
                expectedBalance,
                jdbc.queryForObject(
                        """
                                SELECT cash_balance
                                FROM accounts
                                WHERE id = ?
                                """,
                        BigDecimal.class,
                        accountId));

        /*
         * Position updated exactly once.
         */
        assertAmount(
                new BigDecimal("100"),
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
                "concurrent-"
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
                                'Concurrent',
                                'Test'
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
                "ACC-CON-" + suffix,
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
                                'Concurrent Test Instrument',
                                'EQUITY',
                                'NYSE',
                                'US',
                                'USD',
                                TRUE
                            )
                        RETURNING id
                        """,
                Long.class,
                "CON" + suffix);
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

        assertNotNull(
                actual);

        assertEquals(
                0,
                expected.compareTo(
                        actual));
    }
}