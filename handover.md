# Captcha Verification Refactor Handover

The implementation and documentation through Phase 06 are approved. The active architectural contract is `docs/design/captcha/captcha-verification-lifecycle-contract.md`; migration and traceability are in `docs/design/captcha/captcha-migration-and-traceability.md`; the focused cumulative handover is `docs/design/captcha/handover.md`.

Phase 07 reconciles active documentation only. Historical prompts, plans, generated test evidence, and prior evidence directories remain unchanged. Phase 08 is limited to final cumulative validation and merge-request evidence. Settled lifecycle, HTTP 422, captcha-owned standalone errors, atomic verification, atomic consumption, Registration preservation, Username Recovery preservation, answer isolation, TTL preservation, and state concealment must not be reopened without new evidence and explicit architecture approval.

## Controller Refactoring Stage 001

- stage: 001 controller-classification-and-baseline
- canonical controller-refactoring documentation directory: `docs/refactor/controller/`
- current status: READY_FOR_ARCHITECT_REVIEW
- exact evidence directory: `target\evidence\controller-refactoring-001-20260819-160419`
- repository: root Maven project with standard `src/main/java` and `src/test/java` roots
- endpoint controllers: 17
- advice classes: 1
- filter-stack exception handlers: 1
- effective annotation-proven endpoints: 36
- accepted static facts: recorded in `docs/refactor/controller/controller-refactoring-classification.md`
- provisional migration decisions: recorded in evidence `021-proposed-migration-map.txt`
- reference-date duplicate result: 1 filesystem match(es); see evidence 017
- baseline compile result: PASS (exit 0)
- bounded-test result: PASS (exit 0)
- accepted boundary decisions: `ReferenceDataController` targets `web.referencedata`; `AuthenticatedPortalContextWebExceptionHandler` remains under `web.filter`
- Git-controlled files changed: `handover.md` and controller-refactoring Markdown documents under `docs/refactor/controller/`
- stage_002_started=false
- architect_status=PENDING
- commit_authorization=false
- Stage 002 has not started.
- endpoint contract completion: response types resolved for 36 endpoints; unresolved response types=0
- endpoint test evidence: focused HTTP/mapping=20, behavior-or-focused=35, class-reference-only=1, none=0
- evidence precedence: `082-superseded-evidence-manifest.txt` governs historical corrected evidence
- exact next-stage gate: architect PASS and Stage 001 documentation commit before Stage 002
## Controller Refactoring Stage 002

- stage: 002 advice-and-internal-api-boundaries
- Stage 001 architect status: PASS
- Stage 001 approved commit hash: `d9613470adaa6fab113f30cc9c12724d009b526a`
- implementation status: READY_FOR_ARCHITECT_REVIEW
- exact evidence directory: `target\evidence\controller-refactoring-002-20260819-163644`
- accepted package move: `web.controller.ApiExceptionHandler` to `web.advice.ApiExceptionHandler`
- accepted package move: `web.controller.ReferenceDateController` to `web.internal.referencedate.ReferenceDateController`
- preserved endpoints: `POST /api/v1/internal/reference-date/refresh`, `GET /api/v1/internal/reference-date/all`, `DELETE /api/v1/internal/reference-date/all`
- advice behavior: PRESERVATION_VALIDATION_PENDING
- LoggingSanitizer dependency: preserved exactly for Stage 002
- deferred boundaries: `ReferenceDataController` and `AuthenticatedPortalContextWebExceptionHandler` unchanged
- stage_003_started=false
- architect_status=PENDING
- commit_authorization=false

- manual-fix bounded validation totals: tests=10, failures=0, errors=0, skipped=0
- manual-fix files: `PortalAccessContextArchitectureGuardTest.java` and `RegistrationSessionSecurityAndReactiveCorrectnessTest.java`
- post-manual-fix full suite: NOT RERUN by explicit solution-owner direction
- historical full suite before manual fix: 3799 tests, 0 failures, 3 errors, 12 skipped; all three errors were in the two manually repaired test classes
- Stage 002 validation qualification: bounded repair validation PASS; architect must assess the accepted full-suite exception
## Controller Refactoring Stage 003

