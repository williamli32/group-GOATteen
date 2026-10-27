-- Preserve the first recorded price of each UTC day independently of tick cleanup.
CREATE TABLE daily_market_baselines (
    instrument_id BIGINT NOT NULL REFERENCES instruments(id) ON DELETE CASCADE,
    trading_day DATE NOT NULL,
    reference_price NUMERIC(18,6) NOT NULL CHECK (reference_price > 0),
    first_observed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (instrument_id, trading_day)
);

CREATE INDEX idx_daily_market_baselines_day ON daily_market_baselines(trading_day);
