#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCENARIO="${CIM_SCENARIO_SCRIPT:-$ROOT/scripts/e2e/investment-choice-scenarios.sh}"
LIB="$ROOT/scripts/e2e/lib/result-records.sh"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT
real_jq="$(command -v jq)"
mkdir -p "$tmp/bin"
# shellcheck disable=SC1090 # LIB is resolved from ROOT at runtime.
source "$LIB"

fail=0
fail_count=0
case_rc=0

fail_test() {
  printf 'FAIL %s\n' "$1"
  fail=1
  fail_count=$((fail_count + 1))
}

pass_test() {
  printf 'PASS %s\n' "$1"
}

cat >"$tmp/bin/curl" <<'FAKE_CURL'
#!/usr/bin/env bash
set -Eeuo pipefail

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
body_present=no
[[ -z "$body_arg" ]] || body_present=yes
authenticated=no
csrf=no
account_ref=no
token=no
[[ "$headers" != *'Cookie: portal_sid='* ]] || authenticated=yes
[[ "$headers" != *'X-XSRF-TOKEN:'* ]] || csrf=yes
[[ "$headers" != *'Account-Ref:'* ]] || account_ref=yes
[[ "$headers" != *'X-Password-Validation-Token:'* ]] || token=yes

call_number="$(wc -l <"$FAKE_CALLS")"
call_number=$((call_number + 1))
printf '%s|%s|body=%s|auth=%s|csrf=%s|account=%s|token=%s\n' \
  "$method" "$path" "$body_present" "$authenticated" "$csrf" "$account_ref" "$token" \
  >>"$FAKE_CALLS"

status=200
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
      response="{\"success\":true,\"status\":\"SUCCESS\",\"result\":{\"data\":{\"accountNumber\":\"SYNTHETIC\",\"scenario\":\"NORMAL\",\"intervalPercentage\":20,\"switchSources\":[{\"switchSourceCode\":\"SYNTH-SOURCE\",\"switchSource\":\"Synthetic Source\",\"sequence\":1,\"available\":true}],\"funds\":$funds,\"history\":[],\"pendingInstructions\":[{\"referenceNo\":\"SYNTH-REF\"}] }},\"messages\":[],\"errors\":[]}"
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
if [[ "$call_number" == "${FAKE_FAIL_CODE_CALL:-}" ]]; then
  response='{"success":false,"status":"ERROR","result":null,"messages":[],"errors":[{"code":"err.synthetic.code-mismatch"}]}'
fi
if [[ "$call_number" == "${FAKE_NO_TOKEN:-}" ]]; then
  status=200
  response='{"success":true,"status":"SUCCESS","result":{"data":{"issued":true}},"messages":[],"errors":[]}'
fi
printf '%s\n%s' "$response" "$status"
FAKE_CURL
chmod 700 "$tmp/bin/curl"

run_case() {
  local name="$1" journal_mode="$2" snapshot="$3" fail_call="$4" fail_status="$5" target_fail="$6" journal="$7" path_override="${8:-}" fail_code_call="${9:-}" no_token="${10:-}"
  local -a env_args=(
    "PATH=${path_override:-$tmp/bin:$PATH}"
    "BASE_URL=http://synthetic.invalid"
    "SESSION_COOKIE=synthetic-cookie"
    "CSRF_TOKEN=synthetic-csrf"
    "ACCOUNT_REF=synthetic-account"
    "CIM_PASSWORD=synthetic-password"
    "CIM_USE_PASSWORD=yes"
    "CIM_RANDOM_SEED=17"
    "FAKE_CALLS=$tmp/$name.calls"
    "FAKE_SNAPSHOT=$snapshot"
    "FAKE_FAIL_CALL=$fail_call"
    "FAKE_FAIL_STATUS=$fail_status"
    "FAKE_TARGET_FAIL=$target_fail"
    "FAKE_FAIL_CODE_CALL=$fail_code_call"
    "FAKE_NO_TOKEN=$no_token"
    "REAL_JQ=${REAL_JQ:-$real_jq}"
  )

  : >"$tmp/$name.calls"
  if [[ "$journal_mode" == set ]]; then
    env "${env_args[@]}" "E2E_RESULT_JOURNAL=$journal" bash "$SCENARIO" \
      >"$tmp/$name.stdout" 2>"$tmp/$name.stderr" && case_rc=0 || case_rc=$?
  elif [[ "$journal_mode" == empty ]]; then
    env "${env_args[@]}" E2E_RESULT_JOURNAL= bash "$SCENARIO" \
      >"$tmp/$name.stdout" 2>"$tmp/$name.stderr" && case_rc=0 || case_rc=$?
  else
    env -u E2E_RESULT_JOURNAL "${env_args[@]}" bash "$SCENARIO" \
      >"$tmp/$name.stdout" 2>"$tmp/$name.stderr" && case_rc=0 || case_rc=$?
  fi
}

