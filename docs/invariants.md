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
- Controlled seed funding must create journal evidence; direct balance mutation is allowed only in tests that intentionally prove reconciliation mismatch detection.
- Application service methods must not expose non-journaled deposit or withdrawal paths.

## Transfer

- Source account and destination account must be different.
- A successful internal transfer preserves the sum of both account balances.
- A successful internal transfer creates exactly one committed financial transaction.
- A successful internal transfer creates exactly two account journal entries.
- The source journal entry decreases balance and the destination journal entry increases balance.
- Both transfer journal entries use the same amount as the financial transaction.
- Failed transfers must leave no partial balance change, financial transaction, or journal entry.
- Rollback tests must observe final database state outside a test-managed transaction.
- Fault injection after journal flush must not leave committed rows.
- Unsafe no-lock experiments are allowed only in test scope and must not be used by production services.
- Optimistic lock experiments must prove that stale concurrent writes do not both commit.
- Pessimistic lock experiments must prove that competing writers observe serialized account state.

## Idempotency

- The idempotency identity is scoped by caller, operation, and client-provided key.
- The request fingerprint includes fingerprint version, operation, currency, source account, destination account, and amount.
- Same scoped key with the same fingerprint has at most one committed money effect.
- Concurrent same-key requests with the same fingerprint must converge to one committed transfer result.
- Same scoped key with a different fingerprint is rejected.
- Failed single-transaction idempotent transfers leave no committed idempotency record.
- Completed idempotency records reference the committed response transaction.

## Reconciliation

- Account balance should equal total journal increases minus total journal decreases.
- Reconciliation detects mismatches and reports them.
- Reconciliation does not automatically repair balances.
- Accounts created, funded, and transferred through journaled flows should not appear in reconciliation mismatch results.

## Querying

- Account journal lookup should use keyset pagination rather than offset pagination.
- Account journal lookup is ordered by descending journal entry id for stable newest-first paging.
