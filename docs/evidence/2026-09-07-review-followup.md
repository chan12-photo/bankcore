# External Review Follow-Up Evidence - 2026-09-07

## Purpose

This pass closes the highest-value follow-up gaps from the second external review without expanding BankCore beyond its portfolio scope. The focus is proof quality: replay snapshots, rollback-and-retry behavior, frontend journal verification, and lock-contention evidence.

## Changes

- Strengthened the Lab Console journal proof so a green result requires the debit row, credit row, movement types, amounts, and both `balanceAfter` snapshots to match the transfer response.
- Updated the lost-response UI copy to avoid claiming the first request definitely reached or committed on the server when the browser only observed a network failure.
- Strengthened the lost-response frontend test so the retry must preserve the same `Idempotency-Key`, `X-Caller-Scope`, and JSON request body.
- Added a frontend regression test proving a wrong journal `balanceAfter` snapshot fails the proof instead of showing a green invariant.
- Added a service integration test proving a fault-injected transfer that fails after journal flush rolls back fully, then the same idempotency key and same body can be retried to produce one committed result.
- Added a service integration test proving replay of an older idempotent transfer returns the original `sourceBalanceAfter` and `destinationBalanceAfter` even after later transfers changed both account balances.
- Added a service integration test proving the same idempotency key with the same amount but a different destination account is rejected as a fingerprint conflict.
- Narrowed idempotent replay `balance_after` validation from a global journal window scan to the target transaction rows plus each involved account's prior journal history.
- Strengthened the pessimistic-lock experiment so the second transfer reaches the source-lock attempt and remains waiting while the first transaction still holds the row lock.

## Targeted Verification

Frontend targeted checks:

```bash
cd frontend
npm run test -- --run
npm run lint
npm run build
```

Observed result:

```text
Vitest: 1 file, 3 tests passed
Frontend lint: passed
Frontend build: built successfully
```

Backend targeted checks:

```bash
./gradlew test --tests com.bankcore.service.TransferServiceConcurrencyRetryTest --no-daemon
./gradlew test --tests com.bankcore.service.TransferServiceIntegrationTest --tests com.bankcore.service.PessimisticLockTransferConcurrencyTest --no-daemon
```

Observed result:

```text
BUILD SUCCESSFUL
```

Full local verification:

```bash
./scripts/verify-local.sh
```

Observed result:

```text
Backend tests: BUILD SUCCESSFUL
Frontend lint: passed
Frontend tests: 1 file, 3 tests passed
Frontend build: built successfully
Backend demo: Demo completed successfully.
Frontend proxy demo: Frontend demo completed successfully.
Local verification completed successfully.
```

## Notes

The replay query is now scoped to the target transaction's accounts, but the full transaction-journal reconciliation endpoint intentionally keeps its broader scan because that endpoint is a diagnostic sweep, not a hot replay path.

Production banking concerns such as authentication, authorization, regulatory compliance, immutable audit retention, and external payment integration remain intentionally out of scope.
