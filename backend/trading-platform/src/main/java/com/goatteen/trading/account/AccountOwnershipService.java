package com.goatteen.trading.account;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountOwnershipService {

    private final AccountRepository accountRepository;

    public AccountOwnershipService(
            AccountRepository accountRepository
    ) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public Account getCurrentUserAccount(Long userId) {

        return accountRepository
                .findByUserId(userId)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Account not found for authenticated user"
                        )
                );
    }
}
