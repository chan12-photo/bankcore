package com.bankcore.service;

import com.bankcore.domain.Account;
import com.bankcore.domain.AccountJournalEntry;
import com.bankcore.domain.Customer;
import com.bankcore.domain.IdempotencyStatus;
import com.bankcore.domain.JournalMovementType;
import com.bankcore.exception.IdempotencyKeyConflictException;
import com.bankcore.exception.InsufficientBalanceException;
import com.bankcore.exception.InvalidIdempotencyRequestException;
import com.bankcore.exception.SameAccountTransferException;
import com.bankcore.repository.AccountJournalEntryRepository;
import com.bankcore.repository.AccountRepository;
import com.bankcore.repository.CustomerRepository;
import com.bankcore.repository.FinancialTransactionRepository;
import com.bankcore.repository.IdempotencyRecordRepository;
import com.bankcore.support.MySqlContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@ImportTestcontainers(MySqlContainerSupport.class)
class TransferServiceIntegrationTest {

    private static final AtomicLong ACCOUNT_SEQUENCE = new AtomicLong(10_000);

    @Autowired
    private TransferService transferService;

    @Autowired
    private ControlledFundingService controlledFundingService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    @Autowired
    private AccountJournalEntryRepository accountJournalEntryRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Test
    void transferInternal_shouldMoveMoneyAndRecordBalancedJournalEntries() {
        Account sourceAccount = createAccountWithBalance(10_000L);
        Account destinationAccount = createAccountWithBalance(2_000L);
        long transactionCountBefore = financialTransactionRepository.count();
        long journalCountBefore = accountJournalEntryRepository.count();

        InternalTransferResult result = transferService.transferInternal(
                sourceAccount.getId(),
                destinationAccount.getId(),
                3_000L
        );

        assertThat(result.transactionId()).isNotNull();
        assertThat(result.transactionKey()).hasSize(36);
        assertThat(result.sourceBalanceAfter()).isEqualTo(7_000L);
        assertThat(result.destinationBalanceAfter()).isEqualTo(5_000L);
        assertThat(financialTransactionRepository.count()).isEqualTo(transactionCountBefore + 1);
        assertThat(accountJournalEntryRepository.count()).isEqualTo(journalCountBefore + 2);

        assertThat(accountRepository.findById(sourceAccount.getId()).orElseThrow().getBalance()).isEqualTo(7_000L);
        assertThat(accountRepository.findById(destinationAccount.getId()).orElseThrow().getBalance()).isEqualTo(5_000L);

        List<AccountJournalEntry> entries =
                accountJournalEntryRepository.findByTransactionIdOrderByEntryNo(result.transactionId());
        assertThat(entries)
                .extracting(
                        AccountJournalEntry::getEntryNo,
                        AccountJournalEntry::getMovementType,
                        AccountJournalEntry::getAmount,
                        AccountJournalEntry::getBalanceAfter
                )
                .containsExactly(
                        tuple(1, JournalMovementType.BALANCE_DECREASE, 3_000L, 7_000L),
                        tuple(2, JournalMovementType.BALANCE_INCREASE, 3_000L, 5_000L)
                );
    }

    @Test
    void transferInternal_shouldRollbackBalanceAndJournalRows_whenFailureOccursAfterFlush() {
        Account sourceAccount = createAccountWithBalance(10_000L);
        Account destinationAccount = createAccountWithBalance(2_000L);
        long transactionCountBefore = financialTransactionRepository.count();
        long journalCountBefore = accountJournalEntryRepository.count();

        assertThatThrownBy(() -> transferService.transferInternal(
                sourceAccount.getId(),
                destinationAccount.getId(),
                3_000L,
                TransferFailurePoint.AFTER_JOURNAL_FLUSH
        )).isInstanceOf(TransferFaultInjectedException.class);

        assertThat(accountRepository.findById(sourceAccount.getId()).orElseThrow().getBalance()).isEqualTo(10_000L);
        assertThat(accountRepository.findById(destinationAccount.getId()).orElseThrow().getBalance()).isEqualTo(2_000L);
        assertThat(financialTransactionRepository.count()).isEqualTo(transactionCountBefore);
        assertThat(accountJournalEntryRepository.count()).isEqualTo(journalCountBefore);
    }

