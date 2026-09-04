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
- Customer creation API
- Account creation API

External money-moving APIs are intentionally not exposed at this stage. Deposit and withdrawal behavior currently exists as domain logic for fixtures and future transfer implementation; the submitted external money command will be internal transfer with an idempotency contract.

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

## Key Documents

- [PLAN.md](PLAN.md)
- [docs/status.md](docs/status.md)
- [docs/invariants.md](docs/invariants.md)
- [docs/threat-model.md](docs/threat-model.md)
