package com.bankcore.controller;

import com.bankcore.controller.dto.AccountResponse;
import com.bankcore.controller.dto.CreateAccountRequest;
import com.bankcore.controller.dto.MoneyMovementRequest;
import com.bankcore.service.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@RequestBody CreateAccountRequest request) {
        AccountResponse response = accountService.createAccount(request.customerId(), request.accountNumber());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{accountId}/deposits")
    public AccountResponse deposit(@PathVariable Long accountId, @RequestBody MoneyMovementRequest request) {
        return accountService.deposit(accountId, request.amount());
    }

    @PostMapping("/{accountId}/withdrawals")
    public AccountResponse withdraw(@PathVariable Long accountId, @RequestBody MoneyMovementRequest request) {
        return accountService.withdraw(accountId, request.amount());
    }
}
