#!/usr/bin/env bash
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.."&&pwd)"
source "$ROOT/scripts/e2e/run-post-login.sh" --library-only
for fn in redacted_presence redact_headers_file redact_json_file; do declare -F "$fn" >/dev/null||{ printf 'FAIL missing helper: %s\n' "$fn"; exit 1; }; done
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
printf 'Set-Cookie: portal_sid=secret-cookie; Path=/\nX-XSRF-TOKEN: secret-csrf\n' >"$tmp/raw.headers"
printf '{"password":"secret-password","otp":"123456","csrfToken":"secret-csrf"}' >"$tmp/raw.json"
redact_headers_file "$tmp/raw.headers" "$tmp/safe.headers"
redact_json_file "$tmp/raw.json" "$tmp/safe.json"
if grep -R -F -e secret-cookie -e secret-csrf -e secret-password "$tmp/safe.headers" "$tmp/safe.json"; then exit 1; fi
[[ "$(redacted_presence value)" != value ]]||exit 1
printf 'PASS post-login redaction library loading\n'
