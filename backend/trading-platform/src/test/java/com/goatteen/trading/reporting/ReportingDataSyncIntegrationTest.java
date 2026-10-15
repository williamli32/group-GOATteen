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
import com.goatteen.trading.reporting.sync.OrderFactSyncService;
import com.goatteen.trading.reporting.tracking.SyncTracking;
import com.goatteen.trading.reporting.tracking.SyncTrackingRepository;
import com.goatteen.trading.user.User;
import com.goatteen.trading.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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

        private User testUser;
        private Client testClient;
        private Account testAccount;
        private Instrument testInstrument;
        private Quote testQuote;

        @BeforeEach
        void setUp() {

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
                assertEquals(original.getTradeFactId(), tradeFactRepository.findByFillId(fill.getId()).orElseThrow().getTradeFactId());
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
        void reportingQueriesDoNotChangeTransactionalRecords()
                        throws Exception {

                Fill fill = createFilledOrder(testAccount, testInstrument, OrderSide.BUY,
                                new BigDecimal("11"), LocalDateTime.now().minusMinutes(2));
                waitForTradeFact(fill.getId());

                Order beforeOrder = orderRepository.findById(fill.getOrder().getId()).orElseThrow();
                BigDecimal beforeQuantity = beforeOrder.getQuantity();
                OrderStatus beforeStatus = beforeOrder.getStatus();
                BigDecimal beforeFillPrice = fillRepository.findById(fill.getId()).orElseThrow().getFillPrice();

                currentMetrics();
                tradeFactRepository.findByClientIdAndDateRange(
                                testClient.getId(),
                                LocalDateTime.now().minusMinutes(10),
                                LocalDateTime.now().plusMinutes(10));

                Order afterOrder = orderRepository.findById(fill.getOrder().getId()).orElseThrow();
                BigDecimal afterFillPrice = fillRepository.findById(fill.getId()).orElseThrow().getFillPrice();

                assertEquals(beforeQuantity, afterOrder.getQuantity());
                assertEquals(beforeStatus, afterOrder.getStatus());
                assertEquals(beforeFillPrice, afterFillPrice);
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