- stage: 003 standalone-capability-packages
- Stage 002 architect status: PASS
- Stage 002 approved commit hash: `8eedd7ab39bc6ac63735c1ae9970fff22b3995c0`
- implementation status: READY_FOR_ARCHITECT_REVIEW
- exact evidence directory: `target\evidence\controller-refactoring-003-20260819-173419`
- accepted renames: `AuthCaptchaController` to `CaptchaController`; `AuthLogoutController` to `LogoutController`; `AuthUsernameRecoveryController` to `UsernameRecoveryController`
- accepted moves: captcha, logout, username recovery, contribution, notification, personal information, and reference data into capability packages
- preserved endpoint count: 14
- Stage 002 boundaries: preservation evidence PASS from current Surefire reports
- stage_004_started=false
- architect_status=PASS
- commit_authorization=true

- Surefire evidence totals: tests=3790, failures=0, errors=0, skipped=12
- Surefire report count: 863
- validation source: existing `target/surefire-reports/TEST-*.xml`; Maven not rerun for final report generation
- validation freshness: PASS against all current changed Java sources
- endpoint contract audit: PASS; 14 effective method endpoints preserved
- source ownership audit: PASS; seven old sources absent and seven approved targets present

### Stage 003 Classification Documentation Repair 016

- repair type: documentation only
- corrected entries: seven source-to-target MOVE/RENAME classifications
- production changes: none
- test changes: none
- Maven rerun: false
- validation evidence reused: fresh passing Surefire XML, endpoint audit PASS, source ownership audit PASS
- architect status: PASS
- commit authorization: false
- stage_004_started=false

- Stage 003 prerequisite commit: `a336c80a1d710040208f872a9c709c7603d9db5c`
- architect decision: PASS

## Controller Refactoring Stage 004

- stage: 004 login-http-boundary
- Stage 003 architect status: PASS
- Stage 003 prerequisite commit: `a336c80a1d710040208f872a9c709c7603d9db5c`
- Stage 003 governance commit: `6ad5f4d141fec0fc2f8f27d84c452fb00dd16826`
- implementation status: READY_FOR_ARCHITECT_REVIEW
- exact evidence directory: `target/evidence/controller-refactoring-004-20260819-220150`
- consolidation: four Login HTTP controllers into `web.auth.login.LoginController`
- preserved Login endpoint count: 5
- constructor dependencies: 13 after valid cookie-writer deduplication
- helper collisions: resolved mechanically without behavior change
- focused validation totals: tests=28, failures=0, errors=0, skipped=0
- Stage 002 preservation totals: tests=32, failures=0, errors=0, skipped=0
- Stage 003 preservation totals: tests=83, failures=0, errors=0, skipped=0
- cumulative controller regression totals: tests=183, failures=0, errors=0, skipped=0
- full-suite totals: tests=3793, failures=0, errors=0, skipped=12
- compile result: PASS
- diff check result: PASS, exit=0; CRLF conversion warnings are informational
- production scope audit: PASS
- test scope audit: PASS
- documentation scope audit: PASS
- branch result: PASS; branch unchanged
- stash result: PASS; no stash operation performed by Stage 004
- Registration and Forgot Password: deferred and unchanged
- Surefire XML reports are authoritative; full Maven output is not duplicated in final evidence
- stage_005_started=false
- architect_status=PENDING
- commit_authorization=false

## Controller Refactoring Stage 005

