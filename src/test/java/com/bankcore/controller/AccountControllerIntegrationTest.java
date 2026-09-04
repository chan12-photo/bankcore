package com.bankcore.controller;

import com.bankcore.domain.Account;
import com.bankcore.domain.Customer;
import com.bankcore.repository.AccountRepository;
import com.bankcore.repository.CustomerRepository;
import com.bankcore.support.MySqlContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerIntegrationTest extends MySqlContainerSupport {

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
    void deposit_shouldIncreaseBalance() throws Exception {
        Account account = createAccount(0L);

        mockMvc.perform(post("/api/v1/accounts/{accountId}/deposits", account.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(amountJson(15_000L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(15_000));

        assertThat(accountRepository.findById(account.getId()))
                .isPresent()
                .get()
                .extracting(Account::getBalance)
                .isEqualTo(15_000L);
    }

    @Test
    void withdraw_shouldDecreaseBalance_whenBalanceIsEnough() throws Exception {
        Account account = createAccount(10_000L);

        mockMvc.perform(post("/api/v1/accounts/{accountId}/withdrawals", account.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(amountJson(4_000L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(6_000));

        assertThat(accountRepository.findById(account.getId()))
                .isPresent()
                .get()
                .extracting(Account::getBalance)
                .isEqualTo(6_000L);
    }

    @Test
    void withdraw_shouldFail_whenBalanceIsInsufficient() throws Exception {
        Account account = createAccount(5_000L);

        mockMvc.perform(post("/api/v1/accounts/{accountId}/withdrawals", account.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(amountJson(7_000L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));

        assertThat(accountRepository.findById(account.getId()))
                .isPresent()
                .get()
                .extracting(Account::getBalance)
                .isEqualTo(5_000L);
    }

    @Test
    void deposit_shouldFail_whenAmountIsNotPositive() throws Exception {
        Account account = createAccount(0L);

        mockMvc.perform(post("/api/v1/accounts/{accountId}/deposits", account.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(amountJson(0L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT"));
    }

    @Test
    void frozenAccount_shouldRejectMoneyMovement() throws Exception {
        Account account = createAccount(10_000L);
        account.freeze();
        accountRepository.saveAndFlush(account);

        mockMvc.perform(post("/api/v1/accounts/{accountId}/deposits", account.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(amountJson(1_000L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCOUNT_FROZEN"));
    }

    @Test
    void closedAccount_shouldRejectMoneyMovement() throws Exception {
        Account account = createAccount(10_000L);
        account.close();
        accountRepository.saveAndFlush(account);

        mockMvc.perform(post("/api/v1/accounts/{accountId}/withdrawals", account.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(amountJson(1_000L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCOUNT_CLOSED"));
    }

    private Long createCustomer() {
        return customerRepository.saveAndFlush(new Customer("Customer " + ACCOUNT_SEQUENCE.incrementAndGet())).getId();
    }

    private Account createAccount(long initialBalance) {
        Customer customer = customerRepository.saveAndFlush(new Customer("Customer " + ACCOUNT_SEQUENCE.incrementAndGet()));
        Account account = new Account(customer, nextAccountNumber());
        if (initialBalance > 0) {
            account.deposit(initialBalance);
        }
        return accountRepository.saveAndFlush(account);
    }

    private String nextAccountNumber() {
        return "100-000-" + ACCOUNT_SEQUENCE.incrementAndGet();
    }

    private static String createAccountJson(Long customerId, String accountNumber) {
        return """
                {"customerId":%d,"accountNumber":"%s"}
                """.formatted(customerId, accountNumber);
    }

    private static String amountJson(long amount) {
        return """
                {"amount":%d}
                """.formatted(amount);
    }
}
