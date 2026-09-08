-- Optimistic locking for atomic cash/position updates (BR-09)
ALTER TABLE accounts
    ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;

ALTER TABLE positions
    ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;

-- Add CHECK constraint for data integrity
ALTER TABLE positions
    ADD CONSTRAINT chk_positions_quantity_non_negative
        CHECK (quantity >= 0);

-- Reporting indexes (BR-16): enable fast queries by period, instrument, client
CREATE INDEX idx_fills_executed_at ON fills(executed_at);
CREATE INDEX idx_orders_instrument_id ON orders(instrument_id);
CREATE INDEX idx_cash_transactions_created_at ON cash_transactions(created_at);
