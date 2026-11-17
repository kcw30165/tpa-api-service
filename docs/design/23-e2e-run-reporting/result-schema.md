# Proposed Result Schema and Lifecycle

- **PROPOSAL** The objective and compatibility constraints are in [README.md](README.md); milestone acceptance criteria are in [implementation-plan.md](implementation-plan.md).

## 1. Schema v1

- **PROPOSAL** `results.json` uses `schema_version: 1`; the JSON block below is a structural sketch, and the field-set bullets that follow define the exact fields, including lifecycle and scenario fields not shown in the sketch. Unknown fields are rejected by the schema tests until a later version explicitly adds them.
- **PROPOSAL** The JSON block is a schema sketch with placeholders, not an actual or illustrative execution result.
- **REQUIREMENT (R-5, R-6)** The same structured event/result records are the only source for `REPORT.md` and `results.json`; terminal output is never parsed to build either.
- **PROPOSAL** JSON `null` means a value is inapplicable or unavailable; it never substitutes for an observed value.

```json
{
  "schema_version": 1,
  "run": {
    "run_id": "string",
    "started_at_utc": "RFC3339 timestamp",
    "finished_at_utc": "RFC3339 timestamp or null while incomplete",
    "environment": "safe environment label",
    "selected_feature_packs": ["string"],
    "git_head": "full commit SHA",
    "process_exit_code": 0
  },
  "lifecycle": {
    "login": {"outcome": "PASS | FAIL | SKIP | NOT_RUN | INTERRUPTED"},
    "otp": {"outcome": "PASS | FAIL | SKIP | NOT_RUN | INTERRUPTED"},
    "account_selection": {"outcome": "PASS | FAIL | SKIP | NOT_RUN | INTERRUPTED"},
    "logout": {"outcome": "PASS | FAIL | SKIP | NOT_RUN | INTERRUPTED"},
    "cleanup": {"outcome": "PASS | FAIL | SKIP | NOT_RUN | INTERRUPTED"}
  },
  "feature_packs": [
    {
      "name": "string",
      "outcome": "PASS | FAIL | SKIP | NOT_RUN | INTERRUPTED",
      "child_exit_status": "integer or null if never started",
      "reason_code": "fixed code or null",
      "scenario_counts": {
        "pass": 0,
        "fail": 0,
        "skip": 0
      },
      "phases": [
        {"phase_id": "string", "outcome": "PASS | FAIL | SKIP | NOT_RUN | INTERRUPTED"}
      ],
      "scenarios": []
    }
  ],
  "report_errors": [],
  "final_outcome": "PASS | FAIL | SKIP"
}
```

