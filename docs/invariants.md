# BankCore Invariants

## Account

- New accounts start with `balance = 0`.
- Account balance is stored as integer KRW using Java `long` and MySQL `BIGINT`.
- Account balance must never be negative.
- Money movement is allowed only when account status is `ACTIVE`.
- A single requested money amount must be greater than zero.
- A single requested money amount must not exceed `MoneyPolicy.MAX_AMOUNT`.
- Stored account balance must not exceed `MoneyPolicy.MAX_BALANCE`.
- MySQL CHECK constraints enforce the lower and upper account balance bounds.

## Transfer

- Source account and destination account must be different.
- A successful internal transfer preserves the sum of both account balances.
- A successful internal transfer creates exactly one committed financial transaction.
- A successful internal transfer creates exactly two account journal entries.
- The source journal entry decreases balance and the destination journal entry increases balance.
- Both transfer journal entries use the same amount as the financial transaction.
- Failed transfers must leave no partial balance change, financial transaction, or journal entry.

## Idempotency

- The idempotency identity is scoped by caller, operation, and client-provided key.
- The request fingerprint includes operation, currency, source account, destination account, and amount.
- Same scoped key with the same fingerprint has at most one committed money effect.
- Same scoped key with a different fingerprint is rejected.

## Reconciliation

- Account balance should equal total journal increases minus total journal decreases.
- Reconciliation detects mismatches and reports them.
- Reconciliation does not automatically repair balances.
