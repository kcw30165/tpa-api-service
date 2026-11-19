#!/usr/bin/env bash
set -Eeuo pipefail

TEST_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
PACK="${E2E_VESTING_PACK_OVERRIDE:-$TEST_ROOT/scripts/e2e/vesting-scale-scenarios.sh}"
PACK_ROOT="$(cd "$(dirname "$PACK")" && pwd)"
tmp="$(mktemp -d)"
fail=0

# shellcheck disable=SC2329 # Invoked by the EXIT trap below.
cleanup_test_tmp() {
  rm -rf -- "$tmp"
}
trap cleanup_test_tmp EXIT

fail_test() {
  printf 'FAIL vesting result hooks: %s\n' "$1" >&2
  fail=1
}

[[ -f "$PACK" ]] || { printf 'FAIL Vesting pack is unavailable\n' >&2; builtin exit 1; }
real_jq="$(command -v jq)"
[[ -x "$real_jq" ]] || { printf 'FAIL real jq is unavailable\n' >&2; builtin exit 1; }
mkdir -p "$tmp/bin" "$tmp/private" "$tmp/evidence" "$tmp/args" "$tmp/journals" "$tmp/output"

cat >"$tmp/bin/curl" <<'FAKE_CURL'
#!/usr/bin/env bash
set -u
args=("$@")
method=GET
body_file=''
headers_file=''
url=''
language=''
while (($#)); do
  case "$1" in
    -X) method="$2"; shift 2 ;;
    -H)
      case "$2" in
        @*) headers_file="${2#@}" ;;
        'Accept-Language: '*) language="${2#Accept-Language: }" ;;
      esac
      shift 2
      ;;
    -o) body_file="$2"; shift 2 ;;
    -w) shift 2 ;;
    http://*|https://*) url="$1"; shift ;;
    *) shift ;;
  esac
done
call_number="$(($(wc -l <"$CALLS_FILE") + 1))"
date -u +%s >>"$CURL_START_FILE"
if [[ "${FAKE_CURL_DELAY_FIRST:-false}" == true && "$call_number" == 1 ]]; then sleep 2; fi
if [[ "${FAKE_CURL_DELAY_SECOND:-false}" == true && "$call_number" == 2 ]]; then sleep 2; fi
printf '%s %s %s\n' "$method" "${url#"$BASE_URL"}" "$language" >>"$CALLS_FILE"
printf '%s\n' "${args[@]}" >"$CURL_ARGS_DIR/args-$call_number"
env -0 >"$CURL_ARGS_DIR/env-$call_number"
cat "$headers_file" >"$CURL_ARGS_DIR/headers-$call_number"
printf 'Accept-Language: %s\n' "$language" >>"$CURL_ARGS_DIR/headers-$call_number"
status="$FAKE_HTTP_STATUS"
if [[ "$language" == en && -n "${FAKE_HTTP_STATUS_EN:-}" ]]; then
  status="$FAKE_HTTP_STATUS_EN"
fi
jq --arg mode "$FAKE_RESPONSE_MODE" --arg language "$language" '
  if $mode == "body-fail" and $language == "en" then
    .success = false
  elif $mode == "cross-language" and $language == "zh-HK" then
    .result.data.planTypes[0].planNo = "Z1"
    | .result.data.overview.plans[0].planNo = "Z1"
    | .result.data.vestingScale.plans[0].planNo = "Z1"
  else . end
' "$FIXTURE_FILE" >"$body_file"
if [[ "$language" == en && "${FAKE_CURL_FAIL_EN:-false}" == true ]]; then
  date -u +%s >>"$CURL_TIME_FILE"
  printf '000'
  exit 7
fi
printf '%s' "$status"
date -u +%s >>"$CURL_TIME_FILE"
FAKE_CURL
chmod 700 "$tmp/bin/curl"
export PATH="$tmp/bin:$PATH"