- **PROPOSAL** Lifecycle and phase `outcome` values are exactly `PASS`, `FAIL`, `SKIP`, `NOT_RUN`, or `INTERRUPTED`. `NOT_RUN` means the lifecycle step or phase never started; `INTERRUPTED` means it was active when INT/TERM was handled.
- **PROPOSAL** Each lifecycle value has exactly `outcome`, `expected_http_status`, `actual_http_status`, `expected_public_error_code`, `actual_public_error_code`, `duration_ms`, `request_id`, `echoed_request_id`, `reason_code`, and `evidence_refs`. Non-HTTP steps use `null` for HTTP/code/duration fields.
- **PROPOSAL** A pack has exactly `name`, `outcome`, `child_exit_status`, `reason_code`, `scenario_counts`, `phases`, and `scenarios`. The parent records `child_exit_status`, not the child; it is null if the pack never started. A pack's `outcome` uses the same five-value enum; `scenario_counts` contains exactly `pass`, `fail`, and `skip`, with no `not_run` count. `reason_code` is `INTERRUPTED` for an `INTERRUPTED` pack; `CHILD_EXIT_NONZERO` when the child exit status is nonzero and not 77 and the pack is not `INTERRUPTED`; `REPORT_RECORDS_MISSING` for the Q13 zero-record case; and null otherwise. A pack failing only because a scenario or phase is `FAIL` has null `reason_code`; the scenario or phase record explains the failure.
- **PROPOSAL** Each feature phase has exactly `phase_id`, `outcome`, `expected_http_status`, `actual_http_status`, `expected_public_error_code`, `actual_public_error_code`, `duration_ms`, `request_id`, `echoed_request_id`, `reason_code`, `safe_observations`, and `evidence_refs`. Its `outcome` uses the five-value enum. `safe_observations` is an allow-listed object of booleans/enums only; it is not an output-text field.
- **REQUIREMENT (R-6)** Each scenario has ID and description; status exactly `PASS`, `FAIL`, or `SKIP`; expected/actual HTTP status; expected/actual public error code; duration; request ID; failure/skip reason; and references to redacted evidence.
- **DECISION (Q6, 2026-11-17)** A scenario status is exactly `PASS`, `FAIL`, or `SKIP`. A declared scenario that never started is `SKIP` with reason code `NOT_EXECUTED`; a scenario active at INT/TERM is `FAIL` with reason code `INTERRUPTED`. Scenario `reason_code` is null only on `PASS`. `scenario_counts` has only `pass`, `fail`, and `skip`. Pack, lifecycle, and phase outcomes are exactly `PASS`, `FAIL`, `SKIP`, `NOT_RUN`, or `INTERRUPTED`; `NOT_RUN` means never started and `INTERRUPTED` means active at INT/TERM.
- **DECISION (Q11, 2026-11-17)** "Declared scenario" in Q6 means a scenario that a pack explicitly records as skipped (for example reason FEATURE_DISABLED, PRECONDITION_UNAVAILABLE or NOT_EXECUTED). There is no up-front scenario manifest in R1-R4. A scenario that is never recorded is absent from the report; pack failure is determined by the child exit status (see result-schema.md §1, pack outcome rules). An up-front manifest is a candidate for the R6 JSON specs. Thus unreached scenarios in a crashed pack are not listed. The pack outcome rules also account for recorded scenario outcomes.
- **PROPOSAL** A schema-v1 scenario has exactly `id`, `description`, `status`, `expected_http_status`, `actual_http_status`, `expected_public_error_code`, `actual_public_error_code`, `duration_ms`, `request_id`, `echoed_request_id`, `reason_code`, and `evidence_refs`. The scenario `status` enum is exactly `PASS`, `FAIL`, or `SKIP`; `reason_code` is null only on `PASS` and required on `FAIL` or `SKIP`.
- **PROPOSAL** `reason_code` is a closed enum chosen by the pack or parent as applicable: `ASSERTION_FAILED`, `HTTP_STATUS_MISMATCH`, `PUBLIC_ERROR_CODE_MISMATCH`, `CONTRACT_MISMATCH`, `PRECONDITION_UNAVAILABLE`, `FEATURE_DISABLED`, `INTERRUPTED`, `NOT_EXECUTED`, `REPORT_RECORDS_MISSING`, or `CHILD_EXIT_NONZERO`. It is never copied from stdout/stderr, an exception, a response body, or a caller-provided free-form string.
- **DECISION (approved with Q17, 2026-11-17)** `report_errors` is an array of closed codes: `JSON_WRITE_FAILED`, `MARKDOWN_WRITE_FAILED`, `SCHEMA_VALIDATION_FAILED`, `FINALIZATION_FAILED`, or `JOURNAL_RECORD_FAILED`. The R3 parent uses `JOURNAL_RECORD_FAILED` when it finds a journal failure marker. It contains no exception message, path outside the retained run tree, or captured output.
- **PROPOSAL** An HTTP status is an integer; public error codes and IDs are bounded strings validated against an explicit safe character set. Evidence references are normalized relative paths beneath the run directory only. `null` means a field is unavailable; R1 records `null` wherever current code does not capture it. Capturing a new field is a separate sub-task with characterization tests.
- **PROPOSAL** `request_id` is the generated request ID when it matches the safe character set `[A-Za-z0-9._:-]{1,128}`, otherwise `null`. `echoed_request_id` is recorded only when an echoed ID differs from the generated ID and independently passes the same check; otherwise it is `null`.
- **DECISION (D3, 2026-11-16)** Record the generated request ID, and also the echoed one if it differs.
- **REQUIREMENT (R-6)** A negative HTTP test, including an expected 4xx response, is `PASS` when the expected HTTP status/public error contract matches. An unexecuted scenario is never `PASS`; a scenario explicitly recorded as skipped is `SKIP`. `NOT_RUN` applies to pack/lifecycle/phase outcomes, not scenario status.
- **DECISION (D2, 2026-11-16)** An all-skipped run reports `SKIP` in both `results.json` and `REPORT.md`; the exit code stays 0.
- **PROPOSAL** The parent, not the child, records each child exit status. Pack outcome precedence is `INTERRUPTED` > `FAIL` > `SKIP` > `PASS`; `NOT_RUN` applies only when the pack never started. A started pack is `INTERRUPTED` if active at INT/TERM. It is `FAIL` if the child exit status is nonzero and not 77, any recorded scenario is `FAIL`, any recorded phase has outcome `FAIL`, or the child exited 0 and recorded zero scenarios. It is `SKIP` if the child exit status is 77 or the pack started and every recorded scenario is `SKIP`. It is `PASS` only if the child exited 0, at least one scenario is `PASS`, and no recorded scenario or phase is `FAIL`. Phases do not count toward the at-least-one-scenario-PASS requirement or the Q13 zero-record rule. Scenario counts include only `pass`, `fail`, and `skip`; scenarios never recorded, including unreached scenarios in a crashed pack, are absent.
- **DECISION (Q13, 2026-11-17)** A pack whose child exits 0 but recorded zero scenarios is FAIL with reason_code REPORT_RECORDS_MISSING, not SKIP.
- **DECISION (Q15, 2026-11-17)** In R1 the Vesting Scale hooks record each check that the pack already reports as a scenario record; the phase records for Vesting (get_en, validate_en, get_zh_hk, validate_zh_hk, summary) are added additively in R4.2. A pack's outcome also takes phase outcomes into account: a phase with outcome FAIL makes the pack FAIL. Phases do not count toward "at least one scenario is PASS" or toward the Q13 zero-record rule.
- **PROPOSAL** Overall outcome is `FAIL` if any lifecycle step is `FAIL` or `INTERRUPTED`; any selected pack is `FAIL`, `NOT_RUN`, or `INTERRUPTED`; cleanup or report failure occurs; or a failure-marker or missing-expected-record condition is detected from R3. Overall outcome is `SKIP` when every selected pack is `SKIP` and no lifecycle step is `FAIL` or `INTERRUPTED` (D2: process exit code 0). It is `PASS` otherwise. A reporting failure forces a nonzero process exit.

