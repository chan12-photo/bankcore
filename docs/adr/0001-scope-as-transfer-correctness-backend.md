# ADR 0001: Scope BankCore as a Transfer Correctness Backend

## Status

Accepted

## Context

The original idea used the name BankCore and included many concepts that appear in real banking systems. That creates a portfolio risk: reviewers may interpret the project as claiming production-grade core banking, general ledger, compliance, fraud, payment-network, or operational reliability capabilities.

The strongest interview value is not broad feature count. It is reproducible evidence that a Java/Spring and MySQL backend can preserve financial transfer invariants under rollback, idempotency, reconciliation, and concurrency scenarios.

## Decision

BankCore is scoped as an experimental financial transfer correctness backend.

It will intentionally focus on:

- Account balance invariants
- Transaction boundaries
- Journal evidence
- Idempotency replay and conflict handling
- Reconciliation
- Optimistic and pessimistic locking experiments
- SQL query plans and pagination evidence

It will not claim to be a real banking core system.

## Consequences

- Public claims become easier to defend in an interview.
- The project can show deep backend judgment with less surface area.
- Non-goals must remain visible in README and threat-model documentation.
- Future features should be rejected unless they strengthen correctness evidence.
