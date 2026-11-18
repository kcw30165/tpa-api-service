#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCENARIO="${CIM_SCENARIO_SCRIPT:-$ROOT/scripts/e2e/investment-choice-scenarios.sh}"
LIB="$ROOT/scripts/e2e/lib/result-records.sh"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT
real_jq="$(command -v jq)"
real_mktemp="$(command -v mktemp)"
mkdir -p "$tmp/bin" "$tmp/scenario-temp"
# shellcheck disable=SC1090 # LIB is resolved from ROOT at runtime.
source "$LIB"

fail=0
fail_count=0
case_rc=0
test_mode="${CIM_CAPTURE_TEST_MODE:-all}"

fail_test() {
  printf 'FAIL %s\n' "$1"
  fail=1
  fail_count=$((fail_count + 1))
}

pass_test() {
  printf 'PASS %s\n' "$1"
}

cat >"$tmp/bin/mktemp" <<'FAKE_MKTEMP'
#!/usr/bin/env bash
set -Eeuo pipefail
if [[ "${1-}" == -d && "${2-}" == "${FAKE_WORK_PATTERN:-}" ]]; then
  mkdir -p "$FAKE_WORK_DIR"
  printf '%s\n' "$FAKE_WORK_DIR"
else
  exec "$REAL_MKTEMP" "$@"
fi
FAKE_MKTEMP

cat >"$tmp/bin/curl" <<'FAKE_CURL'
#!/usr/bin/env bash
set -Eeuo pipefail

original_args=("$@")
method=GET
url=''
headers=''
body_arg=''
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

path="${url#*://}"
path="/${path#*/}"
request_id=''
while IFS= read -r header; do
  if [[ "$header" =~ ^X-Request-Id:\ (.+)$ ]]; then
    request_id="${BASH_REMATCH[1]}"
  fi
done <<<"$headers"

call_number="$(wc -l <"$FAKE_LOG")"
call_number=$((call_number + 1))
args_json="$(printf '%s\n' "${original_args[@]}" | jq -Rsc 'split("\n")[:-1]')"
exported_globals=''
for global_name in CIM_LAST_REQUEST_ID CIM_LAST_TS_START CIM_LAST_TS_END; do
  if [[ -v "$global_name" ]]; then exported_globals+="${global_name},"; fi
done
jq -cn \
  --argjson call "$call_number" \
  --arg method "$method" \
  --arg path "$path" \
  --arg request_id "$request_id" \
  --argjson args "$args_json" \
  --arg exported_globals "$exported_globals" \
  '{call:$call,method:$method,path:$path,request_id:$request_id,args:$args,exported_globals:$exported_globals}' \
  >>"$FAKE_LOG"

