package com.goatteen.trading.account;

import com.goatteen.trading.account.dto.PortfolioHistoryPoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class PortfolioHistoryService {
    private final JdbcTemplate jdbc;

    public PortfolioHistoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<PortfolioHistoryPoint> history(Long accountId, int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate;
        if (days == 0) {
            startDate = jdbc.queryForObject("""
                    SELECT COALESCE(MIN(event_day), CURRENT_DATE)
                    FROM (
                        SELECT created_at::date AS event_day
                        FROM cash_transactions
                        WHERE account_id = ?
                        UNION ALL
                        SELECT recorded_at::date AS event_day
                        FROM position_history
                        WHERE account_id = ?
                    ) account_events
                    """, LocalDate.class, accountId, accountId);
                } else if (days == -1) {
                    startDate = endDate.withDayOfYear(1);
        } else {
            int boundedDays = Math.max(1, Math.min(days, 365));
            startDate = endDate.minusDays(boundedDays - 1L);
        }

        String sql = """
                WITH days AS (
                    SELECT generate_series(?::date, ?::date, interval '1 day')::date AS day
                ),
                daily_cash AS (
                    SELECT
                        d.day,
                        COALESCE(
                            (
                                SELECT ct.balance_after
                                FROM cash_transactions ct
                                WHERE ct.account_id = ?
                                  AND ct.created_at < d.day + interval '1 day'
                                ORDER BY ct.created_at DESC, ct.id DESC
                                LIMIT 1
                            ),
                            (
                                SELECT a.cash_balance
                                FROM accounts a
                                WHERE a.id = ?
                            ) - COALESCE(
                                (
                                    SELECT SUM(ct.amount)
                                    FROM cash_transactions ct
                                    WHERE ct.account_id = ?
                                      AND ct.created_at >= d.day + interval '1 day'
                                ),
                                0
                            )
                        ) AS cash_value
                    FROM days d
                )
                SELECT
                    dc.day,
                    dc.cash_value,
                    COALESCE(SUM(
                        COALESCE((
                            SELECT ph.quantity_after
                            FROM position_history ph
                            WHERE ph.account_id = ?
                              AND ph.instrument_id = p.instrument_id
                              AND ph.recorded_at < dc.day + interval '1 day'
                            ORDER BY ph.recorded_at DESC, ph.id DESC
                            LIMIT 1
                        ), 0)
                        * COALESCE((
                            SELECT mq.last_price
                            FROM market_quotes mq
                            WHERE mq.instrument_id = p.instrument_id
                              AND mq.quoted_at < dc.day + interval '1 day'
                            ORDER BY mq.quoted_at DESC, mq.id DESC
                            LIMIT 1
                        ), 0)
                    ), 0) AS holdings_value
                FROM daily_cash dc
                LEFT JOIN positions p ON p.account_id = ?
                GROUP BY dc.day, dc.cash_value
                ORDER BY dc.day
                """;

        return jdbc.query(sql, (result, rowNumber) -> {
            LocalDate date = result.getObject("day", LocalDate.class);
            BigDecimal cash = result.getBigDecimal("cash_value");
            BigDecimal holdings = result.getBigDecimal("holdings_value");
            return new PortfolioHistoryPoint(date, cash.add(holdings), cash, holdings);
        }, startDate, endDate, accountId, accountId, accountId, accountId, accountId);
    }
}
