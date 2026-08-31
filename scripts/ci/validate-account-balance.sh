#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
command -v bash >/dev/null 2>&1 || { printf 'ERROR: bash is required.\n' >&2; exit 2; }
E2E_UPLOAD_ROOT="${E2E_UPLOAD_ROOT:-$ROOT/target/ci-artifacts}" bash scripts/e2e/run-account-balance-regression.sh offline
git diff --exit-code
git diff --cached --exit-code