status=200
response=''
case "$call_number" in
  1)
    if [[ "$FAKE_SNAPSHOT" == FUNCTION_UNAVAILABLE ]]; then
      response='{"success":true,"status":"SUCCESS","result":{"data":{"accountNumber":"SYNTHETIC","scenario":"FUNCTION_UNAVAILABLE","history":[]}},"messages":[],"errors":[]}'
    elif [[ "$FAKE_SNAPSHOT" == BAD_CONTRACT ]]; then
      response='{"success":true,"status":"SUCCESS","result":{"data":{"accountNumber":"SYNTHETIC","scenario":"NORMAL"}},"messages":[],"errors":[]}'
    else
      if [[ "$FAKE_SNAPSHOT" == ONE_FUND ]]; then
        funds='[{"fundCode":"SYNTH-FUND-1","fundName":"Synthetic Fund 1"}]'
      else
        funds='[{"fundCode":"SYNTH-FUND-1","fundName":"Synthetic Fund 1"},{"fundCode":"SYNTH-FUND-2","fundName":"Synthetic Fund 2"},{"fundCode":"SYNTH-FUND-3","fundName":"Synthetic Fund 3"}]'
      fi
      response="{\"success\":true,\"status\":\"SUCCESS\",\"result\":{\"data\":{\"accountNumber\":\"SYNTHETIC\",\"scenario\":\"NORMAL\",\"intervalPercentage\":20,\"switchSources\":[{\"switchSourceCode\":\"SYNTH-SOURCE\",\"switchSource\":\"Synthetic Source\",\"sequence\":1,\"available\":true}],\"funds\":$funds,\"history\":[],\"pendingInstructions\":[{\"referenceNo\":\"SYNTH-REF\"}]}},\"messages\":[],\"errors\":[]}"
    fi
    ;;
  2) status=401; response='{"success":false,"status":"ERROR","result":null,"messages":[],"errors":[{"code":"err.security.authentication.required"}]}' ;;
  3|4|6|7|8) status=403; response='{"success":false,"status":"ERROR","result":null,"messages":[],"errors":[{"code":"err.password-validation.authorization.invalid"}]}' ;;
  5|9) status=422; response='{"success":false,"status":"VALIDATION_FAILED","result":null,"messages":[],"errors":[{"code":"err.investment-choice.request.invalid","targets":[]}]}' ;;
  10) status=422; response='{"success":false,"status":"VALIDATION_FAILED","result":null,"messages":[],"errors":[{"code":"err.investment-choice.cancel.ref-required","targets":["referenceNo"]}]}' ;;
  11)
    if [[ "$FAKE_SNAPSHOT" == ONE_FUND ]]; then
      status=422
      response='{"success":false,"status":"VALIDATION_FAILED","result":null,"messages":[],"errors":[{"code":"err.investment-choice.switch-source.unavailable","targets":["switchSources[0]"]}]}'
    else
      response='{"success":true,"status":"SUCCESS","result":{"data":{"token":"SYNTH-TOKEN-P0"}},"messages":[],"errors":[]}'
    fi
    ;;
  12)
    status=422
    if [[ "$FAKE_SNAPSHOT" == ONE_FUND ]]; then
      response='{"success":false,"status":"VALIDATION_FAILED","result":null,"messages":[],"errors":[{"code":"err.investment-choice.cancel.ref-invalid","targets":["referenceNo"]}]}'
    elif [[ "$FAKE_TARGET_FAIL" == 12 ]]; then
      response='{"success":false,"status":"VALIDATION_FAILED","result":null,"messages":[],"errors":[{"code":"err.investment-choice.percentage.interval-invalid","targets":[]}] }'
    else
      response='{"success":false,"status":"VALIDATION_FAILED","result":null,"messages":[],"errors":[{"code":"err.investment-choice.percentage.interval-invalid","targets":["allocations[0].percentage"]}]}'
    fi
    ;;
  13)
    status=422
    if [[ "$FAKE_TARGET_FAIL" == 13 ]]; then
      response='{"success":false,"status":"VALIDATION_FAILED","result":null,"messages":[],"errors":[{"code":"err.investment-choice.cancel.ref-invalid","targets":[]}] }'
    else
      response='{"success":false,"status":"VALIDATION_FAILED","result":null,"messages":[],"errors":[{"code":"err.investment-choice.cancel.ref-invalid","targets":["referenceNo"]}]}'
    fi
    ;;
  14) response='{"success":true,"status":"SUCCESS","result":{"data":{"token":"SYNTH-TOKEN-P3"}},"messages":[],"errors":[]}' ;;
  15) status=403; response='{"success":false,"status":"ERROR","result":null,"messages":[],"errors":[{"code":"err.password-validation.authorization.invalid"}]}' ;;
  *) status=500; response='{"success":false,"status":"ERROR","result":null,"messages":[],"errors":[{"code":"err.synthetic.unexpected"}]}' ;;
esac

if [[ "$call_number" == "${FAKE_FAIL_CALL:-}" ]]; then
  status="${FAKE_FAIL_STATUS:-503}"
fi
if [[ "$call_number" == "${FAKE_DELAY_CALL:-}" ]]; then
  sleep "${FAKE_DELAY_SECONDS:-0.2}"
fi
if [[ "$call_number" == "${FAKE_CURL_FAIL_CALL:-}" ]]; then
  exit 7
fi
printf '%s\n%s' "$response" "$status"
FAKE_CURL

cat >"$tmp/bin/jq" <<'FAKE_JQ_DELAY'
#!/usr/bin/env bash
set -Eeuo pipefail
exec "$REAL_JQ" "$@"
FAKE_JQ_DELAY
chmod 700 "$tmp/bin/mktemp" "$tmp/bin/curl" "$tmp/bin/jq"

cat >"$tmp/post-curl-delay.sh" <<'POST_CURL_DELAY'
if [[ "${FAKE_POST_CURL_DELAY:-}" == yes ]]; then
  set -T
  trap 'if [[ "$BASH_COMMAND" == HTTP_STATUS=000 ]]; then sleep 0.2; fi' DEBUG
