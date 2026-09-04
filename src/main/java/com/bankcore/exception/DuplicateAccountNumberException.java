package com.bankcore.exception;

public class DuplicateAccountNumberException extends RuntimeException implements BankCoreException {

    public DuplicateAccountNumberException(String accountNumber) {
        super("Account number already exists: " + accountNumber);
    }

    @Override
    public String getCode() {
        return "DUPLICATE_ACCOUNT_NUMBER";
    }
}
