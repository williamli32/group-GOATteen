package com.goatteen.trading.execution;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface FillRepository
                extends JpaRepository<Fill, Long> {

        Optional<Fill> findByOrderId(Long orderId);

        Optional<Fill> findByIdempotencyKey(
                        String idempotencyKey);

        @EntityGraph(attributePaths = {
                        "order",
                        "order.account",
                        "order.account.client",
                        "order.instrument",
                        "quote"
        })
        @Query("SELECT f FROM Fill f")
        Page<Fill> findAllForReportingBackfill(Pageable pageable);

}
