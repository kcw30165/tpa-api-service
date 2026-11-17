# Milestones, Gates, Decisions, and Open Items

- **PROPOSAL** Scope and current-state evidence are in [README.md](README.md); schema and journal contracts are in [result-schema.md](result-schema.md).

## 1. Milestones

### R1 — structured result collection

- **DECISION (D6, 2026-11-16)** R1 is limited to the record library, private journal, pack hooks, and journal tests.
- **DECISION (Q3, 2026-11-17)** R1 adds only the record library and inert hooks; the parent creates the journal in R3.
- **PROPOSAL — R1.1 record library and journal tests.** Expected changes: new `scripts/e2e/lib/result-records.sh` and proposed `scripts/e2e/tests/post-login-results-test.sh`. Acceptance: fail-first tests prove valid PASS/FAIL/SKIP records, fixed fields/reason codes, private test-journal append, and no sentinel secret/reference serialization. This test-owned journal does not require the parent to create a production journal.
- **PROPOSAL — R1.2 inert pack hooks.** Expected changes: narrowly scoped hooks in the existing `scripts/e2e/investment-choice-scenarios.sh` and `scripts/e2e/vesting-scale-scenarios.sh`, plus focused offline fake tests if needed. Acceptance: unset journal path is a no-op; an explicitly supplied test journal receives structured records at existing result points; HTTP order, terminal output, and guards remain unchanged.
- **PROPOSAL** Explicitly out of scope: rendering, result aggregation in the parent, journal creation by the parent, run-ID reservation, terminal-summary changes, directory movement, and live E2E.
- **DECISION (Q3, 2026-11-17)** The parent does not create the journal until R3.

### R2 — automatic reports and redaction corrections

- **PROPOSAL — R2.1 schema and report writer.** Expected changes: new `scripts/e2e/lib/report-writer.sh` and schema/writer tests under `scripts/e2e/tests/`. Acceptance: strict schema-v1 tests; both outputs render from one structured result set; report examples are distinguished from actual results; fake write failures prove atomic/best-effort behavior and failure outcome.
- **PROPOSAL — R2.2 URL/reference redaction.** Expected changes: only necessary edits to existing `scripts/e2e/lib/redaction.sh`, `scripts/e2e/lib/http-client.sh`, and/or `scripts/e2e/run-post-login.sh`, with regression tests under `scripts/e2e/tests/`. Acceptance: synthetic URL credentials/query secrets and instruction-reference fields are absent from retained artifacts and diagnostics. Confirmed risk locations: `evidence/e2e-refactor-p0-investigation/REPORT.md` §Redaction risks; `evidence/e2e-refactor-p0b-gap-closure/REPORT.md` §9.4.
- **DECISION (D5, 2026-11-16)** URL and `referenceNo` redaction gaps are fixed in R2 as a separate sub-task.
- **PROPOSAL** Acceptance: schema-v1 tests assert required keys, types, enums, and rejection of unknown keys; both reports are rendered from the same structured records; atomic publication succeeds under fake filesystem failure conditions; partial output remains useful; writer failure changes overall result and exit status; output contains no sentinel secrets, raw URLs, or references.
- **REQUIREMENT (R-2, R-5)** Adding a conforming feature pack must not require changes to the report writer or schema implementation.
- **PROPOSAL** Explicitly out of scope: parent orchestration changes, directory reservation/renaming, request-order changes, and live mutation.

### R3 — orchestrator integration and finalization

- **PROPOSAL — R3.1 parent journal and run reservation.** Expected changes: `scripts/e2e/run-post-login.sh`, `scripts/e2e/lib/evidence.sh`, and journal/reservation tests under `scripts/e2e/tests/`. Acceptance: parent creates the journal; each execution gets an exclusive run directory; collisions never reuse another run's evidence; the name/path mapping is approved before implementation.
- **PROPOSAL — R3.2 lifecycle aggregation and finalization.** Expected changes: `scripts/e2e/lib/post-login-orchestrator.sh`, `scripts/e2e/lib/authenticated-login-journey.sh`, `scripts/e2e/run-post-login.sh`, and fake lifecycle/offline tests. Acceptance: login, OTP, account selection, every pack, logout, and cleanup are recorded; children receive only the non-secret journal path addition; packs aggregate in order; failures and INT/TERM preserve partial results and cannot become `PASS`.
- **PROPOSAL** Both R3 sub-tasks must preserve legacy paths and terminal output after the evidence-layout conflict is resolved. Source anchors: `evidence/e2e-refactor-p0-investigation/REPORT.md` §Dependency map and §Current evidence behavior; `evidence/e2e-refactor-p0b-gap-closure/REPORT.md` §3 and §4.
- **DECISION (Q2, 2026-11-17)** Evidence-directory collision handling is deferred to R3; the name format is unchanged until then.
- **OPEN** The run-ID format and reconciliation of new scenario directories with legacy evidence paths must be approved before R3 coding.
- **PROPOSAL** Explicitly out of scope: feature behavior changes, extra HTTP calls, request-ID header changes for Vesting Scale, and directory moves reserved for R5.

