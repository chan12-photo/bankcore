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
                    id,
                    transaction_id,
                    balance_after,
                    SUM(CASE movement_type
                        WHEN 'BALANCE_INCREASE' THEN amount
                        WHEN 'BALANCE_DECREASE' THEN -amount
                        ELSE 0
                    END) OVER (
                        PARTITION BY account_id
                        ORDER BY id
                        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                    ) AS expected_balance_after
                FROM account_journal_entry
            ) balance_after_check
            WHERE transaction_id = :transactionId
                AND balance_after <> expected_balance_after
            """, nativeQuery = true)
    long countBalanceAfterMismatchesForTransaction(@Param("transactionId") Long transactionId);
}