- stage: 005 registration-http-boundary
- Stage 004 architect status: PASS
- Stage 004 prerequisite commit: `f4f043a37504d2dc36103dd3d13b4622c501edd9`
- implementation status: FOCUSED_ARCHITECTURE_PASS
- exact evidence directory: `target/evidence/controller-refactoring-005-20260819-233249`
- classification result: IMPLEMENTABLE_AS_DESIGNED
- consolidation: `AuthRegistrationController`, `AuthRegistrationOtpController`, and `AuthRegistrationUsernameController` into `web.auth.registration.RegistrationController`
- preserved Registration endpoint count: 6
- constructor dependencies: 15 after deduplicating the shared `AuthFlowCookieWriter`
- helper decisions: one identical `hasText(String)` retained; OTP-specific `trimToNull(String)` retained
- genuine fail-first result: PASS, expected architectural failure retained with exit 1
- formatter: Spotless PASS
- production compile: PASS
- test compile: PASS
- focused Registration architecture: PASS, tests=3, failures=0, errors=0, skipped=0
- Registration start and captcha: PASS
- Registration OTP lifecycle: PASS
- remaining Registration focused gates: PENDING
- eligible trust preservation: PENDING final validation; existing characterization identified
- Stage 002, Stage 003, and Stage 004 preservation: PENDING
- cumulative controller regression: PENDING
- full suite: PENDING
- final compile and audits: PENDING
- Forgot Password work: deferred and unchanged
- stage_006_started=false
- architect_status=PENDING
- commit_authorization=false

## Stage 005 validation continuation

- HEAD: `f4f043a37504d2dc36103dd3d13b4622c501edd9`
- Evidence: `target/evidence/controller-refactoring-005-20260819-233249`
- Username gate: PASS
- Remaining validation result: **FAIL**
- Failed gate: `registration-completion-cancel`
- Stage 006 started: **false**
- Commit authorization: **false**

## Stage 005 validation continuation

- HEAD: `f4f043a37504d2dc36103dd3d13b4622c501edd9`
- Evidence: `target/evidence/controller-refactoring-005-20260819-233249`
- Username gate: PASS
- Remaining validation result: **FAIL**
- Failed gate: `registration-completion-cancel`
- Stage 006 started: **false**
- Commit authorization: **false**

## Stage 005 final validation

- Registration start: PASS
- Registration OTP: PASS
- Registration username: PASS
- Registration completion/cancellation: PASS
- Registration security/session: PASS
- Eligible-trust: PASS
- Cumulative validation: PASS
- Full test suite: PASS
- Full test result: 3,806 run, 0 failures, 0 errors, 12 skipped
- Surefire reports are the authoritative test evidence.
- Stage 006 started: false
- Commit authorization: false
- Next action: review the final Stage 005 diff and authorize the commit.

## Controller Refactoring Stage 006
- classification: IMPLEMENTABLE_AS_DESIGNED
- prerequisite: `c56172d94c9c80690e04280980c186f8f7fb4f1c`
- evidence: `target\evidence\controller-refactoring-006-20260820-141556`
- consolidation: two legacy Forgot Password controllers into `web.auth.forgotpassword.ForgotPasswordController`
- preserved endpoint count: 8
- Change Password excluded: true
- production compile: PASS
- test compile: PASS
- test execution: PASS by Solution Owner manual run, 3,806 tests, 0 failures, 0 errors, 12 skipped
- stage_007_started=false
- architect_status=PASS
- commit_authorization=true

- Stage 006 approved commit: `9d0b4741ff00618f809d56aa6cee42545e5850a6`
## Controller Refactoring Stage 007
- Stage 006 architect status: PASS
- Stage 006 prerequisite commit: `e5892a49bd3453721a1f216e5d6a7ecf3f4da7a8`
- stage: 007 architecture-enforcement-and-final-cleanup
- implementation status: READY_FOR_ARCHITECT_REVIEW
- evidence: `target\evidence\controller-refactoring-007-20260820-150427`
- final architecture rules: final ownership, flat-package absence, advice/filter separation, forbidden dependency exclusion, route uniqueness, 36-endpoint parity, journey single ownership, Change Password exclusion
- expected endpoint count: 36
- route uniqueness: PASS by static classification audit; runtime test not run by script
- old flat package: PASS
- forbidden dependency audit: PASS
- production compile: PASS
- test compile: PASS
- test_execution=NOT_RUN_BY_SCRIPT
- solution_owner_validation_override=true
- full_suite_run=false
- full_suite_reason=manual post-implementation gate outside Stage 007 implementation script
- controller_refactoring_complete=false
- unplanned_stage_008_started=false
- architect_status=PENDING
- commit_authorization=false

