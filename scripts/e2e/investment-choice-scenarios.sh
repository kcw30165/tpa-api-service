#!/usr/bin/env bash
set +x
set -Eeuo pipefail
umask 077

# CIM curl scenarios. Configuration is environment-only:
#   BASE_URL                    BFF base URL, e.g. https://host (no trailing slash)
#   SESSION_COOKIE              value of the authenticated portal_sid cookie
#   CSRF_TOKEN                  authenticated X-XSRF-TOKEN value
#   ACCOUNT_REF                 selected member Account-Ref
#   CIM_PASSWORD                test member's password (required only for CIM_USE_PASSWORD=yes)
#   Test member                  must have an editable switch source and at least two available funds
# Optional:
#   ACCEPT_LANGUAGE             defaults to en
#   CIM_USE_PASSWORD=yes        enable token-binding/replay scenarios (up to 3 verifications)
#   CIM_LIVE_SUBMIT=yes         enable the live submit/cancel scenario (3 more verifications)
#   CIM_CONFIRM_LIVE_SUBMIT=I-UNDERSTAND-THIS-CHANGES-REAL-DATA
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
DRY_RUN="${CIM_DRY_RUN:-no}"

if [[ "$LIVE_SUBMIT" == yes && "$USE_PASSWORD" != yes ]]; then
  printf 'ERROR CIM_LIVE_SUBMIT=yes requires CIM_USE_PASSWORD=yes\n' >&2
  exit 2
fi
if [[ "$LIVE_SUBMIT" == yes && "${CIM_CONFIRM_LIVE_SUBMIT:-}" != I-UNDERSTAND-THIS-CHANGES-REAL-DATA ]]; then
  printf 'ERROR live submit requires CIM_CONFIRM_LIVE_SUBMIT=I-UNDERSTAND-THIS-CHANGES-REAL-DATA\n' >&2
  exit 2
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
  local password_issuances=0
  [[ "$USE_PASSWORD" == yes ]] && password_issuances=3
  [[ "$LIVE_SUBMIT" == yes ]] && password_issuances=$((password_issuances + 3))
  printf 'DRY RUN — no network calls; values and request bodies are masked.\n'
  printf 'Scenarios: S1 S2 S3 S4 S5 S6 S7 S8 S9'
  [[ "$USE_PASSWORD" == yes ]] && printf ' P1 P2 P3'
  [[ "$LIVE_SUBMIT" == yes ]] && printf ' L1 L2 L3 L4'
  printf '\nPassword verifications if P1 is available: %s\n' "$password_issuances"
  [[ "$USE_PASSWORD" != yes ]] || printf 'P1 may be skipped if the S1 snapshot cannot form an isolated validation failure.\n'
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
    dry_request POST "$SUBMIT_PATH" yes '<issued-token>' yes
    dry_request POST "$SUBMIT_PATH" yes '<same-consumed-token>' yes
    dry_request POST "$ISSUE_PATH" yes none yes
    dry_request POST "$CANCEL_PATH" yes '<issued-token>' yes
    dry_request POST "$CANCEL_PATH" yes '<same-consumed-token>' yes
    dry_request POST "$ISSUE_PATH" yes none yes
    dry_request POST "$CANCEL_PATH" yes '<issued-token>' yes
  fi
  if [[ "$LIVE_SUBMIT" == yes ]]; then
    dry_request POST "$ISSUE_PATH" yes none yes
    dry_request POST "$SUBMIT_PATH" yes '<issued-token>' yes
    dry_request GET "$DETAILS_PATH" yes none no
    dry_request POST "$ISSUE_PATH" yes none yes
    dry_request POST "$CANCEL_PATH" yes '<issued-token>' yes
    dry_request GET "$DETAILS_PATH" yes none no
    dry_request POST "$ISSUE_PATH" yes none yes
    dry_request POST "$CANCEL_PATH" yes '<issued-token>' yes
  fi
  printf 'DRY RUN complete; no requests were sent.\n'
}

if [[ "$DRY_RUN" == yes ]]; then
  dry_run
  exit 0
fi

