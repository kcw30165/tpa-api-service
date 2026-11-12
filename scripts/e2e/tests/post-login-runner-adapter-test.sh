#!/usr/bin/env bash
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.."&&pwd)"; tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/bin"; cat >"$tmp/bin/bash" <<'FAKE'
#!/bin/bash
printf 'BASE_URL=%s\nSESSION_COOKIE=%s\nCSRF_TOKEN=%s\nACCOUNT_REF=%s\nPACK=%s\n' "$BASE_URL" "$SESSION_COOKIE" "$CSRF_TOKEN" "$ACCOUNT_REF" "$1" >"$RUNNER_CAPTURE"
FAKE
chmod +x "$tmp/bin/bash"
export RUNNER_CAPTURE="$tmp/capture" BFF_BASE_URL=http://mock PORTAL_SESSION_COOKIE_VALUE=cookie-secret XSRF_TOKEN=csrf-secret ACCOUNT_REF=account-secret
source "$ROOT/scripts/e2e/run-post-login.sh" --library-only
PATH="$tmp/bin:$PATH"
orchestrator_run_feature investment-choice
[[ "$(cat "$tmp/capture")" == $'BASE_URL=http://mock\nSESSION_COOKIE=cookie-secret\nCSRF_TOKEN=csrf-secret\nACCOUNT_REF=account-secret\nPACK='$ROOT'/scripts/e2e/investment-choice-scenarios.sh' ]]||exit 1
unset PORTAL_SESSION_COOKIE_VALUE XSRF_TOKEN ACCOUNT_REF
if orchestrator_run_feature investment-choice >/dev/null 2>&1; then exit 1; fi
printf 'PASS post-login runner adapter\n'
