package com.goatteen.trading.settlement;

import com.goatteen.trading.account.Account;
import com.goatteen.trading.account.AccountRepository;
import com.goatteen.trading.instrument.Instrument;
import com.goatteen.trading.order.Order;
import com.goatteen.trading.order.OrderRepository;
import com.goatteen.trading.order.OrderService;
import com.goatteen.trading.order.OrderStatus;
import com.goatteen.trading.portfolio.CashTransaction;
import com.goatteen.trading.portfolio.CashTransactionRepository;
import com.goatteen.trading.portfolio.Position;
import com.goatteen.trading.portfolio.PositionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Settlement Service
 * 
 * Orchestrates the settlement process (cash/position movement) after order execution.
 * 
 * Guarantees:
 * 1. IDEMPOTENT: Same idempotency key never executes twice
 * 2. RESUMABLE: If app crashes mid-settlement, resume from current step
 * 3. ATOMIC: All-or-nothing per step (prevents partial state)
 * 
 * Settlement Lifecycle:
 * INITIATED → CASH_DEBITED → POSITION_CREDITED → COMPLETED
 * 
 * If any step fails: Status = SETTLING_FAILED (can retry)
 */
@Service
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final SettlementIdempotencyKeyRepository idempotencyKeyRepository;
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final AccountRepository accountRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final PositionRepository positionRepository;

    public SettlementService(
            SettlementRepository settlementRepository,
            SettlementIdempotencyKeyRepository idempotencyKeyRepository,
            OrderService orderService,
            OrderRepository orderRepository,
            AccountRepository accountRepository,
            CashTransactionRepository cashTransactionRepository,
            PositionRepository positionRepository) {
        this.settlementRepository = settlementRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.accountRepository = accountRepository;
        this.cashTransactionRepository = cashTransactionRepository;
        this.positionRepository = positionRepository;
    }

    /**
     * Execute settlement idempotently
     * 
     * Main entry point for settlement execution.
     * Checks idempotency key first - if already processed, returns cached result.
     * 
     * @param orderId The order to settle
     * @param idempotencyKey Unique key for this settlement attempt (e.g., UUID)
     * @return Completed settlement
     * @throws SettlementException if settlement fails
     */
    @Transactional
    public Settlement executeSettlement(Long orderId, String idempotencyKey) {
        // Step 1: Check idempotency key 
        Optional<SettlementIdempotencyKey> existingKey = 
            idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey);
        
        if (existingKey.isPresent()) {
            if (existingKey.get().isSuccess()) {
                // Successfully settled before - retrieve the settlement
                return settlementRepository.findByOrderId(orderId)
                    .orElseThrow(() -> new SettlementException("Settlement record not found for order: " + orderId));
            } else {
                // Failed before
                throw new SettlementException(
                    "Previous settlement attempt failed: " + existingKey.get().getResultJson()
                );
            }
        }

        try {
            // Step 2: Get or create settlement record
            Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
            
            Settlement settlement = getOrCreateSettlement(order);

            // Step 3: Execute settlement steps
            executeSettlementSteps(settlement, order);

            // Step 4: Complete settlement
            completeSettlement(settlement);

            // Step 5: Store idempotency key with success
            String resultJson = serializeSettlementResult(settlement);
            SettlementIdempotencyKey keyRecord = 
                SettlementIdempotencyKey.ofSuccess(order, idempotencyKey, resultJson);
            idempotencyKeyRepository.save(keyRecord);

            return settlement;

        } catch (Exception e) {
            // If settlement fails, mark as SETTLING_FAILED and store error
            Settlement settlement = settlementRepository.findByOrderId(orderId).orElse(null);
            
            if (settlement != null) {
                failSettlement(settlement, e);
            }

            // Store idempotency key with failure
            Order order = orderRepository.findById(orderId).orElse(null);
            if (order != null) {
                String errorJson = serializeError(e);
                SettlementIdempotencyKey keyRecord = 
                    SettlementIdempotencyKey.ofFailure(order, idempotencyKey, errorJson);
                idempotencyKeyRepository.save(keyRecord);
            }

            throw new SettlementException("Settlement failed for order: " + orderId, e);
        }
    }

    /**
     * Get existing settlement or create new one
     */
    @Transactional
    public Settlement getOrCreateSettlement(Order order) {
        Optional<Settlement> existing = settlementRepository.findByOrderId(order.getId());
        
        if (existing.isPresent()) {
            return existing.get();
        }

        // Create new settlement
        Settlement settlement = new Settlement();
        settlement.setOrder(order);
        settlement.setAccount(order.getAccount());
        settlement.setQuantity(order.getQuantity());
        // totalAmount will be set when we have the fill price
        // For now, it's a placeholder
        settlement.setStatus(SettlementStatus.INITIATED);
        settlement.setInitiatedAt(LocalDateTime.now());
        
        return settlementRepository.save(settlement);
    }

    /**
     * Execute settlement steps: Debit cash → Credit shares
     */
    @Transactional
    private void executeSettlementSteps(Settlement settlement, Order order) {
        SettlementStatus currentStatus = settlement.getStatus();

        // Step 1: Debit cash
        if (currentStatus == SettlementStatus.INITIATED) {
            debitCash(settlement);
            settlement.setStatus(SettlementStatus.CASH_DEBITED);
            settlementRepository.save(settlement);
            currentStatus = SettlementStatus.CASH_DEBITED;
        }

        // Step 2: Credit shares
        if (currentStatus == SettlementStatus.CASH_DEBITED) {
            creditShares(settlement, order);
            settlement.setStatus(SettlementStatus.POSITION_CREDITED);
            settlementRepository.save(settlement);
        }
    }

    /**
     * Debit cash from account
     * 
     * Updates account.cashBalance and creates CashTransaction record.
     */
    private void debitCash(Settlement settlement) {
        Account account = settlement.getAccount();
        BigDecimal settlementAmount = settlement.getTotalAmount();

        // Validate sufficient balance
        if (account.getCashBalance().compareTo(settlementAmount) < 0) {
            throw new SettlementException(
                "Insufficient cash balance. Required: " + settlementAmount + 
                ", Available: " + account.getCashBalance()
            );
        }

        // Debit cash
        BigDecimal newBalance = account.getCashBalance().subtract(settlementAmount);
        account.setCashBalance(newBalance);
        accountRepository.save(account);

        // Record transaction
        CashTransaction transaction = new CashTransaction();
        transaction.setAccount(account);
        transaction.setAmount(settlementAmount.negate()); // Negative for debit
        transaction.setBalanceAfter(newBalance);
        transaction.setDescription("Settlement debit for order: " + settlement.getOrder().getId());
        transaction.setCreatedAt(LocalDateTime.now());
        cashTransactionRepository.save(transaction);
    }

    /**
     * Credit shares to account (update or create position)
     */
    private void creditShares(Settlement settlement, Order order) {
        Account account = settlement.getAccount();
        Instrument instrument = order.getInstrument();
        BigDecimal quantity = settlement.getQuantity();

        // Find existing position or create new
        Optional<Position> existingPosition = positionRepository
            .findByAccountIdAndInstrumentId(account.getId(), instrument.getId());

        Position position;
        if (existingPosition.isPresent()) {
            // Add to existing position
            position = existingPosition.get();
            BigDecimal newQuantity = position.getQuantity().add(quantity);
            position.setQuantity(newQuantity);
        } else {
            // Create new position
            position = new Position();
            position.setAccount(account);
            position.setInstrument(instrument);
            position.setQuantity(quantity);
        }
        
        position.setUpdatedAt(LocalDateTime.now());
        positionRepository.save(position);
    }

    /**
     * Mark settlement as COMPLETED (terminal state)
     */
    @Transactional
    private void completeSettlement(Settlement settlement) {
        settlement.setStatus(SettlementStatus.COMPLETED);
        settlement.setCompletedAt(LocalDateTime.now());
        settlementRepository.save(settlement);

        // Update order status to SETTLED
        orderService.updateOrderStatus(settlement.getOrder().getId(), OrderStatus.SETTLED);
    }

    /**
     * Mark settlement as FAILED (retry-able)
     */
    @Transactional
    private void failSettlement(Settlement settlement, Exception error) {
        settlement.setStatus(SettlementStatus.FAILED);
        settlementRepository.save(settlement);

        try {
            // Update order to SETTLING_FAILED so it can be retried
            orderService.updateOrderStatus(
                settlement.getOrder().getId(),
                OrderStatus.SETTLING_FAILED
            );
        } catch (Exception e) {
            // Log but don't fail
        }
    }

    /**
     * Serialize settlement result to JSON
     */
    private String serializeSettlementResult(Settlement settlement) {
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("settlementId", settlement.getId());
            result.put("orderId", settlement.getOrder().getId());
            result.put("status", settlement.getStatus());
            result.put("totalAmount", settlement.getTotalAmount());
            result.put("completedAt", settlement.getCompletedAt().toString());
            return convertMapToJson(result);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * Serialize error to JSON
     */
    private String serializeError(Exception error) {
        try {
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("error", error.getMessage());
            errorMap.put("timestamp", LocalDateTime.now().toString());
            return convertMapToJson(errorMap);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * Simple JSON conversion without Jackson dependency
     */
    private String convertMapToJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{");
        map.forEach((key, value) -> {
            json.append("\"").append(key).append("\":");
            if (value instanceof String) {
                json.append("\"").append(value).append("\"");
            } else {
                json.append(value);
            }
            json.append(",");
        });
        if (json.length() > 1) {
            json.deleteCharAt(json.length() - 1);
        }
        json.append("}");
        return json.toString();
    }
}