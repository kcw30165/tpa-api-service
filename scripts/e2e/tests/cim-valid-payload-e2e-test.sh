#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCENARIO="$ROOT/scripts/e2e/investment-choice-scenarios.sh"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT
mkdir -p "$tmp/bin"

cat >"$tmp/bin/curl" <<'FAKE_CURL'
#!/usr/bin/env bash
set -Eeuo pipefail

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
  request_body=''
fi
token=''
if [[ "$headers" =~ X-Password-Validation-Token:\ ([^$'\r\n']+) ]]; then
  token="${BASH_REMATCH[1]}"
fi

status=200
response=''
case "$method $path" in
  'GET /api/v1/investment-choice')
    count="$(cat "$FAKE_GET_COUNT")"
    count=$((count + 1))
    printf '%s' "$count" >"$FAKE_GET_COUNT"
    if ((count == 2)); then
      status=401
      response='{"success":false,"status":"ERROR","result":null,"messages":[],"errors":[{"code":"err.security.authentication.required"}]}'
    elif [[ "$FAKE_SCENARIO" != NORMAL ]]; then
      response="$(jq -cn --arg scenario "$FAKE_SCENARIO" '{
        success:true,status:"SUCCESS",result:{data:{
          accountNumber:"TEST-ACCOUNT",scenario:$scenario,history:[]
        }},messages:[],errors:[]
      }')"
    else
      pending='[]'
      [[ ! -s "$FAKE_PENDING" ]] || pending="$(cat "$FAKE_PENDING")"
      response="$(jq -cn --argjson pending "$pending" '{
        success:true,status:"SUCCESS",result:{data:{
          accountNumber:"TEST-ACCOUNT",scenario:"NORMAL",intervalPercentage:20,
          switchSources:[{switchSourceCode:"SRC-A",switchSource:"Display A",sequence:1,available:true}],
          funds:[
            {fundCode:"FUND-A",fundName:"Fund A",sequence:1,riskLevel:"1"},
            {fundCode:"FUND-B",fundName:"Fund B",sequence:2,riskLevel:"2"},
            {fundCode:"FUND-C",fundName:"Fund C",sequence:3,riskLevel:"3"}
          ],
          history:[],pendingInstructions:$pending
        }},messages:[],errors:[]
      }')"
    fi
    ;;
  'POST /api/v1/password-validation')
    purpose="$(jq -r '.purpose' <<<"$request_body")"
    operation="$(jq -c '.operationPayload' <<<"$request_body")"
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
      interval=20
      invalid="$(jq -r --argjson interval "$interval" '[.allocations[].percentage | select(. % $interval != 0)] | length' <<<"$operation")"
      if ((invalid > 0)); then
        status=422
        response='{"success":false,"status":"VALIDATION_FAILED","result":null,"messages":[],"errors":[{"code":"err.investment-choice.percentage.interval-invalid","targets":["allocations[0].percentage"]}]}'
      else
        jq -c '.' <<<"$operation" >"$FAKE_VALID_PAYLOAD"
        response='{"success":true,"status":"SUCCESS","result":{"data":{"token":"fake-cim-token"}},"messages":[],"errors":[]}'
      fi
    else
      reference="$(jq -r '.referenceNo // empty' <<<"$operation")"
      pending=''
      [[ ! -s "$FAKE_PENDING" ]] || pending="$(jq -r '.[0].referenceNo // empty' "$FAKE_PENDING")"
      if [[ -n "$reference" && "$reference" == "$pending" ]]; then
        jq -c '.' <<<"$operation" >"$FAKE_VALID_CANCEL_PAYLOAD"
        response='{"success":true,"status":"SUCCESS","result":{"data":{"token":"fake-cancel-token"}},"messages":[],"errors":[]}'
      else
        status=422
        response='{"success":false,"status":"VALIDATION_FAILED","result":null,"messages":[],"errors":[{"code":"err.investment-choice.cancel.ref-invalid","targets":["referenceNo"]}]}'
      fi
    fi
    ;;
  'POST /api/v1/investment-choice/instructions')
    if [[ "$token" == fake-cim-token && "$FAKE_LIVE" == yes ]]; then
      submitted="$(jq -c '.' <<<"$request_body")"
      issued="$(cat "$FAKE_VALID_PAYLOAD")"
      if [[ "$submitted" != "$issued" ]]; then
        printf 'payload_reuse=FAIL\n' >"$FAKE_REUSE"
        status=500
        response='{}'
      else
        printf 'payload_reuse=PASS\n' >"$FAKE_REUSE"
        printf '[{"referenceNo":"LIVE-REF-1","submitDate":"12/11/2026","submitTime":"12:00:00","allocations":[]}]' >"$FAKE_PENDING"
        response='{"success":true,"status":"SUBMITTED","result":{"data":{"referenceNo":"LIVE-REF-1","submitDate":"12/11/2026","submitTime":"12:00:00"}},"messages":[],"errors":[]}'
        printf 'authorized-submit\n' >>"$FAKE_MUTATIONS"
      fi
    else
      status=403
      response='{"success":false,"status":"ERROR","result":null,"messages":[],"errors":[{"code":"err.password-validation.authorization.invalid"}]}'
    fi
    ;;
  'POST /api/v1/investment-choice/instructions/cancel')
    if [[ "$token" == fake-cancel-token ]]; then
      cancel_payload="$(jq -c '.' <<<"$request_body")"
      issued_cancel_payload="$(cat "$FAKE_VALID_CANCEL_PAYLOAD")"
      reference="$(jq -r '.referenceNo' <<<"$request_body")"
      if [[ "$cancel_payload" != "$issued_cancel_payload" ]]; then
        status=403
        response='{"success":false,"status":"ERROR","result":null,"messages":[],"errors":[{"code":"err.password-validation.authorization.invalid"}]}'
      elif [[ "$FAKE_LIVE" == yes && "$reference" == LIVE-REF-1 ]]; then
        response='{"success":true,"status":"SUCCESS","result":{"data":{"referenceNo":"LIVE-REF-1"}},"messages":[],"errors":[]}'
        printf '[]' >"$FAKE_PENDING"
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
chmod +x "$tmp/bin/curl"

