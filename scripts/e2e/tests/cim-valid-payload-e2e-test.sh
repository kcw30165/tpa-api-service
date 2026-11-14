#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCENARIO="$ROOT/scripts/e2e/investment-choice-scenarios.sh"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT
mkdir -p "$tmp/bin"
real_jq="$(command -v jq)"

cat >"$tmp/bin/curl" <<'FAKE_CURL'
#!/usr/bin/env bash
set -Eeuo pipefail

export FAKE_INSIDE_CURL=yes
FAKE_CALLS="${FAKE_CALLS:-/dev/null}"
FAKE_L2_MODE="${FAKE_L2_MODE:-normal}"
FAKE_L4_MODE="${FAKE_L4_MODE:-normal}"
FAKE_SUBMIT_MODE="${FAKE_SUBMIT_MODE:-normal}"
FAKE_CANCEL_MODE="${FAKE_CANCEL_MODE:-normal}"
FAKE_INTERVAL="${FAKE_INTERVAL:-20}"
FAKE_LIVE_CANCEL_PENDING="${FAKE_LIVE_CANCEL_PENDING:-no}"
FAKE_PENDING_CANCEL_MODE="${FAKE_PENDING_CANCEL_MODE:-normal}"
FAKE_REFERENCE="${FAKE_REFERENCE:-LIVE-REF-1}"
method=GET
url=''
body_arg=''
headers=''
while (($#)); do
  case "$1" in
    -X) method="$2"; shift 2 ;;
    -H)
      if [[ "$2" == @/dev/fd/* ]]; then
        headers="$(cat "${2#@}" 2>/dev/null || true)"
      fi
      shift 2
      ;;
    --data-binary) body_arg="$2"; shift 2 ;;
    http://*|https://*) url="$1"; shift ;;
    *) shift ;;
  esac
done

path="${url#*fake.invalid}"
if [[ "$body_arg" == @/dev/fd/* ]]; then
  request_body="$(cat "${body_arg#@}" 2>/dev/null)" || {
    printf 'fake curl could not read request body descriptor %s\n' "$body_arg" >>"$FAKE_DIAGNOSTIC"
    exit 97
  }
else
  request_body="$body_arg"
fi
token=''
if [[ "$headers" =~ X-Password-Validation-Token:\ ([^$'\r\n']+) ]]; then
  token="${BASH_REMATCH[1]}"
fi
eligible_funds='["FUND-A","FUND-B","FUND-C"]'

status=200
response=''
case "$method $path" in
  'GET /api/v1/investment-choice')
    count="$(cat "$FAKE_GET_COUNT")"
    count=$((count + 1))
    printf '%s' "$count" >"$FAKE_GET_COUNT"
    printf 'GET-%s\n' "$count" >>"$FAKE_CALLS"
    if [[ "$FAKE_LIVE" == yes ]]; then
      case "$count" in
        1) printf 'LIFECYCLE-GET-1\n' >>"$FAKE_CALLS" ;;
        3) printf 'LIFECYCLE-GET-2\n' >>"$FAKE_CALLS" ;;
        4) printf 'LIFECYCLE-GET-3\n' >>"$FAKE_CALLS" ;;
      esac
    fi
    if [[ "$FAKE_LIVE_CANCEL_PENDING" == yes ]]; then
      case "$count" in
        1) printf 'EXISTING-GET-1\n' >>"$FAKE_CALLS" ;;
        3) printf 'EXISTING-GET-2\n' >>"$FAKE_CALLS" ;;
      esac
    fi
    if ((count == 2)); then
      status=401
      response='{"success":false,"status":"ERROR","result":null,"messages":[],"errors":[{"code":"err.security.authentication.required"}]}'
    elif ((count == 3)) && [[ "$FAKE_L2_MODE" == missing || "$FAKE_L2_MODE" == other-first ]]; then
      pending='[]'
      [[ "$FAKE_L2_MODE" != other-first ]] || pending="$(jq -cn --arg ref "$FAKE_REFERENCE" '[{referenceNo:"OTHER-PENDING"},{referenceNo:$ref}]')"
      response="$(jq -cn --argjson interval "$FAKE_INTERVAL" --argjson pending "$pending" --argjson funds "$eligible_funds" '{
        success:true,status:"SUCCESS",result:{data:{
          accountNumber:"TEST-ACCOUNT",scenario:"NORMAL",intervalPercentage:$interval,
          switchSources:[{switchSourceCode:"SRC-A",switchSource:"Display A",sequence:1,available:true}],
          funds:[$funds | to_entries[] | {fundCode:.value,fundName:("Fund "+.value),sequence:(.key+1),riskLevel:"1"}],
          history:[],pendingInstructions:$pending
        }},messages:[],errors:[]
      }')"
    elif [[ "$FAKE_SCENARIO" != NORMAL ]]; then
      response="$(jq -cn --arg scenario "$FAKE_SCENARIO" '{
        success:true,status:"SUCCESS",result:{data:{
          accountNumber:"TEST-ACCOUNT",scenario:$scenario,history:[]
        }},messages:[],errors:[]
      }')"
    else
      pending='[]'
      [[ ! -s "$FAKE_PENDING" ]] || pending="$(cat "$FAKE_PENDING")"
      response="$(jq -cn --argjson pending "$pending" --argjson funds "$eligible_funds" '{
        success:true,status:"SUCCESS",result:{data:{
          accountNumber:"TEST-ACCOUNT",scenario:"NORMAL",intervalPercentage:20,
          switchSources:[{switchSourceCode:"SRC-A",switchSource:"Display A",sequence:1,available:true}],
          funds:[$funds | to_entries[] | {fundCode:.value,fundName:("Fund "+.value),sequence:(.key+1),riskLevel:"1"}],
          history:[],pendingInstructions:$pending
        }},messages:[],errors:[]
      }')"
      if ((count == 4)) && [[ "$FAKE_L4_MODE" == malformed ]]; then
        response='{"success":true,"status":"SUCCESS","result":{"data":{"pendingInstructions":null}},"messages":[],"errors":[]}'
      fi
    fi
    ;;
  'POST /api/v1/password-validation')
    purpose="$(jq -r '.purpose' <<<"$request_body")"
    printf 'ISSUE-%s\n' "$purpose" >>"$FAKE_CALLS"
    operation="$(jq -c '.operationPayload' <<<"$request_body")"
    if [[ "$FAKE_LIVE_CANCEL_PENDING" == yes && "$purpose" == CANCEL_INVESTMENT_CHOICE ]]; then
      issue_count="$(cat "$FAKE_CANCEL_ISSUE_COUNT")"
      issue_count=$((issue_count + 1))
      printf '%s' "$issue_count" >"$FAKE_CANCEL_ISSUE_COUNT"
      if ((issue_count == 1)); then
        printf 'EXISTING-PV\n' >>"$FAKE_CALLS"
      fi
    fi
    if jq -e 'has("unknown")' <<<"$operation" >/dev/null; then
      status=422
      response='{"success":false,"status":"VALIDATION_FAILED","result":null,"messages":[],"errors":[{"code":"err.investment-choice.request.invalid","targets":[] }]}'
    elif [[ "$purpose" == CANCEL_INVESTMENT_CHOICE ]] && jq -e 'has("extra")' <<<"$operation" >/dev/null; then
      status=422
      response='{"success":false,"status":"VALIDATION_FAILED","result":null,"messages":[],"errors":[{"code":"err.investment-choice.request.invalid","targets":[] }]}'
    elif [[ "$purpose" == CANCEL_INVESTMENT_CHOICE ]] && jq -e '.referenceNo | strings | test("^\\s*$")' <<<"$operation" >/dev/null; then
      status=422
      response='{"success":false,"status":"VALIDATION_FAILED","result":null,"messages":[],"errors":[{"code":"err.investment-choice.cancel.ref-required","targets":["referenceNo"]}]}'
    elif [[ "$purpose" == CHANGE_INVESTMENT_CHOICE ]]; then
      interval="$FAKE_INTERVAL"
      invalid="$(jq -r --argjson interval "$interval" '[.allocations[].percentage | select(. % $interval != 0)] | length' <<<"$operation")"
      if ((invalid > 0)); then
        status=422
        response='{"success":false,"status":"VALIDATION_FAILED","result":null,"messages":[],"errors":[{"code":"err.investment-choice.percentage.interval-invalid","targets":["allocations[0].percentage"]}]}'
      elif jq -e --argjson eligible_funds "$eligible_funds" '
        [.allocations[].fundCode] | any(.[]; . as $fund | ($eligible_funds | index($fund)) == null)
      ' <<<"$operation" >/dev/null; then
        status=422
        response='{"success":false,"status":"VALIDATION_FAILED","result":null,"messages":[],"errors":[{"code":"err.investment-choice.fund.unavailable","targets":["allocations[0].fundCode"]}]}'
      else
        jq -c '.' <<<"$operation" >"$FAKE_VALID_PAYLOAD"
        response='{"success":true,"status":"SUCCESS","result":{"data":{"token":"fake-cim-token"}},"messages":[],"errors":[]}'
        if [[ "$FAKE_LIVE" == yes ]]; then
          printf 'LIFECYCLE-CHANGE-ISSUE\n' >>"$FAKE_CALLS"
        fi
      fi
    else
      reference="$(jq -r '.referenceNo // empty' <<<"$operation")"
      if [[ -n "$reference" ]] \
        && jq -e --arg reference "$reference" 'any(.[]; .referenceNo == $reference)' \
          "$FAKE_PENDING" >/dev/null; then
        jq -c '.' <<<"$operation" >"$FAKE_VALID_CANCEL_PAYLOAD"
        response='{"success":true,"status":"SUCCESS","result":{"data":{"token":"fake-cancel-token"}},"messages":[],"errors":[]}'
        if [[ "$FAKE_LIVE" == yes ]]; then
          if (( $(cat "$FAKE_GET_COUNT") >= 3 )); then
            printf 'LIFECYCLE-CANCEL-ISSUE\n' >>"$FAKE_CALLS"
          else
            printf 'LIFECYCLE-P3-CANCEL-ISSUE\n' >>"$FAKE_CALLS"
          fi
        fi
      else
        status=422
        response='{"success":false,"status":"VALIDATION_FAILED","result":null,"messages":[],"errors":[{"code":"err.investment-choice.cancel.ref-invalid","targets":["referenceNo"]}]}'
      fi
    fi
    ;;
  'POST /api/v1/investment-choice/instructions')
    if [[ "$token" == fake-cim-token && "$FAKE_LIVE" == yes ]]; then
      printf 'SUBMIT\n' >>"$FAKE_CALLS"
      printf 'LIFECYCLE-SUBMIT\n' >>"$FAKE_CALLS"
      if [[ "$FAKE_SUBMIT_MODE" == fail ]]; then
        status=500
        response='{"success":false,"status":"ERROR","result":null,"messages":[],"errors":[]}'
      elif [[ "$FAKE_SUBMIT_MODE" == reject ]]; then
        status=503
        response='{"success":false,"status":"SYSTEM_ERROR","result":null,"messages":[],"errors":[{"code":"err.investment-choice.submission.unavailable"}]}'
      else
        submitted="$(jq -c '.' <<<"$request_body")"
        issued="$(cat "$FAKE_VALID_PAYLOAD")"
        if [[ "$submitted" != "$issued" ]]; then
          printf 'payload_reuse=FAIL\n' >"$FAKE_REUSE"
          status=500
          response='{}'
        else
          printf 'payload_reuse=PASS\n' >"$FAKE_REUSE"
          updated_pending="$(jq -cn --arg ref "$FAKE_REFERENCE" --slurpfile existing "$FAKE_PENDING" \
            '$existing[0] + [{referenceNo:$ref,submitDate:"12/11/2026",submitTime:"12:00:00",allocations:[]}]')"
          printf '%s' "$updated_pending" >"$FAKE_PENDING"
          response="$(jq -cn --arg ref "$FAKE_REFERENCE" '{success:true,status:"SUBMITTED",result:{data:{referenceNo:$ref,submitDate:"12/11/2026",submitTime:"12:00:00"}},messages:[],errors:[]}')"
          printf 'authorized-submit\n' >>"$FAKE_MUTATIONS"
        fi
      fi
    else
      status=403
      response='{"success":false,"status":"ERROR","result":null,"messages":[],"errors":[{"code":"err.password-validation.authorization.invalid"}]}'
    fi
    ;;
  'POST /api/v1/investment-choice/instructions/cancel')
    if [[ "$FAKE_LIVE" == yes && "$token" == fake-cancel-token ]]; then
      reference="$(jq -r '.referenceNo' <<<"$request_body")"
      if [[ "$reference" == "$FAKE_REFERENCE" ]]; then
        printf 'CANCEL\n' >>"$FAKE_CALLS"
        printf 'LIFECYCLE-CANCEL\n' >>"$FAKE_CALLS"
      fi
    fi
    if [[ "$token" == fake-cancel-token ]]; then
      cancel_payload="$(jq -c '.' <<<"$request_body")"
      issued_cancel_payload="$(cat "$FAKE_VALID_CANCEL_PAYLOAD")"
      reference="$(jq -r '.referenceNo' <<<"$request_body")"
      if [[ "$FAKE_LIVE_CANCEL_PENDING" == yes \
        && "$cancel_payload" == "$issued_cancel_payload" ]]; then
        printf 'EXISTING-CANCEL-ATTEMPT\n' >>"$FAKE_CALLS"
      fi
      if [[ "$cancel_payload" != "$issued_cancel_payload" ]]; then
        status=403
        response='{"success":false,"status":"ERROR","result":null,"messages":[],"errors":[{"code":"err.password-validation.authorization.invalid"}]}'
      elif [[ "$FAKE_CANCEL_MODE" == fail ]]; then
        status=500
        response='{"success":false,"status":"ERROR","result":null,"messages":[],"errors":[]}'
      elif [[ "$FAKE_LIVE_CANCEL_PENDING" == yes \
        && "$reference" == "$(jq -r '.[0].referenceNo // empty' "$FAKE_PENDING")" ]]; then
        if [[ "$FAKE_PENDING_CANCEL_MODE" == not-cancellable ]]; then
          status=422
          response='{"success":false,"status":"BUSINESS_REJECTED","result":null,"messages":[],"errors":[{"code":"err.investment-choice.cancel.not-cancellable"}]}'
        else
          response="$(jq -cn --arg ref "$reference" '{success:true,status:"SUCCESS",result:{data:{referenceNo:$ref}},messages:[],errors:[]}')"
          printf '[]' >"$FAKE_PENDING"
          printf 'authorized-existing-cancel\n' >>"$FAKE_MUTATIONS"
          printf 'EXISTING-CANCEL\n' >>"$FAKE_CALLS"
        fi
      elif [[ "$FAKE_LIVE" == yes && "$reference" == "$FAKE_REFERENCE" ]]; then
        response="$(jq -cn --arg ref "$FAKE_REFERENCE" '{success:true,status:"SUCCESS",result:{data:{referenceNo:$ref}},messages:[],errors:[]}')"
        if [[ "$FAKE_L4_MODE" != present ]]; then
          remaining_pending="$(jq -c --arg reference "$reference" \
            '[.[] | select(.referenceNo != $reference)]' "$FAKE_PENDING")"
          printf '%s' "$remaining_pending" >"$FAKE_PENDING"
        fi
        printf 'authorized-cancel\n' >>"$FAKE_MUTATIONS"
      else
        status=403
        response='{"success":false,"status":"ERROR","result":null,"messages":[],"errors":[{"code":"err.password-validation.authorization.invalid"}]}'
      fi
    else
      status=403
      response='{"success":false,"status":"ERROR","result":null,"messages":[],"errors":[{"code":"err.password-validation.authorization.invalid"}]}'
    fi
    ;;
  *)
    status=404
    response='{}'
    ;;
esac
printf '%s\n%s' "$response" "$status"
FAKE_CURL
cat >"$tmp/bin/jq" <<'FAKE_JQ'
#!/usr/bin/env bash
set -Eeuo pipefail
if [[ "${FAKE_INSIDE_CURL:-no}" != yes ]]; then
  previous_arg=''
  for arg in "$@"; do
    if [[ "$arg" == *LIVE-REF-1* || "$arg" == *OTHER-PENDING* \
      || ( -n "${FAKE_PENDING_REFERENCE:-}" && "$arg" == *"$FAKE_PENDING_REFERENCE"* ) \
      || ( -n "${FAKE_REFERENCE:-}" && "$arg" == *"$FAKE_REFERENCE"* ) ]]; then
      printf 'instruction-reference-argument-after=%s\n' "$previous_arg" >>"$FAKE_JQ_ARG_LEAKS"
    fi
    previous_arg="$arg"
  done
  for arg in "$@"; do
    if [[ "$arg" == */p3-reference.json ]]; then
      printf 'p3-reference-mode=%s\n' "$(stat -c '%a' "$arg")" >>"$FAKE_PRIVATE_AUDIT"
    elif [[ "$arg" == */pending-cancel-reference.json ]]; then
      printf 'pending-cancel-reference-mode=%s\n' "$(stat -c '%a' "$arg")" >>"$FAKE_PRIVATE_AUDIT"
    fi
  done
