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
AUTH_FLOW_COOKIE_VALUE=auth-flow-secret-value
export BFF_BASE_URL E2E_CONNECT_TIMEOUT_SECONDS E2E_REQUEST_TIMEOUT_SECONDS E2E_TLS_VERIFY
export E2E_SEND_REQUEST_ID REQUEST_ID_PREFIX ACCEPT_LANGUAGE E2E_RUN_EVIDENCE_DIR
curl() {
  printf '%s\n' "$@" >>"$state_dir/curl-arguments"
  local headers='' body='' header_ref='' body_arg='' name
  while (($#)); do
    case "$1" in
      -D) headers="$2"; shift 2 ;;
      -o) body="$2"; shift 2 ;;
      -H)
        [[ "$2" == @* ]] && header_ref="${2#@}"
        shift 2
        ;;
      --data-binary) body_arg="$2"; shift 2 ;;
      *) shift ;;
    esac
  done
  for name in PORTAL_SESSION_COOKIE_VALUE AUTH_FLOW_COOKIE_VALUE XSRF_TOKEN ACCOUNT_REF; do
    [[ "$(declare -p "$name" 2>/dev/null)" != declare\ -x* ]] || {
      printf 'FAIL auth state was exported to curl\n'
      printf 'exported-auth-state\n' >>"$state_dir/curl-security-failures"
    }
  done
  if [[ -n "$header_ref" ]]; then
    cat "$header_ref" >"$state_dir/request-headers"
  fi
  if [[ -n "$body_arg" ]]; then
    if [[ "$body_arg" == @* ]]; then
      cat "${body_arg#@}" >"$state_dir/request-body"
    else
      printf '%s' "$body_arg" >"$state_dir/request-body"
    fi
  fi
  : >"$headers"
  printf '%s' '{"status":"SUCCESS"}' >"$body"
  printf '200 0.001'
}
bff_request GET /api/v1/test-account >/dev/null
bff_request POST /api/v1/test-account '{"password":"http-body-secret"}' >/dev/null
headers_content=''
[[ ! -f "$state_dir/request-headers" ]] || headers_content="$(<"$state_dir/request-headers")"
[[ "$headers_content" == *'Cookie: portal_sid=portal-secret-value'* \
  && "$headers_content" == *'auth_flow_sid=auth-flow-secret-value'* \
  && "$headers_content" == *'X-XSRF-TOKEN: csrf-secret-value'* \
  && "$headers_content" == *'Account-Ref: account-secret-value'* ]] || {
  printf 'FAIL selected Account-Ref was not sent by the existing HTTP client\n'
  fail=1
}
body_content=''
[[ ! -f "$state_dir/request-body" ]] || body_content="$(<"$state_dir/request-body")"
[[ "$body_content" == '{"password":"http-body-secret"}' ]] || {
  printf 'FAIL secret-bearing HTTP request body changed\n'
  fail=1
}
while IFS= read -r argument; do
  case "$argument" in
    *portal-secret-value*|*auth-flow-secret-value*|*csrf-secret-value*|*account-secret-value*|*http-body-secret*)
      printf 'FAIL HTTP secret appeared in curl process arguments\n'
      fail=1
      break
      ;;
  esac
done <"$state_dir/curl-arguments"
[[ ! -s "$state_dir/curl-security-failures" ]] || { fail=1; }
grep -Fq 'account_ref=<provided-redacted>' "$E2E_RUN_EVIDENCE_DIR/005-request-command-redacted.txt" || {
  printf 'FAIL Account-Ref was not redacted from request evidence\n'
  fail=1
}
for secret in portal-secret-value auth-flow-secret-value csrf-secret-value account-secret-value http-body-secret; do
  if grep -R -F -q -- "$secret" "$E2E_RUN_EVIDENCE_DIR"; then
    printf 'FAIL HTTP secret appeared in request evidence\n'
    fail=1
    break
  fi
done
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
