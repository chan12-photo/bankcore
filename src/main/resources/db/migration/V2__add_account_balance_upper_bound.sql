ALTER TABLE account
    ADD CONSTRAINT chk_account_balance_upper_bound CHECK (balance <= 100000000000000);
