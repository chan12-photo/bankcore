# Threat Model

## Scope

BankCore is a portfolio project using synthetic data. It is not a production banking system and does not implement complete authentication, authorization, regulatory compliance, or fraud detection.

## In Scope

- Input validation for money amounts and account identifiers
- No raw SQL string concatenation for user-controlled values
- Standardized error responses
- No real personal data
- No real bank accounts
- No secrets committed beyond local development placeholders
- Logs should not contain full request payloads, raw idempotency keys, passwords, JDBC URLs, or complete account identifiers
- Raw idempotency keys should not be stored in database rows or unique-index values; BankCore stores a scoped SHA-256 digest instead.

## Out of Scope

- JWT and OAuth2
- Customer identity verification
- Real object-level authorization
- Real audit immutability
- Encryption and key management
- Production incident response
- Regulatory certification

## Important Limitation

Because authentication and authorization are intentionally out of scope, this project must not claim that it protects real customer accounts. The correct claim is narrower: it demonstrates transaction correctness, concurrency behavior, idempotency design, reconciliation, and SQL performance under controlled conditions.
