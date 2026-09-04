package com.bankcore.controller.dto;

import com.bankcore.domain.AccountStatus;

public record AccountResponse(
        Long id,
        Long customerId,
        String accountNumber,
        long balance,
        AccountStatus status
) {
}
