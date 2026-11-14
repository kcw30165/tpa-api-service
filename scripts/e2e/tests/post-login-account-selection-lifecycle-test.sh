#!/usr/bin/env bash
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT

mkdir -p "$tmp/otp"
printf '%s' otp-code-sentinel >"$tmp/otp/code"
export E2E_REPOSITORY_ROOT="$ROOT"
export E2E_ALLOW_EXTERNAL_EFFECTS=true
export E2E_ALLOW_OTP_VERIFICATION=true
export E2E_OFFLINE_TEST_MODE=true
export E2E_TEST_OTP_CODE_FILE="$tmp/otp/code"
export BFF_BASE_URL=http://fake-e2e.invalid
export LOGIN_TEST_USERNAME=login-user-sentinel
export LOGIN_TEST_PASSWORD=login-password-sentinel
export REQUEST_ID_PREFIX=account-selection-test
export E2E_CONNECT_TIMEOUT_SECONDS=1
export E2E_REQUEST_TIMEOUT_SECONDS=1
export ACCEPT_LANGUAGE=en
unset E2E_PRIVATE_STATE_DIR E2E_ACCOUNT_REF

# shellcheck source=scripts/e2e/run-post-login.sh
source "$ROOT/scripts/e2e/run-post-login.sh" --library-only

fake_curl() {
  local headers_file='' body_file='' url='' endpoint='' method=GET
  while (($#)); do
    case "$1" in
      -D) headers_file="$2"; shift 2 ;;
      -o) body_file="$2"; shift 2 ;;
      -X) method="$2"; shift 2 ;;
      http://*|https://*) url="$1"; shift ;;
      *) shift ;;
    esac
  done
  endpoint="${url#"$BFF_BASE_URL"}"
  printf '%s %s\n' "$method" "$endpoint" >>"$tmp/calls"
  case "$endpoint" in
    /api/v1/auth/login)
      printf 'Set-Cookie: auth_flow_sid=auth-flow-cookie-sentinel; Path=/\r\n' >"$headers_file"
      printf '%s' '{"status":"SUCCESS","result":{"nextStep":"SELECT_OTP_DESTINATION","data":{"csrfToken":"login-csrf-sentinel","destinations":[{"destinationId":"email-destination","channel":"EMAIL"}]}}}' >"$body_file"
      printf '200 0.001'
      ;;
    /api/v1/auth/login/otp/send)
      : >"$headers_file"
      printf '%s' '{"status":"SUCCESS"}' >"$body_file"
      printf '200 0.001'
      ;;
    /api/v1/auth/login/otp/verify)
      printf 'Set-Cookie: portal_sid=portal-cookie-sentinel; Path=/\r\n' >"$headers_file"
      printf '%s' '{"status":"SUCCESS","result":{"nextStep":"SELECT_ACCOUNT","data":{"csrfToken":"authenticated-csrf-sentinel","accounts":[{"accountRef":"account-reference-first"},{"accountRef":"account-reference-second"}]}}}' >"$body_file"
      printf '200 0.001'
      ;;
    /api/v1/auth/logout)
      if ! private_runtime_state_is_owned \
        || [[ "$(read_private_value portal_cookie 2>/dev/null)" != portal-cookie-sentinel ]]; then
        printf 'FAIL authenticated state was unavailable during logout\n' >&2
        return 1
      fi
      if [[ "${LOGOUT_MODE:-success}" == failure ]]; then
        : >"$headers_file"
        printf '%s' '{"status":"SYSTEM_ERROR"}' >"$body_file"
        printf '503 0.001'
      else
        printf 'Set-Cookie: portal_sid=; Max-Age=0; Path=/\r\n' >"$headers_file"
        printf '%s' '{"status":"SUCCESS"}' >"$body_file"
        printf '200 0.001'
      fi
      ;;
    *)
      printf 'FAIL unexpected fake endpoint\n' >&2
      return 1
      ;;
  esac
}
curl() { fake_curl "$@"; }
orchestrator_run_feature() {
  printf 'feature %s\n' "$1" >>"$tmp/features"
  [[ ! -v E2E_ACCOUNT_REF ]] || return 1
  printf '%s\n%s\n' "$ACCOUNT_REF" "$(read_private_value selected_account_ref)" >"$tmp/feature-state"
  return 0
}

