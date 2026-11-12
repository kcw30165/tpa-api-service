#!/usr/bin/env bash
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.."&&pwd)"; tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT; fail=0
source "$ROOT/scripts/e2e/run-post-login.sh" --library-only
if collect_feature_names unknown >/dev/null 2>&1; then printf 'FAIL unknown accepted\n'; fail=1; fi
[[ "$(collect_feature_names investment-choice)" == investment-choice ]]||{ printf 'FAIL feature collection\n'; fail=1; }
E2E_PRIVATE_STATE_DIR="$tmp/private"; mkdir -m 700 "$E2E_PRIVATE_STATE_DIR"; PORTAL_SESSION_COOKIE_VALUE=x; XSRF_TOKEN=y; ACCOUNT_REF=z
orchestrator_cleanup
[[ ! -e "$tmp/private" && -z "${PORTAL_SESSION_COOKIE_VALUE:-}${XSRF_TOKEN:-}${ACCOUNT_REF:-}" ]]||{ printf 'FAIL cleanup\n'; fail=1; }
headers="$tmp/logout.headers"; body="$tmp/logout.json"; printf 'Set-Cookie: portal_sid=; Max-Age=0; Path=/\n' >"$headers"; printf '{"status":"SUCCESS"}' >"$body"
validate_logout_contract 0 200 "$headers" "$body"||{ printf 'FAIL valid logout\n'; fail=1; }
printf 'Set-Cookie: portal_sid=value; Path=/\n' >"$headers"; if validate_logout_contract 0 200 "$headers" "$body" >/dev/null 2>&1; then printf 'FAIL uncleared cookie accepted\n'; fail=1; fi
[[ "$fail" -eq 0 ]]||exit 1; printf 'PASS runner preflight cleanup and logout contract\n'
