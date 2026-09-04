package com.bankcore.controller.dto;

public record TransferResponse(
        Long transactionId,
        String transactionKey,
        Long sourceAccountId,
        Long destinationAccountId,
        long sourceBalanceAfter,
        long destinationBalanceAfter,
        long amount
) {
}
