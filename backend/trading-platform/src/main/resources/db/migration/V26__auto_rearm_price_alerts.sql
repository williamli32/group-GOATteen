-- V25 alerts were one-shot. Remember whether a threshold is ready to fire
-- independently from whether the user has paused monitoring.
ALTER TABLE price_alerts
    ADD COLUMN armed BOOLEAN NOT NULL DEFAULT TRUE;

-- Old alerts that fired were disabled automatically by the former evaluator.
-- Migrate them into the waiting-for-reset state. (Old schema cannot distinguish
-- these from alerts explicitly paused *after* an earlier trigger; review this
-- transition with your users if that distinction matters.)
UPDATE price_alerts
SET enabled = TRUE,
    armed = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE triggered_at IS NOT NULL AND enabled = FALSE;

-- Active alerts are no longer constrained to triggered_at IS NULL.
DROP INDEX IF EXISTS idx_price_alerts_active;
CREATE INDEX idx_price_alerts_active
    ON price_alerts (instrument_id, id) WHERE enabled;