assert_case_parity() {
  local name="$1" unset_rc="$2" set_rc="$3"
  [[ "$unset_rc" -eq "$set_rc" ]] || fail_test "$name journal toggle exit status"
  cmp -s "$tmp/$name-unset.stdout" "$tmp/$name-set.stdout" || fail_test "$name journal toggle stdout"
  cmp -s "$tmp/$name-unset.stderr" "$tmp/$name-set.stderr" || fail_test "$name journal toggle stderr"
  cmp -s "$tmp/$name-unset.calls" "$tmp/$name-set.calls" || fail_test "$name journal toggle request arguments"
}

group_start=$fail_count
run_case lifecycle set ONE_FUND 12 403 '' "$tmp/lifecycle.jsonl"
[[ "$case_rc" -eq 1 ]] || fail_test 'forced P2 HTTP mismatch exits 1'
[[ -f "$tmp/lifecycle.jsonl" ]] || fail_test 'default-path journal is created'
if [[ -f "$tmp/lifecycle.jsonl" ]]; then
  if ! jq -e -s '
    def finished: [.[] | select(.event == "finish")];
    length == 26
    and [.[].seq] == [range(1;27)]
    and [.[].event] == [range(0;13) | "start","finish"]
    and [.[].id] == [
      "S1","S1","S2","S2","S3","S3","S4","S4","S5","S5","S6","S6","S7","S7",
      "S8","S8","S9-extra","S9-extra","S9-blank","S9-blank","P0-issue","P0-issue","P1-issue","P1-issue","P2-issue","P2-issue"
    ]
    and all(.[];
      .record_type == "scenario"
      and .pack == "investment-choice"
      and .duration_ms == null
      and .request_id == null
      and .echoed_request_id == null
    )
    and all(finished[]; .description != "" and .evidence_refs == [])
    and finished[0].expected_http_status == 200 and finished[0].actual_http_status == 200
    and finished[0].expected_public_error_code == null and finished[0].actual_public_error_code == null
    and finished[1].expected_http_status == 401 and finished[1].actual_http_status == 401
    and finished[1].expected_public_error_code == "err.security.authentication.required"
    and finished[1].actual_public_error_code == "err.security.authentication.required"
    and finished[8].expected_http_status == 422 and finished[8].actual_http_status == 422
    and finished[9].expected_public_error_code == "err.investment-choice.cancel.ref-required"
    and finished[10].status == "SKIP" and finished[10].reason_code == "PRECONDITION_UNAVAILABLE"
    and finished[10].expected_http_status == null and finished[10].actual_http_status == null
    and finished[11].status == "PASS"
    and finished[11].expected_public_error_code == "err.investment-choice.switch-source.unavailable"
    and finished[11].actual_public_error_code == "err.investment-choice.switch-source.unavailable"
    and finished[12].status == "FAIL" and finished[12].reason_code == "HTTP_STATUS_MISMATCH"
    and finished[12].expected_http_status == 422 and finished[12].actual_http_status == 403
    and finished[12].expected_public_error_code == "err.investment-choice.cancel.ref-invalid"
    and finished[12].actual_public_error_code == "err.investment-choice.cancel.ref-invalid"
    and all(.[ ]; .description != "")
  ' "$tmp/lifecycle.jsonl" >/dev/null; then
    jq -r -s '[.[] | {seq,event,id,status,reason_code,expected_http_status,actual_http_status}]' \
      "$tmp/lifecycle.jsonl" >&2
    fail_test 'journal pairs, fields, descriptions, status and reason values'
  fi
  if [[ "$(wc -l <"$tmp/lifecycle.jsonl")" -ne 26 ]]; then
    jq -r -s '[.[] | {seq,event,id,status}]' "$tmp/lifecycle.jsonl" >&2
    fail_test 'failing scenario has a finish before script exit'
  fi
