package com.goatteen.trading.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CashTransactionRepository
        extends JpaRepository<CashTransaction, Long> {

    List<CashTransaction> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    Optional<CashTransaction>
            findFirstByAccountIdOrderByCreatedAtDescIdDesc(Long accountId);

    List<CashTransaction> findByFillIdOrderByCreatedAtAsc(
            Long fillId);
}