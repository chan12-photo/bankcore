package com.bankcore.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReconciliationService {

    private final JdbcTemplate jdbcTemplate;

    public ReconciliationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<AccountBalanceReconciliationResult> findAccountBalanceMismatches() {
        return jdbcTemplate.query("""
                SELECT account_id, stored_balance, journal_balance, stored_balance - journal_balance AS difference
                FROM (
                    SELECT
                        a.id AS account_id,
                        a.balance AS stored_balance,
                        COALESCE(SUM(
                            CASE aje.movement_type
                                WHEN 'BALANCE_INCREASE' THEN aje.amount
                                WHEN 'BALANCE_DECREASE' THEN -aje.amount
                                ELSE 0
                            END
                        ), 0) AS journal_balance
                    FROM account a
                    LEFT JOIN account_journal_entry aje ON aje.account_id = a.id
                    GROUP BY a.id, a.balance
                ) account_balance_reconciliation
                WHERE stored_balance <> journal_balance
                ORDER BY account_id
                """, (resultSet, rowNumber) -> new AccountBalanceReconciliationResult(
                resultSet.getLong("account_id"),
                resultSet.getLong("stored_balance"),
                resultSet.getLong("journal_balance"),
                resultSet.getLong("difference")
        ));
    }

    @Transactional(readOnly = true)
    public List<TransactionJournalReconciliationResult> findTransactionJournalMismatches() {
        return jdbcTemplate.query("""
                SELECT
                    ft.id AS transaction_id,
                    ft.type AS transaction_type,
                    ft.amount AS transaction_amount,
                    COUNT(aje.id) AS journal_entry_count,
                    SUM(CASE WHEN aje.entry_no = 1 THEN 1 ELSE 0 END) AS entry_no_one_count,
                    SUM(CASE WHEN aje.entry_no = 2 THEN 1 ELSE 0 END) AS entry_no_two_count,
                    SUM(CASE
                        WHEN aje.entry_no = 1 AND aje.movement_type = 'BALANCE_DECREASE' THEN 1
                        ELSE 0
                    END) AS entry_one_decrease_count,
                    SUM(CASE
                        WHEN aje.entry_no = 1 AND aje.movement_type = 'BALANCE_INCREASE' THEN 1
                        ELSE 0
                    END) AS entry_one_increase_count,
                    SUM(CASE
                        WHEN aje.entry_no = 2 AND aje.movement_type = 'BALANCE_INCREASE' THEN 1
                        ELSE 0
                    END) AS entry_two_increase_count,
                    SUM(CASE WHEN aje.movement_type = 'BALANCE_DECREASE' THEN 1 ELSE 0 END) AS decrease_entry_count,
                    SUM(CASE WHEN aje.movement_type = 'BALANCE_INCREASE' THEN 1 ELSE 0 END) AS increase_entry_count,
                    COUNT(DISTINCT aje.account_id) AS distinct_account_count,
                    SUM(CASE
                        WHEN aje.id IS NOT NULL
                            AND aje.movement_type NOT IN ('BALANCE_DECREASE', 'BALANCE_INCREASE') THEN 1
                        ELSE 0
                    END) AS unknown_movement_count,
                    SUM(CASE WHEN aje.id IS NOT NULL AND aje.amount <> ft.amount THEN 1 ELSE 0 END)
                        AS journal_amount_mismatch_count,
                    COALESCE(SUM(CASE aje.movement_type
                        WHEN 'BALANCE_INCREASE' THEN aje.amount
                        WHEN 'BALANCE_DECREASE' THEN -aje.amount
                        ELSE 0
                    END), 0) AS signed_journal_amount
                FROM financial_transaction ft
                LEFT JOIN account_journal_entry aje ON aje.transaction_id = ft.id
                GROUP BY ft.id, ft.type, ft.amount
                ORDER BY ft.id
                """, (resultSet, rowNumber) -> {
            TransactionJournalInvariant.TransactionJournalStats stats =
                    new TransactionJournalInvariant.TransactionJournalStats(
                            resultSet.getString("transaction_type"),
                            resultSet.getLong("transaction_amount"),
                            resultSet.getLong("journal_entry_count"),
                            resultSet.getLong("entry_no_one_count"),
                            resultSet.getLong("entry_no_two_count"),
                            resultSet.getLong("entry_one_decrease_count"),
                            resultSet.getLong("entry_one_increase_count"),
                            resultSet.getLong("entry_two_increase_count"),
                            resultSet.getLong("decrease_entry_count"),
                            resultSet.getLong("increase_entry_count"),
                            resultSet.getLong("distinct_account_count"),
                            resultSet.getLong("unknown_movement_count"),
                            resultSet.getLong("journal_amount_mismatch_count"),
                            resultSet.getLong("signed_journal_amount")
                    );
            List<String> issueCodes = TransactionJournalInvariant.findIssueCodes(stats);
            if (issueCodes.isEmpty()) {
                return null;
            }
            return new TransactionJournalReconciliationResult(
                    resultSet.getLong("transaction_id"),
                    stats.transactionType(),
                    stats.transactionAmount(),
                    issueCodes,
                    stats.journalEntryCount(),
                    stats.decreaseEntryCount(),
                    stats.increaseEntryCount(),
                    stats.distinctAccountCount(),
                    stats.journalAmountMismatchCount(),
                    stats.signedJournalAmount()
            );
        }).stream()
                .filter(result -> result != null)
                .toList();
    }
}
