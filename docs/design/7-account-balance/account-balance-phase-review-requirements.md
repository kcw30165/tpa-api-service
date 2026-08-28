# Account Balance Phase Review Requirements

## Purpose

This document defines the reproducible review package required at the end of every Account Balance implementation phase. A phase is not approved merely because compilation succeeds. The package must let the solution architect verify scope, cumulative behavior, architecture boundaries, preservation, validation, open items, and the next-phase gate from committed evidence.

## Review package location

Each phase-ending script must create the review package inside its exact timestamped evidence directory:

```text
target/evidence/account-balance-<phase>-<subphase>-<timestamp>/phase-review/
```

The evidence directory must be created before formatting or validation. The upload artifact must package that exact directory as a ZIP archive whose extension is renamed to `.txt`.

## Required review files

The `phase-review` directory must contain exactly these files:

```text
01-phase-summary.md
02-commit-list.txt
03-cumulative-diff-stat.txt
04-cumulative-name-status.txt
05-validation-summary.md
06-test-inventory.txt
07-architecture-audit.md
08-preservation-report.md
09-design-conformance.md
10-open-items.md
11-handover-snapshot.md
12-review-status.txt
```

## File requirements

### 01-phase-summary.md

Record the phase objective, fixed branch, phase base, phase start commit, pre-review HEAD, review commit when available, implemented subphases, delivered boundaries, deliberately excluded work, and the requested architect decision.

### 02-commit-list.txt

List commits from the parent of the first phase implementation commit through the pre-review HEAD in chronological order. Each line must include the full hash and exact subject.

### 03-cumulative-diff-stat.txt

Capture the cumulative Git diff stat from the phase base through the pre-review HEAD. After the review commit is created, the evidence-level committed diff stat may additionally include the review commit.

### 04-cumulative-name-status.txt

Capture cumulative name-status from the phase base through the pre-review HEAD.

### 05-validation-summary.md

Record exact formatter, compile, and test-compilation commands and exit codes. Identify authoritative build-report locations. Do not duplicate full Maven output. Preserve concise failure diagnostics only when a command fails. State explicitly that full runtime tests were not executed when prohibited by the implementation workflow.

### 06-test-inventory.txt

List all phase-focused test and architecture-guard source files. Distinguish compilation coverage from runtime execution. Do not claim runtime passage when only test compilation ran.

### 07-architecture-audit.md

At minimum, verify:

- The controller follows the approved contribution-controller boundary pattern without copying contribution business logic.
- `Idempotency-Key` is not required for Account Balance GET.
- `Accept-Language` is optional and the shared resolver provides English fallback.
- `Account-Ref` remains owned by selected-account context resolution.
- Only `X-Request-Id` is universally filtered and propagated to APIM.
- Technical errors use `SYSTEM_ERROR`, `err.system.unavailable`, and error type `SYSTEM`.
- `curr` is serialized and initial load uses the approved empty wire value.
- Successful APIM content requires exactly one complete data item.
- Application code contains no Reactor blocking or detached subscription.
- Account Balance adapters do not duplicate OAuth, certificate, encryption, retry, or transport infrastructure.
- Final grouping, aggregation, localization, number rendering, and factsheet validation are not embedded in the controller or APIM adapter.
- The APIM routing ledger contains `TRPGetMemberAccBal` exactly once with the approved executor-owned routing.

### 08-preservation-report.md

Record each earlier approved phase boundary and whether it remains preserved. Any intentional replacement must cite its superseding committed decision. Include public contract, trusted context mapping, metadata behavior, APIM DTOs, typed error boundary, raw source boundary, executor adapter, and boundary orchestration.

### 09-design-conformance.md

Compare implementation against the current master design, source and requirements, implementation plan, and cumulative handover. Separate conformant behavior, resolved deviations, and unresolved contradictions. Do not silently reinterpret approved requirements.

### 10-open-items.md

List every deferred, temporary, unresolved, or next-phase item. For Phase III, this must identify the provisional `AccountBalanceAssembler` production bean as a Phase IV blocker and state that it returns a sanitized system failure rather than fabricated financial output until the real transformation is implemented.

### 11-handover-snapshot.md

Copy the complete committed `docs/design/7-account-balance/handover.md` content after the phase-ending update. It must end with exactly one newline.

### 12-review-status.txt

Use machine-readable `KEY=VALUE` lines. A package ready for review must include:

```text
STATUS=READY_FOR_ARCHITECT_REVIEW
PHASE=<phase>
SUBPHASE=<subphase>
BRANCH=features/feature-impl-account-balance
PHASE_BASE=<full-hash>
PRE_REVIEW_HEAD=<full-hash>
COMMIT_SUBJECT=<exact-subject>
FORMATTER=PASS
PRODUCTION_COMPILE=PASS
TEST_COMPILE=PASS
STATIC_AUDITS=PASS
DIFF_CHECK=PASS
RUNTIME_TESTS_EXECUTED=NO
NEXT_PHASE_BLOCKED_PENDING_ARCHITECT_DECISION=YES
```

The final committed HEAD may be recorded in the evidence-level final status after commit creation.

## Validation rules

At the feature-level stabilization point, run the repository formatter exactly once, then run:

```bash
mvn -DskipTests compile
mvn -DskipTests test-compile
```

Do not run `mvn test` in generated implementation or validation scripts. Stop on formatter, compile, test-compile, static-audit, package-validation, diff-check, staging, or commit failure. Do not use `mvnw`.

## Evidence rules

The phase-ending evidence directory must retain sequential top-level evidence for pre-state, prerequisite commits, fail-first behavior, write summary, formatting, compile, test-compile, static audits, preservation, diff check, diff stat, name-status, command exit codes, and final status. Maven evidence must contain exact commands, exit codes, concise summaries, report manifests, and preserved failure diagnostics only when needed.

## Git and safety rules

Verify the fixed branch and expected cumulative HEAD before writing. Verify required commit subjects using Bash substring matching and resolve hashes by iterating recent hashes and comparing exact subjects. Never reset, clean, stash, or destructively checkout user work. Commit only after every required gate passes.

## Architect decision gate

The phase-ending result must remain blocked from the next phase until the solution architect returns one of:

```text
PHASE_DECISION=APPROVED
PHASE_DECISION=APPROVED_WITH_FOLLOW_UP
PHASE_DECISION=REJECTED
```

`APPROVED_WITH_FOLLOW_UP` permits progression only when the decision explicitly states that the follow-up is non-blocking. `REJECTED` requires a repair round and a refreshed review package.
