#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
LIB="$ROOT/scripts/e2e/lib/result-records.sh"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT
umask 077

# The library path is resolved from ROOT at runtime, including in mutation scratch copies.
# shellcheck disable=SC1090
source "$LIB"

fail=0
RUN_RC=0
JOURNAL="$tmp/scenarios.jsonl"
E2E_RESULT_JOURNAL="$JOURNAL"
export E2E_RESULT_JOURNAL

fail_test() {
  printf 'FAIL %s\n' "$1"
  fail=1
}

pass_test() {
  printf 'PASS %s\n' "$1"
}

run_record() {
  if "$@" >"$tmp/stdout" 2>"$tmp/stderr"; then
    RUN_RC=0
  else
    RUN_RC=$?
  fi
  [[ ! -s "$tmp/stdout" && ! -s "$tmp/stderr" ]] || fail_test "record function output"
}

expect_rc() {
  local expected="$1"
  local label="$2"
  [[ "$RUN_RC" -eq "$expected" ]] || fail_test "$label return code"
}

assert_keys_and_enums() {
  local journal="$1"
  jq -e -s '
    def common: ["event", "journal_version", "pack", "record_type", "recorded_at_utc", "seq"];
    def keys_are($extra): (keys | sort) == ((common + $extra) | sort);
    length == 6
    and [.[].seq] == [range(1; 7)]
    and [.[].event] == ["start", "finish", "start", "finish", "start", "finish"]
    and all(.[];
      .journal_version == 1
      and (.recorded_at_utc | type == "string" and test("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$"))
      and .record_type == "scenario"
      and .pack == "test-pack"
      and (if .event == "start" then
        keys_are(["id", "description"])
      else
        keys_are(["id", "description", "status", "expected_http_status", "actual_http_status",
          "expected_public_error_code", "actual_public_error_code", "duration_ms", "request_id",
          "echoed_request_id", "reason_code", "evidence_refs"])
      end)
    )
    and [.[].id] == ["pass_case", "pass_case", "fail_case", "fail_case", "skip_case", "skip_case"]
    and [.[1].status, .[3].status, .[5].status] == ["PASS", "FAIL", "SKIP"]
    and .[1].expected_http_status == 422
    and .[1].actual_http_status == 422
    and .[1].expected_public_error_code == "err.validation.invalid"
    and .[1].actual_public_error_code == "err.validation.invalid"
    and .[1].duration_ms == 42
    and .[1].evidence_refs == ["scenarios/validation.json", "logs/request.txt"]
    and .[1].reason_code == null
    and .[3].reason_code == "ASSERTION_FAILED"
    and .[3].duration_ms == 86400000
    and .[5].reason_code == "NOT_EXECUTED"
  ' "$journal" >/dev/null
}

run_record e2e_result_start scenario test-pack pass_case 'Synthetic expected 422 validation'
expect_rc 0 'scenario PASS start'
run_record e2e_result_finish scenario test-pack pass_case \
  'description=Synthetic expected 422 validation' \
  'status=PASS' \
  'expected_http_status=422' \
  'actual_http_status=422' \
  'duration_ms=42' \
  'expected_public_error_code=err.validation.invalid' \
  'actual_public_error_code=err.validation.invalid' \
  'evidence_refs=scenarios/validation.json,logs/request.txt'
expect_rc 0 'scenario PASS finish'
run_record e2e_result_start scenario test-pack fail_case 'Synthetic assertion failure'
expect_rc 0 'scenario FAIL start'
run_record e2e_result_finish scenario test-pack fail_case \
  'description=Synthetic assertion failure' \
  'status=FAIL' \
  'duration_ms=86400000' \
  'reason_code=ASSERTION_FAILED'
expect_rc 0 'scenario FAIL finish'
run_record e2e_result_start scenario test-pack skip_case 'Synthetic unexecuted scenario'
expect_rc 0 'scenario SKIP start'
run_record e2e_result_finish scenario test-pack skip_case \
  'description=Synthetic unexecuted scenario' \
  'status=SKIP' \
  'reason_code=NOT_EXECUTED'
