# BankCore Lab Console

React, TypeScript, Vite, and TanStack Query UI for demonstrating the BankCore backend evidence flow.

This is not a customer-facing banking app. It is a local verification console for the portfolio reviewer: demo accounts, internal transfer, idempotent replay, intentional conflict probing, journal rows, and reconciliation status are shown together.

## Run Locally

Start the backend from the repository root with the demo profile:

```bash
docker compose up -d
SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun
```

Then start the console:

```bash
cd frontend
npm install
npm run dev
```

Open:

```text
http://localhost:5173
```

The default Vite dev proxy forwards `/api` to `http://localhost:8080`, so browser CORS setup is not required for local development.

## Verification Flow

1. Load demo accounts from `GET /api/v1/demo/accounts`.
2. Run `POST /api/v1/transfers/internal` with `X-Caller-Scope` and `Idempotency-Key`.
3. Replay the exact same request and confirm the response matches the first response.
4. Reuse the same key with a changed amount and confirm the expected HTTP 409 conflict.
5. Read both transfer-side journal rows.
6. Confirm reconciliation returns no balance mismatches.

## Environment

```bash
cp .env.example .env.local
```

Useful variables:

```text
VITE_BANKCORE_API_BASE_URL=
VITE_BANKCORE_PROXY_TARGET=http://localhost:8080
```

Keep `VITE_BANKCORE_API_BASE_URL` blank during local development to use the Vite proxy. Set `VITE_BANKCORE_PROXY_TARGET` only if the Spring Boot backend is running on a different origin.

## Checks

```bash
npm run lint
npm run build
```