fi
exec "$REAL_JQ" "$@"
FAKE_JQ
chmod +x "$tmp/bin/curl"
chmod +x "$tmp/bin/jq"

run_fake_scenario() {
  local mode="$1" out="$2" initial_pending="${3:-[]}" expected_failure="${4:-no}" run_name scenario l2_mode l4_mode submit_mode cancel_mode reference pending_cancel_mode pending_cancel_result interval run_dir
  run_name="${5:-$mode}"
  scenario="${6:-NORMAL}"
  l2_mode="${7:-normal}"
  l4_mode="${8:-normal}"
  submit_mode="${9:-normal}"
  cancel_mode="${10:-normal}"
  reference="${11:-LIVE-REF-1}"
  pending_cancel_mode="${12:-no}"
  pending_cancel_result="${13:-normal}"
  interval="${14:-20}"
  run_dir="$tmp/$run_name"
  mkdir -p "$run_dir" "$run_dir/recovery"
  mkdir -p "$run_dir/private-tmp"
  chmod 700 "$run_dir/private-tmp"
  chmod 700 "$run_dir/recovery"
  : >"$run_dir/get-count"
  : >"$run_dir/cancel-issue-count"
  : >"$run_dir/valid-payload"
  : >"$run_dir/valid-cancel-payload"
  printf '%s' "$initial_pending" >"$run_dir/pending"
  : >"$run_dir/reuse"
  : >"$run_dir/mutations"
  : >"$run_dir/calls"
  : >"$run_dir/jq-arg-leaks"
  : >"$run_dir/private-audit"
  : >"$run_dir/diagnostic"
  if ! PATH="$tmp/bin:$PATH" \
    BASE_URL=http://fake.invalid \
    SESSION_COOKIE=generator-cookie-sentinel \
    CSRF_TOKEN=generator-csrf-sentinel \
    ACCOUNT_REF=generator-account-ref-sentinel \
    CIM_USE_PASSWORD=yes \
    CIM_PASSWORD=generator-password-sentinel \
    LOGIN_TEST_PASSWORD=unused-login-password-sentinel \
    CIM_RANDOM_SEED=2468 \
    CIM_LIVE_SUBMIT="$mode" \
    CIM_CONFIRM_LIVE_SUBMIT=I-UNDERSTAND-THIS-CHANGES-REAL-DATA \
    CIM_LIVE_CANCEL_PENDING="$pending_cancel_mode" \
    CIM_CONFIRM_LIVE_PENDING_CANCEL=I-UNDERSTAND-THIS-CANCELS-AN-EXISTING-INSTRUCTION \
    CIM_RECOVERY_DIR="$run_dir/recovery" \
    TMPDIR="$run_dir/private-tmp" \
    FAKE_REFERENCE="$reference" \
    FAKE_PENDING_REFERENCE=PRE-EXISTING \
    FAKE_JQ_ARG_LEAKS="$run_dir/jq-arg-leaks" \
    FAKE_PRIVATE_AUDIT="$run_dir/private-audit" \
    REAL_JQ="$real_jq" \
    FAKE_LIVE="$mode" \
    FAKE_SCENARIO="$scenario" \
    FAKE_L2_MODE="$l2_mode" \
    FAKE_L4_MODE="$l4_mode" \
    FAKE_SUBMIT_MODE="$submit_mode" \
    FAKE_CANCEL_MODE="$cancel_mode" \
    FAKE_INTERVAL="$interval" \
    FAKE_LIVE_CANCEL_PENDING="$pending_cancel_mode" \
    FAKE_PENDING_CANCEL_MODE="$pending_cancel_result" \
    FAKE_CANCEL_ISSUE_COUNT="$run_dir/cancel-issue-count" \
    FAKE_GET_COUNT="$run_dir/get-count" \
    FAKE_CALLS="$run_dir/calls" \
    FAKE_VALID_PAYLOAD="$run_dir/valid-payload" \
    FAKE_VALID_CANCEL_PAYLOAD="$run_dir/valid-cancel-payload" \
    FAKE_PENDING="$run_dir/pending" \
    FAKE_REUSE="$run_dir/reuse" \
    FAKE_MUTATIONS="$run_dir/mutations" \
    FAKE_DIAGNOSTIC="$run_dir/diagnostic" \
    bash "$SCENARIO" >"$out" 2>&1; then
    [[ ! -s "$run_dir/diagnostic" ]] || cat "$run_dir/diagnostic" >&2
    if [[ "$expected_failure" == yes ]]; then
      return 0
    fi
    sed -E \
      -e 's/generator-password-sentinel/[redacted]/g' \
      -e 's/unused-login-password-sentinel/[redacted]/g' \
      -e 's/generator-cookie-sentinel/[redacted]/g' \
      -e 's/generator-csrf-sentinel/[redacted]/g' \
      -e 's/generator-account-ref-sentinel/[redacted]/g' \
      -e 's/fake-cim-token/[redacted]/g' \
      -e 's/fake-cancel-token/[redacted]/g' "$out" >&2
    return 1
  fi
  if [[ "$expected_failure" == yes ]]; then
    printf 'FAIL scenario unexpectedly succeeded\n'
    return 1
  fi
}

