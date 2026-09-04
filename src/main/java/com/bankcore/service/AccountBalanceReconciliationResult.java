package com.bankcore.service;

public record AccountBalanceReconciliationResult(
        Long accountId,
        long storedBalance,
        long journalBalance,
        long difference
) {
}
