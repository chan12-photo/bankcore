#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${BANKCORE_DEMO_PORT:-18080}"
BASE_URL="http://localhost:${PORT}"
LOG_FILE="${ROOT_DIR}/build/bankcore-demo.log"

cd "${ROOT_DIR}"

mkdir -p build

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

wait_for_health() {
  for _ in {1..60}; do
    if curl -fsS "${BASE_URL}/api/v1/health" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "BankCore demo server did not become healthy. See ${LOG_FILE}" >&2
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

entries = json.loads(sys.argv[1])
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
require_command python3

echo "Starting MySQL with Docker Compose..."
docker compose up -d

echo "Starting BankCore demo server on ${BASE_URL}..."
SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun --args="--server.port=${PORT}" --no-daemon >"${LOG_FILE}" 2>&1 &
SERVER_PID=$!

cleanup() {
  if kill -0 "${SERVER_PID}" >/dev/null 2>&1; then
    kill "${SERVER_PID}" >/dev/null 2>&1 || true
    wait "${SERVER_PID}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

wait_for_health

echo
echo "Health:"
curl -fsS "${BASE_URL}/api/v1/health"
echo

echo
echo "Demo accounts:"
ACCOUNTS_JSON="$(curl -fsS "${BASE_URL}/api/v1/demo/accounts")"
echo "${ACCOUNTS_JSON}"
echo

read -r SOURCE_ACCOUNT_ID DESTINATION_ACCOUNT_ID < <(extract_demo_account_ids "${ACCOUNTS_JSON}")
IDEMPOTENCY_KEY="demo-script-$(date +%Y%m%d%H%M%S)-$$"
TRANSFER_BODY="{\"sourceAccountId\":${SOURCE_ACCOUNT_ID},\"destinationAccountId\":${DESTINATION_ACCOUNT_ID},\"amount\":1000}"

echo
echo "First transfer:"
FIRST_RESPONSE="$(curl -fsS -X POST "${BASE_URL}/api/v1/transfers/internal" \
  -H "Content-Type: application/json" \
  -H "X-Caller-Scope: demo-script" \
  -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
  -d "${TRANSFER_BODY}")"
echo "${FIRST_RESPONSE}"
echo

echo
echo "Replay transfer with the same idempotency key:"
REPLAY_RESPONSE="$(curl -fsS -X POST "${BASE_URL}/api/v1/transfers/internal" \
  -H "Content-Type: application/json" \
  -H "X-Caller-Scope: demo-script" \
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
CONFLICT_STATUS="$(curl -sS -o "${CONFLICT_FILE}" -w "%{http_code}" -X POST "${BASE_URL}/api/v1/transfers/internal" \
  -H "Content-Type: application/json" \
  -H "X-Caller-Scope: demo-script" \
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
echo "Source account journal:"
SOURCE_JOURNAL="$(curl -fsS "${BASE_URL}/api/v1/accounts/${SOURCE_ACCOUNT_ID}/journal-entries?limit=10")"
echo "${SOURCE_JOURNAL}"
assert_journal_contains_transaction "${SOURCE_JOURNAL}" "${TRANSACTION_ID}"
echo

echo
echo "Destination account journal:"
DESTINATION_JOURNAL="$(curl -fsS "${BASE_URL}/api/v1/accounts/${DESTINATION_ACCOUNT_ID}/journal-entries?limit=10")"
echo "${DESTINATION_JOURNAL}"
assert_journal_contains_transaction "${DESTINATION_JOURNAL}" "${TRANSACTION_ID}"
echo

echo
echo "Reconciliation mismatches:"
MISMATCHES="$(curl -fsS "${BASE_URL}/api/v1/reconciliation/account-balances/mismatches")"
echo "${MISMATCHES}"
echo

if [[ "${MISMATCHES}" != "[]" ]]; then
  echo "Expected no reconciliation mismatches." >&2
  exit 1
fi

echo
echo "Demo completed successfully."
