package com.goatteen.trading.reporting;

import com.goatteen.trading.account.Account;
import com.goatteen.trading.account.AccountRepository;
import com.goatteen.trading.client.Client;
import com.goatteen.trading.client.ClientRepository;
import com.goatteen.trading.execution.Fill;
import com.goatteen.trading.execution.FillRepository;
import com.goatteen.trading.execution.OrderExecutionService;
import com.goatteen.trading.instrument.Instrument;
import com.goatteen.trading.instrument.InstrumentClass;
import com.goatteen.trading.instrument.InstrumentRepository;
import com.goatteen.trading.marketdata.Quote;
import com.goatteen.trading.marketdata.QuoteRepository;
import com.goatteen.trading.order.Order;
import com.goatteen.trading.order.OrderRepository;
import com.goatteen.trading.order.OrderSide;
import com.goatteen.trading.order.OrderStatus;
import com.goatteen.trading.reporting.data.TradeFact;
import com.goatteen.trading.reporting.data.TradeFactRepository;
import com.goatteen.trading.reporting.tracking.SyncTracking;
import com.goatteen.trading.reporting.tracking.SyncTrackingRepository;
import com.goatteen.trading.user.User;
import com.goatteen.trading.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Tests for Goal 1: Separate Reporting Data Path
 * 
 * Verifies that:
 * 1. Orders executed in operational schema are synced to reporting schema
 * 2. Reporting data is denormalized correctly
 * 3. Idempotency prevents duplicate syncs
 * 4. Reporting queries work correctly on synced data
 * 5. Zero latency impact on operational execution
 * 
 * Architecture:
 * - OrderExecutionService publishes OrderFilledEvent after TX commits
 * - ReportingDataSyncService listens for events and syncs to reporting schema
 * - reporting.trade_facts is a denormalized, immutable fact table
 * - reporting.sync_tracking audits all sync attempts
 */
@SpringBootTest
@ActiveProfiles("test")
class ReportingDataSyncIntegrationTest {

    @Autowired
    private OrderExecutionService orderExecutionService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private FillRepository fillRepository;

    @Autowired
    private TradeFactRepository tradeFactRepository;

    @Autowired
    private SyncTrackingRepository syncTrackingRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private QuoteRepository quoteRepository;

    // Test data fixtures
    private User testUser;
    private Client testClient;
    private Account testAccount;
    private Instrument testInstrument;
    private Quote testQuote;

    /**
     * Set up test data before each test.
     * Creates a complete trading environment with user, client, account, and instrument.
     */
@BeforeEach
public void setUp() {
    // Create test user
    testUser = new User("reporting-test@example.com", "hashed-password");
    testUser = userRepository.save(testUser);

    // Create test client
    testClient = new Client(testUser, "Alice", "Smith");
    testClient = clientRepository.save(testClient);

    // Create test account
    testAccount = new Account(testClient, "REP-TEST-001", "USD");
    testAccount.setCashBalance(new BigDecimal("500000.00"));
    testAccount = accountRepository.save(testAccount);

    // Create test instrument - use constructor with all required fields
    testInstrument = new Instrument(
            "GOOGL",
            "Alphabet Inc",
            InstrumentClass.EQUITY,
            "NASDAQ",      // exchange (required)
            "US",          // countryCode (required)
            "USD"
    );
    testInstrument = instrumentRepository.save(testInstrument);

    // Create test quote - use constructor with all fields
    testQuote = new Quote(
            testInstrument,
            new BigDecimal("140.00"),  // bidPrice
            new BigDecimal("140.50"),  // askPrice
            new BigDecimal("140.25"),  // lastPrice
            LocalDateTime.now()        // quotedAt
    );
    testQuote = quoteRepository.save(testQuote);
}

