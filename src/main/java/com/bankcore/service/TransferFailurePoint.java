package com.bankcore.service;

public enum TransferFailurePoint {
    NONE,
    AFTER_SOURCE_WITHDRAWAL,
    AFTER_JOURNAL_FLUSH
}
