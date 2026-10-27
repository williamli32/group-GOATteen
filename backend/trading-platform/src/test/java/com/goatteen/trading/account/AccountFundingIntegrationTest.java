package com.goatteen.trading.account;

import com.goatteen.trading.support.DatabaseIntegrationTestSupport;

import com.goatteen.trading.account.dto.AddFundsRequest;
import com.goatteen.trading.account.dto.AddFundsResponse;
import com.goatteen.trading.account.dto.WithdrawFundsRequest;
import com.goatteen.trading.auth.dto.RegisterRequest;
import com.goatteen.trading.auth.security.AuthProperties;
import com.goatteen.trading.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AccountFundingIntegrationTest extends DatabaseIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountFundingService funding;
    @Autowired AuthService registration;
    @Autowired AuthProperties authProperties;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach
    void requireDedicatedTestDatabase() {
        assertEquals("leap_trading_test", jdbc.queryForObject("select current_database()", String.class),
                "Funding integration tests must use the dedicated test database");
    }

    private long[] createAccount() {
        String suffix = UUID.randomUUID().toString();
        Long userId = jdbc.queryForObject("INSERT INTO users(email,password_hash) VALUES (?,?) RETURNING id",
                Long.class, suffix + "@funding.test", "integration-test-password-hash");
        fixtures.user(userId);
        Long clientId = jdbc.queryForObject("INSERT INTO clients(user_id,first_name,last_name) VALUES (?,'Funding','Test') RETURNING id",
                Long.class, userId);
        Long accountId = jdbc.queryForObject("INSERT INTO accounts(client_id,account_number,currency) VALUES (?,?,'USD') RETURNING id",
                Long.class, clientId, "FUND-" + suffix);
        return new long[]{userId, accountId};
    }

    private BigDecimal balance(long accountId) {
        return jdbc.queryForObject("SELECT cash_balance FROM accounts WHERE id=?", BigDecimal.class, accountId);
    }

    private int credits(long accountId) {
        return jdbc.queryForObject("SELECT count(*) FROM cash_transactions WHERE account_id=? AND funding_key IS NOT NULL",
                Integer.class, accountId);
    }

    private AddFundsResponse[] simultaneous(long userId, UUID first, UUID second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var a = executor.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return funding.addFunds(userId, new AddFundsRequest(new BigDecimal("10000.00"), first));
            });
            var b = executor.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return funding.addFunds(userId, new AddFundsRequest(new BigDecimal("10000.00"), second));
            });
            start.countDown();
            return new AddFundsResponse[]{a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)};
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS),
                    "Funding workers must finish before fixture cleanup");
        }
    }

    @Test
    void simultaneousRetriesCreditTheAccountOnlyOnce() throws Exception {
        long[] account = createAccount();
        UUID key = UUID.randomUUID();
        var results = simultaneous(account[0], key, key);
        assertEquals(new BigDecimal("10000.00"), balance(account[1]));
        assertEquals(1, credits(account[1]));
        assertNotEquals(results[0].replayed(), results[1].replayed());
    }

    @Test
    void independentSimultaneousDepositsDoNotLoseAnUpdate() throws Exception {
        long[] account = createAccount();
        simultaneous(account[0], UUID.randomUUID(), UUID.randomUUID());
        assertEquals(new BigDecimal("20000.00"), balance(account[1]));
        assertEquals(2, credits(account[1]));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM cash_transactions WHERE account_id=? AND fill_id IS NOT NULL",
                Integer.class, account[1]));
    }

    @Test
    void rollbackRemovesBothTheBalanceChangeAndLedgerEntry() {
        long[] account = createAccount();
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            funding.addFunds(account[0], new AddFundsRequest(new BigDecimal("10000.00"), UUID.randomUUID()));
            throw new IllegalStateException("Force transaction rollback");
        }));
        assertEquals(new BigDecimal("0.00"), balance(account[1]));
        assertEquals(0, credits(account[1]));
    }

    @Test
    void withdrawalRetriesDebitOnceAndDoNotReuseADepositResult() {
        long[] account = createAccount();
        UUID key = UUID.randomUUID();
        funding.addFunds(account[0], new AddFundsRequest(new BigDecimal("100.00"), key));
        var request = new WithdrawFundsRequest(new BigDecimal("40.00"), key);
        var original = funding.withdrawFunds(account[0], request);
        var replay = funding.withdrawFunds(account[0], request);
        assertFalse(original.replayed());
        assertTrue(replay.replayed());
        assertEquals(original.transactionId(), replay.transactionId());
        assertEquals(new BigDecimal("-40.00"), replay.amount());
        assertEquals(new BigDecimal("60.00"), balance(account[1]));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM cash_transactions WHERE account_id=? AND withdrawal_key IS NOT NULL",
                Integer.class, account[1]));
    }

    @Test
    void insufficientWithdrawalLeavesTheBalanceAndLedgerUnchanged() {
        long[] account = createAccount();
        funding.addFunds(account[0], new AddFundsRequest(new BigDecimal("100.00"), UUID.randomUUID()));
        var rejected = assertThrows(ResponseStatusException.class, () -> funding.withdrawFunds(account[0],
                new WithdrawFundsRequest(new BigDecimal("100.01"), UUID.randomUUID())));
        assertEquals(409, rejected.getStatusCode().value());
        assertEquals(new BigDecimal("100.00"), balance(account[1]));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM cash_transactions WHERE account_id=?",
                Integer.class, account[1]));
    }

    @ParameterizedTest
    @CsvSource({"CLIENT,USD", "ANALYST,GBP", "OPERATIONS,INR"})
    void registrationPersistsTheChosenRoleAndCurrency(String role, String currency) {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Signup"); request.setLastName("Test");
        request.setEmail(UUID.randomUUID() + "@signup.test"); request.setPassword("Test-password-123!");
        request.setRole(role); request.setCurrency(currency);
        if (!"CLIENT".equals(role)) {
            request.setAccessCode(authProperties.getInternalAccessCode());
        }
        var result = registration.register(request);
        fixtures.user(jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, request.getEmail()));
        assertEquals(currency, result.getCurrency());
        assertEquals(currency, jdbc.queryForObject("SELECT currency FROM accounts WHERE account_number=?",
                String.class, result.getAccountNumber()));
        assertEquals(role, jdbc.queryForObject("""
                SELECT r.name FROM roles r JOIN user_roles ur ON ur.role_id=r.id
                JOIN users u ON u.id=ur.user_id WHERE u.email=?
                """, String.class, request.getEmail()));
    }
}
