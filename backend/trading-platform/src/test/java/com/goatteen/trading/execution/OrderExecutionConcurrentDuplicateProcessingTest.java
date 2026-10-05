package com.goatteen.trading.execution;

import com.goatteen.trading.order.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Goal 5: Concurrent Duplicate-Processing Protection
 * Verify that simultaneous execution requests with the same idempotency key
 * do not create duplicate Fills, duplicate cash movements, or duplicate position updates.
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderExecutionConcurrentDuplicateProcessingTest {

    private static final String TEST_DB = "leap_trading";
    private static final String TEST_USER = "postgres";

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        String password = System.getenv("LEAP_TEST_DB_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Set LEAP_TEST_DB_PASSWORD before running this test");
        }
        registry.add("spring.datasource.url", 
            () -> "jdbc:postgresql://localhost:5433/" + TEST_DB);
        registry.add("spring.datasource.username", () -> TEST_USER);
        registry.add("spring.datasource.password", () -> password);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private OrderExecutionService executionService;

    @Autowired
    private OrderRepository orderRepository;

    /**
     * Goal 5: Concurrent Duplicate-Processing Protection
     * Verify that simultaneous execution requests with the same idempotency key
     * do not create duplicate Fills, duplicate cash movements, or duplicate position updates.
     */
    @Test
    void testConcurrentDuplicateProcessingProtection() throws InterruptedException {
        // Generate unique suffix to avoid database constraint violations on repeated runs
        // Use first 8 chars of UUID to stay within VARCHAR(50) limit for account_number
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        // Setup: Create account, instrument, and quote
        Long accountId = createAccount(uniqueSuffix, new BigDecimal("50000.00"));
        Long instrumentId = createInstrument(uniqueSuffix);
        createQuote(instrumentId, new BigDecimal("100.00"), new BigDecimal("100.05"));

        // Create order with idempotency key (unique per execution attempt, not per test run)
        String sharedIdempotencyKey = "concurrent-" + UUID.randomUUID();
        Long orderId = jdbc.queryForObject(
            """
            INSERT INTO orders
                (account_id, instrument_id, side, quantity, status, submitted_at, idempotency_key)
            VALUES (?, ?, 'BUY', 100, 'SUBMITTED', CURRENT_TIMESTAMP, ?)
            RETURNING id
            """,
            Long.class,
            accountId,
            instrumentId,
            sharedIdempotencyKey);

        // Accept the order before concurrent execution
        executionService.acceptOrder(orderId);

        // Concurrent execution: Two threads try to execute the same order simultaneously
        int threadCount = 2;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        var order = orderRepository.findById(orderId).orElseThrow();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    executionService.executeOrder(order.getId(), sharedIdempotencyKey);
                    successCount.incrementAndGet();
                } catch (OrderExecutionService.OrderExecutionException e) {
                    if (e.getMessage().contains("already")) {
                        failureCount.incrementAndGet();
                    } else {
                        fail("Unexpected exception: " + e.getMessage());
                    }
                } catch (Exception e) {
                    fail("Unexpected exception: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // Verify: At most one execution succeeded
        assertTrue(successCount.get() <= 1,
            "At most one execution should succeed; got " + successCount.get());

        // Verify database state: only ONE Fill exists for this order
        List<Long> fills = jdbc.queryForList(
            "SELECT id FROM fills WHERE order_id = ?",
            Long.class,
            orderId);

        assertEquals(1, fills.size(), "Exactly one Fill should exist, not " + fills.size());

        // Verify cash transaction: only ONE cash movement
        List<Long> cashTransactions = jdbc.queryForList(
            "SELECT id FROM cash_transactions WHERE account_id = ?",
            Long.class,
            accountId);

        assertEquals(1, cashTransactions.size(),
            "Exactly one cash transaction should exist for account, not " + cashTransactions.size());

        // Verify final order status: FILLED exactly once
        String orderStatus = jdbc.queryForObject(
            "SELECT status FROM orders WHERE id = ?",
            String.class,
            orderId);

        assertEquals("FILLED", orderStatus,
            "Order should be FILLED after concurrent execution attempts");

        // Verify cash was deducted exactly once
        BigDecimal finalBalance = jdbc.queryForObject(
            "SELECT cash_balance FROM accounts WHERE id = ?",
            BigDecimal.class,
            accountId);

        BigDecimal expectedCost = new BigDecimal("100.05").multiply(new BigDecimal("100"));
        BigDecimal expectedBalance = new BigDecimal("50000.00").subtract(expectedCost);

        assertEquals(expectedBalance, finalBalance,
            "Cash should be deducted exactly once, not multiple times");
    }

    // Helper methods
    private Long createAccount(String suffix, BigDecimal initialCash) {
        Long userId = jdbc.queryForObject(
            """
            INSERT INTO users (email, password_hash)
            VALUES (?, 'test-password-hash')
            RETURNING id
            """,
            Long.class,
            "account-" + suffix + "@test.com");

        Long clientId = jdbc.queryForObject(
            """
            INSERT INTO clients (user_id, first_name, last_name)
            VALUES (?, 'Test', 'Client')
            RETURNING id
            """,
            Long.class,
            userId);

        return jdbc.queryForObject(
            """
            INSERT INTO accounts (client_id, account_number, cash_balance, currency)
            VALUES (?, ?, ?, 'USD')
            RETURNING id
            """,
            Long.class,
            clientId,
            "ACC-" + suffix,
            initialCash);
    }

    private Long createInstrument(String suffix) {
        return jdbc.queryForObject(
            """
            INSERT INTO instruments (symbol, name, instrument_class, exchange, country_code, currency, tradable)
            VALUES (?, 'Test Instrument', 'EQUITY', 'NYSE', 'US', 'USD', TRUE)
            RETURNING id
            """,
            Long.class,
            "TEST" + suffix);
    }

    private void createQuote(Long instrumentId, BigDecimal bidPrice, BigDecimal askPrice) {
        jdbc.update(
            """
            INSERT INTO market_quotes (instrument_id, bid_price, ask_price, last_price, quoted_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            """,
            instrumentId,
            bidPrice,
            askPrice,
            bidPrice);
    }
}
