#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
MODE="${1:-}"
command -v zip >/dev/null 2>&1 || { printf 'ERROR: zip is required.\n' >&2; exit 2; }
case "$MODE" in offline|live-safe|live-otp|live-otp-remaining|all) ;; *) printf 'Usage: bash scripts/e2e/run-login-regression.sh {offline|live-safe|live-otp|live-otp-remaining|all}\n' >&2; exit 2;; esac

TS="$(date +%Y%m%d-%H%M%S)"
PARENT="${E2E_EVIDENCE_ROOT:-$ROOT/target/evidence}/${TS}-bff-e2e-login-regression-${MODE}"
CHILD_ROOT="$PARENT/children"
mkdir -p "$CHILD_ROOT"
chmod 700 "$PARENT" "$CHILD_ROOT"
SUITE_UPLOAD_ROOT="${E2E_UPLOAD_ROOT:-/mnt/d/Projects/ORSO/ngtpa}"
SUITE_UPLOAD_FILE="$SUITE_UPLOAD_ROOT/$(basename "$PARENT").txt"
SUITE_PACKAGED=0
package_suite_evidence() {
  [[ "$SUITE_PACKAGED" -eq 0 ]] || return 0
  SUITE_PACKAGED=1
  mkdir -p "$SUITE_UPLOAD_ROOT"
  rm -f "$SUITE_UPLOAD_FILE"
  (cd "$(dirname "$PARENT")" && zip -qr "$SUITE_UPLOAD_FILE" "$(basename "$PARENT")")
}
MANIFEST="$PARENT/040-scenario-manifest.txt"
FAILURES="$PARENT/050-failure-manifest.txt"
: >"$MANIFEST"; : >"$FAILURES"
OVERALL=0

cleanup() {
  local rc=$?
  unset LOGIN_TEST_PASSWORD LOGIN_TEST_USERNAME
  find "${TMPDIR:-/tmp}" -mindepth 1 -maxdepth 1 -type d -name 'bff-e2e-private.*' -exec rm -rf -- {} + 2>/dev/null || true
  package_suite_evidence || true
  [[ -s "$SUITE_UPLOAD_FILE" ]] && printf 'Upload artifact: %s\n' "$SUITE_UPLOAD_FILE"
  return "$rc"
}
trap cleanup EXIT

record_child() {
  local name="$1" rc="$2" output="$3" child
  child="$(sed -n 's/^Evidence: //p' "$output" | tail -n 1)"
  printf '%s|exit=%s|evidence=%s\n' "$name" "$rc" "${child:-MISSING}" >>"$MANIFEST"
  if [[ "$rc" -ne 0 ]]; then printf '%s|exit=%s|evidence=%s\n' "$name" "$rc" "${child:-MISSING}" >>"$FAILURES"; OVERALL=1; fi
}

run_live_child() {
  local alias output rc
  alias="$1"
  output="$PARENT/${2}-${alias}.txt"
  printf '\nStarting live Login flow: %s\n' "$alias"
  printf 'Child progress and interactive prompts will remain visible.\n'
  set +e
  E2E_EVIDENCE_ROOT="$CHILD_ROOT" bash scripts/e2e/run-login-dev.sh "$alias" 2>&1 | tee "$output"
  rc=${PIPESTATUS[0]}
  set -e
  record_child "$alias" "$rc" "$output"
  return "$rc"
}

