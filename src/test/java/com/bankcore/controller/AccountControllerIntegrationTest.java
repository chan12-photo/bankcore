package com.bankcore.controller;

import com.bankcore.domain.Account;
import com.bankcore.domain.Customer;
import com.bankcore.repository.AccountRepository;
import com.bankcore.repository.CustomerRepository;
import com.bankcore.support.MySqlContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ImportTestcontainers(MySqlContainerSupport.class)
class AccountControllerIntegrationTest {

    private static final AtomicLong ACCOUNT_SEQUENCE = new AtomicLong(1000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void createAccount_shouldStartWithZeroBalance() throws Exception {
        Long customerId = createCustomer();
        String accountNumber = nextAccountNumber();

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createAccountJson(customerId, accountNumber)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.customerId").value(customerId))
                .andExpect(jsonPath("$.accountNumber").value(accountNumber))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createAccount_shouldRejectDuplicateAccountNumber() throws Exception {
        Long customerId = createCustomer();
        String accountNumber = nextAccountNumber();
        accountRepository.saveAndFlush(new Account(customerRepository.getReferenceById(customerId), accountNumber));

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createAccountJson(customerId, accountNumber)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ACCOUNT_NUMBER"));
    }

    @Test
    void createAccount_shouldRejectMissingCustomerId() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountNumber":"100-000-validation"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Invalid request field: customerId"));
    }

    @Test
    void createAccount_shouldRejectBlankAccountNumber() throws Exception {
        Long customerId = createCustomer();

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createAccountJson(customerId, " ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Invalid request field: accountNumber"));
    }

    @Test
    void depositEndpoint_shouldNotBePublicApi() throws Exception {
        Account account = createAccount();

        mockMvc.perform(post("/api/v1/accounts/{accountId}/deposits", account.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":1000}
                                """))
                .andExpect(status().isNotFound());
    }

    private Long createCustomer() {
        return customerRepository.saveAndFlush(new Customer("Customer " + ACCOUNT_SEQUENCE.incrementAndGet())).getId();
    }

    private Account createAccount() {
        Customer customer = customerRepository.saveAndFlush(new Customer("Customer " + ACCOUNT_SEQUENCE.incrementAndGet()));
        return accountRepository.saveAndFlush(new Account(customer, nextAccountNumber()));
    }

    private String nextAccountNumber() {
        return "100-000-" + ACCOUNT_SEQUENCE.incrementAndGet();
    }

    private static String createAccountJson(Long customerId, String accountNumber) {
        return """
                {"customerId":%d,"accountNumber":"%s"}
                """.formatted(customerId, accountNumber);
    }

}
