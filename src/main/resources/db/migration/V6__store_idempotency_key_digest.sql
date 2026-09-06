ALTER TABLE idempotency_record
    ADD COLUMN idempotency_key_digest BINARY(32) NULL AFTER operation;

UPDATE idempotency_record
SET idempotency_key_digest = UNHEX(SHA2(CONCAT(
    'bankcore-idempotency-v1',
    '|callerScope=', caller_scope,
    '|operation=', operation,
    '|idempotencyKey=', idempotency_key
), 256));

ALTER TABLE idempotency_record
    MODIFY idempotency_key_digest BINARY(32) NOT NULL;

ALTER TABLE idempotency_record
    DROP INDEX uk_idempotency_scope_operation_key;

ALTER TABLE idempotency_record
    DROP COLUMN idempotency_key;

ALTER TABLE idempotency_record
    ADD UNIQUE KEY uk_idempotency_scope_operation_key_digest
        (caller_scope, operation, idempotency_key_digest);