for command_name in curl jq date mktemp; do
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
  read_secret_if_missing CIM_PASSWORD 'Test member password: '
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
LIVE_REFERENCE=''
LIVE_SUBMITTED=no
# shellcheck disable=SC2329 # Invoked indirectly by the EXIT trap.
on_exit() {
  local result=$?
  if [[ "$result" -ne 0 && "$FAILED" -eq 0 ]]; then FAILED=1; fi
  if [[ "$result" -ne 0 && "$LIVE_SUBMITTED" == yes ]]; then
    printf 'MANUAL CLEANUP: submitted referenceNo=%s; inspect its status and cancel manually if it remains pending.\n' "$LIVE_REFERENCE" >&2
  fi
  printf 'Summary: passed=%s skipped=%s failed=%s\n' "$PASSED" "$SKIPPED" "$FAILED"
  rm -f -- "$COOKIE_JAR"
  rmdir -- "$WORK_DIR"
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
  local purpose="$1" operation="$2" password="$3"
  ISSUANCE_BODY="$(printf '%s' "$password" | jq -Rsc --arg purpose "$purpose" --argjson operation "$operation" \
    '{password: ., purpose: $purpose, operationPayload: $operation}')"
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
  and (.result.data.intervalPercentage | type == "number" and floor == . and . >= 1 and . <= 100)
  and (.result.data.switchSources | type == "array" and all(.[]; has("switchSource") and has("sequence") and has("available")))
  and (.result.data.funds | type == "array")
  and (.result.data.history | type == "array")
  and (.result.data.pendingInstructions | type == "array")
' <<<"$RESPONSE_BODY" >/dev/null 2>&1 || fail_scenario S1 '200/SUCCESS contract' "$HTTP_STATUS" "$ERROR_CODE"
GET_DATA="$(jq -c '.result.data' <<<"$RESPONSE_BODY")"
editable_source="$(jq -r '[.switchSources[] | select(.available == true) | .switchSource][0] // empty' <<<"$GET_DATA")"
fund_count="$(jq -r '.funds | length' <<<"$GET_DATA")"
first_fund="$(jq -r '.funds[0].fundCode // empty' <<<"$GET_DATA")"
second_fund="$(jq -r '.funds[1].fundCode // empty' <<<"$GET_DATA")"
interval="$(jq -r '.intervalPercentage' <<<"$GET_DATA")"

P1_SKIP_REASON=''
P1_BODY=''
P1_CODE=''
P1_TARGET=''
if [[ "$USE_PASSWORD" == yes ]]; then
  if [[ -z "$first_fund" ]]; then
    P1_SKIP_REASON='S1 has no available fund for an isolated snapshot validation'
  elif [[ "$interval" -gt 1 && -n "$editable_source" && "$fund_count" -ge 2 \
    && -n "$second_fund" && "$first_fund" != "$second_fund" ]]; then
    P1_BODY="$(jq -cn --arg source "$editable_source" --arg first "$first_fund" --arg second "$second_fund" \
      '{switchSources:[$source],allocations:[{fundCode:$first,percentage:1},{fundCode:$second,percentage:99}]}')"
    P1_CODE=err.investment-choice.percentage.interval-invalid
    P1_TARGET='allocations[0].percentage'
  elif [[ "$interval" -gt 1 && -n "$editable_source" && $((100 % interval)) -ne 0 ]]; then
    P1_BODY="$(jq -cn --arg source "$editable_source" --arg fund "$first_fund" \
      '{switchSources:[$source],allocations:[{fundCode:$fund,percentage:100}]}')"
    P1_CODE=err.investment-choice.percentage.interval-invalid
    P1_TARGET='allocations[0].percentage'
  elif [[ $((100 % interval)) -eq 0 ]]; then
    unavailable_source=CIM-UNAVAILABLE-SOURCE
    source_suffix=0
    while jq -e --arg source "$unavailable_source" \
      '.switchSources | any(.[]; .switchSource == $source)' <<<"$GET_DATA" >/dev/null 2>&1; do
      source_suffix=$((source_suffix + 1))
      unavailable_source="CIM-UNAVAILABLE-SOURCE-$source_suffix"
    done
    P1_BODY="$(jq -cn --arg source "$unavailable_source" --arg fund "$first_fund" \
      '{switchSources:[$source],allocations:[{fundCode:$fund,percentage:100}]}')"
    P1_CODE=err.investment-choice.switch-source.unavailable
    P1_TARGET='switchSources[0]'
  else
    P1_SKIP_REASON='S1 has no editable source for interval-invalid, and 100 is not interval-valid for unavailable-source validation'
  fi
fi

if [[ "$LIVE_SUBMIT" == yes ]]; then
  [[ -n "$editable_source" ]] || fail_scenario LIVE-preflight 'editable switch source' 'none' ''
  [[ "$fund_count" -ge 2 && -n "$first_fund" && -n "$second_fund" ]] \
    || fail_scenario LIVE-preflight 'at least two available funds' 'not available' ''
  pending_count="$(jq -r '.pendingInstructions | length' <<<"$GET_DATA")"
  [[ "$pending_count" == 0 ]] || fail_scenario LIVE-preflight 'no pending instruction' 'pending instruction exists' ''
fi
ERROR_CODE=''
pass_scenario S1

PASSWORD_ISSUANCES=0
if [[ "$USE_PASSWORD" == yes ]]; then
  PASSWORD_ISSUANCES=2
  [[ -n "$P1_SKIP_REASON" ]] || PASSWORD_ISSUANCES=$((PASSWORD_ISSUANCES + 1))
  [[ "$LIVE_SUBMIT" == yes ]] && PASSWORD_ISSUANCES=$((PASSWORD_ISSUANCES + 3))
  printf 'WARNING: every token issuance verifies the password and counts toward the member password-validation throttle.\n'
  printf 'Password verifications this run: %s\n' "$PASSWORD_ISSUANCES"
  [[ -z "$P1_SKIP_REASON" ]] || printf 'P1 will be skipped if reached: %s\n' "$P1_SKIP_REASON"
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
assert_result S9-extra 400 err.password-validation.request.invalid

SCENARIO=S9-blank
MALFORMED_CANCEL='{"referenceNo":" "}'
password_json CANCEL_INVESTMENT_CHOICE "$MALFORMED_CANCEL" 'not-a-real-password'
REQUEST_BODY="$ISSUANCE_BODY"
request POST "$ISSUE_PATH" yes none present
assert_result S9-blank 400 err.password-validation.request.invalid

if [[ "$USE_PASSWORD" == yes ]]; then
  if [[ -n "$P1_SKIP_REASON" ]]; then
    skip_scenario P1 "$P1_SKIP_REASON"
  else
    SCENARIO=P1-issue
    issue_token CHANGE_INVESTMENT_CHOICE "$P1_BODY" "$CIM_PASSWORD" P1-issue
    P1_TOKEN="$TOKEN_VALUE"
    REQUEST_BODY="$P1_BODY"
    SCENARIO=P1-submit
    request POST "$SUBMIT_PATH" yes "$P1_TOKEN" present
    assert_result P1-submit 422 "$P1_CODE"
    assert_error_target "$P1_TARGET"
    REQUEST_BODY="$P1_BODY"
    request POST "$SUBMIT_PATH" yes "$P1_TOKEN" present
    assert_result P1-reuse 403 err.password-validation.authorization.invalid
  fi

  P2_BODY='{"referenceNo":"000000000"}'
  SCENARIO=P2-issue
  issue_token CANCEL_INVESTMENT_CHOICE "$P2_BODY" "$CIM_PASSWORD" P2-issue
  P2_TOKEN="$TOKEN_VALUE"
  REQUEST_BODY="$P2_BODY"
  request POST "$CANCEL_PATH" yes "$P2_TOKEN" present
  assert_result P2-submit 422 err.investment-choice.cancel.ref-invalid
  assert_error_target referenceNo
  REQUEST_BODY="$P2_BODY"
  request POST "$CANCEL_PATH" yes "$P2_TOKEN" present
  assert_result P2-reuse 403 err.password-validation.authorization.invalid

  SCENARIO=P3-issue
  issue_token CANCEL_INVESTMENT_CHOICE "$P2_BODY" "$CIM_PASSWORD" P3-issue
  P3_TOKEN="$TOKEN_VALUE"
  REQUEST_BODY='{"referenceNo":"000000001"}'
  request POST "$CANCEL_PATH" yes "$P3_TOKEN" present
  assert_result P3-body-binding 403 err.password-validation.authorization.invalid

  if [[ "$LIVE_SUBMIT" == yes ]]; then
    [[ "$interval" =~ ^[0-9]+$ && "$interval" -gt 0 ]] || fail_scenario LIVE-preflight 'valid interval percentage' 'invalid' ''
    [[ $((100 % interval)) -eq 0 ]] || fail_scenario LIVE-preflight '100 divisible by interval percentage' 'not divisible' ''

    if (( interval <= 50 )); then
      live_first="$interval"
      live_second=$((100 - interval))
      LIVE_BODY="$(jq -cn --arg source "$editable_source" --arg first "$first_fund" --arg second "$second_fund" \
        --argjson firstPercentage "$live_first" --argjson secondPercentage "$live_second" \
        '{switchSources:[$source],allocations:[{fundCode:$first,percentage:$firstPercentage},{fundCode:$second,percentage:$secondPercentage}]}')"
    else
      LIVE_BODY="$(jq -cn --arg source "$editable_source" --arg fund "$first_fund" \
        '{switchSources:[$source],allocations:[{fundCode:$fund,percentage:100}]}')"
    fi

    SCENARIO=L1-issue
    issue_token CHANGE_INVESTMENT_CHOICE "$LIVE_BODY" "$CIM_PASSWORD" L1-issue
    LIVE_TOKEN="$TOKEN_VALUE"
    REQUEST_BODY="$LIVE_BODY"
    request POST "$SUBMIT_PATH" yes "$LIVE_TOKEN" present
    [[ "$HTTP_STATUS" == 200 ]] || fail_scenario L1-submit '200/SUBMITTED' "$HTTP_STATUS" "$ERROR_CODE"
    jq -e '
      .success == true and .status == "SUBMITTED"
      and (.result.data.referenceNo | type == "string" and length > 0)
      and (.result.data.submitDate | test("^[0-9]{2}/[0-9]{2}/[0-9]{4}$"))
      and (.result.data.submitTime | test("^[0-9]{2}:[0-9]{2}:[0-9]{2}$"))
    ' <<<"$RESPONSE_BODY" >/dev/null 2>&1 || fail_scenario L1-submit 'SUBMITTED response contract' "$HTTP_STATUS" "$ERROR_CODE"
    LIVE_REFERENCE="$(jq -r '.result.data.referenceNo' <<<"$RESPONSE_BODY")"
    LIVE_SUBMITTED=yes
    ERROR_CODE=''
    printf 'PASS L1-submit status=%s code=- referenceNo=%s\n' "$HTTP_STATUS" "$LIVE_REFERENCE"
    PASSED=$((PASSED + 1))

    SCENARIO=L2-get
    request GET "$DETAILS_PATH" yes none no
    [[ "$HTTP_STATUS" == 200 ]] || fail_scenario L2-get 200 "$HTTP_STATUS" "$ERROR_CODE"
    jq -e --arg ref "$LIVE_REFERENCE" '.result.data.pendingInstructions | any(.[]; .referenceNo == $ref)' \
      <<<"$RESPONSE_BODY" >/dev/null 2>&1 || fail_scenario L2-get 'submitted reference in pendingInstructions' "$HTTP_STATUS" "$ERROR_CODE"
    GET_DATA="$(jq -c '.result.data' <<<"$RESPONSE_BODY")"
    ERROR_CODE=''
    pass_scenario L2-get

    LIVE_CANCEL_BODY="$(jq -cn --arg ref "$LIVE_REFERENCE" '{referenceNo:$ref}')"
    SCENARIO=L3-issue
    issue_token CANCEL_INVESTMENT_CHOICE "$LIVE_CANCEL_BODY" "$CIM_PASSWORD" L3-issue
    LIVE_CANCEL_TOKEN="$TOKEN_VALUE"
    REQUEST_BODY="$LIVE_CANCEL_BODY"
    request POST "$CANCEL_PATH" yes "$LIVE_CANCEL_TOKEN" present
    if [[ "$HTTP_STATUS" == 422 && "$ERROR_CODE" == err.investment-choice.cancel.not-cancellable ]]; then
      printf 'FAIL L3-cancel instruction is already processing; manual handling is required. referenceNo=%s\n' "$LIVE_REFERENCE" >&2
      FAILED=1
      exit 1
    fi
    [[ "$HTTP_STATUS" == 200 ]] || fail_scenario L3-cancel '200/SUCCESS' "$HTTP_STATUS" "$ERROR_CODE"
    jq -e --arg ref "$LIVE_REFERENCE" '.status == "SUCCESS" and .result.data.referenceNo == $ref' \
      <<<"$RESPONSE_BODY" >/dev/null 2>&1 || fail_scenario L3-cancel 'SUCCESS with matching referenceNo' "$HTTP_STATUS" "$ERROR_CODE"
    ERROR_CODE=''
    pass_scenario L3-cancel

    SCENARIO=L4-get
    request GET "$DETAILS_PATH" yes none no
    [[ "$HTTP_STATUS" == 200 ]] || fail_scenario L4-get 200 "$HTTP_STATUS" "$ERROR_CODE"
    jq -e --arg ref "$LIVE_REFERENCE" '(.result.data.pendingInstructions | any(.[]; .referenceNo == $ref)) | not' \
      <<<"$RESPONSE_BODY" >/dev/null 2>&1 || fail_scenario L4-get 'cancelled reference absent from pendingInstructions' "$HTTP_STATUS" "$ERROR_CODE"
    GET_DATA="$(jq -c '.result.data' <<<"$RESPONSE_BODY")"
    ERROR_CODE=''
    pass_scenario L4-get

    SCENARIO=L4-issue
    issue_token CANCEL_INVESTMENT_CHOICE "$LIVE_CANCEL_BODY" "$CIM_PASSWORD" L4-issue
    REQUEST_BODY="$LIVE_CANCEL_BODY"
    request POST "$CANCEL_PATH" yes "$TOKEN_VALUE" present
    assert_result L4-repeat-cancel 422 err.investment-choice.cancel.ref-invalid
    assert_error_target referenceNo
  fi
fi

printf 'All enabled CIM scenarios completed.\n'
exit 0
