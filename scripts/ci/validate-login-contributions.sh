#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
command -v mvn >/dev/null 2>&1 || { printf 'ERROR: mvn is required.\n' >&2; exit 2; }
command -v bash >/dev/null 2>&1 || { printf 'ERROR: bash is required.\n' >&2; exit 2; }
command -v jq >/dev/null 2>&1 || { printf 'ERROR: jq is required.\n' >&2; exit 2; }
command -v zip >/dev/null 2>&1 || { printf 'ERROR: zip is required.\n' >&2; exit 2; }

mvn -DskipTests compile
mvn -DskipTests test-compile
mvn -Dtest=LoginStartCancellationWebIntegrationTest test
E2E_UPLOAD_ROOT="${E2E_UPLOAD_ROOT:-$ROOT/target/ci-artifacts}" \
  bash scripts/e2e/run-login-regression.sh offline

git diff --exit-code
git diff --cached --exit-code
