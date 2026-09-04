package com.bankcore.exception;

import com.bankcore.domain.AccountStatus;

public class AccountNotActiveException extends RuntimeException implements BankCoreException {

    private final String code;

    public AccountNotActiveException(AccountStatus status) {
        super(status == AccountStatus.FROZEN ? "Account is frozen." : "Account is closed.");
        this.code = status == AccountStatus.FROZEN ? "ACCOUNT_FROZEN" : "ACCOUNT_CLOSED";
    }

    @Override
    public String getCode() {
        return code;
    }
}