### Login Cancel Revision-Aware CSRF Repair
- implementation: Option A validates CSRF against the exact already-loaded auth-flow session snapshot
- persistence reads: Login Cancel loads one revisioned snapshot and does not request a second CSRF-time session load
- cancellation: AuthFlowSessionPort.cancelRevisioned uses the exact loaded snapshot
- conflict policy: revision/state conflicts and uncertain persistence outcomes return SYSTEM_FAILED and do not report successful cleanup
- OTP ordering: OTP invalidation occurs only after revision-aware session cancellation is applied; revision conflicts perform no OTP mutation
- security logging: raw CSRF token and auth-flow session logging removed from the repaired service path
- compatibility: validateForSession remains as the load-and-delegate convenience API for callers without a loaded session
- validation: formatting, production compile, and test compilation are executed by the implementation script; runtime tests remain a manual post-implementation gate

### Login CSRF Runtime Fingerprint Diagnostics
- purpose: distinguish submitted-token, persisted-hash, and session-correlation differences between Send OTP and Cancel
- logging: SHA-256 fingerprints truncated to 8 bytes; no raw CSRF token, stored hash, session ID, OTP, or revision is logged
- path identification: Send OTP validates while the flow is STARTED; Cancel normally validates while the flow is OTP_SENT
- persistence conclusion: load and loadSnapshot use the same Redis key and AuthFlowSession record mapping
- status: temporary local diagnostic instrumentation; remove after the mismatch is identified

### Login CSRF Loaded-Session API Removal
- runtime root cause: Cancel submitted a different X-XSRF-TOKEN value from Send OTP; the server session and stored CSRF hash fingerprints were unchanged
- API decision: AuthFlowSessionCsrfService.validateLoadedSession removed because it was not part of the root-cause fix
- validation path: Login Cancel again uses the shared asynchronous validateForSession API
- concurrency: Login Cancel still loads a revisioned snapshot and uses that snapshot for cancelRevisioned after successful CSRF validation
- diagnostics: safe fingerprint logging remains temporarily available for final local verification
- validation: formatting, production compile, and test compilation are executed by the removal script; runtime tests remain manual

## CSRF Token Refresh Phase 2 Foundation

Baseline: `30a3028dba96adcfb0159dd2ed69171cdb36a9f7`.

Implemented in this round:

- introduced the framework-free refresh command, result, session-type, and inbound use-case contract
- extracted `AuthenticatedSessionCsrfRotationPort` as the shared atomic authenticated-session CSRF capability while preserving `AuthenticatedLoginReplaySessionPort` compatibility
- hardened pre-auth rotation so its response reports the existing session expiry and remaining lifetime instead of resetting the advertised lifetime from a configured scenario TTL
- retained revision-aware pre-auth persistence, existing token generation/hashing, Login replay CAS behavior, payload transport, cookie names, and protected-request `X-XSRF-TOKEN` validation
- added contract and remaining-lifetime tests

Not yet implemented:

- session-selecting refresh orchestration
- authenticated token-free validation and rotation service
- Spring composition
- HTTP controller, response mapper, cache headers, and public error mappings

The next round must build from this cumulative state and must not recreate either pre-auth rotation or authenticated Redis CAS logic.

## CSRF Token Refresh Phase 3A Typed Outcomes and Fail-First Contracts

Baseline: `da34340ab371d757e7a5b13584e6dac792bd6b59`.

This round restores the implementation plan's fail-first gate before adding orchestration or HTTP production classes.

Added:

- typed `RefreshCsrfTokenStatus` outcomes for success, missing, invalid, unusable, provenance rejection, conflict, and system failure
- an invariant-bearing `RefreshCsrfTokenResult` that never carries a raw token or session type on failure
- redaction and result-invariant tests
- reflection-based fail-first specifications for the application orchestration service, capability-scoped authenticated rotation dependency, and approved controller route

