package com.bankcore.service;

import java.util.List;

public record AccountJournalPageResult(
        List<AccountJournalEntryResult> items,
        Long nextCursor,
        boolean hasNext
) {
}