run_fake_scenario no "$tmp/no-live.log"
grep -Fq 'PASS P0-issue status=200' "$tmp/no-live.log" || {
  printf 'FAIL valid snapshot-driven issuance did not pass\n'
  exit 1
}
grep -Fq 'PASS P1-issue status=422 code=err.investment-choice.percentage.interval-invalid' "$tmp/no-live.log" || {
  printf 'FAIL P1 did not assert its M6 issuance-time rejection\n'
  exit 1
}
grep -Fq 'PASS P2-issue status=422 code=err.investment-choice.cancel.ref-invalid' "$tmp/no-live.log" || {
  printf 'FAIL P2 did not assert its M6 issuance-time rejection\n'
  exit 1
}
grep -Fq 'Password verifications this run (snapshot rejections excluded): 1' "$tmp/no-live.log" || {
  printf 'FAIL rejected prechecks were counted as password verifications\n'
  exit 1
}
grep -Fq 'P1-submit' "$tmp/no-live.log" && {
  printf 'FAIL P1 attempted a token-dependent submit after issuance rejection\n'
  exit 1
}
[[ ! -s "$tmp/no/mutations" ]] || {
  printf 'FAIL authorized mutation occurred without live opt-in\n'
  exit 1
}

run_fake_scenario yes "$tmp/live.log"
grep -Fq 'payload_reuse=PASS' "$tmp/yes/reuse" || {
  printf 'FAIL issuance and live submission did not reuse the exact operationPayload\n'
  exit 1
}
grep -Fq 'Password verifications this run (snapshot rejections excluded): 2' "$tmp/live.log" || {
  printf 'FAIL live path password-verification count is inaccurate\n'
  exit 1
}
[[ "$(wc -l <"$tmp/yes/mutations")" -eq 2 ]] || {
  printf 'FAIL explicit live opt-in did not exercise exactly one submit and one cancel\n'
  exit 1
}
expected_live_calls=$'LIFECYCLE-GET-1\nLIFECYCLE-CHANGE-ISSUE\nLIFECYCLE-SUBMIT\nLIFECYCLE-GET-2\nLIFECYCLE-CANCEL-ISSUE\nLIFECYCLE-CANCEL\nLIFECYCLE-GET-3'
actual_live_calls="$(grep '^LIFECYCLE-' "$tmp/yes/calls" || true)"
[[ "$actual_live_calls" == "$expected_live_calls" ]] || {
  printf 'FAIL live lifecycle call order differed from submit, refreshed GET, cancel issuance, cancel, final GET\n'
  exit 1
}
grep -Fq 'reference_state=not-listed' "$tmp/live.log" || {
  printf 'FAIL final GET did not report the observed pending-list state\n'
  exit 1
}
grep -Fq 'LIVE-REF-1' "$tmp/yes/valid-cancel-payload" || {
  printf 'FAIL cancel issuance was not bound to the reference created by this run\n'
  exit 1
}
jq -e '
  (.allocations | length) == 3
  and ([.allocations[].fundCode] | unique | length) == 3
  and ([.allocations[].percentage] | add) == 100
  and all(.allocations[]; .percentage > 0 and .percentage % 20 == 0)
