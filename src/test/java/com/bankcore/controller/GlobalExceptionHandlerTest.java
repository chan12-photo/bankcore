package com.bankcore.controller;

import com.bankcore.controller.dto.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    void handleConcurrentModification_shouldReturnConflictApiErrorForOptimisticLockFailures() {
        ResponseEntity<ApiErrorResponse> response =
                exceptionHandler.handleConcurrentModification(new OptimisticLockingFailureException("stale write"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
                "CONCURRENT_MODIFICATION",
                "Concurrent account update could not be completed. Retry the same request with the same idempotency key."
        ));
    }

    @Test
    void handleConcurrentModification_shouldReturnConflictApiErrorForPessimisticLockFailures() {
        ResponseEntity<ApiErrorResponse> response =
                exceptionHandler.handleConcurrentModification(new CannotAcquireLockException("lock wait timeout"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
                "CONCURRENT_MODIFICATION",
                "Concurrent account update could not be completed. Retry the same request with the same idempotency key."
        ));
    }
}
