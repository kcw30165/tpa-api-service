#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
printf 'DEPRECATED: use scripts/e2e/run-account-balance-dev.sh provided-session\n' >&2
exec bash "$ROOT/scripts/e2e/run-account-balance-dev.sh" provided-session
