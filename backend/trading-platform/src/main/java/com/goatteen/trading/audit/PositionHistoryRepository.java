package com.goatteen.trading.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PositionHistoryRepository extends JpaRepository<PositionHistory, Long> {
    Optional<PositionHistory> findByFillId(Long fillId);
}
