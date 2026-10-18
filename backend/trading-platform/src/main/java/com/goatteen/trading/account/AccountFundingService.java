package com.goatteen.trading.account;

import com.goatteen.trading.account.dto.AddFundsRequest;
import com.goatteen.trading.account.dto.AddFundsResponse;
import com.goatteen.trading.portfolio.CashTransaction;
import com.goatteen.trading.portfolio.CashTransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class AccountFundingService {
    private final AccountRepository accounts;
    private final CashTransactionRepository transactions;

    public AccountFundingService(AccountRepository accounts, CashTransactionRepository transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @Transactional
    public AddFundsResponse addFunds(Long userId, AddFundsRequest request) {
        if (request == null || request.idempotencyKey() == null) {
            throw new IllegalArgumentException("A funding request ID is required");
        }
        // The account comes exclusively from the authenticated user. Serialise
        // funding requests for that account before reading either its balance
        // or a previous result for the same request ID.
        Account account = accounts.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        AccountCurrency currency = AccountCurrency.fromCode(account.getCurrency());
        BigDecimal amount = currency.validateAmount(request.amount());

        CashTransaction existing = transactions.findByAccountIdAndFundingKey(
                account.getId(), request.idempotencyKey()).orElse(null);
        if (existing != null) {
            if (existing.getAmount().compareTo(amount) != 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This funding request ID has already been used for a different amount");
            }
            return response(account, existing, true);
        }

        BigDecimal before = account.getCashBalance();
        BigDecimal after = before.add(amount);
        if (after.compareTo(new BigDecimal("9999999999999999.99")) > 0) {
            throw new IllegalArgumentException("The resulting balance exceeds the account balance limit");
        }

        CashTransaction entry = new CashTransaction();
        entry.setAccount(account);
        entry.setFundingKey(request.idempotencyKey());
        entry.setAmount(amount);
        entry.setBalanceBefore(before);
        entry.setBalanceAfter(after);
        entry.setCreatedAt(LocalDateTime.now());
        entry.setDescription("Account funding (" + currency.name() + ")");

        account.setCashBalance(after);
        accounts.saveAndFlush(account);
        entry = transactions.save(entry);
        return response(account, entry, false);
    }

    private AddFundsResponse response(Account account, CashTransaction entry, boolean replayed) {
        return new AddFundsResponse(account.getId(), entry.getId(), entry.getAmount(),
                account.getCurrency(), account.getCashBalance(), entry.getCreatedAt(), replayed);
    }
}
