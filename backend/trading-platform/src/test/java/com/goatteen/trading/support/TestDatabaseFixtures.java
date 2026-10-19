package com.goatteen.trading.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Deletes only IDs explicitly registered by the current test, after async reporting finishes. */
public final class TestDatabaseFixtures implements AutoCloseable {
    private final JdbcTemplate jdbc;
    private final Set<Long> users = new LinkedHashSet<>();
    private final Set<Long> instruments = new LinkedHashSet<>();

    public TestDatabaseFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        String database = jdbc.queryForObject("SELECT current_database()", String.class);
        if (database == null || !database.endsWith("_test")) {
            throw new IllegalStateException("Fixture cleanup requires a dedicated database ending in _test");
        }
    }

    public long user(long id) {
        users.add(id);
        return id;
    }

    public long instrument(long id) {
        instruments.add(id);
        return id;
    }

    /** Complete the history of a newly inserted SUBMITTED/ACCEPTED fixture only. */
    public static void recordInitialLifecycle(JdbcTemplate jdbc, long orderId) {
        String state = jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, orderId);
        Long history = jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id=?",
                Long.class, orderId);
        if (!("SUBMITTED".equals(state) || "ACCEPTED".equals(state)) || history == null || history != 0) {
            throw new IllegalStateException("Initial lifecycle requires a new fixture with no history: " + orderId);
        }
        jdbc.update("""
                INSERT INTO order_status_history(order_id,status,changed_at,note)
                SELECT o.id,h.status,
                       CASE WHEN h.phase=1 THEN o.submitted_at ELSE COALESCE(o.accepted_at,o.submitted_at) END,
                       'Initial test fixture lifecycle'
                FROM orders o CROSS JOIN (VALUES (1,'SUBMITTED'),(2,'ACCEPTED')) h(phase,status)
                WHERE o.id=? AND h.phase<=CASE WHEN o.status='ACCEPTED' THEN 2 ELSE 1 END
                ORDER BY h.phase
                """, orderId);
    }

    public static void awaitReporting(ThreadPoolTaskExecutor executor) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (executor.getActiveCount() != 0 || !executor.getThreadPoolExecutor().getQueue().isEmpty()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Reporting tasks did not finish before fixture cleanup");
            }
            Thread.sleep(10);
        }
    }

    @Override
    public void close() {
        if (users.isEmpty() && instruments.isEmpty()) {
            return;
        }
        var params = new MapSqlParameterSource()
                .addValue("users", users.isEmpty() ? List.of(-1L) : users)
                .addValue("instruments", instruments.isEmpty() ? List.of(-1L) : instruments);
        var sql = new NamedParameterJdbcTemplate(jdbc);
        String accounts = "SELECT a.id FROM accounts a JOIN clients c ON c.id=a.client_id "
                + "WHERE c.user_id IN (:users)";
        String orders = "SELECT id FROM orders WHERE account_id IN (" + accounts + ")";
        String fills = "SELECT id FROM fills WHERE order_id IN (" + orders + ")";
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        transaction.executeWithoutResult(status -> {
            // Reporting lineage and settlement rows must go before their source fills/orders.
            sql.update("DELETE FROM reporting.sync_tracking WHERE fill_id IN (" + fills + ")", params);
            sql.update("DELETE FROM reporting.trade_facts WHERE order_id IN (" + orders + ")", params);
            sql.update("DELETE FROM reporting.order_facts WHERE order_id IN (" + orders + ")", params);
            sql.update("DELETE FROM position_history WHERE account_id IN (" + accounts + ")", params);
            sql.update("DELETE FROM cash_transactions WHERE account_id IN (" + accounts + ")", params);
            sql.update("DELETE FROM fills WHERE order_id IN (" + orders + ")", params);
            sql.update("DELETE FROM order_status_history WHERE order_id IN (" + orders + ")", params);
            sql.update("DELETE FROM orders WHERE account_id IN (" + accounts + ")", params);
            sql.update("DELETE FROM positions WHERE account_id IN (" + accounts + ")", params);
            sql.update("DELETE FROM accounts WHERE id IN (" + accounts + ")", params);
            sql.update("DELETE FROM clients WHERE user_id IN (:users)", params);
            sql.update("DELETE FROM auth_sessions WHERE user_id IN (:users)", params);
            sql.update("DELETE FROM user_roles WHERE user_id IN (:users)", params);
            sql.update("DELETE FROM users WHERE id IN (:users)", params);
            sql.update("DELETE FROM market_quotes WHERE instrument_id IN (:instruments)", params);
            sql.update("DELETE FROM instruments WHERE id IN (:instruments)", params);
        });
        users.clear();
        instruments.clear();
    }
}
