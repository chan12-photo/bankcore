package com.bankcore.service;

import com.bankcore.domain.Account;
import com.bankcore.domain.AccountJournalEntry;
import com.bankcore.domain.FinancialTransaction;
import com.bankcore.domain.JournalMovementType;
import com.bankcore.domain.MoneyPolicy;
import com.bankcore.domain.TransactionType;
import com.bankcore.exception.AccountNotFoundException;
import com.bankcore.repository.AccountJournalEntryRepository;
import com.bankcore.repository.AccountRepository;
import com.bankcore.repository.FinancialTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ControlledFundingService {

    private final AccountRepository accountRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final AccountJournalEntryRepository accountJournalEntryRepository;

    public ControlledFundingService(
            AccountRepository accountRepository,
            FinancialTransactionRepository financialTransactionRepository,
            AccountJournalEntryRepository accountJournalEntryRepository
    ) {
        this.accountRepository = accountRepository;
        this.financialTransactionRepository = financialTransactionRepository;
        this.accountJournalEntryRepository = accountJournalEntryRepository;
    }

    @Transactional
    public ControlledFundingResult seedFunds(Long accountId, Long amount) {
        long validAmount = MoneyPolicy.requireValidAmount(amount);
        Account account = findAccount(accountId);
        account.deposit(validAmount);

        FinancialTransaction transaction = financialTransactionRepository.saveAndFlush(
                new FinancialTransaction(UUID.randomUUID().toString(), TransactionType.CONTROLLED_SEED, validAmount)
        );
        accountJournalEntryRepository.saveAllAndFlush(List.of(
                new AccountJournalEntry(
                        transaction,
                        1,
                        account,
                        JournalMovementType.BALANCE_INCREASE,
                        validAmount,
                        account.getBalance()
                )
        ));

        return new ControlledFundingResult(
                transaction.getId(),
                transaction.getTransactionKey(),
                account.getId(),
                account.getBalance(),
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
}
