-- Sprint 5: persistent idempotency

ALTER TABLE fills
    ADD COLUMN idempotency_key VARCHAR(255)
    UNIQUE NULL;

CREATE INDEX idx_fills_idempotency_key
    ON fills(idempotency_key);