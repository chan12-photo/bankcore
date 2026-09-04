package com.bankcore.exception;

public class AccountNotFoundException extends RuntimeException implements BankCoreException {

    public AccountNotFoundException(Long accountId) {
        super("Account not found: " + accountId);
    }

    @Override
    public String getCode() {
        return "ACCOUNT_NOT_FOUND";
    }
}