expect_rc 0 'scenario SKIP finish'

if assert_keys_and_enums "$JOURNAL"; then
  pass_test 'scenario start/finish keys, order, sequence, timestamp, and status'
else
  fail_test 'scenario journal schema'
fi
[[ "$(wc -l <"$JOURNAL")" -eq 6 ]] || fail_test 'scenario records are not one JSON object per line'
[[ "$(stat -c '%a' "$JOURNAL")" == 600 ]] || fail_test 'journal mode'

LIFECYCLE_JOURNAL="$tmp/lifecycle.jsonl"
E2E_RESULT_JOURNAL="$LIFECYCLE_JOURNAL"
export E2E_RESULT_JOURNAL
run_record e2e_result_start lifecycle - login
expect_rc 0 'lifecycle start'
run_record e2e_result_finish lifecycle - login outcome=PASS
expect_rc 0 'lifecycle finish'
run_record e2e_result_start phase investment-choice submit
expect_rc 0 'phase start'
run_record e2e_result_finish phase investment-choice submit outcome=PASS
expect_rc 0 'phase finish'
jq -e -s '
  def common: ["event", "journal_version", "pack", "record_type", "recorded_at_utc", "seq"];
  def keys_are($extra): (keys | sort) == ((common + $extra) | sort);
  length == 4
  and [.[].seq] == [1, 2, 3, 4]
  and .[0].record_type == "lifecycle" and .[0].pack == null and .[0].step == "login"
  and (.[0] | keys_are(["step"]))
  and .[1].outcome == "PASS"
  and (.[1] | keys_are(["step", "outcome", "expected_http_status", "actual_http_status",
    "expected_public_error_code", "actual_public_error_code", "duration_ms", "request_id",
    "echoed_request_id", "reason_code", "evidence_refs"]))
  and .[1].expected_http_status == null and .[1].actual_http_status == null
  and .[1].expected_public_error_code == null and .[1].actual_public_error_code == null
  and .[1].duration_ms == null and .[1].request_id == null and .[1].echoed_request_id == null
  and .[1].reason_code == null and .[1].evidence_refs == []
  and .[2].record_type == "phase" and .[2].pack == "investment-choice"
  and .[2].phase_id == "submit" and (.[2] | keys_are(["phase_id"]))
  and .[3].outcome == "PASS" and .[3].safe_observations == {}
  and (.[3] | keys_are(["phase_id", "outcome", "expected_http_status", "actual_http_status",
    "expected_public_error_code", "actual_public_error_code", "duration_ms", "request_id",
    "echoed_request_id", "reason_code", "safe_observations", "evidence_refs"]))
  and .[3].evidence_refs == []
' "$LIFECYCLE_JOURNAL" >/dev/null || fail_test 'lifecycle and phase schema/defaults'
[[ "$(stat -c '%a' "$LIFECYCLE_JOURNAL")" == 600 ]] || fail_test 'lifecycle journal mode'
pass_test 'lifecycle and phase records with null, array, and object defaults'

REJECT_JOURNAL="$tmp/rejections.jsonl"
REJECT_MARKER="$REJECT_JOURNAL.failed"
E2E_RESULT_JOURNAL="$REJECT_JOURNAL"
export E2E_RESULT_JOURNAL
run_record e2e_result_start scenario test-pack seed_case 'Synthetic seed'
expect_rc 0 'rejection seed start'
run_record e2e_result_finish scenario test-pack seed_case description='Synthetic seed' status=PASS
expect_rc 0 'rejection seed finish'
: >"$tmp/all-rejection-output"

expect_rejection() {
  local label="$1"
  local before after marker_line
  shift
  before="$(wc -l <"$REJECT_JOURNAL")"
  run_record "$@"
  expect_rc 2 "$label"
  after="$(wc -l <"$REJECT_JOURNAL")"
  [[ "$before" == "$after" ]] || fail_test "$label appended a record"
  marker_line="$(tail -n 1 "$REJECT_MARKER" 2>/dev/null || true)"
  [[ "$marker_line" == RECORD_REJECTED ]] || fail_test "$label marker"
  cat "$tmp/stdout" "$tmp/stderr" >>"$tmp/all-rejection-output"
}