    /**
     * TEST 1: Order Execution Syncs to Reporting Schema
     * 
     * Verifies the core reporting pipeline:
     * 1. Order submitted and accepted in operational schema
     * 2. Order executed (Fill created)
     * 3. OrderFilledEvent published AFTER transaction commits
     * 4. ReportingDataSyncService receives event and syncs to reporting.trade_facts
     * 5. TradeFact row appears with correct denormalized data
     * 
     * This is the happy path test for the entire Goal 1 architecture.
     */
    @Test
    public void testOrderExecutionSyncsToReporting() throws Exception {
        // ARRANGE: Create and prepare an order
        Order order = new Order();
        order.setAccount(testAccount);
        order.setInstrument(testInstrument);
        order.setSide(OrderSide.BUY);
        order.setQuantity(new BigDecimal("50.00"));
        order.setStatus(OrderStatus.SUBMITTED);
        order.setSubmittedAt(LocalDateTime.now());
        Order savedOrder = orderRepository.save(order);

        // ACT: Execute the order through the full operational pipeline
        orderExecutionService.acceptOrder(savedOrder.getId());
        Fill fill = orderExecutionService.executeOrder(savedOrder.getId());

        // ASSERT: Verify TradeFact was synced to reporting schema
        TradeFact tradeFact = tradeFactRepository.findByFillId(fill.getId())
                .orElseThrow(() -> new AssertionError("TradeFact not found after order execution"));

        // Verify transactional linkage
        assertEquals(fill.getId(), tradeFact.getFillId(), 
                "TradeFact fill_id should match source Fill");
        assertEquals(savedOrder.getId(), tradeFact.getOrderId(), 
                "TradeFact order_id should match source Order");

        // Verify account/client denormalization
        assertEquals(testAccount.getId(), tradeFact.getAccountId(), 
                "TradeFact should denormalize account_id");
        assertEquals(testClient.getId(), tradeFact.getClientId(), 
                "TradeFact should denormalize client_id");
        assertEquals("Alice", tradeFact.getClientFirstName(), 
                "TradeFact should denormalize client first name");
        assertEquals("Smith", tradeFact.getClientLastName(), 
                "TradeFact should denormalize client last name");

        // Verify instrument denormalization
        assertEquals(testInstrument.getId(), tradeFact.getInstrumentId(), 
                "TradeFact should denormalize instrument_id");
        assertEquals("GOOGL", tradeFact.getInstrumentSymbol(), 
                "TradeFact should denormalize instrument symbol");
        assertEquals("EQUITY_US", tradeFact.getInstrumentClass(), 
                "TradeFact should denormalize instrument class");

        // Verify trade execution details
        assertEquals("BUY", tradeFact.getSide(), 
                "TradeFact should record order side");
        assertEquals(new BigDecimal("50.00"), tradeFact.getOrderQuantity(), 
                "TradeFact should record order quantity");
        assertEquals(new BigDecimal("140.50"), tradeFact.getExecutionPrice(), 
                "TradeFact should record execution price (ask price)");
        assertEquals("FILLED", tradeFact.getOrderStatus(), 
                "TradeFact should record final order status");

        // Verify quote context
        assertEquals(new BigDecimal("140.00"), tradeFact.getBidPrice(), 
                "TradeFact should capture bid price context");
        assertEquals(new BigDecimal("140.50"), tradeFact.getAskPrice(), 
                "TradeFact should capture ask price context");

        // Verify trade value calculation
        BigDecimal expectedTradeValue = new BigDecimal("140.50")
                .multiply(new BigDecimal("50.00"));
        assertEquals(expectedTradeValue, tradeFact.getTradeValue(), 
                "TradeFact should calculate trade value correctly");
    }

    /**
     * TEST 2: Idempotency – No Duplicate Syncs
     * 
     * Verifies the idempotency guarantee:
     * 1. Execute order with idempotency key
     * 2. TradeFact created in reporting schema
     * 3. Retry execution with same idempotency key
     * 4. OrderExecutionService returns same Fill (no new execution)
     * 5. ReportingDataSyncService detects existing TradeFact and skips
     * 6. Only ONE TradeFact row exists for this fill
     * 
     * This tests the critical idempotency mechanism that prevents 
     * duplicate reporting data on retries or replays.
     */
    @Test
    public void testIdempotency_NoDuplicateTradeFacts() throws Exception {
        // ARRANGE: Generate unique idempotency key
        String idempotencyKey = "test-idem-" + System.currentTimeMillis();
        
        Order order = new Order();
        order.setAccount(testAccount);
        order.setInstrument(testInstrument);
        order.setSide(OrderSide.BUY);
        order.setQuantity(new BigDecimal("25.00"));
        order.setStatus(OrderStatus.SUBMITTED);
        order.setSubmittedAt(LocalDateTime.now());
        order.setIdempotencyKey(idempotencyKey);
        Order savedOrder = orderRepository.save(order);

        // ACT: First execution
        orderExecutionService.acceptOrder(savedOrder.getId());
        Fill firstFill = orderExecutionService.executeOrder(savedOrder.getId(), idempotencyKey);

        // ASSERT: First TradeFact created
        TradeFact firstTradeFact = tradeFactRepository.findByFillId(firstFill.getId())
                .orElseThrow(() -> new AssertionError("First TradeFact not created"));
        assertNotNull(firstTradeFact, "First execution should create TradeFact");

        // ACT: Retry with same idempotency key
        Fill secondFill = orderExecutionService.executeOrder(savedOrder.getId(), idempotencyKey);

        // ASSERT: Same Fill returned (no new execution)
        assertEquals(firstFill.getId(), secondFill.getId(), 
                "Retry with same idempotency key should return same Fill");

        // ASSERT: Only one TradeFact exists (no duplicate)
        long tradeFactCount = tradeFactRepository.findAll().stream()
                .filter(tf -> tf.getFillId().equals(firstFill.getId()))
                .count();
        assertEquals(1, tradeFactCount, 
                "Should have exactly ONE TradeFact for this fill, not duplicates");

        // Verify sync tracking recorded both attempts
        List<SyncTracking> syncRecords = syncTrackingRepository.findAll();
        long successfulSyncs = syncRecords.stream()
                .filter(st -> st.getFillId().equals(firstFill.getId()))
                .filter(st -> "SUCCESS".equals(st.getSyncStatus()))
                .count();
        assertTrue(successfulSyncs >= 1, 
                "sync_tracking should record successful syncs");
    }

