package com.bankcore.exception;

public class SameAccountTransferException extends RuntimeException implements BankCoreException {

    public SameAccountTransferException() {
        super("Source account and destination account must be different.");
    }

    @Override
    public String getCode() {
        return "SAME_ACCOUNT_TRANSFER";
    }
}