The reflection specifications intentionally compile before the production classes exist and are expected to fail if run at this commit. Implementation remains deferred to the next cumulative round.

## CSRF Token Refresh Phase 3B Application Orchestration

Baseline: `c77b47a4c616fb1d7677a954c79d118fb779fe7b`.

Implemented in this round:

- token-free authenticated-session load, protection validation, lifecycle validation, remaining-TTL calculation, and atomic CSRF-hash compare-and-set rotation
- session-family orchestration with authenticated-cookie precedence and no downgrade fallback
- strict Origin/Referer validation using configured CORS allowed origins
- stable internal refresh failure classification that never returns a raw token on conflict or failure
- Spring application composition reusing the existing Login replay Redis CAS adapter
- preservation of authenticated actor, account, lifecycle timestamp, absolute expiry, and Redis TTL state

Still deferred to the HTTP phase:

- controller and payload DTO mapping
- cookie extraction and public status mapping
- cache-prevention headers and endpoint documentation

### Phase 3B cumulative outcome correction

Pre-auth rotation now passes through `PreAuthSessionCsrfRefreshService`, which preserves distinct typed outcomes for missing, unusable, revision-conflict, and system-failure cases. The orchestration layer no longer collapses every pre-auth failure into `SESSION_NOT_USABLE`. Structural and classifier tests were added while the HTTP controller remains deferred.

## CSRF Token Refresh Phase 4 HTTP Adapter

Baseline: `c8975ebdcb2aa960bf608eae6187023d1cc38828`.

Added the `POST /api/v1/auth/csrf-token/refresh` WebFlux adapter, standard `MutationResponse` payload, configured session-cookie names, authenticated-cookie precedence, selected-cookie ambiguity rejection, request provenance mapping, request ID echo, and `no-store`/`no-cache` response headers. The endpoint does not require the old CSRF token and never publishes an XSRF cookie. Typed application failures map to unauthenticated, forbidden, conflict, or system-error HTTP responses without session or token leakage.

### Phase 4 cumulative no-downgrade correction

A present but blank authenticated-session cookie is now rejected immediately. The controller does not treat a blank `portal_sid` as absent and therefore cannot downgrade to a valid `auth_flow_sid`.

## CSRF Token Refresh Phase 5 Hardening

Baseline: `f0abac361e590f44cf46e20e240fef2dcf3dd27e`.

The refresh route is explicitly permitted through the in-process WebFlux security chain because the endpoint must recover a CSRF token before a protected request can be made. Session validity and Origin/Referer remain enforced by the application use case. Behavioral tests cover authenticated precedence, blank-cookie no-fallback, selected-cookie ambiguity, pre-auth selection, non-cacheable headers, request-ID echo, and absence of response cookies. Architecture guards retain configured cookie names and keep WebFlux/Redis dependencies out of the application orchestration package.

### Phase 6 focused-test fixture correction

The first focused runtime run reached all selected suites but the four controller behavior cases errored before their assertions because the direct `AuthWorkflowProperties` test fixture did not initialize its bindable `login` subtree. Production Spring binding was not implicated. The fixture now installs `AuthWorkflowProperties.Login`, retaining production defaults for `portal_sid`; the pre-auth properties already provide the `auth_flow_sid` default.

### Phase 6 JDT assertion compatibility correction

`RefreshCsrfTokenServiceStructureTest` now extracts declared field type names before asserting membership. This avoids Eclipse JDT selecting AssertJ's indexed `contains(element, Index)` overload for the two `Class<?>` arguments while retaining the same structural contract under Maven `javac` and IDE compilation.

## CSRF Token Refresh Result Data Envelope

Baseline: `abe67a1a523b55c9f4718c843cb2870ff43e023e`.

