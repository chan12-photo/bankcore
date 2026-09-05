package com.bankcore.exception;

public class InvalidCustomerNameException extends RuntimeException implements BankCoreException {

    public InvalidCustomerNameException() {
        super("Customer name must not be blank.");
    }

    public InvalidCustomerNameException(int maxLength) {
        super("Customer name length must be at most " + maxLength + ".");
    }

    @Override
    public String getCode() {
        return "INVALID_CUSTOMER_NAME";
    }
}