run_fake_scenario() {
  local mode="$1" out="$2" initial_pending="${3:-[]}" expected_failure="${4:-no}" run_name scenario run_dir
  run_name="${5:-$mode}"
  scenario="${6:-NORMAL}"
  run_dir="$tmp/$run_name"
  mkdir -p "$run_dir"
  : >"$run_dir/get-count"
  : >"$run_dir/valid-payload"
  : >"$run_dir/valid-cancel-payload"
  printf '%s' "$initial_pending" >"$run_dir/pending"
  : >"$run_dir/reuse"
  : >"$run_dir/mutations"
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
    FAKE_LIVE="$mode" \
    FAKE_SCENARIO="$scenario" \
    FAKE_GET_COUNT="$run_dir/get-count" \
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

run_fake_scenario yes "$tmp/pending-preflight.log" \
  '[{"referenceNo":"PRE-EXISTING","submitDate":"12/11/2026","submitTime":"11:00:00","allocations":[]}]' yes pending
grep -Fq 'pending instructions exist' "$tmp/pending-preflight.log" || {
  printf 'FAIL live preflight did not reject an existing pending instruction\n'
  exit 1
}
[[ ! -s "$tmp/pending/mutations" ]] || {
  printf 'FAIL pending-instruction preflight altered member data\n'
  exit 1
}
grep -Fq 'PRE-EXISTING' "$tmp/pending/pending" || {
  printf 'FAIL pending-instruction preflight did not preserve the existing reference\n'
  exit 1
}

run_fake_scenario no "$tmp/p3-pending.log" \
  '[{"referenceNo":"PRE-EXISTING","submitDate":"12/11/2026","submitTime":"11:00:00","allocations":[]}]' no existing-pending
grep -Fq 'SKIP P0 reason=pending instructions exist' "$tmp/p3-pending.log" || {
  printf 'FAIL positive change issuance was not skipped for an existing pending instruction\n'
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
grep -Fq 'Password verifications this run (snapshot rejections excluded): 1' "$tmp/p3-pending.log" || {
  printf 'FAIL valid P3 issuance was not counted as a password verification\n'
  exit 1
}
[[ ! -s "$tmp/existing-pending/mutations" ]] || {
  printf 'FAIL non-live P3 altered existing member data\n'
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

for secret in \
  generator-password-sentinel \
  unused-login-password-sentinel \
  generator-cookie-sentinel \
  generator-csrf-sentinel \
  generator-account-ref-sentinel \
  fake-cim-token \
  fake-cancel-token; do
  if grep -Fq -- "$secret" "$tmp"/*.log; then
    printf 'FAIL secret appeared in scenario output/evidence\n'
    exit 1
  fi
done

printf 'PASS CIM M6 issuance, exact payload reuse, live gates, and output redaction\n'
