package com.bankcore.exception;

public class InvalidAccountNumberException extends RuntimeException implements BankCoreException {

    public InvalidAccountNumberException() {
        super("Account number must not be blank.");
    }

    public InvalidAccountNumberException(int maxLength) {
        super("Account number length must be at most " + maxLength + ".");
    }

    @Override
    public String getCode() {
        return "INVALID_ACCOUNT_NUMBER";
    }
}