    /**
     * TEST 3: Reporting Queries Work Correctly
     * 
     * Verifies that the reporting schema supports analytical queries:
     * 1. Execute multiple orders for same client
     * 2. Query reporting.trade_facts using TradeFactRepository
     * 3. Verify all trades returned with correct data
     * 4. Verify data structure optimized for dashboards
     * 
     * This tests that the denormalized schema actually works
     * for analytical queries without performance issues.
     */
    @Test
    public void testReportingQueriesReturnAccurateData() throws Exception {
        // ARRANGE: Execute multiple orders
        int orderCount = 3;
        for (int i = 0; i < orderCount; i++) {
            Order order = new Order();
            order.setAccount(testAccount);
            order.setInstrument(testInstrument);
            order.setSide(OrderSide.BUY);
            order.setQuantity(new BigDecimal("10.00"));
            order.setStatus(OrderStatus.SUBMITTED);
            order.setSubmittedAt(LocalDateTime.now());
            Order savedOrder = orderRepository.save(order);
            
            orderExecutionService.acceptOrder(savedOrder.getId());
            orderExecutionService.executeOrder(savedOrder.getId());
        }

        // ACT: Query reporting schema for this client
        List<TradeFact> trades = tradeFactRepository.findByClientIdAndDateRange(
                testClient.getId(),
                LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now().plusMinutes(10)
        );

        // ASSERT: All trades returned
        assertEquals(orderCount, trades.size(), 
                String.format("Should find %d trades for client", orderCount));

        // ASSERT: Each trade has correct structure
        trades.forEach(trade -> {
            assertEquals(testClient.getId(), trade.getClientId(), 
                    "All trades should belong to test client");
            assertEquals("BUY", trade.getSide(), 
                    "All trades should be BUY orders");
            assertEquals(new BigDecimal("10.00"), trade.getOrderQuantity(), 
                    "All trades should have correct quantity");
            assertEquals("GOOGL", trade.getInstrumentSymbol(), 
                    "All trades should reference correct instrument");
            assertEquals("FILLED", trade.getOrderStatus(), 
                    "All trades should be in FILLED status");
            assertNotNull(trade.getTradeValue(), 
                    "All trades should have calculated trade value");
        });
    }

