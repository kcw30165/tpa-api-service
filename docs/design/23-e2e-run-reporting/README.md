# Post-login E2E Run Reporting

## Status and navigation

- **PROPOSAL** This pack designs a shell-only post-login E2E reporting refactor; it does not authorize implementation of R1-R6.
- **PROPOSAL** [result-schema.md](result-schema.md) defines the proposed versioned output and journal contract.
- **PROPOSAL** [implementation-plan.md](implementation-plan.md) defines milestones, gates, decisions, and open items.

## 1. Overview and requirements

- **REQUIREMENT (R-1)** Refactor the post-login shell E2E framework to make comprehensive, automatic per-run reporting mandatory for the framework, then restructure scripts without changing behavior. Reporting is the first milestone; existing terminal output remains useful.
- **REQUIREMENT (R-2)** Reporting is required for every E2E run and future feature pack, including Vesting Scale. Multiple packs must be supported without reporting-framework changes.
- **REQUIREMENT (R-3)** Every run creates the following unique directory tree; retained evidence contains no private secrets. Private runtime state remains outside retained evidence and is securely cleaned if used. Secrets must not leak into terminal logs, JSON, Markdown, scenario attachments, or failure diagnostics.

  ```text
  target/
    evidence/
      <run-id>/
        REPORT.md
        results.json
        scenarios/
          login/
          investment-choice/
          vesting-scale/  (when selected)
          logout/
  ```
- **REQUIREMENT (R-4)** `REPORT.md` is human-readable and contains run ID, environment, selected packs, Git HEAD, overall outcome, login/OTP/account-selection results, per-feature scenario counts and rows, logout, cleanup, and report-generation errors. Each scenario row has ID, result, HTTP status, public error code, and relevant reason. It distinguishes actual execution results from examples.
- **REQUIREMENT (R-5)** `results.json` is machine-readable, usable by Jenkins without terminal parsing, and built from the same structured event/result records as `REPORT.md`. It contains run metadata, lifecycle results, feature-pack aggregates, scenario records, final outcome, and a documented versioned schema.
- **REQUIREMENT (R-6)** Each scenario record contains ID and description; status exactly `PASS`, `FAIL`, or `SKIP`; expected and actual HTTP status; expected and actual public error code; duration; request ID for backend-log correlation; failure/skip reason; and references to redacted evidence. A scenario the pack explicitly records as skipped has status `SKIP`; one active at INT/TERM has status `FAIL` with `reason_code=INTERRUPTED`; `reason_code` is null only on `PASS`. An expected non-2xx response can pass when its expected contract matches. An unexecuted scenario is never `PASS`; skips are explicit.
- **DECISION (Q6, 2026-11-17)** A scenario status is exactly `PASS`, `FAIL`, or `SKIP`. A declared scenario that never started is `SKIP` with reason code `NOT_EXECUTED`; a scenario active at INT/TERM is `FAIL` with reason code `INTERRUPTED`. Scenario `reason_code` is null only on `PASS`. `scenario_counts` has only `pass`, `fail`, and `skip`. Pack, lifecycle, and phase outcomes are exactly `PASS`, `FAIL`, `SKIP`, `NOT_RUN`, or `INTERRUPTED`; `NOT_RUN` means never started and `INTERRUPTED` means active at INT/TERM.
- **DECISION (Q11 clarification, 2026-11-17)** Under Q11, "declared scenario" means a scenario that a pack explicitly records as skipped, for example reason `FEATURE_DISABLED`, `PRECONDITION_UNAVAILABLE`, or `NOT_EXECUTED`. There is no up-front scenario manifest in R1-R4. A scenario that is never recorded is absent from the report, including an unreached scenario in a crashed pack; an up-front manifest is a candidate for R6 JSON specs. Pack outcome is determined from the child exit status recorded by the parent and recorded scenario outcomes using the precedence defined in `result-schema.md` §1.
- **REQUIREMENT (R-7)** Record results as scenarios execute, not by parsing terminal output. Preserve partial evidence on failure, interruption, and best-effort signal cleanup. Login, OTP, account selection, logout, and cleanup are first-class outcomes. Report-generation failure is visible and affects overall outcome; never silently claim `PASS`. Keep credentials, cookies, CSRF/authorization tokens, and raw account/instruction references out of retained evidence. Sensitive recovery references require an approved private mechanism. Preserve interactive terminal output and mutation-safety guards.
- **REQUIREMENT (R-8)** Live CIM reporting covers GET #1 eligibility and redacted snapshot summary; password validation with token-issued yes/no only; submit outcome and instruction-reference-presence only; GET #2 refreshed-snapshot lookup; cancel authorization with token-issued yes/no only; cancel outcome; GET #3 final instruction state; and cleanup as complete, incomplete, or manual follow-up required. Vesting Scale gets phase-level reporting based on its actual workflow, without invented CIM-like steps. Recovery visibility remains while sensitive values are redacted.
- **REQUIREMENT (R-9)** Approved milestone order: R1 structured result collection and characterization tests; R2 automatic `REPORT.md`/`results.json` from one source of truth, atomic/best-effort persistence, schema tests, partial-result behavior, and reporting-failure handling; R3 orchestrator integration, login/OTP/account selection/packs/logout/cleanup, multi-pack aggregation, and interruption handling; R4 live CIM and Vesting Scale phase evidence, recovery, redaction, and preserved mutation authorization; R5 directory restructuring only after reporting is stable, preserving behavior/compatibility or following an explicitly approved migration. Do not move directories in R1-R4 for aesthetics; do not make unrelated feature changes.
- **REQUIREMENT (R-10)** Work incrementally with fail-first/characterization tests, bounded diffs, a separate unique evidence folder per prompt, and independent gate reviews. Run offline tests before any authorized live E2E. Do not trigger submit/cancel mutations without Nick's explicit authorization and safety checks. Keep Git auditable: no rebase, force-push, or merge without explicit approval. Distinguish implemented, tested, reviewed, approved, committed, and merged states.
- **REQUIREMENT (R-11)** Preserve existing working E2E behavior and terminal experience.
- **REQUIREMENT (R-12)** Use branch names `feature/<topic>`, `fix/<topic>`, or `refactor/<topic>`; put design documents under `docs/design/<n>-<feature>/` on the implementation branch and update them if design changes. Use Conventional Commit subjects. If a command needs Java/Maven, use `JAVA_HOME=/home/nd2887/tools/jdk-21.0.12.1+1`, `M2_HOME=/home/nd2887/tools/apache-maven`, and an updated `PATH`; use `mvn` rather than `mvnw`, `python` rather than `python3`, and no shell aliases.

