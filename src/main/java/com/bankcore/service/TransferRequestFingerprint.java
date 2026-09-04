package com.bankcore.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class TransferRequestFingerprint {

    private TransferRequestFingerprint() {
    }

    static String internalTransfer(Long sourceAccountId, Long destinationAccountId, long amount) {
        String canonicalRequest = "v1"
                + "|operation=INTERNAL_TRANSFER"
                + "|currency=KRW"
                + "|sourceAccountId=" + sourceAccountId
                + "|destinationAccountId=" + destinationAccountId
                + "|amount=" + amount;
        return sha256(canonicalRequest);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