### R4 — live mutation and read-only phase evidence

- **REQUIREMENT (R-8)** CIM reports all actual live-submit phases and cleanup state while omitting token values and raw instruction references. Vesting Scale reports only its real read-only request/validation phases.
- **PROPOSAL — R4.1 CIM lifecycle records.** Expected changes: focused hooks in `scripts/e2e/investment-choice-scenarios.sh`, its fake-curl tests, and report-schema assertions. Acceptance: offline tests prove each live-submit phase, status/duration/reason, redacted evidence reference, token-issued booleans, instruction-reference-presence boolean, final state, and cleanup outcome; authorization gates, confirmations, one-attempt/no-retry behavior, and private recovery remain unchanged.
- **PROPOSAL — R4.2 Vesting Scale phase records.** Expected changes: focused hooks in `scripts/e2e/vesting-scale-scenarios.sh`, its fake-curl tests, and report-schema assertions. Acceptance: records correspond to the existing two GETs and validation only, with request ID null; no login/OTP, password, submit, cancel, or instruction-reference phases are introduced.
- **PROPOSAL** Explicitly out of scope: relaxing guards, automatic submit/cancel retries, invented Vesting phases, or real E2E without Nick's explicit authorization.

### R5 — behavior-preserving directory restructuring

- **PROPOSAL — R5.1 wrapper-path characterization.** Expected changes only if approved: existing `scripts/e2e/run-account-balance-regression.sh`, `scripts/e2e/run-account-balance-local-stubbed.sh`, the implicated scenario/helper paths, and focused offline tests. Acceptance: characterize the Phase 0b path mismatches before changing them; prove wrapper target and behavior before/after. Source: `evidence/e2e-refactor-p0b-gap-closure/REPORT.md` §2.
- **PROPOSAL — R5.2 approved path migration.** Expected changes only after R5.1 and explicit approval: `scripts/e2e/scenarios/account-summary/list-authenticated.sh`, `scripts/e2e/scenarios/account-summary/local-stubbed/assertions.sh`, and implicated wrapper references if the approved mapping requires them. Acceptance: all callers resolve, compatibility is retained or migration explicitly approved, reporting remains stable, and independent review/Nick approval are recorded.
- **REQUIREMENT (R-9)** Do not restructure directories during R1-R4; R5 requires behavior/compatibility preservation or an explicitly approved migration.
- **PROPOSAL** Explicitly out of scope: unrelated path cleanup and feature behavior changes.

### R6 — data-driven JSON scenario specification roadmap

- **DECISION (D8, 2026-11-16)** R6 (data-driven JSON scenario specs) is on the roadmap after R4, pilot on Vesting Scale only.
- **PROPOSAL — R6.1 spec design.** Expected changes: a design/evaluation document and test fixtures only; no production scenario file is changed. Acceptance: map the Phase 0b `DECLARATIVE` / `NEEDS_HOOK` / `CODE_ONLY` inventory to a proposed JSON grammar and identify which checks are not safely declarative. Source: `evidence/e2e-refactor-p0b-gap-closure/REPORT.md` §9.
- **PROPOSAL — R6.2 read-only Vesting pilot evaluation.** Expected changes: pilot fixture and offline fake-driven evaluation tests only after design approval. Acceptance: validate schema/security limits and report the subset that can run declaratively; no live call, mutation, or production implementation before approval.
- **OPEN** The JSON spec grammar, ownership of dynamic assertions, and the exact pilot subset are not decided by this pack.

## 2. Gates

- **PROPOSAL** Per sub-task: run fail-first or characterization tests offline; record expected red and green results in a unique evidence folder; run the full 16-script offline E2E suite; run ShellCheck and compare against baseline with no new findings; perform a diff-scope check; include a `REPORT.md`.
- **PROPOSAL** Per milestone: obtain independent review in a separate session and Nick's approval before advancing. Record review, approval, implementation, test, commit, and merge as separate states.
- **REQUIREMENT (R-10)** Run offline tests before any authorized live E2E; never trigger submit/cancel without Nick's explicit authorization and safety checks.
- **DECISION (Q5, 2026-11-17)** No Maven, JaCoCo, smoke-test, or Sonar gates apply to shell-only milestones. Java gates apply only to a milestone that touches Java.
- **PROPOSAL** Any live E2E, especially mutation-enabled CIM, is a separately authorized operation, not a routine acceptance command.
- **REQUIREMENT (R-12)** Use the specified Java 21/Maven 3.9.15 environment only if a command requires it; use `mvn`, not `mvnw`, and the specified `python` command name if Python is needed. No shell aliases.

