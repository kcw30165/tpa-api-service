# Proposed Result Schema and Lifecycle

- **PROPOSAL** The objective and compatibility constraints are in [README.md](README.md); milestone acceptance criteria are in [implementation-plan.md](implementation-plan.md).

## 1. Schema v1

- **PROPOSAL** `results.json` uses `schema_version: 1`; the JSON object below defines the complete top-level, lifecycle, pack, phase, and scenario field sets. Unknown fields are rejected by the schema tests until a later version explicitly adds them.
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
    "login": {},
    "otp": {},
    "account_selection": {},
    "logout": {},
    "cleanup": {}
  },
  "feature_packs": [
    {
      "name": "string",
      "outcome": "PASS | FAIL | SKIP | NOT_RUN | INTERRUPTED",
      "scenario_counts": {
        "pass": 0,
        "fail": 0,
        "skip": 0,
        "not_run": 0
      },
      "phases": [],
      "scenarios": []
    }
  ],
  "report_errors": [],
  "final_outcome": "PASS | FAIL | SKIP"
}
```

- **PROPOSAL** Each lifecycle value has exactly `outcome`, `expected_http_status`, `actual_http_status`, `expected_public_error_code`, `actual_public_error_code`, `duration_ms`, `request_id`, `echoed_request_id`, `reason_code`, and `evidence_refs`. Non-HTTP steps use `null` for HTTP/code/duration fields.
- **PROPOSAL** Each feature phase has exactly `phase_id`, `outcome`, `expected_http_status`, `actual_http_status`, `expected_public_error_code`, `actual_public_error_code`, `duration_ms`, `request_id`, `echoed_request_id`, `reason_code`, `safe_observations`, and `evidence_refs`. `safe_observations` is an allow-listed object of booleans/enums only; it is not an output-text field.
- **REQUIREMENT (R-6)** Each scenario has ID and description; `PASS`, `FAIL`, or `SKIP`; expected/actual HTTP status; expected/actual public error code; duration; request ID; failure/skip reason; and references to redacted evidence.
- **PROPOSAL** A schema-v1 scenario has exactly `id`, `description`, `status`, `expected_http_status`, `actual_http_status`, `expected_public_error_code`, `actual_public_error_code`, `duration_ms`, `request_id`, `echoed_request_id`, `reason_code`, and `evidence_refs`. `reason_code` is `null` on `PASS` and required for `FAIL` or `SKIP`.
- **PROPOSAL** `reason_code` is a closed enum chosen by the pack: `ASSERTION_FAILED`, `HTTP_STATUS_MISMATCH`, `PUBLIC_ERROR_CODE_MISMATCH`, `CONTRACT_MISMATCH`, `PRECONDITION_UNAVAILABLE`, `FEATURE_DISABLED`, `INTERRUPTED`, or `NOT_EXECUTED`. It is never copied from stdout/stderr, an exception, a response body, or a caller-provided free-form string.
- **PROPOSAL** `report_errors` is an array of closed codes: `JSON_WRITE_FAILED`, `MARKDOWN_WRITE_FAILED`, `SCHEMA_VALIDATION_FAILED`, or `FINALIZATION_FAILED`. It contains no exception message, path outside the retained run tree, or captured output.
- **PROPOSAL** An HTTP status is an integer; public error codes and IDs are bounded strings validated against an explicit safe character set. Evidence references are normalized relative paths beneath the run directory only. Invalid values become `null` with an explicit safe reason code; they are never copied raw.
- **PROPOSAL** `request_id` is the generated request ID when it matches the safe character set `[A-Za-z0-9._:-]{1,128}`, otherwise `null`. `echoed_request_id` is recorded only when an echoed ID differs from the generated ID and independently passes the same check; otherwise it is `null`.
- **DECISION (D3, 2026-11-16)** Record the generated request ID, and also the echoed one if it differs.
- **REQUIREMENT (R-6)** A negative HTTP test, including an expected 4xx response, is `PASS` when the expected HTTP status/public error contract matches. A scenario that did not execute is never `PASS`; it is explicitly `SKIP` or `NOT_RUN` in the enclosing aggregate.
- **DECISION (D2, 2026-11-16)** An all-skipped run reports `SKIP` in both `results.json` and `REPORT.md`; the exit code stays 0.
- **PROPOSAL** The all-skipped outcome is represented by `final_outcome: "SKIP"` and `run.process_exit_code: 0`; report-generation or lifecycle failures override it to `FAIL` and a nonzero exit.
- **PROPOSAL** A pack is `FAIL` if any scenario fails; `SKIP` if all executed/declared scenarios are skipped; `PASS` if at least one passes and none fail; and `NOT_RUN` if the pack never started. Overall outcome is `FAIL` for lifecycle, pack, cleanup, or report failure; otherwise it is `SKIP` when all selected pack scenarios are skipped, and `PASS` otherwise. Report-writer failure always forces a nonzero process exit.

## 2. `REPORT.md` rendering

- **REQUIREMENT (R-4)** The report includes run ID, safe environment label, selected packs, Git HEAD, final outcome, login, OTP, account selection, each pack's counts and scenario rows, logout, cleanup, and report-generation errors.
- **PROPOSAL** A scenario row renders ID, description, execution result, expected/actual HTTP status, expected/actual public error code, duration, request ID, fixed reason code, and relative redacted evidence references. A skipped or unexecuted row is visibly distinct from an executed result.
- **REQUIREMENT (R-4)** Any illustrative examples are explicitly marked as examples and cannot be confused with actual execution results.
- **PROPOSAL** The report prints only a safe environment label, never a base URL or raw runtime input. It renders report errors by closed code, not exception text.
- **REQUIREMENT (R-7)** Existing useful interactive terminal output remains available; output is not treated as the report source.

## 3. Private journal and pack hooks

- **PROPOSAL** Add a shared `scripts/e2e/lib/result-records.sh` function as the only result-event hook point. It emits one allow-listed structured record per event and is inert when the journal-path variable is absent.
- **CONFIRMED** `assertions.sh` is not a universal outcome hook: CIM and Vesting Scale implement their own result helpers. Source: `evidence/e2e-refactor-p0b-gap-closure/REPORT.md` §2.
- **PROPOSAL** Journal entries are append-only JSONL produced with `jq`; the private journal resides outside retained evidence, uses restrictive permissions, and contains only already-redacted structured values. Child Bash packs receive only the non-secret journal path via the existing child environment contract.
- **DECISION (D6, 2026-11-16)** R1 is limited to the record library, private journal, pack hooks, and journal tests.
- **DECISION (Q3, 2026-11-17)** R1 adds only the record library and inert hooks; the parent creates the journal in R3.
- **PROPOSAL** Therefore R1 tests the library against a test-owned private journal, while production hooks do nothing unless a parent supplies a journal path. R1 does not add rendering or change the orchestrator.
- **PROPOSAL** CIM hooks capture duration from curl's `%{time_total}` while retaining existing status/body parsing. Characterization tests must prove status, response body, and mutation behavior remain unchanged.
- **CONFIRMED** The current CIM custom curl captures HTTP status but not duration; the generic HTTP client already captures `%{time_total}`. Source: `evidence/e2e-refactor-p0b-gap-closure/REPORT.md` §8 and §9.1.
- **PROPOSAL** Vesting Scale request IDs remain `null` because the current pack sends no explicit `X-Request-Id`; adding that header is deferred. Its phase records reflect the actual two GETs and validation workflow only.
- **CONFIRMED** Vesting Scale currently makes English and `zh-HK` GET requests and does not send an explicit request ID. Source: `evidence/e2e-refactor-p0b-gap-closure/REPORT.md` §9.2.

## 4. Mutation and read-only phase records

- **REQUIREMENT (R-8)** CIM live mutation reporting has first-class records for initial GET, password validation, submit, refreshed GET, cancel authorization, cancel, final GET, and cleanup. Token values and instruction references are never recorded.
- **PROPOSAL** For the live-submit path, use phase IDs `get_1`, `password_validation`, `submit`, `get_2`, `cancel_authorization`, `cancel`, `get_3`, and `cleanup`. `safe_observations` may contain only: `eligibility_met`, `snapshot_summary_code`, `token_issued`, `instruction_reference_present`, `submitted_instruction_found`, `cancel_authorization_token_issued`, `cancel_outcome`, `final_instruction_state`, and `cleanup_state`. `snapshot_summary_code` is a closed pack-defined code representing a redacted summary, not a free-form snapshot. `cleanup_state` is exactly `COMPLETE`, `INCOMPLETE`, or `MANUAL_FOLLOW_UP_REQUIRED`. Values are booleans or closed enums; no snapshot, token, or reference is serialized.
- **CONFIRMED** The live-submit workflow has those ordered operations and no automatic retries; recovery information is held in the configured private recovery location. Source: `evidence/e2e-refactor-p0-investigation/REPORT.md` §Mutation lifecycles.
- **PROPOSAL** A separate existing-pending cancel workflow, if selected, records its actual initial GET, authorization, cancel, final GET, and cleanup outcomes; it does not pretend to be the new-instruction submit path.
- **REQUIREMENT (R-8)** Vesting Scale reporting must reflect its actual workflow and must not invent password-validation, submit, cancel, or instruction-reference phases.
- **PROPOSAL** Vesting Scale phases are `get_en`, `validate_en`, `get_zh_hk`, `validate_zh_hk`, and `summary`; each request phase carries status, duration, request ID (`null` under the current contract), and redacted evidence references. This phase naming is proposed to represent its actual two-language GET-and-validation flow, not a confirmed callback interface.
- **CONFIRMED** Vesting Scale is read-only, issues two language-specific GETs, validates response invariants and cross-language sequence, and writes summary evidence. Source: `evidence/e2e-refactor-p0b-gap-closure/REPORT.md` §9.2.

## 5. Persistence, aggregation, and interruption

- **PROPOSAL** R2 writes JSON and Markdown through same-directory temporary files, validates the completed output, applies restrictive file permissions, and atomically renames each artifact into place. If atomic publication is unavailable, use best-effort persistence and report the failure explicitly.
- **PROPOSAL** The record library accepts lifecycle/scenario events as execution happens. The report writer reads the structured journal/result set only; it does not scrape terminal output or arbitrary files.
- **PROPOSAL** R3 reserves a run directory atomically and handles collisions without reusing another run's evidence. The exact run-ID format remains open until R3, consistent with Q2.
- **PROPOSAL** R3 finalization attempts logout only when authentication was established, captures cleanup outcome, preserves completed records, marks an active scenario interrupted, renders partial results on failure/INT/TERM, and returns nonzero for an overall failure. SIGKILL and power loss remain outside shell-trap guarantees.
- **PROPOSAL** The normal finalization order is: finish selected packs; attempt logout once when authenticated; clean sensitive runtime state and append the cleanup outcome to the separate private journal; render/publish `results.json` and `REPORT.md`; then remove temporary journal state. This keeps the cleanup result reportable without retaining authentication state in evidence.
- **PROPOSAL** Keep sensitive runtime state and the safe structured journal in separate private locations. Remove temporary journal state after finalization; do not claim forensic erasure.
- **REQUIREMENT (R-7)** A report-generation failure is visible and changes the overall outcome; no path may silently return success when required reporting failed.
- **PROPOSAL** On writer failure, set final outcome to `FAIL`, add a closed report-error code to the shared result set, attempt to persist both outputs again, emit a sanitized failure diagnostic, and exit nonzero even if one output cannot be recovered.
- **OPEN** The required exact evidence tree and legacy artifact-path compatibility remain unresolved as recorded in `README.md` §4; R3 must close this before run-directory reservation is implemented.