else
  fail_test 'default-path journal content'
fi
[[ "$fail_count" -ne "$group_start" ]] || pass_test 'default-path lifecycle records include PASS, SKIP, failure, and sequence'

group_start=$fail_count
run_case code-mismatch set NORMAL '' '' '' "$tmp/code-mismatch.jsonl" '' 2
[[ "$case_rc" -eq 1 ]] || fail_test 'public error-code mismatch exits 1'
jq -e -s '
  [.[] | select(.event == "finish" and .id == "S2")][0]
  | .status == "FAIL"
    and .reason_code == "PUBLIC_ERROR_CODE_MISMATCH"
    and .expected_http_status == 401 and .actual_http_status == 401
    and .expected_public_error_code == "err.security.authentication.required"
    and .actual_public_error_code == "err.synthetic.code-mismatch"
' "$tmp/code-mismatch.jsonl" >/dev/null || fail_test 'public error-code mismatch has the specific reason code'
[[ "$fail_count" -ne "$group_start" ]] || pass_test 'public error-code mismatch is classified specifically'

group_start=$fail_count
run_case first-fail-unset unset NORMAL 1 503 '' ''
first_fail_unset_rc=$case_rc
run_case first-fail-set set NORMAL 1 503 '' "$tmp/first-fail-set.jsonl"
first_fail_set_rc=$case_rc
assert_case_parity first-fail "$first_fail_unset_rc" "$first_fail_set_rc"
[[ "$case_rc" -eq 1 ]] || fail_test 'S1 HTTP mismatch exits 1'
jq -e -s '
  length == 2
  and [.[].event] == ["start","finish"]
  and [.[].id] == ["S1","S1"]
  and .[1].status == "FAIL"
  and .[1].reason_code == "HTTP_STATUS_MISMATCH"
  and .[1].expected_http_status == 200 and .[1].actual_http_status == 503
  and ([.[] | select(.id == "S2")] | length) == 0
' "$tmp/first-fail-set.jsonl" >/dev/null || fail_test 'S1 HTTP mismatch finishes only S1 with actual status 503'
[[ "$fail_count" -ne "$group_start" ]] || pass_test 'first-request HTTP failure is recorded and execution remains byte-identical'

group_start=$fail_count
run_case bad-contract-unset unset BAD_CONTRACT '' '' '' ''
bad_contract_unset_rc=$case_rc
run_case bad-contract-set set BAD_CONTRACT '' '' '' "$tmp/bad-contract-set.jsonl"
bad_contract_set_rc=$case_rc
assert_case_parity bad-contract "$bad_contract_unset_rc" "$bad_contract_set_rc"
[[ "$case_rc" -eq 1 ]] || fail_test 'S1 bad contract exits 1'
jq -e -s '
  length == 2
  and .[1].id == "S1" and .[1].status == "FAIL"
  and .[1].reason_code == "CONTRACT_MISMATCH"
  and .[1].expected_http_status == 200 and .[1].actual_http_status == 200
  and ([.[] | select(.id == "S2")] | length) == 0
' "$tmp/bad-contract-set.jsonl" >/dev/null || fail_test 'S1 contract failure records status 200 without reaching S2'
[[ "$fail_count" -ne "$group_start" ]] || pass_test 'S1 contract failure is recorded and execution remains byte-identical'

group_start=$fail_count
run_case token-missing-unset unset NORMAL '' '' '' '' '' '' 11
token_missing_unset_rc=$case_rc
run_case token-missing-set set NORMAL '' '' '' "$tmp/token-missing-set.jsonl" '' '' 11
token_missing_set_rc=$case_rc
assert_case_parity token-missing "$token_missing_unset_rc" "$token_missing_set_rc"
[[ "$case_rc" -eq 1 ]] || fail_test 'missing P0 token exits 1'
jq -e -s '
  ([.[] | select(.event == "finish" and .id == "P0-issue")] | length) == 1
  and ([.[] | select(.event == "finish" and .id == "P0-issue")][0]
    | .status == "FAIL"
      and .reason_code == "CONTRACT_MISMATCH"
      and .expected_http_status == 200 and .actual_http_status == 200)
  and ([.[] | select(.id == "P1-issue")] | length) == 0
