package com.bankcore.controller.dto;

public record ApiErrorResponse(
        String code,
        String message
) {
}
