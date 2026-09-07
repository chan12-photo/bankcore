ALTER TABLE idempotency_record
    ADD CONSTRAINT chk_idempotency_status_response_consistent
        CHECK (
            CAST(status AS BINARY) NOT IN (
                CAST('PROCESSING' AS BINARY),
                CAST('COMPLETED' AS BINARY)
            )
            OR
            (CAST(status AS BINARY) = CAST('PROCESSING' AS BINARY) AND response_transaction_id IS NULL)
            OR
            (CAST(status AS BINARY) = CAST('COMPLETED' AS BINARY) AND response_transaction_id IS NOT NULL)
        );
