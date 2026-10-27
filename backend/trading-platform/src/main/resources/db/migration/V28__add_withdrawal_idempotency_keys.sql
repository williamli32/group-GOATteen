ALTER TABLE cash_transactions
    ADD COLUMN withdrawal_key UUID;

CREATE UNIQUE INDEX uq_cash_transactions_withdrawal_key
    ON cash_transactions(account_id, withdrawal_key)
    WHERE withdrawal_key IS NOT NULL;

ALTER TABLE cash_transactions
    ADD CONSTRAINT chk_withdrawal_debit
    CHECK (withdrawal_key IS NULL OR (fill_id IS NULL AND amount < 0));
