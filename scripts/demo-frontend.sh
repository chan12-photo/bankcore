#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_PORT="${BANKCORE_FRONTEND_DEMO_BACKEND_PORT:-18080}"
FRONTEND_PORT="${BANKCORE_FRONTEND_DEMO_FRONTEND_PORT:-15173}"
BACKEND_URL="http://localhost:${BACKEND_PORT}"
FRONTEND_URL="http://127.0.0.1:${FRONTEND_PORT}"
BACKEND_LOG_FILE="${ROOT_DIR}/build/bankcore-frontend-demo-backend.log"
FRONTEND_LOG_FILE="${ROOT_DIR}/build/bankcore-frontend-demo-vite.log"

cd "${ROOT_DIR}"

mkdir -p build

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

wait_for_url() {
  local url="$1"
  local label="$2"

  for _ in {1..90}; do
    if curl -fsS "${url}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done

  echo "${label} did not become available: ${url}" >&2
  return 1
}

wait_for_mysql() {
  for _ in {1..90}; do
    local health_status
    health_status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}' bankcore-mysql 2>/dev/null || true)"
    if [[ "${health_status}" == "healthy" ]]; then
      return 0
    fi
    sleep 2
  done

  echo "MySQL container did not become healthy." >&2
  docker compose ps >&2 || true
  return 1
}

extract_demo_account_ids() {
  python3 - "$1" <<'PY'
import json
import sys

accounts = json.loads(sys.argv[1])
by_number = {account["accountNumber"]: account for account in accounts}
alice = by_number["DEMO-ALICE-001"]
bob = by_number["DEMO-BOB-001"]
print(alice["accountId"], bob["accountId"])
PY
}

extract_transaction_id() {
  python3 - "$1" <<'PY'
import json
import sys

print(json.loads(sys.argv[1])["transactionId"])
PY
}

assert_journal_contains_transaction() {
  python3 - "$1" "$2" <<'PY'
import json
import sys

page = json.loads(sys.argv[1])
entries = page["items"]
transaction_id = int(sys.argv[2])
if not any(entry["transactionId"] == transaction_id for entry in entries):
    raise SystemExit(f"Journal did not include transaction {transaction_id}")
PY
}

assert_idempotency_conflict() {
  python3 - "$1" <<'PY'
import json
import sys

payload = json.loads(sys.argv[1])
if payload.get("code") != "IDEMPOTENCY_KEY_CONFLICT":
    raise SystemExit(f"Expected IDEMPOTENCY_KEY_CONFLICT, got {payload}")
PY
}

require_command curl
require_command docker
require_command npm
require_command python3

echo "Starting MySQL with Docker Compose..."
docker compose up -d
wait_for_mysql

echo "Starting BankCore demo backend on ${BACKEND_URL}..."
SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun --args="--server.port=${BACKEND_PORT}" --no-daemon >"${BACKEND_LOG_FILE}" 2>&1 &
BACKEND_PID=$!
FRONTEND_PID=""

cleanup() {
  if [[ -n "${FRONTEND_PID}" ]] && kill -0 "${FRONTEND_PID}" >/dev/null 2>&1; then
    kill "${FRONTEND_PID}" >/dev/null 2>&1 || true
    wait "${FRONTEND_PID}" >/dev/null 2>&1 || true
  fi
  if kill -0 "${BACKEND_PID}" >/dev/null 2>&1; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

if ! wait_for_url "${BACKEND_URL}/api/v1/health" "BankCore backend"; then
  tail -n 120 "${BACKEND_LOG_FILE}" >&2 || true
  exit 1
fi

echo "Installing frontend dependencies from lockfile..."
(cd frontend && npm ci)

echo "Starting Vite frontend on ${FRONTEND_URL}..."
(
  cd frontend
  VITE_BANKCORE_PROXY_TARGET="${BACKEND_URL}" npm run dev -- --host 127.0.0.1 --port "${FRONTEND_PORT}" --strictPort
) >"${FRONTEND_LOG_FILE}" 2>&1 &
FRONTEND_PID=$!

if ! wait_for_url "${FRONTEND_URL}" "Vite frontend"; then
  tail -n 120 "${FRONTEND_LOG_FILE}" >&2 || true
  exit 1
fi

echo
echo "Frontend root:"
curl -fsS "${FRONTEND_URL}" >/dev/null
echo "OK"

echo
echo "Demo accounts through frontend proxy:"
ACCOUNTS_JSON="$(curl -fsS "${FRONTEND_URL}/api/v1/demo/accounts")"
echo "${ACCOUNTS_JSON}"
echo

read -r SOURCE_ACCOUNT_ID DESTINATION_ACCOUNT_ID < <(extract_demo_account_ids "${ACCOUNTS_JSON}")
IDEMPOTENCY_KEY="frontend-demo-$(date +%Y%m%d%H%M%S)-$$"
TRANSFER_BODY="{\"sourceAccountId\":${SOURCE_ACCOUNT_ID},\"destinationAccountId\":${DESTINATION_ACCOUNT_ID},\"amount\":1000}"

echo
echo "First transfer through frontend proxy:"
FIRST_RESPONSE="$(curl -fsS -X POST "${FRONTEND_URL}/api/v1/transfers/internal" \
  -H "Content-Type: application/json" \
  -H "X-Caller-Scope: frontend-demo" \
  -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
  -d "${TRANSFER_BODY}")"
