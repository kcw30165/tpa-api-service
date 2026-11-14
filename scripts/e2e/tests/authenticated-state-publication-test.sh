#!/usr/bin/env bash
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
source "$ROOT/scripts/e2e/lib/private-runtime-state.sh"
source "$ROOT/scripts/e2e/lib/authenticated-login-journey.sh"
source "$ROOT/scripts/e2e/lib/redaction.sh"
source "$ROOT/scripts/e2e/lib/http-client.sh"
fail=0
expect_eq(){ [[ "$1" == "$2" ]] || { printf 'FAIL %s expected=%s actual=%s\n' "$3" "$2" "$1"; fail=1; }; }
create_private_runtime_state
state_dir="$E2E_PRIVATE_STATE_DIR"
write_private_value portal_cookie 'portal-secret-value'
write_private_value authenticated_csrf 'csrf-secret-value'
write_private_value selected_account_ref 'account-secret-value'
publish_authenticated_http_state || { printf 'FAIL publish returned non-zero\n'; exit 1; }
expect_eq "${PORTAL_SESSION_COOKIE_VALUE:-}" portal-secret-value portal_cookie
expect_eq "${XSRF_TOKEN:-}" csrf-secret-value csrf
expect_eq "${ACCOUNT_REF:-}" account-secret-value account_ref
[[ -z "${AUTH_FLOW_COOKIE_VALUE:-}" ]] || { printf 'FAIL auth-flow cookie was retained\n'; fail=1; }
for file in "$state_dir"/*; do [[ "$(stat -c '%a' "$file")" == 600 ]] || { printf 'FAIL file mode %s\n' "$file"; fail=1; }; done
[[ "$(stat -c '%a' "$state_dir")" == 700 ]] || { printf 'FAIL directory mode\n'; fail=1; }
mkdir -p "$state_dir/evidence"
E2E_RUN_EVIDENCE_DIR="$state_dir/evidence"
BFF_BASE_URL=http://fake-e2e.invalid
E2E_CONNECT_TIMEOUT_SECONDS=1
E2E_REQUEST_TIMEOUT_SECONDS=1
E2E_TLS_VERIFY=true
E2E_SEND_REQUEST_ID=true
REQUEST_ID_PREFIX=account-header-test
ACCEPT_LANGUAGE=en
export BFF_BASE_URL E2E_CONNECT_TIMEOUT_SECONDS E2E_REQUEST_TIMEOUT_SECONDS E2E_TLS_VERIFY
export E2E_SEND_REQUEST_ID REQUEST_ID_PREFIX ACCEPT_LANGUAGE E2E_RUN_EVIDENCE_DIR
curl() {
  printf '%s\n' "$@" >"$state_dir/curl-arguments"
  local headers='' body=''
  while (($#)); do
    case "$1" in
      -D) headers="$2"; shift 2 ;;
      -o) body="$2"; shift 2 ;;
      *) shift ;;
    esac
  done
  : >"$headers"
  printf '%s' '{"status":"SUCCESS"}' >"$body"
  printf '200 0.001'
}
bff_request GET /api/v1/test-account >/dev/null
grep -Fq 'Account-Ref: account-secret-value' "$state_dir/curl-arguments" || {
  printf 'FAIL selected Account-Ref was not sent by the existing HTTP client\n'
  fail=1
}
grep -Fq 'account_ref=<provided-redacted>' "$E2E_RUN_EVIDENCE_DIR/005-request-command-redacted.txt" || {
  printf 'FAIL Account-Ref was not redacted from request evidence\n'
  fail=1
}
if grep -R -F -q account-secret-value "$E2E_RUN_EVIDENCE_DIR"; then
  printf 'FAIL selected Account-Ref appeared in HTTP-client evidence\n'
  fail=1
fi
destroy_private_runtime_state
[[ ! -e "$state_dir" ]] || { printf 'FAIL state directory remains\n'; fail=1; }
[[ -z "${E2E_PRIVATE_STATE_DIR:-}" ]] || { printf 'FAIL state directory variable remains\n'; fail=1; }
unset PORTAL_SESSION_COOKIE_VALUE XSRF_TOKEN ACCOUNT_REF
create_private_runtime_state
write_private_value portal_cookie only-cookie
if publish_authenticated_http_state >"$E2E_PRIVATE_STATE_DIR/error.out" 2>&1; then printf 'FAIL incomplete state unexpectedly published\n'; fail=1; fi
[[ -z "${PORTAL_SESSION_COOKIE_VALUE:-}${XSRF_TOKEN:-}${ACCOUNT_REF:-}" ]] || { printf 'FAIL partial state leaked\n'; fail=1; }
destroy_private_runtime_state
[[ "$fail" -eq 0 ]] || exit 1
printf 'PASS authenticated state publication and cleanup\n'
