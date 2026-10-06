-- Sprint 4:
-- An account must never contain a negative cash balance.

ALTER TABLE accounts
    ADD CONSTRAINT chk_accounts_cash_balance_non_negative
    CHECK (cash_balance >= 0);