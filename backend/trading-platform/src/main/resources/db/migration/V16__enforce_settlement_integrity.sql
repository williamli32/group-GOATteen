-- Fill must have a quote
ALTER TABLE fills
    ALTER COLUMN quote_id SET NOT NULL;

-- Only one cash settlement per Fill
CREATE UNIQUE INDEX uq_cash_transactions_fill_id
    ON cash_transactions(fill_id)
    WHERE fill_id IS NOT NULL;

-- Position history should use same 6-decimal precision
ALTER TABLE position_history
    ALTER COLUMN quantity_before TYPE NUMERIC(19,6),
    ALTER COLUMN quantity_after TYPE NUMERIC(19,6),
    ALTER COLUMN quantity_change TYPE NUMERIC(19,6);

-- Keep the account balance from before the transaction
ALTER TABLE cash_transactions
    ADD COLUMN balance_before NUMERIC(18,2);

UPDATE cash_transactions
SET balance_before = balance_after - amount
WHERE balance_before IS NULL;

ALTER TABLE cash_transactions
    ALTER COLUMN balance_before SET NOT NULL;