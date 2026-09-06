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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "caller_scope", nullable = false, length = 100)
    private String callerScope;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 50)
    private IdempotencyOperation operation;

    @Column(name = "idempotency_key_digest", nullable = false, columnDefinition = "BINARY(32)")
    private byte[] idempotencyKeyDigest;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private IdempotencyStatus status;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "response_transaction_id")
    private FinancialTransaction responseTransaction;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(
            String callerScope,
            IdempotencyOperation operation,
            byte[] idempotencyKeyDigest,
            String requestFingerprint
    ) {
        this.callerScope = requireText(callerScope, "callerScope");
        this.operation = Objects.requireNonNull(operation, "operation must not be null");
        this.idempotencyKeyDigest = IdempotencyKeyDigest.copy(idempotencyKeyDigest);
        this.requestFingerprint = requireText(requestFingerprint, "requestFingerprint");
        this.status = IdempotencyStatus.PROCESSING;
    }

    public Long getId() {
        return id;
    }

    public String getCallerScope() {
        return callerScope;
    }

    public IdempotencyOperation getOperation() {
        return operation;
    }

    public byte[] getIdempotencyKeyDigest() {
        return IdempotencyKeyDigest.copy(idempotencyKeyDigest);
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public IdempotencyStatus getStatus() {
        return status;
    }

    public FinancialTransaction getResponseTransaction() {
        return responseTransaction;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void complete(FinancialTransaction responseTransaction) {
        this.responseTransaction = Objects.requireNonNull(responseTransaction, "responseTransaction must not be null");
        this.status = IdempotencyStatus.COMPLETED;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