## 2. `REPORT.md` rendering

- **REQUIREMENT (R-4)** The report includes run ID, safe environment label, selected packs, Git HEAD, final outcome, login, OTP, account selection, each pack's counts and scenario rows, logout, cleanup, and report-generation errors.
- **PROPOSAL** A scenario row renders ID, description, execution result, expected/actual HTTP status, expected/actual public error code, duration, request ID, fixed reason code, and relative redacted evidence references. A skipped or unexecuted row is visibly distinct from an executed result.
- **REQUIREMENT (R-4)** Any illustrative examples are explicitly marked as examples and cannot be confused with actual execution results.
- **PROPOSAL** The report prints only a safe environment label, never a base URL or raw runtime input. It renders report errors by closed code, not exception text.
- **REQUIREMENT (R-7)** Existing useful interactive terminal output remains available; output is not treated as the report source.

## 3. Private journal and pack hooks

- **PROPOSAL** Add a shared `scripts/e2e/lib/result-records.sh` function as the only result-event hook point. It emits one allow-listed structured record per event and is inert when the journal-path variable is absent.
- **CONFIRMED** `assertions.sh` is not a universal outcome hook: CIM and Vesting Scale implement their own result helpers. Source: [P0b §2].
- **PROPOSAL** Journal entries are append-only JSONL produced with `jq`; the private journal resides outside retained evidence, uses restrictive permissions, and contains only already-redacted structured values. Child Bash packs receive only the non-secret journal path via the existing child environment contract.
- **PROPOSAL** Journal format v1 is one closed JSON object per line. Every line has exactly these common fields: integer `journal_version` equal to `1`; positive integer `seq`, assigned by the record function and monotonically increasing within one journal; `event` equal to `start` or `finish`; `record_type` equal to `lifecycle`, `phase`, or `scenario`; `pack` (pack name, or `null` for lifecycle records); and RFC3339 `recorded_at_utc`. Lifecycle lines add `step` (`login`, `otp`, `account_selection`, `logout`, or `cleanup`); phase lines add `phase_id`; scenario lines add `id`.
- **PROPOSAL** A start line contains only the common fields and its record-type identifier; scenario start lines also contain `description`. A finish line contains the common fields, identifier, and exactly the corresponding fields defined in §1: lifecycle and phase records contain `outcome`, expected/actual HTTP status, expected/actual public error code, `duration_ms`, `request_id`, `echoed_request_id`, `reason_code`, and `evidence_refs`; phase records also contain `safe_observations`. Scenario finish records contain `description`, `status`, expected/actual HTTP status, expected/actual public error code, `duration_ms`, `request_id`, `echoed_request_id`, `reason_code`, and `evidence_refs`.
- **PROPOSAL** Closed value sets and safe-character validation from §1 apply to every line. The record function rejects unknown fields and invalid values other than the Q17 safe-string nulling cases without writing them. R1 `evidence_refs` is an empty array unless a pack already has a normalized relative path for the artifact; run-directory-relative normalization is defined in R3. A start without a matching finish identifies the active record at an interrupt. A finish without a start is accepted and flagged by the R3 parent. The record function returns 0 and writes nothing when the journal-path variable is absent, returns 0 on a successful append, returns 2 on input rejection, and returns 1 on append failure.
- **DECISION (Q14, 2026-11-17)** Journal records include start and finish events. R1 hooks emit a start record and a finish record around each recorded scenario and phase, so that from R3 the parent can mark the scenario or phase that was active at INT/TERM as INTERRUPTED without editing the hooks again.
- **DECISION (Q12, 2026-11-17)** On append failure (including missing `jq` or an unwritable journal), the record function returns nonzero and writes a private failure marker beside the journal. Pack hooks ignore the return value, so R1 terminal output, request order, child exit codes, and live mutation lifecycles are not interrupted by reporting failure. From R3, the parent treats a failure marker or a missing expected record as a report failure and sets final outcome to `FAIL`. If the marker itself cannot be written, the parent still detects the problem through the recorded child exit status and Q13's zero-record rule.
- **DECISION (Q17, 2026-11-17)** Journal record validation: request_id, echoed_request_id, expected_public_error_code and actual_public_error_code values that are not bounded safe strings ([A-Za-z0-9._:-]{1,128}) are recorded as null and the record is still written; an echoed_request_id equal to request_id is recorded as null. Every other invalid input causes REJECTION: nothing is written, the function returns 2 and writes a private failure marker. An append failure returns 1 and writes the marker. The function returns 0 on success and when inert. The record functions print NOTHING to stdout or stderr.
- **DECISION (approved with Q17, 2026-11-17)** Names and defaults: journal-path variable E2E_RESULT_JOURNAL (absolute file path); functions e2e_result_start, e2e_result_finish, e2e_result_seconds_to_ms; marker file "<journal path>.failed" containing one closed code per line, APPEND_FAILED or RECORD_REJECTED (never any value); identifiers (pack, id, phase_id) match [A-Za-z0-9._-]{1,64}; description is printable ASCII 0x20-0x7E, 1-200 chars; journal and marker files mode 0600; the library never creates directories; seq is the existing line count plus 1 (sequential packs, no locking); safe_observations is accepted only as an empty object in R1; scenario finish records require description again; e2e_result_seconds_to_ms is owned by R1.1 and R1.2 only uses it; JOURNAL_RECORD_FAILED is added to the closed report_errors list.
- **DECISION (approved with Q17, 2026-11-17)** The functions never change caller shell options, traps, umask or cwd; sourcing the library has no side effects and re-sourcing is idempotent and prints nothing. The functions do not call exit, set, or trap, and any umask/cwd changes are confined to their own subshell. e2e_result_seconds_to_ms prints floor(seconds*1000+0.5) as an integer and rejects non-numeric, empty, negative, and exponent input with a nonzero return and no output.
- **DECISION (D6, 2026-11-16)** R1 is limited to the record library, private journal, pack hooks, and journal tests.
- **DECISION (Q3, 2026-11-17)** R1 adds only the record library and inert hooks; the parent creates the journal in R3.
- **PROPOSAL** Therefore R1 tests the library against a test-owned private journal, while production hooks do nothing unless a parent supplies a journal path. R1 does not add rendering or change the orchestrator.
- **PROPOSAL** R1.2 adds curl's `%{time_total}` to CIM's write-out as its only request-path change. Characterization tests prove existing HTTP-status parsing, response-body handling, and public-error-code parsing remain unchanged. The CIM hook may pass the already-generated local request ID to the record when the hook is in scope of that variable; this is not a request-path change. The echoed ID is `null` in R1 unless a pack already captures response headers.
- **CONFIRMED** The current CIM custom curl captures HTTP status/public error code but not duration; the generic HTTP client captures `%{time_total}`. Source: [P0b §8, §9.1, §9.4].
- **PROPOSAL** Vesting Scale request IDs remain `null` because the current pack sends no explicit `X-Request-Id`; adding that header is deferred. Echoed request IDs also remain `null` in R1 unless the pack already captures response headers. R1.3 scenario records map the checks the pack already reports; R4.2 adds phase records for the actual two GETs and validation workflow.
- **CONFIRMED** Vesting Scale's current helper captures HTTP status only; it does not parse a public error code, record duration, or send an explicit request ID. Source: [P0b §9.2, §9.4].
- **PROPOSAL** Until a separately tested field-capture sub-task, Vesting Scale R1.3 scenario records and R4.2 phase records use `null` for public error code, duration, and request ID; status remains available. Request and echoed request IDs are `null` for this pack in R1.

