package com.bankcore.service;

import com.bankcore.domain.Account;
import com.bankcore.domain.Customer;
import com.bankcore.domain.FinancialTransaction;
import com.bankcore.domain.IdempotencyOperation;
import com.bankcore.domain.IdempotencyRecord;
import com.bankcore.repository.AccountJournalEntryRepository;
import com.bankcore.repository.AccountRepository;
import com.bankcore.repository.FinancialTransactionRepository;
import com.bankcore.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransferServiceConcurrencyRetryTest {

    @Test
    void transferInternalIdempotent_shouldRetryTransientConcurrencyFailureBeforeCommitting() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        FinancialTransactionRepository financialTransactionRepository = mock(FinancialTransactionRepository.class);
        AccountJournalEntryRepository accountJournalEntryRepository = mock(AccountJournalEntryRepository.class);
        IdempotencyRecordRepository idempotencyRecordRepository = mock(IdempotencyRecordRepository.class);
        TransferService transferService = new TransferService(
                accountRepository,
                financialTransactionRepository,
                accountJournalEntryRepository,
                idempotencyRecordRepository,
                transactionManager()
        );
        Account sourceAccount = account(1L, 5_000L);
        Account destinationAccount = account(2L, 2_000L);
        when(idempotencyRecordRepository.findByCallerScopeAndOperationAndIdempotencyKeyDigest(
                eq("retry-test"),
                eq(IdempotencyOperation.INTERNAL_TRANSFER),
                any(byte[].class)
        ))
                .thenThrow(new CannotAcquireLockException("temporary lock failure"))
                .thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(destinationAccount));
        when(idempotencyRecordRepository.saveAndFlush(any(IdempotencyRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(financialTransactionRepository.saveAndFlush(any(FinancialTransaction.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 100L));

        InternalTransferResult result = transferService.transferInternalIdempotent(
                "retry-test",
                "same-key",
                1L,
                2L,
                1_000L
        );

        assertThat(result.amount()).isEqualTo(1_000L);
        assertThat(result.sourceBalanceAfter()).isEqualTo(4_000L);
        assertThat(result.destinationBalanceAfter()).isEqualTo(3_000L);
        verify(idempotencyRecordRepository, times(2))
                .findByCallerScopeAndOperationAndIdempotencyKeyDigest(
                        eq("retry-test"),
                        eq(IdempotencyOperation.INTERNAL_TRANSFER),
                        any(byte[].class)
                );
    }

    @Test
    void transferInternalIdempotent_shouldStopRetryingAfterBoundedConcurrencyAttempts() {
        IdempotencyRecordRepository idempotencyRecordRepository = mock(IdempotencyRecordRepository.class);
        TransferService transferService = new TransferService(
                mock(AccountRepository.class),
                mock(FinancialTransactionRepository.class),
                mock(AccountJournalEntryRepository.class),
                idempotencyRecordRepository,
                transactionManager()
        );
        when(idempotencyRecordRepository.findByCallerScopeAndOperationAndIdempotencyKeyDigest(
                eq("retry-test"),
                eq(IdempotencyOperation.INTERNAL_TRANSFER),
                any(byte[].class)
        )).thenThrow(new CannotAcquireLockException("persistent lock failure"));

        assertThatThrownBy(() -> transferService.transferInternalIdempotent(
                "retry-test",
                "same-key",
                1L,
                2L,
                1_000L
        )).isInstanceOf(CannotAcquireLockException.class);

        verify(idempotencyRecordRepository, times(3))
                .findByCallerScopeAndOperationAndIdempotencyKeyDigest(
                        eq("retry-test"),
                        eq(IdempotencyOperation.INTERNAL_TRANSFER),
                        any(byte[].class)
                );
    }

    private static PlatformTransactionManager transactionManager() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(invocation -> new SimpleTransactionStatus());
        return transactionManager;
    }

    private static Account account(Long id, long balance) {
        Account account = new Account(new Customer("Retry Test Customer " + id), "retry-" + id);
        ReflectionTestUtils.setField(account, "id", id);
        ReflectionTestUtils.setField(account, "balance", balance);
        return account;
    }

    private static FinancialTransaction withId(FinancialTransaction transaction, Long id) {
        ReflectionTestUtils.setField(transaction, "id", id);
        return transaction;
    }
}
