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
import org.springframework.jdbc.core.JdbcTemplate;
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
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@ImportTestcontainers(MySqlContainerSupport.class)
class UnsafeNoLockTransferRaceConditionTest {

    private static final AtomicLong ACCOUNT_SEQUENCE = new AtomicLong(70_000);

    @Autowired
    private ControlledFundingService controlledFundingService;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void unsafeNoLockTransfer_shouldCreateReconciliationMismatch_whenTwoTransfersUseSameStaleBalance()
            throws Exception {
        Account sourceAccount = createZeroBalanceAccount();
        Account destinationAccount = createZeroBalanceAccount();
        controlledFundingService.seedFunds(sourceAccount.getId(), 10_000L);
        CountDownLatch bothTransactionsReadBalances = new CountDownLatch(2);
        UnsafeNoLockTransferExperiment unsafeTransfer =
                new UnsafeNoLockTransferExperiment(jdbcTemplate, transactionManager, bothTransactionsReadBalances);
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        try {
            Callable<Void> transferTask = () -> {
                unsafeTransfer.transfer(sourceAccount.getId(), destinationAccount.getId(), 6_000L);
                return null;
            };

            Future<Void> firstTransfer = executorService.submit(transferTask);
            Future<Void> secondTransfer = executorService.submit(transferTask);

            firstTransfer.get(10, TimeUnit.SECONDS);
            secondTransfer.get(10, TimeUnit.SECONDS);
        } finally {
            executorService.shutdownNow();
        }

        assertThat(accountRepository.findById(sourceAccount.getId()).orElseThrow().getBalance()).isEqualTo(4_000L);
        assertThat(accountRepository.findById(destinationAccount.getId()).orElseThrow().getBalance()).isEqualTo(6_000L);
        assertThat(reconciliationService.findAccountBalanceMismatches())
                .filteredOn(result -> result.accountId().equals(sourceAccount.getId())
                        || result.accountId().equals(destinationAccount.getId()))
                .extracting(
                        AccountBalanceReconciliationResult::accountId,
                        AccountBalanceReconciliationResult::storedBalance,
                        AccountBalanceReconciliationResult::journalBalance,
                        AccountBalanceReconciliationResult::difference
                )
                .containsExactlyInAnyOrder(
                        tuple(sourceAccount.getId(), 4_000L, -2_000L, 6_000L),
                        tuple(destinationAccount.getId(), 6_000L, 12_000L, -6_000L)
                );
    }

    private Account createZeroBalanceAccount() {
        long sequence = ACCOUNT_SEQUENCE.incrementAndGet();
        Customer customer = customerRepository.saveAndFlush(new Customer("Unsafe Race Customer " + sequence));
        return accountRepository.saveAndFlush(new Account(customer, "700-000-" + sequence));
    }

    private static class UnsafeNoLockTransferExperiment {

        private final JdbcTemplate jdbcTemplate;
        private final TransactionTemplate transactionTemplate;
        private final CountDownLatch bothTransactionsReadBalances;

        private UnsafeNoLockTransferExperiment(
                JdbcTemplate jdbcTemplate,
                PlatformTransactionManager transactionManager,
                CountDownLatch bothTransactionsReadBalances
        ) {
            this.jdbcTemplate = jdbcTemplate;
            this.transactionTemplate = new TransactionTemplate(transactionManager);
            this.bothTransactionsReadBalances = bothTransactionsReadBalances;
        }

        private void transfer(Long sourceAccountId, Long destinationAccountId, long amount) {
            transactionTemplate.executeWithoutResult(status -> {
                long sourceBalance = selectBalance(sourceAccountId);
                long destinationBalance = selectBalance(destinationAccountId);
                awaitOtherTransactionAfterStaleRead();

                if (sourceBalance < amount) {
                    throw new IllegalStateException("Insufficient balance in unsafe experiment.");
                }

                long sourceBalanceAfter = sourceBalance - amount;
                long destinationBalanceAfter = destinationBalance + amount;
                updateBalanceWithoutVersionCheck(sourceAccountId, sourceBalanceAfter);
                updateBalanceWithoutVersionCheck(destinationAccountId, destinationBalanceAfter);
                long transactionId = insertTransaction(amount);
                insertJournalEntries(transactionId, sourceAccountId, destinationAccountId, amount, List.of(
                        sourceBalanceAfter,
                        destinationBalanceAfter
                ));
            });
        }

        private long selectBalance(Long accountId) {
            return jdbcTemplate.queryForObject(
                    "SELECT balance FROM account WHERE id = ?",
                    Long.class,
                    accountId
            );
        }

        private void awaitOtherTransactionAfterStaleRead() {
            bothTransactionsReadBalances.countDown();
            try {
                if (!bothTransactionsReadBalances.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for concurrent stale read.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for concurrent stale read.", exception);
            }
        }

        private void updateBalanceWithoutVersionCheck(Long accountId, long balanceAfter) {
            jdbcTemplate.update("""
                    UPDATE account
                    SET balance = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP(6)
                    WHERE id = ?
                    """, balanceAfter, accountId);
        }

        private long insertTransaction(long amount) {
            jdbcTemplate.update("""
                    INSERT INTO financial_transaction (transaction_key, type, amount)
                    VALUES (?, 'INTERNAL_TRANSFER', ?)
                    """, UUID.randomUUID().toString(), amount);
            return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        }

        private void insertJournalEntries(
                long transactionId,
                Long sourceAccountId,
                Long destinationAccountId,
                long amount,
                List<Long> balancesAfter
        ) {
            jdbcTemplate.update("""
                    INSERT INTO account_journal_entry
                        (transaction_id, entry_no, account_id, movement_type, amount, balance_after)
                    VALUES (?, 1, ?, 'BALANCE_DECREASE', ?, ?)
                    """, transactionId, sourceAccountId, amount, balancesAfter.get(0));
            jdbcTemplate.update("""
                    INSERT INTO account_journal_entry
                        (transaction_id, entry_no, account_id, movement_type, amount, balance_after)
                    VALUES (?, 2, ?, 'BALANCE_INCREASE', ?, ?)
                    """, transactionId, destinationAccountId, amount, balancesAfter.get(1));
        }
    }
}
