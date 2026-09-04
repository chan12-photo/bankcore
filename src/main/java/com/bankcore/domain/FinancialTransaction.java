package com.bankcore.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "financial_transaction")
public class FinancialTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_key", nullable = false, length = 36, unique = true)
    private String transactionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private TransactionType type;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected FinancialTransaction() {
    }

    public FinancialTransaction(String transactionKey, TransactionType type, long amount) {
        this.transactionKey = requireText(transactionKey, "transactionKey");
        this.type = Objects.requireNonNull(type, "type must not be null");
        MoneyPolicy.requireValidAmount(amount);
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public String getTransactionKey() {
        return transactionKey;
    }

    public TransactionType getType() {
        return type;
    }

    public long getAmount() {
        return amount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
