CREATE TABLE watchlist_entries (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    instrument_id BIGINT NOT NULL REFERENCES instruments(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, instrument_id)
);

CREATE INDEX idx_watchlist_instrument ON watchlist_entries(instrument_id);
