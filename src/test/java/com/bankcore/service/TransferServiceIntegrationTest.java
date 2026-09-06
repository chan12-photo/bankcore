package com.bankcore.service;

import com.bankcore.domain.Account;
import com.bankcore.domain.AccountJournalEntry;
import com.bankcore.domain.Customer;
import com.bankcore.domain.FinancialTransaction;
import com.bankcore.domain.IdempotencyKeyDigest;
import com.bankcore.domain.IdempotencyOperation;
import com.bankcore.domain.IdempotencyRecord;
import com.bankcore.domain.IdempotencyStatus;
import com.bankcore.domain.JournalMovementType;
import com.bankcore.domain.TransactionType;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.Transactional;

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

    @Autowired
    private AmbientTransferCaller ambientTransferCaller;

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
                .filteredOn(record -> record.getCallerScope().equals("integration-test"))
                .filteredOn(record -> record.getOperation() == IdempotencyOperation.INTERNAL_TRANSFER)
                .filteredOn(record -> record.getRequestFingerprint().equals(TransferRequestFingerprint.internalTransfer(
                        sourceAccount.getId(),
                        destinationAccount.getId(),
                        3_000L
                )))
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
                    assertThat(record.getIdempotencyKeyDigest()).isEqualTo(IdempotencyKeyDigest.of(
                            "integration-test",
                            IdempotencyOperation.INTERNAL_TRANSFER,
                            idempotencyKey
                    ));
                });
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
    void transferInternalIdempotent_shouldRejectConcurrentSameKeyWithDifferentFingerprint()
            throws Exception {
        Account sourceAccount = createAccountWithBalance(10_000L);
        Account destinationAccount = createAccountWithBalance(2_000L);
        String idempotencyKey = nextIdempotencyKey();
        long transactionCountBefore = financialTransactionRepository.count();
        long journalCountBefore = accountJournalEntryRepository.count();
        long idempotencyCountBefore = idempotencyRecordRepository.count();
        CountDownLatch startSignal = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        List<TransferCallOutcome> outcomes;
        try {
            List<Callable<TransferCallOutcome>> tasks = List.of(
                    () -> callTransferAndCaptureOutcome(
                            startSignal,
                            idempotencyKey,
                            sourceAccount.getId(),
                            destinationAccount.getId(),
                            3_000L
                    ),
                    () -> callTransferAndCaptureOutcome(
                            startSignal,
                            idempotencyKey,
                            sourceAccount.getId(),
                            destinationAccount.getId(),
                            4_000L
                    )
            );
            List<Future<TransferCallOutcome>> futures = tasks.stream()
                    .map(executorService::submit)
                    .toList();

            startSignal.countDown();
            outcomes = futures.stream()
                    .map(future -> getFuture(future, 10, TimeUnit.SECONDS))
                    .toList();
        } finally {
            executorService.shutdownNow();
        }

        List<TransferCallOutcome> successes = outcomes.stream()
                .filter(TransferCallOutcome::isSuccess)
                .toList();
        List<TransferCallOutcome> conflicts = outcomes.stream()
                .filter(outcome -> outcome.failure() instanceof IdempotencyKeyConflictException)
                .toList();

        assertThat(successes).hasSize(1);
        assertThat(conflicts).hasSize(1);
        long committedAmount = successes.get(0).result().amount();
        assertThat(committedAmount).isIn(3_000L, 4_000L);
        assertThat(accountRepository.findById(sourceAccount.getId()).orElseThrow().getBalance())
                .isEqualTo(10_000L - committedAmount);
        assertThat(accountRepository.findById(destinationAccount.getId()).orElseThrow().getBalance())
                .isEqualTo(2_000L + committedAmount);
        assertThat(financialTransactionRepository.count()).isEqualTo(transactionCountBefore + 1);
        assertThat(accountJournalEntryRepository.count()).isEqualTo(journalCountBefore + 2);
        assertThat(idempotencyRecordRepository.count()).isEqualTo(idempotencyCountBefore + 1);
    }

    @Test
    void transferInternalIdempotent_shouldSerializeOppositeDirectionTransfersWithOrderedLocks()
            throws Exception {
        Account firstAccount = createAccountWithBalance(10_000L);
        Account secondAccount = createAccountWithBalance(10_000L);
        long transactionCountBefore = financialTransactionRepository.count();
        long journalCountBefore = accountJournalEntryRepository.count();
        long idempotencyCountBefore = idempotencyRecordRepository.count();
        CountDownLatch startSignal = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        List<InternalTransferResult> results;
        try {
            List<Callable<InternalTransferResult>> tasks = List.of(
                    () -> {
                        assertThat(startSignal.await(5, TimeUnit.SECONDS)).isTrue();
                        return transferService.transferInternalIdempotent(
                                "integration-test",
                                nextIdempotencyKey(),
                                firstAccount.getId(),
                                secondAccount.getId(),
                                3_000L
                        );
                    },
                    () -> {
                        assertThat(startSignal.await(5, TimeUnit.SECONDS)).isTrue();
                        return transferService.transferInternalIdempotent(
                                "integration-test",
                                nextIdempotencyKey(),
                                secondAccount.getId(),
                                firstAccount.getId(),
                                4_000L
                        );
                    }
            );
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

        assertThat(results).hasSize(2);
        assertThat(accountRepository.findById(firstAccount.getId()).orElseThrow().getBalance()).isEqualTo(11_000L);
        assertThat(accountRepository.findById(secondAccount.getId()).orElseThrow().getBalance()).isEqualTo(9_000L);
        assertThat(financialTransactionRepository.count()).isEqualTo(transactionCountBefore + 2);
        assertThat(accountJournalEntryRepository.count()).isEqualTo(journalCountBefore + 4);
        assertThat(idempotencyRecordRepository.count()).isEqualTo(idempotencyCountBefore + 2);
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

    @Test
    void transferInternalIdempotent_shouldTreatIdempotencyKeysAsCaseSensitive() {
        Account sourceAccount = createAccountWithBalance(10_000L);
        Account destinationAccount = createAccountWithBalance(2_000L);
        long transactionCountBefore = financialTransactionRepository.count();
        long journalCountBefore = accountJournalEntryRepository.count();
        long idempotencyCountBefore = idempotencyRecordRepository.count();

        transferService.transferInternalIdempotent(
                "integration-test",
                "Case-Sensitive-Key",
                sourceAccount.getId(),
                destinationAccount.getId(),
                1_000L
        );
        transferService.transferInternalIdempotent(
                "integration-test",
                "case-sensitive-key",
                sourceAccount.getId(),
                destinationAccount.getId(),
                1_000L
        );

        assertThat(accountRepository.findById(sourceAccount.getId()).orElseThrow().getBalance()).isEqualTo(8_000L);
        assertThat(accountRepository.findById(destinationAccount.getId()).orElseThrow().getBalance()).isEqualTo(4_000L);
        assertThat(financialTransactionRepository.count()).isEqualTo(transactionCountBefore + 2);
        assertThat(accountJournalEntryRepository.count()).isEqualTo(journalCountBefore + 4);
        assertThat(idempotencyRecordRepository.count()).isEqualTo(idempotencyCountBefore + 2);
    }

    @Test
    void transferInternalIdempotent_shouldReplayConcurrentSameKeySafelyFromAmbientTransactionalCaller()
            throws Exception {
        Account sourceAccount = createAccountWithBalance(10_000L);
        Account destinationAccount = createAccountWithBalance(2_000L);
        String idempotencyKey = nextIdempotencyKey();
        long transactionCountBefore = financialTransactionRepository.count();
        long journalCountBefore = accountJournalEntryRepository.count();
        long idempotencyCountBefore = idempotencyRecordRepository.count();
        CountDownLatch startSignal = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        List<InternalTransferResult> results;
        try {
            List<Callable<InternalTransferResult>> tasks = IntStream.range(0, 2)
                    .mapToObj(index -> (Callable<InternalTransferResult>) () -> {
                        assertThat(startSignal.await(5, TimeUnit.SECONDS)).isTrue();
                        return ambientTransferCaller.transfer(
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

        assertThat(results).hasSize(2);
        assertThat(results.stream().map(InternalTransferResult::transactionKey).collect(java.util.stream.Collectors.toSet()))
                .hasSize(1);
        assertThat(accountRepository.findById(sourceAccount.getId()).orElseThrow().getBalance()).isEqualTo(7_000L);
        assertThat(accountRepository.findById(destinationAccount.getId()).orElseThrow().getBalance()).isEqualTo(5_000L);
        assertThat(financialTransactionRepository.count()).isEqualTo(transactionCountBefore + 1);
        assertThat(accountJournalEntryRepository.count()).isEqualTo(journalCountBefore + 2);
        assertThat(idempotencyRecordRepository.count()).isEqualTo(idempotencyCountBefore + 1);
    }

    @Test
    void transferInternalIdempotent_shouldRejectReplayWhenStoredJournalPairHasWrongDirection() {
        Account sourceAccount = createAccountWithBalance(0L);
        Account destinationAccount = createAccountWithBalance(0L);
        String idempotencyKey = nextIdempotencyKey();
        Long amount = 1_000L;
        FinancialTransaction transaction = financialTransactionRepository.saveAndFlush(
                new FinancialTransaction(nextTransactionKey(), TransactionType.INTERNAL_TRANSFER, amount)
        );
        accountJournalEntryRepository.saveAllAndFlush(List.of(
                new AccountJournalEntry(
                        transaction,
                        1,
                        sourceAccount,
                        JournalMovementType.BALANCE_INCREASE,
                        amount,
                        amount
                ),
                new AccountJournalEntry(
                        transaction,
                        2,
                        destinationAccount,
                        JournalMovementType.BALANCE_DECREASE,
                        amount,
                        0L
                )
        ));
        IdempotencyRecord record = new IdempotencyRecord(
                "integration-test",
                IdempotencyOperation.INTERNAL_TRANSFER,
                IdempotencyKeyDigest.of("integration-test", IdempotencyOperation.INTERNAL_TRANSFER, idempotencyKey),
                TransferRequestFingerprint.internalTransfer(sourceAccount.getId(), destinationAccount.getId(), amount)
        );
        record.complete(transaction);
        idempotencyRecordRepository.saveAndFlush(record);

        assertThatThrownBy(() -> transferService.transferInternalIdempotent(
                "integration-test",
                idempotencyKey,
                sourceAccount.getId(),
                destinationAccount.getId(),
                amount
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(TransactionJournalInvariant.ENTRY_DIRECTION_ORDER);
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

    private static String nextTransactionKey() {
        return "tx-" + ACCOUNT_SEQUENCE.incrementAndGet();
    }

    private TransferCallOutcome callTransferAndCaptureOutcome(
            CountDownLatch startSignal,
            String idempotencyKey,
            Long sourceAccountId,
            Long destinationAccountId,
            Long amount
    ) {
        try {
            assertThat(startSignal.await(5, TimeUnit.SECONDS)).isTrue();
            return TransferCallOutcome.success(transferService.transferInternalIdempotent(
                    "integration-test",
                    idempotencyKey,
                    sourceAccountId,
                    destinationAccountId,
                    amount
            ));
        } catch (RuntimeException exception) {
            return TransferCallOutcome.failure(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for concurrent transfer.", exception);
        }
    }

    private static <T> T getFuture(Future<T> future, long timeout, TimeUnit unit) {
        try {
            return future.get(timeout, unit);
        } catch (Exception exception) {
            throw new AssertionError("Timed out or failed while waiting for concurrent transfer.", exception);
        }
    }

    private record TransferCallOutcome(
            InternalTransferResult result,
            RuntimeException failure
    ) {

        private static TransferCallOutcome success(InternalTransferResult result) {
            return new TransferCallOutcome(result, null);
        }

        private static TransferCallOutcome failure(RuntimeException failure) {
            return new TransferCallOutcome(null, failure);
        }

        private boolean isSuccess() {
            return result != null;
        }
    }

    @TestConfiguration
    static class AmbientTransferCallerConfiguration {

        @Bean
        AmbientTransferCaller ambientTransferCaller(TransferService transferService) {
            return new AmbientTransferCaller(transferService);
        }
    }

    static class AmbientTransferCaller {

        private final TransferService transferService;

        AmbientTransferCaller(TransferService transferService) {
            this.transferService = transferService;
        }

        @Transactional
        InternalTransferResult transfer(
                String idempotencyKey,
                Long sourceAccountId,
                Long destinationAccountId,
                Long amount
        ) {
            return transferService.transferInternalIdempotent(
                    "ambient-integration-test",
                    idempotencyKey,
                    sourceAccountId,
                    destinationAccountId,
                    amount
            );
        }
    }
}
