package com.bankcore.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record InternalTransferRequest(
        @NotNull
        Long sourceAccountId,

        @NotNull
        Long destinationAccountId,

        @NotNull
        @Positive
        Long amount
) {
}