fi
POST_CURL_DELAY

extract_function() {
  local name="$1" destination="$2"
  sed -n "/^${name}() {/,/^}/p" "$SCENARIO" >"$destination"
}

elapsed_function="$tmp/cim-elapsed.sh"
extract_function cim_elapsed_ms "$elapsed_function"
if ! grep -q '^cim_elapsed_ms() {' "$elapsed_function"; then
  fail_test 'cim_elapsed_ms is extractable from the scenario script'
else
  # shellcheck disable=SC1090 # Function is extracted verbatim from the tested scenario script.
  source "$elapsed_function"
fi

assert_elapsed() {
  local expected="$1" start="$2" end="$3" actual rc
  if actual="$(cim_elapsed_ms "$start" "$end")"; then rc=0; else rc=$?; fi
  [[ "$rc" -eq 0 && "$actual" == "$expected" ]] || fail_test "cim_elapsed_ms $start -> $end expected $expected, got rc=$rc output=<$actual>"
}

assert_elapsed_failure() {
  local start="$1" end="$2" actual rc
  if actual="$(cim_elapsed_ms "$start" "$end")"; then rc=0; else rc=$?; fi
  [[ "$rc" -ne 0 && -z "$actual" ]] || fail_test "cim_elapsed_ms failure case $start -> $end returned rc=$rc output=<$actual>"
}

assert_elapsed 123 100.000000 100.123456
assert_elapsed 0 100.000000 100.000499
assert_elapsed 1 100.000000 100.000500
assert_elapsed 500 100,000000 100,500000
assert_elapsed_failure 100.000000 99.999999
assert_elapsed_failure 100.000000 186500.000001
assert_elapsed_failure '' 100.000000
assert_elapsed_failure 100.000000 ''
assert_elapsed_failure abc 1.000000
[[ "$fail_count" -ne 0 ]] || pass_test 'integer microsecond conversion, half-up rounding, comma timestamps and rejection cases'

extract_hooks() {
  local function_name
  : >"$tmp/cim-hooks.sh"
  for function_name in cim_hook_description cim_result_start cim_result_finish; do
    sed -n "/^${function_name}() {/,/^}/p" "$SCENARIO" >>"$tmp/cim-hooks.sh"
  done
}

extract_hooks
# shellcheck disable=SC1091 # Hook bodies are extracted verbatim from the tested scenario script.
source "$tmp/cim-hooks.sh"

run_case() {
  local name="$1" journal_mode="$2" snapshot="$3" fail_call="$4" fail_status="$5"
  local curl_fail_call="$6" delay_call="$7" delay_seconds="$8" bash_env="$9" path_override="${10:-$tmp/bin:$PATH}"
  local work_name="${name%-unset}"
  work_name="${work_name%-set}"
  local -a env_args=(
    "PATH=$path_override"
    "BASE_URL=http://synthetic.invalid"
    "SESSION_COOKIE=SYNTH_SECRET_COOKIE"
    "CSRF_TOKEN=SYNTH_SECRET_CSRF"
    "ACCOUNT_REF=SYNTH_SECRET_ACCOUNT"
    "CIM_PASSWORD=SYNTH_SECRET_PASSWORD"
    "CIM_USE_PASSWORD=yes"
    "CIM_RANDOM_SEED=17"
    "FAKE_LOG=$tmp/$name.calls"
    "FAKE_SNAPSHOT=$snapshot"
    "FAKE_FAIL_CALL=$fail_call"
    "FAKE_FAIL_STATUS=$fail_status"
    "FAKE_CURL_FAIL_CALL=$curl_fail_call"
    "FAKE_DELAY_CALL=$delay_call"
    "FAKE_DELAY_SECONDS=$delay_seconds"
    "FAKE_POST_CURL_DELAY=${FAKE_POST_CURL_DELAY_OVERRIDE:-}"
    "FAKE_TARGET_FAIL="
    "FAKE_WORK_PATTERN=$tmp/scenario-temp/cim-curl.XXXXXX"
    "FAKE_WORK_DIR=$tmp/work-$work_name"
    "TMPDIR=$tmp/scenario-temp"
    "REAL_JQ=$real_jq"
    "REAL_MKTEMP=$real_mktemp"
    "BASH_ENV=$bash_env"
  )

  : >"$tmp/$name.calls"
  if [[ "$journal_mode" == set ]]; then
    env "${env_args[@]}" "E2E_RESULT_JOURNAL=$tmp/$name.jsonl" bash "$SCENARIO" \
      >"$tmp/$name.stdout" 2>"$tmp/$name.stderr" && case_rc=0 || case_rc=$?
  else
    env -u E2E_RESULT_JOURNAL "${env_args[@]}" bash "$SCENARIO" \
      >"$tmp/$name.stdout" 2>"$tmp/$name.stderr" && case_rc=0 || case_rc=$?
  fi
}

