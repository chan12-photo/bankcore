package com.bankcore.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyKeyDigestTest {

    @Test
    void of_shouldReturnStableThirtyTwoByteDigest() {
        byte[] first = IdempotencyKeyDigest.of(
                "caller",
                IdempotencyOperation.INTERNAL_TRANSFER,
                "retry-key"
        );
        byte[] second = IdempotencyKeyDigest.of(
                "caller",
                IdempotencyOperation.INTERNAL_TRANSFER,
                "retry-key"
        );

        assertThat(first).hasSize(IdempotencyKeyDigest.BYTE_LENGTH);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void of_shouldTreatRawKeysAsCaseSensitive() {
        byte[] upperCaseKey = IdempotencyKeyDigest.of(
                "caller",
                IdempotencyOperation.INTERNAL_TRANSFER,
                "Retry-Key"
        );
        byte[] lowerCaseKey = IdempotencyKeyDigest.of(
                "caller",
                IdempotencyOperation.INTERNAL_TRANSFER,
                "retry-key"
        );

        assertThat(upperCaseKey).isNotEqualTo(lowerCaseKey);
    }

    @Test
    void copy_shouldRejectInvalidDigestLength() {
        assertThatThrownBy(() -> IdempotencyKeyDigest.copy(new byte[31]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }
}
