package com.goatteen.trading.execution;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class OrderExecutionLifecycleIntegrationTest {

    private static final String TEST_DB = "leap_sprint4_rollback_test";
    private static final String TEST_USER = "leap_sprint4_test";

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        String password = System.getenv("LEAP_TEST_DB_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Set LEAP_TEST_DB_PASSWORD before running this test");
        }

        registry.add("spring.datasource.url",
                () -> "jdbc:postgresql://localhost:5432/" + TEST_DB);
        registry.add("spring.datasource.username", () -> TEST_USER);
        registry.add("spring.datasource.password", () -> password);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private OrderExecutionService executionService;

    @Test
    void shouldPersistRejectionWithoutSettlingTheOrder() {
        assertDedicatedTestDatabase();
        String suffix = newSuffix();

        Long accountId = createAccount(suffix, new BigDecimal("100.00"));
        Long instrumentId = createInstrument(suffix);
        Long orderId = createOrder(accountId, instrumentId, "SUBMITTED");

        // This specifically tests the rejection lifecycle. Business-rule
        // validation is covered separately by OrderValidationServiceTest.
        executionService.rejectOrder(orderId, "Insufficient cash balance");

        assertEquals("REJECTED", orderStatus(orderId));
        assertEquals("Insufficient cash balance", jdbc.queryForObject(
                "SELECT rejection_reason FROM orders WHERE id = ?",
                String.class, orderId));
        assertNotNull(jdbc.queryForObject(
                "SELECT completed_at FROM orders WHERE id = ?",
                java.time.LocalDateTime.class, orderId));

        assertEquals(1L, count(
                "SELECT COUNT(*) FROM order_status_history " +
                        "WHERE order_id = ? AND status = 'REJECTED'",
                orderId));
        assertEquals(0L, count(
                "SELECT COUNT(*) FROM order_status_history " +
                        "WHERE order_id = ? AND status = 'FILLED'",
                orderId));

        assertAmount("100.00", cashBalance(accountId));
        assertEquals(0L, count(
                "SELECT COUNT(*) FROM positions WHERE account_id = ?", accountId));
        assertEquals(0L, count(
                "SELECT COUNT(*) FROM cash_transactions WHERE account_id = ?", accountId));
        assertEquals(0L, count(
                "SELECT COUNT(*) FROM fills WHERE order_id = ?", orderId));

        // A rejected order cannot be executed afterward.
        OrderExecutionService.OrderExecutionException error = assertThrows(
                OrderExecutionService.OrderExecutionException.class,
                () -> executionService.executeOrder(orderId));
        assertEquals("Order is not in ACCEPTED state", error.getMessage());

        // The failed second attempt must not change any persisted state.
        assertEquals("REJECTED", orderStatus(orderId));
        assertAmount("100.00", cashBalance(accountId));
        assertEquals(0L, count(
                "SELECT COUNT(*) FROM cash_transactions WHERE account_id = ?", accountId));
        assertEquals(0L, count(
                "SELECT COUNT(*) FROM fills WHERE order_id = ?", orderId));
    }

    @Test
    void shouldNotSettleAnAlreadyFilledOrderTwice() {
        assertDedicatedTestDatabase();
        String suffix = newSuffix();

        Long accountId = createAccount(suffix, new BigDecimal("1000.00"));
        Long instrumentId = createInstrument(suffix);
        Long quoteId = createQuote(instrumentId);
        Long orderId = createOrder(accountId, instrumentId, "ACCEPTED");

        // The first execution must settle successfully.
        executionService.executeOrder(orderId);

        assertEquals("FILLED", orderStatus(orderId));
        assertAmount("849.80", cashBalance(accountId));
        assertAmount("2", positionQuantity(accountId, instrumentId));
        assertEquals(1L, count(
                "SELECT COUNT(*) FROM fills WHERE order_id = ?", orderId));
        assertEquals(1L, count(
                "SELECT COUNT(*) FROM cash_transactions WHERE account_id = ?", accountId));
        assertEquals(1L, count(
                "SELECT COUNT(*) FROM order_status_history " +
                        "WHERE order_id = ? AND status = 'FILLED'",
                orderId));

        Long actualQuoteId = jdbc.queryForObject(
                "SELECT quote_id FROM fills WHERE order_id = ?",
                Long.class, orderId);
        assertEquals(quoteId, actualQuoteId);

        Long fillId = jdbc.queryForObject(
                "SELECT id FROM fills WHERE order_id = ?",
                Long.class, orderId);
        Long ledgerFillId = jdbc.queryForObject(
                "SELECT fill_id FROM cash_transactions WHERE account_id = ?",
                Long.class, accountId);
        assertEquals(fillId, ledgerFillId);

        // A second attempt for the same FILLED order must be rejected.
        OrderExecutionService.OrderExecutionException error = assertThrows(
                OrderExecutionService.OrderExecutionException.class,
                () -> executionService.executeOrder(orderId));
        assertEquals("Order is not in ACCEPTED state", error.getMessage());

        // Check persisted state again: no second cash deduction, position
        // update, Fill, ledger entry or FILLED audit event.
        assertEquals("FILLED", orderStatus(orderId));
        assertAmount("849.80", cashBalance(accountId));
        assertAmount("2", positionQuantity(accountId, instrumentId));
        assertEquals(1L, count(
                "SELECT COUNT(*) FROM fills WHERE order_id = ?", orderId));
        assertEquals(1L, count(
                "SELECT COUNT(*) FROM cash_transactions WHERE account_id = ?", accountId));
        assertEquals(1L, count(
                "SELECT COUNT(*) FROM order_status_history " +
                        "WHERE order_id = ? AND status = 'FILLED'",
                orderId));
        assertAmount("-150.20", jdbc.queryForObject(
                "SELECT amount FROM cash_transactions WHERE account_id = ?",
                BigDecimal.class, accountId));
    }

    private void assertDedicatedTestDatabase() {
        assertEquals(TEST_DB, jdbc.queryForObject(
                "SELECT current_database()", String.class),
                "Refusing to run against an unexpected database");
        assertEquals(TEST_USER, jdbc.queryForObject(
                "SELECT current_user", String.class),
                "Refusing to run with an unexpected database user");
    }

    private String newSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private Long createAccount(String suffix, BigDecimal initialCash) {
        Long userId = jdbc.queryForObject("""
                INSERT INTO users (email, password_hash)
                VALUES (?, 'test-password-hash') RETURNING id
                """, Long.class, "lifecycle-" + suffix + "@example.test");
        Long clientId = jdbc.queryForObject("""
                INSERT INTO clients (user_id, first_name, last_name)
                VALUES (?, 'Lifecycle', 'Test') RETURNING id
                """, Long.class, userId);
        return jdbc.queryForObject("""
                INSERT INTO accounts (client_id, account_number, cash_balance, currency)
                VALUES (?, ?, ?, 'GBP') RETURNING id
                """, Long.class, clientId, "LEAP-LIFE-" + suffix, initialCash);
    }

    private Long createInstrument(String suffix) {
        return jdbc.queryForObject("""
                INSERT INTO instruments
                    (symbol, name, instrument_class, exchange,
                     country_code, currency, tradable)
                VALUES (?, 'Lifecycle Test Instrument', 'EQUITY',
                        'LSE', 'GB', 'GBP', TRUE)
                RETURNING id
                """, Long.class, "LIFE" + suffix);
    }

    private Long createQuote(Long instrumentId) {
        return jdbc.queryForObject("""
                INSERT INTO market_quotes
                    (instrument_id, bid_price, ask_price, last_price, quoted_at)
                VALUES (?, 74.90, 75.10, 75.00, CURRENT_TIMESTAMP)
                RETURNING id
                """, Long.class, instrumentId);
    }

    private Long createOrder(Long accountId, Long instrumentId, String status) {
        return jdbc.queryForObject("""
                INSERT INTO orders
                    (account_id, instrument_id, side, quantity, status, submitted_at)
                VALUES (?, ?, 'BUY', 2, ?, CURRENT_TIMESTAMP)
                RETURNING id
                """, Long.class, accountId, instrumentId, status);
    }

    private String orderStatus(Long orderId) {
        return jdbc.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private BigDecimal cashBalance(Long accountId) {
        return jdbc.queryForObject(
                "SELECT cash_balance FROM accounts WHERE id = ?",
                BigDecimal.class, accountId);
    }

    private BigDecimal positionQuantity(Long accountId, Long instrumentId) {
        return jdbc.queryForObject("""
                SELECT quantity FROM positions
                WHERE account_id = ? AND instrument_id = ?
                """, BigDecimal.class, accountId, instrumentId);
    }

    private long count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Long.class, arguments);
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
