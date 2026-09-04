package com.bankcore.controller.dto;

public record InternalTransferRequest(
        Long sourceAccountId,
        Long destinationAccountId,
        Long amount
) {
}
