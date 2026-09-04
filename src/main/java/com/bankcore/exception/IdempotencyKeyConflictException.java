package com.bankcore.exception;

public class IdempotencyKeyConflictException extends RuntimeException implements BankCoreException {

    public IdempotencyKeyConflictException() {
        super("Idempotency key was already used for a different request.");
    }

    @Override
    public String getCode() {
        return "IDEMPOTENCY_KEY_CONFLICT";
    }
}
