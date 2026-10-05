package com.goatteen.trading.execution;

import com.goatteen.trading.order.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration test for Goal 5: Account Isolation
 * Verify that settlement on one account does not affect another account's cash balance or positions.
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderExecutionAccountIsolationTest {

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
     * Goal 5: Account Isolation
     * Verify that settlement on one account does not affect another account's cash balance or positions.
     */
    @Test
    @Transactional
    void testAccountIsolation() {
        // Setup: Create two separate accounts
        Long account1Id = createAccount("isolation-1", new BigDecimal("10000.00"));
        Long account2Id = createAccount("isolation-2", new BigDecimal("5000.00"));

        // Create instrument and quote
        Long instrumentId = createInstrument("isolation-test");
        createQuote(instrumentId, new BigDecimal("150.00"), new BigDecimal("150.05"));

        // Create orders for each account
        Long order1Id = jdbc.queryForObject(
            """
            INSERT INTO orders
                (account_id, instrument_id, side, quantity, status, submitted_at, idempotency_key)
            VALUES (?, ?, 'BUY', 10, 'SUBMITTED', CURRENT_TIMESTAMP, ?)
            RETURNING id
            """,
            Long.class,
            account1Id,
            instrumentId,
            "isolation-order-1");

        Long order2Id = jdbc.queryForObject(
            """
            INSERT INTO orders
                (account_id, instrument_id, side, quantity, status, submitted_at, idempotency_key)
            VALUES (?, ?, 'BUY', 5, 'SUBMITTED', CURRENT_TIMESTAMP, ?)
            RETURNING id
            """,
            Long.class,
            account2Id,
            instrumentId,
            "isolation-order-2");

        // Accept orders before execution
        executionService.acceptOrder(order1Id);
        executionService.acceptOrder(order2Id);

        // Store initial balances
        BigDecimal initialBalance1 = jdbc.queryForObject(
            "SELECT cash_balance FROM accounts WHERE id = ?",
            BigDecimal.class,
            account1Id);

        BigDecimal initialBalance2 = jdbc.queryForObject(
            "SELECT cash_balance FROM accounts WHERE id = ?",
            BigDecimal.class,
            account2Id);

        // Execute order on account1
        var order1 = orderRepository.findById(order1Id).orElseThrow();
        executionService.executeOrder(order1.getId(), "isolation-order-1");

        // Verify account1 balance decreased
        BigDecimal updatedBalance1 = jdbc.queryForObject(
            "SELECT cash_balance FROM accounts WHERE id = ?",
            BigDecimal.class,
            account1Id);

        BigDecimal expectedCost1 = new BigDecimal("150.05").multiply(new BigDecimal("10"));
        BigDecimal expectedBalance1 = initialBalance1.subtract(expectedCost1);
        assertEquals(expectedBalance1, updatedBalance1,
            "Account1 cash should be reduced by order execution");

        // Verify account2 balance unchanged
        BigDecimal updatedBalance2 = jdbc.queryForObject(
            "SELECT cash_balance FROM accounts WHERE id = ?",
            BigDecimal.class,
            account2Id);

        assertEquals(initialBalance2, updatedBalance2,
            "Account2 cash should not be affected by Account1's order");

        // Execute order on account2
        var order2 = orderRepository.findById(order2Id).orElseThrow();
        executionService.executeOrder(order2.getId(), "isolation-order-2");

        // Verify account2 balance decreased by correct amount
        updatedBalance2 = jdbc.queryForObject(
            "SELECT cash_balance FROM accounts WHERE id = ?",
            BigDecimal.class,
            account2Id);

        BigDecimal expectedCost2 = new BigDecimal("150.05").multiply(new BigDecimal("5"));
        BigDecimal expectedBalance2 = initialBalance2.subtract(expectedCost2);
        assertEquals(expectedBalance2, updatedBalance2,
            "Account2 cash should be reduced by only its own order");

        // Final verification: account1 unchanged after account2's order
        BigDecimal finalBalance1 = jdbc.queryForObject(
            "SELECT cash_balance FROM accounts WHERE id = ?",
            BigDecimal.class,
            account1Id);

        assertEquals(expectedBalance1, finalBalance1,
            "Account1 final balance should be unchanged after Account2's order");
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