normalise_ids() {
  sed -E 's/("request_id":"cim-)[0-9]+-([0-9]+)-[0-9]+"/\1\2"/' "$1" >"$2"
}

assert_parity() {
  local name="$1" unset_rc="$2" set_rc="$3"
  [[ "$unset_rc" -eq "$set_rc" ]] || fail_test "$name journal toggle exit status"
  cmp -s "$tmp/$name-unset.stdout" "$tmp/$name-set.stdout" || fail_test "$name journal toggle stdout"
  cmp -s "$tmp/$name-unset.stderr" "$tmp/$name-set.stderr" || fail_test "$name journal toggle stderr"
  normalise_ids "$tmp/$name-unset.calls" "$tmp/$name-unset.normalised"
  normalise_ids "$tmp/$name-set.calls" "$tmp/$name-set.normalised"
  cmp -s "$tmp/$name-unset.normalised" "$tmp/$name-set.normalised" \
    || fail_test "$name journal toggle full curl arguments differ beyond request_id"
}

scenario_id_for_call() {
  case "$1" in
    1) printf S1 ;;
    2) printf S2 ;;
    3) printf S3 ;;
    4) printf S4 ;;
    5) printf S5 ;;
    6) printf S6 ;;
    7) printf S7 ;;
    8) printf S8 ;;
    9) printf S9-extra ;;
    10) printf S9-blank ;;
    11) printf P0-issue ;;
    12) printf P1-issue ;;
    13) printf P2-issue ;;
    14) printf P3-issue ;;
    15) printf P3-body-binding ;;
    *) return 1 ;;
  esac
}

assert_request_ids() {
  local call id recorded logged
  for call in {1..15}; do
    id="$(scenario_id_for_call "$call")"
    logged="$(jq -er --argjson call "$call" 'select(.call == $call).request_id' "$tmp/pass-set.calls")" \
      || { fail_test "fake curl log has request ID for call $call"; continue; }
    recorded="$(jq -er --arg id "$id" \
      'select(.event == "finish" and .id == $id).request_id' "$tmp/pass-set.jsonl")" \
      || { fail_test "journal finish has request ID for $id"; continue; }
    [[ "$recorded" == "$logged" ]] || fail_test "journal request_id for $id differs from fake curl header"
  done
}

assert_closed_journal() {
  local journal="$1"
  jq -e -s '
    all(.[];
      if .event == "start" then
        (keys | sort) == (["journal_version","seq","event","record_type","pack","recorded_at_utc","id","description"] | sort)
      else
        (keys | sort) == (["journal_version","seq","event","record_type","pack","recorded_at_utc","id","description","status","expected_http_status","actual_http_status","expected_public_error_code","actual_public_error_code","duration_ms","request_id","echoed_request_id","reason_code","evidence_refs"] | sort)
      end
    )
  ' "$journal" >/dev/null || fail_test "$journal contains a field outside the closed scenario record keys"
}

