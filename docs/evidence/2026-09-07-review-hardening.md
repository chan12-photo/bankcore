# External Review Hardening Evidence - 2026-09-07

## Purpose

This pass addresses the highest-value findings from an external portfolio review while keeping the project scope narrow: transaction correctness, idempotency, journal replayability, reconciliation, local demo repeatability, and reviewer-facing evidence.

## Changes

- Disabled Jackson float-to-integer coercion so JSON ids and money amounts such as `1.9` or `1000.99` are rejected instead of silently truncated.
- Added MockMvc regression tests proving decimal transfer ids and amounts return `INVALID_REQUEST_BODY` and leave balances, transactions, journal rows, and idempotency rows unchanged.
- Added transaction-journal reconciliation for `balance_after` snapshots using per-account journal replay in entry id order.
- Added replay protection so an idempotency record cannot return a result for a non-`INTERNAL_TRANSFER` transaction or a transaction whose stored `balance_after` snapshots are inconsistent.
- Added exact enum CHECK constraints in Flyway V8 so case/accent variants are rejected despite the database's case-insensitive default collation.
- Added an idempotency status/response-transaction consistency CHECK in Flyway V9.
- Replaced test fixture `LAST_INSERT_ID()` reads with `GeneratedKeyHolder` in the database constraint integration test.
- Added stable `INVALID_REQUEST_PARAMETER` API errors for non-numeric path and query parameters.
- Added bounded retry for transient Spring concurrency failures in the idempotent transfer path, followed by the existing stable conflict response if the conflict persists.
- Updated the journal pagination benchmark script and evidence to use the service-shaped `limit + 1` probe.
- Added a hidden demo reserve account so demo startup can rebalance Alice and Bob even when both accounts have surplus balances.
- Updated the Lab Console to preserve the submitted idempotency operation when the first transfer response is lost, then recover the committed response with the same request.

## Targeted Verification

Backend targeted checks:

```bash
./gradlew test --tests TransferControllerIntegrationTest --no-daemon
./gradlew test --tests ReconciliationServiceIntegrationTest --tests TransferServiceIntegrationTest --tests TransferControllerIntegrationTest --no-daemon
./gradlew test --tests DatabaseCheckConstraintIntegrationTest --tests AccountJournalControllerIntegrationTest --tests ReconciliationServiceIntegrationTest --tests TransferServiceIntegrationTest --no-daemon
./gradlew test --tests GlobalExceptionHandlerTest --tests AccountJournalControllerIntegrationTest --no-daemon
./gradlew test --tests TransferServiceConcurrencyRetryTest --tests GlobalExceptionHandlerTest --no-daemon
./gradlew test --tests DemoControllerIntegrationTest --tests TransferServiceConcurrencyRetryTest --tests TransferServiceIntegrationTest --no-daemon
./gradlew test --tests DatabaseCheckConstraintIntegrationTest --no-daemon
```

Observed result:

```text
BUILD SUCCESSFUL
```

Frontend targeted checks:

```bash
cd frontend
npm run lint
npm run test -- --run
```

Observed result:

```text
Test Files  1 passed (1)
Tests       2 passed (2)
```

## Full Local Verification

The full local reviewer wrapper was run after the hardening changes:

```bash
./scripts/verify-local.sh
```

Observed result:

```text
Backend tests: BUILD SUCCESSFUL
Frontend lint: passed
Frontend tests: 1 file, 2 tests passed
Frontend build: built successfully
Backend demo: Demo completed successfully.
Frontend proxy demo: Frontend demo completed successfully.
Local verification completed successfully.
```

## Pagination Benchmark Re-Run

The benchmark was re-run locally after aligning the script with `AccountJournalQueryService`'s `limit + 1` behavior.

```bash
docker exec -i bankcore-mysql mysql -ubankcore -pbankcore_password bankcore \
  < scripts/sql/cleanup-journal-pagination-benchmark.sql
docker exec -i bankcore-mysql mysql -ubankcore -pbankcore_password bankcore \
  < scripts/sql/seed-journal-pagination-benchmark.sql
```

Observed access pattern:

- First keyset page probed `21` rows.
- Middle keyset page probed `21` rows.
- Offset comparison read `25021` rows to return the probed page.
- Offset was about `109x` slower than the middle keyset page in this synthetic local run.

## Remaining Scope Boundary

Authentication, authorization, real customer identity, regulatory compliance, external payments, audit retention policy, and production observability remain intentionally out of scope. The correct portfolio claim is that BankCore demonstrates transfer correctness and evidence-driven hardening in a controlled local backend, not that it is production banking software.