' "$tmp/yes/valid-payload" >/dev/null || {
  printf 'FAIL live payload did not use three distinct eligible funds with interval-valid percentages totaling 100\n'
  exit 1
}
grep -Fq 'LIVE-REF-1' "$tmp/live.log" && {
  printf 'FAIL submitted instruction reference appeared in terminal output\n'
  exit 1
}
[[ ! -s "$tmp/yes/jq-arg-leaks" ]] || {
  printf 'FAIL submitted instruction reference appeared in jq process arguments (%s occurrences)\n' \
    "$(wc -l <"$tmp/yes/jq-arg-leaks")"
  cat "$tmp/yes/jq-arg-leaks"
  exit 1
}

run_fake_scenario yes "$tmp/l2-missing.log" '[]' yes l2-missing NORMAL missing
! grep -Fq 'ISSUE-CANCEL_INVESTMENT_CHOICE' <(sed -n '/^SUBMIT$/,$p' "$tmp/l2-missing/calls") || {
  printf 'FAIL cancellation issuance ran when GET #2 omitted the submitted reference\n'
  exit 1
}
[[ "$(grep -c '^SUBMIT$' "$tmp/l2-missing/calls")" -eq 1 ]] || {
  printf 'FAIL submission was retried after GET #2 rejected cancellation eligibility\n'
  exit 1
}
recovery_file="$(find "$tmp/l2-missing/recovery" -type f -print -quit)"
[[ -n "$recovery_file" && "$(stat -c '%a' "$recovery_file")" == 600 ]] || {
  printf 'FAIL incomplete GET #2 cleanup did not preserve a mode-0600 recovery record\n'
  exit 1
}
jq -e '.status == "cleanup-incomplete" and .submitOutcome == "confirmed" and .referenceNo == "LIVE-REF-1"' \
  "$recovery_file" >/dev/null || {
  printf 'FAIL private recovery record omitted the outstanding submitted reference\n'
  exit 1
}
[[ "$(stat -c '%a %u' "$tmp/l2-missing/recovery")" == "700 $(id -u)" \
  && "$(stat -c '%u' "$recovery_file")" == "$(id -u)" ]] || {
  printf 'FAIL recovery directory or record ownership/mode was unsafe\n'
  exit 1
}
grep -Fq 'LIVE-REF-1' "$tmp/l2-missing.log" && {
  printf 'FAIL outstanding reference appeared in terminal output\n'
  exit 1
}