expect_rejection unknown-record-type e2e_result_start unknown - step
expect_rejection unknown-key e2e_result_finish scenario test-pack bad_key \
  description='Synthetic invalid key' status=PASS "private_value=SYNTHETIC_UNKNOWN_FIELD_SECRET"
expect_rejection invalid-status e2e_result_finish scenario test-pack bad_status \
  description='Synthetic invalid status' status=INVALID
expect_rejection invalid-outcome e2e_result_finish lifecycle - logout outcome=INVALID
expect_rejection invalid-reason-code e2e_result_finish scenario test-pack bad_reason \
  description='Synthetic invalid reason' status=FAIL reason_code=SYNTHETIC_REASON_SECRET
expect_rejection missing-failure-reason e2e_result_finish scenario test-pack missing_reason \
  description='Synthetic missing reason' status=FAIL
expect_rejection pass-with-reason e2e_result_finish scenario test-pack pass_reason \
  description='Synthetic PASS with reason' status=PASS reason_code=ASSERTION_FAILED
expect_rejection missing-scenario-description e2e_result_finish scenario test-pack missing_description status=PASS
expect_rejection missing-start-description e2e_result_start scenario test-pack missing_start_description
expect_rejection unknown-lifecycle-step e2e_result_start lifecycle - authenticate
expect_rejection duplicate-finish-key e2e_result_finish scenario test-pack duplicate_finish \
  description='Synthetic duplicate key' status=PASS status=PASS
expect_rejection finish-argument-without-equals e2e_result_finish scenario test-pack malformed_finish \
  description='Synthetic malformed argument' status=PASS bare-argument
expect_rejection lifecycle-record-with-pack e2e_result_start lifecycle test-pack login
expect_rejection phase-record-without-pack e2e_result_start phase - submit
expect_rejection scenario-record-without-pack e2e_result_start scenario - packless_case 'Synthetic packless scenario'
expect_rejection bad-identifier e2e_result_start scenario test-pack 'bad/id' 'Synthetic identifier'
expect_rejection control-character-description e2e_result_start scenario test-pack control_description $'Synthetic\ninvalid'
too_long_description="$(printf '%201s' '')"
expect_rejection overlong-description e2e_result_start scenario test-pack long_description "$too_long_description"
expect_rejection http-status-out-of-range e2e_result_finish scenario test-pack bad_http \
  description='Synthetic HTTP status' status=PASS actual_http_status=600
expect_rejection nonnumeric-actual-http-status e2e_result_finish scenario test-pack bad_http_text \
  description='Synthetic nonnumeric HTTP status' status=PASS actual_http_status=abc
expect_rejection expected-http-status-out-of-range e2e_result_finish scenario test-pack bad_expected_http \
  description='Synthetic expected HTTP status' status=PASS expected_http_status=99
expect_rejection negative-duration e2e_result_finish scenario test-pack negative_duration \
  description='Synthetic negative duration' status=PASS duration_ms=-1
expect_rejection non-integer-duration e2e_result_finish scenario test-pack fractional_duration \
  description='Synthetic fractional duration' status=PASS duration_ms=1.5
expect_rejection duration-over-maximum e2e_result_finish scenario test-pack excessive_duration \
  description='Synthetic excessive duration' status=PASS duration_ms=86400001
expect_rejection traversal-evidence-ref e2e_result_finish scenario test-pack traversal_ref \
  description='Synthetic evidence traversal' status=PASS evidence_refs=../SYNTHETIC_EVIDENCE_SECRET
expect_rejection absolute-evidence-ref e2e_result_finish scenario test-pack absolute_ref \
  description='Synthetic absolute evidence' status=PASS evidence_refs=/private/evidence
expect_rejection empty-evidence-segment e2e_result_finish scenario test-pack empty_segment \
  description='Synthetic empty evidence segment' status=PASS evidence_refs=logs//request.txt
