# Frontend Lab Console Evidence

Date: 2026-09-05

## Purpose

The BankCore Lab Console adds a reviewer-friendly UI for the existing backend evidence flow. It is intentionally a local verification console, not a customer banking product.

The console demonstrates:

- Demo account loading through `GET /api/v1/demo/accounts`
- Internal transfer through `POST /api/v1/transfers/internal`
- Idempotent replay with the same caller scope, same idempotency key, and same request body
- Expected HTTP 409 conflict for the same idempotency key with a changed amount
- Transfer-side account journal rows
- Account balance reconciliation mismatch status
- Repeatable demo startup balances for Alice and Bob

## Implementation

- Location: `frontend/`
- Stack: React 19, TypeScript, Vite, TanStack Query
- API client: `frontend/src/api.ts`
- Main UI: `frontend/src/App.tsx`
- Dev proxy: `frontend/vite.config.ts`
- Automated proxy demo: `scripts/demo-frontend.sh`

The default local frontend uses relative `/api` requests. Vite forwards those requests to `http://localhost:8080`, so no browser CORS configuration is required for the local demo path.

## Local Verification

Commands run from `frontend/`:

```bash
npm install
npm ci
npm run lint
npm run build
```

Observed result:

- `npm install`: dependencies up to date, 0 vulnerabilities
- `npm ci`: clean lockfile install passed, 0 vulnerabilities
- `npm run lint`: passed
- `npm run build`: TypeScript build and Vite production build passed
- Vite dev proxy check: `GET http://127.0.0.1:5173/api/v1/demo/accounts` returned demo account JSON through the frontend server

Command run from the repository root:

```bash
./scripts/demo-frontend.sh
```

Observed result:

- The script started the demo backend on `http://localhost:18080`.
- The script started the Vite frontend on `http://127.0.0.1:15173`.
- `GET /api/v1/demo/accounts` through the frontend proxy returned Alice `100000` and Bob `30000`.
- The first transfer through the frontend proxy succeeded.
- Replaying the same request through the frontend proxy returned the identical response.
- Reusing the same idempotency key with amount `1001` returned HTTP `409` with `IDEMPOTENCY_KEY_CONFLICT`.
- Source and destination journal lookups both contained the captured transaction id.
- Reconciliation returned `[]`.

GitHub Actions also runs the frontend dependency install, lint, production build, and frontend proxy demo after the backend test suite.

The full local verification wrapper is:

```bash
./scripts/verify-local.sh
```

It runs backend tests, frontend install/lint/build, backend API demo, and frontend proxy demo.

Build output observed:

```text
dist/index.html
dist/assets/index-*.css
dist/assets/index-*.js
```

## Reviewer Demo

Start the backend from the repository root:

```bash
docker compose up -d
SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun
```

Start the frontend:

```bash
cd frontend
npm install
npm run dev
```

Open:

```text
http://localhost:5173
```

Recommended flow:

1. Confirm Alice and Bob demo accounts are loaded.
2. Run a transfer with the generated idempotency key.
3. Click replay and confirm the replay response matches the first response.
4. Click same-key changed-amount conflict probe and confirm HTTP 409 is shown as expected.
5. Confirm the journal table shows the debit-side and credit-side rows for the captured transfer.
6. Confirm reconciliation shows no mismatches.
