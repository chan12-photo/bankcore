package com.bankcore.service;

import com.bankcore.domain.Account;
import com.bankcore.domain.AccountJournalEntry;
import com.bankcore.domain.FinancialTransaction;
import com.bankcore.domain.IdempotencyKeyDigest;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

@Service
public class TransferService {

    private static final int MAX_CALLER_SCOPE_LENGTH = 100;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 120;
    private static final String IDEMPOTENCY_UNIQUE_CONSTRAINT = "uk_idempotency_scope_operation_key_digest";

    private final AccountRepository accountRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final AccountJournalEntryRepository accountJournalEntryRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate idempotentTransferTransactionTemplate;

    public TransferService(
            AccountRepository accountRepository,
            FinancialTransactionRepository financialTransactionRepository,
            AccountJournalEntryRepository accountJournalEntryRepository,
            IdempotencyRecordRepository idempotencyRecordRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.accountRepository = accountRepository;
        this.financialTransactionRepository = financialTransactionRepository;
        this.accountJournalEntryRepository = accountJournalEntryRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.idempotentTransferTransactionTemplate = new TransactionTemplate(transactionManager);
        this.idempotentTransferTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    InternalTransferResult transferInternal(Long sourceAccountId, Long destinationAccountId, Long amount) {
        return transferInternal(sourceAccountId, destinationAccountId, amount, TransferFailurePoint.NONE);
    }

    InternalTransferResult transferInternal(
            Long sourceAccountId,
            Long destinationAccountId,
            Long amount,
            TransferFailurePoint failurePoint
    ) {
        validateDistinctAccounts(sourceAccountId, destinationAccountId);
        long validAmount = MoneyPolicy.requireValidAmount(amount);
        return transactionTemplate.execute(status ->
                performTransfer(sourceAccountId, destinationAccountId, validAmount, failurePoint).toResult()
        );
    }

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
        byte[] idempotencyKeyDigest = IdempotencyKeyDigest.of(callerScope, operation, idempotencyKey);
        try {
            return idempotentTransferTransactionTemplate.execute(status -> claimAndRunOrReplay(
                    callerScope,
                    operation,
                    idempotencyKeyDigest,
                    requestFingerprint,
                    sourceAccountId,
                    destinationAccountId,
                    validAmount,
                    failurePoint
            ));
        } catch (DataIntegrityViolationException exception) {
            if (!DatabaseConstraintMatcher.containsConstraintName(exception, IDEMPOTENCY_UNIQUE_CONSTRAINT)) {
                throw exception;
            }
            return replayAfterConcurrentReservation(callerScope, operation, idempotencyKeyDigest, requestFingerprint);
        }
    }

    private InternalTransferResult claimAndRunOrReplay(
            String callerScope,
            IdempotencyOperation operation,
            byte[] idempotencyKeyDigest,
            String requestFingerprint,
            Long sourceAccountId,
            Long destinationAccountId,
            long validAmount,
            TransferFailurePoint failurePoint
    ) {
        return idempotencyRecordRepository.findByCallerScopeAndOperationAndIdempotencyKeyDigest(
                        callerScope,
                        operation,
                        idempotencyKeyDigest
                )
                .map(record -> replayOrReject(record, requestFingerprint))
                .orElseGet(() -> createIdempotentTransfer(
                        callerScope,
                        operation,
                        idempotencyKeyDigest,
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
            byte[] idempotencyKeyDigest,
            String requestFingerprint,
            Long sourceAccountId,
            Long destinationAccountId,
            long validAmount,
            TransferFailurePoint failurePoint
    ) {
        IdempotencyRecord record = idempotencyRecordRepository.saveAndFlush(
                new IdempotencyRecord(callerScope, operation, idempotencyKeyDigest, requestFingerprint)
        );
        PersistedTransfer persistedTransfer =
                performTransfer(sourceAccountId, destinationAccountId, validAmount, failurePoint);
        record.complete(persistedTransfer.transaction());
        idempotencyRecordRepository.saveAndFlush(record);
        return persistedTransfer.toResult();
    }

    private InternalTransferResult replayAfterConcurrentReservation(
            String callerScope,
            IdempotencyOperation operation,
            byte[] idempotencyKeyDigest,
            String requestFingerprint
    ) {
        return idempotentTransferTransactionTemplate.execute(status -> {
            IdempotencyRecord record = idempotencyRecordRepository.findByCallerScopeAndOperationAndIdempotencyKeyDigest(
                            callerScope,
                            operation,
                            idempotencyKeyDigest
                    )
                    .orElseThrow(() -> new IllegalStateException("Idempotency record disappeared after conflict."));
            return replayOrReject(record, requestFingerprint);
        });
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
        LockedTransferAccounts transferAccounts = findTransferAccountsForUpdate(sourceAccountId, destinationAccountId);
        Account sourceAccount = transferAccounts.sourceAccount();
        Account destinationAccount = transferAccounts.destinationAccount();

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
        TransactionJournalInvariant.InternalTransferJournalPair journalPair =
                TransactionJournalInvariant.requireReplayableInternalTransfer(transaction, entries);
        AccountJournalEntry sourceEntry = journalPair.sourceEntry();
        AccountJournalEntry destinationEntry = journalPair.destinationEntry();
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

    private LockedTransferAccounts findTransferAccountsForUpdate(Long sourceAccountId, Long destinationAccountId) {
        if (sourceAccountId == null || destinationAccountId == null) {
            throw new AccountNotFoundException(null);
        }
        List<Long> accountIds = sourceAccountId.compareTo(destinationAccountId) < 0
                ? List.of(sourceAccountId, destinationAccountId)
                : List.of(destinationAccountId, sourceAccountId);
        List<Account> lockedAccounts = accountRepository.findAllByIdInOrderByIdForUpdate(accountIds);

        Account sourceAccount = null;
        Account destinationAccount = null;
        for (Account account : lockedAccounts) {
            if (account.getId().equals(sourceAccountId)) {
                sourceAccount = account;
            }
            if (account.getId().equals(destinationAccountId)) {
                destinationAccount = account;
            }
        }
        if (sourceAccount == null) {
            throw new AccountNotFoundException(sourceAccountId);
        }
        if (destinationAccount == null) {
            throw new AccountNotFoundException(destinationAccountId);
        }
        return new LockedTransferAccounts(sourceAccount, destinationAccount);
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
        int maxLength = switch (fieldName) {
            case "callerScope" -> MAX_CALLER_SCOPE_LENGTH;
            case "idempotencyKey" -> MAX_IDEMPOTENCY_KEY_LENGTH;
            default -> throw new IllegalArgumentException("Unknown idempotency field: " + fieldName);
        };
        if (value.length() > maxLength) {
            throw new InvalidIdempotencyRequestException(fieldName, maxLength);
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

    private record LockedTransferAccounts(
            Account sourceAccount,
            Account destinationAccount
    ) {
    }
}
