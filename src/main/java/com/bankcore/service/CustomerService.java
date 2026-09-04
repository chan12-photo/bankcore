package com.bankcore.service;

import com.bankcore.controller.dto.CustomerResponse;
import com.bankcore.domain.Customer;
import com.bankcore.exception.InvalidCustomerNameException;
import com.bankcore.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public CustomerResponse createCustomer(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidCustomerNameException();
        }

        Customer customer = customerRepository.saveAndFlush(new Customer(name));

        return new CustomerResponse(customer.getId(), customer.getName());
    }
}