expect_rejection non-empty-safe-observations e2e_result_finish phase investment-choice submit \
  outcome=PASS safe_observations='{"token":"synthetic"}'
expect_rejection unknown-enum-secret e2e_result_finish scenario test-pack enum_secret \
  description='Synthetic enum secret' status=SYNTHETIC_ENUM_SECRET

for sentinel in \
  SYNTHETIC_UNKNOWN_FIELD_SECRET \
  SYNTHETIC_ENUM_SECRET \
  SYNTHETIC_REASON_SECRET \
  SYNTHETIC_EVIDENCE_SECRET
do
  if grep -Fq "$sentinel" "$REJECT_JOURNAL" "$REJECT_MARKER" "$tmp/all-rejection-output"; then
    fail_test 'rejected sentinel leaked'
  fi
done
[[ "$(stat -c '%a' "$REJECT_MARKER")" == 600 ]] || fail_test 'rejection marker mode'
if grep -Ev '^(APPEND_FAILED|RECORD_REJECTED)$' "$REJECT_MARKER" >/dev/null; then
  fail_test 'marker contains a non-closed value'
fi
pass_test 'rejections preserve the journal, write closed markers, and do not leak sentinels'

Q17_JOURNAL="$tmp/q17.jsonl"
Q17_MARKER="$Q17_JOURNAL.failed"
E2E_RESULT_JOURNAL="$Q17_JOURNAL"
export E2E_RESULT_JOURNAL
run_record e2e_result_start scenario test-pack q17_unsafe 'Synthetic unsafe values'
expect_rc 0 'Q17 unsafe start'
run_record e2e_result_finish scenario test-pack q17_unsafe \
  description='Synthetic unsafe values' status=PASS \
  'request_id=SYNTHETIC_Q17_REQUEST_SECRET value' \
  'echoed_request_id=SYNTHETIC_Q17_ECHO_SECRET value' \
  'expected_public_error_code=SYNTHETIC_Q17_EXPECTED_SECRET value' \
  'actual_public_error_code=SYNTHETIC_Q17_ACTUAL_SECRET value'
expect_rc 0 'Q17 unsafe finish'
run_record e2e_result_start scenario test-pack q17_equal 'Synthetic equal request IDs'
expect_rc 0 'Q17 equal start'
run_record e2e_result_finish scenario test-pack q17_equal \
  description='Synthetic equal request IDs' status=PASS \
  request_id=synthetic-request-1 echoed_request_id=synthetic-request-1
expect_rc 0 'Q17 equal finish'
jq -e -s '
  .[1].request_id == null and .[1].echoed_request_id == null
  and .[1].expected_public_error_code == null and .[1].actual_public_error_code == null
  and .[3].request_id == "synthetic-request-1" and .[3].echoed_request_id == null
' "$Q17_JOURNAL" >/dev/null || fail_test 'Q17 null handling'
for sentinel in \
  SYNTHETIC_Q17_REQUEST_SECRET \
  SYNTHETIC_Q17_ECHO_SECRET \
  SYNTHETIC_Q17_EXPECTED_SECRET \
  SYNTHETIC_Q17_ACTUAL_SECRET
do
  if grep -Fq "$sentinel" "$Q17_JOURNAL" "$tmp/stdout" "$tmp/stderr"; then
    fail_test 'Q17 sentinel leaked'
  elif [[ -f "$Q17_MARKER" ]] && grep -Fq "$sentinel" "$Q17_MARKER"; then
    fail_test 'Q17 sentinel leaked'
  fi
done
pass_test 'Q17 unsafe values and echoed request IDs are null without rejection'

state_snapshot() {
  printf '%s\n%s\n%s\n%s\n' "$-" "$(trap -p)" "$(umask)" "$PWD"
}