newline_reference=$'line-one\nsubmit_outcome=forged'
run_fake_scenario yes "$tmp/newline-recovery.log" '[]' yes newline-recovery NORMAL missing normal normal normal "$newline_reference"
newline_recovery="$(find "$tmp/newline-recovery/recovery" -type f -print -quit)"
[[ -n "$newline_recovery" ]] || {
  printf 'FAIL newline-bearing reference did not preserve recovery record\n'
  exit 1
}
jq -e --arg expected "$newline_reference" '
  .status == "cleanup-incomplete"
  and .submitOutcome == "confirmed"
  and .referenceNo == $expected
' "$newline_recovery" >/dev/null || {
  printf 'FAIL recovery record did not preserve an arbitrary reference as unambiguous JSON\n'
  exit 1
}
[[ ! -s "$tmp/newline-recovery/jq-arg-leaks" ]] || {
  printf 'FAIL newline-bearing reference appeared in jq process arguments\n'
  exit 1
}

run_fake_scenario yes "$tmp/other-pending.log" '[]' no other-pending NORMAL other-first
grep -Fq 'PASS L3-issue status=200' "$tmp/other-pending.log" || {
  printf 'FAIL cancel issuance did not accept the submitted reference when another pending reference came first\n'
  exit 1
}
grep -Fq 'LIVE-REF-1' "$tmp/other-pending/valid-cancel-payload" || {
  printf 'FAIL another pending reference was substituted for the run-created reference\n'
  exit 1
}

run_fake_scenario yes "$tmp/submit-failed.log" '[]' yes submit-failed NORMAL normal normal fail
[[ "$(grep -c '^SUBMIT$' "$tmp/submit-failed/calls")" -eq 1 ]] || {
  printf 'FAIL failed submit was automatically retried\n'
  exit 1
}
[[ "$(find "$tmp/submit-failed/recovery" -type f | wc -l)" -eq 1 ]] || {
  printf 'FAIL ambiguous submit outcome did not preserve recovery status\n'
  exit 1
}
jq -e '.status == "cleanup-incomplete" and .submitOutcome == "unknown" and (has("referenceNo") | not)' \
  "$tmp/submit-failed/recovery/"* >/dev/null || {
  printf 'FAIL ambiguous submit recovery record did not identify unknown outcome\n'
  exit 1
}

run_fake_scenario yes "$tmp/submit-rejected.log" \
  '[{"referenceNo":"PRE-EXISTING","submitDate":"12/11/2026","submitTime":"11:00:00","allocations":[]}]' \
  yes submit-rejected NORMAL normal normal reject
grep -Fq 'FAIL L1-submit expected=200/SUBMITTED got=503 code=err.investment-choice.submission.unavailable' \
  "$tmp/submit-rejected.log" || {
  printf 'FAIL Progress rejection did not report its public error code\n'
  exit 1
}
[[ "$(grep -c '^SUBMIT$' "$tmp/submit-rejected/calls")" -eq 1 ]] || {
  printf 'FAIL Progress rejection caused an automatic submit retry\n'
  exit 1
}
if grep -Eq '^LIFECYCLE-CANCEL-(ISSUE|CANCEL)$' \
  <(sed -n '/^LIFECYCLE-SUBMIT$/,$p' "$tmp/submit-rejected/calls"); then
  printf 'FAIL cancellation proceeded after Progress rejected submission\n'
  exit 1
fi
[[ ! -s "$tmp/submit-rejected/mutations" ]] || {
  printf 'FAIL rejected Progress submission caused a fake mutation\n'
  exit 1
}
rejected_recovery="$(find "$tmp/submit-rejected/recovery" -type f -print -quit)"
[[ -n "$rejected_recovery" && "$(stat -c '%a' "$rejected_recovery")" == 600 ]] || {
  printf 'FAIL rejected submission did not preserve protected recovery state\n'
  exit 1
}
grep -Fq 'PRE-EXISTING' "$tmp/submit-rejected.log" && {
  printf 'FAIL existing pending reference appeared in rejection output\n'
  exit 1
}

run_fake_scenario yes "$tmp/cancel-failed.log" '[]' yes cancel-failed NORMAL normal normal normal fail
[[ "$(grep -c '^CANCEL$' "$tmp/cancel-failed/calls")" -eq 1 ]] || {
  printf 'FAIL failed cancel was automatically retried\n'
  exit 1
}
[[ "$(grep -c '^SUBMIT$' "$tmp/cancel-failed/calls")" -eq 1 ]] || {
  printf 'FAIL cancel failure caused an automatic submit retry\n'
  exit 1
}
jq -e '.referenceNo == "LIVE-REF-1"' "$tmp/cancel-failed/recovery/"* >/dev/null || {
  printf 'FAIL failed cancellation recovery record omitted the outstanding reference\n'
  exit 1
}

run_fake_scenario yes "$tmp/final-get-invalid.log" '[]' yes final-get-invalid NORMAL normal malformed
[[ "$(grep -c '^CANCEL$' "$tmp/final-get-invalid/calls")" -eq 1 ]] || {
  printf 'FAIL final GET verification failure retried cancellation\n'
  exit 1
}
[[ "$(find "$tmp/final-get-invalid/recovery" -type f | wc -l)" -eq 1 ]] || {
  printf 'FAIL invalid final GET did not mark cleanup incomplete\n'
  exit 1
}