if [[ "$test_mode" == all || "$test_mode" == request-id || "$test_mode" == globals ]]; then
  before_pass_failures=$fail_count
  run_case pass-unset unset NORMAL '' '' '' '' '' ''
  pass_unset_rc=$case_rc
  run_case pass-set set NORMAL '' '' '' '' '' ''
  pass_set_rc=$case_rc
  assert_parity pass "$pass_unset_rc" "$pass_set_rc"
  [[ "$pass_set_rc" -eq 0 ]] || fail_test 'default passing scenario run exits 0'
  [[ "$(wc -l <"$tmp/pass-set.calls")" -eq 15 ]] || fail_test 'default run logs all 15 requests'
  assert_request_ids
  assert_closed_journal "$tmp/pass-set.jsonl"
  jq -e -s 'all(.[]; .exported_globals == "")' "$tmp/pass-set.calls" >/dev/null \
    || fail_test 'captured globals are not exported to curl'
  jq -e -s '
    all(.[]; . as $call | ([$call.args[] | contains($call.request_id)] | any | not))
  ' "$tmp/pass-set.calls" >/dev/null \
    || fail_test 'request ID value is carried in the header stream, not curl argv'
  if grep -Eq 'CIM_LAST_REQUEST_ID|CIM_LAST_TS_START|CIM_LAST_TS_END|SYNTH_SECRET_' \
    "$tmp/pass-set.stdout" "$tmp/pass-set.stderr" "$tmp/pass-set.calls" "$tmp/pass-set.jsonl"; then
    fail_test 'captured globals or sentinel secrets leaked to terminal, curl arguments or journal'
  fi
  for secret in SYNTH_SECRET_COOKIE SYNTH_SECRET_CSRF SYNTH_SECRET_ACCOUNT SYNTH_SECRET_PASSWORD; do
    if grep -Fq "$secret" "$tmp/pass-set.jsonl" "$tmp/pass-set.jsonl.failed" 2>/dev/null; then
      fail_test "journal or marker leaked $secret"
    fi
  done
  [[ ! -e "$tmp/pass-set.jsonl.failed" ]] || fail_test 'passing run has no result failure marker'
  [[ "$fail_count" -eq "$before_pass_failures" ]] \
    && pass_test 'request IDs match logged X-Request-Id values and curl globals/secrets stay private'

  before_skip_failures=$fail_count
  run_case skip-unset unset FUNCTION_UNAVAILABLE '' '' '' '' '' ''
  skip_unset_rc=$case_rc
  run_case skip-set set FUNCTION_UNAVAILABLE '' '' '' '' '' ''
  skip_set_rc=$case_rc
  assert_parity skip "$skip_unset_rc" "$skip_set_rc"
  jq -e -s '
    all([.[] | select(.event == "finish" and (.id | test("^P[0-3]-")))][];
      .status == "SKIP" and .request_id == null and .duration_ms == null
    )
  ' "$tmp/skip-set.jsonl" >/dev/null || fail_test 'skipped P groups have null request IDs and durations'
  [[ "$fail_count" -eq "$before_skip_failures" ]] \
    && pass_test 'skipped P-group records clear captured request metadata'
fi

if [[ "$test_mode" == all || "$test_mode" == duration ]]; then
  if [[ ! -f "$tmp/pass-set.jsonl" ]]; then
    run_case pass-set set NORMAL '' '' '' '' '' ''
  fi
  run_case delayed set NORMAL '' '' '' 3 0.2 ''
  delayed_rc=$case_rc
  [[ "$delayed_rc" -eq 0 ]] || fail_test 'delayed request run exits 0'
  delayed_ms="$(jq -er -s '[.[] | select(.event == "finish" and .id == "S3")][0].duration_ms' "$tmp/delayed.jsonl" || true)"
  undelayed_ms="$(jq -er -s '[.[] | select(.event == "finish" and .id == "S2")][0].duration_ms' "$tmp/delayed.jsonl" || true)"
  if [[ ! "$delayed_ms" =~ ^[0-9]+$ ]] || (( delayed_ms < 150 || delayed_ms >= 5000 )); then
    fail_test "delayed duration is outside [150,5000): $delayed_ms"
  fi
  if [[ ! "$undelayed_ms" =~ ^[0-9]+$ ]] || (( undelayed_ms >= 150 || undelayed_ms >= delayed_ms )); then
    fail_test "undelayed duration is not lower than delayed duration: $undelayed_ms/$delayed_ms"
  fi

  FAKE_POST_CURL_DELAY_OVERRIDE=yes
  run_case parse-delay set NORMAL '' '' '' '' '' "$tmp/post-curl-delay.sh"
  FAKE_POST_CURL_DELAY_OVERRIDE=''
  parse_delay_rc=$case_rc
  parse_delay_ms="$(jq -er -s '[.[] | select(.event == "finish" and .id == "S2")][0].duration_ms' "$tmp/parse-delay.jsonl" || true)"
  if [[ "$parse_delay_rc" -ne 0 || ! "$parse_delay_ms" =~ ^[0-9]+$ ]] || (( parse_delay_ms >= 150 )); then
    fail_test "duration includes post-curl status parsing delay: $parse_delay_ms"
  fi
  pass_test 'duration measures curl wall time, excludes post-curl parsing, and respects the maximum'
fi

