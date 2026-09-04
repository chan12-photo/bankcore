package com.bankcore.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "account_journal_entry")
public class AccountJournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private FinancialTransaction transaction;

    @Column(name = "entry_no", nullable = false)
    private int entryNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30)
    private JournalMovementType movementType;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected AccountJournalEntry() {
    }

    public AccountJournalEntry(
            FinancialTransaction transaction,
            int entryNo,
            Account account,
            JournalMovementType movementType,
            long amount,
            long balanceAfter
    ) {
        this.transaction = Objects.requireNonNull(transaction, "transaction must not be null");
        this.entryNo = entryNo;
        this.account = Objects.requireNonNull(account, "account must not be null");
        this.movementType = Objects.requireNonNull(movementType, "movementType must not be null");
        MoneyPolicy.requireValidAmount(amount);
        MoneyPolicy.requireValidBalance(balanceAfter);
        this.amount = amount;
        this.balanceAfter = balanceAfter;
    }

    public Long getId() {
        return id;
    }

    public FinancialTransaction getTransaction() {
        return transaction;
    }

    public int getEntryNo() {
        return entryNo;
    }

    public Account getAccount() {
        return account;
    }

    public JournalMovementType getMovementType() {
        return movementType;
    }

    public long getAmount() {
        return amount;
    }

    public long getBalanceAfter() {
        return balanceAfter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
