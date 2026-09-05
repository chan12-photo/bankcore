package com.bankcore.service;

import com.bankcore.controller.dto.AccountResponse;
import com.bankcore.domain.Account;
import com.bankcore.domain.Customer;
import com.bankcore.exception.CustomerNotFoundException;
import com.bankcore.exception.DuplicateAccountNumberException;
import com.bankcore.exception.InvalidAccountNumberException;
import com.bankcore.repository.AccountRepository;
import com.bankcore.repository.CustomerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final int MAX_ACCOUNT_NUMBER_LENGTH = 30;
    private static final String ACCOUNT_NUMBER_UNIQUE_CONSTRAINT = "uk_account_account_number";

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;

    public AccountService(CustomerRepository customerRepository, AccountRepository accountRepository) {
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public AccountResponse createAccount(Long customerId, String accountNumber) {
        validateCustomerId(customerId);
        validateAccountNumber(accountNumber);
        if (accountRepository.findByAccountNumber(accountNumber).isPresent()) {
            throw new DuplicateAccountNumberException(accountNumber);
        }

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        try {
            Account account = accountRepository.saveAndFlush(new Account(customer, accountNumber));
            return toResponse(account);
        } catch (DataIntegrityViolationException exception) {
            if (!DatabaseConstraintMatcher.containsConstraintName(exception, ACCOUNT_NUMBER_UNIQUE_CONSTRAINT)) {
                throw exception;
            }
            throw new DuplicateAccountNumberException(accountNumber);
        }
    }

    private static void validateCustomerId(Long customerId) {
        if (customerId == null) {
            throw new CustomerNotFoundException(null);
        }
    }

    private static void validateAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new InvalidAccountNumberException();
        }
        if (accountNumber.length() > MAX_ACCOUNT_NUMBER_LENGTH) {
            throw new InvalidAccountNumberException(MAX_ACCOUNT_NUMBER_LENGTH);
        }
    }

    private static AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getCustomer().getId(),
                account.getAccountNumber(),
                account.getBalance(),
                account.getStatus()
        );
    }
}