umask 027
trap ':' ERR
source_journal_before="$(sha256sum "$Q17_JOURNAL")"
state_before="$(state_snapshot)"
{
  # Runtime source path keeps the library usable from mutation scratch copies.
  # shellcheck disable=SC1090
  source "$LIB"
  # Runtime source path keeps the library usable from mutation scratch copies.
  # shellcheck disable=SC1090
  source "$LIB"
} >"$tmp/source-stdout" 2>"$tmp/source-stderr"
state_after="$(state_snapshot)"
[[ "$state_before" == "$state_after" ]] || fail_test 'sourcing changed caller state'
[[ ! -s "$tmp/source-stdout" && ! -s "$tmp/source-stderr" ]] || fail_test 'sourcing produced output'
[[ "$source_journal_before" == "$(sha256sum "$Q17_JOURNAL")" ]] || fail_test 'sourcing changed the journal'

STATE_JOURNAL="$tmp/state.jsonl"
E2E_RESULT_JOURNAL="$STATE_JOURNAL"
export E2E_RESULT_JOURNAL
state_before="$(state_snapshot)"
run_record e2e_result_start scenario test-pack state_case 'Synthetic state check'
expect_rc 0 'state scenario start'
run_record e2e_result_finish scenario test-pack state_case \
  description='Synthetic state check' status=PASS
expect_rc 0 'state scenario finish'
seconds_output="$(e2e_result_seconds_to_ms 1)"
[[ "$seconds_output" == 1000 ]] || fail_test 'state helper output'
state_after="$(state_snapshot)"
[[ "$state_before" == "$state_after" ]] || fail_test 'function call changed caller state'
pass_test 'sourcing and function calls preserve shell state and are silent'

expect_seconds() {
  local label="$1"
  local input="$2"
  local expected="$3"
  local output
  if output="$(e2e_result_seconds_to_ms "$input" 2>"$tmp/seconds-stderr")"; then
    [[ "$output" == "$expected" ]] || fail_test "$label conversion"
  else
    fail_test "$label unexpectedly rejected"
  fi
  [[ ! -s "$tmp/seconds-stderr" ]] || fail_test "$label stderr"
}

expect_invalid_seconds() {
  local label="$1"
  local input="$2"
  local output
  if output="$(e2e_result_seconds_to_ms "$input" 2>"$tmp/seconds-stderr")"; then
    fail_test "$label unexpectedly passed"
  fi
  [[ -z "$output" && ! -s "$tmp/seconds-stderr" ]] || fail_test "$label emitted output"
}

expect_seconds seconds-zero 0 0
expect_seconds seconds-round-down 0.0004 0
expect_seconds seconds-half-up 0.0005 1
expect_seconds seconds-fraction 0.123456 123
expect_seconds seconds-one 1 1000
expect_seconds seconds-round-up 12.3456 12346
expect_seconds half-ms-500500 0.500500 501
expect_seconds half-ms-501500 0.501500 502
expect_seconds half-ms-1000500 1.000500 1001
expect_seconds half-ms-12345500 12.345500 12346
expect_seconds half-ms-999500 0.999500 1000
expect_seconds half-ms-59999500 59.999500 60000
expect_seconds duration-maximum 86400.0004 86400000
expect_seconds fraction-extra-digits 0.1234567 123
expect_seconds leading-zero-integer 007.5 7500
for sweep_whole in 0 1 2 59; do
  for ((sweep_ms = 0; sweep_ms < 1000; sweep_ms++)); do
    printf -v sweep_fraction '%03d' "$sweep_ms"
    sweep_expected=$((sweep_whole * 1000 + sweep_ms + 1))
    expect_seconds "half-ms-sweep-${sweep_whole}-${sweep_fraction}" \
      "${sweep_whole}.${sweep_fraction}500" "$sweep_expected"
  done
done
expect_invalid_seconds seconds-nonnumeric abc
expect_invalid_seconds seconds-empty ''
expect_invalid_seconds seconds-negative -1
expect_invalid_seconds seconds-exponent 1e3
expect_invalid_seconds seconds-leading-dot .5
expect_invalid_seconds seconds-trailing-dot 5.
expect_invalid_seconds seconds-multiple-dots 1.2.3
expect_invalid_seconds seconds-leading-space ' 1'
expect_invalid_seconds seconds-trailing-space '1 '
expect_invalid_seconds seconds-long-integer 1234567890123
pass_test 'seconds-to-milliseconds exact decimal rounding, half-ms sweep, and invalid-input cases'

