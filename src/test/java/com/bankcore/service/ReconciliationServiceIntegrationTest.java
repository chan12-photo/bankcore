package com.bankcore.service;

import com.bankcore.domain.Account;
import com.bankcore.domain.AccountJournalEntry;
import com.bankcore.domain.Customer;
import com.bankcore.domain.FinancialTransaction;
import com.bankcore.domain.JournalMovementType;
import com.bankcore.domain.TransactionType;
import com.bankcore.repository.AccountRepository;
import com.bankcore.repository.AccountJournalEntryRepository;
import com.bankcore.repository.CustomerRepository;
import com.bankcore.repository.FinancialTransactionRepository;
import com.bankcore.support.MySqlContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ImportTestcontainers(MySqlContainerSupport.class)
class ReconciliationServiceIntegrationTest {

    private static final AtomicLong ACCOUNT_SEQUENCE = new AtomicLong(50_000);

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private ControlledFundingService controlledFundingService;

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
    void findAccountBalanceMismatches_shouldNotReportAccountsFundedAndTransferredThroughJournaledFlows() {
        Account sourceAccount = createZeroBalanceAccount();
        Account destinationAccount = createZeroBalanceAccount();
        controlledFundingService.seedFunds(sourceAccount.getId(), 10_000L);
        controlledFundingService.seedFunds(destinationAccount.getId(), 2_000L);

        transferService.transferInternalIdempotent(
                "reconciliation-test",
                nextIdempotencyKey(),
                sourceAccount.getId(),
                destinationAccount.getId(),
                3_000L
        );

        assertThat(reconciliationService.findAccountBalanceMismatches())
                .filteredOn(result -> result.accountId().equals(sourceAccount.getId())
                        || result.accountId().equals(destinationAccount.getId()))
                .isEmpty();
    }

