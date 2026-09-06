# BankCore Status

## Current Checkpoint

Status date: 2026-09-06

Completed:

- Spring Boot application boots.
- Health API is implemented and tested.
- Docker Compose starts MySQL 8.4.
- Flyway creates the initial `customer` and `account` tables.
- Hibernate validates schema instead of creating or updating it.
- Open Session in View is disabled for clearer service transaction boundaries.
- Customer and Account entities are mapped.
- Customer and Account repositories are tested with Testcontainers MySQL.
- Customer creation API is implemented.
- Account creation API is implemented.
- Public deposit and withdrawal endpoints were removed from the MVP scope.
- Flyway creates `financial_transaction` and `account_journal_entry` tables.
- Internal transfer service moves money and records one transaction with two journal entries.
- Rollback injection tests verify balance, transaction, and journal rows are all rolled back after a flushed write.
- Flyway creates the single-transaction `idempotency_record` table with scoped unique key.
- Service-layer idempotent internal transfer replays the committed result for the same request.
- Service-layer idempotent internal transfer rejects the same key with a different request fingerprint.
- Failed idempotent transfers roll back the idempotency record together with balances and journal rows.
- Concurrent same-key idempotent transfer requests are covered by an integration test and produce one committed money effect.
- Concurrent same-key different-fingerprint transfer requests are covered by an integration test and converge to one success plus one conflict.
- Idempotent transfer claim/run and concurrent-conflict replay paths use explicit `REQUIRES_NEW` transaction boundaries.
- An ambient `@Transactional` caller integration test verifies same-key concurrent replay still produces one committed money effect.
- Database integrity exceptions are translated only when the matched database constraint is the expected business constraint.
- Public internal transfer API is implemented with required `X-Caller-Scope` and `Idempotency-Key` headers.
- Public transfer API retry tests verify same-response replay and no duplicate transfer effect.
- Idempotency header lengths are validated before database insert to match schema limits.
- Raw idempotency keys are transformed into scoped SHA-256 digests before persistence, so the database unique key no longer stores the original key text.
- Idempotency key uniqueness is enforced on a binary digest, making raw key semantics case-sensitive.
- Controlled seed funding service records test funds as transaction and journal rows without exposing a public deposit API.
- Account service no longer exposes non-journaled deposit or withdrawal service methods.
- Internal transfer loads both transfer accounts with pessimistic write locks in deterministic account-id order.
- Opposite-direction concurrent transfers are covered by an ordered-lock integration test to reduce deadlock risk.
- Account balance reconciliation service compares stored balances against journal-derived balances.
- Reconciliation API reports mismatched accounts for evidence and diagnostics.
- Transaction journal reconciliation service detects malformed transaction journal structures, including missing entries, wrong movement directions, same-account transfer pairs, and amount mismatches.
- Reconciliation API reports transaction journal mismatches separately from account balance mismatches.
- Test-only unsafe no-lock race experiment demonstrates stale-balance overwrite and reconciliation mismatch.
- Test-only optimistic locking race experiment demonstrates one successful transfer, one rolled-back transfer, and no reconciliation mismatch.
- Optimistic locking failures are mapped to a stable `409 CONFLICT` API response instead of leaking as a generic server error.
- Test-only pessimistic write lock race experiment demonstrates row-level serialization and no reconciliation mismatch.
- Transfer API returns stable `ApiErrorResponse` bodies for missing headers and malformed request bodies.
- Request DTOs use Bean Validation for required fields and positive transfer amounts.
- Request DTO validation now also mirrors key database limits for customer names, account numbers, account ids, and transfer amounts.
- Customer and account services also validate length limits before repository/database use.
- Account journal keyset pagination API is implemented.
- Flyway creates `idx_account_journal_account_id_id(account_id, id)` for account journal lookup.
- Local SQL evidence confirms the journal pagination index is present and usable.
- Synthetic 50,000-row journal pagination benchmark compares keyset pagination with offset pagination and the seed script includes both query shapes.
- A `demo` Spring profile creates two journal-funded synthetic demo accounts for repeatable local walkthroughs.
- Existing demo accounts are rebalanced back to Alice `100000` and Bob `30000` on demo startup using journaled transfer or controlled seed funding, not direct balance edits.
- `scripts/demo.sh` runs an automated local demo for health, demo accounts, idempotent transfer replay, same-key changed-body conflict, source and destination journal lookup, account reconciliation, and transaction journal reconciliation.
- `scripts/demo-frontend.sh` runs an automated local frontend proxy demo for the Vite root page, demo accounts, idempotent replay, same-key changed-body conflict, source and destination journal lookup, account reconciliation, and transaction journal reconciliation.
- `scripts/verify-local.sh` runs a clean backend test suite, frontend install/lint/test/build, backend API demo, and frontend proxy demo in one command.
- Demo scripts wait for MySQL container health before starting the backend and print recent logs on readiness failure.
- OpenAPI JSON and Swagger UI are available through springdoc-openapi and covered by integration tests.
- React/TypeScript/Vite BankCore Lab Console is implemented under `frontend/`.
- The lab console uses TanStack Query to load demo accounts, run idempotent internal transfers, replay the same request, probe same-key changed-body conflicts, show journal rows, and show reconciliation status.
- The lab console checks both account balance mismatches and transaction journal mismatches.
- Vite dev proxy forwards local `/api` calls to the Spring Boot backend on `http://localhost:8080`.
- Frontend lint, production build, and the frontend proxy demo are covered locally and in GitHub Actions CI.
- Frontend jsdom behavior testing is covered with Vitest and React Testing Library.
- ADRs document the scope reduction, idempotency requirement, and reconciliation decision.
- GitHub Actions CI is green on the latest pushed `main` commits.
- Core behavior evidence is captured in `docs/evidence/2026-09-04-core-behavior.md`.
- Hardening evidence is captured in `docs/evidence/2026-09-05-hardening.md`.
- Frontend lab console evidence is captured in `docs/evidence/2026-09-05-frontend-lab-console.md`.
- Final local and CI verification evidence is captured in `docs/evidence/2026-09-06-local-and-ci-verification.md`.
- Korean submission checklist is captured in `docs/submission-checklist-ko.md`.
- Korean portfolio write-up is captured in `docs/portfolio-writeup-ko.md`.
- Korean resume and interview notes are captured in `docs/resume-and-interview-notes-ko.md`.

Current local environment:

- Java: Temurin 25.0.4.1
- Gradle Wrapper: 9.7.1
- Spring Boot: 4.1.1
- Node.js: 25.8.1
- npm: 11.11.0
- Docker: 29.7.2
- MySQL container: 8.4.11
- MySQL isolation level observed locally: `REPEATABLE-READ`
- MySQL autocommit observed locally: `1`

## Next Steps

1. Add bounded retry for selected optimistic-lock conflicts only if the project scope expands toward production-style operations.
2. Add production-style authentication and authorization only if the project scope expands beyond portfolio evidence.
3. Capture a short screen recording or screenshots of the Lab Console flow if the portfolio submission platform supports media.
4. Choose the final resume bullet wording based on the target role.
5. Keep production banking, authentication, authorization, compliance, and external payment integrations explicitly out of scope unless the project direction changes.

Recent verified CI:

- Commit: `e9993a3f39ad9e19f0e1c766b7baf600a06d4e6c`
- Run: `https://github.com/chan12-photo/bankcore/actions/runs/34016732296`
- Result: success
