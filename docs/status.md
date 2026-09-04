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
2. Implement `FinancialTransaction` and `AccountJournalEntry`.
3. Implement internal transfer with transaction and journal records.
4. Add rollback injection tests that verify final database state from a separate transaction.
5. Add CI and keep it green before expanding the feature set.
