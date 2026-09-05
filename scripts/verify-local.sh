#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "${ROOT_DIR}"

echo "Running backend test suite..."
./gradlew test --no-daemon

echo
echo "Running frontend install, lint, and build..."
(
  cd frontend
  npm ci
  npm run lint
  npm run build
)

echo
echo "Running backend API demo..."
./scripts/demo.sh

echo
echo "Running frontend proxy demo..."
./scripts/demo-frontend.sh

echo
echo "Local verification completed successfully."