    @Test
    void transferInternal_shouldRollbackSourceBalance_whenFailureOccursAfterSourceWithdrawal() {
        Account sourceAccount = createAccountWithBalance(10_000L);
        Account destinationAccount = createAccountWithBalance(2_000L);

        assertThatThrownBy(() -> transferService.transferInternal(
                sourceAccount.getId(),
                destinationAccount.getId(),
                3_000L,
                TransferFailurePoint.AFTER_SOURCE_WITHDRAWAL
        )).isInstanceOf(TransferFaultInjectedException.class);

        assertThat(accountRepository.findById(sourceAccount.getId()).orElseThrow().getBalance()).isEqualTo(10_000L);
        assertThat(accountRepository.findById(destinationAccount.getId()).orElseThrow().getBalance()).isEqualTo(2_000L);
    }

    @Test
    void transferInternal_shouldRejectSameSourceAndDestinationAccount() {
        Account account = createAccountWithBalance(10_000L);

        assertThatThrownBy(() -> transferService.transferInternal(account.getId(), account.getId(), 1_000L))
                .isInstanceOf(SameAccountTransferException.class);
    }

    @Test
    void transferInternal_shouldRejectInsufficientBalance() {
        Account sourceAccount = createAccountWithBalance(500L);
        Account destinationAccount = createAccountWithBalance(2_000L);

        assertThatThrownBy(() -> transferService.transferInternal(
                sourceAccount.getId(),
                destinationAccount.getId(),
                1_000L
        )).isInstanceOf(InsufficientBalanceException.class);
    }

    @Test
    void transferInternalIdempotent_shouldReplayCommittedResult_whenSameRequestIsRetried() {
        Account sourceAccount = createAccountWithBalance(10_000L);
        Account destinationAccount = createAccountWithBalance(2_000L);
        long transactionCountBefore = financialTransactionRepository.count();
        long journalCountBefore = accountJournalEntryRepository.count();
        long idempotencyCountBefore = idempotencyRecordRepository.count();
        String idempotencyKey = nextIdempotencyKey();

        InternalTransferResult firstResult = transferService.transferInternalIdempotent(
                "integration-test",
                idempotencyKey,
                sourceAccount.getId(),
                destinationAccount.getId(),
                3_000L
        );
        InternalTransferResult replayedResult = transferService.transferInternalIdempotent(
                "integration-test",
                idempotencyKey,
                sourceAccount.getId(),
                destinationAccount.getId(),
                3_000L
        );

        assertThat(replayedResult).isEqualTo(firstResult);
        assertThat(accountRepository.findById(sourceAccount.getId()).orElseThrow().getBalance()).isEqualTo(7_000L);
        assertThat(accountRepository.findById(destinationAccount.getId()).orElseThrow().getBalance()).isEqualTo(5_000L);
        assertThat(financialTransactionRepository.count()).isEqualTo(transactionCountBefore + 1);
        assertThat(accountJournalEntryRepository.count()).isEqualTo(journalCountBefore + 2);
        assertThat(idempotencyRecordRepository.count()).isEqualTo(idempotencyCountBefore + 1);
        assertThat(idempotencyRecordRepository.findAll())
                .filteredOn(record -> record.getIdempotencyKey().equals(idempotencyKey))
                .singleElement()
                .extracting("status")
                .isEqualTo(IdempotencyStatus.COMPLETED);
    }

    @Test
    void transferInternalIdempotent_shouldApplyMoneyEffectOnce_whenSameRequestArrivesConcurrently()
            throws Exception {
        Account sourceAccount = createAccountWithBalance(10_000L);
        Account destinationAccount = createAccountWithBalance(2_000L);
        String idempotencyKey = nextIdempotencyKey();
        long transactionCountBefore = financialTransactionRepository.count();
        long journalCountBefore = accountJournalEntryRepository.count();
        long idempotencyCountBefore = idempotencyRecordRepository.count();
        int requestCount = 50;
        CountDownLatch startSignal = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(16);

        List<InternalTransferResult> results;
        try {
            List<Callable<InternalTransferResult>> tasks = IntStream.range(0, requestCount)
                    .mapToObj(index -> (Callable<InternalTransferResult>) () -> {
                        assertThat(startSignal.await(5, TimeUnit.SECONDS)).isTrue();
                        return transferService.transferInternalIdempotent(
                                "integration-test",
                                idempotencyKey,
                                sourceAccount.getId(),
                                destinationAccount.getId(),
                                3_000L
                        );
                    })
                    .toList();
            List<Future<InternalTransferResult>> futures = tasks.stream()
                    .map(executorService::submit)
                    .toList();

            startSignal.countDown();
            results = futures.stream()
                    .map(future -> getFuture(future, 10, TimeUnit.SECONDS))
                    .toList();
        } finally {
            executorService.shutdownNow();
        }

        Set<String> transactionKeys = results.stream()
                .map(InternalTransferResult::transactionKey)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(results).hasSize(requestCount);
        assertThat(transactionKeys).hasSize(1);
        assertThat(accountRepository.findById(sourceAccount.getId()).orElseThrow().getBalance()).isEqualTo(7_000L);
        assertThat(accountRepository.findById(destinationAccount.getId()).orElseThrow().getBalance()).isEqualTo(5_000L);
        assertThat(financialTransactionRepository.count()).isEqualTo(transactionCountBefore + 1);
        assertThat(accountJournalEntryRepository.count()).isEqualTo(journalCountBefore + 2);
        assertThat(idempotencyRecordRepository.count()).isEqualTo(idempotencyCountBefore + 1);
    }