### Scope and non-goals

- **REQUIREMENT (R-1, R-2, R-9, R-11)** Reporting is the first deliverable; behavior-preserving restructuring follows after reporting is stable. The contract applies across selected and future packs.
- **PROPOSAL** R1-R4 are bounded to the existing shell E2E framework and its offline tests; API/BFF behavior, endpoints, and unrelated features are outside scope.
- **REQUIREMENT (R-9)** Directory movement is reserved for R5; no directory move is permitted in R1-R4 merely for aesthetic consistency.
- **DECISION (Q5, 2026-11-17)** There are no Java milestone gates for shell-only milestones; Java gates apply again only to a milestone that touches Java.

## 2. Current state

- **CONFIRMED** The post-login entry point sources runtime, assertions, evidence, redaction, private-state, authenticated-login, registry, and orchestrator libraries; it validates pack selection before creating run state or starting login. Source: [P0 §Dependency map].
- **CONFIRMED** The orchestrator owns shared login/logout and sequential child-pack execution, continues after pack failures, maps child status 77 to `SKIP`, and reports lifecycle/feature summaries. Source: [P0 §Dependency map] [P0b §3].
- **CONFIRMED** The registry is an ordered fixed allow-list for `investment-choice` and `vesting-scale`; it rejects empty, duplicate, unavailable, or unknown entries before login. D1 subsequently added comma-plus-whitespace separators and rejects leading, trailing, and doubled commas before pack resolution. Sources: [P0 §Dependency map, §Env/CLI contract] [D1 §Change, §Verification]. The merge is verifiable with `git log --first-parent origin/dev` and `git merge-base --is-ancestor <commit> origin/dev`.
- **CONFIRMED** Shared runtime defaults include environment label `local`, anonymous bootstrap, 5-second connect timeout, 20-second request timeout, TLS verification enabled, `ACCEPT_LANGUAGE=en`, `REQUEST_ID_PREFIX=bff-e2e`, and external-effects/mutation defaults of `false`. Selector, password, OTP, and live-mutation gates are separate; CIM submit/cancel uses its own exact confirmation and recovery protections. Source: [P0 §Env/CLI contract].
- **CONFIRMED** Existing retained artifacts currently use a local-time timestamp/pack/scenario naming scheme, mode 0700, and `mkdir -p`; there is no unique run ID or collision refusal. Existing numbered lifecycle and redacted response artifacts are produced, but no post-login `results.json` or Markdown report exists. Source: [P0 §Current evidence behavior].
- **CONFIRMED** CIM emits scenario outcomes to the terminal and keeps temporary response/cookie/reference files out of ordinary retained evidence. The top-level post-login summary is aggregate-only. Field availability is summarized in the matrix below. Sources: [P0 §Current evidence behavior] [P0b §9.1, §9.2, §9.4].
- **CONFIRMED** A post-login pack is a child Bash script receiving authenticated context and returning process status; 0 means success, 77 means skip, and another nonzero status means failure. Packs do not own shared login/logout. `assertions.sh` is not a universal hook: CIM and Vesting Scale have pack-specific result helpers. Sources: [P0 §Pack interface] [P0b §2, §3].
- **CONFIRMED** The child invocation is not `env -i`: the runner unexports a named sensitive-variable set, explicitly supplies authenticated context values, and other already-exported variables remain inherited. A non-secret journal-path variable can be passed explicitly. Source: [P0b §3].
- **CONFIRMED** Normal orchestration runs login, selected packs, logout, then private-state cleanup. The existing EXIT/INT/TERM handler performs cleanup but does not record an interrupted outcome or finalize partial results; SIGKILL/power loss cannot be handled by a shell trap. Source: [P0b §4].
- **CONFIRMED** Current redaction gaps include unredacted configured/request URLs and JSON fields such as `referenceNo` that are not in the current redaction key list. Phase 0b classified some direct-curl argv exposure as suspected and host-dependent, not a confirmed exploit. Sources: [P0 §Redaction risks] [P0b §9.4].
- **CONFIRMED** Phase 0b recorded jq 1.8.1 as user-supplied WSL availability, and jq is already used throughout `scripts/e2e`. It found Python at eight source lines in two locations: `scripts/e2e/password-validation-scenarios.sh` and `scripts/e2e/scenarios/account-summary/local-stubbed/assertions.sh`. Source: [P0b §1].
- **REQUIREMENT (R-12 clarification)** New E2E framework code must not use Python.
- **CONFIRMED** Sixteen offline Bash test scripts existed and were reported passing individually in the Phase 0 and D1 test-fixup evidence. There was no tracked aggregate runner reference in the bounded search; fake curl is used where relevant, and the tests do not require live credentials, login, BFF access, or external network. Sources: [P0 §Baseline tests] [D1-fixup §Baseline, §Verification].
- **CONFIRMED** The Phase 0b CI search covered seven Jenkins/CI candidate files and found no `run-post-login` or `scripts/e2e` reference. This bounded search does not establish whether external or differently named CI invokes the scripts. Source: [P0b §1].
- **CONFIRMED** CIM live submit is a non-retrying workflow from an initial authenticated GET through token issuance, one submit, refreshed GET, cancel-token issuance, one cancel, and final GET; uncertain cleanup uses a private recovery record. Vesting Scale is read-only and performs two GETs, English then `zh-HK`, without CIM submit/cancel phases. Sources: [P0 §Mutation lifecycles] [P0b §9.1, §9.2].
- **CONFIRMED** D1's merge is `a73dcf6087c5f1205858a4d08cf7d45712f64c3e`, dated 2026-11-17; both original D1 commits are ancestors of `origin/dev`. The Vesting Scale merge commits `6067be2fe97cda21bc61368e0ee1997e480d3f8c` and `7cb131f0d47618dab36ac7ab0b47e186f5df102a` are also ancestors. Sources: `git log --first-parent origin/dev`; `git merge-base --is-ancestor <commit> origin/dev`.

