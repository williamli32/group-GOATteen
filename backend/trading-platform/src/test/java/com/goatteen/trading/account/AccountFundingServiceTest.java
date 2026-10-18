package com.goatteen.trading.account;

import com.goatteen.trading.account.dto.AddFundsRequest;
import com.goatteen.trading.client.Client;
import com.goatteen.trading.portfolio.CashTransaction;
import com.goatteen.trading.portfolio.CashTransactionRepository;
import com.goatteen.trading.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountFundingServiceTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final CashTransactionRepository transactions = mock(CashTransactionRepository.class);
    private final AccountFundingService service = new AccountFundingService(accounts, transactions);
    private final UUID key = UUID.randomUUID();
    private Account account;

    @BeforeEach
    void setup() {
        account = new Account(new Client(new User("test@example.com", "hash"), "Test", "User"), "ACC-10", "USD");
        ReflectionTestUtils.setField(account, "id", 10L);
        account.setCashBalance(new BigDecimal("250.00"));
        when(accounts.findByUserIdForUpdate(7L)).thenReturn(Optional.of(account));
        when(transactions.save(any())).thenAnswer(call -> {
            CashTransaction entry = call.getArgument(0);
            ReflectionTestUtils.setField(entry, "id", 99L);
            return entry;
        });
    }

    @Test
    void creditsTenThousandInTheAccountCurrencyAndRecordsTheLedger() {
        var response = service.addFunds(7L, new AddFundsRequest(new BigDecimal("10000.00"), key));
        assertEquals(new BigDecimal("10250.00"), account.getCashBalance());
        assertEquals(account.getCashBalance(), response.cashBalance());
        assertEquals("USD", response.currency());
        assertFalse(response.replayed());
        var entry = org.mockito.ArgumentCaptor.forClass(CashTransaction.class);
        verify(transactions).save(entry.capture());
        assertEquals(new BigDecimal("250.00"), entry.getValue().getBalanceBefore());
        assertEquals(new BigDecimal("10250.00"), entry.getValue().getBalanceAfter());
        assertEquals(key, entry.getValue().getFundingKey());
        assertNull(entry.getValue().getFill());
        verify(accounts).findByUserIdForUpdate(7L);
        verify(accounts).saveAndFlush(account);
    }

    @Test
    void retryReturnsTheExistingCreditAndCurrentBalanceWithoutAddingItAgain() {
        CashTransaction existing = new CashTransaction();
        existing.setAmount(new BigDecimal("10000.00"));
        existing.setBalanceAfter(new BigDecimal("10250.00"));
        account.setCashBalance(new BigDecimal("10100.00"));
        when(transactions.findByAccountIdAndFundingKey(10L, key)).thenReturn(Optional.of(existing));
        var result = service.addFunds(7L, new AddFundsRequest(new BigDecimal("10000"), key));
        assertTrue(result.replayed());
        assertEquals(new BigDecimal("10100.00"), result.cashBalance());
        verify(accounts, never()).saveAndFlush(any());
        verify(transactions, never()).save(any());
    }

    @Test
    void cannotReuseAFundingRequestForADifferentAmount() {
        CashTransaction existing = new CashTransaction();
        existing.setAmount(new BigDecimal("10000"));
        when(transactions.findByAccountIdAndFundingKey(10L, key)).thenReturn(Optional.of(existing));
        var exception = assertThrows(ResponseStatusException.class,
                () -> service.addFunds(7L, new AddFundsRequest(BigDecimal.TEN, key)));
        assertEquals(409, exception.getStatusCode().value());
        verify(accounts, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.001", "10000000000000000"})
    void rejectsInvalidAmountsWithoutWriting(String value) {
        assertThrows(IllegalArgumentException.class,
                () -> service.addFunds(7L, new AddFundsRequest(new BigDecimal(value), key)));
        verify(accounts, never()).saveAndFlush(any());
        verify(transactions, never()).save(any());
    }

    @Test
    void cannotCreditSomeoneElsesAccount() {
        assertThrows(ResponseStatusException.class,
                () -> service.addFunds(99L, new AddFundsRequest(BigDecimal.TEN, key)));
        verify(accounts).findByUserIdForUpdate(99L);
        verify(transactions, never()).save(any());
    }

    @Test
    void preventsBalanceOverflow() {
        account.setCashBalance(new BigDecimal("9999999999999999.99"));
        assertThrows(IllegalArgumentException.class,
                () -> service.addFunds(7L, new AddFundsRequest(BigDecimal.ONE, key)));
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void rejectsFractionalYen() {
        ReflectionTestUtils.setField(account, "currency", "JPY");
        assertThrows(IllegalArgumentException.class,
                () -> service.addFunds(7L, new AddFundsRequest(new BigDecimal("1.10"), key)));
        verify(transactions, never()).save(any());
    }
}
