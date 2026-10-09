CREATE SCHEMA IF NOT EXISTS reporting;

CREATE TABLE reporting.trade_facts(
    trade_fact_id BIGSERIAL PRIMARY KEY,

    -- Transactional Foreign keys
    fill_id BIGINT NOT NULL UNIQUE,
    order_id BIGINT NOT NULL,

    -- Time dimensions
    order_submitted_at TIMESTAMP NOT NULL,
    order_accepted_at TIMESTAMP,
    order_filled_at TIMESTAMP NOT NULL,

    -- Account dimensions
    account_id BIGINT NOT NULL,
    client_id BIGINT NOT NULL,
    client_first_name VARCHAR(255),
    client_last_name VARCHAR(255),

    -- Instrument dimensions
    instrument_id BIGINT NOT NULL,
    instrument_symbol VARCHAR(20),
    instrument_class VARCHAR(20),
    instrument_name VARCHAR(255),
    currency VARCHAR(10),

    -- Trade execution details
    side VARCHAR(4) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    order_quantity NUMERIC(18,6) NOT NULL,
    execution_price NUMERIC(18,6) NOT NULL,
    execution_quantity NUMERIC(18,6) NOT NULL,
    trade_value NUMERIC(18,2) NOT NULL,

    -- Order status at time of fact creation
    order_status VARCHAR(20) NOT NULL,

    -- Market context
    bid_price NUMERIC(18,6),
    ask_price NUMERIC(18,6),
    quote_timestamp TIMESTAMP,

    -- Audit & lineage
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_trade_facts_source_fill
        FOREIGN KEY(fill_id)
        REFERENCES fills(id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_trade_facts_order_filled_at 
    ON reporting.trade_facts(order_filled_at DESC);

CREATE INDEX idx_trade_facts_client_id_filled_at 
    ON reporting.trade_facts(client_id, order_filled_at DESC);

CREATE INDEX idx_trade_facts_account_id_filled_at 
    ON reporting.trade_facts(account_id, order_filled_at DESC);

CREATE INDEX idx_trade_facts_instrument_id_filled_at 
    ON reporting.trade_facts(instrument_id, order_filled_at DESC);

CREATE INDEX idx_trade_facts_side_filled_at 
    ON reporting.trade_facts(side, order_filled_at DESC);

CREATE INDEX idx_trade_facts_fill_id 
    ON reporting.trade_facts(fill_id);

-- Tracking table: which trades have been synced (for idempotency on replay)
CREATE TABLE reporting.sync_tracking (
    sync_id BIGSERIAL PRIMARY KEY,
    
    fill_id BIGINT NOT NULL UNIQUE,
    event_timestamp TIMESTAMP NOT NULL,
    sync_completed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sync_status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS'
        CHECK (sync_status IN ('SUCCESS', 'FAILED', 'PENDING')),
    error_message TEXT,
    
    CONSTRAINT fk_sync_tracking_fill 
        FOREIGN KEY(fill_id) 
        REFERENCES fills(id) 
        ON DELETE RESTRICT
);

CREATE INDEX idx_sync_tracking_fill_id 
    ON reporting.sync_tracking(fill_id);

CREATE INDEX idx_sync_tracking_sync_status 
    ON reporting.sync_tracking(sync_status);