ALTER TABLE cash_transactions ADD COLUMN funding_key UUID;

CREATE UNIQUE INDEX uq_cash_transactions_funding_key
    ON cash_transactions(account_id, funding_key)
    WHERE funding_key IS NOT NULL;

ALTER TABLE cash_transactions ADD CONSTRAINT chk_funding_credit
    CHECK (funding_key IS NULL OR (fill_id IS NULL AND amount > 0));
