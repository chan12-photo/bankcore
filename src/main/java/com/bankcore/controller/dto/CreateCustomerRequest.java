package com.bankcore.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(
        @NotBlank
        @Size(max = 100)
        String name
) {
}
