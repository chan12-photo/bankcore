package com.bankcore.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCustomerRequest(
        @NotBlank
        String name
) {
}
