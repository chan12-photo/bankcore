package com.bankcore.repository;

import com.bankcore.domain.AccountJournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountJournalEntryRepository extends JpaRepository<AccountJournalEntry, Long> {

    List<AccountJournalEntry> findByTransactionIdOrderByEntryNo(Long transactionId);
}
