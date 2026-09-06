package com.bankcore.service;

import com.bankcore.domain.Account;
import com.bankcore.domain.AccountJournalEntry;
import com.bankcore.domain.Customer;
import com.bankcore.domain.FinancialTransaction;
import com.bankcore.domain.JournalMovementType;
import com.bankcore.domain.TransactionType;
import com.bankcore.repository.AccountJournalEntryRepository;
import com.bankcore.repository.AccountRepository;
import com.bankcore.repository.CustomerRepository;
import com.bankcore.repository.FinancialTransactionRepository;
import com.bankcore.support.MySqlContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ImportTestcontainers(MySqlContainerSupport.class)
class OptimisticLockTransferConcurrencyTest {

    private static final AtomicLong ACCOUNT_SEQUENCE = new AtomicLong(80_000);

    @Autowired
    private ControlledFundingService controlledFundingService;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    @Autowired
    private AccountJournalEntryRepository accountJournalEntryRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void optimisticVersion_shouldAllowOnlyOneConcurrentTransfer_whenBothReadSameSourceVersion()
            throws Exception {
        Account sourceAccount = createZeroBalanceAccount();
        Account firstDestinationAccount = createZeroBalanceAccount();
        Account secondDestinationAccount = createZeroBalanceAccount();
        controlledFundingService.seedFunds(sourceAccount.getId(), 10_000L);
        CountDownLatch bothTransactionsLoadedAccounts = new CountDownLatch(2);
        OptimisticTransferExperiment optimisticTransfer = new OptimisticTransferExperiment(
                transactionManager,
                accountRepository,
                financialTransactionRepository,
                accountJournalEntryRepository,
                bothTransactionsLoadedAccounts
        );
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        List<TransferAttempt> attempts;
        try {
            Callable<TransferAttempt> firstTransfer = () -> optimisticTransfer.transfer(
                    sourceAccount.getId(),
                    firstDestinationAccount.getId(),
                    6_000L
            );
            Callable<TransferAttempt> secondTransfer = () -> optimisticTransfer.transfer(
                    sourceAccount.getId(),
                    secondDestinationAccount.getId(),
                    6_000L
            );

            Future<TransferAttempt> firstAttempt = executorService.submit(firstTransfer);
            Future<TransferAttempt> secondAttempt = executorService.submit(secondTransfer);
            attempts = List.of(
                    firstAttempt.get(10, TimeUnit.SECONDS),
                    secondAttempt.get(10, TimeUnit.SECONDS)
            );
        } finally {
            executorService.shutdownNow();
        }

        assertThat(attempts)
                .containsExactlyInAnyOrder(TransferAttempt.SUCCESS, TransferAttempt.OPTIMISTIC_LOCK_ROLLBACK);
        assertThat(accountRepository.findById(sourceAccount.getId()).orElseThrow().getBalance()).isEqualTo(4_000L);
        long firstDestinationBalance =
                accountRepository.findById(firstDestinationAccount.getId()).orElseThrow().getBalance();
        long secondDestinationBalance =
                accountRepository.findById(secondDestinationAccount.getId()).orElseThrow().getBalance();
        assertThat(firstDestinationBalance + secondDestinationBalance).isEqualTo(6_000L);
        assertThat(reconciliationService.findAccountBalanceMismatches())
                .filteredOn(result -> result.accountId().equals(sourceAccount.getId())
                        || result.accountId().equals(firstDestinationAccount.getId())
                        || result.accountId().equals(secondDestinationAccount.getId()))
                .isEmpty();
    }

    private Account createZeroBalanceAccount() {
        long sequence = ACCOUNT_SEQUENCE.incrementAndGet();
        Customer customer = customerRepository.saveAndFlush(new Customer("Optimistic Race Customer " + sequence));
        return accountRepository.saveAndFlush(new Account(customer, "800-000-" + sequence));
    }

    private enum TransferAttempt {
        SUCCESS,
        OPTIMISTIC_LOCK_ROLLBACK
    }

    private static class OptimisticTransferExperiment {

        private final TransactionTemplate transactionTemplate;
        private final AccountRepository accountRepository;
        private final FinancialTransactionRepository financialTransactionRepository;
        private final AccountJournalEntryRepository accountJournalEntryRepository;
        private final CountDownLatch bothTransactionsLoadedAccounts;

        private OptimisticTransferExperiment(
                PlatformTransactionManager transactionManager,
                AccountRepository accountRepository,
                FinancialTransactionRepository financialTransactionRepository,
                AccountJournalEntryRepository accountJournalEntryRepository,
                CountDownLatch bothTransactionsLoadedAccounts
        ) {
            this.transactionTemplate = new TransactionTemplate(transactionManager);
            this.accountRepository = accountRepository;
            this.financialTransactionRepository = financialTransactionRepository;
            this.accountJournalEntryRepository = accountJournalEntryRepository;
            this.bothTransactionsLoadedAccounts = bothTransactionsLoadedAccounts;
        }

        private TransferAttempt transfer(Long sourceAccountId, Long destinationAccountId, long amount) {
            try {
                transactionTemplate.executeWithoutResult(status -> transferInsideTransaction(
                        sourceAccountId,
                        destinationAccountId,
                        amount
                ));
                return TransferAttempt.SUCCESS;
            } catch (OptimisticLockingFailureException exception) {
                return TransferAttempt.OPTIMISTIC_LOCK_ROLLBACK;
            }
        }

        private void transferInsideTransaction(Long sourceAccountId, Long destinationAccountId, long amount) {
            Account sourceAccount = accountRepository.findById(sourceAccountId).orElseThrow();
            Account destinationAccount = accountRepository.findById(destinationAccountId).orElseThrow();
            awaitOtherTransactionAfterVersionedRead();

            sourceAccount.withdraw(amount);
            destinationAccount.deposit(amount);
            FinancialTransaction transaction = financialTransactionRepository.saveAndFlush(
                    new FinancialTransaction(UUID.randomUUID().toString(), TransactionType.INTERNAL_TRANSFER, amount)
            );
            accountJournalEntryRepository.saveAllAndFlush(List.of(
                    new AccountJournalEntry(
                            transaction,
                            1,
                            sourceAccount,
                            JournalMovementType.BALANCE_DECREASE,
                            amount,
                            sourceAccount.getBalance()
                    ),
                    new AccountJournalEntry(
                            transaction,
                            2,
                            destinationAccount,
                            JournalMovementType.BALANCE_INCREASE,
                            amount,
                            destinationAccount.getBalance()
                    )
            ));
        }

        private void awaitOtherTransactionAfterVersionedRead() {
            bothTransactionsLoadedAccounts.countDown();
            try {
                if (!bothTransactionsLoadedAccounts.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for concurrent versioned read.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for concurrent versioned read.", exception);
            }
        }
    }
}
