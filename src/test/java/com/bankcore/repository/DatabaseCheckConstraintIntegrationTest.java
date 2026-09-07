package com.bankcore.repository;

import com.bankcore.support.MySqlContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
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

    @Test
    void database_shouldRejectEnumValuesWithDifferentCaseOrAccents() {
        long customerId = insertCustomer();
        long accountId = insertAccount(customerId);
        long transactionId = insertFinancialTransaction();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO account (customer_id, account_number, balance, status, version)
                VALUES (?, ?, 0, 'active', 0)
                """, customerId, "120-999-" + SEQUENCE.incrementAndGet()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_account_status_valid_exact");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO account (customer_id, account_number, balance, status, version)
                VALUES (?, ?, 0, 'ACTÍVE', 0)
                """, customerId, "120-999-" + SEQUENCE.incrementAndGet()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_account_status_valid_exact");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO financial_transaction (transaction_key, type, amount)
                VALUES (?, 'internal_transfer', 1000)
                """, UUID.randomUUID().toString()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_financial_transaction_type_valid_exact");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO account_journal_entry
                    (transaction_id, entry_no, account_id, movement_type, amount, balance_after)
                VALUES (?, 1, ?, 'balance_increase', 1000, 1000)
                """, transactionId, accountId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_account_journal_movement_type_valid_exact");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO idempotency_record
                    (caller_scope, operation, idempotency_key_digest, request_fingerprint, status)
                VALUES ('schema-test', 'internal_transfer', UNHEX(REPEAT('a', 64)), REPEAT('b', 64), 'PROCESSING')
                """))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_idempotency_operation_valid_exact");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO idempotency_record
                    (caller_scope, operation, idempotency_key_digest, request_fingerprint, status)
                VALUES ('schema-test', 'INTERNAL_TRANSFER', UNHEX(REPEAT('a', 64)), REPEAT('b', 64), 'completed')
                """))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_idempotency_status_valid_exact");
    }

    @Test
    void database_shouldRejectIdempotencyStatusAndResponseTransactionMismatch() {
        long transactionId = insertFinancialTransaction();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO idempotency_record
                    (caller_scope, operation, idempotency_key_digest, request_fingerprint, status)
                VALUES (?, 'INTERNAL_TRANSFER', UNHEX(REPEAT('a', 64)), REPEAT('b', 64), 'COMPLETED')
                """, "schema-test-" + SEQUENCE.incrementAndGet()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_idempotency_status_response_consistent");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO idempotency_record
                    (caller_scope, operation, idempotency_key_digest, request_fingerprint, status, response_transaction_id)
                VALUES (?, 'INTERNAL_TRANSFER', UNHEX(REPEAT('c', 64)), REPEAT('d', 64), 'PROCESSING', ?)
                """, "schema-test-" + SEQUENCE.incrementAndGet(), transactionId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_idempotency_status_response_consistent");
    }

    private long insertCustomer() {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement =
                    connection.prepareStatement("INSERT INTO customer (name) VALUES (?)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, "Schema Constraint Customer");
            return statement;
        }, keyHolder);
        return generatedId(keyHolder);
    }

    private long insertAccount(long customerId) {
        String accountNumber = "120-000-" + SEQUENCE.incrementAndGet();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO account (customer_id, account_number, balance, status, version)
                    VALUES (?, ?, 0, 'ACTIVE', 0)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, customerId);
            statement.setString(2, accountNumber);
            return statement;
        }, keyHolder);
        return generatedId(keyHolder);
    }

    private long insertFinancialTransaction() {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO financial_transaction (transaction_key, type, amount)
                    VALUES (?, 'CONTROLLED_SEED', 1000)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, UUID.randomUUID().toString());
            return statement;
        }, keyHolder);
        return generatedId(keyHolder);
    }

    private static long generatedId(KeyHolder keyHolder) {
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert did not return a generated key.");
        }
        return key.longValue();
    }
}
