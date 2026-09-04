# BankCore Status

## Current Checkpoint

Status date: 2026-09-05

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
- Public internal transfer API is implemented with required `X-Caller-Scope` and `Idempotency-Key` headers.
- Public transfer API retry tests verify same-response replay and no duplicate transfer effect.
- Idempotency header lengths are validated before database insert to match schema limits.
- Controlled seed funding service records test funds as transaction and journal rows without exposing a public deposit API.
- Account service no longer exposes non-journaled deposit or withdrawal service methods.
- Account balance reconciliation service compares stored balances against journal-derived balances.
- Reconciliation API reports mismatched accounts for evidence and diagnostics.
- Test-only unsafe no-lock race experiment demonstrates stale-balance overwrite and reconciliation mismatch.
- Test-only optimistic locking race experiment demonstrates one successful transfer, one rolled-back transfer, and no reconciliation mismatch.
- Test-only pessimistic write lock race experiment demonstrates row-level serialization and no reconciliation mismatch.
- Transfer API returns stable `ApiErrorResponse` bodies for missing headers and malformed request bodies.
- Request DTOs use Bean Validation for required fields and positive transfer amounts.
- Request DTO validation now also mirrors key database limits for customer names, account numbers, account ids, and transfer amounts.
- Account journal keyset pagination API is implemented.
- Flyway creates `idx_account_journal_account_id_id(account_id, id)` for account journal lookup.
- Local SQL evidence confirms the journal pagination index is present and usable.
- Synthetic 50,000-row journal pagination benchmark compares keyset pagination with offset pagination and the seed script includes both query shapes.
- A `demo` Spring profile creates two journal-funded synthetic demo accounts for repeatable local walkthroughs.
- ADRs document the scope reduction, idempotency requirement, and reconciliation decision.
- GitHub Actions CI is green on the latest pushed `main` commits.
- Core behavior evidence is captured in `docs/evidence/2026-09-04-core-behavior.md`.
- Hardening evidence is captured in `docs/evidence/2026-09-05-hardening.md`.
- Korean portfolio write-up is captured in `docs/portfolio-writeup-ko.md`.
- Korean resume and interview notes are captured in `docs/resume-and-interview-notes-ko.md`.

Current local environment:

- Java: Temurin 25.0.4.1
- Gradle Wrapper: 9.7.1
- Spring Boot: 4.1.1
- Docker: 29.7.2
- MySQL container: 8.4.11
- MySQL isolation level observed locally: `REPEATABLE-READ`
- MySQL autocommit observed locally: `1`

## Next Steps

1. Decide whether optimistic locking conflicts should remain a clear 409-style operational policy or gain bounded retry behavior.
2. Add production-style authentication and authorization only if the project scope expands beyond portfolio evidence.
3. Add optional OpenAPI documentation if this becomes a submitted API project.
4. Choose the final resume bullet wording based on the target role.