write_mock() {
  local scenario="$1" dir="$2"
  cat >"$dir/curl" <<'MOCK'
#!/usr/bin/env bash
count_file="${MOCK_COUNT_FILE:?}"; scenario="${MOCK_SCENARIO:?}"
count=0; [[ -f "$count_file" ]] && count="$(cat "$count_file")"; count=$((count+1)); printf '%s' "$count" >"$count_file"
out=''; headers=''; while [[ $# -gt 0 ]]; do case "$1" in -o) out="$2"; shift 2;; -D) headers="$2"; shift 2;; -w) shift 2;; *) shift;; esac; done
start() { printf 'HTTP/1.1 200 OK\r\nSet-Cookie: auth_flow_sid=SYNTHETIC_COOKIE; Path=/api/v1/auth; Secure; HttpOnly\r\n\r\n' >"$headers"; printf '{"success":true,"status":"SUCCESS","result":{"nextStep":"SELECT_OTP_DESTINATION","data":{"csrfToken":"SYNTHETIC_CSRF","destinations":[{"destinationId":"SYNTHETIC_DEST","channel":"email","maskedDestination":"SYNTHETIC_MASK"}]}}}' >"$out"; }
send() { printf 'HTTP/1.1 200 OK\r\n\r\n' >"$headers"; printf '{"success":true,"status":"SUCCESS","result":{"nextStep":"VERIFY_OTP","data":{"resendCooldownSeconds":60}}}' >"$out"; }
case "$scenario:$count" in
  *:1) start; code=200;;
  start-and-cancel:2) printf 'HTTP/1.1 200 OK\r\nSet-Cookie: auth_flow_sid=; Max-Age=0; Path=/api/v1/auth\r\n\r\n' >"$headers"; printf '{"success":true,"status":"SUCCESS","result":{"nextStep":"RETURN_TO_LOGIN","data":{"actions":[]}},"messages":[],"errors":[]}' >"$out"; code=200;;
  otp-send-and-verify:2|otp-send-and-invalid-verify:2|otp-send-and-resend:2|otp-send-verify-and-logout:2) send; code=200;;
  otp-send-and-verify:3) printf 'HTTP/1.1 200 OK\r\nSet-Cookie: portal_sid=SYNTHETIC_PORTAL; Path=/api; Secure; HttpOnly\r\n\r\n' >"$headers"; printf '{"success":true,"status":"SUCCESS","result":{"nextStep":"SELECT_ACCOUNT","data":{"csrfToken":"SYNTHETIC_AUTH_CSRF","accounts":[{"accountRef":"SYNTHETIC_ACCOUNT","accountEnv":"SYNTHETIC_ENV"}]}}}' >"$out"; code=200;;
  otp-send-and-invalid-verify:3) printf 'HTTP/1.1 422 Unprocessable Entity\r\n\r\n' >"$headers"; printf '{"success":false,"status":"BUSINESS_REJECTED","result":{"nextStep":"VERIFY_OTP","data":{"remainingVerifyAttempts":4,"accounts":[]}},"messages":[],"errors":[]}' >"$out"; code=422;;
  otp-send-and-resend:3) printf 'HTTP/1.1 429 Too Many Requests\r\nRetry-After: 60\r\n\r\n' >"$headers"; printf '{"success":false,"status":"BUSINESS_REJECTED","result":{"nextStep":"VERIFY_OTP","data":{"resendCooldownSeconds":60,"retryAfterSeconds":60}},"messages":[],"errors":[]}' >"$out"; code=429;;
  otp-send-verify-and-logout:3) printf 'HTTP/1.1 200 OK\r\nSet-Cookie: portal_sid=SYNTHETIC_PORTAL; Path=/api; Secure; HttpOnly\r\n\r\n' >"$headers"; printf '{"success":true,"status":"SUCCESS","result":{"nextStep":"SELECT_ACCOUNT","data":{"csrfToken":"SYNTHETIC_AUTH_CSRF","accounts":[{"accountRef":"SYNTHETIC_ACCOUNT","accountEnv":"SYNTHETIC_ENV"}]}}}' >"$out"; code=200;;
  otp-send-verify-and-logout:4) printf 'HTTP/1.1 200 OK\r\nSet-Cookie: portal_sid=; Max-Age=0; Path=/api\r\n\r\n' >"$headers"; printf '{"success":true,"status":"SUCCESS","result":null,"messages":[],"errors":[]}' >"$out"; code=200;;
  *) exit 98;;
esac
printf '%s 0.001000' "$code"
MOCK
  chmod 755 "$dir/curl"
}

