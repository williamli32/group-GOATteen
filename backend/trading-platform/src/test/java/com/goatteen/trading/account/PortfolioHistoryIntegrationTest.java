package com.goatteen.trading.account;

import com.goatteen.trading.fx.HistoricalFxRates;
import com.goatteen.trading.fx.FxRateService.FxRateUnavailableException;
import com.goatteen.trading.support.DatabaseIntegrationTestSupport;
import com.goatteen.trading.support.ReportingLoadFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class PortfolioHistoryIntegrationTest extends DatabaseIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    private HistoricalFxRates fx;
    private PortfolioHistoryService history;
    private long account;
    private long instrument;
    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    @BeforeEach void createFixtures() {
        fx = mock(HistoricalFxRates.class);
        history = new PortfolioHistoryService(jdbc, fx, transactions);
        long user = fixtures.user(jdbc.queryForObject("INSERT INTO users(email,password_hash) VALUES (?, 'test-hash') RETURNING id",
                Long.class, UUID.randomUUID() + "@history.test"));
        long client = jdbc.queryForObject("INSERT INTO clients(user_id,first_name,last_name) VALUES (?,'History','Test') RETURNING id", Long.class, user);
        account = jdbc.queryForObject("""
                INSERT INTO accounts(client_id,account_number,currency,cash_balance,created_at)
                VALUES (?,?,'GBP',2000,?) RETURNING id
                """, Long.class, client, "HIST-" + UUID.randomUUID(), today.minusDays(2).atStartOfDay());
        instrument = fixtures.instrument(jdbc.queryForObject("""
                INSERT INTO instruments(symbol,name,instrument_class,exchange,country_code,currency)
                VALUES (?,'History fixture','EQUITY','NASDAQ','US','USD') RETURNING id
                """, Long.class, "PH" + UUID.randomUUID().toString().substring(0, 12)));
    }

    @Test void cashOnlyHistoryStopsAtAccountCreationAndNeedsNoFx() {
        var points = history.history(account, 30);
        assertEquals(3, points.size());
        assertEquals(today.minusDays(2), points.getFirst().date());
        assertTrue(points.stream().allMatch(point -> new BigDecimal("2000.00").equals(point.value())));
        verifyNoInteractions(fx);
    }

    @Test void convertsHistoricalHoldingsAndReleasesTheReadTransactionBeforeFx() {
        seedHolding();
        when(fx.between(eq("USD"), eq("GBP"), any(), any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "Provider calls must not retain a trading database connection");
            return new TreeMap<>(Map.of(today.minusDays(1), new BigDecimal("0.8")));
        });
        var points = history.history(account, 0);
        assertEquals(new BigDecimal("2080.00"), points.getLast().value());
        assertEquals(new BigDecimal("80.00"), points.getLast().holdingsValue());
        assertTrue(points.getLast().unavailable().isEmpty());
        verify(fx, times(1)).between(eq("USD"), eq("GBP"), any(), any());
    }

    @Test void missingFxCreatesAGapAndNeverLabelsRawUsdAsGbp() {
        seedHolding();
        when(fx.between(anyString(), anyString(), any(), any())).thenThrow(new FxRateUnavailableException("offline"));
        var point = history.history(account, 5).getLast();
        assertNull(point.value()); assertNull(point.holdingsValue());
        assertEquals(new BigDecimal("2000.00"), point.cashValue());
        assertFalse(point.unavailable().isEmpty());
    }

    @Test void missingQuotesCreateAGapWithoutChangingOperationalRows() {
        jdbc.update("INSERT INTO positions(account_id,instrument_id,quantity,updated_at) VALUES (?,?,1,?)",
                account, instrument, LocalDateTime.now(ZoneOffset.UTC));
        var before = jdbc.queryForList("SELECT * FROM accounts WHERE id=?", account);
        var position = jdbc.queryForList("SELECT * FROM positions WHERE account_id=?", account);
        assertNull(history.history(account, 5).getLast().value());
        assertEquals(before, jdbc.queryForList("SELECT * FROM accounts WHERE id=?", account));
        assertEquals(position, jdbc.queryForList("SELECT * FROM positions WHERE account_id=?", account));
    }

    private void seedHolding() {
        LocalDateTime at = today.minusDays(1).atTime(12, 0);
        long quote = jdbc.queryForObject("""
                INSERT INTO market_quotes(instrument_id,bid_price,ask_price,last_price,quoted_at)
                VALUES (?,100,100,100,?) RETURNING id
                """, Long.class, instrument, at);
        ReportingLoadFixture.seed(jdbc, account, instrument, quote, 1, new BigDecimal("2100"), at);
        jdbc.update("UPDATE fills SET fx_rate=0.8,fx_provider='TEST_DAILY',cash_settlement_amount=80 WHERE order_id IN (SELECT id FROM orders WHERE account_id=?)", account);
        jdbc.update("UPDATE cash_transactions SET amount=-80,balance_before=2080,balance_after=2000 WHERE account_id=?", account);
    }
}
