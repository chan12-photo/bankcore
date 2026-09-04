package com.bankcore.controller.dto;

public record AccountBalanceReconciliationResponse(
        Long accountId,
        long storedBalance,
        long journalBalance,
        long difference
) {
}
