package com.bankcore.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateAccountRequest(
        @NotNull
        Long customerId,

        @NotBlank
        String accountNumber
) {
}