if [[ "$test_mode" == all || "$test_mode" == unset-epoch ]]; then
  printf '%s\n' 'unset EPOCHREALTIME' >"$tmp/unset-epoch.sh"
  # shellcheck disable=SC2016 # The child Bash evaluates this expression after sourcing BASH_ENV.
  if env BASH_ENV="$tmp/unset-epoch.sh" bash -c '[[ -z "${EPOCHREALTIME+x}" ]]'; then
    if [[ ! -f "$tmp/pass-set.stdout" ]]; then
      run_case pass-set set NORMAL '' '' '' '' '' ''
      pass_set_rc=$case_rc
    fi
    run_case no-epoch set NORMAL '' '' '' '' '' "$tmp/unset-epoch.sh"
    no_epoch_rc=$case_rc
    [[ "$no_epoch_rc" -eq "$pass_set_rc" ]] || fail_test 'unset EPOCHREALTIME run exit status matches normal run'
    cmp -s "$tmp/pass-set.stdout" "$tmp/no-epoch.stdout" || fail_test 'unset EPOCHREALTIME run stdout matches normal run'
    cmp -s "$tmp/pass-set.stderr" "$tmp/no-epoch.stderr" || fail_test 'unset EPOCHREALTIME run stderr matches normal run'
    jq -e -s 'all([.[] | select(.event == "finish")][]; .duration_ms == null)' \
      "$tmp/no-epoch.jsonl" >/dev/null || fail_test 'unset EPOCHREALTIME records null duration for every finish'
    pass_test 'BASH_ENV reliably unsets EPOCHREALTIME; durations are omitted without behavior changes'
  else
    fail_test 'BASH_ENV could not unset EPOCHREALTIME in this Bash'
  fi
fi

if [[ "$test_mode" == all || "$test_mode" == status ]]; then
  run_case curl-fail-unset unset NORMAL '' '' 2 '' '' ''
  curl_fail_unset_rc=$case_rc
  run_case curl-fail-set set NORMAL '' '' 2 '' '' ''
  curl_fail_set_rc=$case_rc
  assert_parity curl-fail "$curl_fail_unset_rc" "$curl_fail_set_rc"
  [[ "$curl_fail_set_rc" -eq 1 ]] || fail_test 'curl failure status 000 exits as the existing failed scenario'
  grep -Fq 'got=000' "$tmp/curl-fail-set.stderr" || fail_test 'curl failure continues to hold HTTP_STATUS=000'
  jq -e -s '
    [.[] | select(.event == "finish" and .id == "S2")][0]
    | .status == "FAIL" and .actual_http_status == null and .actual_public_error_code == null
  ' "$tmp/curl-fail-set.jsonl" >/dev/null || fail_test 'curl failure record omits status 000 and public code'
  [[ ! -e "$tmp/curl-fail-set.jsonl.failed" ]] || fail_test 'omitted status 000 does not create a failure marker'

  direct_journal="$tmp/direct-000.jsonl"
  E2E_RESULT_JOURNAL="$direct_journal"
  CIM_LAST_REQUEST_ID=''
  CIM_LAST_TS_START=''
  CIM_LAST_TS_END=''
  CIM_EXPECTED_HTTP_STATUS=200
  CIM_EXPECTED_PUBLIC_ERROR_CODE=''
  HTTP_STATUS=000
  ERROR_CODE=err.synthetic.invalid
  [[ "$E2E_RESULT_JOURNAL" == "$direct_journal" && -z "$CIM_LAST_REQUEST_ID$CIM_LAST_TS_START$CIM_LAST_TS_END" \
    && "$CIM_EXPECTED_HTTP_STATUS" == 200 && -z "$CIM_EXPECTED_PUBLIC_ERROR_CODE" \
    && "$HTTP_STATUS" == 000 && "$ERROR_CODE" == err.synthetic.invalid ]] \
    || fail_test 'direct status-000 fixture uses the expected hook state'
  if cim_result_finish S1 PASS >"$tmp/direct.stdout" 2>"$tmp/direct.stderr"; then direct_rc=0; else direct_rc=$?; fi
  [[ "$direct_rc" -eq 0 ]] || fail_test 'direct CIM finish with HTTP status 000 returns 0'
  [[ ! -s "$tmp/direct.stdout" && ! -s "$tmp/direct.stderr" ]] || fail_test 'direct CIM finish remains silent'
  jq -e -s '
    length == 1
    and .[0].actual_http_status == null
    and .[0].actual_public_error_code == null
    and .[0].expected_http_status == 200
  ' "$direct_journal" >/dev/null || fail_test 'direct CIM finish omits HTTP status 000'
  [[ ! -e "$direct_journal.failed" ]] || fail_test 'direct status 000 guard does not write a marker'
  pass_test 'existing status guard omits 000 in the curl-failure and direct-hook cases'
