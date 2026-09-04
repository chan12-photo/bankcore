package com.bankcore.exception;

public class BalanceLimitExceededException extends RuntimeException implements BankCoreException {

    public BalanceLimitExceededException() {
        super("Balance exceeds the configured limit.");
    }

    @Override
    public String getCode() {
        return "BALANCE_LIMIT_EXCEEDED";
    }
}
