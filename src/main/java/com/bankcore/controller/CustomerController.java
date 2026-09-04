package com.bankcore.controller;

import com.bankcore.controller.dto.CreateCustomerRequest;
import com.bankcore.controller.dto.CustomerResponse;
import com.bankcore.service.CustomerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> createCustomer(@RequestBody CreateCustomerRequest request) {
        CustomerResponse response = customerService.createCustomer(request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
