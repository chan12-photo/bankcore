#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "${ROOT_DIR}"

mkdir -p build

SHORT_SHA="$(git rev-parse --short HEAD)"
OUTPUT_PATH="build/BankCore-portfolio-${SHORT_SHA}.zip"

if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "Working tree has uncommitted changes. Commit or stash them before creating an archive." >&2
  exit 1
fi

git archive --format=zip --output "${OUTPUT_PATH}" --prefix="bankcore/" HEAD

echo "Created clean submission archive: ${OUTPUT_PATH}"
