package com.goatteen.trading.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CashTransactionRepository
        extends JpaRepository<CashTransaction, Long> {

    Optional<CashTransaction> findByAccountIdAndFundingKey(Long accountId, UUID fundingKey);

        Optional<CashTransaction> findByAccountIdAndWithdrawalKey(Long accountId, UUID withdrawalKey);

    List<CashTransaction> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    Optional<CashTransaction>
            findFirstByAccountIdOrderByCreatedAtDescIdDesc(Long accountId);

    List<CashTransaction> findByFillIdOrderByCreatedAtAsc(
            Long fillId);
}
