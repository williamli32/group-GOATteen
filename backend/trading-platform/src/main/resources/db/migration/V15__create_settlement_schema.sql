-- V15__create_settlement_schema.sql
-- PostgreSQL Migration: Create settlement tables for Goal 3 failure recovery
-- Purpose: Track settlement process and prevent duplicate execution
-- Database: PostgreSQL 17+

-- ============================================================================
-- TABLE: settlements
-- Purpose: Track the settlement of executed orders
-- Lifecycle: INITIATED → CASH_DEBITED → POSITION_CREDITED → COMPLETED
-- ============================================================================

CREATE TABLE settlements (
    id BIGSERIAL PRIMARY KEY,
    
    -- Foreign Keys
    order_id BIGINT NOT NULL UNIQUE,
    account_id BIGINT NOT NULL,
    
    -- Settlement Details
    quantity NUMERIC(19, 8) NOT NULL,
    price NUMERIC(19, 8) NOT NULL,
    total_amount NUMERIC(19, 8) NOT NULL,
    
    -- Status: INITIATED, CASH_DEBITED, POSITION_CREDITED, COMPLETED, FAILED
    status VARCHAR(50) NOT NULL,
    
    -- Timestamps
    initiated_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT fk_settlements_order FOREIGN KEY (order_id) 
        REFERENCES orders(id),
    CONSTRAINT fk_settlements_account FOREIGN KEY (account_id) 
        REFERENCES accounts(id)
);

-- Create indexes for settlements table
CREATE INDEX idx_order_settlement ON settlements(order_id);
CREATE INDEX idx_status_created ON settlements(status, created_at);
CREATE INDEX idx_account_created ON settlements(account_id, created_at);

-- ============================================================================
-- TABLE: settlement_idempotency_keys
-- Purpose: Prevent duplicate settlement execution across app restarts
-- Design: Store the key and result for replay if request is retried
-- ============================================================================

CREATE TABLE settlement_idempotency_keys (
    id BIGSERIAL PRIMARY KEY,
    
    -- Foreign Keys
    order_id BIGINT NOT NULL,
    
    -- Idempotency Key: Unique identifier for each settlement attempt
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    
    -- Result Status: SUCCESS or FAILURE
    status VARCHAR(20) NOT NULL,
    
    -- Result JSON: Complete result stored for replay
    -- For SUCCESS: { "settlementId": 999, "status": "COMPLETED", ... }
    -- For FAILURE: { "error": "...", "errorCode": "...", ... }
    result_json TEXT,
    
    -- Timestamp
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT fk_idempotency_order FOREIGN KEY (order_id) 
        REFERENCES orders(id),
    CONSTRAINT uk_idempotency_key UNIQUE (idempotency_key)
);

-- Create indexes for settlement_idempotency_keys table
CREATE INDEX idx_order_key ON settlement_idempotency_keys(order_id, idempotency_key);
CREATE INDEX idx_key_created ON settlement_idempotency_keys(idempotency_key, created_at);
CREATE INDEX idx_order_created ON settlement_idempotency_keys(order_id, created_at);