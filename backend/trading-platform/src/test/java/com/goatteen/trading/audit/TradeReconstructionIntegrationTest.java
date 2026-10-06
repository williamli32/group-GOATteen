package com.goatteen.trading.audit;

import com.goatteen.trading.execution.OrderExecutionService;
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
class TradeReconstructionIntegrationTest {

        private static final String TEST_DB = "leap_sprint4_rollback_test";

        private static final String TEST_USER = "leap_sprint4_test";

        @DynamicPropertySource
        static void configureDatabase(
                        DynamicPropertyRegistry registry) {

                String password = System.getenv("LEAP_TEST_DB_PASSWORD");

                if (password == null
                                || password.isBlank()) {

                        throw new IllegalStateException(
                                        "Set LEAP_TEST_DB_PASSWORD before running this test");
                }

                registry.add(
                                "spring.datasource.url",
                                () -> "jdbc:postgresql://localhost:5432/"
                                                + TEST_DB);

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
        private TradeReconstructionService reconstructionService;

        @Test
        void shouldReconstructFilledTradeFromPersistedData() {

                assertDedicatedTestDatabase();

                String suffix = newSuffix();

                Long accountId = createAccount(
                                suffix,
                                new BigDecimal("1000.00"));

                Long instrumentId = createInstrument(suffix);

                createQuote(instrumentId);

                Long orderId = createSubmittedOrder(
                                accountId,
                                instrumentId);

                executionService.acceptOrder(orderId);

                executionService.executeOrder(orderId);

                TradeAuditTrail audit = reconstructionService
                                .reconstructTrade(orderId);

                assertTrue(
                                audit.isFullyReconstructable());

                assertEquals(
                                "FILLED",
                                audit.getCurrentStatus().name());

                assertEquals(
                                orderId,
                                audit.getOrderId());

                assertEquals(
                                accountId,
                                audit.getAccountId());

                assertEquals(
                                instrumentId,
                                audit.getInstrumentId());

                assertEquals(
                                "BUY",
                                audit.getOrderSide());

                assertAmount(
                                "2",
                                audit.getOrderQuantity());

                assertAmount(
                                "2",
                                audit.getFillQuantity());

                assertAmount(
                                "75.10",
                                audit.getFillPrice());

                assertAmount(
                                "-150.20",
                                audit.getCashMovement());

                assertAmount(
                                "849.80",
                                audit.getBalanceAfter());

                assertAmount(
                                "0",
                                audit.getQuantityBefore());

                assertAmount(
                                "2",
                                audit.getQuantityAfter());

                assertAmount(
                                "2",
                                audit.getQuantityChange());

                assertEquals(
                                3,
                                audit.getStatusHistory().size());

                assertEquals(
                                "SUBMITTED",
                                audit.getStatusHistory()
                                                .get(0)
                                                .getStatus()
                                                .name());

                assertEquals(
                                "ACCEPTED",
                                audit.getStatusHistory()
                                                .get(1)
                                                .getStatus()
                                                .name());

                assertEquals(
                                "FILLED",
                                audit.getStatusHistory()
                                                .get(2)
                                                .getStatus()
                                                .name());
        }

        @Test
        void shouldReconstructRejectedTradeFromPersistedData() {

                assertDedicatedTestDatabase();

                String suffix = newSuffix();

                Long accountId = createAccount(
                                suffix,
                                new BigDecimal("100.00"));

                Long instrumentId = createInstrument(suffix);

                Long orderId = createSubmittedOrder(
                                accountId,
                                instrumentId);

                executionService.rejectOrder(
                                orderId,
                                "Insufficient cash balance");

                TradeAuditTrail audit = reconstructionService
                                .reconstructTrade(orderId);

                assertTrue(
                                audit.isFullyReconstructable());

                assertEquals(
                                "REJECTED",
                                audit.getCurrentStatus().name());

                assertEquals(
                                "Insufficient cash balance",
                                audit.getRejectionReason());

                assertNull(
                                audit.getFillId());

                assertEquals(
                                2,
                                audit.getStatusHistory().size());

                assertEquals(
                                "SUBMITTED",
                                audit.getStatusHistory()
                                                .get(0)
                                                .getStatus()
                                                .name());

                assertEquals(
                                "REJECTED",
                                audit.getStatusHistory()
                                                .get(1)
                                                .getStatus()
                                                .name());

                assertEquals(
                                0L,
                                count(
                                                "SELECT COUNT(*) FROM fills " +
                                                                "WHERE order_id = ?",
                                                orderId));

                assertEquals(
                                0L,
                                count(
                                                "SELECT COUNT(*) " +
                                                                "FROM position_history ph " +
                                                                "JOIN fills f ON f.id = ph.fill_id " +
                                                                "WHERE f.order_id = ?",
                                                orderId));
        }

        @Test
        void shouldFailWhenFilledTradeAuditDataIsIncomplete() {

                assertDedicatedTestDatabase();

                String suffix = newSuffix();

                Long accountId = createAccount(
                                suffix,
                                new BigDecimal("1000.00"));

                Long instrumentId = createInstrument(suffix);

                createQuote(instrumentId);

                Long orderId = createSubmittedOrder(
                                accountId,
                                instrumentId);

                executionService.acceptOrder(orderId);

                executionService.executeOrder(orderId);

                Long fillId = jdbc.queryForObject(
                                "SELECT id FROM fills " +
                                                "WHERE order_id = ?",
                                Long.class,
                                orderId);

                assertNotNull(fillId);

                /*
                 * Deliberately remove persisted position history.
                 * Reconstruction must refuse to call the trade complete.
                 */
                jdbc.update(
                                "DELETE FROM position_history " +
                                                "WHERE fill_id = ?",
                                fillId);

                TradeReconstructionService.TradeReconstructionException error = assertThrows(
                                TradeReconstructionService.TradeReconstructionException.class,
                                () -> reconstructionService
                                                .reconstructTrade(orderId));

                assertTrue(
                                error.getMessage()
                                                .contains(
                                                                "position history"));
        }

        @Test
        void shouldDetectInconsistentCashSettlementBalance() {

                assertDedicatedTestDatabase();

                String suffix = newSuffix();

                Long accountId = createAccount(
                                suffix,
                                new BigDecimal("1000.00"));

                Long instrumentId = createInstrument(suffix);

                createQuote(instrumentId);

                Long orderId = createSubmittedOrder(
                                accountId,
                                instrumentId);

                executionService.acceptOrder(orderId);
                executionService.executeOrder(orderId);

                Long fillId = jdbc.queryForObject(
                                "SELECT id FROM fills WHERE order_id = ?",
                                Long.class,
                                orderId);

                assertNotNull(fillId);

                /*
                 * Deliberately corrupt the settlement arithmetic:
                 * balance_before + amount will no longer equal balance_after.
                 */
                jdbc.update(
                                """
                                                UPDATE cash_transactions
                                                SET balance_after = balance_after + 1.00
                                                WHERE fill_id = ?
                                                """,
                                fillId);

                TradeIntegrityReport report = reconstructionService
                                .verifyTradeIntegrity(orderId);

                assertFalse(report.isValid());

                assertTrue(
                                report.getMessage()
                                                .contains(
                                                                "Cash transaction balance is inconsistent"));
        }

        @Test
        void shouldDetectCurrentCashBalanceMismatch() {

                assertDedicatedTestDatabase();

                String suffix = newSuffix();

                Long accountId = createAccount(
                                suffix,
                                new BigDecimal("1000.00"));

                Long instrumentId = createInstrument(suffix);

                createQuote(instrumentId);

                Long orderId = createSubmittedOrder(
                                accountId,
                                instrumentId);

                executionService.acceptOrder(orderId);
                executionService.executeOrder(orderId);

                /*
                 * Deliberately corrupt the current account balance while leaving
                 * the cash transaction ledger unchanged.
                 */
                jdbc.update(
                                """
                                                UPDATE accounts
                                                SET cash_balance = cash_balance + 1.00
                                                WHERE id = ?
                                                """,
                                accountId);

                TradeIntegrityReport report = reconstructionService
                                .verifyTradeIntegrity(orderId);

                assertFalse(report.isValid());

                assertTrue(
                                report.getMessage()
                                                .contains(
                                                                "Current account cash balance does not match latest cash transaction"));
        }

        @Test
        void shouldDetectCurrentPositionMismatch() {

                assertDedicatedTestDatabase();

                String suffix = newSuffix();

                Long accountId = createAccount(
                                suffix,
                                new BigDecimal("1000.00"));

                Long instrumentId = createInstrument(suffix);

                createQuote(instrumentId);

                Long orderId = createSubmittedOrder(
                                accountId,
                                instrumentId);

                executionService.acceptOrder(orderId);
                executionService.executeOrder(orderId);

                /*
                 * Deliberately corrupt the current position while leaving
                 * position history unchanged.
                 */
                jdbc.update(
                                """
                                                UPDATE positions
                                                SET quantity = quantity + 1
                                                WHERE account_id = ?
                                                  AND instrument_id = ?
                                                """,
                                accountId,
                                instrumentId);

                TradeIntegrityReport report = reconstructionService
                                .verifyTradeIntegrity(orderId);

                assertFalse(report.isValid());

                assertTrue(
                                report.getMessage()
                                                .contains(
                                                                "Current position does not match latest position history"));
        }

        private Long createAccount(
                        String suffix,
                        BigDecimal initialCash) {

                Long userId = jdbc.queryForObject(
                                """
                                                INSERT INTO users
                                                    (email, password_hash)
                                                VALUES
                                                    (?, 'test-password-hash')
                                                RETURNING id
                                                """,
                                Long.class,
                                "reconstruct-"
                                                + suffix
                                                + "@example.test");

                Long clientId = jdbc.queryForObject(
                                """
                                                INSERT INTO clients
                                                    (user_id, first_name, last_name)
                                                VALUES
                                                    (?, 'Reconstruction', 'Test')
                                                RETURNING id
                                                """,
                                Long.class,
                                userId);

                return jdbc.queryForObject(
                                """
                                                INSERT INTO accounts
                                                    (client_id,
                                                     account_number,
                                                     cash_balance,
                                                     currency)
                                                VALUES
                                                    (?, ?, ?, 'GBP')
                                                RETURNING id
                                                """,
                                Long.class,
                                clientId,
                                "LEAP-RECON-" + suffix,
                                initialCash);
        }

        private Long createInstrument(
                        String suffix) {

                return jdbc.queryForObject(
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
                                                    (?, 'Reconstruction Test Instrument',
                                                     'EQUITY',
                                                     'LSE',
                                                     'GB',
                                                     'GBP',
                                                     TRUE)
                                                RETURNING id
                                                """,
                                Long.class,
                                "RECON" + suffix);
        }

        private Long createQuote(
                        Long instrumentId) {

                return jdbc.queryForObject(
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
                                                RETURNING id
                                                """,
                                Long.class,
                                instrumentId);
        }

        private Long createSubmittedOrder(
                        Long accountId,
                        Long instrumentId) {

                Long orderId = jdbc.queryForObject(
                                """
                                                INSERT INTO orders
                                                    (account_id,
                                                     instrument_id,
                                                     side,
                                                     quantity,
                                                     status,
                                                     submitted_at)
                                                VALUES
                                                    (?, ?, 'BUY', 2,
                                                     'SUBMITTED',
                                                     CURRENT_TIMESTAMP)
                                                RETURNING id
                                                """,
                                Long.class,
                                accountId,
                                instrumentId);

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

                return orderId;
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

        private String newSuffix() {

                return UUID.randomUUID()
                                .toString()
                                .substring(0, 8);
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
                        String expected,
                        BigDecimal actual) {

                assertNotNull(actual);

                assertEquals(
                                0,
                                new BigDecimal(expected)
                                                .compareTo(actual));
        }
}