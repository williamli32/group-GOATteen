-- market_quotes was one-row-per-instrument, so every price tick overwrote the last
-- one, and a fill's quote_id could end up pointing at a price it was never priced against.
-- Make it an append-only ledger instead: drop the per-instrument uniqueness and index
-- for "latest quote per instrument" lookups.

ALTER TABLE market_quotes
    DROP CONSTRAINT market_quotes_instrument_id_key;

CREATE INDEX idx_market_quotes_instrument_id_quoted_at
    ON market_quotes(instrument_id, quoted_at DESC);
