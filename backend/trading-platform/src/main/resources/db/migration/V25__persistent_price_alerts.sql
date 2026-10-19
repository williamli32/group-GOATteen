CREATE TABLE price_alerts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    instrument_id BIGINT NOT NULL REFERENCES instruments(id) ON DELETE CASCADE,
    direction VARCHAR(5) NOT NULL CHECK (direction IN ('ABOVE', 'BELOW')),
    threshold NUMERIC(20,8) NOT NULL CHECK (threshold > 0),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    generation INTEGER NOT NULL DEFAULT 1 CHECK (generation > 0),
    triggered_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_price_alerts_active ON price_alerts (instrument_id, id) WHERE enabled AND triggered_at IS NULL;
CREATE INDEX idx_price_alerts_user ON price_alerts (user_id, id DESC);
CREATE TABLE price_alert_notifications (
    id BIGSERIAL PRIMARY KEY,
    alert_id BIGINT REFERENCES price_alerts(id) ON DELETE SET NULL,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    generation INTEGER NOT NULL,
    instrument_id BIGINT NOT NULL REFERENCES instruments(id),
    symbol VARCHAR(40) NOT NULL,
    direction VARCHAR(5) NOT NULL,
    threshold NUMERIC(20,8) NOT NULL,
    observed_price NUMERIC(20,8) NOT NULL,
    quote_currency VARCHAR(10) NOT NULL,
    quote_id BIGINT NOT NULL REFERENCES market_quotes(id),
    quote_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP NULL,
    CONSTRAINT uq_price_alert_delivery UNIQUE (alert_id, generation)
);
CREATE INDEX idx_price_alert_notifications_user ON price_alert_notifications (user_id, created_at DESC, id DESC);
-- Keep quotes referenced by notifications forever, like fills.
