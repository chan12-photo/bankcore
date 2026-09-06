# Local and CI Verification Evidence

Date: 2026-09-06 KST

## Purpose

This note captures the final local and CI verification path for the current BankCore portfolio checkpoint.

The checkpoint proves that the project is not only implemented, but also repeatably runnable from a fresh reviewer path:

- Backend tests pass against Testcontainers MySQL.
- Frontend dependencies install from the lockfile.
- Frontend lint, jsdom behavior tests, and production build pass.
- Backend API demo proves database-backed health readiness, idempotent transfer, replay, conflict handling, journal lookup, account reconciliation, and transaction journal reconciliation.
- Frontend proxy demo proves the same backend evidence flow through the Vite `/api` proxy.
- GitHub Actions runs the same backend, frontend, and frontend proxy demo checks on `main`.

## Verified Code Checkpoint

- Commit: `a441d055ac9dabf7e2166f4c078c4d8e3bb1c3c3`
- Short commit: `a441d05`
- Branch: `main`
- CI workflow: `CI`
- CI run: `https://github.com/chan12-photo/bankcore/actions/runs/34031445684`
- CI result: success

The successful CI job completed these steps:

- `./gradlew test --no-daemon`
- `npm ci`
- `npm run lint`
- `npm run test`
- `npm run build`
- `./scripts/demo-frontend.sh`

This checkpoint also includes the transfer concurrency hardening pass, database-backed health readiness, cursor-shaped journal pagination responses, database enum check constraints, localhost-only default binds, environment-overridable datasource settings, and semantic demo-script JSON assertions.

## Local Verification Command

The preferred local reviewer command is:

```bash
cd bankcore
./scripts/verify-local.sh
```

This wrapper runs:

```bash
./gradlew clean test --no-daemon --no-build-cache
cd frontend && npm ci && npm run lint && npm run test && npm run build
./scripts/demo.sh
./scripts/demo-frontend.sh
```

Observed result:

- Backend tests completed successfully.
- Frontend clean install completed successfully.
- Frontend lint completed successfully.
- Frontend jsdom behavior test completed successfully.
- Frontend production build completed successfully.
- Backend demo completed successfully, including the database-backed health check.
- Frontend proxy demo completed successfully.
- Final wrapper output: `Local verification completed successfully.`

## Demo Script Hardening

The demo scripts now wait for the Docker Compose MySQL container to report `healthy` before starting the Spring Boot application. This prevents a timing-dependent CI failure where the backend starts before MySQL is ready to accept connections.

The scripts also print recent backend or frontend logs if readiness checks fail. This keeps future CI failures diagnosable without rerunning the job blindly.

Updated scripts:

- `scripts/demo.sh`
- `scripts/demo-frontend.sh`

## What This Verifies

The automated evidence path verifies the core portfolio claims:

- Demo accounts are reset to Alice `100000` and Bob `30000` using journaled setup behavior.
- A first internal transfer commits exactly one transaction effect.
- Replaying the same idempotency key and same request returns the same result.
- Reusing the same idempotency key with a changed amount returns `409` and `IDEMPOTENCY_KEY_CONFLICT`.
- Source and destination account journals both contain the captured transfer transaction.
- Account balance reconciliation returns `[]`, meaning stored balances match journal-derived balances.
- Transaction journal reconciliation returns `[]`, meaning normal seed and transfer transaction journal structures match the expected invariants.
- The React/Vite Lab Console can reach those backend APIs through the local frontend proxy.
