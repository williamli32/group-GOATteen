package com.goatteen.trading.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Historical load-test data only. All measured orders still use real execution and async sync.
 * Seed source fills, settlement/audit records and reporting projections together in one transaction.
 */
public final class ReportingLoadFixture {
    private ReportingLoadFixture() { }

    public static void seed(JdbcTemplate jdbc, long accountId, long instrumentId, long quoteId,
                            int count, BigDecimal initialCash, LocalDateTime executedAt) {
        if (count < 1 || count > 2000) {
            throw new IllegalArgumentException("Fixture count must be between 1 and 2000");
        }
        var params = new MapSqlParameterSource()
                .addValue("account", accountId).addValue("instrument", instrumentId)
                .addValue("quote", quoteId).addValue("count", count).addValue("cash", initialCash)
                .addValue("at", Timestamp.valueOf(executedAt));
        var sql = new NamedParameterJdbcTemplate(jdbc);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        transaction.executeWithoutResult(status -> {
            Long existing = jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE account_id=?", Long.class, accountId);
            Long positions = jdbc.queryForObject("SELECT COUNT(*) FROM positions WHERE account_id=?", Long.class, accountId);
            if (existing == null || existing != 0 || positions == null || positions != 0) {
                throw new IllegalStateException("Load fixtures require a new, empty test account");
            }
            for (String statement : STATEMENTS) {
                sql.update(statement, params);
            }
        });
    }

    private static final List<String> STATEMENTS = List.of(
            """
            INSERT INTO orders(account_id,instrument_id,side,quantity,status,submitted_at,accepted_at,completed_at)
            SELECT :account,:instrument,'BUY',1,'FILLED',:at,:at,:at
            FROM generate_series(1,:count)
            """,
            """
            INSERT INTO fills(order_id,quote_id,fill_price,fill_quantity,executed_at,
                              fx_source_currency,fx_target_currency,fx_rate,fx_rate_date,
                              fx_provider,cash_settlement_amount)
            SELECT o.id,:quote,q.ask_price,o.quantity,:at,
                   i.currency,a.currency,1.0,CAST(:at AS DATE),
                   'TEST_SAME_CURRENCY',ROUND(q.ask_price*o.quantity,2)
            FROM orders o JOIN market_quotes q ON q.id=:quote
            JOIN instruments i ON i.id=o.instrument_id
            JOIN accounts a ON a.id=o.account_id
            WHERE o.account_id=:account AND q.instrument_id=o.instrument_id
            """,
            """
            INSERT INTO order_status_history(order_id,status,changed_at,note)
            SELECT o.id,h.status,:at,'Historical load fixture'
            FROM orders o CROSS JOIN (VALUES (1,'SUBMITTED'),(2,'ACCEPTED'),(3,'FILLED')) h(phase,status)
            WHERE o.account_id=:account ORDER BY o.id,h.phase
            """,
            """
            INSERT INTO cash_transactions(account_id,fill_id,amount,balance_before,balance_after,created_at,description)
            SELECT :account,id,-value,:cash-(n-1)*value,:cash-n*value,:at,'Historical load fixture'
            FROM (SELECT f.id,ROUND(f.fill_quantity*f.fill_price,2) AS value,
                         ROW_NUMBER() OVER (ORDER BY f.id) AS n
                  FROM fills f JOIN orders o ON o.id=f.order_id WHERE o.account_id=:account) s
            ORDER BY id
            """,
            """
            INSERT INTO position_history(account_id,instrument_id,fill_id,quantity_before,quantity_after,quantity_change,recorded_at)
            SELECT :account,:instrument,f.id,ROW_NUMBER() OVER (ORDER BY f.id)-1,
                   ROW_NUMBER() OVER (ORDER BY f.id),1,:at
            FROM fills f JOIN orders o ON o.id=f.order_id WHERE o.account_id=:account ORDER BY f.id
            """,
            """
            INSERT INTO positions(account_id,instrument_id,quantity,updated_at)
            VALUES (:account,:instrument,:count,:at)
            """,
            """
            UPDATE accounts SET cash_balance=:cash-(
                SELECT SUM(ROUND(f.fill_quantity*f.fill_price,2))
                FROM fills f JOIN orders o ON o.id=f.order_id WHERE o.account_id=:account),version=version+1
            WHERE id=:account
            """,
            """
            INSERT INTO reporting.order_facts(order_id,account_id,client_id,instrument_id,instrument_symbol,currency,
                                            side,order_quantity,final_status,submitted_at,completed_at)
            SELECT o.id,o.account_id,a.client_id,o.instrument_id,i.symbol,i.currency,
                   o.side,o.quantity,o.status,o.submitted_at,o.completed_at
            FROM orders o JOIN accounts a ON a.id=o.account_id JOIN instruments i ON i.id=o.instrument_id
            WHERE o.account_id=:account
            """,
            """
            INSERT INTO reporting.trade_facts(fill_id,order_id,order_submitted_at,order_accepted_at,order_filled_at,
                account_id,client_id,client_first_name,client_last_name,instrument_id,instrument_symbol,
                instrument_class,instrument_name,currency,side,order_quantity,execution_price,execution_quantity,
                trade_value,order_status,bid_price,ask_price,quote_timestamp)
            SELECT f.id,o.id,o.submitted_at,o.accepted_at,f.executed_at,o.account_id,a.client_id,
                   c.first_name,c.last_name,i.id,i.symbol,i.instrument_class,i.name,i.currency,o.side,o.quantity,
                   f.fill_price,f.fill_quantity,ROUND(f.fill_price*f.fill_quantity,2),o.status,
                   q.bid_price,q.ask_price,q.quoted_at
            FROM fills f JOIN orders o ON o.id=f.order_id JOIN accounts a ON a.id=o.account_id
            JOIN clients c ON c.id=a.client_id JOIN instruments i ON i.id=o.instrument_id
            JOIN market_quotes q ON q.id=f.quote_id WHERE o.account_id=:account
            """,
            """
            INSERT INTO reporting.sync_tracking(fill_id,event_timestamp,sync_status)
            SELECT f.id,f.executed_at,'SUCCESS' FROM fills f JOIN orders o ON o.id=f.order_id
            WHERE o.account_id=:account
            """
    );
}
