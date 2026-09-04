package com.bankcore.exception;

public class InsufficientBalanceException extends RuntimeException implements BankCoreException {

    public InsufficientBalanceException() {
        super("Insufficient balance.");
    }

    @Override
    public String getCode() {
        return "INSUFFICIENT_BALANCE";
    }
}
