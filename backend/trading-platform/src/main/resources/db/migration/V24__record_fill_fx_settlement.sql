-- Record the actual FX decision used at execution, permanently attached to each fill.
ALTER TABLE fills
    ADD COLUMN fx_source_currency VARCHAR(3),
    ADD COLUMN fx_target_currency VARCHAR(3),
    ADD COLUMN fx_rate NUMERIC(24,12),
    ADD COLUMN fx_rate_date DATE,
    ADD COLUMN fx_provider VARCHAR(40),
    ADD COLUMN cash_settlement_amount NUMERIC(18,2);

-- Before this change the application only permitted same-currency orders.
-- Existing fills therefore legitimately used FX rate 1.0000. This migration
-- labels that historical rule explicitly rather than inventing market rates.
UPDATE fills f
SET fx_source_currency = i.currency,
    fx_target_currency = a.currency,
    fx_rate = 1.0,
    fx_rate_date = f.executed_at::date,
    fx_provider = 'LEGACY_SAME_CURRENCY',
    cash_settlement_amount = ROUND(f.fill_price * f.fill_quantity, 2)
FROM orders o
JOIN instruments i ON i.id = o.instrument_id
JOIN accounts a ON a.id = o.account_id
WHERE f.order_id = o.id;

ALTER TABLE fills
    ALTER COLUMN fx_source_currency SET NOT NULL,
    ALTER COLUMN fx_target_currency SET NOT NULL,
    ALTER COLUMN fx_rate SET NOT NULL,
    ALTER COLUMN fx_rate_date SET NOT NULL,
    ALTER COLUMN fx_provider SET NOT NULL,
    ALTER COLUMN cash_settlement_amount SET NOT NULL;

ALTER TABLE fills
    ADD CONSTRAINT ck_fills_fx_rate_positive CHECK (fx_rate > 0),
    ADD CONSTRAINT ck_fills_cash_settlement_nonnegative CHECK (cash_settlement_amount >= 0);
