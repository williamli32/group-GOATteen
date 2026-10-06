-- Sprint 3 development/reference market data.
-- These are deterministic seed values, not live market prices.

INSERT INTO instruments (
    symbol,
    name,
    instrument_class,
    currency,
    tradable
)
VALUES
    ('AAPL',        'Apple Inc.',               'EQUITY_US', 'USD', TRUE),
    ('MSFT',        'Microsoft Corporation',    'EQUITY_US', 'USD', TRUE),

    ('VOD.L',       'Vodafone Group Plc',       'EQUITY_UK', 'GBP', TRUE),
    ('HSBA.L',      'HSBC Holdings Plc',        'EQUITY_UK', 'GBP', TRUE),

    ('RELIANCE.NS', 'Reliance Industries Ltd',  'EQUITY_IN', 'INR', TRUE),
    ('TCS.NS',      'Tata Consultancy Services','EQUITY_IN', 'INR', TRUE),

    ('GBPUSD',      'British Pound / US Dollar','FX',        'USD', TRUE),
    ('EURUSD',      'Euro / US Dollar',         'FX',        'USD', TRUE),

    ('BTCUSD',      'Bitcoin / US Dollar',      'CRYPTO',    'USD', TRUE),
    ('ETHUSD',      'Ethereum / US Dollar',     'CRYPTO',    'USD', TRUE)

ON CONFLICT (symbol) DO NOTHING;


INSERT INTO market_quotes (
    instrument_id,
    bid_price,
    ask_price,
    last_price,
    quoted_at
)
SELECT
    i.id,
    seed.bid_price,
    seed.ask_price,
    seed.last_price,
    CURRENT_TIMESTAMP
FROM (
    VALUES
        ('AAPL',        200.000000, 200.100000, 200.050000),
        ('MSFT',        400.000000, 400.200000, 400.100000),

        ('VOD.L',        75.000000,  75.100000,  75.050000),
        ('HSBA.L',      700.000000, 700.500000, 700.250000),

        ('RELIANCE.NS', 3000.000000, 3002.000000, 3001.000000),
        ('TCS.NS',      4200.000000, 4203.000000, 4201.500000),

        ('GBPUSD',         1.310000,    1.311000,    1.310500),
        ('EURUSD',         1.180000,    1.181000,    1.180500),

        ('BTCUSD',    60000.000000, 60020.000000, 60010.000000),
        ('ETHUSD',     3000.000000,  3002.000000,  3001.000000)
) AS seed(
    symbol,
    bid_price,
    ask_price,
    last_price
)
JOIN instruments i
    ON i.symbol = seed.symbol;