INERT_JOURNAL="$tmp/inert.jsonl"
mkdir "$tmp/inert-area"
# Child scripts are single-quoted to preserve literal code and positional parameters.
# shellcheck disable=SC2016
if "$BASH" -c '
  set -Eeuo pipefail
  cd "$2"
  # Runtime source path keeps the library usable from mutation scratch copies.
  # shellcheck disable=SC1090
  source "$1"
  unset E2E_RESULT_JOURNAL
  e2e_result_start
  e2e_result_finish
  E2E_RESULT_JOURNAL=
  e2e_result_start unknown - step
  e2e_result_finish invalid - step
' _ "$LIB" "$tmp/inert-area" >"$tmp/inert-stdout" 2>"$tmp/inert-stderr"; then
  [[ ! -e "$INERT_JOURNAL" && ! -e "$INERT_JOURNAL.failed" ]] || fail_test 'inert path created files'
  [[ -z "$(find "$tmp/inert-area" -mindepth 1 -maxdepth 1 -print -quit)" ]] || fail_test 'inert path touched the working directory'
  [[ ! -s "$tmp/inert-stdout" && ! -s "$tmp/inert-stderr" ]] || fail_test 'inert path produced output'
  pass_test 'unset and empty journal paths are inert under strict shell options'
else
  fail_test 'inert calls failed under strict shell options'
fi

ERREXIT_JOURNAL="$tmp/errexit.jsonl"
ERREXIT_CONTINUED="$tmp/errexit-continued"
# The child script is single-quoted to preserve literal code and positional parameters.
# shellcheck disable=SC2016
if "$BASH" -c '
  set -Eeuo pipefail
  trap ":" ERR
  # Runtime source path keeps the library usable from mutation scratch copies.
  # shellcheck disable=SC1090
  source "$1"
  E2E_RESULT_JOURNAL="$2"
  export E2E_RESULT_JOURNAL
  e2e_result_finish scenario test-pack err_case description="Synthetic invalid" status=INVALID || :
  printf "continued\n" >"$3"
' _ "$LIB" "$ERREXIT_JOURNAL" "$ERREXIT_CONTINUED" >"$tmp/errexit-stdout" 2>"$tmp/errexit-stderr"; then
  [[ -s "$ERREXIT_CONTINUED" ]] || fail_test 'errexit continuation'
  [[ ! -s "$tmp/errexit-stdout" && ! -s "$tmp/errexit-stderr" ]] || fail_test 'errexit test output'
  pass_test 'rejected call with || : is safe under errexit and ERR trap'
else
  fail_test 'rejected call terminated strict child shell'
fi

DIRECTORY_JOURNAL="$tmp/journal-directory"
mkdir "$DIRECTORY_JOURNAL"
E2E_RESULT_JOURNAL="$DIRECTORY_JOURNAL"
export E2E_RESULT_JOURNAL
run_record e2e_result_start scenario test-pack append_failure 'Synthetic append failure'
expect_rc 1 'directory append failure'
[[ "$(cat "$DIRECTORY_JOURNAL.failed")" == APPEND_FAILED ]] || fail_test 'append failure marker'
[[ "$(stat -c '%a' "$DIRECTORY_JOURNAL.failed")" == 600 ]] || fail_test 'append marker mode'

RELATIVE_MARKER="$tmp/relative-journal.jsonl.failed"
relative_old_pwd="$PWD"
cd "$tmp"
E2E_RESULT_JOURNAL=relative-journal.jsonl
export E2E_RESULT_JOURNAL
run_record e2e_result_start scenario test-pack relative_path 'Synthetic relative path'
cd "$relative_old_pwd"
expect_rc 1 'relative journal path'
[[ "$(cat "$RELATIVE_MARKER")" == APPEND_FAILED ]] || fail_test 'relative path marker'

