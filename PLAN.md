# BankCore Implementation Plan

## Decision

BankCore will be implemented as an experimental financial transfer backend, not as a complete banking core system.

The current implementation baseline is:

- Keep Java 25, Spring Boot 4.1.1, MySQL 8.4, Flyway, JPA, Testcontainers.
- Keep the focus on transaction boundaries, concurrency failures, idempotency, reconciliation, and SQL evidence.
- Treat this file as the repository implementation baseline.
- Treat older external planning documents as background material, not implementation authority.

## Revised MVP

The MVP external money command is internal transfer.

Deposit and withdrawal are not public customer-facing APIs in the MVP. They may be used as domain behavior for test data, fixtures, and future controlled seed tooling.

The MVP must prove:

- Account creation starts with zero balance.
- Successful internal transfer creates one committed financial transaction and two account journal entries. This is implemented for the internal service layer.
- Failed transfer leaves no partial balance, transaction, or journal state. This is covered by rollback injection tests after source withdrawal and after journal flush.
- No-lock behavior can break an invariant under concurrency.
- Optimistic and pessimistic strategies both preserve correctness under their intended tests.
- Production internal transfer uses deterministic ordered pessimistic account locks to reduce opposite-direction deadlock risk.
- Idempotency is scoped by caller, operation, and key. This is implemented at the service layer for internal transfer.
- Same scoped key and same request has at most one committed money effect. This is covered by replay tests and a concurrent same-key integration test.
- Same scoped key and different request is rejected. This is covered by fingerprint conflict tests.
- Stored account balance and journal-derived balance can be reconciled.
- Controlled seed funding exists for test data without creating a public deposit API.
- SQL pagination and index changes are measured with raw results.

## Idempotency Path

The first implementation target is a single-transaction MVP:

1. Insert or claim an idempotency record scoped by caller, operation, and key.
2. Lock or version-check accounts according to the selected strategy.
3. Validate business invariants.
4. Update balances.
5. Insert the committed financial transaction and journal entries.
6. Persist the terminal idempotency result in the same database transaction.

The previous two-transaction PROCESSING model remains a later comparison experiment. It is useful, but it introduces progress and recovery questions that should not block the first interview-ready MVP.

The current implementation also recovers from a concurrent unique-key race by replaying the winning idempotency record in a fresh transaction. This keeps the single-transaction MVP while avoiding duplicate transfer effects and avoiding a raw database constraint error response for same-key retries.

## Non-Goals

- Real personal data or real account numbers
- Real bank, card, or payment network integration
- Complete accounting general ledger
- Production authentication and authorization
- Multi-currency or foreign exchange
- Redis, Kafka, outbox, Kubernetes, cloud deployment, or microservices
- Production SLA, compliance certification, AML, KYC, or FDS

## Progress Rule

Do not add optional infrastructure until the repository contains runnable code, tests, raw experiment outputs, and documentation for the current core behavior.

## Current Boundary

Internal transfer is exposed through a public HTTP endpoint only with the idempotency contract. Public deposit and withdrawal endpoints remain out of scope.
