-- Replace the five international equity slots in the curated
-- market universe with three FX pairs and two cryptocurrencies.
--
-- Final client-facing universe:
-- 45 equities + 3 FX + 2 crypto = 50 instruments.


-- Remove the previous international equity selections.

UPDATE instruments
SET tradable = FALSE
WHERE (symbol, exchange) IN (
    ('SAP', 'XETRA'),
    ('NOVN', 'SIX'),
    ('SHOP', 'TSX'),
    ('BHP', 'ASX'),
    ('RY', 'TSX')
);


-- Ensure the canonical USD/JPY instrument exists.

INSERT INTO instruments (
    symbol,
    name,
    instrument_class,
    exchange,
    country_code,
    currency,
    tradable
)
VALUES (
    'USDJPY',
    'US Dollar / Japanese Yen',
    'FOREX',
    'FOREX',
    'GLOBAL',
    'JPY',
    TRUE
)
ON CONFLICT (symbol, exchange)
DO UPDATE SET
    name = EXCLUDED.name,
    instrument_class = EXCLUDED.instrument_class,
    country_code = EXCLUDED.country_code,
    currency = EXCLUDED.currency,
    tradable = TRUE;


-- Enable the required FX instruments.

UPDATE instruments
SET tradable = TRUE
WHERE (symbol, exchange) IN (
    ('GBPUSD', 'FOREX'),
    ('EURUSD', 'FOREX'),
    ('USDJPY', 'FOREX')
);


-- Enable the required crypto instruments.

UPDATE instruments
SET tradable = TRUE
WHERE (symbol, exchange) IN (
    ('BTCUSD', 'CRYPTO'),
    ('ETHUSD', 'CRYPTO')
);