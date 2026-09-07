ALTER TABLE account
    ADD CONSTRAINT chk_account_status_valid_exact
        CHECK (CAST(status AS BINARY) IN (
            CAST('ACTIVE' AS BINARY),
            CAST('FROZEN' AS BINARY),
            CAST('CLOSED' AS BINARY)
        ));

ALTER TABLE financial_transaction
    ADD CONSTRAINT chk_financial_transaction_type_valid_exact
        CHECK (CAST(type AS BINARY) IN (
            CAST('CONTROLLED_SEED' AS BINARY),
            CAST('INTERNAL_TRANSFER' AS BINARY)
        ));

ALTER TABLE account_journal_entry
    ADD CONSTRAINT chk_account_journal_movement_type_valid_exact
        CHECK (CAST(movement_type AS BINARY) IN (
            CAST('BALANCE_DECREASE' AS BINARY),
            CAST('BALANCE_INCREASE' AS BINARY)
        ));

ALTER TABLE idempotency_record
    ADD CONSTRAINT chk_idempotency_operation_valid_exact
        CHECK (CAST(operation AS BINARY) IN (
            CAST('INTERNAL_TRANSFER' AS BINARY)
        )),
    ADD CONSTRAINT chk_idempotency_status_valid_exact
        CHECK (CAST(status AS BINARY) IN (
            CAST('PROCESSING' AS BINARY),
            CAST('COMPLETED' AS BINARY)
        ));
