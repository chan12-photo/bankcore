package com.bankcore.controller;

import com.bankcore.controller.dto.AccountBalanceReconciliationResponse;
import com.bankcore.controller.dto.TransactionJournalReconciliationResponse;
import com.bankcore.service.AccountBalanceReconciliationResult;
import com.bankcore.service.ReconciliationService;
import com.bankcore.service.TransactionJournalReconciliationResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reconciliation")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping("/account-balances/mismatches")
    public List<AccountBalanceReconciliationResponse> findAccountBalanceMismatches() {
        return reconciliationService.findAccountBalanceMismatches().stream()
                .map(ReconciliationController::toAccountBalanceResponse)
                .toList();
    }

    @GetMapping("/transaction-journals/mismatches")
    public List<TransactionJournalReconciliationResponse> findTransactionJournalMismatches() {
        return reconciliationService.findTransactionJournalMismatches().stream()
                .map(ReconciliationController::toTransactionJournalResponse)
                .toList();
    }

    private static AccountBalanceReconciliationResponse toAccountBalanceResponse(
            AccountBalanceReconciliationResult result
    ) {
        return new AccountBalanceReconciliationResponse(
                result.accountId(),
                result.storedBalance(),
                result.journalBalance(),
                result.difference()
        );
    }

    private static TransactionJournalReconciliationResponse toTransactionJournalResponse(
            TransactionJournalReconciliationResult result
    ) {
        return new TransactionJournalReconciliationResponse(
                result.transactionId(),
                result.transactionType(),
                result.transactionAmount(),
                result.issueCodes(),
                result.journalEntryCount(),
                result.decreaseEntryCount(),
                result.increaseEntryCount(),
                result.distinctAccountCount(),
                result.journalAmountMismatchCount(),
                result.signedJournalAmount()
        );
    }
}
