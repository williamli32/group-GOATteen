-- Add idempotency_key column to fills table
-- Enables persistent idempotent order execution tracking across application restarts
-- idempotency_key is UNIQUE to ensure only one fill per idempotency key

ALTER TABLE fills ADD COLUMN idempotency_key VARCHAR(255) UNIQUE NULL;

-- Index for fast lookup by idempotency key
CREATE INDEX idx_fills_idempotency_key ON fills(idempotency_key);