run_fake_scenario yes "$tmp/final-get-still-pending.log" '[]' yes final-get-still-pending NORMAL normal present
grep -Fq 'submitted reference not listed as pending after successful cancel response' "$tmp/final-get-still-pending.log" || {
  printf 'FAIL final GET did not flag the run-created reference still pending\n'
  exit 1
}
[[ "$(find "$tmp/final-get-still-pending/recovery" -type f | wc -l)" -eq 1 ]] || {
  printf 'FAIL still-pending final state did not preserve cleanup recovery\n'
  exit 1
}

run_fake_scenario yes "$tmp/pending-allowed.log" \
  '[{"referenceNo":"PRE-EXISTING","submitDate":"12/11/2026","submitTime":"11:00:00","allocations":[]},{"referenceNo":"SECOND-PENDING","submitDate":"11/11/2026","submitTime":"10:00:00","allocations":[]}]' \
  no pending-allowed
grep -Fq 'PASS L1-submit status=200' "$tmp/pending-allowed.log" || {
  printf 'FAIL existing pending instructions blocked the authorized live submit lifecycle\n'
  exit 1
}
grep -Fq 'LIVE-REF-1' "$tmp/pending-allowed/valid-cancel-payload" || {
  printf 'FAIL run-created reference was not bound for cancellation with existing pending instructions\n'
  exit 1
}
[[ "$(wc -l <"$tmp/pending-allowed/mutations")" -eq 2 ]] || {
  printf 'FAIL pending-instruction lifecycle did not make exactly one submit and one run-created cancel\n'
  exit 1
}
pending_lifecycle_calls=$'LIFECYCLE-GET-1\nLIFECYCLE-CHANGE-ISSUE\nLIFECYCLE-SUBMIT\nLIFECYCLE-GET-2\nLIFECYCLE-CANCEL-ISSUE\nLIFECYCLE-CANCEL\nLIFECYCLE-GET-3'
actual_pending_lifecycle_calls="$(grep '^LIFECYCLE-' "$tmp/pending-allowed/calls" | grep -v '^LIFECYCLE-P3-' || true)"
[[ "$actual_pending_lifecycle_calls" == "$pending_lifecycle_calls" ]] || {
  printf 'FAIL pending-instruction lifecycle call order differed from the seven-step contract\n'
  printf 'expected:\n%s\nactual:\n%s\n' "$pending_lifecycle_calls" "$actual_pending_lifecycle_calls"
  exit 1
}
jq -e '([.[].referenceNo] | sort) == ["PRE-EXISTING","SECOND-PENDING"]' \
  "$tmp/pending-allowed/pending" >/dev/null || {
  printf 'FAIL live lifecycle canceled or replaced an unrelated pending instruction\n'
  exit 1
}
[[ "$(grep -c '^SUBMIT$' "$tmp/pending-allowed/calls")" -eq 1 \
  && "$(grep -c '^CANCEL$' "$tmp/pending-allowed/calls")" -eq 1 ]] || {
  printf 'FAIL pending-instruction lifecycle retried a mutation\n'
  exit 1
}

run_fake_scenario no "$tmp/p3-pending.log" \
  '[{"referenceNo":"PRE-EXISTING","submitDate":"12/11/2026","submitTime":"11:00:00","allocations":[]}]' no existing-pending
grep -Fq 'PASS P0-issue status=200' "$tmp/p3-pending.log" || {
  printf 'FAIL existing pending instructions blocked valid change issuance\n'
  exit 1
}
grep -Fq 'PASS P3-issue status=200' "$tmp/p3-pending.log" || {
  printf 'FAIL P3 did not use an existing pending reference for its valid issuance\n'
  exit 1
}
grep -Fq 'PASS P3-body-binding status=403' "$tmp/p3-pending.log" || {
  printf 'FAIL P3 did not reject a non-matching operationPayload\n'
  exit 1
}
grep -Fq 'Password verifications this run (snapshot rejections excluded): 2' "$tmp/p3-pending.log" || {
  printf 'FAIL valid P0 and P3 issuances were not counted as password verifications\n'
  exit 1
}
[[ ! -s "$tmp/existing-pending/mutations" ]] || {
  printf 'FAIL non-live P3 altered existing member data\n'
  exit 1
}
[[ ! -s "$tmp/existing-pending/jq-arg-leaks" ]] || {
  printf 'FAIL API-returned pending reference appeared in jq process arguments\n'
  cat "$tmp/existing-pending/jq-arg-leaks"
  exit 1
}
grep -Fxq 'p3-reference-mode=600' "$tmp/existing-pending/private-audit" || {
  printf 'FAIL P3 reference was not supplied through a mode-0600 private file\n'
  exit 1
}
if grep -Fq 'PRE-EXISTING' "$tmp/p3-pending.log"; then
  printf 'FAIL API-returned pending reference appeared in terminal output/evidence\n'
  exit 1
fi
[[ -z "$(find "$tmp/existing-pending/private-tmp" -mindepth 1 -print -quit)" ]] || {
  printf 'FAIL P3 private reference state was not cleaned up\n'
  exit 1
}

run_fake_scenario no "$tmp/existing-cancel.log" \
  '[{"referenceNo":"PRE-EXISTING","submitDate":"12/11/2026","submitTime":"11:00:00","allocations":[]},{"referenceNo":"SECOND-PENDING","submitDate":"11/11/2026","submitTime":"10:00:00","allocations":[]}]' \
  no existing-cancel NORMAL normal normal normal normal LIVE-REF-1 yes normal
expected_existing_calls=$'EXISTING-GET-1\nEXISTING-PV\nEXISTING-CANCEL-ATTEMPT\nEXISTING-CANCEL\nEXISTING-GET-2'
actual_existing_calls="$(grep '^EXISTING-' "$tmp/existing-cancel/calls" || true)"
[[ "$actual_existing_calls" == "$expected_existing_calls" ]] || {
  printf 'FAIL existing-pending cancellation did not follow GET, fresh token, single cancel, GET order\n'
  exit 1
}
[[ "$(wc -l <"$tmp/existing-cancel/mutations")" -eq 1 ]] || {
  printf 'FAIL existing-pending mode did not perform exactly one cancellation\n'
  exit 1
}
jq -e '.referenceNo == "PRE-EXISTING"' "$tmp/existing-cancel/valid-cancel-payload" >/dev/null || {
  printf 'FAIL existing-pending mode did not bind cancellation to the first GET reference\n'
  exit 1
}
grep -Fxq 'pending-cancel-reference-mode=600' "$tmp/existing-cancel/private-audit" || {
  printf 'FAIL existing-pending reference was not supplied from a mode-0600 private file\n'
  exit 1
}
if ! grep -Fq 'PASS E2-cancel status=200' "$tmp/existing-cancel.log" \
  || ! grep -Fq 'PASS E3-get status=200' "$tmp/existing-cancel.log"; then
  printf 'FAIL existing-pending cancellation and refreshed GET were not verified\n'
  exit 1
