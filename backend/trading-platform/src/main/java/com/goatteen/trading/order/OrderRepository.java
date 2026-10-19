package com.goatteen.trading.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository
                extends JpaRepository<Order, Long> {

        List<Order> findByAccountIdOrderBySubmittedAtDesc(
                        Long accountId);

        Optional<Order> findByIdAndAccountId(
                        Long id,
                        Long accountId);

        Optional<Order> findByIdempotencyKey(
                        String idempotencyKey);

                                @Query(value = """
                                                                  SELECT o.id, a.client_id, c.id, a.account_number,
                                                                                         CONCAT(c.first_name, ' ', c.last_name),
                                                                                         o.instrument_id, i.symbol, o.side, o.status,
                                                                                         o.quantity, o.submitted_at
                                                                FROM orders o
                                                                JOIN accounts a ON a.id = o.account_id
                                                                JOIN clients c ON c.id = a.client_id
                                                                JOIN instruments i ON i.id = o.instrument_id
                                                                WHERE (:orderId IS NULL OR o.id = :orderId)
                                                                          AND (CAST(:clientFirstName AS TEXT) IS NULL OR LOWER(c.first_name) LIKE LOWER(CONCAT('%', CAST(:clientFirstName AS TEXT), '%')))
                                                                          AND (CAST(:clientLastName AS TEXT) IS NULL OR LOWER(c.last_name) LIKE LOWER(CONCAT('%', CAST(:clientLastName AS TEXT), '%')))
                                                                        AND (:accountId IS NULL OR a.id = :accountId)
                                                                          AND (CAST(:instrumentSymbol AS TEXT) IS NULL OR LOWER(i.symbol) LIKE LOWER(CONCAT('%', CAST(:instrumentSymbol AS TEXT), '%')))
                                                                        AND (:side IS NULL OR o.side = :side)
                                                                        AND (:status IS NULL OR o.status = :status)
                                                                        AND (CAST(:fromDate AS TIMESTAMP) IS NULL OR o.submitted_at >= CAST(:fromDate AS TIMESTAMP))
                                                                        AND (CAST(:toDate AS TIMESTAMP) IS NULL OR o.submitted_at < CAST(:toDate AS TIMESTAMP))
                                                                ORDER BY o.submitted_at DESC, o.id DESC
                                                                """, nativeQuery = true)
        List<Object[]> searchForOperations(
                        @Param("orderId") Long orderId,
                        @Param("clientFirstName") String clientFirstName,
                        @Param("clientLastName") String clientLastName,
                        @Param("accountId") Long accountId,
                        @Param("instrumentSymbol") String instrumentSymbol,
                        @Param("side") String side,
                        @Param("status") String status,
                        @Param("fromDate") LocalDateTime fromDate,
                        @Param("toDate") LocalDateTime toDate);
        
        List<Order> findByStatus(OrderStatus status);

        @Query("SELECT o FROM Order o WHERE o.id = :id")
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        Optional<Order> findByIdForUpdate(
                        @Param("id") Long id);
}