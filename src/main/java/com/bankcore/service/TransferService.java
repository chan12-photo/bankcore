package com.bankcore.service;

import com.bankcore.domain.Account;
import com.bankcore.domain.AccountJournalEntry;
import com.bankcore.domain.FinancialTransaction;
import com.bankcore.domain.JournalMovementType;
import com.bankcore.domain.MoneyPolicy;
import com.bankcore.domain.TransactionType;
import com.bankcore.exception.AccountNotFoundException;
import com.bankcore.exception.SameAccountTransferException;
import com.bankcore.repository.AccountJournalEntryRepository;
import com.bankcore.repository.AccountRepository;
import com.bankcore.repository.FinancialTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final AccountJournalEntryRepository accountJournalEntryRepository;

    public TransferService(
            AccountRepository accountRepository,
            FinancialTransactionRepository financialTransactionRepository,
            AccountJournalEntryRepository accountJournalEntryRepository
    ) {
        this.accountRepository = accountRepository;
        this.financialTransactionRepository = financialTransactionRepository;
        this.accountJournalEntryRepository = accountJournalEntryRepository;
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

        return new InternalTransferResult(
                transaction.getId(),
                transaction.getTransactionKey(),
                sourceAccount.getId(),
                destinationAccount.getId(),
                sourceAccount.getBalance(),
                destinationAccount.getBalance(),
                validAmount
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

    private static void failIfRequested(TransferFailurePoint actual, TransferFailurePoint expected) {
        if (actual == expected) {
            throw new TransferFaultInjectedException(actual);
        }
    }
}
