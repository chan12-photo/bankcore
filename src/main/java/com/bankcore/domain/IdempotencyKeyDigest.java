package com.bankcore.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public final class IdempotencyKeyDigest {

    public static final int BYTE_LENGTH = 32;

    private IdempotencyKeyDigest() {
    }

    public static byte[] of(String callerScope, IdempotencyOperation operation, String idempotencyKey) {
        String canonicalValue = "bankcore-idempotency-v1"
                + "|callerScope=" + callerScope
                + "|operation=" + operation.name()
                + "|idempotencyKey=" + idempotencyKey;
        return sha256(canonicalValue);
    }

    public static byte[] copy(byte[] digest) {
        if (digest == null || digest.length != BYTE_LENGTH) {
            throw new IllegalArgumentException("idempotencyKeyDigest must be 32 bytes");
        }
        return Arrays.copyOf(digest, BYTE_LENGTH);
    }

    private static byte[] sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
