
package com.goatteen.trading.reporting.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderFactRepository
                extends JpaRepository<OrderFact, Long> {

        Optional<OrderFact> findByOrderId(Long orderId);

        // Find submitted orders within a date range.
        List<OrderFact> findBySubmittedAtGreaterThanEqualAndSubmittedAtLessThan(
                        LocalDateTime startDate,
                        LocalDateTime endDate);

        // Count orders by status during a reporting period.
        @Query("""
                            SELECT COUNT(o)
                            FROM OrderFact o
                            WHERE o.finalStatus = :status
                              AND o.submittedAt >= :startDate
                              AND o.submittedAt < :endDate
                        """)
        long countByStatusAndDateRange(
                        @Param("status") String status,
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        // Count all submitted orders during a reporting period.
        @Query("""
                            SELECT COUNT(o)
                            FROM OrderFact o
                            WHERE o.submittedAt >= :startDate
                              AND o.submittedAt < :endDate
                        """)
        long countOrdersInDateRange(
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        // Count distinct clients submitting orders.
        @Query("""
                            SELECT COUNT(DISTINCT o.clientId)
                            FROM OrderFact o
                            WHERE o.submittedAt >= :startDate
                              AND o.submittedAt < :endDate
                        """)
        long countActiveClients(
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        @Query("""
                            SELECT
                                o.clientId,
                                o.currency,
                                COUNT(o)
                            FROM OrderFact o
                            WHERE o.submittedAt >= :startDate
                              AND o.submittedAt < :endDate
                            GROUP BY o.clientId, o.currency
                            ORDER BY COUNT(o) DESC
                        """)
        List<Object[]> summarizeOrdersByClient(
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        @Query(value = """
                        SELECT
                            DATE(submitted_at),
                            COUNT(*),
                            COUNT(*) FILTER (WHERE final_status = 'FILLED'),
                            COUNT(*) FILTER (WHERE final_status = 'REJECTED'),
                            COUNT(DISTINCT client_id)

                        FROM reporting.order_facts

                        WHERE submitted_at >= :startDate
                          AND submitted_at < :endDate

                        GROUP BY DATE(submitted_at)
                        ORDER BY DATE(submitted_at)
                        """, nativeQuery = true)
        List<Object[]> summarizeDailyActivity(
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);
}
