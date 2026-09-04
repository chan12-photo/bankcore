CREATE TABLE idempotency_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    caller_scope VARCHAR(100) NOT NULL,
    operation VARCHAR(50) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    response_transaction_id BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_idempotency_scope_operation_key (caller_scope, operation, idempotency_key),
    UNIQUE KEY uk_idempotency_response_transaction (response_transaction_id),
    CONSTRAINT fk_idempotency_response_transaction
        FOREIGN KEY (response_transaction_id) REFERENCES financial_transaction (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
