package com.bankcore.exception;

public class CustomerNotFoundException extends RuntimeException implements BankCoreException {

    public CustomerNotFoundException(Long customerId) {
        super("Customer not found: " + customerId);
    }

    @Override
    public String getCode() {
        return "CUSTOMER_NOT_FOUND";
    }
}
