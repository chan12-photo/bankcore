package com.bankcore.exception;

public class InvalidAccountNumberException extends RuntimeException implements BankCoreException {

    public InvalidAccountNumberException() {
        super("Account number must not be blank.");
    }

    @Override
    public String getCode() {
        return "INVALID_ACCOUNT_NUMBER";
    }
}