cat >"$tmp/fixture.json" <<'FIXTURE'
{
  "success": true,
  "status": "SUCCESS",
  "result": {"data": {
    "planTypes": [{"planNo":"P1"},{"planNo":"P2"}],
    "overview": {
      "member": {"dateJoinedCompany":"01/01/2020","dateJoinedScheme":null,"employmentDate":null},
      "plans": [
        {"planNo":"P1","planEffectiveDate":"01/01/2020","dateOfJoiningPlan":"01/01/2020"},
        {"planNo":"P2","planEffectiveDate":null,"dateOfJoiningPlan":null}
      ]
    },
    "vestingScale": {"plans": [
      {"planNo":"P1","scales":[{"completedYears":0},{"completedYears":1}]},
      {"planNo":"P2","scales":null}
    ]}
  }},
  "messages": [],
  "errors": []
}
FIXTURE
export FIXTURE_FILE="$tmp/fixture.json"
export CALLS_FILE="$tmp/calls"
export CURL_ARGS_DIR="$tmp/args"
SENTINEL_BASE_URL='http://base-url-sentinel.invalid'
SENTINEL_SESSION_COOKIE='session-cookie-sentinel'
SENTINEL_CSRF_TOKEN='csrf-token-sentinel'
SENTINEL_ACCOUNT_REF='account-reference-sentinel'
export BASE_URL="$SENTINEL_BASE_URL" SESSION_COOKIE="$SENTINEL_SESSION_COOKIE"
export CSRF_TOKEN="$SENTINEL_CSRF_TOKEN" ACCOUNT_REF="$SENTINEL_ACCOUNT_REF"
export E2E_CONNECT_TIMEOUT_SECONDS=1
export E2E_REQUEST_TIMEOUT_SECONDS=1
export E2E_TLS_VERIFY=true
export FAKE_HTTP_STATUS=200
export FAKE_HTTP_STATUS_EN=''
export FAKE_CURL_FAIL_EN=false
export FAKE_RESPONSE_MODE=full

# The sourced pack's final exit is captured so paired runs share one BASHPID.
# shellcheck disable=SC2329 # Intercepts the sourced pack final exit for same-process comparisons.
exit() {
  VESTING_PACK_EXIT_CODE="${1:-0}"
  return 0
}

run_source_pack() {
  local mode="$1" label="$2"
  local saved_path="$PATH"
  export BASE_URL="$SENTINEL_BASE_URL" SESSION_COOKIE="$SENTINEL_SESSION_COOKIE"
  export CSRF_TOKEN="$SENTINEL_CSRF_TOKEN" ACCOUNT_REF="$SENTINEL_ACCOUNT_REF"
  export E2E_PRIVATE_STATE_DIR="$tmp/private/$label"
  export E2E_RUN_EVIDENCE_DIR="$tmp/evidence/$label"
  export CURL_ARGS_DIR="$tmp/args/$label"
  export CALLS_FILE="$tmp/calls-$label"
  mkdir -p "$E2E_PRIVATE_STATE_DIR" "$E2E_RUN_EVIDENCE_DIR" "$CURL_ARGS_DIR"
  chmod 700 "$E2E_PRIVATE_STATE_DIR" "$E2E_RUN_EVIDENCE_DIR"
  : >"$CALLS_FILE"
  CURL_TIME_FILE="$tmp/times-$label"
  CURL_START_FILE="$tmp/starts-$label"
  export CURL_START_FILE
  : >"$CURL_START_FILE"
  export CURL_TIME_FILE
  : >"$CURL_TIME_FILE"
  case "$mode" in
    unset) unset E2E_RESULT_JOURNAL ;;
    set) export E2E_RESULT_JOURNAL="$tmp/journals/$label.jsonl" ;;
    empty) export E2E_RESULT_JOURNAL='' ;;
    directory)
      export E2E_RESULT_JOURNAL="$tmp/journals/$label-directory"
      mkdir -p "$E2E_RESULT_JOURNAL"
      ;;
    *) fail_test "unknown journal mode"; return 1 ;;
  esac
  VESTING_PACK_EXIT_CODE=255
  # shellcheck disable=SC1090 # PACK may point to a scratch mutation copy.
  source "$PACK" >"$tmp/output/$label.$mode.stdout" 2>"$tmp/output/$label.$mode.stderr"
  export PATH="$saved_path"
  trap cleanup_test_tmp EXIT
  [[ "$VESTING_PACK_EXIT_CODE" != 255 ]] || fail_test "pack exit was not captured"
  RUN_EXIT_CODE="$VESTING_PACK_EXIT_CODE"
  RUN_CALLS_FILE="$CALLS_FILE"
  RUN_ARGS_DIR="$CURL_ARGS_DIR"
  RUN_SUMMARY_FILE="$E2E_RUN_EVIDENCE_DIR/040-vesting-scale-summary.txt"
}


normalize_environment_logs() {
  local source_dir="$1" target_dir="$2" env_file normalized
  mkdir -p "$target_dir"
  for env_file in "$source_dir"/env-*; do
    [[ -f "$env_file" ]] || continue
    normalized="$(tr '\0' '\n' <"$env_file" | grep -v '^E2E_RESULT_JOURNAL=' | LC_ALL=C sort)"
    printf '%s\n' "$normalized" >"$target_dir/$(basename "$env_file")"
  done
}
copy_request_arguments() {
  local source_dir="$1" target_dir="$2" arg_file
  mkdir -p "$target_dir"
  for arg_file in "$source_dir"/args-* "$source_dir"/headers-*; do
    [[ -f "$arg_file" ]] || continue
    cp "$arg_file" "$target_dir/"
  done
}

