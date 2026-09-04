package com.bankcore.service;

import com.bankcore.domain.JournalMovementType;
import com.bankcore.exception.AccountNotFoundException;
import com.bankcore.exception.InvalidPageRequestException;
import com.bankcore.repository.AccountRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;

@Service
public class AccountJournalQueryService {

    private static final int MAX_LIMIT = 100;

    private final AccountRepository accountRepository;
    private final JdbcTemplate jdbcTemplate;

    public AccountJournalQueryService(AccountRepository accountRepository, JdbcTemplate jdbcTemplate) {
        this.accountRepository = accountRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<AccountJournalEntryResult> findRecentEntries(Long accountId, Long beforeEntryId, int limit) {
        validateAccountExists(accountId);
        validateLimit(limit);

        if (beforeEntryId == null) {
            return queryFirstPage(accountId, limit);
        }
        return queryNextPage(accountId, beforeEntryId, limit);
    }

    private List<AccountJournalEntryResult> queryFirstPage(Long accountId, int limit) {
        return jdbcTemplate.query("""
                SELECT id, transaction_id, entry_no, movement_type, amount, balance_after, created_at
                FROM account_journal_entry
                WHERE account_id = ?
                ORDER BY id DESC
                LIMIT ?
                """, (resultSet, rowNumber) -> new AccountJournalEntryResult(
                resultSet.getLong("id"),
                resultSet.getLong("transaction_id"),
                resultSet.getInt("entry_no"),
                JournalMovementType.valueOf(resultSet.getString("movement_type")),
                resultSet.getLong("amount"),
                resultSet.getLong("balance_after"),
                resultSet.getObject("created_at", Timestamp.class).toInstant()
        ), accountId, limit);
    }

    private List<AccountJournalEntryResult> queryNextPage(Long accountId, Long beforeEntryId, int limit) {
        return jdbcTemplate.query("""
                SELECT id, transaction_id, entry_no, movement_type, amount, balance_after, created_at
                FROM account_journal_entry
                WHERE account_id = ?
                  AND id < ?
                ORDER BY id DESC
                LIMIT ?
                """, (resultSet, rowNumber) -> new AccountJournalEntryResult(
                resultSet.getLong("id"),
                resultSet.getLong("transaction_id"),
                resultSet.getInt("entry_no"),
                JournalMovementType.valueOf(resultSet.getString("movement_type")),
                resultSet.getLong("amount"),
                resultSet.getLong("balance_after"),
                resultSet.getObject("created_at", Timestamp.class).toInstant()
        ), accountId, beforeEntryId, limit);
    }

    private void validateAccountExists(Long accountId) {
        if (accountId == null || !accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
    }

    private static void validateLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidPageRequestException();
        }
    }
}
