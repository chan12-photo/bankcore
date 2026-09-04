package com.bankcore.controller;

import com.bankcore.controller.dto.ApiErrorResponse;
import com.bankcore.exception.AccountNotActiveException;
import com.bankcore.exception.AccountNotFoundException;
import com.bankcore.exception.AmountLimitExceededException;
import com.bankcore.exception.BankCoreException;
import com.bankcore.exception.BalanceLimitExceededException;
import com.bankcore.exception.CustomerNotFoundException;
import com.bankcore.exception.DuplicateAccountNumberException;
import com.bankcore.exception.InsufficientBalanceException;
import com.bankcore.exception.InvalidAccountNumberException;
import com.bankcore.exception.InvalidAmountException;
import com.bankcore.exception.InvalidCustomerNameException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleCustomerNotFound(CustomerNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountNotFound(AccountNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(DuplicateAccountNumberException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateAccountNumber(DuplicateAccountNumberException exception) {
        return error(HttpStatus.CONFLICT, exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler({
            AccountNotActiveException.class,
            AmountLimitExceededException.class,
            BalanceLimitExceededException.class,
            InsufficientBalanceException.class,
            InvalidAccountNumberException.class,
            InvalidAmountException.class,
            InvalidCustomerNameException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(RuntimeException exception) {
        return error(HttpStatus.BAD_REQUEST, ((BankCoreException) exception).getCode(), exception.getMessage());
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message));
    }
}
