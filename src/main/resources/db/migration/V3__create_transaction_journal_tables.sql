CREATE TABLE financial_transaction (
    id BIGINT NOT NULL AUTO_INCREMENT,
    transaction_key CHAR(36) NOT NULL,
    type VARCHAR(30) NOT NULL,
    amount BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_financial_transaction_key (transaction_key),
    CONSTRAINT chk_financial_transaction_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_financial_transaction_amount_upper_bound CHECK (amount <= 1000000000000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE account_journal_entry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    transaction_id BIGINT NOT NULL,
    entry_no INT NOT NULL,
    account_id BIGINT NOT NULL,
    movement_type VARCHAR(30) NOT NULL,
    amount BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_journal_transaction_entry_no (transaction_id, entry_no),
    CONSTRAINT fk_account_journal_transaction
        FOREIGN KEY (transaction_id) REFERENCES financial_transaction (id),
    CONSTRAINT fk_account_journal_account
        FOREIGN KEY (account_id) REFERENCES account (id),
    CONSTRAINT chk_account_journal_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_account_journal_amount_upper_bound CHECK (amount <= 1000000000000),
    CONSTRAINT chk_account_journal_balance_non_negative CHECK (balance_after >= 0),
    CONSTRAINT chk_account_journal_balance_upper_bound CHECK (balance_after <= 100000000000000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