    @Test
    void transferInternalIdempotent_shouldRejectSameKeyWithDifferentFingerprint() {
        Account sourceAccount = createAccountWithBalance(10_000L);
        Account destinationAccount = createAccountWithBalance(2_000L);
        String idempotencyKey = nextIdempotencyKey();

        transferService.transferInternalIdempotent(
                "integration-test",
                idempotencyKey,
                sourceAccount.getId(),
                destinationAccount.getId(),
                3_000L
        );

        assertThatThrownBy(() -> transferService.transferInternalIdempotent(
                "integration-test",
                idempotencyKey,
                sourceAccount.getId(),
                destinationAccount.getId(),
                4_000L
        )).isInstanceOf(IdempotencyKeyConflictException.class);

        assertThat(accountRepository.findById(sourceAccount.getId()).orElseThrow().getBalance()).isEqualTo(7_000L);
        assertThat(accountRepository.findById(destinationAccount.getId()).orElseThrow().getBalance()).isEqualTo(5_000L);
    }

    @Test
    void transferInternalIdempotent_shouldRollbackIdempotencyRecord_whenTransferFails() {
        Account sourceAccount = createAccountWithBalance(10_000L);
        Account destinationAccount = createAccountWithBalance(2_000L);
        long transactionCountBefore = financialTransactionRepository.count();
        long journalCountBefore = accountJournalEntryRepository.count();
        long idempotencyCountBefore = idempotencyRecordRepository.count();

        assertThatThrownBy(() -> transferService.transferInternalIdempotent(
                "integration-test",
                nextIdempotencyKey(),
                sourceAccount.getId(),
                destinationAccount.getId(),
                3_000L,
                TransferFailurePoint.AFTER_JOURNAL_FLUSH
        )).isInstanceOf(TransferFaultInjectedException.class);

        assertThat(accountRepository.findById(sourceAccount.getId()).orElseThrow().getBalance()).isEqualTo(10_000L);
        assertThat(accountRepository.findById(destinationAccount.getId()).orElseThrow().getBalance()).isEqualTo(2_000L);
        assertThat(financialTransactionRepository.count()).isEqualTo(transactionCountBefore);
        assertThat(accountJournalEntryRepository.count()).isEqualTo(journalCountBefore);
        assertThat(idempotencyRecordRepository.count()).isEqualTo(idempotencyCountBefore);
    }

    @Test
    void transferInternalIdempotent_shouldRejectBlankIdempotencyKey() {
        Account sourceAccount = createAccountWithBalance(10_000L);
        Account destinationAccount = createAccountWithBalance(2_000L);

        assertThatThrownBy(() -> transferService.transferInternalIdempotent(
                "integration-test",
                " ",
                sourceAccount.getId(),
                destinationAccount.getId(),
                3_000L
        )).isInstanceOf(InvalidIdempotencyRequestException.class);
    }

    private Account createAccountWithBalance(long balance) {
        long sequence = ACCOUNT_SEQUENCE.incrementAndGet();
        Customer customer = customerRepository.saveAndFlush(new Customer("Transfer Customer " + sequence));
        Account account = new Account(customer, "200-000-" + sequence);
        Account savedAccount = accountRepository.saveAndFlush(account);
        if (balance > 0) {
            controlledFundingService.seedFunds(savedAccount.getId(), balance);
        }
        return savedAccount;
    }

    private static String nextIdempotencyKey() {
        return "idem-" + ACCOUNT_SEQUENCE.incrementAndGet();
    }

    private static <T> T getFuture(Future<T> future, long timeout, TimeUnit unit) {
        try {
            return future.get(timeout, unit);
        } catch (Exception exception) {
            throw new AssertionError("Timed out or failed while waiting for concurrent transfer.", exception);
        }
    }
}
