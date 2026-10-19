-- Retire USDJPY from new trading without changing its identity, currency,
-- quotes, orders, fills or audit history (BR-14 / BR-15).
UPDATE instruments
SET tradable = FALSE
WHERE symbol = 'USDJPY' AND exchange = 'FOREX';

-- INRUSD is quoted in USD: the price is USD per one Indian rupee.
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
    'INRUSD',
    'Indian Rupee / US Dollar',
    'FOREX',
    'FOREX',
    'GLOBAL',
    'USD',
    TRUE
)
ON CONFLICT (symbol, exchange)
DO UPDATE SET
    name = EXCLUDED.name,
    instrument_class = EXCLUDED.instrument_class,
    country_code = EXCLUDED.country_code,
    currency = EXCLUDED.currency,
    tradable = TRUE;