fi
grep -Fq 'SKIP P3 reason=dedicated existing-instruction cancellation mode uses its own fresh token and one cancel attempt' \
  "$tmp/existing-cancel.log" || {
  printf 'FAIL standalone P3 flow was not suppressed for the one-shot existing-cancellation lifecycle\n'
  exit 1
}
grep -Fq 'Password verifications this run (snapshot rejections excluded): 1' "$tmp/existing-cancel.log" || {
  printf 'FAIL existing cancellation mode issued an unexpected additional password verification\n'
  exit 1
}
[[ "$(jq -r 'length' "$tmp/existing-cancel/pending")" == 0 ]] || {
  printf 'FAIL fake final GET retained the canceled reference\n'
  exit 1
}
grep -Fq 'PRE-EXISTING' "$tmp/existing-cancel.log" && {
  printf 'FAIL existing pending reference appeared in terminal output\n'
  exit 1
}
[[ ! -s "$tmp/existing-cancel/jq-arg-leaks" ]] || {
  printf 'FAIL existing pending reference appeared in jq arguments\n'
  exit 1
}
[[ -z "$(find "$tmp/existing-cancel/private-tmp" -mindepth 1 -print -quit)" ]] || {
  printf 'FAIL existing-pending private reference state was not cleaned up\n'
  exit 1
}

run_fake_scenario no "$tmp/existing-not-cancellable.log" \
  '[{"referenceNo":"PRE-EXISTING","submitDate":"12/11/2026","submitTime":"11:00:00","allocations":[]},{"referenceNo":"SECOND-PENDING","submitDate":"11/11/2026","submitTime":"10:00:00","allocations":[]}]' \
  yes existing-not-cancellable NORMAL normal normal normal normal LIVE-REF-1 yes not-cancellable
[[ "$(grep -c '^EXISTING-CANCEL-ATTEMPT$' "$tmp/existing-not-cancellable/calls")" -eq 1 ]] || {
  printf 'FAIL non-cancellable instruction did not receive exactly one cancellation attempt\n'
  exit 1
}
if grep -q '^EXISTING-CANCEL$' "$tmp/existing-not-cancellable/calls"; then
  printf 'FAIL a second pending reference was substituted after the first was not cancellable\n'
  exit 1
fi
not_cancellable_recovery="$(find "$tmp/existing-not-cancellable/recovery" -type f -print -quit)"
[[ -n "$not_cancellable_recovery" && "$(stat -c '%a' "$not_cancellable_recovery")" == 600 ]] || {
  printf 'FAIL rejected existing cancellation did not preserve protected recovery state\n'
  exit 1
}
[[ "$(stat -c '%a %u' "$tmp/existing-not-cancellable/recovery")" == "700 $(id -u)" \
  && "$(stat -c '%u' "$not_cancellable_recovery")" == "$(id -u)" ]] || {
  printf 'FAIL existing-cancellation recovery ownership or directory mode was unsafe\n'
  exit 1
}
jq -e '.operation == "cancel-existing-pending" and .cancelAttempted == true and .referenceNo == "PRE-EXISTING"' \
  "$not_cancellable_recovery" >/dev/null || {
  printf 'FAIL rejected existing cancellation recovery record was incomplete\n'
  exit 1
}
grep -Fq 'PRE-EXISTING' "$tmp/existing-not-cancellable.log" && {
  printf 'FAIL rejected existing reference appeared in terminal output\n'
  exit 1
}

run_fake_scenario no "$tmp/existing-cancel-unknown.log" \
  '[{"referenceNo":"PRE-EXISTING","submitDate":"12/11/2026","submitTime":"11:00:00","allocations":[]}]' \
  yes existing-cancel-unknown NORMAL normal normal normal fail LIVE-REF-1 yes normal
[[ "$(grep -c '^EXISTING-CANCEL-ATTEMPT$' "$tmp/existing-cancel-unknown/calls")" -eq 1 ]] || {
  printf 'FAIL ambiguous existing cancellation was retried or omitted\n'
  exit 1
}
unknown_cancel_recovery="$(find "$tmp/existing-cancel-unknown/recovery" -type f -print -quit)"
[[ -n "$unknown_cancel_recovery" && "$(stat -c '%a' "$unknown_cancel_recovery")" == 600 ]] || {
  printf 'FAIL ambiguous existing cancellation did not preserve protected recovery state\n'
  exit 1
}
jq -e '.status == "cleanup-incomplete" and .operation == "cancel-existing-pending" and .cancelAttempted == true and .cancelOutcome == "unknown" and .referenceNo == "PRE-EXISTING"' \
  "$unknown_cancel_recovery" >/dev/null || {
  printf 'FAIL ambiguous existing cancellation recovery state was incorrect\n'
  exit 1
}

run_fake_scenario yes "$tmp/live-two-funds.log" '[]' yes live-two-funds NORMAL normal normal normal normal LIVE-REF-1 no normal 50
[[ ! -s "$tmp/live-two-funds/mutations" ]] || {
  printf 'FAIL live submit proceeded without three interval-compliant fund allocations\n'
  exit 1
}

run_fake_scenario no "$tmp/restricted.log" '[]' no restricted FUNCTION_UNAVAILABLE
grep -Fq 'PASS S1 status=200' "$tmp/restricted.log" || {
  printf 'FAIL restricted GET response was not accepted as a valid S1 contract\n'
  exit 1
}
grep -Fq 'SKIP P0 reason=snapshot scenario=FUNCTION_UNAVAILABLE' "$tmp/restricted.log" || {
  printf 'FAIL restricted scenario did not produce an explicit P0 SKIP\n'
  exit 1
}

mkdir -p "$tmp/fund-unavailable"
: >"$tmp/fund-unavailable/get-count"
: >"$tmp/fund-unavailable/valid-payload"
: >"$tmp/fund-unavailable/valid-cancel-payload"
: >"$tmp/fund-unavailable/reuse"
: >"$tmp/fund-unavailable/mutations"
: >"$tmp/fund-unavailable/diagnostic"
printf '[]' >"$tmp/fund-unavailable/pending"
fund_request='{"password":"fake-password-sentinel","purpose":"CHANGE_INVESTMENT_CHOICE","operationPayload":{"switchSources":["SRC-A"],"allocations":[{"fundCode":"FUND-NOT-IN-GET","percentage":40},{"fundCode":"FUND-B","percentage":60}]}}'
fund_response="$(
  PATH="$tmp/bin:$PATH" \
  FAKE_GET_COUNT="$tmp/fund-unavailable/get-count" \
  FAKE_VALID_PAYLOAD="$tmp/fund-unavailable/valid-payload" \
  FAKE_VALID_CANCEL_PAYLOAD="$tmp/fund-unavailable/valid-cancel-payload" \
  FAKE_PENDING="$tmp/fund-unavailable/pending" \
  FAKE_REUSE="$tmp/fund-unavailable/reuse" \
  FAKE_MUTATIONS="$tmp/fund-unavailable/mutations" \
  FAKE_DIAGNOSTIC="$tmp/fund-unavailable/diagnostic" \
  FAKE_JQ_ARG_LEAKS="$tmp/fund-unavailable/jq-arg-leaks" \
  FAKE_REFERENCE=LIVE-REF-1 \
  REAL_JQ="$real_jq" \
  FAKE_LIVE=no \
  FAKE_SCENARIO=NORMAL \
    curl -X POST --data-binary "$fund_request" http://fake.invalid/api/v1/password-validation
)"
fund_status="${fund_response##*$'\n'}"
fund_body="${fund_response%$'\n'*}"
if [[ "$fund_status" != 422 ]] || ! jq -e '
    .errors[0].code == "err.investment-choice.fund.unavailable"
    and .errors[0].targets == ["allocations[0].fundCode"]
  ' <<<"$fund_body" >/dev/null; then
  printf 'FAIL fake issuance validator did not reject a fund absent from the GET fixture with HTTP 422\n'
  exit 1
