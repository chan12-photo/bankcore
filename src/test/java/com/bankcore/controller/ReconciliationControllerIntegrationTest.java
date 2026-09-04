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
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ImportTestcontainers(MySqlContainerSupport.class)
class ReconciliationControllerIntegrationTest {

    private static final AtomicLong ACCOUNT_SEQUENCE = new AtomicLong(60_000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void findAccountBalanceMismatches_shouldReturnMismatchRows() throws Exception {
        Account account = createZeroBalanceAccount();
        account.deposit(7_000L);
        accountRepository.saveAndFlush(account);

        mockMvc.perform(get("/api/v1/reconciliation/account-balances/mismatches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.accountId == %d && @.difference == 7000)]", account.getId()).exists());
    }

    private Account createZeroBalanceAccount() {
        long sequence = ACCOUNT_SEQUENCE.incrementAndGet();
        Customer customer = customerRepository.saveAndFlush(new Customer("Reconciliation API Customer " + sequence));
        return accountRepository.saveAndFlush(new Account(customer, "600-000-" + sequence));
    }
}
