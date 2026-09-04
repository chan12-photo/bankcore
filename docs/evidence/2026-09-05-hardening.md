# Hardening Evidence - 2026-09-05

## Purpose

This pass closes review findings that could otherwise weaken the portfolio story:

- Concurrent same-key idempotency should not leak a raw database unique constraint failure or create duplicate money movement.
- Non-journaled service-layer deposit and withdrawal paths should not exist outside domain/test setup.
- Public request validation should reject values that exceed database column or check-constraint limits before persistence.
- The local walkthrough should provide a repeatable way to run a successful transfer without exposing a public deposit API.
- The pagination benchmark seed script should include the same offset comparison query described in evidence.

## Validation Commands

```bash
./gradlew compileJava compileTestJava --no-daemon
```

Result:

```text
BUILD SUCCESSFUL
```

```bash
./gradlew test --no-daemon --rerun-tasks
```

Result:

```text
BUILD SUCCESSFUL
tests=55 failures=0 errors=0 skipped=0
```

Docker was running locally for Testcontainers:

```text
Docker ServerVersion: 29.7.2
```

Demo profile was also verified manually on a non-default port:

```bash
SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun --args='--server.port=18080' --no-daemon
```

```bash
curl -s http://localhost:18080/api/v1/demo/accounts
```

Observed response on the existing local development database:

```json
[
  {
    "accountId": 3,
    "customerId": 4,
    "customerName": "Alice Demo",
    "accountNumber": "DEMO-ALICE-001",
    "balance": 100000
  },
  {
    "accountId": 4,
    "customerId": 5,
    "customerName": "Bob Demo",
    "accountNumber": "DEMO-BOB-001",
    "balance": 30000
  }
]
```

Repeating the same transfer request with the same idempotency key returned the same transaction:

```json
{
  "transactionId": 50003,
  "transactionKey": "d0fd8af5-6eed-497b-9f20-cbddc2511348",
  "sourceAccountId": 3,
  "destinationAccountId": 4,
  "sourceBalanceAfter": 99000,
  "destinationBalanceAfter": 31000,
  "amount": 1000
}
```

Reconciliation after the demo transfer:

```json
[]
```

## Implemented Hardening

### Concurrent Idempotency

`TransferService` now executes idempotent transfer claims through `TransactionTemplate`.

If two identical requests race to insert the same `(caller_scope, operation, idempotency_key)` record, one request wins the insert and performs the transfer. A losing request catches the unique-key `DataIntegrityViolationException` outside the failed transaction and opens a fresh transaction to replay the winning completed idempotency record.

The integration test `transferInternalIdempotent_shouldApplyMoneyEffectOnce_whenSameRequestArrivesConcurrently` sends 50 same-key requests concurrently and verifies:

- 50 callers receive results.
- All results share one transaction key.
- Source and destination balances reflect exactly one transfer.
- Only one financial transaction is created.
- Only two journal rows are created.
- Only one idempotency record is created.

### Non-Journaled Money Movement

`AccountService` no longer exposes `deposit` or `withdraw` service methods. Public money movement remains limited to the idempotent internal transfer API, while controlled seed funding stays journaled through `ControlledFundingService`.

The `Account` domain still owns `deposit` and `withdraw` behavior because transfer and controlled funding need domain-level balance transitions. The application service boundary no longer presents those operations as standalone non-journaled use cases.

### Request Validation

Bean Validation now mirrors database limits for public request DTOs:

- Customer name: nonblank, max 100.
- Customer id: required, positive.
- Account number: nonblank, max 30.
- Transfer account ids: required, positive.
- Transfer amount: required, positive, max `1_000_000_000_000`.

Idempotency headers are validated before persistence:

- `X-Caller-Scope`: nonblank, max 100.
- `Idempotency-Key`: nonblank, max 120.

### Demo Walkthrough

The `demo` Spring profile creates two synthetic journal-funded accounts:

- `DEMO-ALICE-001` with `100000`
- `DEMO-BOB-001` with `30000`

`GET /api/v1/demo/accounts` returns their current ids and balances so a local user can immediately run a successful idempotent transfer. The demo endpoint is available only under the `demo` profile.

### Pagination Benchmark Reproducibility

`scripts/sql/seed-journal-pagination-benchmark.sql` now includes the offset `EXPLAIN ANALYZE` query used in `docs/evidence/2026-09-04-journal-pagination-benchmark.md`, so the seed script and evidence document describe the same benchmark comparison.

## Remaining Deliberate Choice

The main remaining product/operations choice is optimistic-lock conflict behavior. The current project already demonstrates optimistic and pessimistic concurrency correctness in tests. A later production-style API could either:

- Return a clear conflict response when an optimistic stale write loses.
- Add bounded retry in a fresh transaction for selected retry-safe operations.

This is intentionally left as a scope decision rather than mixed into the current transfer API without a stronger product requirement.