fi
[[ ! -s "$tmp/fund-unavailable/mutations" ]] || {
  printf 'FAIL unavailable-fund issuance test caused a mutation\n'
  exit 1
}

mkdir -p "$tmp/missing-opt-in"
: >"$tmp/missing-opt-in/get-count"
if PATH="$tmp/bin:$PATH" \
  CIM_LIVE_SUBMIT=yes \
  CIM_USE_PASSWORD=yes \
  CIM_CONFIRM_LIVE_SUBMIT='' \
  FAKE_GET_COUNT="$tmp/missing-opt-in/get-count" \
  bash "$SCENARIO" >"$tmp/missing-opt-in.log" 2>&1; then
  printf 'FAIL live submission ran without the explicit confirmation value\n'
  exit 1
else
  [[ "$?" -eq 2 ]] || {
    printf 'FAIL missing live confirmation returned an unexpected status\n'
    exit 1
  }
fi
[[ ! -s "$tmp/missing-opt-in/get-count" ]] || {
  printf 'FAIL a network request occurred before live confirmation\n'
  exit 1
}

mkdir -p "$tmp/pending-cancel-missing-opt-in"
: >"$tmp/pending-cancel-missing-opt-in/get-count"
if PATH="$tmp/bin:$PATH" \
  CIM_USE_PASSWORD=yes \
  CIM_LIVE_CANCEL_PENDING=yes \
  CIM_CONFIRM_LIVE_PENDING_CANCEL='' \
  FAKE_GET_COUNT="$tmp/pending-cancel-missing-opt-in/get-count" \
  bash "$SCENARIO" >"$tmp/pending-cancel-missing-opt-in.log" 2>&1; then
  printf 'FAIL existing-pending cancellation ran without its dedicated confirmation\n'
  exit 1
else
  [[ "$?" -eq 2 ]] || {
    printf 'FAIL missing existing-cancellation confirmation returned an unexpected status\n'
    exit 1
  }
fi
[[ ! -s "$tmp/pending-cancel-missing-opt-in/get-count" ]] || {
  printf 'FAIL a network request occurred before existing-cancellation confirmation\n'
  exit 1
}

mkdir -p "$tmp/conflicting-live-modes"
: >"$tmp/conflicting-live-modes/get-count"
if PATH="$tmp/bin:$PATH" \
  CIM_USE_PASSWORD=yes \
  CIM_LIVE_SUBMIT=yes \
  CIM_CONFIRM_LIVE_SUBMIT=I-UNDERSTAND-THIS-CHANGES-REAL-DATA \
  CIM_LIVE_CANCEL_PENDING=yes \
  CIM_CONFIRM_LIVE_PENDING_CANCEL=I-UNDERSTAND-THIS-CANCELS-AN-EXISTING-INSTRUCTION \
  FAKE_GET_COUNT="$tmp/conflicting-live-modes/get-count" \
  bash "$SCENARIO" >"$tmp/conflicting-live-modes.log" 2>&1; then
  printf 'FAIL mutually exclusive live mutation modes were accepted together\n'
  exit 1
else
  [[ "$?" -eq 2 ]] || {
    printf 'FAIL conflicting live modes returned an unexpected status\n'
    exit 1
  }
fi
[[ ! -s "$tmp/conflicting-live-modes/get-count" ]] || {
  printf 'FAIL a network request occurred with conflicting live modes\n'
  exit 1
}

mkdir -p "$tmp/insecure-recovery"
chmod 755 "$tmp/insecure-recovery"
: >"$tmp/insecure-recovery/get-count"
if PATH="$tmp/bin:$PATH" \
  BASE_URL=http://fake.invalid \
  CIM_USE_PASSWORD=yes \
  CIM_LIVE_SUBMIT=yes \
  CIM_CONFIRM_LIVE_SUBMIT=I-UNDERSTAND-THIS-CHANGES-REAL-DATA \
  CIM_RECOVERY_DIR="$tmp/insecure-recovery" \
  FAKE_GET_COUNT="$tmp/insecure-recovery/get-count" \
  bash "$SCENARIO" >"$tmp/insecure-recovery.log" 2>&1; then
  printf 'FAIL live submission accepted an insecure recovery directory\n'
  exit 1
else
  [[ "$?" -eq 2 ]] || {
    printf 'FAIL insecure recovery directory returned an unexpected status\n'
    exit 1
  }
fi
[[ ! -s "$tmp/insecure-recovery/get-count" ]] || {
  printf 'FAIL network request occurred before recovery directory validation\n'
  exit 1
}

for secret in \
  generator-password-sentinel \
  unused-login-password-sentinel \
  generator-cookie-sentinel \
  generator-csrf-sentinel \
  generator-account-ref-sentinel \
  fake-cim-token \
  fake-cancel-token \
  LIVE-REF-1 \
  OTHER-PENDING \
  PRE-EXISTING \
  SECOND-PENDING; do
  if grep -Fq -- "$secret" "$tmp"/*.log; then
    printf 'FAIL secret appeared in scenario output/evidence\n'
    exit 1
  fi
done

for recovery_file in "$tmp"/*/recovery/*; do
  [[ -f "$recovery_file" ]] || continue
  for secret in \
    generator-password-sentinel \
    generator-cookie-sentinel \
    generator-csrf-sentinel \
    fake-cim-token \
    fake-cancel-token; do
    if grep -Fq -- "$secret" "$recovery_file"; then
      printf 'FAIL authentication secret appeared in private recovery record\n'
      exit 1
    fi
  done
done

printf 'PASS CIM M6 issuance, exact payload reuse, live gates, and output redaction\n'
