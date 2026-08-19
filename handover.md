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
- architect_status=PENDING
- commit_authorization=false

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
- architect status: PENDING corrected-bundle review
- commit authorization: false
- stage_004_started=false
