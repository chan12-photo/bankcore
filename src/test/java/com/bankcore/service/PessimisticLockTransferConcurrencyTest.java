package com.bankcore.service;

import com.bankcore.domain.Account;
import com.bankcore.domain.AccountJournalEntry;
import com.bankcore.domain.Customer;
import com.bankcore.domain.FinancialTransaction;
import com.bankcore.domain.JournalMovementType;
import com.bankcore.domain.TransactionType;
import com.bankcore.exception.AccountNotFoundException;
import com.bankcore.exception.InsufficientBalanceException;
import com.bankcore.repository.AccountJournalEntryRepository;
import com.bankcore.repository.AccountRepository;
import com.bankcore.repository.CustomerRepository;
import com.bankcore.repository.FinancialTransactionRepository;
import com.bankcore.support.MySqlContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
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
class PessimisticLockTransferConcurrencyTest {

    private static final AtomicLong ACCOUNT_SEQUENCE = new AtomicLong(90_000);

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
    void pessimisticWriteLock_shouldSerializeConcurrentTransfersAndMakeSecondSeeLatestBalance()
            throws Exception {
        Account sourceAccount = createZeroBalanceAccount();
        Account firstDestinationAccount = createZeroBalanceAccount();
        Account secondDestinationAccount = createZeroBalanceAccount();
        controlledFundingService.seedFunds(sourceAccount.getId(), 10_000L);
        CountDownLatch firstTransactionLockedSource = new CountDownLatch(1);
        CountDownLatch allowFirstTransactionToCommit = new CountDownLatch(1);
        PessimisticTransferExperiment pessimisticTransfer = new PessimisticTransferExperiment(
                transactionManager,
                accountRepository,
                financialTransactionRepository,
                accountJournalEntryRepository
        );
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        TransferAttempt firstAttempt;
        TransferAttempt secondAttempt;
        try {
            Callable<TransferAttempt> firstTransfer = () -> pessimisticTransfer.transfer(
                    sourceAccount.getId(),
                    firstDestinationAccount.getId(),
                    6_000L,
                    firstTransactionLockedSource,
                    allowFirstTransactionToCommit
            );
            Future<TransferAttempt> firstFuture = executorService.submit(firstTransfer);
            assertThat(firstTransactionLockedSource.await(5, TimeUnit.SECONDS)).isTrue();

            Future<TransferAttempt> secondFuture = executorService.submit(() -> pessimisticTransfer.transfer(
                    sourceAccount.getId(),
                    secondDestinationAccount.getId(),
                    6_000L
            ));

            allowFirstTransactionToCommit.countDown();
            firstAttempt = firstFuture.get(10, TimeUnit.SECONDS);
            secondAttempt = secondFuture.get(10, TimeUnit.SECONDS);
        } finally {
            executorService.shutdownNow();
        }

        assertThat(List.of(firstAttempt, secondAttempt))
                .containsExactlyInAnyOrder(TransferAttempt.SUCCESS, TransferAttempt.INSUFFICIENT_BALANCE_ROLLBACK);
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
        Customer customer = customerRepository.saveAndFlush(new Customer("Pessimistic Race Customer " + sequence));
        return accountRepository.saveAndFlush(new Account(customer, "900-000-" + sequence));
    }

    private enum TransferAttempt {
        SUCCESS,
        INSUFFICIENT_BALANCE_ROLLBACK
    }

    private static class PessimisticTransferExperiment {

        private final TransactionTemplate transactionTemplate;
        private final AccountRepository accountRepository;
        private final FinancialTransactionRepository financialTransactionRepository;
        private final AccountJournalEntryRepository accountJournalEntryRepository;

        private PessimisticTransferExperiment(
                PlatformTransactionManager transactionManager,
                AccountRepository accountRepository,
                FinancialTransactionRepository financialTransactionRepository,
                AccountJournalEntryRepository accountJournalEntryRepository
        ) {
            this.transactionTemplate = new TransactionTemplate(transactionManager);
            this.accountRepository = accountRepository;
            this.financialTransactionRepository = financialTransactionRepository;
            this.accountJournalEntryRepository = accountJournalEntryRepository;
        }

        private TransferAttempt transfer(Long sourceAccountId, Long destinationAccountId, long amount) {
            return transfer(sourceAccountId, destinationAccountId, amount, null, null);
        }

        private TransferAttempt transfer(
                Long sourceAccountId,
                Long destinationAccountId,
                long amount,
                CountDownLatch sourceLocked,
                CountDownLatch continueAfterSourceLock
        ) {
            try {
                transactionTemplate.executeWithoutResult(status -> transferInsideTransaction(
                        sourceAccountId,
                        destinationAccountId,
                        amount,
                        sourceLocked,
                        continueAfterSourceLock
                ));
                return TransferAttempt.SUCCESS;
            } catch (InsufficientBalanceException exception) {
                return TransferAttempt.INSUFFICIENT_BALANCE_ROLLBACK;
            }
        }

        private void transferInsideTransaction(
                Long sourceAccountId,
                Long destinationAccountId,
                long amount,
                CountDownLatch sourceLocked,
                CountDownLatch continueAfterSourceLock
        ) {
            Account sourceAccount = lockAccount(sourceAccountId);
            signalAndWaitIfRequested(sourceLocked, continueAfterSourceLock);
            Account destinationAccount = lockAccount(destinationAccountId);

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

        private Account lockAccount(Long accountId) {
            return accountRepository.findByIdForUpdate(accountId)
                    .orElseThrow(() -> new AccountNotFoundException(accountId));
        }

        private void signalAndWaitIfRequested(CountDownLatch sourceLocked, CountDownLatch continueAfterSourceLock) {
            if (sourceLocked == null || continueAfterSourceLock == null) {
                return;
            }
            sourceLocked.countDown();
            try {
                if (!continueAfterSourceLock.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out while holding pessimistic source lock.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while holding pessimistic source lock.", exception);
            }
        }
    }
}
