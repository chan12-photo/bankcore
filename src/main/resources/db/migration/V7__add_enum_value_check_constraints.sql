ALTER TABLE account
    ADD CONSTRAINT chk_account_status_valid
        CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'));

ALTER TABLE financial_transaction
    ADD CONSTRAINT chk_financial_transaction_type_valid
        CHECK (type IN ('CONTROLLED_SEED', 'INTERNAL_TRANSFER'));

ALTER TABLE account_journal_entry
    ADD CONSTRAINT chk_account_journal_movement_type_valid
        CHECK (movement_type IN ('BALANCE_DECREASE', 'BALANCE_INCREASE'));

ALTER TABLE idempotency_record
    ADD CONSTRAINT chk_idempotency_operation_valid
        CHECK (operation IN ('INTERNAL_TRANSFER')),
    ADD CONSTRAINT chk_idempotency_status_valid
        CHECK (status IN ('PROCESSING', 'COMPLETED'));
