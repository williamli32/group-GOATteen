package com.goatteen.trading.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;


public interface OrderRepository
        extends JpaRepository<Order, Long> {

    List<Order> findByAccountIdOrderBySubmittedAtDesc(
            Long accountId
    );

    Optional<Order> findByIdAndAccountId(
            Long id,
            Long accountId
    );

    /**
     * Find order by ID with pessimistic write lock.
     * Prevents concurrent execution of the same order by blocking other transactions.
     * Only one transaction can hold a write lock on the order row at a time.
     */
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findByIdForUpdate(@Param("id") Long id);
}