' "$tmp/token-missing-set.jsonl" >/dev/null || fail_test 'missing token has exactly one P0-issue CONTRACT_MISMATCH finish'
[[ "$fail_count" -ne "$group_start" ]] || pass_test 'first token failure is recorded and execution remains byte-identical'

group_start=$fail_count
run_case normal set NORMAL '' '' '' "$tmp/normal.jsonl"
[[ "$case_rc" -eq 0 ]] || fail_test 'normal P0-P3 run exits 0'
if [[ -f "$tmp/normal.jsonl" ]]; then
  jq -e -s '
    def finished: [.[] | select(.event == "finish")];
    length == 30
    and ([finished[] | select(.id == "P1-issue")] | length) == 1
    and ([finished[] | select(.id == "P2-issue")] | length) == 1
    and ([finished[] | select(.id == "P1-issue")][0].status) == "PASS"
    and ([finished[] | select(.id == "P2-issue")][0].status) == "PASS"
    and ([.[].id] | index("P3-body-binding") != null)
  ' "$tmp/normal.jsonl" >/dev/null || fail_test 'P1/P2 have one finish after passing target checks'
else
  fail_test 'normal P0-P3 journal exists'
fi
[[ "$fail_count" -ne "$group_start" ]] || pass_test 'P1/P2 passing target checks have one finish each'

group_start=$fail_count
for target_fail in 12 13; do
  name="target-fail-$target_fail"
  run_case "$name" set NORMAL '' '' "$target_fail" "$tmp/$name.jsonl"
  [[ "$case_rc" -eq 1 ]] || fail_test "$name exits 1"
  target_id=P1-issue
  [[ "$target_fail" != 13 ]] || target_id=P2-issue
  if [[ -f "$tmp/$name.jsonl" ]]; then
    jq -e -s --arg id "$target_id" '
      [.[] | select(.event == "finish" and .id == $id)] as $finished
      | ($finished | length) == 1
        and $finished[0].status == "FAIL"
        and $finished[0].reason_code == "CONTRACT_MISMATCH"
    ' "$tmp/$name.jsonl" >/dev/null || fail_test "$name has one CONTRACT_MISMATCH finish and no premature PASS finish"
  else
    fail_test "$name journal exists"
  fi
done
[[ "$fail_count" -ne "$group_start" ]] || pass_test 'P1/P2 target assertion failures finish once as FAIL'

group_start=$fail_count
for scenario_case in pass fail skip; do
  case "$scenario_case" in
    pass) snapshot=NORMAL; fail_call=''; fail_status='' ;;
    fail) snapshot=NORMAL; fail_call=2; fail_status=503 ;;
    skip) snapshot=FUNCTION_UNAVAILABLE; fail_call=''; fail_status='' ;;
  esac
  run_case "$scenario_case-unset" unset "$snapshot" "$fail_call" "$fail_status" '' "$tmp/$scenario_case-unset.jsonl"
  unset_rc=$case_rc
  run_case "$scenario_case-set" set "$snapshot" "$fail_call" "$fail_status" '' "$tmp/$scenario_case-set.jsonl"
  set_rc=$case_rc
  [[ "$unset_rc" -eq "$set_rc" ]] || fail_test "$scenario_case journal toggle exit status"
  cmp -s "$tmp/$scenario_case-unset.stdout" "$tmp/$scenario_case-set.stdout" || fail_test "$scenario_case journal toggle stdout"
  cmp -s "$tmp/$scenario_case-unset.stderr" "$tmp/$scenario_case-set.stderr" || fail_test "$scenario_case journal toggle stderr"
  cmp -s "$tmp/$scenario_case-unset.calls" "$tmp/$scenario_case-set.calls" || fail_test "$scenario_case journal toggle request arguments"
done
[[ "$fail_count" -ne "$group_start" ]] || pass_test 'journal toggle preserves output, status, and request arguments for pass/fail/skip'

