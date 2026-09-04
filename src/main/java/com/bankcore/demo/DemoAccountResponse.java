package com.bankcore.demo;

public record DemoAccountResponse(
        Long accountId,
        Long customerId,
        String customerName,
        String accountNumber,
        long balance
) {
}
