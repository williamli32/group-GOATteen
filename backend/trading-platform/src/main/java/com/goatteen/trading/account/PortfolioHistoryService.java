package com.goatteen.trading.account;

import com.goatteen.trading.account.dto.PortfolioHistoryPoint;
import com.goatteen.trading.fx.HistoricalFxRates;
import com.goatteen.trading.fx.FxRateService.FxRateUnavailableException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

@Service
public class PortfolioHistoryService {
    private final JdbcTemplate jdbc;
    private final HistoricalFxRates fx;
    private final TransactionTemplate snapshotTransaction;

    public PortfolioHistoryService(JdbcTemplate jdbc, HistoricalFxRates fx,
                                   PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.fx = fx;
        snapshotTransaction = new TransactionTemplate(transactions);
        snapshotTransaction.setReadOnly(true);
        snapshotTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        snapshotTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public List<PortfolioHistoryPoint> history(Long accountId, int days) {
        Snapshot snapshot = snapshotTransaction.execute(status -> readSnapshot(accountId, days));
        if (snapshot == null) return List.of();
        // Release the database snapshot/connection before contacting the FX provider.
        Map<String, NavigableMap<LocalDate, BigDecimal>> rates = new LinkedHashMap<>();
        for (Row row : snapshot.rows()) {
            if (row.currency() == null || row.currency().equals(snapshot.currency())
                    || row.quantity() == null || row.quantity().signum() == 0 || rates.containsKey(row.currency())) continue;
            try {
                rates.put(row.currency(), fx.between(row.currency(), snapshot.currency(), snapshot.start(), snapshot.end()));
            } catch (FxRateUnavailableException unavailable) {
                rates.put(row.currency(), new TreeMap<>());
            }
        }
        Map<LocalDate, List<Row>> byDay = new LinkedHashMap<>();
        snapshot.rows().forEach(row -> byDay.computeIfAbsent(row.day(), ignored -> new ArrayList<>()).add(row));
        List<PortfolioHistoryPoint> result = new ArrayList<>();
        int digits = Currency.getInstance(snapshot.currency()).getDefaultFractionDigits();
        byDay.forEach((day, rows) -> {
            BigDecimal holdings = BigDecimal.ZERO;
            var unavailable = new LinkedHashSet<String>();
            for (Row row : rows) {
                if (row.symbol() == null || (row.quantity() != null && row.quantity().signum() == 0)) continue;
                var observation = row.currency().equals(snapshot.currency()) ? Map.entry(day, BigDecimal.ONE)
                        : rates.getOrDefault(row.currency(), new TreeMap<>()).floorEntry(day);
                if (row.quantity() == null || row.quantity().signum() < 0 || row.price() == null || row.price().signum() <= 0
                        || row.priceDay() == null || row.priceDay().isBefore(day.minusDays(7))
                        || observation == null || observation.getKey().isBefore(day.minusDays(5))) {
                    unavailable.add(row.symbol());
                } else {
                    holdings = holdings.add(row.quantity().multiply(row.price()).multiply(observation.getValue()));
                }
            }
            BigDecimal cash = rows.getFirst().cash().setScale(digits, RoundingMode.HALF_UP);
            BigDecimal valuedHoldings = unavailable.isEmpty() ? holdings.setScale(digits, RoundingMode.HALF_UP) : null;
            result.add(new PortfolioHistoryPoint(day, valuedHoldings == null ? null : cash.add(valuedHoldings),
                    cash, valuedHoldings, List.copyOf(unavailable)));
        });
        return List.copyOf(result);
    }

    private Snapshot readSnapshot(Long accountId, int days) {
        if (!(days == 0 || days == -1 || (days > 0 && days <= 365)))
            throw new IllegalArgumentException("Choose 1–365 days, YTD (-1), or all account history (0)");
        var account = jdbc.queryForObject("SELECT currency, created_at::date FROM accounts WHERE id=?",
                (r, n) -> new AccountStart(r.getString(1), r.getObject(2, LocalDate.class)), accountId);
        if (account == null) throw new IllegalArgumentException("Account not found");
        LocalDate end = LocalDate.now(ZoneOffset.UTC);
        LocalDate requested = days == 0 ? account.created() : days == -1 ? end.withDayOfYear(1) : end.minusDays(days - 1L);
        LocalDate start = requested.isBefore(account.created()) ? account.created() : requested;
        var rows = jdbc.query("""
                WITH days AS (
                    SELECT generate_series(?::date, ?::date, interval '1 day')::date AS day
                ), held AS (
                    SELECT instrument_id FROM positions WHERE account_id=?
                    UNION SELECT instrument_id FROM position_history WHERE account_id=?
                )
                SELECT d.day,
                    COALESCE((SELECT ct.balance_after FROM cash_transactions ct
                        WHERE ct.account_id=? AND ct.created_at < d.day + interval '1 day'
                        ORDER BY ct.created_at DESC,ct.id DESC LIMIT 1),
                        (SELECT cash_balance FROM accounts WHERE id=?) - COALESCE(
                            (SELECT SUM(amount) FROM cash_transactions WHERE account_id=?
                                AND created_at >= d.day + interval '1 day'), 0)) AS cash,
                    i.symbol,i.currency,
                    COALESCE((SELECT quantity_after FROM position_history ph WHERE ph.account_id=?
                        AND ph.instrument_id=h.instrument_id AND ph.recorded_at < d.day + interval '1 day'
                        ORDER BY ph.recorded_at DESC,ph.id DESC LIMIT 1),
                        (SELECT quantity_before FROM position_history ph WHERE ph.account_id=?
                            AND ph.instrument_id=h.instrument_id ORDER BY ph.recorded_at,ph.id LIMIT 1),
                        CASE WHEN COALESCE(p.quantity,0)=0 THEN 0 ELSE NULL END) AS quantity,
                    q.last_price,q.quoted_at::date AS price_day
                FROM days d LEFT JOIN held h ON TRUE
                LEFT JOIN instruments i ON i.id=h.instrument_id
                LEFT JOIN positions p ON p.account_id=? AND p.instrument_id=h.instrument_id
                LEFT JOIN LATERAL (SELECT last_price,quoted_at FROM market_quotes mq
                    WHERE mq.instrument_id=h.instrument_id AND mq.quoted_at < d.day + interval '1 day'
                    AND mq.quoted_at <= (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')
                    ORDER BY mq.quoted_at DESC,mq.id DESC LIMIT 1) q ON TRUE
                ORDER BY d.day,i.id
                """, (r, n) -> new Row(r.getObject("day", LocalDate.class), r.getBigDecimal("cash"),
                        r.getString("symbol"), r.getString("currency"), r.getBigDecimal("quantity"),
                        r.getBigDecimal("last_price"), r.getObject("price_day", LocalDate.class)),
                start, end, accountId, accountId, accountId, accountId, accountId, accountId, accountId, accountId);
        return new Snapshot(account.currency(), start, end, rows);
    }

    private record AccountStart(String currency, LocalDate created) {}
    private record Row(LocalDate day, BigDecimal cash, String symbol, String currency,
                       BigDecimal quantity, BigDecimal price, LocalDate priceDay) {}
    private record Snapshot(String currency, LocalDate start, LocalDate end, List<Row> rows) {}
}
