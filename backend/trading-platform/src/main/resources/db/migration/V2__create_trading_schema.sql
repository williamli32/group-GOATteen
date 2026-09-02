-- Instruments (BR-12: equities UK/US/India, FX, crypto)
CREATE TABLE instruments (

    id BIGSERIAL PRIMARY KEY,

    symbol VARCHAR(20) NOT NULL UNIQUE,

    name VARCHAR(255) NOT NULL,

    instrument_class VARCHAR(20) NOT NULL
        CHECK (instrument_class IN ('EQUITY_UK', 'EQUITY_US', 'EQUITY_IN', 'FX', 'CRYPTO')),

    currency VARCHAR(10) NOT NULL,

    tradable BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);


-- Current market quote per instrument (BR-08, BR-12, BR-13)
CREATE TABLE market_quotes (

    id BIGSERIAL PRIMARY KEY,

    instrument_id BIGINT NOT NULL UNIQUE,

    bid_price NUMERIC(18,6) NOT NULL,

    ask_price NUMERIC(18,6) NOT NULL,

    last_price NUMERIC(18,6) NOT NULL,

    quoted_at TIMESTAMP NOT NULL,


    CONSTRAINT fk_market_quotes_instrument
        FOREIGN KEY(instrument_id)
        REFERENCES instruments(id)
);


-- Orders (BR-04 to BR-09): recorded as a firm commitment before execution
CREATE TABLE orders (

    id BIGSERIAL PRIMARY KEY,

    account_id BIGINT NOT NULL,

    instrument_id BIGINT NOT NULL,

    side VARCHAR(4) NOT NULL
        CHECK (side IN ('BUY', 'SELL')),

    quantity NUMERIC(18,6) NOT NULL,

    status VARCHAR(20) NOT NULL
        CHECK (status IN ('SUBMITTED', 'ACCEPTED', 'REJECTED', 'FILLED')),

    rejection_reason VARCHAR(255),

    submitted_at TIMESTAMP NOT NULL,

    completed_at TIMESTAMP,


    CONSTRAINT fk_orders_account
        FOREIGN KEY(account_id)
        REFERENCES accounts(id),

    CONSTRAINT fk_orders_instrument
        FOREIGN KEY(instrument_id)
        REFERENCES instruments(id)
);

CREATE INDEX idx_orders_account_id ON orders(account_id);
CREATE INDEX idx_orders_submitted_at ON orders(submitted_at);


-- Order status history (BR-07, BR-15): full lifecycle, append-only audit trail
CREATE TABLE order_status_history (

    id BIGSERIAL PRIMARY KEY,

    order_id BIGINT NOT NULL,

    status VARCHAR(20) NOT NULL
        CHECK (status IN ('SUBMITTED', 'ACCEPTED', 'REJECTED', 'FILLED')),

    changed_at TIMESTAMP NOT NULL,

    note VARCHAR(255),


    CONSTRAINT fk_order_status_history_order
        FOREIGN KEY(order_id)
        REFERENCES orders(id)
);

CREATE INDEX idx_order_status_history_order_id ON order_status_history(order_id);


-- Fills (BR-08, BR-09): the outcome of an order being executed at a price
CREATE TABLE fills (

    id BIGSERIAL PRIMARY KEY,

    order_id BIGINT NOT NULL UNIQUE,

    quote_id BIGINT,

    fill_price NUMERIC(18,6) NOT NULL,

    fill_quantity NUMERIC(18,6) NOT NULL,

    executed_at TIMESTAMP NOT NULL,


    CONSTRAINT fk_fills_order
        FOREIGN KEY(order_id)
        REFERENCES orders(id),

    CONSTRAINT fk_fills_quote
        FOREIGN KEY(quote_id)
        REFERENCES market_quotes(id)
);


-- Positions (BR-10): a client's current holding in a given instrument
CREATE TABLE positions (

    id BIGSERIAL PRIMARY KEY,

    account_id BIGINT NOT NULL,

    instrument_id BIGINT NOT NULL,

    quantity NUMERIC(18,6) NOT NULL DEFAULT 0,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,


    CONSTRAINT fk_positions_account
        FOREIGN KEY(account_id)
        REFERENCES accounts(id),

    CONSTRAINT fk_positions_instrument
        FOREIGN KEY(instrument_id)
        REFERENCES instruments(id),

    CONSTRAINT uq_positions_account_instrument
        UNIQUE(account_id, instrument_id)
);


-- Cash transactions (BR-09, BR-14): append-only ledger backing the account cash balance
CREATE TABLE cash_transactions (

    id BIGSERIAL PRIMARY KEY,

    account_id BIGINT NOT NULL,

    fill_id BIGINT,

    amount NUMERIC(18,2) NOT NULL,

    balance_after NUMERIC(18,2) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    description VARCHAR(255),


    CONSTRAINT fk_cash_transactions_account
        FOREIGN KEY(account_id)
        REFERENCES accounts(id),

    CONSTRAINT fk_cash_transactions_fill
        FOREIGN KEY(fill_id)
        REFERENCES fills(id)
);

CREATE INDEX idx_cash_transactions_account_id ON cash_transactions(account_id);
