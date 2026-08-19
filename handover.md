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