group_start=$fail_count
if [[ -f "$tmp/skip-set.jsonl" ]]; then
  jq -e -s '
    def finished: [.[] | select(.event == "finish")];
    length == 30
    and [.[].seq] == [range(1;31)]
    and [.[].event] == [range(0;15) | "start","finish"]
    and [.[].id] == [
      "S1","S1","S2","S2","S3","S3","S4","S4","S5","S5","S6","S6","S7","S7",
      "S8","S8","S9-extra","S9-extra","S9-blank","S9-blank",
      "P0-issue","P0-issue","P1-issue","P1-issue","P2-issue","P2-issue",
      "P3-issue","P3-issue","P3-body-binding","P3-body-binding"
    ]
    and all(finished[10:][];
      .status == "SKIP"
      and .reason_code == "PRECONDITION_UNAVAILABLE"
      and .expected_http_status == null and .actual_http_status == null
      and .expected_public_error_code == null and .actual_public_error_code == null
    )
    and all(.[20:][]; .event == "start" or .event == "finish")
    and all(.[20:][]; .id != "P0" and .id != "P1" and .id != "P2" and .id != "P3")
  ' "$tmp/skip-set.jsonl" >/dev/null || fail_test 'all skipped P-checks use executed IDs, exact pairs, null HTTP fields and sequence'
  for skip_name in P0 P1 P2 P3; do
    [[ "$(grep -c "^SKIP $skip_name " "$tmp/skip-set.stdout" || :)" -eq 1 ]] \
      || fail_test "all-skipped terminal output preserves one original $skip_name line"
  done
  grep -Fqx 'Summary: passed=10 skipped=4 failed=0' "$tmp/skip-set.stdout" \
    || fail_test 'all-skipped terminal summary keeps one increment per P-group skip'
else
  fail_test 'all-skipped P-check journal exists'
fi
jq -r -s '[.[] | select(.record_type == "scenario" and (.id | test("^P[0-3]"))) | .id] | unique | .[]' \
  "$tmp/skip-set.jsonl" >"$tmp/skipped-p-ids"
jq -r -s '[.[] | select(.record_type == "scenario" and (.id | test("^P[0-3]"))) | .id] | unique | .[]' \
  "$tmp/normal.jsonl" >"$tmp/executed-p-ids"
cmp -s "$tmp/skipped-p-ids" "$tmp/executed-p-ids" || fail_test 'all-skipped and all-executed P journal ID sets match'
[[ "$fail_count" -ne "$group_start" ]] || pass_test 'all P groups skip with canonical IDs, original terminal names and unchanged counter'

group_start=$fail_count
mkdir "$tmp/unwritable-journal"
run_case unwritable-unset unset NORMAL '' '' '' "$tmp/unwritable-journal"
unwritable_unset_rc=$case_rc
run_case unwritable-set set NORMAL '' '' '' "$tmp/unwritable-journal"
[[ "$case_rc" -eq "$unwritable_unset_rc" ]] || fail_test 'unwritable journal exit status'
cmp -s "$tmp/unwritable-unset.stdout" "$tmp/unwritable-set.stdout" || fail_test 'unwritable journal stdout'
cmp -s "$tmp/unwritable-unset.stderr" "$tmp/unwritable-set.stderr" || fail_test 'unwritable journal stderr'
cmp -s "$tmp/unwritable-unset.calls" "$tmp/unwritable-set.calls" || fail_test 'unwritable journal request arguments'
[[ -s "$tmp/unwritable-journal.failed" ]] || fail_test 'unwritable journal failure marker'
[[ "$fail_count" -ne "$group_start" ]] || pass_test 'unwritable journal is inert to execution and writes failure marker'

group_start=$fail_count
mkdir "$tmp/no-jq-path"
for command_name in bash cat wc date mktemp stat id rm chmod rmdir dirname sort grep sed touch; do
  ln -s "$(command -v "$command_name")" "$tmp/no-jq-path/$command_name"
