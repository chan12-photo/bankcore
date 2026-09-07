package com.bankcore.repository;

import com.bankcore.domain.AccountJournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AccountJournalEntryRepository extends JpaRepository<AccountJournalEntry, Long> {

    List<AccountJournalEntry> findByTransactionIdOrderByEntryNo(Long transactionId);

    @Query(value = """
            SELECT COUNT(*)
            FROM (
                SELECT
                    target_entry.id,
                    target_entry.balance_after,
                    SUM(CASE history_entry.movement_type
                        WHEN 'BALANCE_INCREASE' THEN history_entry.amount
                        WHEN 'BALANCE_DECREASE' THEN -history_entry.amount
                        ELSE 0
                    END) AS expected_balance_after
                FROM account_journal_entry target_entry
                JOIN account_journal_entry history_entry
                    ON history_entry.account_id = target_entry.account_id
                    AND history_entry.id <= target_entry.id
                WHERE target_entry.transaction_id = :transactionId
                GROUP BY target_entry.id, target_entry.balance_after
            ) balance_after_check
            WHERE balance_after <> expected_balance_after
            """, nativeQuery = true)
    long countBalanceAfterMismatchesForTransaction(@Param("transactionId") Long transactionId);
}
