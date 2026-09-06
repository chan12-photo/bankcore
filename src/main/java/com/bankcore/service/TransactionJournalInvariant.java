package com.bankcore.service;

import com.bankcore.domain.AccountJournalEntry;
import com.bankcore.domain.FinancialTransaction;
import com.bankcore.domain.JournalMovementType;
import com.bankcore.domain.TransactionType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class TransactionJournalInvariant {

    static final String JOURNAL_ENTRY_COUNT = "JOURNAL_ENTRY_COUNT";
    static final String ENTRY_NO_SEQUENCE = "ENTRY_NO_SEQUENCE";
    static final String ENTRY_DIRECTION_ORDER = "ENTRY_DIRECTION_ORDER";
    static final String MOVEMENT_TYPE = "MOVEMENT_TYPE";
    static final String DECREASE_ENTRY_COUNT = "DECREASE_ENTRY_COUNT";
    static final String INCREASE_ENTRY_COUNT = "INCREASE_ENTRY_COUNT";
    static final String DISTINCT_ACCOUNT_COUNT = "DISTINCT_ACCOUNT_COUNT";
    static final String JOURNAL_AMOUNT = "JOURNAL_AMOUNT";
    static final String SIGNED_AMOUNT_BALANCE = "SIGNED_AMOUNT_BALANCE";
    static final String TRANSACTION_TYPE = "TRANSACTION_TYPE";

    private TransactionJournalInvariant() {
    }

    static List<String> findIssueCodes(TransactionJournalStats stats) {
        List<String> issueCodes = new ArrayList<>();
        String transactionType = stats.transactionType();

        if (TransactionType.INTERNAL_TRANSFER.name().equals(transactionType)) {
            addInternalTransferIssues(stats, issueCodes);
        } else if (TransactionType.CONTROLLED_SEED.name().equals(transactionType)) {
            addControlledSeedIssues(stats, issueCodes);
        } else {
            issueCodes.add(TRANSACTION_TYPE);
        }

        if (stats.unknownMovementCount() > 0) {
            issueCodes.add(MOVEMENT_TYPE);
        }
        if (stats.journalAmountMismatchCount() > 0) {
            issueCodes.add(JOURNAL_AMOUNT);
        }

        return List.copyOf(issueCodes);
    }

    static InternalTransferJournalPair requireReplayableInternalTransfer(
            FinancialTransaction transaction,
            List<AccountJournalEntry> entries
    ) {
        List<JournalEntrySnapshot> snapshots = entries.stream()
                .map(entry -> new JournalEntrySnapshot(
                        entry.getEntryNo(),
                        entry.getAccount().getId(),
                        entry.getMovementType().name(),
                        entry.getAmount()
                ))
                .toList();
        TransactionJournalStats stats = fromEntries(
                transaction.getType().name(),
                transaction.getAmount(),
                snapshots
        );
        List<String> issueCodes = findIssueCodes(stats);
        if (!issueCodes.isEmpty()) {
            throw new IllegalStateException(
                    "Internal transfer journal is not replayable: " + transaction.getId() + " " + issueCodes
            );
        }

        AccountJournalEntry sourceEntry = null;
        AccountJournalEntry destinationEntry = null;
        for (AccountJournalEntry entry : entries) {
            if (entry.getEntryNo() == 1) {
                sourceEntry = entry;
            } else if (entry.getEntryNo() == 2) {
                destinationEntry = entry;
            }
        }
        return new InternalTransferJournalPair(sourceEntry, destinationEntry);
    }

    private static void addInternalTransferIssues(
            TransactionJournalStats stats,
            List<String> issueCodes
    ) {
        if (stats.journalEntryCount() != 2) {
            issueCodes.add(JOURNAL_ENTRY_COUNT);
        }
        if (stats.entryNoOneCount() != 1
                || stats.entryNoTwoCount() != 1
                || stats.entryNoOneCount() + stats.entryNoTwoCount() != stats.journalEntryCount()) {
            issueCodes.add(ENTRY_NO_SEQUENCE);
        }
        if (stats.entryOneDecreaseCount() != 1 || stats.entryTwoIncreaseCount() != 1) {
            issueCodes.add(ENTRY_DIRECTION_ORDER);
        }
        if (stats.decreaseEntryCount() != 1) {
            issueCodes.add(DECREASE_ENTRY_COUNT);
        }
        if (stats.increaseEntryCount() != 1) {
            issueCodes.add(INCREASE_ENTRY_COUNT);
        }
        if (stats.distinctAccountCount() != 2) {
            issueCodes.add(DISTINCT_ACCOUNT_COUNT);
        }
        if (stats.signedJournalAmount() != 0) {
            issueCodes.add(SIGNED_AMOUNT_BALANCE);
        }
    }

    private static void addControlledSeedIssues(
            TransactionJournalStats stats,
            List<String> issueCodes
    ) {
        if (stats.journalEntryCount() != 1) {
            issueCodes.add(JOURNAL_ENTRY_COUNT);
        }
        if (stats.entryNoOneCount() != 1 || stats.entryNoTwoCount() != 0) {
            issueCodes.add(ENTRY_NO_SEQUENCE);
        }
        if (stats.entryOneIncreaseCount() != 1) {
            issueCodes.add(ENTRY_DIRECTION_ORDER);
        }
        if (stats.decreaseEntryCount() != 0) {
            issueCodes.add(DECREASE_ENTRY_COUNT);
        }
        if (stats.increaseEntryCount() != 1) {
            issueCodes.add(INCREASE_ENTRY_COUNT);
        }
        if (stats.distinctAccountCount() != 1) {
            issueCodes.add(DISTINCT_ACCOUNT_COUNT);
        }
        if (stats.signedJournalAmount() != stats.transactionAmount()) {
            issueCodes.add(SIGNED_AMOUNT_BALANCE);
        }
    }

    private static TransactionJournalStats fromEntries(
            String transactionType,
            long transactionAmount,
            List<JournalEntrySnapshot> entries
    ) {
        Set<Long> accountIds = new HashSet<>();
        long entryNoOneCount = 0;
        long entryNoTwoCount = 0;
        long entryOneDecreaseCount = 0;
        long entryOneIncreaseCount = 0;
        long entryTwoIncreaseCount = 0;
        long decreaseEntryCount = 0;
        long increaseEntryCount = 0;
        long unknownMovementCount = 0;
        long journalAmountMismatchCount = 0;
        long signedJournalAmount = 0;

        for (JournalEntrySnapshot entry : entries) {
            accountIds.add(entry.accountId());
            if (entry.entryNo() == 1) {
                entryNoOneCount++;
            }
            if (entry.entryNo() == 2) {
                entryNoTwoCount++;
            }
            if (!entry.movementType().equals(JournalMovementType.BALANCE_DECREASE.name())
                    && !entry.movementType().equals(JournalMovementType.BALANCE_INCREASE.name())) {
                unknownMovementCount++;
            }
            if (entry.movementType().equals(JournalMovementType.BALANCE_DECREASE.name())) {
                decreaseEntryCount++;
                signedJournalAmount -= entry.amount();
                if (entry.entryNo() == 1) {
                    entryOneDecreaseCount++;
                }
            }
            if (entry.movementType().equals(JournalMovementType.BALANCE_INCREASE.name())) {
                increaseEntryCount++;
                signedJournalAmount += entry.amount();
                if (entry.entryNo() == 1) {
                    entryOneIncreaseCount++;
                }
                if (entry.entryNo() == 2) {
                    entryTwoIncreaseCount++;
                }
            }
            if (entry.amount() != transactionAmount) {
                journalAmountMismatchCount++;
            }
        }

        return new TransactionJournalStats(
                transactionType,
                transactionAmount,
                entries.size(),
                entryNoOneCount,
                entryNoTwoCount,
                entryOneDecreaseCount,
                entryOneIncreaseCount,
                entryTwoIncreaseCount,
                decreaseEntryCount,
                increaseEntryCount,
                accountIds.size(),
                unknownMovementCount,
                journalAmountMismatchCount,
                signedJournalAmount
        );
    }

    record TransactionJournalStats(
            String transactionType,
            long transactionAmount,
            long journalEntryCount,
            long entryNoOneCount,
            long entryNoTwoCount,
            long entryOneDecreaseCount,
            long entryOneIncreaseCount,
            long entryTwoIncreaseCount,
            long decreaseEntryCount,
            long increaseEntryCount,
            long distinctAccountCount,
            long unknownMovementCount,
            long journalAmountMismatchCount,
            long signedJournalAmount
    ) {
    }

    record InternalTransferJournalPair(
            AccountJournalEntry sourceEntry,
            AccountJournalEntry destinationEntry
    ) {
    }

    private record JournalEntrySnapshot(
            int entryNo,
            Long accountId,
            String movementType,
            long amount
    ) {
    }
}
