#!/usr/bin/env bash
set +x
set -Eeuo pipefail
umask 077
# shellcheck source=scripts/e2e/lib/cim-password.sh
source "$(dirname -- "${BASH_SOURCE[0]}")/lib/cim-password.sh"
# shellcheck source=scripts/e2e/lib/cim-valid-payload.sh
source "$(dirname -- "${BASH_SOURCE[0]}")/lib/cim-valid-payload.sh"

# CIM curl scenarios. Configuration is environment-only:
#   BASE_URL                    BFF base URL, e.g. https://host (no trailing slash)
#   SESSION_COOKIE              value of the authenticated portal_sid cookie
#   CSRF_TOKEN                  authenticated X-XSRF-TOKEN value
#   ACCOUNT_REF                 selected member Account-Ref
#   CIM_PASSWORD                test member's password (required only for CIM_USE_PASSWORD=yes)
#   LOGIN_TEST_PASSWORD         fallback when CIM_PASSWORD is not provided
#   CIM_RANDOM_SEED             optional integer 0..32767 for reproducible payload selection
# Optional:
#   ACCEPT_LANGUAGE             defaults to en
#   CIM_USE_PASSWORD=yes        enable password-validation scenarios using the S1 snapshot
#   CIM_LIVE_SUBMIT=yes         enable live submit/cancel using the P0 token and a live cancel token
#   CIM_CONFIRM_LIVE_SUBMIT=I-UNDERSTAND-THIS-CHANGES-REAL-DATA
#   CIM_LIVE_CANCEL_PENDING=yes enable one separately authorized existing-instruction cancellation
#   CIM_CONFIRM_LIVE_PENDING_CANCEL=I-UNDERSTAND-THIS-CANCELS-AN-EXISTING-INSTRUCTION
#   CIM_RECOVERY_DIR            existing private directory (mode 0700) for incomplete live cleanup records
#   CIM_DRY_RUN=yes             list planned scenarios/commands without network access
#
# Input secrets may be supplied in the environment or entered at a silent prompt.
# They are never accepted as script arguments, printed, or written to files. Curl's
# response cookie jar is kept under a private mktemp directory and removed on exit.

readonly ISSUE_PATH=/api/v1/password-validation
readonly DETAILS_PATH=/api/v1/investment-choice
readonly SUBMIT_PATH=/api/v1/investment-choice/instructions
readonly CANCEL_PATH=/api/v1/investment-choice/instructions/cancel
readonly TOKEN_HEADER=X-Password-Validation-Token
readonly CONNECT_TIMEOUT=5
readonly REQUEST_TIMEOUT=20

BASE_URL="${BASE_URL:-}"
ACCEPT_LANGUAGE="${ACCEPT_LANGUAGE:-en}"
SESSION_COOKIE="${SESSION_COOKIE:-}"
CSRF_TOKEN="${CSRF_TOKEN:-}"
ACCOUNT_REF="${ACCOUNT_REF:-}"
CIM_PASSWORD="${CIM_PASSWORD:-}"
USE_PASSWORD="${CIM_USE_PASSWORD:-no}"
LIVE_SUBMIT="${CIM_LIVE_SUBMIT:-no}"
LIVE_CANCEL_PENDING="${CIM_LIVE_CANCEL_PENDING:-no}"
RECOVERY_DIR="${CIM_RECOVERY_DIR:-}"
DRY_RUN="${CIM_DRY_RUN:-no}"

if [[ -n "${CIM_RANDOM_SEED:-}" ]] && ! cim_resolve_random_seed "$CIM_RANDOM_SEED"; then
  printf 'ERROR CIM_RANDOM_SEED must be an integer from 0 through 32767\n' >&2
  exit 2
fi

if [[ "$LIVE_SUBMIT" == yes && "$USE_PASSWORD" != yes ]]; then
  printf 'ERROR CIM_LIVE_SUBMIT=yes requires CIM_USE_PASSWORD=yes\n' >&2
  exit 2
fi
if [[ "$LIVE_SUBMIT" == yes && "${CIM_CONFIRM_LIVE_SUBMIT:-}" != I-UNDERSTAND-THIS-CHANGES-REAL-DATA ]]; then
  printf 'ERROR live submit requires CIM_CONFIRM_LIVE_SUBMIT=I-UNDERSTAND-THIS-CHANGES-REAL-DATA\n' >&2
  exit 2
fi
if [[ "$LIVE_CANCEL_PENDING" == yes && "$USE_PASSWORD" != yes ]]; then
  printf 'ERROR CIM_LIVE_CANCEL_PENDING=yes requires CIM_USE_PASSWORD=yes\n' >&2
  exit 2
fi
if [[ "$LIVE_CANCEL_PENDING" == yes \
  && "${CIM_CONFIRM_LIVE_PENDING_CANCEL:-}" != I-UNDERSTAND-THIS-CANCELS-AN-EXISTING-INSTRUCTION ]]; then
  printf 'ERROR existing-instruction cancellation requires CIM_CONFIRM_LIVE_PENDING_CANCEL=I-UNDERSTAND-THIS-CANCELS-AN-EXISTING-INSTRUCTION\n' >&2
  exit 2
fi
if [[ "$LIVE_SUBMIT" == yes && "$LIVE_CANCEL_PENDING" == yes ]]; then
  printf 'ERROR CIM_LIVE_SUBMIT and CIM_LIVE_CANCEL_PENDING must run separately\n' >&2
  exit 2