done
make_no_jq_shim() {
  local shim_dir="$1"
  mkdir "$shim_dir"
  cat >"$shim_dir/jq" <<'NO_JQ'
#!/usr/bin/env bash
set -u
input="$(cat)"
status=0
"$REAL_JQ" "$@" <<<"$input" || status=$?
if [[ "$*" == *'.errors[0].code // empty'* && "$input" == *'err.security.authentication.required'* ]]; then
  rm -f -- "$0"
fi
exit "$status"
NO_JQ
  chmod 700 "$shim_dir/jq"
}
make_no_jq_shim "$tmp/no-jq-unset-bin"
make_no_jq_shim "$tmp/no-jq-set-bin"
no_jq_path_unset="$tmp/no-jq-unset-bin:$tmp/bin:$tmp/no-jq-path"
no_jq_path_set="$tmp/no-jq-set-bin:$tmp/bin:$tmp/no-jq-path"
run_case no-jq-unset unset NORMAL 2 503 '' "$tmp/no-jq-unset.jsonl" "$no_jq_path_unset"
no_jq_unset_rc=$case_rc
run_case no-jq-set set NORMAL 2 503 '' "$tmp/no-jq-set.jsonl" "$no_jq_path_set"
no_jq_set_rc=$case_rc
[[ "$no_jq_set_rc" -eq "$no_jq_unset_rc" ]] || fail_test 'jq unavailable to hook exit status'
cmp -s "$tmp/no-jq-unset.stdout" "$tmp/no-jq-set.stdout" || fail_test 'jq unavailable to hook stdout'
cmp -s "$tmp/no-jq-unset.stderr" "$tmp/no-jq-set.stderr" || fail_test 'jq unavailable to hook stderr'
cmp -s "$tmp/no-jq-unset.calls" "$tmp/no-jq-set.calls" || fail_test 'jq unavailable to hook request arguments'
if [[ ! -s "$tmp/no-jq-set.jsonl.failed" ]]; then
  grep -E 'command not found|unavailable|ERROR' "$tmp/no-jq-set.stderr" >&2 || :
  fail_test 'jq unavailable to hook failure marker'
fi
[[ "$fail_count" -ne "$group_start" ]] || pass_test 'jq unavailable at result finish is inert and writes failure marker'

group_start=$fail_count
for journal_mode in unset empty; do
  name="standalone-$journal_mode"
  if [[ "$journal_mode" == unset ]]; then
    run_case "$name" unset FUNCTION_UNAVAILABLE '' '' '' "$tmp/$name.jsonl"
  else
    run_case "$name" empty FUNCTION_UNAVAILABLE '' '' '' "$tmp/$name.jsonl"
  fi
  [[ "$case_rc" -eq 0 ]] || fail_test "$name standalone run exits 0"
  [[ ! -e "$tmp/$name.jsonl" && ! -e "$tmp/$name.jsonl.failed" ]] || fail_test "$name standalone writes no journal or marker"
done
[[ "$fail_count" -ne "$group_start" ]] || pass_test 'standalone runs with unset or empty journal path write no evidence'

group_start=$fail_count
hook_functions="$tmp/hook-functions.sh"
: >"$hook_functions"
for function_name in cim_hook_description cim_result_start cim_result_finish cim_skip_journal_ids skip_scenario; do
  sed -n "/^${function_name}() {/,/^}/p" "$SCENARIO" >>"$hook_functions"
done
if ! grep -q '^cim_hook_description() {' "$hook_functions"; then
  fail_test 'hook description lookup is present'