The successful refresh response now wraps the payload under `result.data`, yielding `result.data.sessionType` and `result.data.csrfToken`. Failure envelopes remain unchanged and continue to return a null result through the standard `MutationResponse` contract.

### Result data documentation alignment

The implementation plan, Phase 1 design record, session architecture, frontend integration guide, operations/testing guide, and session/CSRF README now document the final `result.data.sessionType` and `result.data.csrfToken` response path. The Phase 1 record explicitly marks only its original envelope detail as superseded; all locked security and persistence decisions remain valid.

## WSL Runtime and Validation Handover

- Updated: 20260825-222602
- Branch: `dev`
- Validated HEAD: `4864b746a9cb1615d2ac757561753bf15d7298ac`
- Maven production compile: PASS, reused from Phase 07-002 evidence
- Maven test compile: PASS, reused from Phase 07-002 evidence
- Full Maven test suite in Phase 07: NOT RUN
- Repaired runtime PID/PGID isolation: PASS
- Actuator health and safe missing `X-Request-Id` contract: PASS
- Targeted graceful shutdown with validation-process survival: PASS
- Build-once JAR SHA-256: `a0528d83328094253f0f7ea12e9be66571e375a34b56358f3b1d03bc76d736f0`
- Runtime-only image ID: `sha256:d263eb2b6bb9455d3798b75e83eef3ba64b6d768ed048a901c3c7b577f2bf3da`
- Optional local Docker end-to-end runtime: DEFERRED
- Deferral reason: Docker Desktop cannot route to the WSL loopback-only Redis Sentinel forward.
- Future option: install a container-reachable local Redis/Sentinel instance.
- Authoritative local workflow: `ngtpa-runtime-start dev`, `ngtpa-runtime-status dev`, `ngtpa-runtime-stop dev`.

## WSL Workstream Gate State

- Canonical documents: `docs/wsl`.
- WSL Phases 01 through 06: completed evidence exists; optional local Docker-to-Sentinel runtime remains deferred.
- WSL Phase 07 Gate A: complete.
- WSL Phase 07 Gate B: implementation prepared; commit required before completion declaration.
- WSL Phase 07 Gate C: prior direct-runtime evidence exists.
- WSL Phase 07 Gate D: prior safe curl evidence exists; external physical effects are not inferred.
- WSL Phase 07 Gate E: outstanding for Jenkins in Phase 08.
- WSL Phase 07 Gate F: outstanding for immutable deployment and Nick acceptance.
- Account Balance investigation exists, but implementation remains deferred until the WSL workstream advances.

## WSL Phase 08 API-service CI Contract

- Application branch: `features/feature-impl-account-balance`, MR target `dev`.
- Pipeline development branch: `features/feature-ngtpa-131-141-restructure-jenkins-repo`, MR target `dev`.
- Jenkins consumes both repositories for deployment.
- API-service deterministic validation entrypoint: `scripts/ci/validate.sh`.
- Test report contract: Surefire XML plus JaCoCo XML.
- Deployment verification contract: `.devsecops/healthcheck.sh` performs an HTTP Actuator readiness check only.
- Account Balance implementation remains deferred.
- Gate E and protected-merge enforcement remain outstanding.

## BFF Phase 00 Scripted E2E Framework

- The shared curl-based framework is under `scripts/e2e` and replaces Postman as authoritative local functional validation.
- Initial bootstrap modes are `anonymous` and `provided-session`.
- Authenticated transport uses the configured `portal_sid` cookie, `X-XSRF-TOKEN` when required, and selected `Account-Ref`; no `Session-Id` header is introduced.
- Pre-auth transport uses `auth_flow_sid` and `X-XSRF-TOKEN` when required.
- `Accept-Language` and `X-Request-Id` are applied consistently.
- Evidence is timestamped and redacted. Destructive, OTP/SMS, CAPTCHA automation, and scripted-auth flows are deferred.
- Account Balance is the first planned feature-pack consumer, but its provisional design pack remains untracked and uncommitted.

### Guarded Login OTP journey

