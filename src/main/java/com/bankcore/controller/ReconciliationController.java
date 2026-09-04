package com.bankcore.controller;

import com.bankcore.controller.dto.AccountBalanceReconciliationResponse;
import com.bankcore.service.AccountBalanceReconciliationResult;
import com.bankcore.service.ReconciliationService;
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
                .map(ReconciliationController::toResponse)
                .toList();
    }

    private static AccountBalanceReconciliationResponse toResponse(AccountBalanceReconciliationResult result) {
        return new AccountBalanceReconciliationResponse(
                result.accountId(),
                result.storedBalance(),
                result.journalBalance(),
                result.difference()
        );
    }
}