else
  # shellcheck disable=SC1090 # Function bodies are extracted from the tested scenario script.
  source "$hook_functions"
  group_start=$fail_count
  p0_dedicated_reason="$(grep -F 'skip_scenario P0 ' "$SCENARIO" \
    | grep -F 'dedicated existing-instruction' \
    | sed -n "s/^[^']*'\\(.*\\)'$/\\1/p")"
  p3_dedicated_reason="$(grep -F 'skip_scenario P3 ' "$SCENARIO" \
    | grep -F 'dedicated existing-instruction' \
    | sed -n "s/^[^']*'\\(.*\\)'$/\\1/p")"
  reason_mapping_journal="$tmp/reason-mapping.jsonl"
  E2E_RESULT_JOURNAL="$reason_mapping_journal"
  export E2E_RESULT_JOURNAL
  SKIPPED=0
  if [[ -z "$p0_dedicated_reason" || -z "$p3_dedicated_reason" ]]; then
    fail_test 'dedicated skip reasons are extracted from P0 and P3 call sites'
  else
    skip_scenario P0 "$p0_dedicated_reason" >"$tmp/reason-mapping.stdout"
    skip_scenario P3 "$p3_dedicated_reason" >>"$tmp/reason-mapping.stdout"
    skip_scenario P1 'unrelated reason' >>"$tmp/reason-mapping.stdout"
    jq -e -s '
      length == 8
      and [.[].event] == ["start","finish","start","finish","start","finish","start","finish"]
      and [.[].id] == [
        "P0-issue","P0-issue",
        "P3-issue","P3-issue","P3-body-binding","P3-body-binding",
        "P1-issue","P1-issue"
      ]
      and ([.[] | select(.event == "finish" and .id != "P1-issue") | .reason_code]
        == ["FEATURE_DISABLED","FEATURE_DISABLED","FEATURE_DISABLED"])
      and ([.[] | select(.event == "finish" and .id == "P1-issue") | .reason_code]
        == ["PRECONDITION_UNAVAILABLE"])
      and all(.[];
        .event != "finish"
        or (.expected_http_status == null and .actual_http_status == null)
      )
    ' "$reason_mapping_journal" >/dev/null \
      || fail_test 'extracted dedicated reasons map P0/P3 to FEATURE_DISABLED and unrelated reason to PRECONDITION_UNAVAILABLE'
    grep -Fqx "SKIP P0 reason=$p0_dedicated_reason" "$tmp/reason-mapping.stdout" \
      || fail_test 'P0 dedicated skip keeps the original terminal name and reason'
    grep -Fqx "SKIP P3 reason=$p3_dedicated_reason" "$tmp/reason-mapping.stdout" \
      || fail_test 'P3 dedicated skip keeps the original terminal name and reason'
    [[ "$SKIPPED" -eq 3 ]] || fail_test 'isolated skip mapping increments once per call'
  fi
  [[ "$fail_count" -ne "$group_start" ]] || pass_test 'FEATURE_DISABLED mapping is tested from extracted dedicated-mode reasons'

  hook_output_journal="$tmp/hook-output.jsonl"
  E2E_RESULT_JOURNAL="$hook_output_journal"
  export E2E_RESULT_JOURNAL
  cim_result_start S1 >"$tmp/hook-start.stdout" 2>"$tmp/hook-start.stderr" \
    || fail_test 'hook start helper succeeds'
  cim_result_finish S1 PASS >"$tmp/hook-finish.stdout" 2>"$tmp/hook-finish.stderr" \
    || fail_test 'hook finish helper succeeds'
  [[ ! -s "$tmp/hook-start.stdout" && ! -s "$tmp/hook-start.stderr" \
    && ! -s "$tmp/hook-finish.stdout" && ! -s "$tmp/hook-finish.stderr" ]] \
    || fail_test 'hook helpers emit no terminal output'
  live_probe="$tmp/live-probe.jsonl"
  E2E_RESULT_JOURNAL="$live_probe"
  export E2E_RESULT_JOURNAL
  for live_id in E1-issue E2-cancel E3-get L1-submit L2-get L3-issue L3-cancel L4-get P0 P1 P2 P3 unknown; do
    if cim_hook_description "$live_id" >/dev/null; then
      fail_test "hook lookup accepts unhooked id $live_id"
    fi
    cim_result_start "$live_id" || fail_test "unhooked start helper $live_id"
    cim_result_finish "$live_id" PASS || fail_test "unhooked finish helper $live_id"
  done
  [[ ! -s "$live_probe" ]] || fail_test 'unhooked IDs append no journal records'
  while IFS='|' read -r hooked_id expected_description; do
    [[ -z "$hooked_id" ]] && continue
    actual_description="$(cim_hook_description "$hooked_id")" || {
      fail_test "hook lookup misses $hooked_id"
      continue
    }
    [[ "$actual_description" == "$expected_description" ]] || fail_test "literal description for $hooked_id"
  done <<'DESCRIPTIONS'
S1|Authenticated investment-choice GET satisfies the success-envelope and snapshot contract.
S2|Unauthenticated investment-choice GET returns the authentication-required contract.
S3|Instruction submission without a validation token is rejected.
S4|Instruction submission with an invalid validation token is rejected.
S5|Malformed change payload is rejected during password validation.
S6|Instruction submission with a malformed payload and no validation token is rejected.
S7|Cancellation without a validation token is rejected.
S8|Cancellation with an invalid validation token is rejected.
S9-extra|Cancel validation rejects an operation payload containing an extra field.
S9-blank|Cancel validation rejects a blank instruction reference.
P0-issue|A valid change payload receives a password-validation token.
P1-issue|The isolated negative change payload is rejected for its expected validation rule.
P2-issue|Cancellation authorization rejects a reference absent from the snapshot.
P3-issue|A cancellation token is issued for the existing pending instruction.
P3-body-binding|Cancellation with a different reference is rejected by token binding.
DESCRIPTIONS
fi
[[ "$fail_count" -ne "$group_start" ]] || pass_test 'hook lookup and helpers reject all live IDs and unknown ID'

group_start=$fail_count
live_harness="$ROOT/scripts/e2e/tests/cim-valid-payload-e2e-test.sh"
if [[ -f "$live_harness" ]]; then
  env -u E2E_RESULT_JOURNAL bash "$live_harness" >"$tmp/live-unset.stdout" 2>"$tmp/live-unset.stderr" \
    && live_unset_rc=0 || live_unset_rc=$?
  E2E_RESULT_JOURNAL="$tmp/live-set.jsonl" bash "$live_harness" \
    >"$tmp/live-set.stdout" 2>"$tmp/live-set.stderr" && live_set_rc=0 || live_set_rc=$?
  [[ "$live_unset_rc" -eq "$live_set_rc" ]] || fail_test 'live harness journal toggle exit status'
  cmp -s "$tmp/live-unset.stdout" "$tmp/live-set.stdout" || fail_test 'live harness journal toggle stdout'
  cmp -s "$tmp/live-unset.stderr" "$tmp/live-set.stderr" || fail_test 'live harness journal toggle stderr'
  if [[ -f "$tmp/live-set.jsonl" ]]; then
    jq -e -s '
      all(.[];
        .record_type != "scenario"
        or (.id | test("^(E1-issue|E2-cancel|E3-get|L1-submit|L2-get|L3-issue|L3-cancel|L4-get)$") | not)
      )
    ' "$tmp/live-set.jsonl" >/dev/null || fail_test 'live harness records no unhooked live ID'
    jq -e -s '
      def skipped($id; $reason):
        any(.[];
          .record_type == "scenario"
          and .event == "finish"
          and .id == $id
          and .status == "SKIP"
          and .reason_code == $reason
          and .expected_http_status == null
          and .actual_http_status == null
        );
      skipped("P0-issue"; "FEATURE_DISABLED")
      and skipped("P3-issue"; "FEATURE_DISABLED")
      and skipped("P3-body-binding"; "FEATURE_DISABLED")
      and skipped("P0-issue"; "PRECONDITION_UNAVAILABLE")
      and all(.[];
        .record_type != "scenario"
        or (.id | test("^P[0-3]$") | not)
      )
    ' "$tmp/live-set.jsonl" >/dev/null \
      || fail_test 'dedicated and ordinary P skips use executed IDs with their correct reason codes'
  else
    fail_test 'live harness journal exists'
  fi
  printed_live_ids="$(grep -Eo '\\b(E[1-3]-(issue|cancel|get)|L[1-4]-(submit|get|issue|cancel))\\b' \
    "$tmp/live-set.stdout" | sort -u || :)"
  if [[ -z "$printed_live_ids" ]]; then
    pass_test 'offline harness stdout contains no scenario IDs; no live IDs are claimed as exercised'
  else
    while IFS= read -r printed_id; do
      jq -e -s --arg id "$printed_id" \
        'all(.[]; .record_type != "scenario" or .id != $id)' \
        "$tmp/live-set.jsonl" >/dev/null || fail_test "printed live id $printed_id appears in journal"
    done <<<"$printed_live_ids"
  fi
else
  fail_test 'existing offline live-path harness is available'
fi
[[ "$fail_count" -ne "$group_start" ]] || pass_test 'offline live-path harness preserves output and records no live IDs'
if [[ "$fail" -eq 0 ]]; then
  printf 'All CIM result-hook tests passed.\n'
else
  printf 'CIM result-hook tests failed.\n'
fi
exit "$fail"
