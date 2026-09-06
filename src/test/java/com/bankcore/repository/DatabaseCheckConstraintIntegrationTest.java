package com.bankcore.repository;

import com.bankcore.support.MySqlContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ImportTestcontainers(MySqlContainerSupport.class)
class DatabaseCheckConstraintIntegrationTest {

    private static final AtomicLong SEQUENCE = new AtomicLong(120_000);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void database_shouldRejectEnumValuesOutsideDomainEnums() {
        long customerId = insertCustomer();
        long accountId = insertAccount(customerId);
        long transactionId = insertFinancialTransaction();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO account (customer_id, account_number, balance, status, version)
                VALUES (?, ?, 0, 'UNKNOWN', 0)
                """, customerId, "120-999-" + SEQUENCE.incrementAndGet()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_account_status_valid");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO financial_transaction (transaction_key, type, amount)
                VALUES (?, 'UNKNOWN', 1000)
                """, UUID.randomUUID().toString()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_financial_transaction_type_valid");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO account_journal_entry
                    (transaction_id, entry_no, account_id, movement_type, amount, balance_after)
                VALUES (?, 1, ?, 'UNKNOWN', 1000, 1000)
                """, transactionId, accountId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_account_journal_movement_type_valid");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO idempotency_record
                    (caller_scope, operation, idempotency_key_digest, request_fingerprint, status)
                VALUES ('schema-test', 'UNKNOWN', UNHEX(REPEAT('a', 64)), REPEAT('b', 64), 'PROCESSING')
                """))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_idempotency_operation_valid");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO idempotency_record
                    (caller_scope, operation, idempotency_key_digest, request_fingerprint, status)
                VALUES ('schema-test', 'INTERNAL_TRANSFER', UNHEX(REPEAT('a', 64)), REPEAT('b', 64), 'UNKNOWN')
                """))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_idempotency_status_valid");
    }

    private long insertCustomer() {
        jdbcTemplate.update("INSERT INTO customer (name) VALUES (?)", "Schema Constraint Customer");
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertAccount(long customerId) {
        String accountNumber = "120-000-" + SEQUENCE.incrementAndGet();
        jdbcTemplate.update("""
                INSERT INTO account (customer_id, account_number, balance, status, version)
                VALUES (?, ?, 0, 'ACTIVE', 0)
                """, customerId, accountNumber);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertFinancialTransaction() {
        jdbcTemplate.update("""
                INSERT INTO financial_transaction (transaction_key, type, amount)
                VALUES (?, 'CONTROLLED_SEED', 1000)
                """, UUID.randomUUID().toString());
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
