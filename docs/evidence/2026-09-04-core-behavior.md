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

GitHub Actions CI on `main` after push:

```text
completed  success  docs: add portfolio architecture decisions       CI  main  push
completed  success  feat: add account journal keyset pagination      CI  main  push
completed  success  test: stabilize transfer api error responses     CI  main  push
completed  success  test: add pessimistic locking transfer race      CI  main  push
completed  success  test: add optimistic locking transfer race       CI  main  push
```

```bash
./gradlew bootRun --no-daemon
```

Result:

```text
Successfully validated 5 migrations
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
- Missing idempotency headers and malformed JSON return stable `ApiErrorResponse` bodies.
- Bean Validation rejects missing customer id, blank account number, missing transfer amount, and negative transfer amount before service execution.
- Reconciliation detects direct balance mutation without journal evidence.
- Journaled controlled seed funding plus internal transfer does not produce reconciliation mismatches.
- A test-only unsafe no-lock transfer experiment can create reconciliation mismatches under concurrent stale reads.
- A test-only optimistic locking experiment with `@Version` allows only one concurrent stale-version transfer to commit.
- The optimistic locking experiment leaves no reconciliation mismatches for the involved accounts after one transfer rolls back.
- A test-only pessimistic write lock experiment serializes concurrent transfers on the source account row.
- The pessimistic write lock experiment makes the second transfer observe the latest balance and roll back without reconciliation mismatches.
- Account journal keyset pagination is available at `GET /api/v1/accounts/{accountId}/journal-entries`.
- The account journal query is backed by `idx_account_journal_account_id_id(account_id, id)`.
- Larger synthetic pagination evidence is captured in `docs/evidence/2026-09-04-journal-pagination-benchmark.md`.

## SQL Evidence

Local Flyway history after boot:

```text
version  description                            success
1        create customer and account tables      1
2        add account balance upper bound         1
3        create transaction journal tables       1
4        create idempotency record table         1
5        add account journal keyset index        1
```

Index verification:

```text
Key_name                              Seq_in_index  Column_name
idx_account_journal_account_id_id      1             account_id
idx_account_journal_account_id_id      2             id
```

Keyset query:

```sql
SELECT id, transaction_id, entry_no, movement_type, amount, balance_after, created_at
FROM account_journal_entry
WHERE account_id = ?
  AND id < ?
ORDER BY id DESC
LIMIT ?;
```

Observed `EXPLAIN` with `USE INDEX (idx_account_journal_account_id_id)`:

```text
type   key                                  key_len  rows  Extra
range  idx_account_journal_account_id_id     16       1     Using index condition; Backward index scan
```

Observed `EXPLAIN` for the first-page query:

```text
type  key                                  key_len  ref    rows  Extra
ref   idx_account_journal_account_id_id     8        const  1     Backward index scan
```

On the tiny local development database, MySQL may choose the primary key for some `id < ?` variants because the cardinality is near zero. The explicit index check confirms that the intended composite index is present and usable; larger measurement data should be captured later for portfolio-grade pagination benchmarks.

## Important Boundary

This evidence proves correctness properties for the current synthetic portfolio backend. It does not claim production banking correctness, payment-network integration, regulatory compliance, fraud controls, or real general-ledger accounting.
