package com.bankcore.service;

import java.util.List;

public record TransactionJournalReconciliationResult(
        Long transactionId,
        String transactionType,
        long transactionAmount,
        List<String> issueCodes,
        long journalEntryCount,
        long decreaseEntryCount,
        long increaseEntryCount,
        long distinctAccountCount,
        long journalAmountMismatchCount,
        long signedJournalAmount
) {
}
