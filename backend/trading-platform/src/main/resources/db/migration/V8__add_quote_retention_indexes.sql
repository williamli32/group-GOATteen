-- Support efficient cleanup of old simulated market quotes.

CREATE INDEX IF NOT EXISTS idx_market_quotes_quoted_at
    ON market_quotes(quoted_at);

CREATE INDEX IF NOT EXISTS idx_fills_quote_id
    ON fills(quote_id);