MISSING_JQ_BIN="$tmp/no-jq-bin"
mkdir "$MISSING_JQ_BIN"
for utility in date wc chmod
do
  ln -s "$(command -v "$utility")" "$MISSING_JQ_BIN/$utility"
done
MISSING_JQ_JOURNAL="$tmp/missing-jq.jsonl"
old_path="$PATH"
PATH="$MISSING_JQ_BIN"
export PATH
E2E_RESULT_JOURNAL="$MISSING_JQ_JOURNAL"
export E2E_RESULT_JOURNAL
run_record e2e_result_start scenario test-pack no_jq 'Synthetic missing jq'
PATH="$old_path"
export PATH
expect_rc 1 'missing jq'
[[ "$(cat "$MISSING_JQ_JOURNAL.failed")" == APPEND_FAILED ]] || fail_test 'missing jq marker'
[[ ! -s "$tmp/stdout" && ! -s "$tmp/stderr" ]] || fail_test 'missing jq output'

FAILING_JQ_BIN="$tmp/failing-jq-bin"
mkdir "$FAILING_JQ_BIN"
for utility in date wc chmod
do
  ln -s "$(command -v "$utility")" "$FAILING_JQ_BIN/$utility"
done
printf '#!/bin/sh\nexit 17\n' >"$FAILING_JQ_BIN/jq"
chmod 700 "$FAILING_JQ_BIN/jq"
FAILING_JQ_JOURNAL="$tmp/failing-jq.jsonl"
old_path="$PATH"
PATH="$FAILING_JQ_BIN"
export PATH
E2E_RESULT_JOURNAL="$FAILING_JQ_JOURNAL"
export E2E_RESULT_JOURNAL
run_record e2e_result_start scenario test-pack failing_jq 'Synthetic failing jq'
PATH="$old_path"
export PATH
expect_rc 1 'failing jq'
[[ "$(cat "$FAILING_JQ_JOURNAL.failed")" == APPEND_FAILED ]] || fail_test 'failing jq marker'
[[ ! -s "$tmp/stdout" && ! -s "$tmp/stderr" ]] || fail_test 'failing jq output'

MISSING_PARENT_JOURNAL="$tmp/no-such-parent/journal.jsonl"
E2E_RESULT_JOURNAL="$MISSING_PARENT_JOURNAL"
export E2E_RESULT_JOURNAL
run_record e2e_result_start scenario test-pack missing_parent 'Synthetic missing parent'
expect_rc 1 'missing parent append failure'
[[ ! -e "$MISSING_PARENT_JOURNAL" ]] || fail_test 'library created a missing parent'
pass_test 'append failures are silent, marked best-effort, and do not create directories'

MULTI_PROCESS_JOURNAL="$tmp/multi-process.jsonl"
write_in_process() {
  # The child script is single-quoted to preserve literal code and positional parameters.
  # shellcheck disable=SC2016
  "$BASH" -c '
    set -Eeuo pipefail
    # Runtime source path keeps the library usable from mutation scratch copies.
    # shellcheck disable=SC1090
    source "$1"
    E2E_RESULT_JOURNAL="$2"
    export E2E_RESULT_JOURNAL
    e2e_result_start scenario test-pack "$3" "Synthetic process record"
    e2e_result_finish scenario test-pack "$3" description="Synthetic process record" status=PASS
  ' _ "$LIB" "$MULTI_PROCESS_JOURNAL" "$1" >"$tmp/process-stdout" 2>"$tmp/process-stderr"
  [[ ! -s "$tmp/process-stdout" && ! -s "$tmp/process-stderr" ]] || fail_test 'multi-process output'
}
write_in_process process_one
write_in_process process_two
jq -e -s '[.[].seq] == [1, 2, 3, 4]' "$MULTI_PROCESS_JOURNAL" >/dev/null \
  || fail_test 'sequence across separate Bash processes'
pass_test 'sequence continues across separate Bash processes'

[[ "$fail" -eq 0 ]] || exit 1
printf 'PASS post-login result-record library\n'
