
-- Goal 2: Order-level reporting dataset

CREATE TABLE reporting.order_facts (
    order_id BIGINT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    client_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,
    instrument_symbol VARCHAR(20),
    currency VARCHAR(10),

    side VARCHAR(4) NOT NULL
        CHECK (side IN ('BUY', 'SELL')),

    order_quantity NUMERIC(18,6) NOT NULL,

    final_status VARCHAR(20) NOT NULL
        CHECK (final_status IN (
            'SUBMITTED', 'ACCEPTED', 'FILLED', 'REJECTED'
        )),

    submitted_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    rejection_reason TEXT,

    data_synced_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_order_facts_submitted_at
    ON reporting.order_facts(submitted_at);

CREATE INDEX idx_order_facts_client_date
    ON reporting.order_facts(client_id, submitted_at);

CREATE INDEX idx_order_facts_instrument_date
    ON reporting.order_facts(instrument_id, submitted_at);

CREATE INDEX idx_order_facts_status_date
    ON reporting.order_facts(final_status, submitted_at);
