package com.bankcore.exception;

public class InvalidIdempotencyRequestException extends RuntimeException implements BankCoreException {

    public InvalidIdempotencyRequestException(String fieldName) {
        super(fieldName + " must not be blank.");
    }

    @Override
    public String getCode() {
        return "INVALID_IDEMPOTENCY_REQUEST";
    }
}