- Login OTP send is implemented behind `E2E_ALLOW_EXTERNAL_EFFECTS=true` and is disabled by default.
- A fresh Login start is required in the same process. Historic auth-flow state is never reused.
- Cookie, CSRF, and destination IDs exist only in a mode-700 temporary directory with mode-600 files and are deleted at exit.
- Destination selection remains explicit even when only one destination is returned.
- OTP resend and verification remain deferred.

### Guarded Login OTP verification

- The integrated Login scenario supports one fresh Login start, one OTP send, hidden OTP input, and one verification attempt.
- Successful verification expects `SELECT_ACCOUNT`, a non-empty account list, `portal_sid`, and rotated authenticated CSRF.
- Retained evidence contains only account count and structural status. Account references and protected account data are redacted.
- OTP resend and account selection remain disabled.

### Confirmed post-login account selection model

- Successful Login OTP verification creates the authenticated portal session and returns `SELECT_ACCOUNT` with accessible accounts.
- There is no separate Login account-selection mutation endpoint in the confirmed `LoginController` contract.
- The frontend selects an account locally and sends only the selected `Account-Ref` with `portal_sid` on subsequent business requests.
- Unsafe subsequent requests also require authenticated `X-XSRF-TOKEN`.
- The BFF loads the authenticated session and resolves the selected account per request. `Session-Id` is not accepted.
- Login Gate D ends at authenticated `SELECT_ACCOUNT`; selected-account context is validated by each post-login feature pack.

### Guarded invalid Login OTP scenario

- The invalid-OTP scenario uses one fresh Login start, one OTP send, and one deliberately invalid verification attempt.
- Expected behavior is `BUSINESS_REJECTED` with `VERIFY_OTP`, remaining verify attempts, no authenticated portal cookie, and no account list.
- Real execution is fail-closed behind explicit external-effect, verification, and invalid-OTP intent guards.
- OTP resend, account selection, cancel, and logout remain excluded.

### Deterministic invalid Login OTP validation

- Live invalid-OTP validation prompts for the received OTP using hidden `/dev/tty` input, derives a same-length numeric code that differs in the final digit, and submits only the derived code.
- Neither the received nor derived OTP is displayed or retained.
- Invalid-OTP summaries are derived from the actual response, including public status, next step, portal-cookie issuance, and account count. Intended outcomes are not hardcoded into evidence.

### Confirmed live invalid Login OTP contract

- A well-formed but incorrect Login OTP returns HTTP 422, `BUSINESS_REJECTED`, and `VERIFY_OTP`.
- The response includes remaining verify attempts, does not issue `portal_sid`, and does not return accounts.
- The deterministic live run on 2026-08-27 satisfied every functional assertion except the obsolete HTTP 400 expectation, so the run is reclassified as successful after aligning the scenario to HTTP 422.

### Guarded Login OTP resend

- The first resend scenario performs one initial OTP send followed by one immediate resend request.
- Expected cooldown response is HTTP 429, `BUSINESS_REJECTED`, `VERIFY_OTP`, and consistent positive header/body retry timing.
- The scenario never sleeps or retries automatically and does not infer message-delivery success.
- OTP verification, additional resend attempts, cancel, and logout remain excluded.

### Confirmed live Login OTP immediate resend cooldown

- The live immediate-resend run performed one initial OTP send and one immediate resend request.
- The inner scenario passed with HTTP 429, `BUSINESS_REJECTED`, `VERIFY_OTP`, matching positive header/body retry timing, and zero automatic retries.
- The outer runner initially failed because attempt counts were written to the assertion stream instead of the retained resend summary. The summary writer now groups all fields under one file redirection.

### Guarded Login cancellation

- The cancellation scenario performs one fresh Login start followed by one cancellation request before OTP delivery.
- Expected behavior is HTTP 200, `SUCCESS`, `RETURN_TO_LOGIN`, cleared `auth_flow_sid`, and no returned CSRF token.
- The scenario performs no OTP send, resend, verification, account selection, repeated cancellation, or logout.

### Guarded authenticated logout

