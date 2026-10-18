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
import com.goatteen.trading.portfolio.Position;
import com.goatteen.trading.portfolio.PositionRepository;
import com.goatteen.trading.reporting.data.TradeFact;
import com.goatteen.trading.reporting.data.TradeFactRepository;
import com.goatteen.trading.reporting.data.OrderFact;
import com.goatteen.trading.reporting.data.OrderFactRepository;
import com.goatteen.trading.reporting.metrics.ReportingFilters;
import com.goatteen.trading.reporting.metrics.TradingActivityMetrics;
import com.goatteen.trading.reporting.metrics.TradingActivityMetricsService;
import com.goatteen.trading.reporting.metrics.ActivityTrendService;
import com.goatteen.trading.reporting.metrics.InstrumentMetricsService;
import com.goatteen.trading.reporting.metrics.ClientMetricsService;
import com.goatteen.trading.reporting.metrics.ReportingActivityService;
import com.goatteen.trading.reporting.metrics.PeriodicReportingService;
import com.goatteen.trading.reporting.metrics.ReportingPeriod;
import com.goatteen.trading.reporting.metrics.BusinessInsightsService;
import com.goatteen.trading.reporting.event.OrderFilledEvent;
import com.goatteen.trading.reporting.sync.OrderFactSyncService;
import com.goatteen.trading.reporting.tracking.SyncTracking;
import com.goatteen.trading.reporting.tracking.SyncTrackingRepository;
import com.goatteen.trading.user.User;
import com.goatteen.trading.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

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
        private OrderFactRepository orderFactRepository;

        @Autowired
        private TradingActivityMetricsService tradingActivityMetricsService;

        @Autowired
        private OrderFactSyncService orderFactSyncService;

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

        @Autowired
        private PositionRepository positionRepository;

        @Autowired
        private JdbcTemplate jdbc;
        @Autowired
        private PlatformTransactionManager transactionManager;
        @Autowired
        private ApplicationEventPublisher events;
        @Autowired
        private ActivityTrendService activityTrendService;
        @Autowired
        private InstrumentMetricsService instrumentMetricsService;
        @Autowired
        private ClientMetricsService clientMetricsService;
        @Autowired
        private ReportingActivityService reportingActivityService;
        @Autowired
        private PeriodicReportingService periodicReportingService;
        @Autowired
        private BusinessInsightsService businessInsightsService;

        private User testUser;
        private Client testClient;
        private Account testAccount;
        private Instrument testInstrument;
        private Quote testQuote;

        @BeforeEach
        void setUp() {
                assertTrue(jdbc.queryForObject("SELECT current_database()", String.class).endsWith("_test"),
                                "This fixture-writing suite must use a dedicated database ending in _test");

                testUser = new User(
                                "reporting-test-" + java.util.UUID.randomUUID() + "@example.com",
                                "hashed-password");

                testUser = userRepository.save(testUser);

                testClient = new Client(
                                testUser,
                                "Alice",
                                "Smith");

                testClient = clientRepository.save(testClient);

                testAccount = new Account(
                                testClient,
                                "REP-TEST-" + java.util.UUID.randomUUID(),
                                "USD");

                testAccount.setCashBalance(
                                new BigDecimal("500000.00"));

                testAccount = accountRepository.save(testAccount);

                testInstrument = new Instrument(
                                "T" + java.util.UUID.randomUUID()
                                                .toString()
                                                .replace("-", "")
                                                .substring(0, 7),
                                "Test Instrument",
                                InstrumentClass.EQUITY,
                                "NASDAQ",
                                "US",
                                "USD");

                testInstrument = instrumentRepository.save(testInstrument);

                testQuote = new Quote(
                                testInstrument,
                                new BigDecimal("140.00"),
                                new BigDecimal("140.50"),
                                new BigDecimal("140.25"),
                                LocalDateTime.now());

                testQuote = quoteRepository.save(testQuote);
        }

        @Test
        void testOrderExecutionSyncsToReporting()
                        throws Exception {

                Order order = new Order();

                order.setAccount(testAccount);
                order.setInstrument(testInstrument);
                order.setSide(OrderSide.BUY);
                order.setQuantity(
                                new BigDecimal("50.00"));
                order.setStatus(
                                OrderStatus.SUBMITTED);
                order.setSubmittedAt(
                                LocalDateTime.now());

                Order savedOrder = orderRepository.save(order);

                orderExecutionService.acceptOrder(
                                savedOrder.getId());

                Fill fill = orderExecutionService.executeOrder(
                                savedOrder.getId());

                TradeFact tradeFact = waitForTradeFact(fill.getId());

                assertEquals(
                                fill.getId(),
                                tradeFact.getFillId());

                assertEquals(
                                savedOrder.getId(),
                                tradeFact.getOrderId());

                assertEquals(
                                testAccount.getId(),
                                tradeFact.getAccountId());

                assertEquals(
                                testClient.getId(),
                                tradeFact.getClientId());

                assertEquals(
                                "Alice",
                                tradeFact.getClientFirstName());

                assertEquals(
                                "Smith",
                                tradeFact.getClientLastName());

                assertEquals(
                                testInstrument.getId(),
                                tradeFact.getInstrumentId());

                assertEquals(
                                testInstrument.getSymbol(),
                                tradeFact.getInstrumentSymbol());

                assertEquals(
                                "EQUITY",
                                tradeFact.getInstrumentClass());

                assertEquals(
                                "BUY",
                                tradeFact.getSide());

                assertEquals(
                                0,
                                new BigDecimal("50.00")
                                                .compareTo(
                                                                tradeFact.getOrderQuantity()));

                assertEquals(
                                0,
                                new BigDecimal("140.50")
                                                .compareTo(
                                                                tradeFact.getExecutionPrice()));

                assertEquals(
                                "FILLED",
                                tradeFact.getOrderStatus());

                assertEquals(
                                0,
                                new BigDecimal("140.00")
                                                .compareTo(
                                                                tradeFact.getBidPrice()));

                assertEquals(
                                0,
                                new BigDecimal("140.50")
                                                .compareTo(
                                                                tradeFact.getAskPrice()));

                BigDecimal expectedTradeValue = new BigDecimal("140.50")
                                .multiply(
                                                new BigDecimal("50.00"));

                assertEquals(
                                0,
                                expectedTradeValue.compareTo(
                                                tradeFact.getTradeValue()));
        }

        @Test
        void testIdempotency_NoDuplicateTradeFacts()
                        throws Exception {

                String idempotencyKey = "test-idem-" + java.util.UUID.randomUUID();

                Order order = new Order();

                order.setAccount(testAccount);
                order.setInstrument(testInstrument);
                order.setSide(OrderSide.BUY);
                order.setQuantity(
                                new BigDecimal("25.00"));
                order.setStatus(
                                OrderStatus.SUBMITTED);
                order.setSubmittedAt(
                                LocalDateTime.now());
                order.setIdempotencyKey(
                                idempotencyKey);

                Order savedOrder = orderRepository.save(order);

                orderExecutionService.acceptOrder(
                                savedOrder.getId());

                Fill firstFill = orderExecutionService.executeOrder(
                                savedOrder.getId(),
                                idempotencyKey);

                TradeFact firstTradeFact = waitForTradeFact(
                                firstFill.getId());

                assertNotNull(firstTradeFact);

                Fill secondFill = orderExecutionService.executeOrder(
                                savedOrder.getId(),
                                idempotencyKey);

                assertEquals(
                                firstFill.getId(),
                                secondFill.getId());

                long tradeFactCount = tradeFactRepository.findAll()
                                .stream()
                                .filter(tf -> tf.getFillId()
                                                .equals(
                                                                firstFill.getId()))
                                .count();

                assertEquals(
                                1,
                                tradeFactCount);

                SyncTracking syncTracking = waitForSuccessfulSyncTracking(
                                firstFill.getId());

                assertEquals(
                                "SUCCESS",
                                syncTracking.getSyncStatus());
        }

        @Test
        void testReportingQueriesReturnAccurateData()
                        throws Exception {

                int orderCount = 3;

                for (int i = 0; i < orderCount; i++) {

                        Order order = new Order();

                        order.setAccount(testAccount);
                        order.setInstrument(testInstrument);
                        order.setSide(OrderSide.BUY);
                        order.setQuantity(
                                        new BigDecimal("10.00"));
                        order.setStatus(
                                        OrderStatus.SUBMITTED);
                        order.setSubmittedAt(
                                        LocalDateTime.now());

                        Order savedOrder = orderRepository.save(order);

                        orderExecutionService.acceptOrder(
                                        savedOrder.getId());

                        Fill fill = orderExecutionService.executeOrder(
                                        savedOrder.getId());

                        waitForTradeFact(
                                        fill.getId());
                }

                List<TradeFact> trades = tradeFactRepository.findByClientIdAndDateRange(
                                testClient.getId(),
                                LocalDateTime.now()
                                                .minusMinutes(10),
                                LocalDateTime.now()
                                                .plusMinutes(10));

                assertEquals(
                                orderCount,
                                trades.size());

                trades.forEach(trade -> {

                        assertEquals(
                                        testClient.getId(),
                                        trade.getClientId());

                        assertEquals(
                                        "BUY",
                                        trade.getSide());

                        assertEquals(
                                        0,
                                        new BigDecimal("10.00")
                                                        .compareTo(
                                                                        trade.getOrderQuantity()));

                        assertEquals(
                                        testInstrument.getSymbol(),
                                        trade.getInstrumentSymbol());

                        assertEquals(
                                        "FILLED",
                                        trade.getOrderStatus());

                        assertNotNull(
                                        trade.getTradeValue());
                });
        }

        @Test
        void testSyncTrackingRecordsAttempts()
                        throws Exception {

                Order order = new Order();

                order.setAccount(testAccount);
                order.setInstrument(testInstrument);
                order.setSide(OrderSide.BUY);
                order.setQuantity(
                                new BigDecimal("15.00"));
                order.setStatus(
                                OrderStatus.SUBMITTED);
                order.setSubmittedAt(
                                LocalDateTime.now());

                Order savedOrder = orderRepository.save(order);

                orderExecutionService.acceptOrder(
                                savedOrder.getId());

                Fill fill = orderExecutionService.executeOrder(
                                savedOrder.getId());

                SyncTracking record = waitForSuccessfulSyncTracking(
                                fill.getId());

                assertEquals(
                                "SUCCESS",
                                record.getSyncStatus());

                assertNull(
                                record.getErrorMessage());

                assertNotNull(
                                record.getSyncCompletedAt());
        }

        @Test
        void testOperationalExecutionNotBlockedByReportingSync()
                        throws Exception {

                Order buyOrder = new Order();

                buyOrder.setAccount(testAccount);
                buyOrder.setInstrument(testInstrument);
                buyOrder.setSide(OrderSide.BUY);
                buyOrder.setQuantity(
                                new BigDecimal("20.00"));
                buyOrder.setStatus(
                                OrderStatus.SUBMITTED);
                buyOrder.setSubmittedAt(
                                LocalDateTime.now());

                Order savedBuyOrder = orderRepository.save(buyOrder);

                orderExecutionService.acceptOrder(
                                savedBuyOrder.getId());

                Fill buyFill = orderExecutionService.executeOrder(
                                savedBuyOrder.getId());

                waitForTradeFact(
                                buyFill.getId());

                Order sellOrder = new Order();

                sellOrder.setAccount(testAccount);
                sellOrder.setInstrument(testInstrument);
                sellOrder.setSide(OrderSide.SELL);
                sellOrder.setQuantity(
                                new BigDecimal("20.00"));
                sellOrder.setStatus(
                                OrderStatus.SUBMITTED);
                sellOrder.setSubmittedAt(
                                LocalDateTime.now());

                Order savedSellOrder = orderRepository.save(sellOrder);

                long executionStartMs = System.currentTimeMillis();

                orderExecutionService.acceptOrder(
                                savedSellOrder.getId());

                Fill sellFill = orderExecutionService.executeOrder(
                                savedSellOrder.getId());

                long executionTimeMs = System.currentTimeMillis()
                                - executionStartMs;

                System.out.println(
                                "Operational order execution time: "
                                                + executionTimeMs
                                                + "ms");

                TradeFact tradeFact = waitForTradeFact(
                                sellFill.getId());

                assertNotNull(
                                tradeFact);

                assertEquals(
                                sellFill.getId(),
                                tradeFact.getFillId());
        }

        @Test
        void testMultipleClientsTradesAreIsolated()
                        throws Exception {

                User user2 = new User(
                                "reporting-test-2-"
                                                + java.util.UUID.randomUUID()
                                                + "@example.com",
                                "hashed");

                user2 = userRepository.save(user2);

                Client client2 = new Client(
                                user2,
                                "Bob",
                                "Jones");

                client2 = clientRepository.save(client2);

                Account account2 = new Account(
                                client2,
                                "REP-TEST-"
                                                + java.util.UUID.randomUUID(),
                                "USD");

                account2.setCashBalance(
                                new BigDecimal("250000.00"));

                account2 = accountRepository.save(account2);

                Order order1 = new Order();

                order1.setAccount(testAccount);
                order1.setInstrument(testInstrument);
                order1.setSide(OrderSide.BUY);
                order1.setQuantity(
                                new BigDecimal("5.00"));
                order1.setStatus(
                                OrderStatus.SUBMITTED);
                order1.setSubmittedAt(
                                LocalDateTime.now());

                Order savedOrder1 = orderRepository.save(order1);

                orderExecutionService.acceptOrder(
                                savedOrder1.getId());

                Fill fill1 = orderExecutionService.executeOrder(
                                savedOrder1.getId());

                Order order2 = new Order();

                order2.setAccount(account2);
                order2.setInstrument(testInstrument);
                order2.setSide(OrderSide.BUY);
                order2.setQuantity(
                                new BigDecimal("3.00"));
                order2.setStatus(
                                OrderStatus.SUBMITTED);
                order2.setSubmittedAt(
                                LocalDateTime.now());

                Order savedOrder2 = orderRepository.save(order2);

                orderExecutionService.acceptOrder(
                                savedOrder2.getId());

                Fill fill2 = orderExecutionService.executeOrder(
                                savedOrder2.getId());

                waitForTradeFact(
                                fill1.getId());

                waitForTradeFact(
                                fill2.getId());

                List<TradeFact> client1Trades = tradeFactRepository.findByClientIdAndDateRange(
                                testClient.getId(),
                                LocalDateTime.now()
                                                .minusMinutes(10),
                                LocalDateTime.now()
                                                .plusMinutes(10));

                List<TradeFact> client2Trades = tradeFactRepository.findByClientIdAndDateRange(
                                client2.getId(),
                                LocalDateTime.now()
                                                .minusMinutes(10),
                                LocalDateTime.now()
                                                .plusMinutes(10));

                assertEquals(
                                1,
                                client1Trades.size());

                assertEquals(
                                1,
                                client2Trades.size());

                assertEquals(
                                "BUY",
                                client1Trades.get(0).getSide());

                assertEquals(
                                "BUY",
                                client2Trades.get(0).getSide());
        }

        @Test
        void emptyReportingPeriodReturnsZeroWithoutChangingTransactionalData()
                        throws Exception {

                LocalDateTime start = LocalDateTime.of(2099, 1, 1, 0, 0);
                LocalDateTime end = start.plusDays(1);

                long ordersBefore = orderRepository.count();
                long fillsBefore = fillRepository.count();

                TradingActivityMetrics metrics = tradingActivityMetricsService.calculate(
                                new ReportingFilters(start, end, null, null, null, null, testAccount.getId(), "USD"));

                assertEquals(0, metrics.totalOrders());
                assertEquals(0, metrics.filledOrders());
                assertEquals(0, metrics.rejectedOrders());
                assertEquals(0, metrics.activeClients());
                assertEquals(ordersBefore, orderRepository.count());
                assertEquals(fillsBefore, fillRepository.count());
        }

        @Test
        void singleTradeReconcilesTransactionalFillAndReportingTrade()
                        throws Exception {

                Fill fill = createFilledOrder(
                                testAccount,
                                testInstrument,
                                OrderSide.BUY,
                                new BigDecimal("12"),
                                LocalDateTime.now().minusMinutes(2));

                TradeFact trade = waitForTradeFact(fill.getId());

                assertEquals(fill.getId(), trade.getFillId());
                assertEquals(1, tradeFactRepository.findByClientIdAndDateRange(
                                testClient.getId(),
                                LocalDateTime.now().minusMinutes(10),
                                LocalDateTime.now().plusMinutes(10)).size());
        }

        @Test
        void multipleInstrumentsAndBothSidesAggregateCorrectly()
                        throws Exception {

                Instrument secondInstrument = createInstrument("Second Instrument", "NYSE", "US");

                Fill buyFill = createFilledOrder(
                                testAccount, testInstrument, OrderSide.BUY,
                                new BigDecimal("10"), LocalDateTime.now().minusMinutes(4));

                createFilledOrder(
                                testAccount, secondInstrument, OrderSide.BUY,
                                new BigDecimal("20"), LocalDateTime.now().minusMinutes(3));

                createFilledOrder(
                                testAccount, testInstrument, OrderSide.SELL,
                                new BigDecimal("5"), LocalDateTime.now().minusMinutes(2));

                waitForTradeFact(buyFill.getId());

                TradingActivityMetrics metrics = currentMetrics();

                assertEquals(3, metrics.totalOrders());
                assertEquals(2, metrics.buyFills());
                assertEquals(1, metrics.sellFills());
                assertEquals(0, new BigDecimal("30").compareTo(metrics.buyQuantity()));
                assertEquals(0, new BigDecimal("5").compareTo(metrics.sellQuantity()));
                assertEquals(2, tradeFactRepository.findByInstrumentIdOrderByOrderFilledAtDesc(
                                testInstrument.getId()).size());
        }

        @Test
        void rejectedOrdersAppearInOrderReportingButNotTradeReporting()
                        throws Exception {

                Order rejected = createSubmittedOrder(
                                testAccount, testInstrument, OrderSide.BUY,
                                new BigDecimal("7"), LocalDateTime.now().minusMinutes(2));

                orderExecutionService.rejectOrder(rejected.getId(), "Test rejection");
                waitForOrderFact(rejected.getId());

                TradingActivityMetrics metrics = currentMetrics();

                assertEquals(1, metrics.totalOrders());
                assertEquals(0, metrics.filledOrders());
                assertEquals(1, metrics.rejectedOrders());
                assertEquals(0, metrics.totalExecutedQuantity().compareTo(BigDecimal.ZERO));
                assertTrue(tradeFactRepository.findByClientIdAndDateRange(
                                testClient.getId(),
                                LocalDateTime.now().minusMinutes(10),
                                LocalDateTime.now().plusMinutes(10)).isEmpty());
        }

        @Test
        void dateRangeIncludesStartAndExcludesEndBoundary()
                        throws Exception {

                LocalDateTime boundary = LocalDateTime.of(2026, 10, 12, 0, 0);
                Fill firstFill = createFilledOrder(testAccount, testInstrument, OrderSide.BUY,
                                new BigDecimal("4"), boundary);
                Fill secondFill = createFilledOrder(testAccount, testInstrument, OrderSide.BUY,
                                new BigDecimal("8"), boundary.plusDays(1));

                waitForOrderFact(firstFill.getOrder().getId());
                waitForOrderFact(secondFill.getOrder().getId());

                TradingActivityMetrics metrics = tradingActivityMetricsService.calculate(
                                new ReportingFilters(
                                                boundary,
                                                boundary.plusDays(1),
                                                null, null, null, null, testAccount.getId(), "USD"));

                assertEquals(1, metrics.totalOrders());
                assertEquals(1, metrics.activeClients());
        }

        @Test
        void reportingFillCountMatchesTransactionalFillCount()
                        throws Exception {

                Fill firstFill = createFilledOrder(testAccount, testInstrument, OrderSide.BUY,
                                new BigDecimal("3"), LocalDateTime.now().minusMinutes(3));
                Fill secondFill = createFilledOrder(testAccount, testInstrument, OrderSide.BUY,
                                new BigDecimal("6"), LocalDateTime.now().minusMinutes(2));

                TradingActivityMetrics metrics = currentMetrics();
                assertNotNull(tradeFactRepository.findByFillId(firstFill.getId()).orElse(null));
                assertNotNull(tradeFactRepository.findByFillId(secondFill.getId()).orElse(null));
                assertEquals(2, metrics.filledOrders());
        }

        @Test
        void repeatedRefreshDoesNotCreateDuplicateReportingRows()
                        throws Exception {

                Fill fill = createFilledOrder(testAccount, testInstrument, OrderSide.BUY,
                                new BigDecimal("9"), LocalDateTime.now().minusMinutes(2));
                TradeFact original = waitForTradeFact(fill.getId());

                orderFactSyncService.syncOrderById(fill.getOrder().getId());

                assertEquals(1, tradeFactRepository.findByFillId(fill.getId()).stream().count());
                assertEquals(original.getTradeFactId(),
                                tradeFactRepository.findByFillId(fill.getId()).orElseThrow().getTradeFactId());
        }

        @Test
        void reportingRefreshUpdatesOrderStatusWithoutCreatingAnotherFact()
                        throws Exception {

                Order order = createSubmittedOrder(
                                testAccount,
                                testInstrument,
                                OrderSide.BUY,
                                new BigDecimal("13"),
                                LocalDateTime.now().minusMinutes(3));

                orderFactSyncService.syncOrderById(order.getId());
                assertEquals("SUBMITTED", waitForOrderFact(order.getId()).getFinalStatus());

                orderExecutionService.rejectOrder(order.getId(), "Refreshed rejection");
                orderFactSyncService.syncOrderById(order.getId());

                OrderFact refreshed = waitForOrderFact(order.getId());

                assertEquals("REJECTED", refreshed.getFinalStatus());
                assertEquals(1, orderFactRepository.findByOrderId(order.getId()).stream().count());
                assertEquals("Refreshed rejection", refreshed.getRejectionReason());
        }

        @Test
        void executionDatesAndFilteredTotalsReconcileWithPersistedSource() throws Exception {
                LocalDateTime start = LocalDateTime.of(2026, 10, 12, 0, 0);
                LocalDateTime end = start.plusDays(1);
                Instrument second = createInstrument("Boundary Instrument", "NYSE", "US");
                // Submission is outside the window: inclusion must follow execution time.
                persistTimedFill(testInstrument, OrderSide.BUY, new BigDecimal("1"), start.minusDays(2),
                                start.minusSeconds(1));
                persistTimedFill(testInstrument, OrderSide.BUY, new BigDecimal("10"), start.minusDays(1), start);
                persistTimedFill(second, OrderSide.SELL, new BigDecimal("5"), start.minusDays(1), end.minusSeconds(1));
                persistTimedFill(second, OrderSide.BUY, new BigDecimal("2"), start.minusDays(1), end);

                ReportingFilters all = new ReportingFilters(start, end, null, null, null,
                                null, testAccount.getId(), "USD");
                assertEquals(2L, assertExecutionReconciles(all));
                assertEquals(1L, assertExecutionReconciles(new ReportingFilters(start, end,
                                testInstrument.getId(), "BUY", "FILLED", testClient.getId(), testAccount.getId(),
                                "USD")));
                assertEquals(1L, assertExecutionReconciles(new ReportingFilters(start, end,
                                second.getId(), "SELL", "FILLED", testClient.getId(), testAccount.getId(), "USD")));
                assertEquals(0L, assertExecutionReconciles(new ReportingFilters(start, end,
                                null, null, "REJECTED", null, testAccount.getId(), "USD")));
                assertEquals(0L, assertExecutionReconciles(new ReportingFilters(end.plusDays(1), end.plusDays(2),
                                null, null, null, null, testAccount.getId(), "USD")));
        }

        @Test
        void everyReportingServiceLeavesOperationalRecordsUnchanged() throws Exception {
                createFilledOrder(testAccount, testInstrument, OrderSide.BUY, new BigDecimal("11"),
                                LocalDateTime.now().minusMinutes(2));
                ReportingFilters filters = new ReportingFilters(
                                java.time.LocalDate.now().atStartOfDay(),
                                java.time.LocalDate.now().plusDays(1).atStartOfDay(),
                                null, null, null, null, testAccount.getId(), "USD");
                Map<String, List<Map<String, Object>>> before = operationalSnapshot();
                tradingActivityMetricsService.calculate(filters);
                instrumentMetricsService.calculate(filters);
                clientMetricsService.calculate(filters);
                activityTrendService.calculate(filters);
                reportingActivityService.find(filters);
                businessInsightsService.calculate(filters);
                periodicReportingService.generate(ReportingPeriod.DAILY, java.time.LocalDate.now());
                currentMetrics();
                tradeFactRepository.findByClientIdAndDateRange(
                                testClient.getId(),
                                LocalDateTime.now().minusMinutes(10),
                                LocalDateTime.now().plusMinutes(10));
                assertEquals(before, operationalSnapshot(),
                                "Reporting changed persisted orders, fills, balances, cash ledger, holdings, or history");
        }

        /**
         * Explicit performance gate; do not claim Goal 5 load validation from a run
         * that skips it. Thresholds must be selected for the acceptance environment.
         */
        @Test
        void concurrentReportingRespectsTradingLatencyBudgetAndReconciles() throws Exception {
                long budgetMs = Long.getLong("goal5.maxExecutionMs", 2000L);
                double allowedSlowdown = Double.parseDouble(System.getProperty("goal5.allowedSlowdown", "3"));
                int seedTrades = Integer.getInteger("goal5.seedTrades", 200);
                int workers = Integer.getInteger("goal5.reportWorkers", 3);
                assertTrue(budgetMs > 0, "goal5.maxExecutionMs must be greater than zero");
                assertTrue(allowedSlowdown >= 1 && seedTrades >= 1 && seedTrades <= 2000
                                && workers >= 1 && workers <= 8, "Invalid load-test settings");
                testAccount.setCashBalance(BigDecimal.valueOf((seedTrades + 100L) * 200L));
                accountRepository.save(testAccount);
                for (int i = 0; i < seedTrades; i++) {
                        if (i % 25 == 0) {
                                quoteRepository.save(new Quote(testInstrument, new BigDecimal("140.00"),
                                                new BigDecimal("140.50"), new BigDecimal("140.25"),
                                                LocalDateTime.now()));
                        }
                        createFilledOrder(testAccount, testInstrument, OrderSide.BUY,
                                        BigDecimal.ONE, LocalDateTime.now());
                }
                List<Long> baseline = executeMeasuredOrders(5);
                long baselineP95 = percentile95(baseline);
                var filters = new ReportingFilters(java.time.LocalDate.now().atStartOfDay(),
                                java.time.LocalDate.now().plusDays(1).atStartOfDay(),
                                null, null, null, null, testAccount.getId(), "USD");
                var pool = Executors.newFixedThreadPool(workers);
                var stop = new AtomicBoolean(false);
                var completedBatches = new AtomicLong();
                var firstBatch = new CountDownLatch(workers);
                var overlaps = new ConcurrentLinkedQueue<long[]>();
                List<Future<?>> tasks = new ArrayList<>();
                try {
                        for (int i = 0; i < workers; i++) {
                                tasks.add(pool.submit(() -> {
                                        boolean initialBatch = true;
                                        while (!stop.get()) {
                                                long began = System.nanoTime();
                                                tradingActivityMetricsService.calculate(filters);
                                                instrumentMetricsService.calculate(filters);
                                                activityTrendService.calculate(filters);
                                                clientMetricsService.calculate(filters);
                                                reportingActivityService.find(filters);
                                                businessInsightsService.calculate(filters);
                                                overlaps.add(new long[] { began, System.nanoTime() });
                                                completedBatches.incrementAndGet();
                                                if (initialBatch) {
                                                        firstBatch.countDown();
                                                        initialBatch = false;
                                                }
                                        }
                                }));
                        }
                        assertTrue(firstBatch.await(30, TimeUnit.SECONDS),
                                        "Concurrent reporting workers could not complete an initial batch");
                        long tradingStart = System.nanoTime();
                        List<Long> underLoad = executeMeasuredOrders(10);
                        long tradingEnd = System.nanoTime();
                        long loadedP95 = percentile95(underLoad);
                        stop.set(true);
                        for (Future<?> task : tasks) {
                                task.get(30, TimeUnit.SECONDS);
                        }
                        assertTrue(overlaps.stream().anyMatch(span -> span[0] < tradingEnd && span[1] > tradingStart),
                                        "No reporting query batch overlapped measured order execution");
                        assertTrue(underLoad.stream().allMatch(ms -> ms <= budgetMs),
                                        "At least one order exceeded the absolute execution budget: " + underLoad);
                        assertTrue(loadedP95 <= Math.max(100L, Math.ceil(baselineP95 * allowedSlowdown)),
                                        "Reporting exceeded the agreed relative slowdown: baseline p95="
                                                        + baselineP95 + "ms, loaded p95=" + loadedP95 + "ms");
                        System.out.printf(
                                        "Goal5 load: rows=%d workers=%d batches=%d baselineP95=%dms loadedP95=%dms budget=%dms%n",
                                        seedTrades, workers, completedBatches.get(), baselineP95, loadedP95, budgetMs);
                } finally {
                        stop.set(true);
                        pool.shutdown();
                        try {
                                for (Future<?> task : tasks) {
                                        task.get(30, TimeUnit.SECONDS); // Surface query failures; never swallow them.
                                }
                        } finally {
                                pool.shutdownNow();
                        }
                }
                assertTrue(completedBatches.get() >= workers);
                assertEquals(seedTrades + 15L, assertExecutionReconciles(filters));
        }

        private List<Long> executeMeasuredOrders(int count) throws Exception {
                List<Long> elapsed = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                        quoteRepository.save(new Quote(testInstrument, new BigDecimal("140.00"),
                                        new BigDecimal("140.50"), new BigDecimal("140.25"), LocalDateTime.now()));
                        long started = System.nanoTime();
                        Order order = createSubmittedOrder(testAccount, testInstrument, OrderSide.BUY,
                                        BigDecimal.ONE, LocalDateTime.now());
                        orderExecutionService.acceptOrder(order.getId());
                        Fill fill = orderExecutionService.executeOrder(order.getId());
                        elapsed.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
                        waitForTradeFact(fill.getId()); // Outside the measured trading latency.
                        waitForOrderFact(order.getId());
                }
                return elapsed;
        }

        private long percentile95(List<Long> values) {
                List<Long> sorted = values.stream().sorted().toList();
                return sorted.get((int) Math.ceil(sorted.size() * .95) - 1);
        }

        /**
         * Test-only persisted source fixture, not production settlement code.
         * Control execution timestamps at INSERT time; never rewrite an executed trade.
         */
        private Fill persistTimedFill(Instrument instrument, OrderSide side, BigDecimal quantity,
                        LocalDateTime submittedAt, LocalDateTime executedAt) throws Exception {
                Fill fill = new TransactionTemplate(transactionManager).execute(status -> {
                        Quote quote = quoteRepository.save(new Quote(instrument, new BigDecimal("140.00"),
                                        new BigDecimal("140.50"), new BigDecimal("140.25"),
                                        executedAt.minusSeconds(1)));
                        Order order = createSubmittedOrder(testAccount, instrument, side, quantity, submittedAt);
                        order.setAcceptedAt(submittedAt.plusSeconds(1));
                        order.setStatus(OrderStatus.FILLED);
                        order.setCompletedAt(executedAt);
                        orderRepository.save(order);
                        Fill source = new Fill();
                        source.setOrder(order);
                        source.setQuote(quote);
                        source.setFillQuantity(quantity);
                        source.setFillPrice(side == OrderSide.BUY ? quote.getAskPrice() : quote.getBidPrice());
                        source.setExecutedAt(executedAt);
                        source = fillRepository.save(source);
                        orderFactSyncService.syncOrder(order);
                        events.publishEvent(new OrderFilledEvent(this, source.getId(), order.getId(),
                                        testAccount.getId(), testClient.getId(), instrument.getId(), side.name(),
                                        quantity, source.getFillPrice(), quantity, submittedAt, order.getAcceptedAt(),
                                        executedAt, "FILLED", quote.getBidPrice(), quote.getAskPrice(),
                                        quote.getQuotedAt()));
                        return source;
                });
                assertNotNull(fill);
                waitForTradeFact(fill.getId());
                return fill;
        }

        private long assertExecutionReconciles(ReportingFilters filters) {
                StringBuilder sql = new StringBuilder("""
                                SELECT COUNT(*) AS fill_count,
                                       COALESCE(SUM(f.fill_quantity), 0) AS quantity,
                                       COALESCE(SUM(ROUND(f.fill_quantity * f.fill_price, 2)), 0) AS value
                                FROM fills f
                                JOIN orders o ON o.id = f.order_id
                                JOIN accounts a ON a.id = o.account_id
                                JOIN instruments i ON i.id = o.instrument_id
                                WHERE f.executed_at >= :start AND f.executed_at < :end
                                """);
                var params = new MapSqlParameterSource()
                                .addValue("start", java.sql.Timestamp.valueOf(filters.fromDate()))
                                .addValue("end", java.sql.Timestamp.valueOf(filters.toDate()));
                if (filters.accountId() != null) {
                        sql.append(" AND o.account_id = :account");
                        params.addValue("account", filters.accountId());
                }
                if (filters.clientId() != null) {
                        sql.append(" AND a.client_id = :client");
                        params.addValue("client", filters.clientId());
                }
                if (filters.instrumentId() != null) {
                        sql.append(" AND o.instrument_id = :instrument");
                        params.addValue("instrument", filters.instrumentId());
                }
                if (filters.side() != null) {
                        sql.append(" AND o.side = :side");
                        params.addValue("side", filters.side());
                }
                if (filters.status() != null) {
                        sql.append(" AND o.status = :status");
                        params.addValue("status", filters.status());
                }
                if (filters.currency() != null) {
                        sql.append(" AND i.currency = :currency");
                        params.addValue("currency", filters.currency());
                }
                var source = new NamedParameterJdbcTemplate(jdbc).queryForMap(sql.toString(), params);
                TradingActivityMetrics report = tradingActivityMetricsService.calculate(filters);
                long count = ((Number) source.get("fill_count")).longValue();
                assertEquals(count, report.buyFills() + report.sellFills(), "Fill count mismatch");
                assertEquals(0, new BigDecimal(source.get("quantity").toString())
                                .compareTo(report.totalExecutedQuantity()),
                                "Execution quantity mismatch");
                assertEquals(0, new BigDecimal(source.get("value").toString()).compareTo(report.totalTradedValue()),
                                "Executed monetary value mismatch");
                return count;
        }

        private Map<String, List<Map<String, Object>>> operationalSnapshot() {
                var queries = new LinkedHashMap<String, String>();
                queries.put("accounts",
                                "SELECT row_to_json(t)::text AS document FROM accounts t WHERE t.id = ? ORDER BY t.id");
                for (String table : List.of("orders", "cash_transactions", "positions", "position_history")) {
                        queries.put(table, "SELECT row_to_json(t)::text AS document FROM " + table
                                        + " t WHERE t.account_id = ? ORDER BY t.id");
                }
                queries.put("fills",
                                "SELECT row_to_json(t)::text AS document FROM fills t JOIN orders o ON o.id = t.order_id WHERE o.account_id = ? ORDER BY t.id");
                queries.put("status_history",
                                "SELECT row_to_json(t)::text AS document FROM order_status_history t JOIN orders o ON o.id = t.order_id WHERE o.account_id = ? ORDER BY t.id");
                queries.put("execution_quotes",
                                "SELECT row_to_json(t)::text AS document FROM market_quotes t "
                                                + "WHERE t.id IN (SELECT f.quote_id FROM fills f "
                                                + "JOIN orders o ON o.id = f.order_id WHERE o.account_id = ?) "
                                                + "ORDER BY t.id");
                var result = new LinkedHashMap<String, List<Map<String, Object>>>();
                queries.forEach((table, sql) -> result.put(table, jdbc.queryForList(sql, testAccount.getId())));
                return result;
        }

        private TradingActivityMetrics currentMetrics() {
                return tradingActivityMetricsService.calculate(
                                new ReportingFilters(
                                                LocalDateTime.now().minusMinutes(10),
                                                LocalDateTime.now().plusMinutes(10),
                                                null, null, null, null, testAccount.getId(), "USD"));
        }

        private Order createSubmittedOrder(
                        Account account,
                        Instrument instrument,
                        OrderSide side,
                        BigDecimal quantity,
                        LocalDateTime submittedAt) {

                Order order = new Order();
                order.setAccount(account);
                order.setInstrument(instrument);
                order.setSide(side);
                order.setQuantity(quantity);
                order.setStatus(OrderStatus.SUBMITTED);
                order.setSubmittedAt(submittedAt);
                return orderRepository.save(order);
        }

        private Fill createFilledOrder(
                        Account account,
                        Instrument instrument,
                        OrderSide side,
                        BigDecimal quantity,
                        LocalDateTime submittedAt)
                        throws Exception {

                if (side == OrderSide.SELL) {
                        Position position = positionRepository
                                        .findByAccountIdAndInstrumentId(account.getId(), instrument.getId())
                                        .orElseGet(Position::new);
                        position.setAccount(account);
                        position.setInstrument(instrument);
                        position.setQuantity(position.getQuantity() == null
                                        ? quantity
                                        : position.getQuantity().add(quantity));
                        position.setUpdatedAt(LocalDateTime.now());
                        positionRepository.save(position);
                }

                Order order = createSubmittedOrder(account, instrument, side, quantity, submittedAt);
                orderExecutionService.acceptOrder(order.getId());
                Fill fill = orderExecutionService.executeOrder(order.getId());
                waitForTradeFact(fill.getId());
                return fill;
        }

        private Instrument createInstrument(String name, String exchange, String countryCode) {
                Instrument instrument = new Instrument(
                                "T" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 7),
                                name,
                                InstrumentClass.EQUITY,
                                exchange,
                                countryCode,
                                "USD");
                instrument = instrumentRepository.save(instrument);
                quoteRepository.save(new Quote(
                                instrument,
                                new BigDecimal("90.00"),
                                new BigDecimal("90.50"),
                                new BigDecimal("90.25"),
                                LocalDateTime.now()));
                return instrument;
        }

        private OrderFact waitForOrderFact(Long orderId) throws InterruptedException {
                long timeoutAt = System.currentTimeMillis() + 5000;
                while (System.currentTimeMillis() < timeoutAt) {
                        var fact = orderFactRepository.findByOrderId(orderId);
                        if (fact.isPresent()) {
                                return fact.get();
                        }
                        Thread.sleep(50);
                }
                throw new AssertionError("OrderFact was not synchronized for order " + orderId);
        }

        private TradeFact waitForTradeFact(
                        Long fillId)
                        throws InterruptedException {

                long timeoutAt = System.currentTimeMillis()
                                + 5000;

                while (System.currentTimeMillis() < timeoutAt) {

                        var tradeFact = tradeFactRepository.findByFillId(
                                        fillId);

                        if (tradeFact.isPresent()) {
                                return tradeFact.get();
                        }

                        Thread.sleep(50);
                }

                throw new AssertionError(
                                "TradeFact was not synchronized within 5 seconds for fill "
                                                + fillId);
        }

        private SyncTracking waitForSuccessfulSyncTracking(
                        Long fillId)
                        throws InterruptedException {

                long timeoutAt = System.currentTimeMillis()
                                + 5000;

                while (System.currentTimeMillis() < timeoutAt) {

                        List<SyncTracking> records = syncTrackingRepository.findAll();

                        var matchingRecord = records.stream()
                                        .filter(record -> fillId.equals(
                                                        record.getFillId()))
                                        .filter(record -> "SUCCESS".equals(
                                                        record.getSyncStatus()))
                                        .findFirst();

                        if (matchingRecord.isPresent()) {
                                return matchingRecord.get();
                        }

                        Thread.sleep(50);
                }

                throw new AssertionError(
                                "Successful reporting sync was not recorded within 5 seconds for fill "
                                                + fillId);
        }
}
