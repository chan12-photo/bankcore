package com.bankcore.controller;

import com.bankcore.domain.Account;
import com.bankcore.domain.Customer;
import com.bankcore.repository.AccountJournalEntryRepository;
import com.bankcore.repository.AccountRepository;
import com.bankcore.repository.CustomerRepository;
import com.bankcore.repository.FinancialTransactionRepository;
import com.bankcore.service.ControlledFundingService;
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
class TransferControllerIntegrationTest {

    private static final AtomicLong ACCOUNT_SEQUENCE = new AtomicLong(30_000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ControlledFundingService controlledFundingService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    @Autowired
    private AccountJournalEntryRepository accountJournalEntryRepository;

    @Test
    void internalTransfer_shouldMoveMoney_whenIdempotencyHeadersAreProvided() throws Exception {
        Account sourceAccount = createAccountWithBalance(10_000L);
        Account destinationAccount = createAccountWithBalance(2_000L);

        mockMvc.perform(post("/api/v1/transfers/internal")
                        .header("X-Caller-Scope", "controller-test")
                        .header("Idempotency-Key", nextIdempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(sourceAccount.getId(), destinationAccount.getId(), 3_000L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").isNumber())
                .andExpect(jsonPath("$.transactionKey").isString())
                .andExpect(jsonPath("$.sourceAccountId").value(sourceAccount.getId()))
                .andExpect(jsonPath("$.destinationAccountId").value(destinationAccount.getId()))
                .andExpect(jsonPath("$.sourceBalanceAfter").value(7_000))
                .andExpect(jsonPath("$.destinationBalanceAfter").value(5_000))
                .andExpect(jsonPath("$.amount").value(3_000));

        assertThat(accountRepository.findById(sourceAccount.getId()).orElseThrow().getBalance()).isEqualTo(7_000L);
        assertThat(accountRepository.findById(destinationAccount.getId()).orElseThrow().getBalance()).isEqualTo(5_000L);
    }

    @Test
    void internalTransfer_shouldReplaySameResponse_whenSameIdempotencyKeyAndBodyAreRetried() throws Exception {
        Account sourceAccount = createAccountWithBalance(10_000L);
        Account destinationAccount = createAccountWithBalance(2_000L);
        String idempotencyKey = nextIdempotencyKey();
        long transactionCountBefore = financialTransactionRepository.count();
        long journalCountBefore = accountJournalEntryRepository.count();
        String requestBody = transferJson(sourceAccount.getId(), destinationAccount.getId(), 3_000L);

        String firstResponse = mockMvc.perform(post("/api/v1/transfers/internal")
                        .header("X-Caller-Scope", "controller-test")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String replayedResponse = mockMvc.perform(post("/api/v1/transfers/internal")
                        .header("X-Caller-Scope", "controller-test")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(replayedResponse).isEqualTo(firstResponse);
        assertThat(accountRepository.findById(sourceAccount.getId()).orElseThrow().getBalance()).isEqualTo(7_000L);
        assertThat(accountRepository.findById(destinationAccount.getId()).orElseThrow().getBalance()).isEqualTo(5_000L);
        assertThat(financialTransactionRepository.count()).isEqualTo(transactionCountBefore + 1);
        assertThat(accountJournalEntryRepository.count()).isEqualTo(journalCountBefore + 2);
    }

    @Test
    void internalTransfer_shouldRejectSameIdempotencyKeyWithDifferentBody() throws Exception {
        Account sourceAccount = createAccountWithBalance(10_000L);
        Account destinationAccount = createAccountWithBalance(2_000L);
        String idempotencyKey = nextIdempotencyKey();

        mockMvc.perform(post("/api/v1/transfers/internal")
                        .header("X-Caller-Scope", "controller-test")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(sourceAccount.getId(), destinationAccount.getId(), 3_000L)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/transfers/internal")
                        .header("X-Caller-Scope", "controller-test")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(sourceAccount.getId(), destinationAccount.getId(), 4_000L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));

        assertThat(accountRepository.findById(sourceAccount.getId()).orElseThrow().getBalance()).isEqualTo(7_000L);
        assertThat(accountRepository.findById(destinationAccount.getId()).orElseThrow().getBalance()).isEqualTo(5_000L);
    }

    @Test
    void internalTransfer_shouldRejectBlankIdempotencyHeader() throws Exception {
        Account sourceAccount = createAccountWithBalance(10_000L);
        Account destinationAccount = createAccountWithBalance(2_000L);

        mockMvc.perform(post("/api/v1/transfers/internal")
                        .header("X-Caller-Scope", "controller-test")
                        .header("Idempotency-Key", " ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(sourceAccount.getId(), destinationAccount.getId(), 3_000L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDEMPOTENCY_REQUEST"));
    }

    @Test
    void internalTransfer_shouldRejectMissingIdempotencyHeaderWithApiError() throws Exception {
        Account sourceAccount = createAccountWithBalance(10_000L);
        Account destinationAccount = createAccountWithBalance(2_000L);

        mockMvc.perform(post("/api/v1/transfers/internal")
                        .header("X-Caller-Scope", "controller-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(sourceAccount.getId(), destinationAccount.getId(), 3_000L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUIRED_HEADER"))
                .andExpect(jsonPath("$.message").value("Missing required header: Idempotency-Key"));
    }

    @Test
    void internalTransfer_shouldRejectMalformedJsonWithApiError() throws Exception {
        mockMvc.perform(post("/api/v1/transfers/internal")
                        .header("X-Caller-Scope", "controller-test")
                        .header("Idempotency-Key", nextIdempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceAccountId":
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed."));
    }

    private Account createAccountWithBalance(long balance) {
        long sequence = ACCOUNT_SEQUENCE.incrementAndGet();
        Customer customer = customerRepository.saveAndFlush(new Customer("Transfer API Customer " + sequence));
        Account account = new Account(customer, "300-000-" + sequence);
        Account savedAccount = accountRepository.saveAndFlush(account);
        if (balance > 0) {
            controlledFundingService.seedFunds(savedAccount.getId(), balance);
        }
        return savedAccount;
    }

    private static String nextIdempotencyKey() {
        return "api-idem-" + ACCOUNT_SEQUENCE.incrementAndGet();
    }

    private static String transferJson(Long sourceAccountId, Long destinationAccountId, Long amount) {
        return """
                {"sourceAccountId":%d,"destinationAccountId":%d,"amount":%d}
                """.formatted(sourceAccountId, destinationAccountId, amount);
    }
}