fi

if [[ "$test_mode" == all || "$test_mode" == parity ]]; then
  if [[ ! -f "$tmp/pass-set.stdout" ]]; then
    run_case pass-unset unset NORMAL '' '' '' '' '' ''
    pass_unset_rc=$case_rc
    run_case pass-set set NORMAL '' '' '' '' '' ''
    pass_set_rc=$case_rc
    assert_parity pass "$pass_unset_rc" "$pass_set_rc"
  fi
  run_case fail-unset unset NORMAL 2 503 '' '' '' ''
  fail_unset_rc=$case_rc
  run_case fail-set set NORMAL 2 503 '' '' '' ''
  fail_set_rc=$case_rc
  assert_parity fail "$fail_unset_rc" "$fail_set_rc"
  [[ "$fail_set_rc" -eq 1 ]] || fail_test 'failing scenario retains its exit status'

  if [[ ! -f "$tmp/skip-set.stdout" ]]; then
    run_case skip-unset unset FUNCTION_UNAVAILABLE '' '' '' '' '' ''
    skip_unset_rc=$case_rc
    run_case skip-set set FUNCTION_UNAVAILABLE '' '' '' '' '' ''
    skip_set_rc=$case_rc
    assert_parity skip "$skip_unset_rc" "$skip_set_rc"
  fi
  [[ "$skip_set_rc" -eq 0 ]] || fail_test 'skipped scenario retains its exit status'
  pass_test 'passing, failing and skipped runs preserve stdout, stderr, exit status and full curl args'
fi

if [[ "$test_mode" == all ]]; then
  live_harness="$ROOT/scripts/e2e/tests/cim-valid-payload-e2e-test.sh"
  env -u E2E_RESULT_JOURNAL bash "$live_harness" >"$tmp/live-unset.stdout" 2>"$tmp/live-unset.stderr" \
    && live_unset_rc=0 || live_unset_rc=$?
  E2E_RESULT_JOURNAL="$tmp/live-set.jsonl" bash "$live_harness" \
    >"$tmp/live-set.stdout" 2>"$tmp/live-set.stderr" && live_set_rc=0 || live_set_rc=$?
  [[ "$live_unset_rc" -eq "$live_set_rc" ]] || fail_test 'existing live-path harness journal toggle exit status'
  cmp -s "$tmp/live-unset.stdout" "$tmp/live-set.stdout" || fail_test 'existing live-path harness journal toggle stdout'
  cmp -s "$tmp/live-unset.stderr" "$tmp/live-set.stderr" || fail_test 'existing live-path harness journal toggle stderr'
  jq -e -s '
    all(.[];
      .record_type != "scenario"
      or (.id | test("^(E1-issue|E2-cancel|E3-get|L1-submit|L2-get|L3-issue|L3-cancel|L4-get)$") | not)
    )
  ' "$tmp/live-set.jsonl" >/dev/null || fail_test 'existing live-path harness journal has no E1-E3 or L1-L4 IDs'
  pass_test 'existing live-path harness is byte-identical at stdout/stderr/exit and records no live IDs'
fi

shopt -s nullglob
for journal in "$tmp"/*.jsonl; do
  assert_closed_journal "$journal"
done
for artifact in "$tmp"/*.jsonl "$tmp"/*.jsonl.failed; do
  if grep -Eq 'SYNTH_SECRET_|CIM_LAST_REQUEST_ID|CIM_LAST_TS_START|CIM_LAST_TS_END' "$artifact"; then
    fail_test "$artifact contains a secret or captured-global name"
  fi
done
for artifact in "$tmp"/*.stdout "$tmp"/*.stderr "$tmp"/*.calls; do
  if grep -Eq 'SYNTH_SECRET_|CIM_LAST_REQUEST_ID|CIM_LAST_TS_START|CIM_LAST_TS_END' "$artifact"; then
    fail_test "$artifact contains a secret or captured-global name"
  fi
done

if [[ "$fail" -eq 0 ]]; then
  printf 'All CIM request-capture tests passed.\n'
else
  printf 'CIM request-capture tests failed (%s assertions).\n' "$fail_count"
fi
exit "$fail"
