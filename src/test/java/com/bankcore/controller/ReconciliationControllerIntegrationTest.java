package com.bankcore.controller;

import com.bankcore.domain.Account;
import com.bankcore.domain.AccountJournalEntry;
import com.bankcore.domain.Customer;
import com.bankcore.domain.FinancialTransaction;
import com.bankcore.domain.JournalMovementType;
import com.bankcore.domain.TransactionType;
import com.bankcore.repository.AccountRepository;
import com.bankcore.repository.AccountJournalEntryRepository;
import com.bankcore.repository.CustomerRepository;
import com.bankcore.repository.FinancialTransactionRepository;
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

    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    @Autowired
    private AccountJournalEntryRepository accountJournalEntryRepository;

    @Test
    void findAccountBalanceMismatches_shouldReturnMismatchRows() throws Exception {
        Account account = createZeroBalanceAccount();
        account.deposit(7_000L);
        accountRepository.saveAndFlush(account);

        mockMvc.perform(get("/api/v1/reconciliation/account-balances/mismatches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.accountId == %d && @.difference == 7000)]", account.getId()).exists());
    }

    @Test
    void findTransactionJournalMismatches_shouldReturnMismatchRows() throws Exception {
        Account account = createZeroBalanceAccount();
        FinancialTransaction transaction = financialTransactionRepository.saveAndFlush(
                new FinancialTransaction("recon-api-tx-" + ACCOUNT_SEQUENCE.incrementAndGet(),
                        TransactionType.INTERNAL_TRANSFER,
                        1_000L)
        );
        accountJournalEntryRepository.saveAndFlush(new AccountJournalEntry(
                transaction,
                1,
                account,
                JournalMovementType.BALANCE_DECREASE,
                1_000L,
                0L
        ));

        mockMvc.perform(get("/api/v1/reconciliation/transaction-journals/mismatches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.transactionId == %d && @.journalEntryCount == 1)]",
                        transaction.getId()).exists())
                .andExpect(jsonPath("$[?(@.transactionId == %d && @.issueCodes[0] == 'JOURNAL_ENTRY_COUNT')]",
                        transaction.getId()).exists());
    }

    private Account createZeroBalanceAccount() {
        long sequence = ACCOUNT_SEQUENCE.incrementAndGet();
        Customer customer = customerRepository.saveAndFlush(new Customer("Reconciliation API Customer " + sequence));
        return accountRepository.saveAndFlush(new Account(customer, "600-000-" + sequence));
    }
}
