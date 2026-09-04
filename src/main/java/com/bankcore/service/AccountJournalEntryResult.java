package com.bankcore.service;

import com.bankcore.domain.JournalMovementType;

import java.time.Instant;

public record AccountJournalEntryResult(
        Long entryId,
        Long transactionId,
        int entryNo,
        JournalMovementType movementType,
        long amount,
        long balanceAfter,
        Instant createdAt
) {
}
