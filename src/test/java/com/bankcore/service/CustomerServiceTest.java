package com.bankcore.service;

import com.bankcore.exception.InvalidCustomerNameException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerServiceTest {

    @Test
    void createCustomer_shouldRejectNameLongerThanDatabaseLimitBeforeRepositoryUse() {
        CustomerService customerService = new CustomerService(null);

        assertThatThrownBy(() -> customerService.createCustomer("a".repeat(101)))
                .isInstanceOf(InvalidCustomerNameException.class)
                .hasMessage("Customer name length must be at most 100.");
    }
}
