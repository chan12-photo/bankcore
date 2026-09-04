package com.bankcore.service;

import com.bankcore.domain.Account;
import com.bankcore.domain.Customer;
import com.bankcore.repository.AccountRepository;
import com.bankcore.repository.CustomerRepository;
import com.bankcore.support.MySqlContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ImportTestcontainers(MySqlContainerSupport.class)
class ReconciliationServiceIntegrationTest {

    private static final AtomicLong ACCOUNT_SEQUENCE = new AtomicLong(50_000);

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private ControlledFundingService controlledFundingService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void findAccountBalanceMismatches_shouldNotReportAccountsFundedAndTransferredThroughJournaledFlows() {
        Account sourceAccount = createZeroBalanceAccount();
        Account destinationAccount = createZeroBalanceAccount();
        controlledFundingService.seedFunds(sourceAccount.getId(), 10_000L);
        controlledFundingService.seedFunds(destinationAccount.getId(), 2_000L);

        transferService.transferInternalIdempotent(
                "reconciliation-test",
                nextIdempotencyKey(),
                sourceAccount.getId(),
                destinationAccount.getId(),
                3_000L
        );

        assertThat(reconciliationService.findAccountBalanceMismatches())
                .filteredOn(result -> result.accountId().equals(sourceAccount.getId())
                        || result.accountId().equals(destinationAccount.getId()))
                .isEmpty();
    }

    @Test
    void findAccountBalanceMismatches_shouldReportAccountWhenStoredBalanceHasNoMatchingJournal() {
        Account account = createZeroBalanceAccount();
        account.deposit(5_000L);
        accountRepository.saveAndFlush(account);

        assertThat(reconciliationService.findAccountBalanceMismatches())
                .filteredOn(result -> result.accountId().equals(account.getId()))
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.storedBalance()).isEqualTo(5_000L);
                    assertThat(result.journalBalance()).isZero();
                    assertThat(result.difference()).isEqualTo(5_000L);
                });
    }

    private Account createZeroBalanceAccount() {
        long sequence = ACCOUNT_SEQUENCE.incrementAndGet();
        Customer customer = customerRepository.saveAndFlush(new Customer("Reconciliation Customer " + sequence));
        return accountRepository.saveAndFlush(new Account(customer, "500-000-" + sequence));
    }

    private static String nextIdempotencyKey() {
        return "recon-idem-" + ACCOUNT_SEQUENCE.incrementAndGet();
    }
}
