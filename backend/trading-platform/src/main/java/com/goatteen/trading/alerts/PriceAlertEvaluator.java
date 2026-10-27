package com.goatteen.trading.alerts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class PriceAlertEvaluator {
    private static final Logger log = LoggerFactory.getLogger(PriceAlertEvaluator.class);
    private final JdbcTemplate jdbc;
    private final int maxQuoteAgeSeconds;

    public PriceAlertEvaluator(JdbcTemplate jdbc,
          @Value("${app.price-alerts.max-quote-age-seconds:30}") int maxQuoteAgeSeconds) {
        this.jdbc = jdbc;
        this.maxQuoteAgeSeconds = maxQuoteAgeSeconds;
    }

    /**
     * An enabled alert has two phases:
     *   armed=true:  fire on a qualifying quote and disarm;
     *   armed=false: rearm ONLY after a fresh quote crosses back to the
     *                nonqualifying side of the threshold.
     * The next crossing can then produce a new notification (new generation).
     * Updating the alert and writing its notice occur in one transaction.
     */
    @Transactional
    public void evaluate() {
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        var transitions = jdbc.query("""
            SELECT a.id,a.user_id,a.generation,a.instrument_id,a.direction,a.threshold,
                   a.armed,i.symbol,i.currency,q.id AS quote_id,q.last_price,q.quoted_at
            FROM price_alerts a JOIN instruments i ON i.id=a.instrument_id
            JOIN LATERAL (
                SELECT id,last_price,quoted_at FROM market_quotes
                WHERE instrument_id=a.instrument_id
                ORDER BY quoted_at DESC,id DESC LIMIT 1
            ) q ON TRUE
            WHERE a.enabled=TRUE AND i.tradable=TRUE AND q.quoted_at >= ? AND q.quoted_at <= ?
              AND (
                (a.armed AND (
                    (a.direction='ABOVE' AND q.last_price >= a.threshold) OR
                    (a.direction='BELOW' AND q.last_price <= a.threshold)
                )) OR
                (NOT a.armed AND (
                    (a.direction='ABOVE' AND q.last_price < a.threshold) OR
                    (a.direction='BELOW' AND q.last_price > a.threshold)
                ))
              )
            ORDER BY a.id
            FOR UPDATE OF a SKIP LOCKED
            """,(r,n)->new Transition(
              r.getLong(1),r.getLong(2),r.getInt(3),r.getLong(4),
              r.getString(5),r.getBigDecimal(6),r.getBoolean(7),
              r.getString(8),r.getString(9),r.getLong(10),
              r.getBigDecimal(11),r.getTimestamp(12).toLocalDateTime()),
              now.minusSeconds(maxQuoteAgeSeconds), now);

        for (Transition transition : transitions) {
            if (!transition.armed) {
                // A fresh quote has crossed back OUTSIDE the alert condition.
                // Increment generation here so the following trigger is unique.
                jdbc.update("""
                    UPDATE price_alerts SET armed=TRUE, generation=generation+1,
                        updated_at=CURRENT_TIMESTAMP
                    WHERE id=? AND generation=? AND enabled=TRUE AND armed=FALSE
                    """, transition.id, transition.generation);
                continue;
            }

            // A fresh quote has crossed INTO the alert condition.
            int updated = jdbc.update("""
                UPDATE price_alerts SET armed=FALSE, triggered_at=CURRENT_TIMESTAMP,
                    updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND generation=? AND enabled=TRUE AND armed=TRUE
                """, transition.id, transition.generation);
            if (updated == 1) {
                jdbc.update("""
                    INSERT INTO price_alert_notifications
                      (alert_id,user_id,generation,instrument_id,symbol,direction,threshold,
                       observed_price,quote_currency,quote_id,quote_at)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?)
                    ON CONFLICT (alert_id,generation) DO NOTHING
                    """, transition.id, transition.userId, transition.generation,
                    transition.instrumentId, transition.symbol, transition.direction,
                    transition.threshold, transition.price, transition.currency,
                    transition.quoteId, transition.quoteAt);
            }
        }
        if (!transitions.isEmpty())
            log.debug("Processed {} price alert state transitions", transitions.size());
    }

    private record Transition(long id, long userId, int generation, long instrumentId,
            String direction, BigDecimal threshold, boolean armed, String symbol,
            String currency, long quoteId, BigDecimal price, LocalDateTime quoteAt) {}
}
