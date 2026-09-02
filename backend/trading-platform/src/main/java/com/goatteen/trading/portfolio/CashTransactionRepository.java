package com.goatteen.trading.portfolio;


import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface CashTransactionRepository
        extends JpaRepository<CashTransaction, Long> {


    List<CashTransaction> findByAccountIdOrderByCreatedAtDesc(Long accountId);

}