fi
if [[ "$LIVE_SUBMIT" == yes || "$LIVE_CANCEL_PENDING" == yes ]]; then
  if [[ -z "$RECOVERY_DIR" || "$RECOVERY_DIR" != /* || ! -d "$RECOVERY_DIR" || -L "$RECOVERY_DIR" ]]; then
    printf 'ERROR live mutation requires CIM_RECOVERY_DIR to name an existing private directory\n' >&2
    exit 2
  fi
  recovery_permissions="$(stat -c '%a %u' -- "$RECOVERY_DIR" 2>/dev/null || true)"
  if [[ "$recovery_permissions" != "700 $(id -u)" ]]; then
    printf 'ERROR CIM_RECOVERY_DIR must be owned by the current user with mode 0700\n' >&2
    exit 2
  fi
fi

dry_request() {
  local method="$1" path="$2" auth="$3" token="$4" body="$5" account_ref csrf
  account_ref="${6:-$auth}"
  csrf="${7:-$auth}"
  printf '  curl --connect-timeout %s --max-time %s -X %s <BASE_URL>%s' \
    "$CONNECT_TIMEOUT" "$REQUEST_TIMEOUT" "$method" "$path"
  printf " -H 'Accept-Language: <configured>' -H 'X-Request-Id: <fresh>'"
  if [[ "$auth" == yes ]]; then
    printf " -H 'Cookie: portal_sid=<masked>'"
  fi
  [[ "$csrf" == yes ]] && printf " -H 'X-XSRF-TOKEN: <masked>'"
  [[ "$account_ref" == yes ]] && printf " -H 'Account-Ref: <masked>'"
  [[ "$token" == none ]] || printf " -H '%s: %s'" "$TOKEN_HEADER" "$token"
  [[ "$body" == no ]] || printf " --data-binary '<body masked>'"
  printf '\n'
}

skip_scenario() {
  local name="$1" reason="$2"
  printf 'SKIP %s reason=%s\n' "$name" "$reason"
  SKIPPED=$((SKIPPED + 1))
}

dry_run() {
  printf 'DRY RUN — no network calls; values and request bodies are masked.\n'
  printf 'Scenarios: S1 S2 S3 S4 S5 S6 S7 S8 S9'
  [[ "$USE_PASSWORD" == yes ]] && printf ' P0 P1 P2 P3'
  [[ "$LIVE_SUBMIT" == yes ]] && printf ' L1 L2 L3 L4'
  [[ "$LIVE_CANCEL_PENDING" == yes ]] && printf ' E1 E2'
  printf '\nPassword verification count depends on the snapshot and enabled live path.\n'
  [[ "$USE_PASSWORD" != yes ]] || printf 'CIM_RANDOM_SEED=%s\n' "${CIM_RANDOM_SEED:-<generated>}"
  [[ "$USE_PASSWORD" != yes ]] || printf 'P0 requires a normal snapshot with an eligible source, at least two eligible funds, and a feasible interval.\n'
  [[ "$LIVE_SUBMIT" != yes ]] || printf 'Live submit additionally requires three distinct eligible funds and three interval-compliant allocations totaling 100.\n'
  [[ "$LIVE_CANCEL_PENDING" != yes ]] || printf 'Existing cancellation selects only the first reference in the initial GET snapshot and never falls through to another.\n'
  printf 'Planned curl commands:\n'
  dry_request GET "$DETAILS_PATH" yes none no
  dry_request GET "$DETAILS_PATH" no none no yes yes
  dry_request POST "$SUBMIT_PATH" yes none yes
  dry_request POST "$SUBMIT_PATH" yes '<invalid-token>' yes
  dry_request POST "$ISSUE_PATH" yes none yes
  dry_request POST "$SUBMIT_PATH" yes none yes
  dry_request POST "$CANCEL_PATH" yes none yes
  dry_request POST "$CANCEL_PATH" yes '<invalid-token>' yes
  dry_request POST "$ISSUE_PATH" yes none yes
  dry_request POST "$ISSUE_PATH" yes none yes
  if [[ "$USE_PASSWORD" == yes ]]; then
    dry_request POST "$ISSUE_PATH" yes none yes
    dry_request POST "$ISSUE_PATH" yes none yes
    dry_request POST "$ISSUE_PATH" yes none yes
    dry_request POST "$CANCEL_PATH" yes '<issued-token-bound-to-pending-reference>' yes
  fi
  if [[ "$LIVE_CANCEL_PENDING" == yes ]]; then
    dry_request POST "$ISSUE_PATH" yes none yes
    dry_request POST "$CANCEL_PATH" yes '<fresh-token-bound-to-first-snapshot-reference>' yes
    dry_request GET "$DETAILS_PATH" yes none no
  fi
  if [[ "$LIVE_SUBMIT" == yes ]]; then
    dry_request POST "$SUBMIT_PATH" yes '<P0-token-bound-to-snapshot-payload>' yes
    dry_request GET "$DETAILS_PATH" yes none no
    dry_request POST "$ISSUE_PATH" yes none yes
    dry_request POST "$CANCEL_PATH" yes '<issued-token>' yes
    dry_request GET "$DETAILS_PATH" yes none no
    dry_request POST "$ISSUE_PATH" yes none yes
  fi
  printf 'DRY RUN complete; no requests were sent.\n'
}

if [[ "$DRY_RUN" == yes ]]; then
  dry_run
  exit 0
fi

for command_name in curl jq date mktemp stat id; do
  command -v "$command_name" >/dev/null 2>&1 || {
    printf 'ERROR required command is unavailable: %s\n' "$command_name" >&2
    exit 2
  }
done
[[ -n "$BASE_URL" ]] || { printf 'ERROR BASE_URL is required\n' >&2; exit 2; }
[[ "$BASE_URL" == http://* || "$BASE_URL" == https://* ]] || {
  printf 'ERROR BASE_URL must use HTTP or HTTPS\n' >&2
  exit 2
}
BASE_URL="${BASE_URL%/}"

read_secret_if_missing() {
  local variable="$1" prompt="$2" value
  [[ -n "${!variable}" ]] && return 0
  [[ -r /dev/tty ]] || { printf 'ERROR %s is required (or run from a terminal for a silent prompt)\n' "$variable" >&2; exit 2; }
  IFS= read -r -s -p "$prompt" value </dev/tty
  printf '\n' >/dev/tty
  [[ -n "$value" ]] || { printf 'ERROR %s is required\n' "$variable" >&2; exit 2; }
  printf -v "$variable" '%s' "$value"
}

read_secret_if_missing SESSION_COOKIE 'Authenticated portal_sid cookie: '
read_secret_if_missing CSRF_TOKEN 'Authenticated X-XSRF-TOKEN: '
read_secret_if_missing ACCOUNT_REF 'Selected Account-Ref: '
if [[ "$USE_PASSWORD" == yes ]]; then
  acquire_cim_password
fi

for value in "$SESSION_COOKIE" "$CSRF_TOKEN" "$ACCOUNT_REF" "$CIM_PASSWORD" "$ACCEPT_LANGUAGE"; do
  [[ "$value" != *$'\n'* && "$value" != *$'\r'* ]] || {
    printf 'ERROR configured values must not contain line breaks\n' >&2
    exit 2
  }
done

WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/cim-curl.XXXXXX")"
chmod 700 "$WORK_DIR"
COOKIE_JAR="$WORK_DIR/cookies.txt"
touch "$COOKIE_JAR"
chmod 600 "$COOKIE_JAR"

PASSED=0
SKIPPED=0
FAILED=0
SCENARIO=''
LIVE_REFERENCE_FILE="$WORK_DIR/live-reference.json"
touch "$LIVE_REFERENCE_FILE"
chmod 600 "$LIVE_REFERENCE_FILE"
PENDING_CANCEL_REFERENCE_FILE="$WORK_DIR/pending-cancel-reference.json"
touch "$PENDING_CANCEL_REFERENCE_FILE"
chmod 600 "$PENDING_CANCEL_REFERENCE_FILE"
P3_REFERENCE_FILE="$WORK_DIR/p3-reference.json"
touch "$P3_REFERENCE_FILE"
chmod 600 "$P3_REFERENCE_FILE"
LIVE_SUBMITTED=no
LIVE_SUBMIT_ATTEMPTED=no
LIVE_CLEANUP_VERIFIED=no
PENDING_CANCEL_SELECTED=no
PENDING_CANCEL_ATTEMPTED=no
PENDING_CANCEL_VERIFIED=no
PENDING_CANCEL_OUTCOME=not-attempted
# shellcheck disable=SC2329 # Invoked indirectly by the EXIT trap.
on_exit() {
  local result=$?
  if [[ "$result" -ne 0 && "$FAILED" -eq 0 ]]; then FAILED=1; fi
  if [[ "$LIVE_SUBMIT_ATTEMPTED" == yes && "$LIVE_CLEANUP_VERIFIED" != yes ]]; then
    local recovery_file recovery_outcome recovery_written
    recovery_file="$(mktemp "$RECOVERY_DIR/cim-recovery.XXXXXX" 2>/dev/null || true)"
    recovery_outcome=unknown
    recovery_written=no
    [[ "$LIVE_SUBMITTED" != yes ]] || recovery_outcome=confirmed
    if [[ -n "$recovery_file" ]]; then
      if [[ "$LIVE_SUBMITTED" == yes ]]; then
        if jq -cn --arg outcome "$recovery_outcome" --slurpfile reference "$LIVE_REFERENCE_FILE" \
          '{status:"cleanup-incomplete",submitOutcome:$outcome,referenceNo:$reference[0]}' \
          >"$recovery_file"; then recovery_written=yes; fi
      else
        if jq -cn --arg outcome "$recovery_outcome" \
          '{status:"cleanup-incomplete",submitOutcome:$outcome}' >"$recovery_file"; then
          recovery_written=yes
        fi
      fi
    fi
    if [[ -n "$recovery_file" && "$recovery_written" == yes ]] && chmod 600 "$recovery_file"; then
      printf 'CLEANUP INCOMPLETE: private recovery record saved at %s; inspect it through secure local access.\n' "$recovery_file" >&2
      FAILED=1
      result=1
    else
      [[ -z "$recovery_file" ]] || rm -f -- "$recovery_file"
      printf 'CLEANUP INCOMPLETE: private recovery record could not be saved; do not retry submission or cancellation automatically.\n' >&2
      result=1
    fi
  fi
  if [[ "$PENDING_CANCEL_SELECTED" == yes && "$PENDING_CANCEL_VERIFIED" != yes ]]; then
    local pending_recovery_file pending_recovery_written
    pending_recovery_file="$(mktemp "$RECOVERY_DIR/cim-recovery.XXXXXX" 2>/dev/null || true)"
    pending_recovery_written=no
    if [[ -n "$pending_recovery_file" ]] \
      && jq -cn --arg outcome "$PENDING_CANCEL_OUTCOME" --arg attempted "$PENDING_CANCEL_ATTEMPTED" \
        --slurpfile reference "$PENDING_CANCEL_REFERENCE_FILE" \
        '{status:"cleanup-incomplete",operation:"cancel-existing-pending",cancelAttempted:($attempted=="yes"),cancelOutcome:$outcome,referenceNo:$reference[0]}' \
        >"$pending_recovery_file"; then
      pending_recovery_written=yes
    fi
    if [[ -n "$pending_recovery_file" && "$pending_recovery_written" == yes ]] \
      && chmod 600 "$pending_recovery_file"; then
      printf 'CLEANUP INCOMPLETE: private recovery record saved at %s; inspect it through secure local access.\n' "$pending_recovery_file" >&2
      FAILED=1
      result=1
    else
      [[ -z "$pending_recovery_file" ]] || rm -f -- "$pending_recovery_file"
      printf 'CLEANUP INCOMPLETE: private recovery record could not be saved; do not retry cancellation automatically.\n' >&2
      result=1
    fi
  fi
  printf 'Summary: passed=%s skipped=%s failed=%s\n' "$PASSED" "$SKIPPED" "$FAILED"
  rm -f -- "$COOKIE_JAR" "$LIVE_REFERENCE_FILE" "$PENDING_CANCEL_REFERENCE_FILE" "$P3_REFERENCE_FILE"
  rmdir -- "$WORK_DIR"
  [[ "$result" -eq 0 ]] || exit "$result"
}
trap on_exit EXIT

fail_scenario() {
  local name="$1" expected="$2" actual="$3" code="${4:--}"
  FAILED=1
  printf 'FAIL %s expected=%s got=%s code=%s\n' "$name" "$expected" "$actual" "$code" >&2
  exit 1
}

pass_scenario() {
  local name="$1"
  printf 'PASS %s status=%s code=%s\n' "$name" "$HTTP_STATUS" "${ERROR_CODE:--}"
  PASSED=$((PASSED + 1))
}

assert_result() {
  local name="$1" status="$2" code="$3"
  [[ "$HTTP_STATUS" == "$status" && "$ERROR_CODE" == "$code" ]] \
    || fail_scenario "$name" "$status${code:+/$code}" "$HTTP_STATUS" "$ERROR_CODE"
  pass_scenario "$name"
}

assert_error_target() {
  local target="$1"
  jq -e --arg target "$target" \
    '(.errors[0].targets | type == "array") and (.errors[0].targets | index($target) != null)' \
    <<<"$RESPONSE_BODY" >/dev/null 2>&1 || fail_scenario "$SCENARIO" "target=$target" "$HTTP_STATUS" "$ERROR_CODE"
}

request() {
  local method="$1" path="$2" authenticated="$3" token="$4" body="$5" include_account_ref include_csrf
  local request_id raw curl_rc header_fd body_fd
  include_account_ref="${6:-$authenticated}"
  include_csrf="${7:-$authenticated}"
  REQUEST_NUMBER=$((REQUEST_NUMBER + 1))
  request_id="cim-${BASHPID}-${REQUEST_NUMBER}-$(date +%s%N)"
  exec {header_fd}< <(
    printf 'Accept-Language: %s\nX-Request-Id: %s\n' "$ACCEPT_LANGUAGE" "$request_id"
    if [[ "$authenticated" == yes ]]; then
      printf 'Cookie: portal_sid=%s\n' "$SESSION_COOKIE"
    fi
    [[ "$include_csrf" == yes ]] && printf 'X-XSRF-TOKEN: %s\n' "$CSRF_TOKEN"
    [[ "$include_account_ref" == yes ]] && printf 'Account-Ref: %s\n' "$ACCOUNT_REF"
    [[ "$token" == none ]] || printf '%s: %s\n' "$TOKEN_HEADER" "$token"
  )
  local -a args=(
    --silent --show-error
    --connect-timeout "$CONNECT_TIMEOUT"
    --max-time "$REQUEST_TIMEOUT"
    --cookie-jar "$COOKIE_JAR"
    -X "$method"
    -H "@/dev/fd/$header_fd"
    -H 'Accept: application/json'
    "$BASE_URL$path"
  )
  if [[ "$body" == present ]]; then
    exec {body_fd}< <(printf '%s' "$REQUEST_BODY")
    args+=( -H 'Content-Type: application/json' --data-binary "@/dev/fd/$body_fd" )
  fi
  if raw="$(curl "${args[@]}" -w $'\n%{http_code}' 2>/dev/null)"; then
    curl_rc=0
  else
    curl_rc=$?
  fi
  exec {header_fd}<&-
  if [[ "$body" == present ]]; then exec {body_fd}<&-; fi
  HTTP_STATUS=000
  RESPONSE_BODY=''
  if [[ "$curl_rc" -eq 0 ]]; then
    HTTP_STATUS="${raw##*$'\n'}"
    RESPONSE_BODY="${raw%$'\n'*}"
  fi
  ERROR_CODE="$(jq -er '.errors[0].code // empty' <<<"$RESPONSE_BODY" 2>/dev/null || true)"
}

REQUEST_NUMBER=0
HTTP_STATUS=''
ERROR_CODE=''
RESPONSE_BODY=''
REQUEST_BODY=''
GET_DATA=''

password_json() {
  local purpose="$1" operation="$2" password="$3" password_fd operation_fd jq_status
  exec {password_fd}< <(printf '%s' "$password")
  exec {operation_fd}< <(printf '%s' "$operation")
  if ISSUANCE_BODY="$(jq -Rn --arg purpose "$purpose" \
    --rawfile password "/dev/fd/$password_fd" \
    --slurpfile operation "/dev/fd/$operation_fd" \
    '{password:$password,purpose:$purpose,operationPayload:$operation[0]}')"; then
    jq_status=0
  else
    jq_status=$?
  fi
  exec {password_fd}<&-
  exec {operation_fd}<&-
  [[ "$jq_status" -eq 0 ]] || return "$jq_status"
}

issue_token() {
  local purpose="$1" operation="$2" password="$3" label="$4"
  password_json "$purpose" "$operation" "$password"
  REQUEST_BODY="$ISSUANCE_BODY"
  request POST "$ISSUE_PATH" yes none present
  assert_result "$label" 200 ''
  TOKEN_VALUE="$(jq -er '.result.data.token // empty' <<<"$RESPONSE_BODY" 2>/dev/null || true)"
  [[ -n "$TOKEN_VALUE" ]] || fail_scenario "$label" 'token present' "$HTTP_STATUS" "$ERROR_CODE"
}

printf 'Running CIM curl scenarios\n'

SCENARIO=S1
request GET "$DETAILS_PATH" yes none no
[[ "$HTTP_STATUS" == 200 ]] || fail_scenario S1 '200/SUCCESS response contract' "$HTTP_STATUS" "$ERROR_CODE"
jq -e '
  .success == true and .status == "SUCCESS"
  and (keys | sort) == ["errors","messages","result","status","success"]
  and (.result.data.accountNumber | type == "string")
  and (.result.data.scenario == "NORMAL"
    or .result.data.scenario == "FUNCTION_UNAVAILABLE"
    or .result.data.scenario == "MEMBER_INELIGIBLE")
  and (.result.data.history | type == "array")
  and (if .result.data.scenario == "NORMAL" then
    (.result.data.intervalPercentage | type == "number" and floor == . and . >= 1 and . <= 100)
    and (.result.data.switchSources | type == "array" and all(.[]; has("switchSourceCode") and has("switchSource") and has("sequence") and has("available")))
    and (.result.data.funds | type == "array" and all(.[]; has("fundCode")))
    and (.result.data.pendingInstructions | type == "array")
  else
    ((.result.data | has("intervalPercentage")) | not)
    and ((.result.data | has("switchSources")) | not)
    and ((.result.data | has("funds")) | not)
    and ((.result.data | has("pendingInstructions")) | not)
  end)
' <<<"$RESPONSE_BODY" >/dev/null 2>&1 || fail_scenario S1 '200/SUCCESS contract' "$HTTP_STATUS" "$ERROR_CODE"
GET_DATA="$(jq -c '.result.data' <<<"$RESPONSE_BODY")"
SNAPSHOT_SCENARIO="$(jq -r '.scenario' <<<"$GET_DATA")"
interval=''
editable_source=''
fund_count=0
pending_count=0
pending_reference=''
VALID_BODY=''
VALID_SKIP_REASON=''
P1_SKIP_REASON=''
P1_BODY=''
P1_CODE=''
P1_TARGET=''
P2_SKIP_REASON=''
P3_SKIP_REASON=''
P0_TOKEN=''

if [[ "$SNAPSHOT_SCENARIO" == NORMAL ]]; then
  interval="$(jq -r '.intervalPercentage' <<<"$GET_DATA")"
  editable_source="$(jq -r '[.switchSources[] | select(.available == true) | .switchSourceCode][0] // empty' <<<"$GET_DATA")"
  fund_count="$(jq -r '[.funds[].fundCode | select(type == "string" and length > 0)] | unique | length' <<<"$GET_DATA")"
  pending_count="$(jq -r '.pendingInstructions | length' <<<"$GET_DATA")"
  pending_reference="$(jq -r '.pendingInstructions[0].referenceNo // empty' <<<"$GET_DATA")"
else
  VALID_SKIP_REASON="snapshot scenario=$SNAPSHOT_SCENARIO does not support a normal submission"
  P1_SKIP_REASON="$VALID_SKIP_REASON"
fi

if [[ "$USE_PASSWORD" == yes && "$SNAPSHOT_SCENARIO" == NORMAL ]]; then
  if cim_generate_valid_payload "$GET_DATA" "${CIM_RANDOM_SEED:-}"; then
    VALID_BODY="$CIM_GENERATED_PAYLOAD"
    printf 'CIM snapshot selection: seed=%s interval=%s funds=%s percentages=%s\n' \
      "$CIM_GENERATED_SEED" "$CIM_GENERATED_INTERVAL" "$CIM_GENERATED_FUND_COUNT" "$CIM_GENERATED_PERCENTAGES"
  else
    generation_result=$?
    VALID_SKIP_REASON="$CIM_GENERATION_REASON"
    if [[ "$generation_result" -ne 2 ]]; then
      fail_scenario P0-preflight 'valid CIM_RANDOM_SEED from 0 through 32767' 'invalid seed' ''
    fi
  fi
  if cim_build_snapshot_negative_payload "$VALID_BODY" "$interval" "$GET_DATA" "${CIM_RANDOM_SEED:-}"; then
    P1_BODY="$CIM_NEGATIVE_PAYLOAD"
    P1_CODE="$CIM_NEGATIVE_CODE"
    P1_TARGET="$CIM_NEGATIVE_TARGET"
    if [[ -z "$VALID_BODY" ]]; then
      printf 'CIM negative selection: seed=%s interval=%s funds=%s percentages=%s\n' \
        "$CIM_NEGATIVE_SEED" "$interval" \
        "$(jq -r '.allocations | length' <<<"$P1_BODY")" \
        "$(jq -r '[.allocations[].percentage] | join(",")' <<<"$P1_BODY")"
    fi
  else
    P1_SKIP_REASON='no eligible fund/source combination for an isolated snapshot precheck'
  fi
fi

if [[ "$LIVE_SUBMIT" == yes ]]; then
  [[ "$SNAPSHOT_SCENARIO" == NORMAL ]] || fail_scenario LIVE-preflight 'NORMAL snapshot scenario' "$SNAPSHOT_SCENARIO" ''
  [[ -n "$VALID_BODY" ]] || fail_scenario LIVE-preflight 'submission-safe generated payload' "$VALID_SKIP_REASON" ''
  [[ -n "$editable_source" ]] || fail_scenario LIVE-preflight 'editable switch source' 'none' ''
  [[ "$fund_count" -ge 3 ]] || fail_scenario LIVE-preflight 'at least three available funds' "$fund_count" ''
  ((100 % interval == 0 && 100 / interval >= 3)) \
    || fail_scenario LIVE-preflight 'interval supports three positive allocations totaling 100' 'ineligible interval' ''
  jq -e --argjson interval "$interval" '
    (.allocations | length) == 3
    and ([.allocations[].fundCode] | unique | length) == 3
    and ([.allocations[].percentage] | add) == 100
    and all(.allocations[]; .percentage > 0 and .percentage % $interval == 0)
  ' <<<"$VALID_BODY" >/dev/null 2>&1 \
    || fail_scenario LIVE-preflight 'three distinct eligible funds with interval-valid percentages totaling 100' 'invalid generated payload' ''
fi
if [[ "$LIVE_CANCEL_PENDING" == yes ]]; then
  [[ "$SNAPSHOT_SCENARIO" == NORMAL ]] \
    || fail_scenario EXISTING-cancel-preflight 'NORMAL snapshot scenario' "$SNAPSHOT_SCENARIO" ''
  [[ "$pending_count" -gt 0 && -n "$pending_reference" ]] \
    || fail_scenario EXISTING-cancel-preflight 'first pending reference in authenticated GET snapshot' 'none available' ''
fi
ERROR_CODE=''
pass_scenario S1

PASSWORD_ISSUANCES=0
if [[ "$USE_PASSWORD" == yes ]]; then
  [[ -z "$VALID_SKIP_REASON" && "$LIVE_CANCEL_PENDING" != yes ]] \
    && PASSWORD_ISSUANCES=$((PASSWORD_ISSUANCES + 1))
  [[ "$SNAPSHOT_SCENARIO" != NORMAL || -z "$pending_reference" || "$LIVE_CANCEL_PENDING" == yes ]] \
    || PASSWORD_ISSUANCES=$((PASSWORD_ISSUANCES + 1))
  [[ "$LIVE_SUBMIT" != yes ]] || PASSWORD_ISSUANCES=$((PASSWORD_ISSUANCES + 1))
  [[ "$LIVE_CANCEL_PENDING" != yes ]] || PASSWORD_ISSUANCES=$((PASSWORD_ISSUANCES + 1))
  printf 'WARNING: every token issuance verifies the password and counts toward the member password-validation throttle.\n'
  printf 'Password verifications this run (snapshot rejections excluded): %s\n' "$PASSWORD_ISSUANCES"
  [[ -z "$VALID_SKIP_REASON" ]] || printf 'P0 will be skipped if reached: %s\n' "$VALID_SKIP_REASON"
fi

SCENARIO=S2
request GET "$DETAILS_PATH" no none no yes yes
assert_result S2 401 err.security.authentication.required

SCENARIO=S3
REQUEST_BODY='{}'
request POST "$SUBMIT_PATH" yes none present
assert_result S3 403 err.password-validation.authorization.invalid

SCENARIO=S4
REQUEST_BODY='{}'
request POST "$SUBMIT_PATH" yes 'invalid-cim-token' present
assert_result S4 403 err.password-validation.authorization.invalid

SCENARIO=S5
MALFORMED_SUBMIT='{"switchSources":["CIM-SOURCE"],"allocations":[{"fundCode":"CIM-FUND","percentage":99}],"unknown":"member"}'
password_json CHANGE_INVESTMENT_CHOICE "$MALFORMED_SUBMIT" 'not-a-real-password'
REQUEST_BODY="$ISSUANCE_BODY"
request POST "$ISSUE_PATH" yes none present
assert_result S5 422 err.investment-choice.request.invalid

SCENARIO=S6
REQUEST_BODY="$MALFORMED_SUBMIT"
request POST "$SUBMIT_PATH" yes none present
assert_result S6 403 err.password-validation.authorization.invalid

SCENARIO=S7
REQUEST_BODY='{"referenceNo":"000000000"}'
request POST "$CANCEL_PATH" yes none present
assert_result S7 403 err.password-validation.authorization.invalid

SCENARIO=S8
REQUEST_BODY='{"referenceNo":"000000000"}'
request POST "$CANCEL_PATH" yes 'invalid-cim-token' present
assert_result S8 403 err.password-validation.authorization.invalid

SCENARIO=S9-extra
MALFORMED_CANCEL='{"referenceNo":"000000000","extra":"member"}'
password_json CANCEL_INVESTMENT_CHOICE "$MALFORMED_CANCEL" 'not-a-real-password'
REQUEST_BODY="$ISSUANCE_BODY"
request POST "$ISSUE_PATH" yes none present
assert_result S9-extra 422 err.investment-choice.request.invalid

SCENARIO=S9-blank
MALFORMED_CANCEL='{"referenceNo":" "}'
password_json CANCEL_INVESTMENT_CHOICE "$MALFORMED_CANCEL" 'not-a-real-password'
REQUEST_BODY="$ISSUANCE_BODY"
request POST "$ISSUE_PATH" yes none present
assert_result S9-blank 422 err.investment-choice.cancel.ref-required

if [[ "$USE_PASSWORD" == yes ]]; then
  if [[ "$LIVE_CANCEL_PENDING" == yes ]]; then
    skip_scenario P0 'dedicated existing-instruction cancellation mode uses its own fresh token'
  elif [[ -z "$VALID_SKIP_REASON" ]]; then
    SCENARIO=P0-issue
    issue_token CHANGE_INVESTMENT_CHOICE "$VALID_BODY" "$CIM_PASSWORD" P0-issue
    P0_TOKEN="$TOKEN_VALUE"
  else
    skip_scenario P0 "$VALID_SKIP_REASON"
  fi

  if [[ -n "$P1_SKIP_REASON" ]]; then
    skip_scenario P1 "$P1_SKIP_REASON"
  else
    SCENARIO=P1-issue
    password_json CHANGE_INVESTMENT_CHOICE "$P1_BODY" "$CIM_PASSWORD"
    REQUEST_BODY="$ISSUANCE_BODY"
    request POST "$ISSUE_PATH" yes none present
    assert_result P1-issue 422 "$P1_CODE"
    assert_error_target "$P1_TARGET"
  fi

  if [[ "$SNAPSHOT_SCENARIO" != NORMAL ]]; then
    P2_SKIP_REASON="snapshot scenario=$SNAPSHOT_SCENARIO does not support a normal cancel precheck"
  fi
  if [[ -n "$P2_SKIP_REASON" ]]; then
    skip_scenario P2 "$P2_SKIP_REASON"
  else
    P2_REFERENCE="$(cim_unavailable_reference "$GET_DATA")"
    P2_BODY="$(jq -cn --arg reference "$P2_REFERENCE" '{referenceNo:$reference}')"
    SCENARIO=P2-issue
    password_json CANCEL_INVESTMENT_CHOICE "$P2_BODY" "$CIM_PASSWORD"
    REQUEST_BODY="$ISSUANCE_BODY"
    request POST "$ISSUE_PATH" yes none present
    assert_result P2-issue 422 err.investment-choice.cancel.ref-invalid
    assert_error_target referenceNo
  fi

  if [[ "$LIVE_CANCEL_PENDING" == yes ]]; then
    skip_scenario P3 'dedicated existing-instruction cancellation mode uses its own fresh token and one cancel attempt'
  elif [[ "$SNAPSHOT_SCENARIO" != NORMAL || -z "$pending_reference" ]]; then
    P3_SKIP_REASON='no existing pending instruction is available for a valid cancellation-token binding test'
  fi
  if [[ "$LIVE_CANCEL_PENDING" != yes && -n "$P3_SKIP_REASON" ]]; then
    skip_scenario P3 "$P3_SKIP_REASON"
  elif [[ "$LIVE_CANCEL_PENDING" != yes ]]; then
    jq -ec '.pendingInstructions[0].referenceNo | select(type == "string" and length > 0)' \
      <<<"$GET_DATA" >"$P3_REFERENCE_FILE" \
      || fail_scenario P3-preflight 'pending instruction reference available privately' "$HTTP_STATUS" "$ERROR_CODE"
    P3_BODY="$(jq -cn --slurpfile reference "$P3_REFERENCE_FILE" '{referenceNo:$reference[0]}')"
    SCENARIO=P3-issue
    issue_token CANCEL_INVESTMENT_CHOICE "$P3_BODY" "$CIM_PASSWORD" P3-issue
    P3_TOKEN="$TOKEN_VALUE"
    P3_WRONG_REFERENCE="$(cim_unavailable_reference "$GET_DATA")"
    REQUEST_BODY="$(jq -cn --arg reference "$P3_WRONG_REFERENCE" '{referenceNo:$reference}')"
    request POST "$CANCEL_PATH" yes "$P3_TOKEN" present
    assert_result P3-body-binding 403 err.password-validation.authorization.invalid
  fi

  if [[ "$LIVE_CANCEL_PENDING" == yes ]]; then
    jq -ec '.pendingInstructions[0].referenceNo | select(type == "string" and length > 0)' \
      <<<"$GET_DATA" >"$PENDING_CANCEL_REFERENCE_FILE" \
      || fail_scenario EXISTING-cancel-preflight 'first pending reference available privately' "$HTTP_STATUS" "$ERROR_CODE"
    PENDING_CANCEL_SELECTED=yes
    PENDING_CANCEL_BODY="$(jq -cn --slurpfile reference "$PENDING_CANCEL_REFERENCE_FILE" '{referenceNo:$reference[0]}')"
    SCENARIO=E1-issue
    issue_token CANCEL_INVESTMENT_CHOICE "$PENDING_CANCEL_BODY" "$CIM_PASSWORD" E1-issue
    PENDING_CANCEL_TOKEN="$TOKEN_VALUE"
    REQUEST_BODY="$PENDING_CANCEL_BODY"
    SCENARIO=E2-cancel
    PENDING_CANCEL_ATTEMPTED=yes
    PENDING_CANCEL_OUTCOME=unknown
    request POST "$CANCEL_PATH" yes "$PENDING_CANCEL_TOKEN" present
    if [[ "$HTTP_STATUS" != 200 ]]; then
      [[ "$ERROR_CODE" != err.investment-choice.cancel.not-cancellable ]] \
        || PENDING_CANCEL_OUTCOME=rejected
      fail_scenario E2-cancel '200/SUCCESS for selected snapshot reference' "$HTTP_STATUS" "$ERROR_CODE"
    fi
    jq -e --slurpfile reference "$PENDING_CANCEL_REFERENCE_FILE" '
      .success == true and .status == "SUCCESS"
      and .result.data.referenceNo == $reference[0]
    ' <<<"$RESPONSE_BODY" >/dev/null 2>&1 \
      || fail_scenario E2-cancel 'SUCCESS with matching selected reference' "$HTTP_STATUS" "$ERROR_CODE"
    PENDING_CANCEL_OUTCOME=confirmed
    ERROR_CODE=''
    pass_scenario E2-cancel

    SCENARIO=E3-get
    request GET "$DETAILS_PATH" yes none no
    [[ "$HTTP_STATUS" == 200 ]] || fail_scenario E3-get 200 "$HTTP_STATUS" "$ERROR_CODE"
    jq -e --slurpfile reference "$PENDING_CANCEL_REFERENCE_FILE" '
      .success == true and .status == "SUCCESS"
      and (.result.data.accountNumber | type == "string")
      and .result.data.scenario == "NORMAL"
      and (.result.data.history | type == "array")
      and (.result.data.pendingInstructions | type == "array")
      and ([.result.data.pendingInstructions[] | select(.referenceNo == $reference[0])] | length == 0)
    ' <<<"$RESPONSE_BODY" >/dev/null 2>&1 \
      || fail_scenario E3-get 'selected reference no longer listed as pending' "$HTTP_STATUS" "$ERROR_CODE"
    PENDING_CANCEL_VERIFIED=yes
    ERROR_CODE=''
    printf 'PASS E3-get status=%s code=- reference_state=not-listed\n' "$HTTP_STATUS"
    PASSED=$((PASSED + 1))
  fi

  if [[ "$LIVE_SUBMIT" == yes ]]; then
    LIVE_BODY="$VALID_BODY"
    LIVE_TOKEN="$P0_TOKEN"
    [[ -n "$LIVE_TOKEN" ]] || fail_scenario LIVE-preflight 'positive P0 token' 'missing' ''
    SCENARIO=L1-submit
    REQUEST_BODY="$LIVE_BODY"
    LIVE_SUBMIT_ATTEMPTED=yes
    request POST "$SUBMIT_PATH" yes "$LIVE_TOKEN" present
    [[ "$HTTP_STATUS" == 200 ]] || fail_scenario L1-submit '200/SUBMITTED' "$HTTP_STATUS" "$ERROR_CODE"
    jq -e '
      .success == true and .status == "SUBMITTED"
      and (.result.data.referenceNo | type == "string" and length > 0)
      and (.result.data.submitDate | test("^[0-9]{2}/[0-9]{2}/[0-9]{4}$"))
      and (.result.data.submitTime | test("^[0-9]{2}:[0-9]{2}:[0-9]{2}$"))
    ' <<<"$RESPONSE_BODY" >/dev/null 2>&1 || fail_scenario L1-submit 'SUBMITTED response contract' "$HTTP_STATUS" "$ERROR_CODE"
    jq -ec '.result.data.referenceNo | select(type == "string" and length > 0)' \
      <<<"$RESPONSE_BODY" >"$LIVE_REFERENCE_FILE" \
      || fail_scenario L1-submit 'submitted reference available privately' "$HTTP_STATUS" "$ERROR_CODE"
    LIVE_SUBMITTED=yes
    ERROR_CODE=''
    pass_scenario L1-submit

    SCENARIO=L2-get
    request GET "$DETAILS_PATH" yes none no
    [[ "$HTTP_STATUS" == 200 ]] || fail_scenario L2-get 200 "$HTTP_STATUS" "$ERROR_CODE"
    jq -e --slurpfile submitted "$LIVE_REFERENCE_FILE" '
      .success == true and .status == "SUCCESS"
      and (.result.data.accountNumber | type == "string")
      and .result.data.scenario == "NORMAL"
      and (.result.data.history | type == "array")
      and (.result.data.pendingInstructions | type == "array")
      and ([.result.data.pendingInstructions[] | select(.referenceNo == $submitted[0])] | length == 1)
    ' <<<"$RESPONSE_BODY" >/dev/null 2>&1 \
      || fail_scenario L2-get 'submitted reference uniquely present in pendingInstructions' "$HTTP_STATUS" "$ERROR_CODE"
    GET_DATA="$(jq -c '.result.data' <<<"$RESPONSE_BODY")"
    ERROR_CODE=''
    pass_scenario L2-get

    LIVE_CANCEL_BODY="$(jq -cn --slurpfile submitted "$LIVE_REFERENCE_FILE" '{referenceNo:$submitted[0]}')"
    SCENARIO=L3-issue
    issue_token CANCEL_INVESTMENT_CHOICE "$LIVE_CANCEL_BODY" "$CIM_PASSWORD" L3-issue
    LIVE_CANCEL_TOKEN="$TOKEN_VALUE"
    REQUEST_BODY="$LIVE_CANCEL_BODY"
    request POST "$CANCEL_PATH" yes "$LIVE_CANCEL_TOKEN" present
    if [[ "$HTTP_STATUS" == 422 && "$ERROR_CODE" == err.investment-choice.cancel.not-cancellable ]]; then
      fail_scenario L3-cancel '200/SUCCESS' "$HTTP_STATUS" "$ERROR_CODE"
    fi
    [[ "$HTTP_STATUS" == 200 ]] || fail_scenario L3-cancel '200/SUCCESS' "$HTTP_STATUS" "$ERROR_CODE"
    jq -e --slurpfile submitted "$LIVE_REFERENCE_FILE" '
      .success == true and .status == "SUCCESS" and .result.data.referenceNo == $submitted[0]
    ' \
      <<<"$RESPONSE_BODY" >/dev/null 2>&1 || fail_scenario L3-cancel 'SUCCESS with matching referenceNo' "$HTTP_STATUS" "$ERROR_CODE"
    ERROR_CODE=''
    pass_scenario L3-cancel

    SCENARIO=L4-get
    request GET "$DETAILS_PATH" yes none no
    [[ "$HTTP_STATUS" == 200 ]] || fail_scenario L4-get 200 "$HTTP_STATUS" "$ERROR_CODE"
    jq -e '
      .success == true and .status == "SUCCESS"
      and (.result.data.accountNumber | type == "string")
      and .result.data.scenario == "NORMAL"
      and (.result.data.history | type == "array")
      and (.result.data.pendingInstructions | type == "array")
    ' <<<"$RESPONSE_BODY" >/dev/null 2>&1 \
      || fail_scenario L4-get '200/SUCCESS GET contract with pendingInstructions array' "$HTTP_STATUS" "$ERROR_CODE"
    if jq -e --slurpfile submitted "$LIVE_REFERENCE_FILE" \
      '.result.data.pendingInstructions | any(.[]; .referenceNo == $submitted[0])' \
      <<<"$RESPONSE_BODY" >/dev/null 2>&1; then
      fail_scenario L4-get 'submitted reference not listed as pending after successful cancel response' "$HTTP_STATUS" "$ERROR_CODE"
    fi
    GET_DATA="$(jq -c '.result.data' <<<"$RESPONSE_BODY")"
    LIVE_CLEANUP_VERIFIED=yes
    ERROR_CODE=''
    printf 'PASS L4-get status=%s code=- reference_state=not-listed\n' "$HTTP_STATUS"
    PASSED=$((PASSED + 1))
  fi
fi

printf 'All enabled CIM scenarios completed.\n'
exit 0