run_offline_child() {
  local scenario="$1" seq="$2" tmp output rc child
  tmp="$(mktemp -d)"; mkdir -p "$tmp/bin" "$tmp/runtime"; write_mock "$scenario" "$tmp/bin"
  printf '246810' >"$tmp/runtime/otp"; printf '135790' >"$tmp/runtime/invalid"; chmod 600 "$tmp/runtime/"*
  output="$PARENT/${seq}-${scenario}.txt"
  set +e
  MOCK_COUNT_FILE="$tmp/count" MOCK_SCENARIO="$scenario" PATH="$tmp/bin:$PATH" \
    BFF_BASE_URL=http://mock E2E_EVIDENCE_ROOT="$CHILD_ROOT" E2E_ENVIRONMENT=offline \
    E2E_SCENARIO_PACK=login E2E_SCENARIO_ID="$scenario" E2E_BOOTSTRAP_MODE=anonymous \
    E2E_ALLOW_EXTERNAL_EFFECTS=true E2E_ALLOW_OTP_VERIFICATION=true E2E_EXPECT_INVALID_OTP=true \
    E2E_ALLOW_OTP_RESEND=true E2E_ALLOW_LOGIN_CANCELLATION=true E2E_ALLOW_AUTHENTICATED_LOGOUT=true \
    E2E_ALLOW_ACCOUNT_SELECTION=false E2E_OFFLINE_TEST_MODE=true \
    E2E_TEST_OTP_CODE_FILE="$tmp/runtime/otp" E2E_TEST_INVALID_OTP_CODE_FILE="$tmp/runtime/invalid" \
    LOGIN_TEST_USERNAME=synthetic-user LOGIN_TEST_PASSWORD=synthetic-password LOGIN_OTP_DESTINATION_INDEX=0 \
    bash scripts/e2e/run.sh >"$output" 2>&1
  rc=$?
  set -e
  record_child "$scenario" "$rc" "$output"
  rm -rf "$tmp"
  return 0
}

write_contributions_mock() {
  local dir="$1"
  cat >"$dir/curl" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
count_file="${MOCK_COUNT_FILE:?}"
count=0; [[ -f "$count_file" ]] && count="$(cat "$count_file")"
count=$((count+1)); printf '%s' "$count" >"$count_file"
out=''; headers=''; args_file="${MOCK_ARGS_FILE:?}"
printf '%q ' "$@" >>"$args_file"; printf '\n' >>"$args_file"
while [[ $# -gt 0 ]]; do
  case "$1" in
    -o) out="$2"; shift 2 ;;
    -D) headers="$2"; shift 2 ;;
    -w) shift 2 ;;
    *) shift ;;
  esac
done
case "$count" in
  1)
    printf 'HTTP/1.1 200 OK\r\nSet-Cookie: auth_flow_sid=SYNTHETIC_COOKIE; Path=/api/v1/auth; Secure; HttpOnly\r\n\r\n' >"$headers"
    printf '{"success":true,"status":"SUCCESS","result":{"nextStep":"SELECT_OTP_DESTINATION","data":{"csrfToken":"SYNTHETIC_CSRF","destinations":[{"destinationId":"SYNTHETIC_DEST","channel":"email","maskedDestination":"SYNTHETIC_MASK"}]}}}' >"$out" ;;
  2)
    printf 'HTTP/1.1 200 OK\r\n\r\n' >"$headers"
    printf '{"success":true,"status":"SUCCESS","result":{"nextStep":"VERIFY_OTP","data":{"resendCooldownSeconds":60}}}' >"$out" ;;
  3)
    printf 'HTTP/1.1 200 OK\r\nSet-Cookie: portal_sid=SYNTHETIC_PORTAL; Path=/; Secure; HttpOnly\r\nSet-Cookie: auth_flow_sid=; Max-Age=0; Path=/api/v1/auth\r\n\r\n' >"$headers"
    printf '{"success":true,"status":"SUCCESS","result":{"nextStep":"SELECT_ACCOUNT","data":{"csrfToken":"SYNTHETIC_AUTH_CSRF","accounts":[{"accountRef":"SYNTHETIC_ACCOUNT","accountEnv":"SYNTHETIC_ENV"}]}}}' >"$out" ;;
  4)
    printf 'HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n' >"$headers"
    printf '{"actions":{"export":{"enabled":true}},"items":[],"pagination":{"page":1,"pageSize":99999,"totalRecords":0,"hasNextPage":false}}' >"$out" ;;
  5)
    printf 'HTTP/1.1 200 OK\r\nSet-Cookie: portal_sid=; Max-Age=0; Path=/\r\n\r\n' >"$headers"
    printf '{"success":true,"status":"SUCCESS","result":null,"messages":[],"errors":[]}' >"$out" ;;
  *) exit 98 ;;
