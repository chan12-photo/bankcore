package com.bankcore.exception;

public class InvalidCustomerNameException extends RuntimeException implements BankCoreException {

    public InvalidCustomerNameException() {
        super("Customer name must not be blank.");
    }

    @Override
    public String getCode() {
        return "INVALID_CUSTOMER_NAME";
    }
}
