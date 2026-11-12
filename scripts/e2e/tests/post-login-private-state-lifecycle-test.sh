#!/usr/bin/env bash
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT
mkdir -p "$tmp/private-root" "$tmp/evidence"
export TMPDIR="$tmp/private-root"
export E2E_REPOSITORY_ROOT="$ROOT"
export E2E_EVIDENCE_ROOT="$tmp/evidence"
export E2E_ALLOW_EXTERNAL_EFFECTS=true
export E2E_ALLOW_OTP_VERIFICATION=true
export E2E_OFFLINE_TEST_MODE=true
export E2E_TEST_OTP_CODE_FILE="$tmp/otp.txt"
export BFF_BASE_URL=http://fake-e2e.invalid
export LOGIN_TEST_USERNAME=login-user-sentinel
export LOGIN_TEST_PASSWORD=login-password-sentinel
export REQUEST_ID_PREFIX=private-state-test
printf '%s' otp-code-sentinel >"$E2E_TEST_OTP_CODE_FILE"

# shellcheck source=scripts/e2e/run-post-login.sh
source "$ROOT/scripts/e2e/run-post-login.sh" --library-only

fake_curl() {
  local headers_file='' body_file='' url='' endpoint
  while (($#)); do
    case "$1" in
      -D) headers_file="$2"; shift 2 ;;
      -o) body_file="$2"; shift 2 ;;
      http://*|https://*) url="$1"; shift ;;
      *) shift ;;
    esac
  done
  endpoint="${url#"$BFF_BASE_URL"}"
  printf '%s\n' "$endpoint" >>"$tmp/fake-network-calls.txt"
  case "$endpoint" in
    /api/v1/auth/login)
      printf 'Set-Cookie: auth_flow_sid=auth-flow-cookie-sentinel; Path=/\r\n' >"$headers_file"
      printf '%s' '{"status":"SUCCESS","result":{"nextStep":"SELECT_OTP_DESTINATION","data":{"csrfToken":"login-csrf-sentinel","destinations":[{"destinationId":"email-destination","channel":"EMAIL"}]}}}' >"$body_file"
      ;;
    /api/v1/auth/login/otp/send)
      : >"$headers_file"
      printf '%s' '{"status":"SUCCESS"}' >"$body_file"
      ;;
    /api/v1/auth/login/otp/verify)
      printf 'Set-Cookie: portal_sid=portal-cookie-sentinel; Path=/\r\n' >"$headers_file"
      printf '%s' '{"status":"SUCCESS","result":{"nextStep":"SELECT_ACCOUNT","data":{"csrfToken":"authenticated-csrf-sentinel","accounts":[{"accountRef":"account-ref-sentinel"}]}}}' >"$body_file"
      ;;
    *)
      printf 'FAIL unexpected fake endpoint: %s\n' "$endpoint" >&2
      return 1
      ;;
  esac
  printf '200 0.001'
}
curl() { fake_curl "$@"; }

fail=0
assert_equal() {
  local actual="$1" expected="$2" message="$3"
  if [[ "$actual" != "$expected" ]]; then
    printf 'FAIL %s\n' "$message"
    fail=1
  fi
}
assert_no_authentication_values_in_output_or_evidence() {
  local value
  for value in \
    login-user-sentinel \
    login-password-sentinel \
    otp-code-sentinel \
    auth-flow-cookie-sentinel \
    login-csrf-sentinel \
    portal-cookie-sentinel \
    authenticated-csrf-sentinel \
    account-ref-sentinel; do
    if grep -R -F -q -- "$value" "$tmp/journey.out" "$E2E_RUN_EVIDENCE_DIR"; then
      printf 'FAIL authentication value appeared in output or evidence\n'
      fail=1
      return
    fi
  done
}

initialize_post_login_runtime
orchestrator_state_dir="$E2E_PRIVATE_STATE_DIR"
[[ -d "$orchestrator_state_dir" ]] || { printf 'FAIL orchestrator private state was not initialized\n'; fail=1; }
assert_equal "$(find "$TMPDIR" -mindepth 1 -maxdepth 1 -type d | wc -l)" 1 \
  'orchestrator initialization did not create exactly one private directory'
authenticated_login_journey >"$tmp/journey.out" 2>&1 || {
  printf 'FAIL fake-network authenticated login journey failed\n'
  fail=1
}
assert_equal "$E2E_PRIVATE_STATE_DIR" "$orchestrator_state_dir" \
  'authenticated login journey did not reuse the orchestrator private directory'
assert_equal "$(find "$TMPDIR" -mindepth 1 -maxdepth 1 -type d | wc -l)" 1 \
  'authenticated login journey created more than one private directory'
assert_equal "$(wc -l <"$tmp/fake-network-calls.txt")" 3 \
  'fake login did not make exactly the three expected network operations'
assert_no_authentication_values_in_output_or_evidence
orchestrator_cleanup
[[ ! -e "$orchestrator_state_dir" ]] || { printf 'FAIL orchestrator cleanup left private state behind\n'; fail=1; }
assert_equal "$(find "$TMPDIR" -mindepth 1 -maxdepth 1 -type d | wc -l)" 0 \
  'private directory remained after orchestrator cleanup'
[[ -z "${PORTAL_SESSION_COOKIE_VALUE:-}${XSRF_TOKEN:-}${ACCOUNT_REF:-}" ]] || {
  printf 'FAIL authenticated state remained exported after cleanup\n'
  fail=1
}

E2E_SCENARIO_PACK=standalone-login
E2E_SCENARIO_ID=private-state-lifecycle
export E2E_SCENARIO_PACK E2E_SCENARIO_ID
create_evidence_dir
authenticated_login_journey >"$tmp/journey.out" 2>&1 || {
  printf 'FAIL fake-network standalone login journey failed\n'
  fail=1
}
standalone_state_dir="$E2E_PRIVATE_STATE_DIR"
[[ -d "$standalone_state_dir" ]] || { printf 'FAIL standalone login journey did not create private state\n'; fail=1; }
assert_equal "$(find "$TMPDIR" -mindepth 1 -maxdepth 1 -type d | wc -l)" 1 \
  'standalone login journey did not create exactly one private directory'
assert_no_authentication_values_in_output_or_evidence
destroy_private_runtime_state
[[ ! -e "$standalone_state_dir" ]] || { printf 'FAIL standalone cleanup left private state behind\n'; fail=1; }
assert_equal "$(find "$TMPDIR" -mindepth 1 -maxdepth 1 -type d | wc -l)" 0 \
  'standalone private directory remained after cleanup'

[[ "$fail" -eq 0 ]] || exit 1
printf 'PASS post-login private-state lifecycle and cleanup\n'
