package com.bankcore.domain;

import com.bankcore.exception.AmountLimitExceededException;
import com.bankcore.exception.BalanceLimitExceededException;
import com.bankcore.exception.InvalidAmountException;

public final class MoneyPolicy {

    public static final long MAX_AMOUNT = 1_000_000_000_000L;
    public static final long MAX_BALANCE = 100_000_000_000_000L;

    private MoneyPolicy() {
    }

    public static long requireValidAmount(Long amount) {
        if (amount == null) {
            throw new InvalidAmountException();
        }
        requireValidAmount(amount.longValue());
        return amount;
    }

    public static void requireValidAmount(long amount) {
        if (amount <= 0) {
            throw new InvalidAmountException();
        }
        if (amount > MAX_AMOUNT) {
            throw new AmountLimitExceededException();
        }
    }

    public static void requireValidBalance(long balance) {
        if (balance < 0 || balance > MAX_BALANCE) {
            throw new BalanceLimitExceededException();
        }
    }
}
