# 05 Implementation plan

Workflow per sub-task: read-only check -> fail-first tests (where behaviour changes) -> minimal change
-> `spotless:check`, compile, test-compile, targeted tests -> one commit -> evidence archive.
Milestone gate: full `mvn clean test`, eager startup smoke test, SonarQube gate (owner-reported).
Stop rules: 3 consecutive failed scripts, or 10 scripts without a commit -> escalate.

Branch: `feature/change-of-investment-choice` (existing worktree
`api-service-worktrees/change-of-investment-choice`). No new branch or worktree.

| Milestone | Sub-task | Content | Depends on |
|---|---|---|---|
| M0 | 0.1 | Read-only check of the remaining open facts (07, prompt 0.1). Output a fact table. No changes | - |
| M1 password validation second purpose | 1.1 | Purpose `CHANGE_INVESTMENT_CHOICE` in the purpose enum and parser; Personal Information unchanged (regression tests first) | M0 |
| | 1.2 | Issuance structure check selected by purpose; strict V1-V8 for CIM | 1.1 |
| | 1.3 | Token gate maps (method, route) -> purpose; CIM route `POST /api/v1/investment-choice/instructions`; path-variant tests | 1.2 |
| | 1.4 | Merged into 1.3: one route matcher drives gating and confidential logging (as built: 7bc73692, log-marker hardening fcd710eb) | 1.3 |
| M2 TRPGetCIMDtl | 2.1 | APIM DTOs, `ignoreUnknown`, local strict formatters | M0 |
| | 2.2 | `ApimInvestmentChoiceDetailsAdapter`: envelope interpretation, integrity rules, classifier + one `FailureEvent`, feature exception, empty eligibility slot; ledger +1 | 2.1 |
| M3 GET | 3.1 | `InvestmentChoiceSnapshotCacheService` mirroring `PersonalInformationConfigCacheService` (read miss semantics, best-effort write, `belongsTo`) | M0 |
| | 3.2 | `GetInvestmentChoiceUseCase` (assembly, account number, snapshot write) | 2.2, 3.1 |
| | 3.3 | Controller GET, `InvestmentChoiceWebMapper` (ordering, display name), web DTOs, `InvestmentChoiceExceptionMappings` (WARN), `ErrorCodes`; contract tests for every row of 02 section 1 | 3.2 |
| M4 submit | 4.1 | TRPCIMReq DTOs and `ApimInvestmentChoiceInstructionAdapter` (`NON_RETRYABLE`, never resubmitted); outcome = success / err-message / unknown; classifier + one `FailureEvent`; ledger +1 | M2 |
| | 4.2 | Body and snapshot validator (V1-V8, S1-S3) | 3.1 |
| | 4.3 | `SubmitInvestmentChoiceUseCase`: snapshot miss event, expansion, outcome mapping, reconciliation | 4.1, 4.2 |
| | 4.4 | Controller POST behind the token gate with the 422 override; contract tests for every row of 02 section 2 and 04 section 4 | 1.4, 4.3 |
| M4b cancel | 4b.0 | Password validation third purpose `CANCEL_INVESTMENT_CHOICE`: purpose enum, strict issuance check for `{"refNo": "..."}` (CV1, CV2), gate mapping (POST, exact path `/api/v1/investment-choice/instructions/cancel`), confidential-route logging; M1 pattern, two commits | M4 |
| | 4b.1 | `TRPCnclCIMReq` DTOs and `ApimInvestmentChoiceCancelAdapter` (`NON_RETRYABLE`, never resubmitted), `InvestmentChoiceCancelCommand`, port, exceptions (not-cancellable, unavailable), classifier + one `FailureEvent`, config key `apim.investment-choice.cancel.path`; ledger 23 -> 24 | M4 |
| | 4b.2 | Snapshot gains `pendingRefNos` (D-40), `CancelInvestmentChoiceService.cancel` (snapshot read, membership check, adapter, best-effort removal), bean wiring; no method named `execute`, no `accountRef`/`sessionId` components | 4b.0, 4b.1 |
| | 4b.3 | Controller `POST /api/v1/investment-choice/instructions/cancel` behind the token gate, 422/503 mappings, `ErrorCodes`, end-to-end tests behind the real gate | 4b.2 |
| M5 close | 5.1 | Curl scenario script (local build vs dev downstream, test member, explicit live-submit opt-in, plus a cancel scenario) | M4b |
| | 5.2 | As-built notes and traceability in this pack; milestone gate; MR (squash the docs commits) | 5.1 |
| Deferred | D.2 | Eligibility codes -> 422 slot (O-2) | Progress |

Every sub-task with production code also runs `ErrorLogCallSiteRatchetArchitectureTest` and
`ApimFailureClassifierArchitectureTest`; `KNOWN_REMAINING` must not change.
Every test run uses `-Dm15.redis.server=/usr/bin/redis-server`; zero skipped tests required.

M1 may merge separately (it changes the shared password-validation capability); the CIM route stays
unreachable until 4.4 because no controller exists before it.

## Release

Frontend and BFF release together. No new configuration expected.

## M6 purpose-owned prechecks (approved target; not implemented)

This design checkpoint does not authorize implementation. M0-M5 and their
evidence remain the recorded implementation history. Implementation must be
separately authorized and must preserve Personal Information behavior.

| Slice | Scope | Required evidence |
|---|---|---|
| M6.1 Shared decoder/policy composition | Minimal framework-free payload representation, purpose decoder/policy interfaces and fail-closed registries; Jackson adapter parsing; no shared parser/issuance purpose branches | Missing/duplicate/mismatched decoder-policy registrations fail composition; supported purposes compose exactly once |
| M6.2 Issuance orchestration and errors | Dispatch policy before throttle/password; generic 400 and wrong-password behavior unchanged; CIM detailed 422 and feature-specific 503 | Order probes prove prechecks run first and no throttle/password/grant on rejection; exactly one feature-owned event on snapshot miss |
| M6.3 CIM purpose policies | Move submit V1-V8/S1-S3 and cancel CV1-CV2/membership into registered application policies | Rule parity and detailed code/target tests; expected violations produce no incident; snapshot failure has no shared duplicate |
| M6.4 Trusted execution handoff | Reuse existing gate binding attribute; require it at CIM controllers and commands; strict decode/typed construction without feature precheck rerun | Bypass, rejected/missing binding and wrong scope/session/account/body/replay/expiry cannot dispatch; successful path proves prechecks are not repeated |
| M6.5 Execution compatibility | Preserve submit downstream dispatch/reconciliation/snapshot updates; cancel no snapshot precheck, best-effort post-success cleanup only; preserve Progress and PI behavior | Regression tests cover downstream outcomes, cleanup failure, PI issuance/PUT and current-state boundaries |

Use the test requirements in design 16 document 05 and CIM contract tables in
documents 02 and 04. No token migration/versioning, rolling-deployment work,
snapshot concurrency redesign, CAS refactor or GET fencing is authorized. Do
not claim the approved target is implemented or runtime-verified until its
separate implementation gates pass.
