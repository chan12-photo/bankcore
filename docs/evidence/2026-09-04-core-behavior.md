# Core Behavior Evidence - 2026-09-04

## Environment

- Java: Temurin 25.0.4.1
- Gradle Wrapper: 9.7.1
- Spring Boot: 4.1.1
- MySQL: 8.4.11 through Docker Compose and Testcontainers
- Local MySQL isolation observed at boot: `REPEATABLE_READ`

## Validation Commands

```bash
./gradlew test --no-daemon --rerun-tasks
```

Result:

```text
BUILD SUCCESSFUL
4 actionable tasks: 4 executed
```

```bash
./gradlew bootRun --no-daemon
```

Result:

```text
Successfully validated 4 migrations
Schema `bankcore` is up to date. No migration necessary.
Started BankcoreApplication
```

```bash
curl -s http://localhost:8080/api/v1/health
```

Result:

```json
{"status":"UP"}
```

```bash
curl -s http://localhost:8080/api/v1/reconciliation/account-balances/mismatches
```

Result on the local development database after journaled flows:

```json
[]
```

## Proved Behaviors

- Account creation starts with zero balance.
- Public deposit and withdrawal endpoints are not exposed.
- Internal transfer creates one `financial_transaction` row and two `account_journal_entry` rows.
- Runtime failure after source withdrawal rolls back the balance change.
- Runtime failure after flushed transaction and journal writes rolls back balances, transaction rows, and journal rows.
- Idempotent internal transfer uses `caller_scope + operation + idempotency_key` as its unique identity.
- Repeating the same idempotency key with the same request returns the same transfer result without a duplicate money effect.
- Repeating the same idempotency key with a different request fingerprint is rejected.
- Failed idempotent transfer rolls back the idempotency record together with transfer state.
- Reconciliation detects direct balance mutation without journal evidence.
- Journaled controlled seed funding plus internal transfer does not produce reconciliation mismatches.
- A test-only unsafe no-lock transfer experiment can create reconciliation mismatches under concurrent stale reads.
- A test-only optimistic locking experiment with `@Version` allows only one concurrent stale-version transfer to commit.
- The optimistic locking experiment leaves no reconciliation mismatches for the involved accounts after one transfer rolls back.
- A test-only pessimistic write lock experiment serializes concurrent transfers on the source account row.
- The pessimistic write lock experiment makes the second transfer observe the latest balance and roll back without reconciliation mismatches.

## Important Boundary

This evidence proves correctness properties for the current synthetic portfolio backend. It does not claim production banking correctness, payment-network integration, regulatory compliance, fraud controls, or real general-ledger accounting.
