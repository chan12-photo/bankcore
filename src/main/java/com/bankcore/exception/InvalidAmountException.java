package com.bankcore.exception;

public class InvalidAmountException extends RuntimeException implements BankCoreException {

    public InvalidAmountException() {
        super("Amount must be positive.");
    }

    @Override
    public String getCode() {
        return "INVALID_AMOUNT";
    }
}
