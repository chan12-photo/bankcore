# BankCore

BankCore is a Java/Spring and MySQL project for proving financial transfer correctness with real database transactions, locking, idempotency, reconciliation, and SQL query plans.

This is not a real banking core, general ledger, compliance system, or production security implementation. The project uses synthetic data only and focuses on reproducible backend evidence: failing scenarios, fixes, tests, raw measurements, and ADRs.

## Current Scope

- Java 25, Spring Boot 4.1.1, Gradle
- MySQL 8.4 with InnoDB through Docker Compose
- Flyway as the database schema source of truth
- Hibernate `ddl-auto=validate`
- Testcontainers MySQL integration tests
- Customer and Account domain model
- Financial transaction and account journal domain model
- Customer creation API
- Account creation API
- Internal transfer service with rollback integration tests
- Service-layer idempotency for internal transfer
- Public internal transfer API requiring caller scope and idempotency key
- Controlled seed funding service for journaled test data
- Account balance reconciliation API
- Account journal keyset pagination API

Public deposit and withdrawal APIs are intentionally not exposed. Deposit and withdrawal behavior exists as domain logic for fixtures and controlled setup only; public money movement is limited to idempotent internal transfer.

## Run

```bash
docker compose up -d
./gradlew test
./gradlew bootRun
```

Health check:

```bash
curl http://localhost:8080/api/v1/health
```

## Quick Demo

Use the `demo` profile when you want a clean portfolio walkthrough with two pre-funded synthetic accounts.

```bash
docker compose up -d
SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun
```

In another terminal:

```bash
curl http://localhost:8080/api/v1/demo/accounts
```

Example response:

```json
[
  {
    "accountId": 1,
    "customerId": 1,
    "customerName": "Alice Demo",
    "accountNumber": "DEMO-ALICE-001",
    "balance": 100000
  },
  {
    "accountId": 2,
    "customerId": 2,
    "customerName": "Bob Demo",
    "accountNumber": "DEMO-BOB-001",
    "balance": 30000
  }
]
```

Then transfer money using the returned account ids:

```bash
curl -X POST http://localhost:8080/api/v1/transfers/internal \
  -H "Content-Type: application/json" \
  -H "X-Caller-Scope: portfolio-demo" \
  -H "Idempotency-Key: demo-transfer-001" \
  -d '{"sourceAccountId":1,"destinationAccountId":2,"amount":1000}'
```

Send the exact same request again to verify idempotent replay. The response should contain the same `transactionId` and `transactionKey`, and the money effect should not be duplicated.

Check reconciliation after the transfer:

```bash
curl http://localhost:8080/api/v1/reconciliation/account-balances/mismatches
```

A healthy journaled flow returns:

```json
[]
```

## Manual API Flow

Create a synthetic customer and zero-balance account:

```bash
curl -X POST http://localhost:8080/api/v1/customers \
  -H "Content-Type: application/json" \
  -d '{"name":"Chanil Park"}'

curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{"customerId":1,"accountNumber":"100-000-000001"}'
```

Account numbers are unique. If the same account number already exists, the API returns `DUPLICATE_ACCOUNT_NUMBER`.

New accounts intentionally start at `balance = 0`. The normal public API does not expose deposit or withdrawal, so use the demo profile for a ready-to-transfer walkthrough.

Idempotent internal transfer request shape:

```bash
curl -X POST http://localhost:8080/api/v1/transfers/internal \
  -H "Content-Type: application/json" \
  -H "X-Caller-Scope: portfolio-demo" \
  -H "Idempotency-Key: demo-transfer-001" \
  -d '{"sourceAccountId":1,"destinationAccountId":2,"amount":1000}'
```

Find account balance reconciliation mismatches:

```bash
curl http://localhost:8080/api/v1/reconciliation/account-balances/mismatches
```

Read recent account journal entries with keyset pagination:

```bash
curl "http://localhost:8080/api/v1/accounts/1/journal-entries?limit=20"
curl "http://localhost:8080/api/v1/accounts/1/journal-entries?beforeEntryId=100&limit=20"
```

## Key Documents

- [PLAN.md](PLAN.md)
- [docs/status.md](docs/status.md)
- [docs/invariants.md](docs/invariants.md)
- [docs/threat-model.md](docs/threat-model.md)
- [docs/evidence/2026-09-04-core-behavior.md](docs/evidence/2026-09-04-core-behavior.md)
- [docs/evidence/2026-09-04-journal-pagination-benchmark.md](docs/evidence/2026-09-04-journal-pagination-benchmark.md)
- [docs/evidence/2026-09-05-hardening.md](docs/evidence/2026-09-05-hardening.md)
- [docs/portfolio-writeup-ko.md](docs/portfolio-writeup-ko.md)
- [docs/resume-and-interview-notes-ko.md](docs/resume-and-interview-notes-ko.md)
- [docs/adr/0001-scope-as-transfer-correctness-backend.md](docs/adr/0001-scope-as-transfer-correctness-backend.md)
- [docs/adr/0002-public-money-api-requires-idempotency.md](docs/adr/0002-public-money-api-requires-idempotency.md)
- [docs/adr/0003-reconciliation-as-core-evidence.md](docs/adr/0003-reconciliation-as-core-evidence.md)