fail=0
run_case() {
  local name="$1" logout_mode="$2" evidence_root state_dir rc
  evidence_root="$tmp/evidence-$name"
  export E2E_EVIDENCE_ROOT="$evidence_root"
  export LOGOUT_MODE="$logout_mode"
  export E2E_ACCOUNT_REF=selector-reference-not-in-response
  : >"$tmp/calls"
  : >"$tmp/features"
  initialize_post_login_runtime || { printf 'FAIL runtime initialization\n'; fail=1; return; }
  [[ ! -v E2E_ACCOUNT_REF ]] || { printf 'FAIL selector was not removed from environment before login\n'; fail=1; }
  state_dir="$E2E_PRIVATE_STATE_DIR"
  set +e
  run_post_login_orchestrator investment-choice >"$tmp/$name.out" 2>&1
  rc=$?
  set -e

  [[ "$rc" -ne 0 ]] || { printf 'FAIL selector failure returned success\n'; fail=1; }
  grep -Fq 'LOGIN: FAIL' "$tmp/$name.out" || { printf 'FAIL login failure not reported\n'; fail=1; }
  grep -Fq 'ACCOUNT_SELECTION: FAIL' "$tmp/$name.out" || { printf 'FAIL account selection failure not reported\n'; fail=1; }
  grep -Fq 'FEATURE investment-choice: NOT_RUN' "$tmp/$name.out" || { printf 'FAIL feature ran after selector failure\n'; fail=1; }
  grep -Fq 'OVERALL: FAIL' "$tmp/$name.out" || { printf 'FAIL overall failure not reported\n'; fail=1; }
  if [[ "$logout_mode" == success ]]; then
    grep -Fq 'LOGOUT: PASS' "$tmp/$name.out" || { printf 'FAIL successful logout not reported\n'; fail=1; }
    grep -Fq 'portal_cookie_cleared=yes' "$E2E_RUN_EVIDENCE_DIR/032-logout-summary.txt" || {
      printf 'FAIL successful logout evidence missing\n'
      fail=1
    }
  else
    grep -Fq 'LOGOUT: FAIL' "$tmp/$name.out" || { printf 'FAIL logout failure not reported separately\n'; fail=1; }
    grep -Fq 'portal_cookie_cleared=no' "$E2E_RUN_EVIDENCE_DIR/032-logout-summary.txt" || {
      printf 'FAIL failed logout evidence missing\n'
      fail=1
    }
  fi
  [[ "$(grep -c '^POST /api/v1/auth/logout$' "$tmp/calls")" == 1 ]] || {
    printf 'FAIL authenticated logout was not attempted exactly once\n'
    fail=1
  }
  [[ "$(wc -l <"$tmp/calls")" == 4 ]] || {
    printf 'FAIL unexpected network operation after OTP verification\n'
    fail=1
  }
  [[ ! -s "$tmp/features" ]] || { printf 'FAIL feature pack executed\n'; fail=1; }
  [[ ! -v E2E_ACCOUNT_REF ]] || { printf 'FAIL input selector remained in environment\n'; fail=1; }
  [[ ! -e "$state_dir" ]] || { printf 'FAIL private authentication state was not cleaned\n'; fail=1; }

  for value in \
    selector-reference-not-in-response \
    account-reference-first \
    account-reference-second \
    auth-flow-cookie-sentinel \
    login-csrf-sentinel \
    portal-cookie-sentinel \
    authenticated-csrf-sentinel \
    login-user-sentinel \
    login-password-sentinel \
    otp-code-sentinel; do
    if grep -R -F -q -- "$value" "$tmp/$name.out" "$E2E_RUN_EVIDENCE_DIR"; then
      printf 'FAIL sensitive value appeared in output or evidence\n'
      fail=1
      break
    fi
  done
}

run_case logout-pass success
run_case logout-fail failure

export E2E_EVIDENCE_ROOT="$tmp/evidence-match"
export E2E_ACCOUNT_REF=account-reference-second
export LOGOUT_MODE=success
: >"$tmp/calls"
: >"$tmp/features"
: >"$tmp/feature-state"
initialize_post_login_runtime || { printf 'FAIL matching runtime initialization\n'; exit 1; }
[[ ! -v E2E_ACCOUNT_REF ]] || { printf 'FAIL matching selector was not removed from environment before login\n'; fail=1; }
matched_state_dir="$E2E_PRIVATE_STATE_DIR"
set +e
run_post_login_orchestrator investment-choice >"$tmp/matching.out" 2>&1
matching_rc=$?
set -e
[[ "$matching_rc" -eq 0 ]] || { printf 'FAIL matching selector did not pass\n'; fail=1; }
grep -Fq 'ACCOUNT_SELECTION: PASS' "$tmp/matching.out" || { printf 'FAIL matching selection not reported\n'; fail=1; }
grep -Fq 'FEATURE investment-choice: PASS' "$tmp/matching.out" || { printf 'FAIL matching account feature did not run\n'; fail=1; }
grep -Fq 'LOGOUT: PASS' "$tmp/matching.out" || { printf 'FAIL matching account logout not reported\n'; fail=1; }
[[ "$(cat "$tmp/feature-state")" == $'account-reference-second\naccount-reference-second' ]] || {
  printf 'FAIL selected reference was not privately published to the feature\n'
  fail=1
}
grep -Fq 'selected_account_index=1' "$E2E_RUN_EVIDENCE_DIR/017-authenticated-transition-summary.txt" || {
  printf 'FAIL explicit selection index was not recorded\n'
  fail=1
}
[[ ! -v E2E_ACCOUNT_REF ]] || { printf 'FAIL matched selector remained in environment\n'; fail=1; }
[[ ! -e "$matched_state_dir" ]] || { printf 'FAIL matched private authentication state was not cleaned\n'; fail=1; }
[[ "$(grep -c '^POST /api/v1/auth/logout$' "$tmp/calls")" == 1 ]] || {
  printf 'FAIL matched session logout was not attempted exactly once\n'
  fail=1
}
[[ "$(wc -l <"$tmp/calls")" == 4 ]] || {
  printf 'FAIL matched selection issued an unexpected account-selection request\n'
  fail=1
}
for value in \
  account-reference-first \
  account-reference-second \
  auth-flow-cookie-sentinel \
  login-csrf-sentinel \
  portal-cookie-sentinel \
  authenticated-csrf-sentinel \
  login-user-sentinel \
  login-password-sentinel \
  otp-code-sentinel; do
  if grep -R -F -q -- "$value" "$tmp/matching.out" "$E2E_RUN_EVIDENCE_DIR"; then
    printf 'FAIL authentication value appeared in matching output or evidence\n'
    fail=1
    break
  fi
done

[[ "$fail" -eq 0 ]] || exit 1
printf 'PASS post-login account-selection and authenticated lifecycle\n'