## 3. Decision log

### Decisions dated 2026-11-16

- **DECISION (D1, 2026-11-16)** “D1 comma support for E2E_FEATURE_PACKS as a separate fail-first sub-task before R1. DONE and MERGED to dev as its own MR. Nick supplied a git log screenshot showing merge commit a73dcf68 on origin/dev merging refactor/e2e-script-structure, with the two original commits 4bf7fef4f9849895381135bfa0e834285978f3e3 and 19b0931a906d358557198ed667570e5e4ce8efd5 preserved beneath it. Verify this on origin/dev (git log --first-parent and git merge-base --is-ancestor for both SHAs) and record the full merge SHA and date as CONFIRMED; if it cannot be verified, record it as UNVERIFIED and continue.”
- **CONFIRMED** The verified merge is `a73dcf6087c5f1205858a4d08cf7d45712f64c3e`, dated 2026-11-17; both original D1 SHAs are ancestors of `origin/dev`. Source: `target/evidence/e2e-run-reporting-design-20261117T041558Z/REPORT.md` §Anchors.
- **DECISION (D2, 2026-11-16)** “D2 an all-skipped run reports SKIP in results.json/REPORT.md; the exit code stays 0.”
- **DECISION (D3, 2026-11-16)** “D3 record the generated request ID, and also the echoed one if it differs.”
- **DECISION (D4, 2026-11-16)** “D4 new files may be added to the evidence directory; no existing artifact is renamed.”
- **DECISION (D5, 2026-11-16)** “D5 URL and referenceNo redaction gaps are fixed in R2 as a separate sub-task.”
- **DECISION (D6, 2026-11-16)** “D6 R1 is limited to the record library, private journal, pack hooks, journal tests.”
- **DECISION (D7, 2026-11-16)** “D7 uncommitted changes in the cim-pending-state-diagnostic worktree are stale; ignore them (do not read or touch that worktree).”
- **DECISION (D8, 2026-11-16)** “D8 R6 (data-driven JSON scenario specs) is on the roadmap after R4, pilot on Vesting Scale only.”

### Decisions dated 2026-11-17

- **DECISION (Q2, 2026-11-17)** “Q2 evidence-directory collision handling is deferred to R3; the name format is unchanged until then.”
- **DECISION (Q3, 2026-11-17)** “Q3 R1 adds only the record library and inert hooks; the parent creates the journal in R3.”
- **DECISION (Q4, 2026-11-17)** “Q4 D1 goes to dev as its own MR; R1 starts on a new branch.”
- **DECISION (Q5, 2026-11-17)** “Q5 no Java milestone gates (no Maven test, JaCoCo, smoke test or Sonar gates for shell-only milestones).”
- **DECISION (V1, 2026-11-17)** “V1 the Vesting Scale MR is merged to dev (stated by Nick on 2026-11-17; the MR platform state is not independently verified). Phase 0b found both Vesting Scale branch HEADs to be ancestors of origin/dev in git. R1 hooks may therefore edit the Vesting Scale pack on the new branch cut from origin/dev. Record the merge commits from Phase 0b (6067be2f for the BFF branch, 7cb131f0 for the E2E branch) as CONFIRMED only if re-verified on origin/dev.”
- **CONFIRMED** The BFF and E2E merge commits are `6067be2fe97cda21bc61368e0ee1997e480d3f8c` and `7cb131f0d47618dab36ac7ab0b47e186f5df102a`; both are ancestors of the anchored `origin/dev`. Source: `target/evidence/e2e-run-reporting-design-20261117T041558Z/REPORT.md` §Anchors.

## 4. Open items for Nick

- **OPEN** Approve how the required `target/evidence/<run-id>/scenarios/...` structure coexists with the existing timestamp/pack/scenario paths and the no-rename/no-move compatibility requirements before R3; see [README.md §4](README.md#4-evidence-contract-and-unresolved-layout).
- **OPEN** Decide the exact unique run-ID format and collision-reservation behavior in R3; Q2 defers both until that milestone.
- **OPEN** Confirm whether CI use exists outside the seven Jenkins/CI candidate files searched in Phase 0b. The bounded in-repository result is not a global CI inventory.
- **OPEN** Clarify whether “Python only in one helper” means test dependencies or all scripts; Phase 0b identified Python usage in both a password-validation script and a local-stubbed assertion helper.
- **OPEN** Confirm host-level handling expectations for the suspected direct-curl argv exposure before broadening scope beyond the R6 reassessment.
- **OPEN** Confirm the R6 JSON grammar, dynamic-assertion boundary, and pilot scenario subset after R4 design/evaluation.
- **OPEN** V1's Git ancestry and merge commits are verified; the MR platform state, approval state, and any newer MR status remain unverified.
- **OPEN** No other user decision is assumed beyond requirements and decisions recorded in this pack.
