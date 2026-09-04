package com.bankcore.controller.dto;

public record CreateAccountRequest(
        Long customerId,
        String accountNumber
) {
}
