package com.bankcore.service;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseConstraintMatcherTest {

    @Test
    void containsConstraintName_shouldInspectCauseChainMessages() {
        RuntimeException rootCause =
                new RuntimeException("Duplicate entry for key 'uk_idempotency_scope_operation_key_digest'");
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException("could not execute statement", rootCause);

        assertThat(DatabaseConstraintMatcher.containsConstraintName(
                exception,
                "uk_idempotency_scope_operation_key_digest"
        )).isTrue();
    }

    @Test
    void containsConstraintName_shouldReturnFalseWhenConstraintIsAbsent() {
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException("check constraint violation");

        assertThat(DatabaseConstraintMatcher.containsConstraintName(
                exception,
                "uk_idempotency_scope_operation_key_digest"
        )).isFalse();
    }
}
