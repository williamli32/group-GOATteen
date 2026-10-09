package com.goatteen.trading.reporting.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// Read-only repository for reporting.trade_facts.
// No save/update/delete methods, only find(look-up); 
// strictly for analytical queries.
// All queries should use indexes optimized for reporting () V17).
@Repository
public interface TradeFactRepository extends JpaRepository<TradeFact, Long> {

    
    // Find a trade by its source fill_id (for idempotency checks).  
    Optional<TradeFact> findByFillId(Long fillId);

    // All fills for a client within a date range.
    // Indexed on (client_id, order_filled_at).   
    @Query("""
        SELECT tf FROM TradeFact tf
        WHERE tf.clientId = :clientId
          AND tf.orderFilledAt >= :startDate
          AND tf.orderFilledAt < :endDate
        ORDER BY tf.orderFilledAt DESC
    """)
    List<TradeFact> findByClientIdAndDateRange(
            @Param("clientId") Long clientId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // All fills for an account within a date range.
    @Query("""
        SELECT tf FROM TradeFact tf
        WHERE tf.accountId = :accountId
          AND tf.orderFilledAt >= :startDate
          AND tf.orderFilledAt < :endDate
        ORDER BY tf.orderFilledAt DESC
    """)
    List<TradeFact> findByAccountIdAndDateRange(
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    
    // All fills for a specific instrument.
    List<TradeFact> findByInstrumentIdOrderByOrderFilledAtDesc(Long instrumentId);


    // Buy-side volume for a client on a given date.
    @Query("""
        SELECT COALESCE(SUM(tf.executionQuantity), 0)
        FROM TradeFact tf
        WHERE tf.clientId = :clientId
          AND tf.side = 'BUY'
          AND CAST(tf.orderFilledAt AS DATE) = :tradeDate
    """)
    BigDecimal totalBuyVolumeForClientOnDate(
            @Param("clientId") Long clientId,
            @Param("tradeDate") LocalDate tradeDate);

    // Sell-side volume for a client on a given date.
    @Query("""
        SELECT COALESCE(SUM(tf.executionQuantity), 0)
        FROM TradeFact tf
        WHERE tf.clientId = :clientId
          AND tf.side = 'SELL'
          AND CAST(tf.orderFilledAt AS DATE) = :tradeDate
    """)
    BigDecimal totalSellVolumeForClientOnDate(
            @Param("clientId") Long clientId,
            @Param("tradeDate") LocalDate tradeDate);

    // Total notional value of trades for a client on a date.
    @Query("""
        SELECT COALESCE(SUM(tf.tradeValue), 0)
        FROM TradeFact tf
        WHERE tf.clientId = :clientId
          AND CAST(tf.orderFilledAt AS DATE) = :tradeDate
    """)
    BigDecimal totalTradeValueForClientOnDate(
            @Param("clientId") Long clientId,
            @Param("tradeDate") LocalDate tradeDate);
}