run_pair() {
  local label="$1" response_mode="$2" absent="$3" expected_exit="$4"
  local snapshot="$tmp/snapshot-$label" mode
  mkdir -p "$tmp/private/$label" "$tmp/evidence/$label" "$tmp/args/$label"
  chmod 700 "$tmp/private/$label" "$tmp/evidence/$label"
  export FAKE_RESPONSE_MODE="$response_mode"
  export VESTING_SCALE_EXPECT_ABSENT="$absent"
  export FAKE_HTTP_STATUS=200 FAKE_HTTP_STATUS_EN='' FAKE_CURL_FAIL_EN=false
  export FAKE_CURL_DELAY_FIRST=false FAKE_CURL_DELAY_SECOND=false
  case "$label" in
    http-503) export FAKE_HTTP_STATUS_EN=503 ;;
    curl-000) export FAKE_CURL_FAIL_EN=true ;;
    cross-language) export FAKE_CURL_DELAY_FIRST=true FAKE_CURL_DELAY_SECOND=true ;;
  esac
  mkdir -p "$snapshot"
  for mode in unset set; do
    : >"$tmp/evidence/$label/040-vesting-scale-summary.txt"
    rm -f -- "$tmp/args/$label"/* "$tmp/calls-$label"
    run_source_pack "$mode" "$label"
    [[ "$RUN_EXIT_CODE" == "$expected_exit" ]] || fail_test "$label returned an unexpected exit status"
    if [[ "$mode" == unset ]]; then
      mkdir -p "$snapshot/unset"
      cp "$tmp/output/$label.unset.stdout" "$snapshot/unset/stdout"
      cp "$tmp/output/$label.unset.stderr" "$snapshot/unset/stderr"
      cp "$RUN_CALLS_FILE" "$snapshot/unset/calls"
      copy_request_arguments "$RUN_ARGS_DIR" "$snapshot/unset/args"
      cp "$RUN_SUMMARY_FILE" "$snapshot/unset/summary"
      normalize_environment_logs "$RUN_ARGS_DIR" "$snapshot/unset/env"
    else
      cmp -s "$snapshot/unset/stdout" "$tmp/output/$label.set.stdout" || fail_test "$label stdout changed with journal"
      cmp -s "$snapshot/unset/stderr" "$tmp/output/$label.set.stderr" || fail_test "$label stderr changed with journal"
      cmp -s "$snapshot/unset/calls" "$RUN_CALLS_FILE" || fail_test "$label request order changed with journal"
      copy_request_arguments "$RUN_ARGS_DIR" "$snapshot/set-args"
      diff -r "$snapshot/unset/args" "$snapshot/set-args" >/dev/null || fail_test "$label curl arguments or headers changed with journal"
      cmp -s "$snapshot/unset/summary" "$RUN_SUMMARY_FILE" || fail_test "$label summary evidence changed with journal"
      normalize_environment_logs "$RUN_ARGS_DIR" "$snapshot/set-env"
      diff -r "$snapshot/unset/env" "$snapshot/set-env" >/dev/null || fail_test "$label curl environment changed beyond journal path"
    fi
    grep -Eq 'VESTING_HOOK|e2e_result_' "$tmp/output/$label.$mode.stdout" \
      && fail_test "$label hook wrote to terminal stdout"
  done
  [[ "$(wc -l <"$tmp/calls-$label")" == 2 ]] || fail_test "$label did not perform exactly two GETs"
  [[ "$(sed -n '1p' "$tmp/calls-$label")" == 'GET /api/v1/vesting-scale en' \
    && "$(sed -n '2p' "$tmp/calls-$label")" == 'GET /api/v1/vesting-scale zh-HK' ]] \
    || fail_test "$label request order changed"
}

checks=(http-status envelope planTypes-array overview-member-object overview-plans-array \
  overview-plan-order vesting-scale-shape vesting-plan-reference vesting-plan-order \
  completed-years-order member-dates plan-dates)
expected_ids=()
for language in en zh-HK; do
  for check in "${checks[@]}"; do expected_ids+=("$language.$check"); done
done
expected_ids+=(language-plan-sequence)

expected_hook_description() {
  local language check text
  if [[ "$#" -eq 1 && "$1" == language-plan-sequence ]]; then
    printf "%s" "English and zh-HK responses contain the same planTypes sequence."
    return 0
  fi
  [[ "$#" -eq 2 ]] || return 1
  case "$1" in en) language=English ;; zh-HK) language=zh-HK ;; *) return 1 ;; esac
  check="$2"
  case "$check" in
    http-status) text="Vesting Scale response returns HTTP status 200." ;;
    envelope) text="Vesting Scale response has success=true and status=SUCCESS." ;;
    planTypes-array) text="result.data.planTypes is an array." ;;
    overview-member-object) text="result.data.overview.member is an object." ;;
    overview-plans-array) text="result.data.overview.plans is an array." ;;
    overview-plan-order) text="overview plan numbers match planTypes order." ;;
    vesting-scale-shape) text="vestingScale is absent or has an object with a plans array." ;;
    vesting-plan-reference) text="vesting plans reference known planTypes and valid scales." ;;
    vesting-plan-order) text="vesting plans are unique and ordered by planTypes." ;;
    completed-years-order) text="completedYears values are numeric and nondecreasing." ;;
    member-dates) text="member dates are null or formatted DD/MM/YYYY." ;;
    plan-dates) text="plan dates are null or formatted DD/MM/YYYY." ;;
    absent-memberCategory) text="memberCategory is absent from the member object." ;;
    absent-dateJoinedCompany) text="dateJoinedCompany is absent from the member object." ;;
    absent-dateJoinedScheme) text="dateJoinedScheme is absent from the member object." ;;
    absent-employmentDate) text="employmentDate is absent from the member object." ;;
    absent-planEffectiveDate) text="planEffectiveDate is absent from every plan object." ;;
    absent-dateOfJoiningPlan) text="dateOfJoiningPlan is absent from every plan object." ;;
    absent-planService) text="planService is absent from every plan object." ;;
    absent-memberContributionBasis) text="memberContributionBasis is absent from every plan object." ;;
    absent-employerContributionBasis) text="employerContributionBasis is absent from every plan object." ;;
    absent-vestingScale) text="vestingScale is absent from result.data." ;;
    *) return 1 ;;
  esac
  printf "%s %s" "$language" "$text"
}

ids_json() {
  printf '%s\n' "$@" | jq -Rsc 'split("\n")[:-1]'
}

assert_pass_journal() {
  local journal="$1" expected_json descriptions_json id language check description
  local -a ids=("${expected_ids[@]}")
  if [[ -n "${VESTING_SCALE_EXPECT_ABSENT:-}" ]]; then
    local -a fields=()
    IFS=, read -r -a fields <<<"$VESTING_SCALE_EXPECT_ABSENT"
    ids=()
    for language in en zh-HK; do
      for check in "${checks[@]}"; do ids+=("$language.$check"); done
      for check in "${fields[@]}"; do ids+=("$language.absent-$check"); done
    done
    ids+=(language-plan-sequence)
  fi
  local id_list
  id_list="$(ids_json "${ids[@]}")"
  descriptions_json="$(
    for id in "${ids[@]}"; do
      if [[ "$id" == language-plan-sequence ]]; then
        description="$(expected_hook_description language-plan-sequence)" || return 1
      else
        language="${id%%.*}"
        check="${id#*.}"
        description="$(expected_hook_description "$language" "$check")" || return 1
      fi
      printf '%s\t%s\n' "$id" "$description"
    done | jq -Rn '[inputs | split("\t") | {id: .[0], description: .[1]}]'
  )" || fail_test "description lookup failed"
  expected_json="$(jq -cn --argjson ids "$id_list" --argjson descriptions "$descriptions_json" \
    '{ids:$ids, descriptions:$descriptions}')"
  jq -s -e --argjson expected "$expected_json" '
    . as $records
    | ($records | length) == (2 * ($expected.ids | length))
      and ([$records[] | select(.event == "start") | .id] == $expected.ids)
      and ([$records[] | select(.event == "finish") | .id] == $expected.ids)
      and ([$records[].seq] == [range(1; ($records | length) + 1)])
      and all(range(0; ($expected.ids | length)); . as $i |
        $expected.ids[$i] as $id
        | ([$records[] | select(.event == "start" and .id == $id)]) as $starts
        | ([$records[] | select(.event == "finish" and .id == $id)]) as $finishes
        | ($starts | length) == 1
          and ($finishes | length) == 1
          and $starts[0].seq < $finishes[0].seq
          and $starts[0].pack == "vesting-scale"
          and $finishes[0].pack == "vesting-scale"
          and $starts[0].record_type == "scenario"
          and $finishes[0].record_type == "scenario"
          and $starts[0].description == $expected.descriptions[$i].description
          and $finishes[0].description == $expected.descriptions[$i].description
          and $finishes[0].status == "PASS"
          and $finishes[0].reason_code == null
          and $finishes[0].request_id == null
          and $finishes[0].echoed_request_id == null
          and $finishes[0].duration_ms == null
          and $finishes[0].expected_public_error_code == null
          and $finishes[0].actual_public_error_code == null
          and $finishes[0].evidence_refs == []
          and (if $id == "language-plan-sequence" then
            $finishes[0].expected_http_status == null
            and $finishes[0].actual_http_status == null
          else
            $finishes[0].actual_http_status == 200
            and $finishes[0].expected_http_status ==
              (if ($id | endswith(".http-status")) then 200 else null end)
          end)
      )
  ' "$journal" >/dev/null || fail_test "journal contents, pair order, IDs, descriptions, fields, or sequence were incorrect"
}
assert_finish() {
  local journal="$1" id="$2" status="$3" reason="$4" actual="$5" expected="$6"
  jq -s -e --arg id "$id" --arg status "$status" --arg reason "$reason" \
    --arg actual "$actual" --arg expected "$expected" '
    [.[] | select(.event == "finish" and .id == $id)] as $records
    | ($records | length) == 1
      and $records[0].status == $status
      and $records[0].reason_code == (if $reason == "" then null else $reason end)
      and $records[0].actual_http_status == (if $actual == "" then null else ($actual | tonumber) end)
      and $records[0].expected_http_status == (if $expected == "" then null else ($expected | tonumber) end)
  ' "$journal" >/dev/null || fail_test "unexpected outcome fields for $id"
}

if ! grep -q "^vesting_hook_description()" "$PACK"; then
  run_pair pre-hook full "" 0
  [[ -s "$tmp/journals/pre-hook.jsonl" ]] || fail_test "Vesting scenario journal hooks are missing"
  builtin exit 1
fi

en_get_line="$(grep -n '^english_status=.*fetch_language en' "$PACK" | cut -d: -f1 || :)"
zh_get_line="$(grep -n '^chinese_status=.*fetch_language zh-HK' "$PACK" | cut -d: -f1 || :)"
validate_en_line="$(grep -n '^validate_response en ' "$PACK" | cut -d: -f1 || :)"
validate_zh_line="$(grep -n '^validate_response zh-HK ' "$PACK" | cut -d: -f1 || :)"
http_start_line="$(grep -nF "vesting_result_start \"\$language\" http-status || :" "$PACK" | cut -d: -f1 || :)"
http_evaluation_line="$(grep -nF "if [[ \"\$status\" == 200 ]]" "$PACK" | cut -d: -f1 || :)"
http_pass_finish_line="$(grep -nF "vesting_result_finish \"\$language\" http-status PASS || :" "$PACK" | cut -d: -f1 || :)"
http_fail_finish_line="$(grep -nF "vesting_result_finish \"\$language\" http-status FAIL HTTP_STATUS_MISMATCH || :" "$PACK" | cut -d: -f1 || :)"
record_start_line="$(grep -nF "vesting_result_start \"\$language\" \"\$name\" || :" "$PACK" | cut -d: -f1 || :)"
record_evaluation_line="$(grep -nF "if jq -e \"\$expression\"" "$PACK" | cut -d: -f1 || :)"
record_pass_finish_line="$(grep -nF "vesting_result_finish \"\$language\" \"\$name\" PASS || :" "$PACK" | cut -d: -f1 || :)"
record_fail_finish_line="$(grep -nF "vesting_result_finish \"\$language\" \"\$name\" FAIL CONTRACT_MISMATCH || :" "$PACK" | cut -d: -f1 || :)"
absent_start_line="$(grep -nF "vesting_result_start \"\$language\" \"absent-\$field\" || :" "$PACK" | cut -d: -f1 || :)"
absent_evaluation_line="$(grep -nF "if check_absent_field \"\$body_file\" \"\$field\"" "$PACK" | cut -d: -f1 || :)"
absent_pass_finish_line="$(grep -nF "vesting_result_finish \"\$language\" \"absent-\$field\" PASS || :" "$PACK" | cut -d: -f1 || :)"
absent_fail_finish_line="$(grep -nF "vesting_result_finish \"\$language\" \"absent-\$field\" FAIL CONTRACT_MISMATCH || :" "$PACK" | cut -d: -f1 || :)"
sequence_start_line="$(grep -n '^vesting_result_start language-plan-sequence || :' "$PACK" | cut -d: -f1 || :)"
sequence_evaluation_line="$(grep -n '^if jq -e -s ' "$PACK" | cut -d: -f1 || :)"
sequence_pass_finish_line="$(grep -n '^  vesting_result_finish language-plan-sequence PASS || :' "$PACK" | cut -d: -f1 || :)"
sequence_fail_finish_line="$(grep -n '^  vesting_result_finish language-plan-sequence FAIL CONTRACT_MISMATCH || :' "$PACK" | cut -d: -f1 || :)"
[[ "$en_get_line" -lt "$zh_get_line" && "$zh_get_line" -lt "$validate_en_line" \
  && "$validate_en_line" -lt "$validate_zh_line" \
  && "$validate_zh_line" -lt "$sequence_start_line" \
  && "$sequence_start_line" -lt "$sequence_evaluation_line" ]] \
  || fail_test "both GETs, validations, and cross-language evaluation are not in the required order"
[[ "$http_start_line" -lt "$http_evaluation_line" \
  && "$record_start_line" -lt "$record_evaluation_line" \
  && "$absent_start_line" -lt "$absent_evaluation_line" ]] \
  || fail_test "a scenario start does not precede its check evaluation"
[[ "$http_evaluation_line" -lt "$http_pass_finish_line" \
  && "$http_evaluation_line" -lt "$http_fail_finish_line" \
  && "$record_evaluation_line" -lt "$record_pass_finish_line" \
  && "$record_evaluation_line" -lt "$record_fail_finish_line" \
  && "$absent_evaluation_line" -lt "$absent_pass_finish_line" \
  && "$absent_evaluation_line" -lt "$absent_fail_finish_line" \
  && "$sequence_evaluation_line" -lt "$sequence_pass_finish_line" \
  && "$sequence_evaluation_line" -lt "$sequence_fail_finish_line" ]] \
  || fail_test "a finish hook precedes its check outcome"
run_pair pass full '' 0
pass_journal_path="$E2E_RESULT_JOURNAL"
unset E2E_RESULT_JOURNAL
hook_stdout="$(vesting_result_start en envelope; vesting_result_finish en envelope PASS || :)"
[[ -z "$hook_stdout" ]] || fail_test "result hook wrote to stdout"
export E2E_RESULT_JOURNAL="$pass_journal_path"
assert_pass_journal "$tmp/journals/pass.jsonl"
run_pair absent full 'memberCategory,planService' 0
assert_pass_journal "$tmp/journals/absent.jsonl"
assert_balanced_pairs() {
  jq -s -e '([.[] | select(.event == "start") | .id] | sort) == ([.[] | select(.event == "finish") | .id] | sort)
    and ([.[] | select(.event == "start")] | length) == ([.[] | select(.event == "finish")] | length)' "$1" >/dev/null \
    || fail_test "start and finish records were not balanced"
}

run_pair body-failure body-fail '' 1
assert_finish "$tmp/journals/body-failure.jsonl" en.envelope FAIL CONTRACT_MISMATCH 200 ''
assert_balanced_pairs "$tmp/journals/body-failure.jsonl"
jq -s -e 'all(.[] | select(.event == "finish"); if .id == "en.envelope" then .status == "FAIL" and .reason_code == "CONTRACT_MISMATCH" else .status == "PASS" end)' "$tmp/journals/body-failure.jsonl" >/dev/null || fail_test "body failure affected checks other than envelope"
run_pair cross-language cross-language '' 1
assert_finish "$tmp/journals/cross-language.jsonl" language-plan-sequence FAIL CONTRACT_MISMATCH '' ''
assert_balanced_pairs "$tmp/journals/cross-language.jsonl"
last_request_epoch=$(tail -n 1 "$tmp/times-cross-language")
start_timestamp=$(jq -s -r '[.[] | select(.event == "start" and .id == "language-plan-sequence")][0].recorded_at_utc' "$tmp/journals/cross-language.jsonl")
finish_timestamp=$(jq -s -r '[.[] | select(.event == "finish" and .id == "language-plan-sequence")][-1].recorded_at_utc' "$tmp/journals/cross-language.jsonl")
start_epoch=$(date -u -d "$start_timestamp" +%s)
finish_epoch=$(date -u -d "$finish_timestamp" +%s)
[[ "$start_epoch" -ge "$last_request_epoch" ]] || fail_test "cross-language start preceded the second GET"
[[ "$finish_epoch" -ge "$last_request_epoch" ]] || fail_test "cross-language finish preceded the final GET"
jq -s -e 'all(.[] | select(.event == "finish" and .id != "language-plan-sequence"); .status == "PASS")' \
  "$tmp/journals/cross-language.jsonl" >/dev/null || fail_test "cross-language mismatch failed a per-language check"
run_pair http-503 full '' 1
assert_finish "$tmp/journals/http-503.jsonl" en.http-status FAIL HTTP_STATUS_MISMATCH 503 200
assert_balanced_pairs "$tmp/journals/http-503.jsonl"
jq -s -e 'all(.[] | select(.event == "finish" and (.id | startswith("en."))); if .id == "en.http-status" then .status == "FAIL" and .reason_code == "HTTP_STATUS_MISMATCH" else .status == "PASS" end)' "$tmp/journals/http-503.jsonl" >/dev/null || fail_test "503 affected a body-contract check"
jq -s -e 'all(.[] | select(.event == "finish" and (.id | startswith("en."))); .actual_http_status == 503)' \
  "$tmp/journals/http-503.jsonl" >/dev/null || fail_test "503 was not attached to every English record"
run_pair curl-000 full '' 1
assert_finish "$tmp/journals/curl-000.jsonl" en.http-status FAIL HTTP_STATUS_MISMATCH '' 200
assert_balanced_pairs "$tmp/journals/curl-000.jsonl"
[[ ! -e "$tmp/journals/curl-000.jsonl.failed" ]] || fail_test "HTTP 000 caused a journal rejection marker"
jq -s -e 'all(.[] | select(.event == "finish" and (.id | startswith("en."))); .actual_http_status == null)' \
  "$tmp/journals/curl-000.jsonl" >/dev/null || fail_test "HTTP 000 was not omitted from English records"

# Append failure must be inert for the pack and must be marked by the frozen library.
# run_pair uses a normal file; exercise the directory target with a dedicated paired run.
export FAKE_RESPONSE_MODE=full VESTING_SCALE_EXPECT_ABSENT=''
export FAKE_HTTP_STATUS_EN="" FAKE_CURL_FAIL_EN=false
mkdir -p "$tmp/private/append-directory" "$tmp/evidence/append-directory" "$tmp/args/append-directory"
chmod 700 "$tmp/private/append-directory" "$tmp/evidence/append-directory"
for mode in unset directory; do
  : >"$tmp/evidence/append-directory/040-vesting-scale-summary.txt"
  rm -f -- "$tmp/args/append-directory"/* "$tmp/calls-append-directory"
  run_source_pack "$mode" append-directory
  [[ "$RUN_EXIT_CODE" == 0 ]] || fail_test "append-failure mode changed the pack exit"
  if [[ "$mode" == unset ]]; then
    mkdir -p "$tmp/snapshot-append-directory/unset"
    cp "$tmp/output/append-directory.unset.stdout" "$tmp/snapshot-append-directory/unset/stdout"
    cp "$tmp/output/append-directory.unset.stderr" "$tmp/snapshot-append-directory/unset/stderr"
    cp "$RUN_CALLS_FILE" "$tmp/snapshot-append-directory/unset/calls"
    copy_request_arguments "$RUN_ARGS_DIR" "$tmp/snapshot-append-directory/unset/args"
  else
    cmp -s "$tmp/snapshot-append-directory/unset/stdout" "$tmp/output/append-directory.directory.stdout" || fail_test "append failure changed stdout"
    cmp -s "$tmp/snapshot-append-directory/unset/stderr" "$tmp/output/append-directory.directory.stderr" || fail_test "append failure changed stderr"
    cmp -s "$tmp/snapshot-append-directory/unset/calls" "$RUN_CALLS_FILE" || fail_test "append failure changed request order"
    copy_request_arguments "$RUN_ARGS_DIR" "$tmp/snapshot-append-directory/set-args"
    diff -r "$tmp/snapshot-append-directory/unset/args" "$tmp/snapshot-append-directory/set-args" >/dev/null || fail_test "append failure changed curl arguments or headers"
  fi
done
[[ -s "$tmp/journals/append-directory-directory.failed" ]] || fail_test "append failure marker is missing"

# Empty/unset standalone modes create neither journal nor failure marker.
export FAKE_RESPONSE_MODE=full VESTING_SCALE_EXPECT_ABSENT=''
export FAKE_HTTP_STATUS_EN="" FAKE_CURL_FAIL_EN=false
for mode in unset empty; do
  run_source_pack "$mode" "standalone-$mode"
  [[ "$RUN_EXIT_CODE" == 0 ]] || fail_test "standalone run failed with journal $mode"
  [[ ! -e "$tmp/journals/standalone-$mode.jsonl" \
    && ! -e "$tmp/journals/standalone-$mode.jsonl.failed" ]] \
    || fail_test "standalone run created journal artifacts"
done

# Invalid absence configuration remains a pre-request exit-2 path.
export E2E_RESULT_JOURNAL="$tmp/journals/invalid-absence.jsonl"
export E2E_PRIVATE_STATE_DIR="$tmp/private/invalid-absence"
export E2E_RUN_EVIDENCE_DIR="$tmp/evidence/invalid-absence"
export VESTING_SCALE_EXPECT_ABSENT='notAField'
export CALLS_FILE="$tmp/calls-invalid-absence"
export CURL_ARGS_DIR="$tmp/args/invalid-absence"
mkdir -p "$CURL_ARGS_DIR"
: >"$CALLS_FILE"
mkdir -p "$E2E_PRIVATE_STATE_DIR" "$E2E_RUN_EVIDENCE_DIR"
chmod 700 "$E2E_PRIVATE_STATE_DIR" "$E2E_RUN_EVIDENCE_DIR"
set +e
bash "$PACK" >"$tmp/output/invalid-absence.stdout" 2>"$tmp/output/invalid-absence.stderr"
invalid_rc=$?
set -e
[[ "$invalid_rc" == 2 && ! -e "$E2E_RESULT_JOURNAL" \
  && ! -e "$E2E_RESULT_JOURNAL.failed" ]] || fail_test "invalid absence list did not retain the existing exit-2 behavior"
[[ ! -s "$tmp/calls-invalid-absence" ]] || fail_test "invalid absence list made HTTP calls"
unset VESTING_SCALE_EXPECT_ABSENT E2E_RESULT_JOURNAL

# The test must use the real jq; only curl is replaced.
[[ "$(command -v jq)" == "$real_jq" ]] || fail_test "the test intercepted jq instead of using the real jq"


# Description lookup is the hooked-set guard; compare its absent-field literals to the frozen registry.
registry_fields="$(awk '
  /case "\$field" in/ { collecting=1; next }
  collecting && /^[[:space:]]*\*\)/ { exit }
  collecting {
    gsub(/\\/, "")
    gsub(/\)/, "")
    gsub(/;;.*/, "")
    gsub(/[|[:space:]]+/, "\n")
    print
  }
