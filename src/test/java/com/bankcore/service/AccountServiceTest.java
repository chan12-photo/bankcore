package com.bankcore.service;

import com.bankcore.exception.InvalidAccountNumberException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountServiceTest {

    @Test
    void createAccount_shouldRejectAccountNumberLongerThanDatabaseLimitBeforeRepositoryUse() {
        AccountService accountService = new AccountService(null, null);

        assertThatThrownBy(() -> accountService.createAccount(1L, "1".repeat(31)))
                .isInstanceOf(InvalidAccountNumberException.class)
                .hasMessage("Account number length must be at most 30.");
    }
}
