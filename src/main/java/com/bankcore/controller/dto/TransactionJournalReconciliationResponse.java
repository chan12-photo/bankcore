package com.bankcore.controller.dto;

import java.util.List;

public record TransactionJournalReconciliationResponse(
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
