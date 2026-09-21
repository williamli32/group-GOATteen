-- Limit the client-facing trading universe to the curated
-- set of 50 mainstream equities.
--
-- All instruments may remain in the database for simulator
-- and historical purposes, but only these instruments are
-- exposed as tradable by the Trading Platform API.


UPDATE instruments
SET tradable = FALSE;


UPDATE instruments
SET tradable = TRUE
WHERE (symbol, exchange) IN (

    -- =========================
    -- UNITED STATES
    -- =========================

    ('AAPL', 'NASDAQ'),
    ('MSFT', 'NASDAQ'),
    ('GOOGL', 'NASDAQ'),
    ('AMZN', 'NASDAQ'),
    ('NVDA', 'NASDAQ'),
    ('META', 'NASDAQ'),
    ('TSLA', 'NASDAQ'),

    ('JPM', 'NYSE'),
    ('V', 'NYSE'),
    ('MA', 'NYSE'),

    ('WMT', 'NASDAQ'),

    ('KO', 'NYSE'),
    ('PEP', 'NASDAQ'),

    ('NFLX', 'NASDAQ'),
    ('DIS', 'NYSE'),

    ('AMD', 'NASDAQ'),
    ('INTC', 'NASDAQ'),

    ('CRM', 'NYSE'),
    ('ORCL', 'NYSE'),
    ('XOM', 'NYSE'),


    -- =========================
    -- INDIA
    -- =========================

    ('RELIANCE', 'NSE_IN'),
    ('TCS', 'NSE_IN'),
    ('HDFCBANK', 'NSE_IN'),
    ('INFY', 'NSE_IN'),
    ('ICICIBANK', 'NSE_IN'),
    ('ITC', 'NSE_IN'),
    ('SBIN', 'NSE_IN'),
    ('BHARTIARTL', 'NSE_IN'),
    ('KOTAKBANK', 'NSE_IN'),
    ('LT', 'NSE_IN'),
    ('AXISBANK', 'NSE_IN'),
    ('MARUTI', 'NSE_IN'),
    ('SUNPHARMA', 'NSE_IN'),
    ('HCLTECH', 'NSE_IN'),
    ('BAJFINANCE', 'NSE_IN'),


    -- =========================
    -- UNITED KINGDOM
    -- =========================

    ('SHEL', 'LSE'),
    ('AZN', 'LSE'),
    ('HSBA', 'LSE'),
    ('ULVR', 'LSE'),
    ('BP', 'LSE'),
    ('RIO', 'LSE'),
    ('GSK', 'LSE'),
    ('VOD', 'LSE'),
    ('BARC', 'LSE'),
    ('LLOY', 'LSE'),


    -- =========================
    -- INTERNATIONAL
    -- =========================

    ('SAP', 'XETRA'),
    ('NOVN', 'SIX'),
    ('SHOP', 'TSX'),
    ('BHP', 'ASX'),
    ('RY', 'TSX')
);