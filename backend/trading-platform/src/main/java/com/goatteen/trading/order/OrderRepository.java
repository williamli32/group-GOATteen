package com.goatteen.trading.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
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
        
        List<Order> findByStatus(OrderStatus status);

        @Query("SELECT o FROM Order o WHERE o.id = :id")
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        Optional<Order> findByIdForUpdate(
                        @Param("id") Long id);
}