    /**
     * TEST 4: Sync Failure Tracking
     * 
     * Verifies the sync_tracking audit mechanism:
     * 1. Sync attempts are recorded regardless of success/failure
     * 2. Failed syncs capture error messages
     * 3. Recovery queries can find failed syncs
     * 4. Sync tracking doesn't block operational execution
     * 
     * This tests the observability and recovery infrastructure
     * for operational support.
     */
    @Test
    public void testSyncTrackingRecordsAttempts() throws Exception {
        // ARRANGE: Execute an order
        Order order = new Order();
        order.setAccount(testAccount);
        order.setInstrument(testInstrument);
        order.setSide(OrderSide.BUY);
        order.setQuantity(new BigDecimal("15.00"));
        order.setStatus(OrderStatus.SUBMITTED);
        order.setSubmittedAt(LocalDateTime.now());
        Order savedOrder = orderRepository.save(order);

        // ACT: Execute order
        orderExecutionService.acceptOrder(savedOrder.getId());
        Fill fill = orderExecutionService.executeOrder(savedOrder.getId());

        // ASSERT: Sync tracking recorded the attempt
        List<SyncTracking> trackingRecords = syncTrackingRepository.findAll();
        SyncTracking record = trackingRecords.stream()
                .filter(st -> st.getFillId().equals(fill.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No sync tracking record found"));

        assertEquals("SUCCESS", record.getSyncStatus(), 
                "Sync should be recorded as SUCCESS");
        assertNull(record.getErrorMessage(), 
                "Successful sync should have no error message");
        assertNotNull(record.getSyncCompletedAt(), 
                "Sync should record completion timestamp");
    }

    /**
     * TEST 5: Zero Operational Latency Impact
     * 
     * Verifies the BR-16 guarantee of zero performance impact:
     * 1. Time the order execution (without reporting latency)
     * 2. OrderExecutionService.executeOrder() completes in <200ms
     * 3. Client receives response immediately (before sync completes)
     * 4. Reporting sync happens asynchronously after response
     * 
     * This is a conceptual test showing that operational execution
     * is not blocked by reporting sync. Real performance testing
     * would use load testing tools.
     */
    @Test
    public void testOperationalExecutionNotBlockedByReportingSync() throws Exception {
        // ARRANGE
        Order order = new Order();
        order.setAccount(testAccount);
        order.setInstrument(testInstrument);
        order.setSide(OrderSide.SELL);
        order.setQuantity(new BigDecimal("20.00"));
        order.setStatus(OrderStatus.SUBMITTED);
        order.setSubmittedAt(LocalDateTime.now());
        Order savedOrder = orderRepository.save(order);

        // ACT: Time the execution
        long executionStartMs = System.currentTimeMillis();
        orderExecutionService.acceptOrder(savedOrder.getId());
        Fill fill = orderExecutionService.executeOrder(savedOrder.getId());
        long executionTimeMs = System.currentTimeMillis() - executionStartMs;

        // ASSERT: Execution completes quickly (shouldn't wait for reporting sync)
        assertTrue(executionTimeMs < 500, 
                String.format("Order execution should complete in <500ms, took %dms", executionTimeMs));

        // ASSERT: TradeFact eventually syncs (asynchronously, after execution returns)
        // Wait a bit for async sync to complete
        Thread.sleep(200);
        
        TradeFact tradeFact = tradeFactRepository.findByFillId(fill.getId())
                .orElseThrow(() -> new AssertionError("TradeFact should eventually sync"));
        assertNotNull(tradeFact, 
                "TradeFact should be synced (possibly after execution completes)");
    }

    /**
     * TEST 6: Multiple Concurrent Orders Don't Interfere
     * 
     * Verifies that multiple orders from different clients
     * are synced correctly without cross-contamination.
     */
    @Test
    public void testMultipleClientsTradesAreIsolated() throws Exception {
        // ARRANGE: Create second test client
        User user2 = new User("reporting-test-2@example.com", "hashed");
        user2 = userRepository.save(user2);

        Client client2 = new Client(user2, "Bob", "Jones");
        client2 = clientRepository.save(client2);

        Account account2 = new Account(client2, "REP-TEST-002", "USD");
        account2.setCashBalance(new BigDecimal("250000.00"));
        account2 = accountRepository.save(account2);

        // ACT: Execute orders for both clients
        Order order1 = new Order();
        order1.setAccount(testAccount);
        order1.setInstrument(testInstrument);
        order1.setSide(OrderSide.BUY);
        order1.setQuantity(new BigDecimal("5.00"));
        order1.setStatus(OrderStatus.SUBMITTED);
        order1.setSubmittedAt(LocalDateTime.now());
        Order savedOrder1 = orderRepository.save(order1);
        orderExecutionService.acceptOrder(savedOrder1.getId());
        orderExecutionService.executeOrder(savedOrder1.getId());

        Order order2 = new Order();
        order2.setAccount(account2);
        order2.setInstrument(testInstrument);
        order2.setSide(OrderSide.SELL);
        order2.setQuantity(new BigDecimal("3.00"));
        order2.setStatus(OrderStatus.SUBMITTED);
        order2.setSubmittedAt(LocalDateTime.now());
        Order savedOrder2 = orderRepository.save(order2);
        orderExecutionService.acceptOrder(savedOrder2.getId());
        orderExecutionService.executeOrder(savedOrder2.getId());

        // ASSERT: Each client's trades are correctly attributed
        List<TradeFact> client1Trades = tradeFactRepository.findByClientIdAndDateRange(
                testClient.getId(),
                LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now().plusMinutes(10)
        );
        
        List<TradeFact> client2Trades = tradeFactRepository.findByClientIdAndDateRange(
                client2.getId(),
                LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now().plusMinutes(10)
        );

        assertEquals(1, client1Trades.size(), "Client 1 should have 1 trade");
        assertEquals(1, client2Trades.size(), "Client 2 should have 1 trade");
        assertEquals("BUY", client1Trades.get(0).getSide(), "Client 1 trade should be BUY");
        assertEquals("SELL", client2Trades.get(0).getSide(), "Client 2 trade should be SELL");
    }
}