### Per-pack field availability at the Phase 0/0b baseline

| Component | HTTP status | Public error code | Duration | Request ID | Echoed request ID | Source |
|---|---|---|---|---|---|---|
| **CONFIRMED — generic HTTP client** | Captured in `E2E_HTTP_STATUS` | Not parsed by the generic client; callers may assert codes | Captured in `E2E_ELAPSED_SECONDS` | Generated and captured in request state/evidence | **UNVERIFIED** Whether the echoed `X-Request-Id` is captured; Phase 0 confirms redacted response headers are saved but does not state whether that header is read | [P0 §Current evidence behavior] [P0b §8, §9.4] |
| **CONFIRMED — CIM custom curl** | Captured | Extracted from `.errors[0].code` for assertions/results | Not captured before R1.2 | Generated/sent in a local variable; Q10 permits the R1 hook to pass it to the record | **UNVERIFIED** Whether an echoed ID is captured in response headers | [P0 §Current evidence behavior] [P0b §9.1, §9.4] |
| **CONFIRMED — Vesting Scale** | Captured | Not parsed | Not captured | Not generated/sent | **UNVERIFIED** Whether an echoed ID is captured; the pack sends no explicit `X-Request-Id` | [P0b §9.2, §9.4] |

- **REQUIREMENT (R-6 clarification)** `null` means unavailable. R1 records `null` where the current code does not capture a field; capturing a new field is a separate sub-task with characterization tests.

