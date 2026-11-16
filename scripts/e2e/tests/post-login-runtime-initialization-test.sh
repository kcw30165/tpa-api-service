#!/usr/bin/env bash
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.."&&pwd)"; tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
unset E2E_RUN_EVIDENCE_DIR E2E_PRIVATE_STATE_DIR
source "$ROOT/scripts/e2e/run-post-login.sh" --library-only
fail=0; events="$tmp/events"
orchestrator_login(){ [[ -d "${E2E_RUN_EVIDENCE_DIR:-}" && -d "${E2E_PRIVATE_STATE_DIR:-}" ]]||return 1; printf 'login\n' >>"$events"; }
orchestrator_run_feature(){ printf 'feature:%s\n' "$1" >>"$events"; }
orchestrator_logout(){ printf 'logout\n' >>"$events"; }
E2E_EVIDENCE_ROOT="$tmp/evidence" E2E_FEATURE_PACKS=investment-choice post_login_main >"$tmp/out" 2>&1||fail=1
evidence_dir=$(find "$tmp/evidence" -mindepth 1 -maxdepth 1 -type d|head -1)
[[ -n "$evidence_dir" && -d "$evidence_dir" ]]||{ printf 'FAIL evidence missing\n'; fail=1; }
[[ -f "$evidence_dir/001-preflight.txt" && -f "$evidence_dir/002-runtime-input-manifest-redacted.txt" ]]||{ printf 'FAIL manifest missing\n'; fail=1; }
[[ -z "${E2E_PRIVATE_STATE_DIR:-}" ]]||{ printf 'FAIL private state not cleaned\n'; fail=1; }
[[ "$(cat "$events")" == $'login\nfeature:investment-choice\nlogout' ]]||{ printf 'FAIL lifecycle\n'; fail=1; }
unset E2E_RUN_EVIDENCE_DIR E2E_PRIVATE_STATE_DIR; before=$(find "$tmp/evidence" -mindepth 1 -maxdepth 1 -type d|wc -l)
if E2E_EVIDENCE_ROOT="$tmp/evidence" E2E_FEATURE_PACKS=unknown post_login_main >/dev/null 2>&1; then printf 'FAIL unknown accepted\n'; fail=1; fi
after=$(find "$tmp/evidence" -mindepth 1 -maxdepth 1 -type d|wc -l)
[[ "$before" -eq "$after" && -z "${E2E_PRIVATE_STATE_DIR:-}" ]]||{ printf 'FAIL state created before preflight\n'; fail=1; }
if grep -R -E 'portal-secret|csrf-secret|account-secret|password-secret|otp-secret' "$tmp/evidence" >/dev/null 2>&1; then printf 'FAIL secret evidence\n'; fail=1; fi
[[ "$fail" -eq 0 ]]||exit 1; printf 'PASS post-login runtime initialization\n'
