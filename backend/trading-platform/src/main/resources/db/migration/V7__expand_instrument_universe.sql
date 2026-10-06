-- Allow the trading platform to represent the full simulated market universe.
-- Instrument identity is now (symbol, exchange), because the same symbol can
-- legitimately exist on multiple exchanges.

ALTER TABLE instruments
    DROP CONSTRAINT IF EXISTS instruments_instrument_class_check;

ALTER TABLE instruments
    DROP CONSTRAINT IF EXISTS instruments_symbol_key;


-- Add market identity fields.

ALTER TABLE instruments
    ADD COLUMN exchange VARCHAR(30);

ALTER TABLE instruments
    ADD COLUMN country_code VARCHAR(20);


-- Backfill the original Sprint 3 instruments so existing rows remain valid.

UPDATE instruments
SET exchange = CASE symbol
    WHEN 'AAPL' THEN 'NASDAQ'
    WHEN 'MSFT' THEN 'NASDAQ'
    WHEN 'VOD.L' THEN 'LSE'
    WHEN 'HSBA.L' THEN 'LSE'
    WHEN 'RELIANCE.NS' THEN 'NSE'
    WHEN 'TCS.NS' THEN 'NSE'
    WHEN 'GBPUSD' THEN 'FOREX'
    WHEN 'EURUSD' THEN 'FOREX'
    WHEN 'BTCUSD' THEN 'CRYPTO'
    WHEN 'ETHUSD' THEN 'CRYPTO'
    ELSE 'UNKNOWN'
END;

UPDATE instruments
SET country_code = CASE symbol
    WHEN 'AAPL' THEN 'US'
    WHEN 'MSFT' THEN 'US'
    WHEN 'VOD.L' THEN 'GB'
    WHEN 'HSBA.L' THEN 'GB'
    WHEN 'RELIANCE.NS' THEN 'IN'
    WHEN 'TCS.NS' THEN 'IN'
    WHEN 'GBPUSD' THEN 'GLOBAL'
    WHEN 'EURUSD' THEN 'GLOBAL'
    WHEN 'BTCUSD' THEN 'GLOBAL'
    WHEN 'ETHUSD' THEN 'GLOBAL'
    ELSE 'GLOBAL'
END;


-- Normalize the old region-specific equity categories.

UPDATE instruments
SET instrument_class = CASE
    WHEN instrument_class IN (
        'EQUITY_UK',
        'EQUITY_US',
        'EQUITY_IN'
    ) THEN 'EQUITY'
    WHEN instrument_class = 'FX' THEN 'FOREX'
    ELSE instrument_class
END;


ALTER TABLE instruments
    ALTER COLUMN exchange SET NOT NULL;

ALTER TABLE instruments
    ALTER COLUMN country_code SET NOT NULL;


ALTER TABLE instruments
    ADD CONSTRAINT instruments_instrument_class_check
    CHECK (
        instrument_class IN (
            'EQUITY',
            'FOREX',
            'CRYPTO',
            'COMMODITY'
        )
    );


ALTER TABLE instruments
    ADD CONSTRAINT uq_instruments_symbol_exchange
    UNIQUE (symbol, exchange);


CREATE INDEX idx_instruments_symbol_exchange
    ON instruments(symbol, exchange);