## 4. Mutation and read-only phase records

- **REQUIREMENT (R-8)** CIM live mutation reporting has first-class records for initial GET, password validation, submit, refreshed GET, cancel authorization, cancel, final GET, and cleanup. Token values and instruction references are never recorded.
- **PROPOSAL** For the live-submit path, use phase IDs `get_1`, `password_validation`, `submit`, `get_2`, `cancel_authorization`, `cancel`, `get_3`, and `cleanup`. `safe_observations` may contain only: `eligibility_met`, `snapshot_summary_code`, `token_issued`, `instruction_reference_present`, `submitted_instruction_found`, `cancel_authorization_token_issued`, `cancel_outcome`, `final_instruction_state`, and `cleanup_state`. `snapshot_summary_code` is a closed pack-defined code representing a redacted summary, not a free-form snapshot. `cleanup_state` is exactly `COMPLETE`, `INCOMPLETE`, or `MANUAL_FOLLOW_UP_REQUIRED`. Values are booleans or closed enums; no snapshot, token, or reference is serialized.
- **CONFIRMED** The live-submit workflow has those ordered operations and no automatic retries; recovery information is held in the configured private recovery location. Source: [P0 §Mutation lifecycles].
- **PROPOSAL** A separate existing-pending cancel workflow, if selected, records its actual initial GET, authorization, cancel, final GET, and cleanup outcomes; it does not pretend to be the new-instruction submit path.
- **REQUIREMENT (R-8)** Vesting Scale reporting must reflect its actual workflow and must not invent password-validation, submit, cancel, or instruction-reference phases.
- **PROPOSAL** Vesting Scale phases are `get_en`, `validate_en`, `get_zh_hk`, `validate_zh_hk`, and `summary`; request phases carry the currently captured HTTP status, while public error code, duration, and request ID are `null` under the current helper. This phase naming is proposed to represent its actual two-language GET-and-validation flow, not a confirmed callback interface.
- **CONFIRMED** Vesting Scale is read-only, issues two language-specific GETs, validates response invariants and cross-language sequence, and writes summary evidence. Source: [P0b §9.2].