    @Test
    void findAccountBalanceMismatches_shouldReportAccountWhenStoredBalanceHasNoMatchingJournal() {
        Account account = createZeroBalanceAccount();
        account.deposit(5_000L);
        accountRepository.saveAndFlush(account);

        assertThat(reconciliationService.findAccountBalanceMismatches())
                .filteredOn(result -> result.accountId().equals(account.getId()))
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.storedBalance()).isEqualTo(5_000L);
                    assertThat(result.journalBalance()).isZero();
                    assertThat(result.difference()).isEqualTo(5_000L);
                });
    }

    @Test
    void findTransactionJournalMismatches_shouldNotReportValidSeedAndTransferTransactions() {
        Account sourceAccount = createZeroBalanceAccount();
        Account destinationAccount = createZeroBalanceAccount();
        controlledFundingService.seedFunds(sourceAccount.getId(), 10_000L);
        controlledFundingService.seedFunds(destinationAccount.getId(), 2_000L);

        InternalTransferResult result = transferService.transferInternalIdempotent(
                "transaction-reconciliation-test",
                nextIdempotencyKey(),
                sourceAccount.getId(),
                destinationAccount.getId(),
                3_000L
        );

        assertThat(reconciliationService.findTransactionJournalMismatches())
                .filteredOn(mismatch -> mismatch.transactionId().equals(result.transactionId()))
                .isEmpty();
    }

    @Test
    void findTransactionJournalMismatches_shouldReportInternalTransferWithMissingJournalEntry() {
        Account sourceAccount = createZeroBalanceAccount();
        FinancialTransaction transaction = createTransaction(TransactionType.INTERNAL_TRANSFER, 1_000L);
        createJournalEntry(transaction, 1, sourceAccount, JournalMovementType.BALANCE_DECREASE, 1_000L, 9_000L);

        assertThat(findTransactionMismatch(transaction))
                .satisfies(mismatch -> assertThat(mismatch.issueCodes())
                        .contains(TransactionJournalInvariant.JOURNAL_ENTRY_COUNT));
    }

    @Test
    void findTransactionJournalMismatches_shouldReportInternalTransferWithTwoDecreaseEntries() {
        Account sourceAccount = createZeroBalanceAccount();
        Account destinationAccount = createZeroBalanceAccount();
        FinancialTransaction transaction = createTransaction(TransactionType.INTERNAL_TRANSFER, 1_000L);
        createJournalEntry(transaction, 1, sourceAccount, JournalMovementType.BALANCE_DECREASE, 1_000L, 9_000L);
        createJournalEntry(transaction, 2, destinationAccount, JournalMovementType.BALANCE_DECREASE, 1_000L, 1_000L);

        assertThat(findTransactionMismatch(transaction))
                .satisfies(mismatch -> assertThat(mismatch.issueCodes())
                        .contains(
                                TransactionJournalInvariant.ENTRY_DIRECTION_ORDER,
                                TransactionJournalInvariant.DECREASE_ENTRY_COUNT,
                                TransactionJournalInvariant.INCREASE_ENTRY_COUNT,
                                TransactionJournalInvariant.SIGNED_AMOUNT_BALANCE
                        ));
    }

    @Test
    void findTransactionJournalMismatches_shouldReportInternalTransferWithSameAccountPair() {
        Account account = createZeroBalanceAccount();
        FinancialTransaction transaction = createTransaction(TransactionType.INTERNAL_TRANSFER, 1_000L);
        createJournalEntry(transaction, 1, account, JournalMovementType.BALANCE_DECREASE, 1_000L, 9_000L);
        createJournalEntry(transaction, 2, account, JournalMovementType.BALANCE_INCREASE, 1_000L, 10_000L);

        assertThat(findTransactionMismatch(transaction))
                .satisfies(mismatch -> assertThat(mismatch.issueCodes())
                        .contains(TransactionJournalInvariant.DISTINCT_ACCOUNT_COUNT));
    }

    @Test
    void findTransactionJournalMismatches_shouldReportJournalAmountThatDiffersFromTransactionAmount() {
        Account sourceAccount = createZeroBalanceAccount();
        Account destinationAccount = createZeroBalanceAccount();
        FinancialTransaction transaction = createTransaction(TransactionType.INTERNAL_TRANSFER, 1_000L);
        createJournalEntry(transaction, 1, sourceAccount, JournalMovementType.BALANCE_DECREASE, 1_000L, 9_000L);
        createJournalEntry(transaction, 2, destinationAccount, JournalMovementType.BALANCE_INCREASE, 900L, 2_900L);

        assertThat(findTransactionMismatch(transaction))
                .satisfies(mismatch -> assertThat(mismatch.issueCodes())
                        .contains(
                                TransactionJournalInvariant.JOURNAL_AMOUNT,
                                TransactionJournalInvariant.SIGNED_AMOUNT_BALANCE
                        ));
    }

    @Test
    void findTransactionJournalMismatches_shouldReportMalformedControlledSeedTransaction() {
        Account account = createZeroBalanceAccount();
        FinancialTransaction transaction = createTransaction(TransactionType.CONTROLLED_SEED, 1_000L);
        createJournalEntry(transaction, 1, account, JournalMovementType.BALANCE_DECREASE, 1_000L, 0L);

        assertThat(findTransactionMismatch(transaction))
                .satisfies(mismatch -> assertThat(mismatch.issueCodes())
                        .contains(
                                TransactionJournalInvariant.ENTRY_DIRECTION_ORDER,
                                TransactionJournalInvariant.DECREASE_ENTRY_COUNT,
                                TransactionJournalInvariant.INCREASE_ENTRY_COUNT,
                                TransactionJournalInvariant.SIGNED_AMOUNT_BALANCE
                        ));
    }

    private Account createZeroBalanceAccount() {
        long sequence = ACCOUNT_SEQUENCE.incrementAndGet();
        Customer customer = customerRepository.saveAndFlush(new Customer("Reconciliation Customer " + sequence));
        return accountRepository.saveAndFlush(new Account(customer, "500-000-" + sequence));
    }

    private FinancialTransaction createTransaction(TransactionType type, long amount) {
        return financialTransactionRepository.saveAndFlush(
                new FinancialTransaction("recon-tx-" + ACCOUNT_SEQUENCE.incrementAndGet(), type, amount)
        );
    }

    private void createJournalEntry(
            FinancialTransaction transaction,
            int entryNo,
            Account account,
            JournalMovementType movementType,
            long amount,
            long balanceAfter
    ) {
        accountJournalEntryRepository.saveAndFlush(new AccountJournalEntry(
                transaction,
                entryNo,
                account,
                movementType,
                amount,
                balanceAfter
        ));
    }

    private TransactionJournalReconciliationResult findTransactionMismatch(FinancialTransaction transaction) {
        return reconciliationService.findTransactionJournalMismatches().stream()
                .filter(mismatch -> mismatch.transactionId().equals(transaction.getId()))
                .findFirst()
                .orElseThrow();
    }

    private static String nextIdempotencyKey() {
        return "recon-idem-" + ACCOUNT_SEQUENCE.incrementAndGet();
    }
}