esac
printf '200 0.001000'
MOCK
  chmod 755 "$dir/curl"
}

run_offline_contributions_child() {
  local tmp output rc
  tmp="$(mktemp -d)"; mkdir -p "$tmp/bin" "$tmp/runtime"
  write_contributions_mock "$tmp/bin"
  printf '246810' >"$tmp/runtime/otp"; chmod 600 "$tmp/runtime/otp"
  output="$PARENT/016-contributions-list-authenticated.txt"
  set +e
  MOCK_COUNT_FILE="$tmp/count" MOCK_ARGS_FILE="$tmp/requests" PATH="$tmp/bin:$PATH" \
    BFF_BASE_URL=http://mock E2E_EVIDENCE_ROOT="$CHILD_ROOT" E2E_ENVIRONMENT=offline \
    E2E_SCENARIO_PACK=contributions E2E_SCENARIO_ID=list-authenticated E2E_BOOTSTRAP_MODE=anonymous \
    E2E_ALLOW_EXTERNAL_EFFECTS=true E2E_ALLOW_OTP_VERIFICATION=true E2E_ALLOW_AUTHENTICATED_LOGOUT=true \
    E2E_ALLOW_ACCOUNT_SELECTION=false E2E_OFFLINE_TEST_MODE=true \
    E2E_TEST_OTP_CODE_FILE="$tmp/runtime/otp" CONTRIBUTIONS_ACCOUNT_INDEX=0 \
    CONTRIBUTIONS_FROM_DATE=20/03/2026 CONTRIBUTIONS_TO_DATE=29/06/2026 \
    LOGIN_TEST_USERNAME=synthetic-user LOGIN_TEST_PASSWORD=synthetic-password LOGIN_OTP_DESTINATION_INDEX=0 \
    bash scripts/e2e/run.sh >"$output" 2>&1
  rc=$?
  set -e
  record_child contributions-list-authenticated "$rc" "$output"
  [[ "$(cat "$tmp/count" 2>/dev/null || true)" == 5 ]] || { printf 'contributions request count mismatch\n' >>"$FAILURES"; OVERALL=1; }
  for marker in '/api/v1/contributions' 'Account-Ref:' 'X-XSRF-TOKEN:' 'portal_sid=' 'fromDate=20/03/2026' 'toDate=29/06/2026'; do
    grep -Fqi -- "$marker" "$tmp/requests" || { printf 'contributions transport marker missing: %s\n' "$marker" >>"$FAILURES"; OVERALL=1; }
  done
  rm -rf "$tmp"
  return 0
}

collect_credentials_once() {
  if [[ -z "${LOGIN_TEST_USERNAME:-}" ]]; then read -r -p 'Login test username: ' LOGIN_TEST_USERNAME; fi
  if [[ -z "${LOGIN_TEST_PASSWORD:-}" ]]; then read -r -s -p 'Login test password: ' LOGIN_TEST_PASSWORD; printf '\n'; fi
  [[ -n "$LOGIN_TEST_USERNAME" && -n "$LOGIN_TEST_PASSWORD" ]] || { printf 'ERROR: Login credentials are required.\n' >&2; exit 2; }
  export LOGIN_TEST_USERNAME LOGIN_TEST_PASSWORD LOGIN_OTP_DESTINATION_INDEX="${LOGIN_OTP_DESTINATION_INDEX:-0}"
  printf 'credentials=<provided-redacted>\ndestination_index=%s\n' "$LOGIN_OTP_DESTINATION_INDEX" >"$PARENT/003-credential-input-summary-redacted.txt"
}

run_offline_suite() {
  local scenarios=(start-and-cancel otp-send-and-verify otp-send-and-invalid-verify otp-send-and-resend otp-send-verify-and-logout) i=0
  for scenario in "${scenarios[@]}"; do
    i=$((i+1))
    run_offline_child "$scenario" "01${i}"
  done
  run_offline_contributions_child
  printf 'scenarios=6
login_scenarios=5
contributions_scenarios=1
failed=%s
real_requests=0
human_prompts=0
' "$(wc -l <"$FAILURES")" >"$PARENT/010-offline-summary.txt"
}

