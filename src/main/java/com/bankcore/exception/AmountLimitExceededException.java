package com.bankcore.exception;

public class AmountLimitExceededException extends RuntimeException implements BankCoreException {

    public AmountLimitExceededException() {
        super("Amount exceeds the configured limit.");
    }

    @Override
    public String getCode() {
        return "AMOUNT_LIMIT_EXCEEDED";
    }
}
