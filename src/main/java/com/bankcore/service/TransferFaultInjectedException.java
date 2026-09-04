package com.bankcore.service;

class TransferFaultInjectedException extends RuntimeException {

    TransferFaultInjectedException(TransferFailurePoint failurePoint) {
        super("Injected transfer failure at " + failurePoint + ".");
    }
}
