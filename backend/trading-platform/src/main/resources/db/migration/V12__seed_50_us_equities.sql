-- Load 50 major US equity instruments for price generation and trading
-- Data sourced from market-data.csv for US market universe
-- These instruments span 7 major sectors

INSERT INTO instruments (symbol, name, instrument_class, exchange, country_code, currency, tradable)
VALUES
    -- Technology (15 stocks)
    ('AAPL', 'Apple Inc.', 'EQUITY', 'NASDAQ', 'US', 'USD', true),
    ('MSFT', 'Microsoft Corporation', 'EQUITY', 'NASDAQ', 'US', 'USD', true),
    ('GOOGL', 'Alphabet Inc.', 'EQUITY', 'NASDAQ', 'US', 'USD', true),
    ('AMZN', 'Amazon.com Inc.', 'EQUITY', 'NASDAQ', 'US', 'USD', true),
    ('NVDA', 'NVIDIA Corporation', 'EQUITY', 'NASDAQ', 'US', 'USD', true),
    ('TSLA', 'Tesla Inc.', 'EQUITY', 'NASDAQ', 'US', 'USD', true),
    ('META', 'Meta Platforms Inc.', 'EQUITY', 'NASDAQ', 'US', 'USD', true),
    ('INTEL', 'Intel Corporation', 'EQUITY', 'NASDAQ', 'US', 'USD', true),
    ('AMD', 'Advanced Micro Devices', 'EQUITY', 'NASDAQ', 'US', 'USD', true),
    ('CRM', 'Salesforce Inc.', 'EQUITY', 'NYSE', 'US', 'USD', true),
    ('IBM', 'IBM Corporation', 'EQUITY', 'NYSE', 'US', 'USD', true),
    ('ORCL', 'Oracle Corporation', 'EQUITY', 'NYSE', 'US', 'USD', true),
    ('ADBE', 'Adobe Inc.', 'EQUITY', 'NASDAQ', 'US', 'USD', true),
    ('NFLX', 'Netflix Inc.', 'EQUITY', 'NASDAQ', 'US', 'USD', true),
    ('AVGO', 'Broadcom Inc.', 'EQUITY', 'NASDAQ', 'US', 'USD', true),
    
    -- Finance & Banking (8 stocks)
    ('JPM', 'JPMorgan Chase', 'EQUITY', 'NYSE', 'US', 'USD', true),
    ('BAC', 'Bank of America', 'EQUITY', 'NYSE', 'US', 'USD', true),
    ('WFC', 'Wells Fargo', 'EQUITY', 'NYSE', 'US', 'USD', true),
    ('GS', 'Goldman Sachs', 'EQUITY', 'NYSE', 'US', 'USD', true),
    ('MS', 'Morgan Stanley', 'EQUITY', 'NYSE', 'US', 'USD', true),
    ('BLK', 'BlackRock Inc.', 'EQUITY', 'NYSE', 'US', 'USD', true),
    ('SCHW', 'Charles Schwab', 'EQUITY', 'NYSE', 'US', 'USD', true),
    ('COIN', 'Coinbase Global', 'EQUITY', 'NASDAQ', 'US', 'USD', true),
    
    -- Healthcare & Pharma (10 stocks)
    ('JNJ', 'Johnson & Johnson', 'EQUITY', 'NYSE', 'US', 'USD', true),
    ('UNH', 'UnitedHealth Group', 'EQUITY', 'NYSE', 'US', 'USD', true),
    ('PFE', 'Pfizer Inc.', 'EQUITY', 'NYSE', 'US', 'USD', true),
    ('ABBV', 'AbbVie Inc.', 'EQUITY', 'NYSE', 'US', 'USD', true),
    ('TMO', 'Thermo Fisher Scientific', 'EQUITY', 'NYSE', 'US', 'USD', true),
    ('CVS', 'CVS Health Corp.', 'EQUITY', 'NYSE', 'US', 'USD', true),
    ('LLY', 'Eli Lilly', 'EQUITY', 'NYSE', 'US', 'USD', true),
    ('AZN', 'AstraZeneca', 'EQUITY', 'NASDAQ', 'US', 'USD', true),
    ('MRK', 'Merck & Co.', 'EQUITY', 'NYSE', 'US', 'USD', true),
    ('GILD', 'Gilead Sciences', 'EQUITY', 'NASDAQ', 'US', 'USD', true),
    
    -- Consumer & Retail (8 stocks)
    ('KO', 'Coca-Cola', 'EQUITY', 'NYSE', 'US', 'USD', true),
    ('MCD', 'McDonald''s Corp.', 'EQUITY', 'NYSE', 'US', 'USD', true),
    ('SBUX', 'Starbucks Corp.', 'EQUITY', 'NASDAQ', 'US', 'USD', true),
    ('WMT', 'Walmart Inc.', 'EQUITY', 'NYSE', 'US', 'USD', true),
    ('TJX', 'TJX Companies', 'EQUITY', 'NYSE', 'US', 'USD', true),
    ('NKE', 'Nike Inc.', 'EQUITY', 'NYSE', 'US', 'USD', true),
    ('HD', 'The Home Depot', 'EQUITY', 'NYSE', 'US', 'USD', true),
    ('COST', 'Costco', 'EQUITY', 'NASDAQ', 'US', 'USD', true),
    
    -- Industrial & Energy (5 stocks)
    ('EXC', 'Exelon Corporation', 'EQUITY', 'NASDAQ', 'US', 'USD', true),
    ('NEE', 'NextEra Energy', 'EQUITY', 'NYSE', 'US', 'USD', true),
    ('DUK', 'Duke Energy', 'EQUITY', 'NYSE', 'US', 'USD', true),
    ('SO', 'Southern Company', 'EQUITY', 'NYSE', 'US', 'USD', true),
    ('CSX', 'CSX Corporation', 'EQUITY', 'NASDAQ', 'US', 'USD', true),
    
    -- Transportation (3 stocks)
    ('DAL', 'Delta Air Lines', 'EQUITY', 'NYSE', 'US', 'USD', true),
    ('UAL', 'United Airlines', 'EQUITY', 'NASDAQ', 'US', 'USD', true),
    ('BA', 'Boeing', 'EQUITY', 'NYSE', 'US', 'USD', true),
    
    -- Aerospace & Defense (2 stocks)
    ('LMT', 'Lockheed Martin', 'EQUITY', 'NYSE', 'US', 'USD', true),
    ('RTX', 'Raytheon Technologies', 'EQUITY', 'NYSE', 'US', 'USD', true)

ON CONFLICT (symbol, exchange) DO UPDATE
SET tradable = true;
