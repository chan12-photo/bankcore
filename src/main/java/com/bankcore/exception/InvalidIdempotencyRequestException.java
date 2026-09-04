package com.bankcore.exception;

public class InvalidIdempotencyRequestException extends RuntimeException implements BankCoreException {

    public InvalidIdempotencyRequestException(String fieldName) {
        super(fieldName + " must not be blank.");
    }

    public InvalidIdempotencyRequestException(String fieldName, int maxLength) {
        super(fieldName + " length must be at most " + maxLength + ".");
    }

    @Override
    public String getCode() {
        return "INVALID_IDEMPOTENCY_REQUEST";
    }
}
