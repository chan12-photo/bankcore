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
}