run_live_safe_suite() {
  collect_credentials_once
  run_live_child cancel 020 || true
  printf 'scenarios=1\notp_deliveries=0\nfailed=%s\n' "$(wc -l <"$FAILURES")" >"$PARENT/020-live-safe-summary.txt"
}

run_live_otp_remaining_suite() {
  collect_credentials_once
  printf 'Remaining live OTP budget: 2 deliveries, 1 OTP prompt, 1 resend request, no automatic retries.\n'
  if [[ "${E2E_SUITE_AUTHORIZED:-false}" != true ]]; then
    read -r -p 'Type RUN to authorize the remaining suite: ' authorization
    [[ "$authorization" == RUN ]] || { printf 'Remaining suite not authorized.\n' >&2; exit 3; }
  fi
  run_live_child invalid-otp 032 || return 1
  run_live_child resend-cooldown 033 || return 1
  printf 'flows=2\notp_delivery_budget=2\notp_prompt_budget=1\nautomatic_retries=0\nfailed=%s\n' "$(wc -l <"$FAILURES")" >"$PARENT/030-live-otp-remaining-summary.txt"
}

run_live_otp_suite() {
  collect_credentials_once
  printf 'Live OTP budget: 3 deliveries, 2 OTP prompts, 1 resend request, no automatic retries.\n'
  if [[ "${E2E_SUITE_AUTHORIZED:-false}" != true ]]; then
    read -r -p 'Type RUN to authorize this suite: ' authorization
    [[ "$authorization" == RUN ]] || { printf 'Suite not authorized.\n' >&2; exit 3; }
  fi
  run_live_child logout 031 || return 1
  run_live_child invalid-otp 032 || return 1
  run_live_child resend-cooldown 033 || return 1
  printf 'flows=3\notp_delivery_budget=3\notp_prompt_budget=2\nautomatic_retries=0\nfailed=%s\n' "$(wc -l <"$FAILURES")" >"$PARENT/030-live-otp-summary.txt"
}

printf 'mode=%s\nparent=%s\nchild_root=%s\n' "$MODE" "$PARENT" "$CHILD_ROOT" >"$PARENT/001-preflight.txt"
printf 'offline=no-network; live-safe=no-OTP; live-otp=3-delivery-budget\n' >"$PARENT/002-suite-plan.txt"

case "$MODE" in
  offline) run_offline_suite;;
  live-safe) run_live_safe_suite;;
  live-otp) run_live_otp_suite || OVERALL=1;;
  live-otp-remaining) run_live_otp_remaining_suite || OVERALL=1;;
  all) run_offline_suite; [[ "$OVERALL" -eq 0 ]] && run_live_safe_suite; [[ "$OVERALL" -eq 0 ]] && run_live_otp_suite || OVERALL=1;;
esac

if grep -R -E -q 'SYNTHETIC_(COOKIE|CSRF|DEST|MASK|PORTAL|AUTH_CSRF|ACCOUNT|ENV)|synthetic-password|246810|135790' "$CHILD_ROOT" 2>/dev/null; then
  printf 'security_audit=FAIL\n' >"$PARENT/060-security-audit.txt"; OVERALL=1
else
  printf 'security_audit=PASS\n' >"$PARENT/060-security-audit.txt"
fi
printf 'private_runtime_state_cleanup=ENFORCED\n' >"$PARENT/070-preservation-audit.txt"
printf 'overall_exit=%s\nfailed_scenarios=%s\n' "$OVERALL" "$(wc -l <"$FAILURES")" >"$PARENT/080-exit-codes.txt"
if [[ "$OVERALL" -eq 0 ]]; then STATUS=PASS; else STATUS=FAIL; fi
printf 'Status: %s\nSuite: Login regression\nMode: %s\nEvidence: %s\n' "$STATUS" "$MODE" "$PARENT" >"$PARENT/090-final-status.txt"
printf '\nLOGIN_REGRESSION_%s %s\nEvidence: %s\n' "$(printf '%s' "$MODE" | tr '[:lower:]-' '[:upper:]_')" "$STATUS" "$PARENT"
exit "$OVERALL"
