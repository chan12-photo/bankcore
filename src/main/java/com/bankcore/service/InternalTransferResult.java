package com.bankcore.service;

public record InternalTransferResult(
        Long transactionId,
        String transactionKey,
        Long sourceAccountId,
        Long destinationAccountId,
        long sourceBalanceAfter,
        long destinationBalanceAfter,
        long amount
) {
}