## 5. Persistence, aggregation, and interruption

- **PROPOSAL** R2 writes JSON and Markdown through same-directory temporary files, validates the completed output, applies restrictive file permissions, and atomically renames each artifact into place. If atomic publication is unavailable, use best-effort persistence and report the failure explicitly.
- **PROPOSAL** The record library accepts lifecycle/scenario events as execution happens. The report writer reads the structured journal/result set only; it does not scrape terminal output or arbitrary files.
- **PROPOSAL** R3 reserves a run directory atomically and handles collisions without reusing another run's evidence. The exact run-ID format is tracked in [implementation-plan.md §4](implementation-plan.md#4-open-items-for-nick), consistent with Q2.
- **PROPOSAL** R3 finalization attempts logout only when authentication was established, captures cleanup outcome, preserves completed records, marks an active scenario interrupted, renders partial results on failure/INT/TERM, and returns nonzero for an overall failure. SIGKILL and power loss remain outside shell-trap guarantees.
- **PROPOSAL** The normal finalization order is: finish selected packs; attempt logout once when authenticated; clean sensitive runtime state and append the cleanup outcome to the separate private journal; render/publish `results.json` and `REPORT.md`; then remove the temporary journal only after both outputs are published successfully.
- **PROPOSAL** Keep sensitive runtime state and the safe structured journal in separate private locations. Remove the temporary journal only after both outputs are published; keep it, with a sanitized location in the diagnostic, if either fails. Do not claim forensic erasure.
- **REQUIREMENT (R-7)** A report-generation failure is visible and changes the overall outcome; no path may silently return success when required reporting failed.
- **PROPOSAL** If either output fails to publish, set final outcome to `FAIL`, add a closed report-error code, keep the already-redacted restrictive-permission journal, name its sanitized private location in the failure diagnostic, and exit nonzero. No published artifact may state `PASS` when a reporting failure forces a nonzero exit.
- **PROPOSAL** R2 defines a publication order or two-phase approach that cannot leave a stale `PASS` in `results.json` if `REPORT.md` fails to publish, or vice versa, including failure of the second publish. Fake-failure tests exercise each publication ordering and failure of its second publish, proving no published artifact states `PASS` when a reporting failure forces a nonzero exit. The mechanism is undecided; see [implementation-plan.md §4](implementation-plan.md#4-open-items-for-nick).
- **PROPOSAL** The exact evidence-tree/legacy-path reconciliation is tracked in [implementation-plan.md §4](implementation-plan.md#4-open-items-for-nick); R3 must close it before run-directory reservation is implemented.