' "$PACK_ROOT/lib/feature-pack-registry.sh" | sed '/^$/d' | LC_ALL=C sort -u)"
hook_fields="$(sed -n '/^vesting_hook_description()/,/^}/p' "$PACK" \
  | sed -nE 's/^[[:space:]]*absent-([[:alnum:]]+)\).*/\1/p' | LC_ALL=C sort -u)"
[[ -n "$registry_fields" && "$registry_fields" == "$hook_fields" ]] \
  || fail_test "the hook absence-field literals differ from the registry allow-list"
for bad_lookup in 'fr envelope' 'en unknown-check' 'envelope'; do
  IFS=" " read -r -a lookup_args <<<"$bad_lookup"
  if vesting_hook_description "${lookup_args[@]}" >/dev/null 2>&1; then
    fail_test "unknown lookup input was accepted"
  fi
done
if vesting_hook_description en absent-notAField >/dev/null 2>&1; then
  fail_test "an absent-field outside the allow-list was accepted"
fi
before_lines="$(wc -l <"$tmp/journals/pass.jsonl")"
vesting_result_start en unknown-check || :
vesting_result_finish en unknown-check PASS || :
vesting_result_start fr envelope || :
vesting_result_finish fr envelope PASS || :
vesting_result_start envelope || :
vesting_result_finish envelope PASS || :
after_lines="$(wc -l <"$tmp/journals/pass.jsonl")"
[[ "$before_lines" == "$after_lines" ]] || fail_test "an unknown lookup emitted a journal record"