## 3. Compatibility contract

- **REQUIREMENT (R-11, R-9)** Through R1-R4, preserve working behavior, terminal experience, flags/environment variables, exit semantics (including 77 = `SKIP`), existing evidence names/paths, request ordering, and mutation guards.
- **DECISION (D4, 2026-11-16)** New evidence files may be added; no existing artifact is renamed.
- **CONFIRMED** The current runtime uses an ordered allow-list, supports both commas and whitespace after D1, rejects malformed comma lists, and has retained its duplicate/unknown-name behavior. Sources: [D1 §Change, §Verification] [D1-fixup §Fix A mutation evidence].
- **PROPOSAL** Preserve current terminal summary lines and add only a sanitized, explicit failure diagnostic when report persistence fails; never derive records from terminal text.
- **REQUIREMENT (R-7)** Keep interactive OTP acquisition and all current external-effect, authorization, exact-confirmation, and mutation-safety gates intact.

## 4. Evidence contract and unresolved layout

- **REQUIREMENT (R-3)** The required per-run tree is shown in §1; its compatibility mapping is unresolved below.
- **REQUIREMENT (R-4, R-5)** Human and machine outputs include the required run metadata, lifecycle outcomes, pack aggregates, scenario rows, and final outcome; both are rendered from the same structured records.
- **CONFIRMED** Existing retained artifacts use timestamp/pack/scenario directory names and numbered artifacts; there is no separate run ID. Source: [P0 §Current evidence behavior].
- **DECISION (D4, 2026-11-16)** Additional files are allowed, but existing artifacts are not renamed.
- **PROPOSAL** The evidence-layout compatibility conflict is tracked in [implementation-plan.md §4](implementation-plan.md#4-open-items-for-nick); do not choose a mapping or move/rename artifacts before Nick decides.
- **DECISION (Q2, 2026-11-17)** Evidence-directory collision handling is deferred to R3; the name format is unchanged until then.

## 5. Redaction and security boundary

- **REQUIREMENT (R-3, R-7, R-8)** No password, OTP, cookie, CSRF/authorization token, raw account reference, raw instruction reference, or secret-bearing URL may appear in retained evidence, terminal output, JSON, Markdown, attachments, or diagnostics.
- **DECISION (D5, 2026-11-16)** URL and `referenceNo` redaction gaps are fixed in R2 as a separate sub-task.
- **PROPOSAL** Store only allow-listed, normalized fields in the private journal; never store raw output, raw response bodies, raw URLs, arbitrary assertion text, or recovery references. Evidence references are relative paths to retained redacted artifacts.
- **PROPOSAL** A sensitive recovery reference remains in an approved private recovery mechanism only. Report only boolean presence and lifecycle outcome; do not serialize the reference itself.
- **CONFIRMED** Phase 0b classified some direct-curl argv exposure as suspected and host-dependent, not a confirmed exploit. R6 reassesses the exact call sites; see [implementation-plan.md §4](implementation-plan.md#4-open-items-for-nick). Source: [P0b §9.4].
- **CONFIRMED** The existing private-state helper attempts restrictive-permission cleanup, but filesystem-level forensic erasure is unverified and must not be promised. Sources: [P0 §Redaction risks] [P0b §4, §9.4].

## 6. Source packs and evidence sources

- **CONFIRMED** The post-login orchestration design pack at `docs/design/21-post-login-e2e-orchestrator/` uses a README, architecture, and implementation plan. Source: `origin/dev` files `docs/design/21-post-login-e2e-orchestrator/README.md:1-19`, `architecture.md:1-52`, and `implementation-plan.md:1-35`.
- **CONFIRMED** The Vesting Scale design pack at `docs/design/22-basis-of-contribution-vesting-scale/` uses a design-pack index and numbered solution, contract, test, implementation, and decision documents. Source: `origin/dev` files `0-vesting-scale-design-pack-index.md:1-16` and `01-solution-design.md:1-52`.
- **CONFIRMED** `docs/design/` has numbered pack directories but no root index file in the inspected `origin/dev` tree; no separate index is added. Source: `git ls-tree -d --name-only origin/dev:docs/design`.

### Source evidence

| Short ID | Report file | Evidence set |
|---|---|---|
| P0 | `REPORT.md` | **CONFIRMED** `e2e-refactor-p0-investigation`; local, non-versioned evidence kept by Nick |
| P0b | `REPORT.md` | **CONFIRMED** `e2e-refactor-p0b-gap-closure`; local, non-versioned evidence kept by Nick |
| D1 | `REPORT.md` | **CONFIRMED** `e2e-refactor-d1-comma-delimiter`; local, non-versioned evidence kept by Nick |
| D1-closure | `REPORT.md` | **CONFIRMED** `e2e-refactor-d1-review-closure`; local, non-versioned evidence kept by Nick |
| D1-fixup | `REPORT.md` | **CONFIRMED** `e2e-refactor-d1-test-fixup`; local, non-versioned evidence kept by Nick |
| DESIGN | `REPORT.md` | **CONFIRMED** `e2e-run-reporting-design-20261117T041558Z`; local, non-versioned evidence kept by Nick |
| REBASE | `REPORT.md` | **CONFIRMED** `e2e-run-reporting-rebase`; local, non-versioned evidence kept by Nick |

- **REQUIREMENT (Citation handling)** Cite these reports by short ID and section, for example `[P0 §Dependency map]`; the local reports and evidence folders are not tracked with this design pack.

### Relationship to pack 21

- **PROPOSAL** Pack 23 extends, rather than replaces, the post-login orchestration contract in pack 21. R3 extends `docs/design/21-post-login-e2e-orchestrator/architecture.md:10` with structured outputs; changes `architecture.md:18` to account for `SKIP`, lifecycle/report failures, and the new final-outcome rules; and extends `architecture.md:19` with cleanup-result recording and interrupted partial finalization. The ordered login/pack/logout behavior at `README.md:9` remains; the mandatory report augments its summary.
- **PROPOSAL** Pack 21's `implementation-plan.md:31` lists Spotless, Maven, coverage, startup, and Sonar gates. Under Q5, those Java gates do not apply to shell-only R1-R4; Java gates apply only if a milestone touches Java.
- **CONFIRMED** The drift command `git diff --name-status a73dcf60 origin/dev -- docs/design/21-post-login-e2e-orchestrator docs/design/22-basis-of-contribution-vesting-scale scripts/e2e` returned no changed files; the cited pack-21 statements were unchanged in the checked `origin/dev` tree.
- **PROPOSAL** Other unresolved questions are consolidated in [implementation-plan.md §4](implementation-plan.md#4-open-items-for-nick).
