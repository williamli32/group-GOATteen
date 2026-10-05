-- Sprint 5: Trade Audit Trail
-- Create position_history table to enable complete trade reconstruction
-- 
-- Requirement: Make every completed or rejected trade fully reconstructable from persisted data
-- This table tracks every position change linked to a fill, enabling full audit trail reconstruction

CREATE TABLE position_history (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,
    fill_id BIGINT NOT NULL UNIQUE,
    quantity_before NUMERIC(19, 4) NOT NULL,
    quantity_after NUMERIC(19, 4) NOT NULL,
    quantity_change NUMERIC(19, 4) NOT NULL,
    recorded_at TIMESTAMP NOT NULL,
    
    CONSTRAINT fk_position_history_account FOREIGN KEY (account_id) 
        REFERENCES accounts(id) ON DELETE CASCADE,
    CONSTRAINT fk_position_history_instrument FOREIGN KEY (instrument_id) 
        REFERENCES instruments(id) ON DELETE CASCADE,
    CONSTRAINT fk_position_history_fill FOREIGN KEY (fill_id) 
        REFERENCES fills(id) ON DELETE CASCADE
);

-- Index for efficient queries by fill_id (primary access pattern)
CREATE INDEX idx_position_history_fill_id ON position_history(fill_id);

-- Index for efficient queries by account_id (account audit trails)
CREATE INDEX idx_position_history_account_id ON position_history(account_id);

-- Index for efficient queries by instrument_id
CREATE INDEX idx_position_history_instrument_id ON position_history(instrument_id);

-- Index for time-based queries
CREATE INDEX idx_position_history_recorded_at ON position_history(recorded_at DESC);