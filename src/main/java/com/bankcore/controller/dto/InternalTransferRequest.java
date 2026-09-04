package com.bankcore.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

public record InternalTransferRequest(
        @NotNull
        @Positive
        Long sourceAccountId,

        @NotNull
        @Positive
        Long destinationAccountId,

        @NotNull
        @Positive
        @Max(1_000_000_000_000L)
        Long amount
) {
}
