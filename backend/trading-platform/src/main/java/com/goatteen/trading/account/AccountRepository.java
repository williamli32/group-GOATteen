package com.goatteen.trading.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository
        extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    Optional<Account> findByClientId(Long clientId);

    Optional<Account> findByClientUserId(Long userId);

    boolean existsByAccountNumber(String accountNumber);

    @EntityGraph(attributePaths = { "client" })
    @Query("""
            select a
            from Account a
            where a.client.user.id = :userId
            """)
    Optional<Account> findByUserId(@Param("userId") Long userId);
}