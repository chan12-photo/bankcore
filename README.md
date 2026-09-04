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

Create a synthetic customer and account:

```bash
curl -X POST http://localhost:8080/api/v1/customers \
  -H "Content-Type: application/json" \
  -d '{"name":"Chanil Park"}'

curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{"customerId":1,"accountNumber":"100-000-000001"}'
```

Idempotent internal transfer:

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
- [docs/portfolio-writeup-ko.md](docs/portfolio-writeup-ko.md)
- [docs/resume-and-interview-notes-ko.md](docs/resume-and-interview-notes-ko.md)
- [docs/adr/0001-scope-as-transfer-correctness-backend.md](docs/adr/0001-scope-as-transfer-correctness-backend.md)
- [docs/adr/0002-public-money-api-requires-idempotency.md](docs/adr/0002-public-money-api-requires-idempotency.md)
- [docs/adr/0003-reconciliation-as-core-evidence.md](docs/adr/0003-reconciliation-as-core-evidence.md)
