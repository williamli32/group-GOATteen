package com.goatteen.trading.alerts;

import com.goatteen.trading.auth.service.CurrentUserService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PriceAlertService {
    private final JdbcTemplate jdbc;
    private final CurrentUserService currentUser;

    public record Alert(long id, long instrumentId, String symbol, String currency,
                        String direction, BigDecimal threshold, boolean enabled, boolean armed,
                        java.time.LocalDateTime triggeredAt, int generation) {}

    public record Notice(long id, long alertId, long instrumentId, String symbol,
                         String direction, BigDecimal threshold, BigDecimal observedPrice,
                         String currency, java.time.LocalDateTime quoteAt,
                         java.time.LocalDateTime createdAt, java.time.LocalDateTime readAt) {}

    public record Change(long instrumentId, String direction, BigDecimal threshold, boolean enabled) {}

    public PriceAlertService(JdbcTemplate jdbc, CurrentUserService currentUser) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
    }

    private long userId() { return currentUser.getCurrentUserId(); }

    @Transactional(readOnly = true)
    public List<Alert> alerts() {
        return jdbc.query("""
            SELECT a.id,a.instrument_id,i.symbol,i.currency,a.direction,a.threshold,
                   a.enabled,a.armed,a.triggered_at,a.generation
            FROM price_alerts a JOIN instruments i ON i.id=a.instrument_id
            WHERE a.user_id=? ORDER BY a.id DESC
            """, (r,n) -> new Alert(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),
                r.getString(5),r.getBigDecimal(6),r.getBoolean(7),r.getBoolean(8),
                r.getTimestamp(9)==null?null:r.getTimestamp(9).toLocalDateTime(),r.getInt(10)), userId());
    }

    @Transactional
    public Alert create(Change change) {
        validate(change);
        long user = userId();
        requireTradable(change.instrumentId());
        Long id = jdbc.queryForObject("""
            INSERT INTO price_alerts(user_id,instrument_id,direction,threshold,enabled)
            VALUES (?,?,?,?,?) RETURNING id
            """, Long.class, user,change.instrumentId(),change.direction(),change.threshold(),change.enabled());
        return one(user, id);
    }

    @Transactional
    public Alert update(long id, Change change) {
        validate(change);
        long user=userId();
        requireTradable(change.instrumentId());
        // A genuine edit applies the updated condition immediately and starts a fresh cycle.
        int count=jdbc.update("""
            UPDATE price_alerts SET instrument_id=?, direction=?, threshold=?, enabled=?,
                armed=TRUE, triggered_at=NULL, generation=generation+1, updated_at=CURRENT_TIMESTAMP
            WHERE id=? AND user_id=?
            """,change.instrumentId(),change.direction(),change.threshold(),change.enabled(),id,user);
        if (count==0) throw notFound();
        return one(user,id);
    }

    @Transactional
    public Alert setEnabled(long id, boolean enabled) {
        long user=userId();
        // Pausing preserves the cycle. Resuming starts fresh only on a real
        // disabled -> enabled transition; repeated PATCHes are idempotent.
        int count=jdbc.update("""
            UPDATE price_alerts SET enabled=?,
              armed=CASE WHEN ? AND NOT enabled THEN TRUE ELSE armed END,
              triggered_at=CASE WHEN ? AND NOT enabled THEN NULL ELSE triggered_at END,
              generation=CASE WHEN ? AND NOT enabled THEN generation+1 ELSE generation END,
              updated_at=CURRENT_TIMESTAMP
            WHERE id=? AND user_id=?
            """,enabled,enabled,enabled,enabled,id,user);
        if(count==0) throw notFound();
        return one(user,id);
    }

    @Transactional
    public void delete(long id) {
        if(jdbc.update("DELETE FROM price_alerts WHERE id=? AND user_id=?",id,userId())==0)
            throw notFound();
    }

    @Transactional(readOnly = true)
    public List<Notice> notifications() {
        return jdbc.query("""
            SELECT id,alert_id,instrument_id,symbol,direction,threshold,observed_price,
                   quote_currency,quote_at,created_at,read_at
            FROM price_alert_notifications WHERE user_id=? AND cleared_at IS NULL
            ORDER BY created_at DESC,id DESC LIMIT 100
            """,(r,n)-> new Notice(r.getLong(1),r.getLong(2),r.getLong(3),r.getString(4),
                r.getString(5),r.getBigDecimal(6),r.getBigDecimal(7),r.getString(8),
                r.getTimestamp(9).toLocalDateTime(),r.getTimestamp(10).toLocalDateTime(),
                r.getTimestamp(11)==null?null:r.getTimestamp(11).toLocalDateTime()),userId());
    }

    @Transactional
    public void markRead(long notificationId) {
        if(jdbc.update("""
            UPDATE price_alert_notifications SET read_at=COALESCE(read_at,CURRENT_TIMESTAMP)
            WHERE id=? AND user_id=?
            """,notificationId,userId())==0) throw notFound();
    }

    /** Hide a single triggered notification without deleting its immutable price evidence. */
    @Transactional
    public void clearNotification(long notificationId) {
        // The account predicate prevents clearing another customer's notification.
        // COALESCE makes a repeated DELETE safely idempotent.
        if (jdbc.update("""
            UPDATE price_alert_notifications
            SET cleared_at=COALESCE(cleared_at,CURRENT_TIMESTAMP)
            WHERE id=? AND user_id=?
            """, notificationId, userId()) == 0) throw notFound();
    }

    /** Clear the whole notification inbox, not the customer's configured alerts. */
    @Transactional
    public void clearAllNotifications() {
        jdbc.update("""
            UPDATE price_alert_notifications SET cleared_at=CURRENT_TIMESTAMP
            WHERE user_id=? AND cleared_at IS NULL
            """, userId());
    }

    private Alert one(long user,long id) {
        return jdbc.queryForObject("""
            SELECT a.id,a.instrument_id,i.symbol,i.currency,a.direction,a.threshold,
                   a.enabled,a.armed,a.triggered_at,a.generation
            FROM price_alerts a JOIN instruments i ON i.id=a.instrument_id
            WHERE a.id=? AND a.user_id=?
            """,(r,n)->new Alert(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),
                r.getString(5),r.getBigDecimal(6),r.getBoolean(7),r.getBoolean(8),
                r.getTimestamp(9)==null?null:r.getTimestamp(9).toLocalDateTime(),r.getInt(10)),id,user);
    }

    private void requireTradable(long instrumentId) {
        Boolean tradable=jdbc.query("SELECT tradable FROM instruments WHERE id=?",
             r -> r.next()?r.getBoolean(1):null,instrumentId);
        if (tradable==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Instrument not found");
        if (!tradable) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Instrument not tradable");
    }

    private void validate(Change c) {
        if(c==null || c.instrumentId()<=0 || c.threshold()==null ||
           c.threshold().signum()<=0 || c.threshold().scale()>8 ||
           c.threshold().precision()>20 ||
           !("ABOVE".equals(c.direction()) || "BELOW".equals(c.direction())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid alert threshold, instrument or direction");
    }
    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,"Alert or notification not found");
    }
}
