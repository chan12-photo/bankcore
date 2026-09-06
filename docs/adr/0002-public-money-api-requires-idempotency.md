# ADR 0002: Public Money Movement Requires Idempotency

## Status

Accepted

## Context

Public deposit and withdrawal endpoints are risky in a portfolio project because they look like external money creation or destruction commands. If such endpoints exist without idempotency, retries can create duplicate money effects.

Internal transfer is a safer MVP command because it moves value between two existing accounts and can be tested against clear invariants:

- Source and destination accounts must differ.
- The sum of balances should be preserved for the two-account transfer.
- A committed transfer must create transaction and journal evidence.
- A failed transfer must leave no partial balance, transaction, journal, or idempotency state.

## Decision

Public deposit and withdrawal APIs are not exposed.

The public money-moving API is internal transfer only, and it requires:

- `X-Caller-Scope`
- `Idempotency-Key`
- A request fingerprint containing version, operation, currency, source account, destination account, and amount

The first implementation uses a single database transaction for idempotency record creation, balance updates, transaction rows, journal rows, and idempotency completion.

That idempotent transfer transaction is an independent command boundary. The service executes both the first claim/run path and the concurrent-conflict replay path with a `REQUIRES_NEW` transaction template, so a future ambient `@Transactional` caller cannot accidentally make the replay query participate in a rollback-only outer transaction.

The logical idempotency identity is still caller scope, operation, and client-provided key, but the raw key is not stored. BankCore stores a domain-separated SHA-256 digest and enforces the database unique constraint on `(caller_scope, operation, idempotency_key_digest)`.

## Consequences

- Retrying the same request can replay the same response without a duplicate money effect.
- Reusing the same idempotency key with a different request is rejected.
- Failed transfers do not leave stuck idempotency rows in the MVP model.
- The idempotent transfer command is not composable into a larger caller transaction without an explicit redesign.
- Database unique-key errors do not expose the raw idempotency key value because the unique value is a digest.
- The later two-transaction `PROCESSING` model remains a useful advanced comparison, but it is not required for the first interview-ready baseline.
