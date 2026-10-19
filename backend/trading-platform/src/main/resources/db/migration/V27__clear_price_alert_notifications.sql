-- Clearing an inbox entry hides it from the user but preserves the
-- triggered price, source quote and generation for investigation/audit.
-- Configured price_alerts are deliberately NOT deleted or disabled.
ALTER TABLE price_alert_notifications
    ADD COLUMN cleared_at TIMESTAMP NULL;

CREATE INDEX idx_price_alert_notices_visible
    ON price_alert_notifications(user_id, created_at DESC, id DESC)
    WHERE cleared_at IS NULL;
