package com.bankcore.service;

public record ControlledFundingResult(
        Long transactionId,
        String transactionKey,
        Long accountId,
        long balanceAfter,
        long amount
) {
}
