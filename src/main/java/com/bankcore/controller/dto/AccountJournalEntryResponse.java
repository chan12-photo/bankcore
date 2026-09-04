package com.bankcore.controller.dto;

import com.bankcore.domain.JournalMovementType;

import java.time.Instant;

public record AccountJournalEntryResponse(
        Long entryId,
        Long transactionId,
        int entryNo,
        JournalMovementType movementType,
        long amount,
        long balanceAfter,
        Instant createdAt
) {
}
