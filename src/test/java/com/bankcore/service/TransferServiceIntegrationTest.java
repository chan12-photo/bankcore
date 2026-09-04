package com.bankcore.service;

import com.bankcore.domain.Account;
import com.bankcore.domain.AccountJournalEntry;
import com.bankcore.domain.Customer;
import com.bankcore.domain.JournalMovementType;
import com.bankcore.exception.InsufficientBalanceException;
import com.bankcore.exception.SameAccountTransferException;
import com.bankcore.repository.AccountJournalEntryRepository;
import com.bankcore.repository.AccountRepository;
import com.bankcore.repository.CustomerRepository;
import com.bankcore.repository.FinancialTransactionRepository;
import com.bankcore.support.MySqlContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    @Autowired
    private AccountJournalEntryRepository accountJournalEntryRepository;

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

    private Account createAccountWithBalance(long balance) {
        long sequence = ACCOUNT_SEQUENCE.incrementAndGet();
        Customer customer = customerRepository.saveAndFlush(new Customer("Transfer Customer " + sequence));
        Account account = new Account(customer, "200-000-" + sequence);
        if (balance > 0) {
            account.deposit(balance);
        }
        return accountRepository.saveAndFlush(account);
    }
}
