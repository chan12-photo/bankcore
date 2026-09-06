package com.bankcore.controller.dto;

import java.util.List;

public record AccountJournalPageResponse(
        List<AccountJournalEntryResponse> items,
        Long nextCursor,
        boolean hasNext
) {
}