echo "${FIRST_RESPONSE}"
echo

echo
echo "Replay transfer through frontend proxy:"
REPLAY_RESPONSE="$(curl -fsS -X POST "${FRONTEND_URL}/api/v1/transfers/internal" \
  -H "Content-Type: application/json" \
  -H "X-Caller-Scope: frontend-demo" \
  -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
  -d "${TRANSFER_BODY}")"
echo "${REPLAY_RESPONSE}"
echo

if [[ "${FIRST_RESPONSE}" != "${REPLAY_RESPONSE}" ]]; then
  echo "Replay response differed from first response." >&2
  exit 1
fi

echo
echo "Same key with changed amount should return 409:"
CONFLICT_BODY="{\"sourceAccountId\":${SOURCE_ACCOUNT_ID},\"destinationAccountId\":${DESTINATION_ACCOUNT_ID},\"amount\":1001}"
CONFLICT_FILE="$(mktemp)"
CONFLICT_STATUS="$(curl -sS -o "${CONFLICT_FILE}" -w "%{http_code}" -X POST "${FRONTEND_URL}/api/v1/transfers/internal" \
  -H "Content-Type: application/json" \
  -H "X-Caller-Scope: frontend-demo" \
  -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
  -d "${CONFLICT_BODY}")"
CONFLICT_RESPONSE="$(cat "${CONFLICT_FILE}")"
rm -f "${CONFLICT_FILE}"
echo "${CONFLICT_RESPONSE}"
echo

if [[ "${CONFLICT_STATUS}" != "409" ]]; then
  echo "Expected HTTP 409 for same-key changed-body probe, got ${CONFLICT_STATUS}." >&2
  exit 1
fi
assert_idempotency_conflict "${CONFLICT_RESPONSE}"

TRANSACTION_ID="$(extract_transaction_id "${FIRST_RESPONSE}")"

echo
echo "Source account journal through frontend proxy:"
SOURCE_JOURNAL="$(curl -fsS "${FRONTEND_URL}/api/v1/accounts/${SOURCE_ACCOUNT_ID}/journal-entries?limit=10")"
echo "${SOURCE_JOURNAL}"
assert_journal_contains_transaction "${SOURCE_JOURNAL}" "${TRANSACTION_ID}"
echo

echo
echo "Destination account journal through frontend proxy:"
DESTINATION_JOURNAL="$(curl -fsS "${FRONTEND_URL}/api/v1/accounts/${DESTINATION_ACCOUNT_ID}/journal-entries?limit=10")"
echo "${DESTINATION_JOURNAL}"
assert_journal_contains_transaction "${DESTINATION_JOURNAL}" "${TRANSACTION_ID}"
echo

echo
echo "Reconciliation mismatches through frontend proxy:"
MISMATCHES="$(curl -fsS "${FRONTEND_URL}/api/v1/reconciliation/account-balances/mismatches")"
echo "${MISMATCHES}"
echo

if [[ "${MISMATCHES}" != "[]" ]]; then
  echo "Expected no reconciliation mismatches." >&2
  exit 1
fi

echo
echo "Transaction journal mismatches through frontend proxy:"
TRANSACTION_MISMATCHES="$(curl -fsS "${FRONTEND_URL}/api/v1/reconciliation/transaction-journals/mismatches")"
echo "${TRANSACTION_MISMATCHES}"
echo

if [[ "${TRANSACTION_MISMATCHES}" != "[]" ]]; then
  echo "Expected no transaction journal mismatches." >&2
  exit 1
fi

echo
echo "Frontend demo completed successfully."
