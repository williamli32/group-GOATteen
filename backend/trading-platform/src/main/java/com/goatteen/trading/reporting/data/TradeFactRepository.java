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

@Repository
public interface TradeFactRepository
        extends JpaRepository<TradeFact, Long> {

    Optional<TradeFact> findByFillId(Long fillId);

    // ----------------------------------------------------------------
    // Basic lookups
    // ----------------------------------------------------------------

    @Query("""
            SELECT tf
            FROM TradeFact tf
            WHERE tf.clientId = :clientId
              AND tf.orderFilledAt >= :startDate
              AND tf.orderFilledAt < :endDate
            ORDER BY tf.orderFilledAt DESC
            """)
    List<TradeFact> findByClientIdAndDateRange(
            @Param("clientId") Long clientId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("""
            SELECT tf
            FROM TradeFact tf
            WHERE tf.accountId = :accountId
              AND tf.orderFilledAt >= :startDate
              AND tf.orderFilledAt < :endDate
            ORDER BY tf.orderFilledAt DESC
            """)
    List<TradeFact> findByAccountIdAndDateRange(
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    List<TradeFact> findByInstrumentIdOrderByOrderFilledAtDesc(
            Long instrumentId);

    @Query("""
            SELECT COALESCE(
                SUM(tf.executionQuantity),
                0
            )
            FROM TradeFact tf
            WHERE tf.clientId = :clientId
              AND tf.side = 'BUY'
              AND CAST(tf.orderFilledAt AS DATE) = :tradeDate
            """)
    BigDecimal totalBuyVolumeForClientOnDate(
            @Param("clientId") Long clientId,
            @Param("tradeDate") LocalDate tradeDate);

    @Query("""
            SELECT COALESCE(
                SUM(tf.executionQuantity),
                0
            )
            FROM TradeFact tf
            WHERE tf.clientId = :clientId
              AND tf.side = 'SELL'
              AND CAST(tf.orderFilledAt AS DATE) = :tradeDate
            """)
    BigDecimal totalSellVolumeForClientOnDate(
            @Param("clientId") Long clientId,
            @Param("tradeDate") LocalDate tradeDate);

    @Query("""
            SELECT COALESCE(
                SUM(tf.tradeValue),
                0
            )
            FROM TradeFact tf
            WHERE tf.clientId = :clientId
              AND CAST(tf.orderFilledAt AS DATE) = :tradeDate
            """)
    BigDecimal totalTradeValueForClientOnDate(
            @Param("clientId") Long clientId,
            @Param("tradeDate") LocalDate tradeDate);

    // ----------------------------------------------------------------
    // Instrument metrics
    // ----------------------------------------------------------------

    @Query("""
            SELECT
                tf.instrumentId,
                tf.instrumentSymbol,
                tf.currency,
                COUNT(tf),
                SUM(tf.executionQuantity),
                SUM(tf.tradeValue)

            FROM TradeFact tf

            WHERE tf.orderFilledAt >= :startDate
              AND tf.orderFilledAt < :endDate

            GROUP BY
                tf.instrumentId,
                tf.instrumentSymbol,
                tf.currency

            ORDER BY COUNT(tf) DESC
            """)
    List<Object[]> summarizeByInstrument(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("""
            SELECT
                tf.instrumentId,
                tf.instrumentSymbol,
                tf.currency,
                COUNT(tf),
                SUM(tf.executionQuantity),
                SUM(tf.tradeValue)

            FROM TradeFact tf

            WHERE tf.orderFilledAt >= :startDate
              AND tf.orderFilledAt < :endDate
              AND (
                    :instrumentId IS NULL
                    OR tf.instrumentId = :instrumentId
              )
              AND (
                    :side IS NULL
                    OR tf.side = :side
              )
              AND (
                    :status IS NULL
                    OR tf.orderStatus = :status
              )
              AND (
                    :clientId IS NULL
                    OR tf.clientId = :clientId
              )
              AND (
                    :accountId IS NULL
                    OR tf.accountId = :accountId
              )
              AND (
                    :currency IS NULL
                    OR tf.currency = :currency
              )

            GROUP BY
                tf.instrumentId,
                tf.instrumentSymbol,
                tf.currency

            ORDER BY COUNT(tf) DESC
            """)
    List<Object[]> summarizeByInstrument(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("instrumentId") Long instrumentId,
            @Param("side") String side,
            @Param("status") String status,
            @Param("clientId") Long clientId,
            @Param("accountId") Long accountId,
            @Param("currency") String currency);

    // ----------------------------------------------------------------
    // Client metrics
    // ----------------------------------------------------------------

    @Query("""
            SELECT
                tf.clientId,
                tf.currency,
                COUNT(tf),
                SUM(tf.executionQuantity),
                SUM(tf.tradeValue)

            FROM TradeFact tf

            WHERE tf.orderFilledAt >= :startDate
              AND tf.orderFilledAt < :endDate

            GROUP BY
                tf.clientId,
                tf.currency

            ORDER BY COUNT(tf) DESC
            """)
    List<Object[]> summarizeByClient(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("""
            SELECT
                tf.clientId,
                tf.currency,
                COUNT(tf),
                SUM(tf.executionQuantity),
                SUM(tf.tradeValue)

            FROM TradeFact tf

            WHERE tf.orderFilledAt >= :startDate
              AND tf.orderFilledAt < :endDate
              AND (
                    :instrumentId IS NULL
                    OR tf.instrumentId = :instrumentId
              )
              AND (
                    :side IS NULL
                    OR tf.side = :side
              )
              AND (
                    :status IS NULL
                    OR tf.orderStatus = :status
              )
              AND (
                    :clientId IS NULL
                    OR tf.clientId = :clientId
              )
              AND (
                    :accountId IS NULL
                    OR tf.accountId = :accountId
              )
              AND (
                    :currency IS NULL
                    OR tf.currency = :currency
              )

            GROUP BY
                tf.clientId,
                tf.currency

            ORDER BY COUNT(tf) DESC
            """)
    List<Object[]> summarizeByClient(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("instrumentId") Long instrumentId,
            @Param("side") String side,
            @Param("status") String status,
            @Param("clientId") Long clientId,
            @Param("accountId") Long accountId,
            @Param("currency") String currency);

    // ----------------------------------------------------------------
    // Overall execution metrics
    // ----------------------------------------------------------------

    @Query(value = """
            SELECT
                COALESCE(SUM(execution_quantity), 0),
                COALESCE(SUM(trade_value), 0),

                COUNT(*) FILTER (
                    WHERE side = 'BUY'
                ),

                COALESCE(
                    SUM(execution_quantity)
                        FILTER (
                            WHERE side = 'BUY'
                        ),
                    0
                ),

                COALESCE(
                    SUM(trade_value)
                        FILTER (
                            WHERE side = 'BUY'
                        ),
                    0
                ),

                COUNT(*) FILTER (
                    WHERE side = 'SELL'
                ),

                COALESCE(
                    SUM(execution_quantity)
                        FILTER (
                            WHERE side = 'SELL'
                        ),
                    0
                ),

                COALESCE(
                    SUM(trade_value)
                        FILTER (
                            WHERE side = 'SELL'
                        ),
                    0
                )

            FROM reporting.trade_facts

            WHERE order_filled_at >= :startDate
              AND order_filled_at < :endDate
            """, nativeQuery = true)
    Object[] summarizeExecutionActivity(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query(value = """
            SELECT
                CAST(
                    COALESCE(
                        SUM(execution_quantity),
                        0
                    ) AS TEXT
                ),

                CAST(
                    COALESCE(
                        SUM(trade_value),
                        0
                    ) AS TEXT
                ),

                COUNT(*) FILTER (
                    WHERE side = 'BUY'
                ),

                CAST(
                    COALESCE(
                        SUM(execution_quantity)
                            FILTER (
                                WHERE side = 'BUY'
                            ),
                        0
                    ) AS TEXT
                ),

                CAST(
                    COALESCE(
                        SUM(trade_value)
                            FILTER (
                                WHERE side = 'BUY'
                            ),
                        0
                    ) AS TEXT
                ),

                COUNT(*) FILTER (
                    WHERE side = 'SELL'
                ),

                CAST(
                    COALESCE(
                        SUM(execution_quantity)
                            FILTER (
                                WHERE side = 'SELL'
                            ),
                        0
                    ) AS TEXT
                ),

                CAST(
                    COALESCE(
                        SUM(trade_value)
                            FILTER (
                                WHERE side = 'SELL'
                            ),
                        0
                    ) AS TEXT
                )

            FROM reporting.trade_facts

            WHERE order_filled_at >= :startDate
              AND order_filled_at < :endDate
              AND (
                    :instrumentId IS NULL
                    OR instrument_id = :instrumentId
              )
              AND (
                    :side IS NULL
                    OR side = :side
              )
              AND (
                    :status IS NULL
                    OR order_status = :status
              )
              AND (
                    :clientId IS NULL
                    OR client_id = :clientId
              )
              AND (
                    :accountId IS NULL
                    OR account_id = :accountId
              )
              AND (
                    :currency IS NULL
                    OR currency = :currency
              )
            """, nativeQuery = true)
    Object[] summarizeExecutionActivity(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("instrumentId") Long instrumentId,
            @Param("side") String side,
            @Param("status") String status,
            @Param("clientId") Long clientId,
            @Param("accountId") Long accountId,
            @Param("currency") String currency);

    // ----------------------------------------------------------------
    // Daily execution metrics / trends
    // ----------------------------------------------------------------

    @Query(value = """
            SELECT
                DATE(order_filled_at),
                COALESCE(
                    SUM(execution_quantity),
                    0
                ),
                COALESCE(
                    SUM(trade_value),
                    0
                )

            FROM reporting.trade_facts

            WHERE order_filled_at >= :startDate
              AND order_filled_at < :endDate

            GROUP BY DATE(order_filled_at)

            ORDER BY DATE(order_filled_at)
            """, nativeQuery = true)
    List<Object[]> summarizeDailyExecution(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query(value = """
            SELECT
                DATE(order_filled_at),
                COALESCE(
                    SUM(execution_quantity),
                    0
                ),
                COALESCE(
                    SUM(trade_value),
                    0
                )

            FROM reporting.trade_facts

            WHERE order_filled_at >= :startDate
              AND order_filled_at < :endDate
              AND (
                    :instrumentId IS NULL
                    OR instrument_id = :instrumentId
              )
              AND (
                    :side IS NULL
                    OR side = :side
              )
              AND (
                    :status IS NULL
                    OR order_status = :status
              )
              AND (
                    :clientId IS NULL
                    OR client_id = :clientId
              )
              AND (
                    :accountId IS NULL
                    OR account_id = :accountId
              )
              AND (
                    :currency IS NULL
                    OR currency = :currency
              )

            GROUP BY DATE(order_filled_at)

            ORDER BY DATE(order_filled_at)
            """, nativeQuery = true)
    List<Object[]> summarizeDailyExecution(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("instrumentId") Long instrumentId,
            @Param("side") String side,
            @Param("status") String status,
            @Param("clientId") Long clientId,
            @Param("accountId") Long accountId,
            @Param("currency") String currency);
}