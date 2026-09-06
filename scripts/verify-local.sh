#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "${ROOT_DIR}"

echo "Running backend test suite..."
./gradlew clean test --no-daemon --no-build-cache

echo
echo "Running frontend install, lint, test, and build..."
(
  cd frontend
  npm ci
  npm run lint
  npm run test
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
