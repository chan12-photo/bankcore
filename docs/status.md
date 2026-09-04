# BankCore Status

## Current Checkpoint

Status date: 2026-09-04

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
- Public internal transfer API is implemented with required `X-Caller-Scope` and `Idempotency-Key` headers.
- Public transfer API retry tests verify same-response replay and no duplicate transfer effect.
- Controlled seed funding service records test funds as transaction and journal rows without exposing a public deposit API.
- Account balance reconciliation service compares stored balances against journal-derived balances.
- Reconciliation API reports mismatched accounts for evidence and diagnostics.
- Test-only unsafe no-lock race experiment demonstrates stale-balance overwrite and reconciliation mismatch.
- Test-only optimistic locking race experiment demonstrates one successful transfer, one rolled-back transfer, and no reconciliation mismatch.
- Core behavior evidence is captured in `docs/evidence/2026-09-04-core-behavior.md`.

Current local environment:

- Java: Temurin 25.0.4.1
- Gradle Wrapper: 9.7.1
- Spring Boot: 4.1.1
- Docker: 29.7.2
- MySQL container: 8.4.11
- MySQL isolation level observed locally: `REPEATABLE-READ`
- MySQL autocommit observed locally: `1`

## Next Steps

1. Add a controlled seed path for test funds without presenting deposit as a customer-facing money API.
2. Add pessimistic lock concurrency experiment.
3. Capture SQL query plans and pagination measurements.
4. Add portfolio-facing ADRs that explain scope reductions and tradeoffs.
5. Refine API error responses for missing headers and malformed request bodies.