- The authenticated logout scenario extends the successful one-process Login journey with exactly one logout request.
- Logout uses `portal_sid`, authenticated CSRF, and `X-Request-Id`, and returns `MutationResponse<Void>` with HTTP 200 and `SUCCESS`.
- Expected transport cleanup is a cleared `portal_sid` and no CSRF token in the response.
- OTP resend, account selection, repeated logout, and post-logout protected requests remain excluded.

### Committed Login retest interface

- Routine Login retesting uses `scripts/e2e/run-login-dev.sh <scenario>` rather than commit-pinned `/mnt/d` wrappers.
- The dispatcher interactively collects runtime credentials, keeps the password hidden, sets the scenario-specific guards, and invokes the canonical runner.
- Supported names are `cancel`, `valid-otp`, `invalid-otp`, `resend-cooldown`, and `logout`.

### Login regression orchestrator

- `scripts/e2e/run-login-regression.sh` is the canonical suite-level entrypoint.
- Modes are `offline`, `live-safe`, `live-otp`, and `all`.
- The parent evidence manifest records explicit child paths returned by each launch and never discovers the newest evidence directory.
- Offline regression is fully automatic. Live credentials are collected once per suite. OTP operations are never retried automatically.

### Login regression live-child initialization repair

- `run_live_child` now declares local variables before assigning values that depend on `alias`, preventing `set -u` from reading an uninitialized variable.
- Validation uses bounded, non-recursive checks. It does not replace the `bash` executable in `PATH` and cannot recurse into the orchestrator.
- The interrupted validation made no Login, cancellation, or OTP request.

### Login regression interactive child streaming

- Live child output is streamed through `tee`, keeping progress and `/dev/tty` OTP prompts visible while retaining the parent child-output file.
- The child exit status is preserved from `PIPESTATUS[0]`.
- Each live flow is announced before launch, and the explicit child evidence path remains captured from that child output.
- An interrupted OTP is abandoned; retrying the suite requires a fresh Login flow and fresh OTP.

### Resumable Login OTP regression

- `live-otp-remaining` runs only invalid-OTP and resend-cooldown after logout already passed.
- Invalid OTP input is trimmed and allows three local corrections without a BFF verification call for rejected local input.
- Continuation budget is two OTP deliveries, one OTP prompt, one resend request, and zero automatic retries.

### Login suite packaging and OTP derivation stabilization

- Routine Login suites package their exact parent evidence directory as ZIP-in-`.txt` under `/mnt/d/Projects/ORSO/ngtpa` on exit.
- Invalid OTP derivation initializes `last_digit` and `replacement` at function scope before either assignment branch.

### Login Gate D closure

- Login Gate D is closed as PASS at implementation baseline   `863a8a4f733fb09c743b346ee3fecf780fd3e764`.
- Confirmed live coverage includes Login start, OTP destination selection, initial OTP send, valid OTP verification, invalid OTP verification, immediate resend cooldown, cancellation, authenticated session establishment, and authenticated logout.
- Successful OTP verification ends at `SELECT_ACCOUNT`; there is no separate Login account-selection mutation.
- Routine evidence is redacted, private runtime state is cleaned, and suite evidence is packaged automatically as ZIP-in-`.txt` under `/mnt/d/Projects/ORSO/ngtpa`.
- Offline regression is CI-suitable. Live suites remain explicit development or pre-release checks because real credentials and OTP delivery are involved.

### Login start and cancellation in-process integration coverage

- Added `LoginStartCancellationWebIntegrationTest` using Spring `WebTestClient` bound to the real production `LoginController`.
- The journey validates Login start response mapping, `auth_flow_sid` issuance, CSRF publication, cancellation `RETURN_TO_LOGIN`, cookie clearing, and idempotent missing-session cancellation.
- All OTP use cases are deterministic test-only sentinels and remain uninvoked.
- No `src/main/**` file changed, no external network was used, and no OTP was delivered.
- Spotless application remains intentionally deferred to the feature stabilization stage.
