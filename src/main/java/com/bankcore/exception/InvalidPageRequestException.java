package com.bankcore.exception;

public class InvalidPageRequestException extends RuntimeException implements BankCoreException {

    public InvalidPageRequestException() {
        super("Page limit must be between 1 and 100.");
    }

    @Override
    public String getCode() {
        return "INVALID_PAGE_REQUEST";
    }
}
