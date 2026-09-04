# ADR 0003: Treat Reconciliation as Core Evidence

## Status

Accepted

## Context

Financial correctness is stronger when the system can detect its own inconsistencies. Balance updates alone are not enough evidence because an account row can be correct-looking while the journal trail is missing or contradictory.

The project needs a simple, inspectable way to compare stored account balances with journal-derived balances.

## Decision

Reconciliation is part of the core MVP evidence.

The reconciliation query derives account balance by summing journal entries:

- `BALANCE_INCREASE` adds the journal amount.
- `BALANCE_DECREASE` subtracts the journal amount.
- Accounts with stored balance different from journal-derived balance are reported.
- Reconciliation reports mismatches but does not repair them.

## Consequences

- Controlled seed funding records journal entries so normal test setup does not create false mismatches.
- Direct balance mutation remains useful only in tests that intentionally prove mismatch detection.
- No-lock race experiments can demonstrate why reconciliation is useful by creating observable mismatch evidence.
