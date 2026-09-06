package com.bankcore.controller;

import com.bankcore.controller.dto.AccountJournalEntryResponse;
import com.bankcore.controller.dto.AccountJournalPageResponse;
import com.bankcore.service.AccountJournalEntryResult;
import com.bankcore.service.AccountJournalPageResult;
import com.bankcore.service.AccountJournalQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}/journal-entries")
public class AccountJournalController {

    private final AccountJournalQueryService accountJournalQueryService;

    public AccountJournalController(AccountJournalQueryService accountJournalQueryService) {
        this.accountJournalQueryService = accountJournalQueryService;
    }

    @GetMapping
    public AccountJournalPageResponse findRecentEntries(
            @PathVariable Long accountId,
            @RequestParam(required = false) Long beforeEntryId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        AccountJournalPageResult page = accountJournalQueryService.findRecentEntries(accountId, beforeEntryId, limit);
        List<AccountJournalEntryResponse> items = page.items().stream()
                .map(AccountJournalController::toResponse)
                .toList();
        return new AccountJournalPageResponse(items, page.nextCursor(), page.hasNext());
    }

    private static AccountJournalEntryResponse toResponse(AccountJournalEntryResult result) {
        return new AccountJournalEntryResponse(
                result.entryId(),
                result.transactionId(),
                result.entryNo(),
                result.movementType(),
                result.amount(),
                result.balanceAfter(),
                result.createdAt()
        );
    }
}
