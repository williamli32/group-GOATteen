package com.goatteen.trading.reporting.sync;

import com.goatteen.trading.account.Account;
import com.goatteen.trading.account.AccountRepository;
import com.goatteen.trading.client.Client;
import com.goatteen.trading.client.ClientRepository;
import com.goatteen.trading.execution.Fill;
import com.goatteen.trading.execution.FillRepository;
import com.goatteen.trading.instrument.Instrument;
import com.goatteen.trading.instrument.InstrumentRepository;
import com.goatteen.trading.marketdata.Quote;
import com.goatteen.trading.marketdata.QuoteRepository;
import com.goatteen.trading.order.Order;
import com.goatteen.trading.order.OrderRepository;
import com.goatteen.trading.reporting.data.TradeFact;
import com.goatteen.trading.reporting.data.TradeFactRepository;
import com.goatteen.trading.reporting.event.OrderFilledEvent;
import com.goatteen.trading.reporting.tracking.SyncTracking;
import com.goatteen.trading.reporting.tracking.SyncTrackingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Synchronizes order fill events from the transactional path into the reporting schema.
 * 
 * Runs AFTER the order execution transaction commits, ensuring:
 * 1. Zero latency impact on live trading.
 * 2. Exactly-once semantics for reporting data (idempotency via fill_id).
 * 3. Failure isolation: if sync fails, live execution is unaffected.
 * 
 * Uses @TransactionalEventListener to guarantee execution only after commit.
 */
@Service
public class ReportingDataSyncService {

    private static final Logger logger = LoggerFactory.getLogger(ReportingDataSyncService.class);

    private final TradeFactRepository tradeFactRepository;
    private final SyncTrackingRepository syncTrackingRepository;
    private final FillRepository fillRepository;
    private final OrderRepository orderRepository;
    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;
    private final InstrumentRepository instrumentRepository;
    private final QuoteRepository quoteRepository;

    public ReportingDataSyncService(
            TradeFactRepository tradeFactRepository,
            SyncTrackingRepository syncTrackingRepository,
            FillRepository fillRepository,
            OrderRepository orderRepository,
            AccountRepository accountRepository,
            ClientRepository clientRepository,
            InstrumentRepository instrumentRepository,
            QuoteRepository quoteRepository) {

        this.tradeFactRepository = tradeFactRepository;
        this.syncTrackingRepository = syncTrackingRepository;
        this.fillRepository = fillRepository;
        this.orderRepository = orderRepository;
        this.accountRepository = accountRepository;
        this.clientRepository = clientRepository;
        this.instrumentRepository = instrumentRepository;
        this.quoteRepository = quoteRepository;
    }

    /**
     * Listener for order fill events.
     * Executes ONLY AFTER the transactional execution commits.
     * @param event Published from OrderExecutionService.executeOrder()
     */
    @TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void onOrderFilled(OrderFilledEvent event) {
        try {
            logger.info("Syncing fill_id={} to reporting schema", event.getFillId());

            // Idempotency check: has this fill already been synced?
            Optional<TradeFact> existingFact = tradeFactRepository.findByFillId(event.getFillId());
            if (existingFact.isPresent()) {
                logger.debug("Fill {} already synced; skipping", event.getFillId());
                recordSyncTracking(event.getFillId(), "SUCCESS", "Duplicate (already synced)");
                return;
            }

            // Load transactional source data for denormalization
            Fill fill = fillRepository.findById(event.getFillId())
                    .orElseThrow(() -> new SyncException("Fill not found: " + event.getFillId()));

            Order order = orderRepository.findById(event.getOrderId())
                    .orElseThrow(() -> new SyncException("Order not found: " + event.getOrderId()));

            Account account = accountRepository.findById(event.getAccountId())
                    .orElseThrow(() -> new SyncException("Account not found: " + event.getAccountId()));

            Client client = clientRepository.findById(event.getClientId())
                    .orElseThrow(() -> new SyncException("Client not found: " + event.getClientId()));

            Instrument instrument = instrumentRepository.findById(event.getInstrumentId())
                    .orElseThrow(() -> new SyncException("Instrument not found: " + event.getInstrumentId()));

            Quote quote = quoteRepository.findById(fill.getQuote().getId())
                    .orElseThrow(() -> new SyncException("Quote not found: " + fill.getQuote().getId()));

            //  Build trade fact from event and denormalized data
            TradeFact tradeFact = new TradeFact(
                    event.getFillId(),
                    event.getOrderId(),
                    event.getOrderSubmittedAt(),
                    event.getOrderAcceptedAt(),
                    event.getOrderFilledAt(),
                    event.getAccountId(),
                    event.getClientId(),
                    client.getFirstName(),
                    client.getLastName(),
                    event.getInstrumentId(),
                    instrument.getSymbol(),
                    instrument.getInstrumentClass().toString(),
                    instrument.getName(),
                    instrument.getCurrency(),
                    event.getOrderSide(),
                    event.getOrderQuantity(),
                    event.getExecutionPrice(),
                    event.getExecutionQuantity(),
                    event.calculateTradeValue(),
                    event.getOrderStatus(),
                    quote.getBidPrice(),
                    quote.getAskPrice(),
                    quote.getQuotedAt()
            );

            // Persist to reporting schema
            TradeFact saved = tradeFactRepository.save(tradeFact);
            logger.info("Successfully synced TradeFact id={} for fill_id={}", 
                    saved.getTradeFactId(), event.getFillId());

            recordSyncTracking(event.getFillId(), "SUCCESS", null);

        } catch (Exception e) {
            logger.error("Failed to sync fill_id={} to reporting schema", event.getFillId(), e);
            recordSyncTracking(event.getFillId(), "FAILED", e.getMessage());
            // INTENTIONALLY do NOT rethrow: reporting sync failure should not affect live trading
        }
    }

    /**
     * Record sync attempt for audit and replay recovery.
     * Runs in a nested transaction to ensure tracking persists
     * even if main sync was already successful.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void recordSyncTracking(Long fillId, String status, String errorMessage) {
        try {
            SyncTracking tracking = new SyncTracking(
                    fillId,
                    LocalDateTime.now(),
                    status,
                    errorMessage
            );
            syncTrackingRepository.save(tracking);
        } catch (Exception e) {
            logger.error("Failed to record sync tracking for fill_id={}", fillId, e);

        }
    }
}