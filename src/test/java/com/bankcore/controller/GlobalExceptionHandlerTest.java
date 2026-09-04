package com.bankcore.controller;

import com.bankcore.controller.dto.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    void handleOptimisticLockingFailure_shouldReturnConflictApiError() {
        ResponseEntity<ApiErrorResponse> response =
                exceptionHandler.handleOptimisticLockingFailure(new OptimisticLockingFailureException("stale write"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
                "CONCURRENT_MODIFICATION",
                "Account state changed while processing the request. Retry the same request with the same idempotency key."
        ));
    }
}
