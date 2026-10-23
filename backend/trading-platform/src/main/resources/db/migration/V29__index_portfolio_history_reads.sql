-- Account-scoped as-of lookups avoid scanning unrelated trading histories.
CREATE INDEX IF NOT EXISTS idx_cash_transactions_account_time
    ON cash_transactions(account_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_position_history_account_instrument_time
    ON position_history(account_id, instrument_id, recorded_at DESC, id DESC);
