package com.bankcore.service;

final class DatabaseConstraintMatcher {

    private DatabaseConstraintMatcher() {
    }

    static boolean containsConstraintName(Throwable exception, String constraintName) {
        Throwable candidate = exception;
        while (candidate != null) {
            String message = candidate.getMessage();
            if (message != null && message.contains(constraintName)) {
                return true;
            }
            candidate = candidate.getCause();
        }
        return false;
    }
}