# No test output or retained journal/marker may contain sentinels from the runtime environment.
for artifact in "$tmp"/journals/*.jsonl "$tmp"/journals/*.failed; do
  [[ -f "$artifact" ]] || continue
  if grep -F -q -e "$SENTINEL_SESSION_COOKIE" -e "$SENTINEL_CSRF_TOKEN" -e "$SENTINEL_ACCOUNT_REF" -e "$SENTINEL_BASE_URL" "$artifact"; then
    fail_test "a sensitive runtime sentinel entered a journal or marker"
  fi
done

# Every wrapper call is inert under errexit; every frozen call also has local protection.
if grep -E '^[[:space:]]*vesting_result_(start|finish)[[:space:]]' "$PACK" \
  | grep -Ev '\|\|[[:space:]]*:[[:space:]]*$' >/dev/null; then
  fail_test "a hook call is missing its errexit guard"
fi
if grep -E '^[[:space:]]*e2e_result_(start|finish)[[:space:]]' "$PACK" \
  | grep -Ev '\|\|[[:space:]]*:[[:space:]]*$' >/dev/null; then
  fail_test "a frozen library call is missing its local guard"
fi

if [[ "$fail" -ne 0 ]]; then builtin exit 1; fi
printf 'PASS Vesting scenario result hooks, redaction, lifecycle pairs, and byte-identical behavior\n'
builtin exit 0
