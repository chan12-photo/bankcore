package com.bankcore.service;

import com.bankcore.domain.Account;
import com.bankcore.domain.AccountJournalEntry;
import com.bankcore.domain.FinancialTransaction;
import com.bankcore.domain.IdempotencyOperation;
import com.bankcore.domain.IdempotencyRecord;
import com.bankcore.domain.IdempotencyStatus;
import com.bankcore.domain.JournalMovementType;
import com.bankcore.domain.MoneyPolicy;
import com.bankcore.domain.TransactionType;
import com.bankcore.exception.AccountNotFoundException;
import com.bankcore.exception.IdempotencyKeyConflictException;
import com.bankcore.exception.InvalidIdempotencyRequestException;
import com.bankcore.exception.SameAccountTransferException;
import com.bankcore.repository.AccountJournalEntryRepository;
import com.bankcore.repository.AccountRepository;
import com.bankcore.repository.FinancialTransactionRepository;
import com.bankcore.repository.IdempotencyRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final AccountJournalEntryRepository accountJournalEntryRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public TransferService(
            AccountRepository accountRepository,
            FinancialTransactionRepository financialTransactionRepository,
            AccountJournalEntryRepository accountJournalEntryRepository,
            IdempotencyRecordRepository idempotencyRecordRepository
    ) {
        this.accountRepository = accountRepository;
        this.financialTransactionRepository = financialTransactionRepository;
        this.accountJournalEntryRepository = accountJournalEntryRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }

    @Transactional
    public InternalTransferResult transferInternal(Long sourceAccountId, Long destinationAccountId, Long amount) {
        return transferInternal(sourceAccountId, destinationAccountId, amount, TransferFailurePoint.NONE);
    }

    @Transactional
    public InternalTransferResult transferInternal(
            Long sourceAccountId,
            Long destinationAccountId,
            Long amount,
            TransferFailurePoint failurePoint
    ) {
        validateDistinctAccounts(sourceAccountId, destinationAccountId);
        long validAmount = MoneyPolicy.requireValidAmount(amount);
        return performTransfer(sourceAccountId, destinationAccountId, validAmount, failurePoint).toResult();
    }

    @Transactional
    public InternalTransferResult transferInternalIdempotent(
            String callerScope,
            String idempotencyKey,
            Long sourceAccountId,
            Long destinationAccountId,
            Long amount
    ) {
        return transferInternalIdempotent(
                callerScope,
                idempotencyKey,
                sourceAccountId,
                destinationAccountId,
                amount,
                TransferFailurePoint.NONE
        );
    }

    @Transactional
    public InternalTransferResult transferInternalIdempotent(
            String callerScope,
            String idempotencyKey,
            Long sourceAccountId,
            Long destinationAccountId,
            Long amount,
            TransferFailurePoint failurePoint
    ) {
        validateIdempotencyText(callerScope, "callerScope");
        validateIdempotencyText(idempotencyKey, "idempotencyKey");
        validateDistinctAccounts(sourceAccountId, destinationAccountId);
        long validAmount = MoneyPolicy.requireValidAmount(amount);
        String requestFingerprint =
                TransferRequestFingerprint.internalTransfer(sourceAccountId, destinationAccountId, validAmount);

        IdempotencyOperation operation = IdempotencyOperation.INTERNAL_TRANSFER;
        return idempotencyRecordRepository.findByCallerScopeAndOperationAndIdempotencyKey(
                        callerScope,
                        operation,
                        idempotencyKey
                )
                .map(record -> replayOrReject(record, requestFingerprint))
                .orElseGet(() -> createIdempotentTransfer(
                        callerScope,
                        operation,
                        idempotencyKey,
                        requestFingerprint,
                        sourceAccountId,
                        destinationAccountId,
                        validAmount,
                        failurePoint
                ));
    }

    private InternalTransferResult createIdempotentTransfer(
            String callerScope,
            IdempotencyOperation operation,
            String idempotencyKey,
            String requestFingerprint,
            Long sourceAccountId,
            Long destinationAccountId,
            long validAmount,
            TransferFailurePoint failurePoint
    ) {
        IdempotencyRecord record = idempotencyRecordRepository.saveAndFlush(
                new IdempotencyRecord(callerScope, operation, idempotencyKey, requestFingerprint)
        );
        PersistedTransfer persistedTransfer =
                performTransfer(sourceAccountId, destinationAccountId, validAmount, failurePoint);
        record.complete(persistedTransfer.transaction());
        idempotencyRecordRepository.saveAndFlush(record);
        return persistedTransfer.toResult();
    }

    private InternalTransferResult replayOrReject(IdempotencyRecord record, String requestFingerprint) {
        if (!record.getRequestFingerprint().equals(requestFingerprint)) {
            throw new IdempotencyKeyConflictException();
        }
        if (record.getStatus() != IdempotencyStatus.COMPLETED || record.getResponseTransaction() == null) {
            throw new IllegalStateException("Idempotency record is not replayable: " + record.getId());
        }
        return toResult(record.getResponseTransaction());
    }

    private PersistedTransfer performTransfer(
            Long sourceAccountId,
            Long destinationAccountId,
            long validAmount,
            TransferFailurePoint failurePoint
    ) {
        Account sourceAccount = findAccount(sourceAccountId);
        Account destinationAccount = findAccount(destinationAccountId);

        sourceAccount.withdraw(validAmount);
        failIfRequested(failurePoint, TransferFailurePoint.AFTER_SOURCE_WITHDRAWAL);
        destinationAccount.deposit(validAmount);

        FinancialTransaction transaction = financialTransactionRepository.saveAndFlush(
                new FinancialTransaction(UUID.randomUUID().toString(), TransactionType.INTERNAL_TRANSFER, validAmount)
        );
        accountJournalEntryRepository.saveAllAndFlush(List.of(
                new AccountJournalEntry(
                        transaction,
                        1,
                        sourceAccount,
                        JournalMovementType.BALANCE_DECREASE,
                        validAmount,
                        sourceAccount.getBalance()
                ),
                new AccountJournalEntry(
                        transaction,
                        2,
                        destinationAccount,
                        JournalMovementType.BALANCE_INCREASE,
                        validAmount,
                        destinationAccount.getBalance()
                )
        ));
        failIfRequested(failurePoint, TransferFailurePoint.AFTER_JOURNAL_FLUSH);

        return new PersistedTransfer(transaction, sourceAccount, destinationAccount, validAmount);
    }

    private InternalTransferResult toResult(FinancialTransaction transaction) {
        List<AccountJournalEntry> entries =
                accountJournalEntryRepository.findByTransactionIdOrderByEntryNo(transaction.getId());
        if (entries.size() != 2) {
            throw new IllegalStateException("Transfer journal is not replayable: " + transaction.getId());
        }

        AccountJournalEntry sourceEntry = entries.get(0);
        AccountJournalEntry destinationEntry = entries.get(1);
        return new InternalTransferResult(
                transaction.getId(),
                transaction.getTransactionKey(),
                sourceEntry.getAccount().getId(),
                destinationEntry.getAccount().getId(),
                sourceEntry.getBalanceAfter(),
                destinationEntry.getBalanceAfter(),
                transaction.getAmount()
        );
    }

    private Account findAccount(Long accountId) {
        if (accountId == null) {
            throw new AccountNotFoundException(null);
        }
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    private static void validateDistinctAccounts(Long sourceAccountId, Long destinationAccountId) {
        if (sourceAccountId != null && sourceAccountId.equals(destinationAccountId)) {
            throw new SameAccountTransferException();
        }
    }

    private static void validateIdempotencyText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidIdempotencyRequestException(fieldName);
        }
    }

    private static void failIfRequested(TransferFailurePoint actual, TransferFailurePoint expected) {
        if (actual == expected) {
            throw new TransferFaultInjectedException(actual);
        }
    }

    private record PersistedTransfer(
            FinancialTransaction transaction,
            Account sourceAccount,
            Account destinationAccount,
            long amount
    ) {
        private InternalTransferResult toResult() {
            return new InternalTransferResult(
                    transaction.getId(),
                    transaction.getTransactionKey(),
                    sourceAccount.getId(),
                    destinationAccount.getId(),
                    sourceAccount.getBalance(),
                    destinationAccount.getBalance(),
                    amount
            );
        }
    }
}
