# Prompt 006: Consolidate the Forgot Password Controller Boundary

## 1. Role and Authority

You are **M365 Copilot**, acting as the implementation agent for **Controller Refactoring Stage 006: Forgot Password HTTP Boundary**.

Nick is the Solution Owner. M365 Copilot owns the architecture, implementation design, deterministic changes, validation design, evidence requirements, and final review. Cline may only apply M365 Copilot-generated deterministic changes and run the prescribed validation workflow. Cline must not design, improvise, broaden scope, or choose an alternative implementation.

Do not begin Stage 007.

## 2. Governing Documents

Read these files completely before changing the repository:

```text
docs/refactor/controller/controller-refactoring-implementation-plan.md
docs/refactor/controller/controller-refactoring-classification.md
docs/refactor/controller/controller-refactoring-endpoint-inventory.md
docs/refactor/controller/controller-refactoring-prompt-001-classification.md
docs/refactor/controller/controller-refactoring-prompt-002-advice-internal-boundaries.md
docs/refactor/controller/controller-refactoring-prompt-003-standalone-capabilities.md
docs/refactor/controller/controller-refactoring-prompt-004-login-boundary.md
docs/refactor/controller/controller-refactoring-prompt-005-registration-boundary.md
handover.md
```

Use the committed cumulative state. Never reconstruct files from the initial repository state.

Stop with `BLOCKED` before production or test edits if any governing document is missing or `handover.md` does not record Stage 005 architect PASS.

## 3. Stage Identity and Prerequisite

```text
stage=006
stage_name=forgot-password-http-boundary
implementation_status=CLASSIFICATION_IN_PROGRESS
architect_status=PENDING
commit_authorization=false
stage_007_started=false
```

Remain on the current branch.

Resolve the Stage 005 prerequisite commit whose exact subject is:

```text
refactor: consolidate registration controller boundary
```

Do not use `grep` to verify commit subjects. Capture recent hashes and subjects, iterate through recent hashes in Bash, compare each subject, and retain the resolved hash.

Verify that the Stage 005 commit contains the approved Registration consolidation, directly affected tests, Stage 005 architecture protection, and canonical documentation, with no Stage 006 implementation.

Stop with `BLOCKED` if the prerequisite cannot be resolved or its scope is inconsistent.

Commit subject allowed only after architect PASS:

```text
refactor: consolidate forgot password controller boundary
```

Do not commit during this prompt.

## 4. Architectural Decision

Consolidate these two HTTP controllers:

```text
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordController.java
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordOtpController.java
```

into exactly one controller:

```text
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/forgotpassword/ForgotPasswordController.java
```

with package and class:

```java
package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword;

public final class ForgotPasswordController
```

If repository evidence proves that visibility or finality must differ for Spring or package-sensitive tests, preserve the minimum required behavior and record the exact reason. Do not create compatibility controllers.

The resulting controller owns only the current Forgot Password HTTP transport journey. Post-login Change Password is outside this feature and must remain excluded.

## 5. Repository-Derived Scope

Before implementation, derive the complete endpoint and dependency design from the two committed source controllers. The repository is authoritative.

The consolidated controller must preserve every existing Forgot Password operation owned by those sources, including all repository-proven endpoints for:

- workflow start;
- identity verification;
- member or account details verification;
- initial OTP handling;
- OTP resend;
- OTP verification;
- password reset completion;
- cancellation;
- any other operation currently owned by either source controller.

Do not infer endpoint paths or method bodies from filenames or this prompt.

Faithfully preserve:

- class and method annotations;
- effective route mappings;
- endpoint method bodies;
- constructor dependencies and qualifiers;
- private constants and transport helpers;
- request headers, cookies, Origin, Referer, CSRF, idempotency, request-ID, and language handling;
- retry-after behavior;
- auth-flow and XSRF cookie behavior;
- response mapping and status selection;
- synchronous and reactive exception behavior;
- structured logging.

## 6. Dependency and Collision Rules

Preserve constructor dependency order by first appearance in:

```text
AuthForgotPasswordController
AuthForgotPasswordOtpController
```

For repeated dependencies:

1. Deduplicate only when type, qualifier, bean identity, and responsibility are identical.
2. Retain separate fields where the same type serves different workflow responsibilities.
3. Record every decision in the consolidated design manifest.

For colliding fields, constants, helpers, or method names:

1. Compare signatures and behavior.
2. Reuse one implementation only when semantically identical.
3. Otherwise retain both with deterministic endpoint-specific private names.
4. Do not alter behavior to reduce duplication.
5. Preserve public endpoint method names unless Java signature collision requires a deterministic rename with no HTTP change.

## 7. Forgot Password Contract Invariants

Preserve exactly:

- all effective HTTP methods and paths;
- consumes and produces values;
- request and response DTOs and JSON shapes;
- generic base response and `MutationResponse` contracts;
- required and optional headers, cookies, parameters, and bodies;
- validation behavior;
- HTTP status selection;
- `Accept-Language` behavior;
- `X-Request-Id` behavior;
- idempotency behavior;
- CSRF, Origin, and Referer behavior;
- OTP destination behavior returned to the frontend;
- OTP resend, retry-after, verification, replay, and attempt behavior;
- auth-flow session and revision behavior;
- completion authorization and password reset behavior;
- cancellation behavior;
- cookie publication and clearing;
- public error and status normalization;
- security, privacy, audit, anti-abuse, and rate-limit behavior;
- OpenAPI annotations if present.

### 7.1 Canonical feature boundary

Use **Forgot Password** as the canonical feature name. Post-login Change Password is excluded and must not be added to this controller.

### 7.2 Downstream status isolation

The BFF must never expose `ApiStatus.DOWNSTREAM_REJECTED` to the frontend. Preserve the approved public business-status mapping for recognized downstream business rejections and `SYSTEM_ERROR` handling for technical failures.

### 7.3 TAS and APIM ownership

Do not redesign TAS-owned password policy or lock behavior. Do not alter APIM contracts, downstream operations, transport mapping, or error classification.

### 7.4 Configurability

OTP controls, rate limits, message-destination overrides, workflow policies, and anti-abuse controls must remain configuration-driven where currently implemented. Do not hardcode policy values in the controller.

## 8. Thin-Controller Boundary

The consolidated controller may only:

1. receive HTTP input;
2. perform existing declarative or transport-level validation;
3. map input to application commands;
4. invoke application input ports;
5. map results to approved public responses;
6. publish or clear approved cookies and headers;
7. select existing HTTP statuses.

The controller must not absorb or redesign:

- identity-verification policy;
- password policy or lock behavior;
- OTP generation, hashing, persistence, delivery, resend, verification, or attempt policy;
- session persistence or Redis behavior;
- APIM execution or TAS interpretation;
- message delivery;
- rate limiting;
- privacy or audit policy;
- localization ownership;
- public error normalization already owned by mappers or advice.

## 9. Allowed Production Scope

Production changes are limited to:

```text
DELETE/MOVE:
AuthForgotPasswordController.java
AuthForgotPasswordOtpController.java

CREATE:
auth/forgotpassword/ForgotPasswordController.java
```

Mechanically required direct production references to the two old concrete controller types may be updated only when proven during classification.

The final controller must be assembled from the committed cumulative sources with only:

- package and class-name changes;
- merged imports;
- merged fields and constructor;
- faithful endpoint methods and helpers;
- removal of identical duplicate imports;
- deterministic collision resolution with no behavior change.

Do not create deprecated aliases, forwarding controllers, inheritance bridges, duplicate Spring beans, or old-package wrappers.

## 10. Preserved and Deferred Scope

Do not modify:

- `web.auth.login.LoginController`;
- `web.auth.registration.RegistrationController`;
- Captcha, Logout, or Username Recovery controllers;
- business capability controllers;
- `web.advice.ApiExceptionHandler`;
- internal Reference Date controller;
- filter exception handler;
- `SecurityConfig`;
- application ports, services, DTOs, mappers, response policies, Redis, APIM, TAS integration, or configuration.

Stage 007 must not start.

## 11. Allowed Test Scope

Update only tests directly affected by:

- imports, constructors, or references to the two old controllers;
- old source paths, FQCNs, or simple class names;
- Spring test imports or explicit construction;
- endpoint ownership assertions;
- architecture rules selecting old controller ownership.

Add one Stage 006 architecture test proving:

- `web.auth.forgotpassword.ForgotPasswordController` exists;
- it is a Spring REST controller;
- it owns the complete endpoint set extracted before implementation;
- both old source paths are absent;
- both old FQCNs have no source owner;
- exactly one Forgot Password controller owns the HTTP journey;
- no direct outbound adapter, Redis implementation, APIM client, or persistence implementation dependency exists;
- application use cases remain separate;
- Login and Registration ownership is unchanged;
- no post-login Change Password endpoint is introduced.

Do not weaken existing tests or delete tests because ownership changed. Prefer import and fixture updates over moving tests.

## 12. Mandatory Pre-Change Classification

Before editing, record for each source controller:

1. SHA-256 and exact path.
2. Package, visibility, finality, and annotations.
3. Class-level mappings.
4. Every method mapping and effective route.
5. Public endpoint signatures.
6. Request and response types.
7. Constructor and field dependencies, including qualifiers.
8. Private constants and helpers.
9. Identity, details, OTP, completion, cancellation, cookie, CSRF, idempotency, request-ID, locale, retry, status, logging, and exception behavior.
10. Every production and test reference to path, FQCN, and simple name.
11. Package-private access risk.
12. Reflection and source-text dependencies.
13. Duplicate imports, dependencies, fields, constants, methods, and helpers.
14. Exact proposed constructor.
15. Exact proposed endpoint inventory.
16. Exact permitted test-update manifest.

Record pre-state hashes for all Stage 002 through Stage 005 target boundaries and `SecurityConfig`.

Conclude with exactly one:

```text
IMPLEMENTABLE_AS_DESIGNED
```

```text
BLOCKED
```

Do not edit production or tests unless classification is `IMPLEMENTABLE_AS_DESIGNED`.

## 13. Stop Conditions

Stop with `BLOCKED` if:

- the Stage 005 prerequisite is missing or incorrectly scoped;
- effective route uniqueness cannot be proven;
- any public contract would change;
- Change Password would enter scope;
- password, OTP, session, security, privacy, audit, rate-limit, APIM, or TAS behavior must change;
- a helper collision requires new behavioral design;
- package-private access requires unrelated redesign;
- distinct qualified beans cannot be preserved;
- a non-Forgot Password feature must change;
- unrelated working-tree changes cannot be classified;
- branch or stash state changes;
- Stage 007 work is present.

Do not invent a workaround.

## 14. Genuine Fail-First TDD

Before production changes, create the Stage 006 consolidation architecture test using reflection and filesystem assertions.

The test must compile against the unchanged repository and fail because:

```text
com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.ForgotPasswordController
```

does not exist and the two legacy source owners still exist.

Retain:

- exact Maven command;
- complete failure diagnostics;
- test totals;
- nonzero exit;
- timestamps and source hashes proving the run preceded production consolidation.

Use filesystem checks to avoid stale class-file false positives. Do not recreate, simulate, or backdate fail-first evidence.

## 15. Deterministic Implementation Artifacts

M365 Copilot must generate a deterministic Python writer and executable Bash orchestrator. Do not manually edit repository files.

The writer must:

1. Verify branch and Stage 005 prerequisite hash.
2. Verify source hashes and structural markers.
3. Verify the target does not exist.
4. Assemble the target from the committed current sources or write a complete M365 Copilot-generated replacement verified against extracted endpoint bodies.
5. Preserve behavior except for structural changes needed for consolidation.
6. Apply only collision decisions in the design manifest.
7. Update exact repository references using a closed replacement manifest.
8. Reject unexpected match counts.
9. Write and structurally audit the target before deleting old sources.
10. Delete both old sources only after target verification.
11. Update the four canonical controller-refactoring documents and cumulative `handover.md`.
12. Produce an explicit changed-path manifest.
13. Stop safely on a pre-state mismatch.

The Bash orchestrator must invoke `python`, not `python3`.

## 16. Maven Environment and Compile Order

Use exactly:

```bash
export JAVA_HOME="C:/Java/OpenJDK/jdk-21"
export M2_HOME="/d/Tools/apache-maven-3.9.15"
export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH"
```

Use `mvn`, not `mvnw`.

After formatting and before focused tests, run production and test compilation:

```bash
mvn -DskipTests compile
mvn -DskipTests test-compile
```

Stop on compilation failure. Repair all compile errors cumulatively before beginning test validation. After the final full suite, run the final production compile again.

## 17. Evidence Directory

Create one exact timestamped directory:

```text
target/evidence/controller-refactoring-006-<yyyyMMdd-HHmmss>/
```

Retain and reuse the explicit path. Do not use newest-directory discovery or create a second directory.

The status file must be inside the exact directory:

```text
<exact-evidence-directory>/controller-refactoring-006-validation-status.txt
```

Retain sequential evidence for pre-state, prerequisite, source hashes, classification, endpoint baseline, design manifest, permitted test updates, genuine fail-first, writer execution, formatting, production compile, test compile, focused tests, prior-stage preservation, cumulative regression, full suite, final compile, audits, diff check, final status, diff stat, name-status, command exits, and preserved failure diagnostics.

At minimum, use:

```text
001-working-directory.txt
002-branch.txt
003-head.txt
004-recent-commits.txt
005-stage-005-prerequisite.txt
006-git-status-pre.txt
007-git-diff-pre.txt
008-git-diff-cached-pre.txt
009-untracked-pre.txt
010-stash-fingerprint-pre.txt
011-governing-document-hashes.txt
012-forgot-password-source-hashes-pre.txt
013-preserved-boundary-hashes-pre.txt
014-classification-checkpoint.txt
015-reference-inventory.txt
016-endpoint-baseline.txt
017-consolidated-design-manifest.txt
018-permitted-test-update-manifest.txt
019-fail-first-command.txt
020-fail-first-output.txt
021-fail-first-exit.txt
022-writer-command.txt
023-writer-output.txt
024-writer-exit.txt
025-format-command.txt
026-format-exit.txt
027-production-compile-command.txt
028-production-compile-summary.txt
029-production-compile-exit.txt
030-test-compile-command.txt
031-test-compile-summary.txt
032-test-compile-exit.txt
033-focused-architecture-command.txt
034-focused-architecture-summary.txt
035-focused-architecture-exit.txt
036-forgot-password-start-identity-command.txt
037-forgot-password-start-identity-summary.txt
038-forgot-password-start-identity-exit.txt
039-forgot-password-otp-command.txt
040-forgot-password-otp-summary.txt
041-forgot-password-otp-exit.txt
042-forgot-password-completion-cancel-command.txt
043-forgot-password-completion-cancel-summary.txt
044-forgot-password-completion-cancel-exit.txt
045-security-session-command.txt
046-security-session-summary.txt
047-security-session-exit.txt
048-stage-002-preservation-summary.txt
049-stage-003-preservation-summary.txt
050-stage-004-preservation-summary.txt
051-stage-005-preservation-summary.txt
052-cumulative-controller-command.txt
053-cumulative-controller-summary.txt
054-cumulative-controller-exit.txt
055-full-tests-command.txt
056-full-tests-summary.txt
057-full-tests-exit.txt
058-final-compile-command.txt
059-final-compile-summary.txt
060-final-compile-exit.txt
061-endpoint-diff-audit.txt
062-production-scope-audit.txt
063-test-scope-audit.txt
064-documentation-scope-audit.txt
065-preserved-boundary-hashes-post.txt
066-diff-check.txt
067-git-diff-final.txt
068-git-diff-cached-final.txt
069-git-status-final.txt
070-untracked-final.txt
071-stash-fingerprint-final.txt
072-diff-stat.txt
073-name-status.txt
074-command-exit-codes.txt
075-final-status.txt
controller-refactoring-006-validation-status.txt
controller-refactoring-006-final-review-bundle.txt
```

Do not duplicate complete Maven test output into evidence TXT summaries. Surefire XML is authoritative. Retain exact commands, exit codes, concise summaries, report manifests, and failure diagnostics when needed.

## 18. Required Validation

Use explicit test class names, never wildcard selection.

### 18.1 Focused architecture

Run the new Forgot Password consolidation architecture test.

### 18.2 Start, identity, and details

Run all controller, mapper, contract, captcha, identity, details, language, correlation, and workflow tests directly covering start and verification prerequisites.

Inspect and include applicable current tests such as:

```text
AuthForgotPasswordControllerTest
AuthForgotPasswordIdentityControllerTest
AuthForgotPasswordDetailsControllerTest
ForgotPasswordStartWebMapperTest
ForgotPasswordStartActorRoleProvenanceTest
ForgotPasswordIdentitySubtask004FailFirstTest
ForgotPasswordDetailsApimSubtask005001FailFirstTest
ForgotPasswordDetailsOtpOrchestrationSubtask005004FailFirstTest
```

### 18.3 OTP lifecycle

Run all controller, mapper, resend, verification, idempotency, replay, rate-limit, delivery, session-revision, and architecture tests for Forgot Password OTP.

Inspect and include applicable tests such as:

```text
AuthForgotPasswordOtpResendControllerTest
AuthForgotPasswordOtpVerifyControllerTest
AuthForgotPasswordOtpResendEndpointArchitectureFailFirstTest
ForgotPasswordOtpReactiveArchitectureGuardTest
ForgotPasswordOtpVerifyIdempotencyHashArchitectureGuardTest
ForgotPasswordInitialOtpBindingSubtask005002FailFirstTest
ForgotPasswordDeliveryReplayProtectionSubtask005003FailFirstTest
ForgotPasswordOtpResendFoundationSubtask006001FailFirstTest
ForgotPasswordOtpResendOrchestrationSubtask006002FailFirstTest
VerifyForgotPasswordOtpServiceTest
```

### 18.4 Completion and cancellation

Run all completion, password reset, authorization, cancellation, cookie, resilience, and public-contract tests directly covering the HTTP journey.

Inspect and include applicable tests such as:

```text
AuthForgotPasswordCompleteControllerTest
AuthForgotPasswordCancelControllerTest
ForgotPasswordCancelArchitectureGuardTest
ForgotPasswordEndpointSecurityContractSubtask009Test
CompleteForgotPasswordServiceTest
CancelForgotPasswordServiceTest
ForgotPasswordCompletionActorRoleProvenanceTest
```

### 18.5 Security and session

Run applicable CSRF, Origin/Referer, request-ID, session revision, protected context, audit, privacy, public-error, and downstream-status isolation tests.

### 18.6 Prior-stage preservation

Run focused protection for:

- Stage 002 advice and internal Reference Date boundaries;
- Stage 003 standalone capability boundaries;
- Stage 004 Login consolidation;
- Stage 005 Registration consolidation.

### 18.7 Cumulative controller regression

Run the accepted cumulative controller suite, updating selection only for current concrete ownership. Do not remove tests because controllers moved.

### 18.8 Full suite

After the final production or test edit, run:

```bash
mvn test
```

No full-suite exception is pre-authorized.

### 18.9 Final compile

After the successful full suite, run:

```bash
mvn -DskipTests compile
```

Any production edit after a validation gate invalidates all final gates. Any test edit requires rerunning affected focused tests, cumulative regression, full suite, and final compile.

## 19. Automatic Background Validation

The complete Git-controlled `.cline` Stage 006 package must:

1. Apply the deterministic M365 Copilot-generated writer.
2. Launch validation exactly once in the background after writer success.
3. Use the explicit known evidence path.
4. Write `RUNNING` to the status file inside that directory.
5. Redirect command output directly to prescribed evidence files.
6. Poll the explicit status path until `PASS` or `BLOCKED`.
7. Avoid newest-directory discovery.
8. Avoid duplicate launches.
9. Store no validation status under `.cline/state`.
10. Require no manual validation by Nick.

Do not buffer complete Maven output in a shell variable.

## 20. Required Audits

Prove:

- one target `ForgotPasswordController` exists;
- both old sources are absent;
- no compatibility controller exists;
- before and after endpoint sets and counts are identical;
- request/response types, annotations, headers, cookies, validation, and status selectors are unchanged;
- constructor dependencies equal the required union after approved deduplication;
- application use cases remain separate;
- no outbound adapter, Redis implementation, APIM client, or persistence dependency was added;
- no Change Password endpoint entered scope;
- `ApiStatus.DOWNSTREAM_REJECTED` is not exposed publicly;
- Stage 002 through Stage 005 target boundaries retain hashes;
- every changed and untracked file is classified;
- branch and stash fingerprint are unchanged;
- `git diff --check` passes;
- Stage 007 has not started.

## 21. Documentation Updates

Update only:

```text
handover.md
docs/refactor/controller/controller-refactoring-implementation-plan.md
docs/refactor/controller/controller-refactoring-classification.md
docs/refactor/controller/controller-refactoring-endpoint-inventory.md
```

Also add this prompt under:

```text
docs/refactor/controller/controller-refactoring-prompt-006-forgot-password-boundary.md
```

Update endpoint ownership without changing endpoint contracts or counts. Mark the two legacy classifications as completed consolidation. Preserve all prior stage history.

The cumulative Stage 006 handover section must include:

```text
Stage 005 architect PASS and prerequisite commit hash
Stage 006 identity and implementer status
exact evidence directory
two source controllers consolidated into ForgotPasswordController
preserved endpoint set and count
constructor and helper collision decisions
focused validation totals by area
Stage 002 through Stage 005 preservation totals
cumulative controller totals
full-suite totals
compile result
scope, branch, stash, and diff results
Change Password exclusion confirmation
stage_007_started=false
architect_status=PENDING
commit_authorization=false
```

Do not claim architect approval.

## 22. Final Review Bundle

Create:

```text
<exact-evidence-directory>/controller-refactoring-006-final-review-bundle.txt
```

Include complete evidence for:

1. stage identity and final implementer status;
2. branch, HEAD, recent commits, and Stage 005 prerequisite;
3. pre-state Git and stash evidence;
4. complete source classification and consolidated design manifest;
5. before-and-after endpoint, annotation, signature, dependency, helper, and hash evidence;
6. genuine fail-first command, diagnostics, totals, chronology, and exit;
7. deterministic writer command, output, and exit;
8. production and test compile evidence before test execution;
9. faithful production, test, and documentation diffs;
10. focused commands, Surefire summaries/manifests, and exits;
11. all prior-stage preservation evidence;
12. cumulative controller regression evidence;
13. full-suite command, Surefire totals/manifest, and exit;
14. final compile command, summary, and exit;
15. endpoint, ownership, dependency, scope, branch, stash, and diff audits;
16. final Git status, untracked files, diff stat, and name-status;
17. command exit summary and validation status;
18. cumulative documentation updates;
19. `stage_007_started=false`;
20. `architect_status=PENDING`;
21. `commit_authorization=false`.

## 23. Completion Rules

Use `READY_FOR_ARCHITECT_REVIEW` only when:

- classification is `IMPLEMENTABLE_AS_DESIGNED`;
- genuine fail-first evidence exists;
- one `ForgotPasswordController` replaces both old controllers;
- all contracts and security behavior are preserved;
- production and test compile pass before tests;
- focused and prior-stage preservation gates pass;
- cumulative regression passes;
- full suite passes after the final source edit;
- final compile passes;
- all audits pass;
- documentation and handover are current;
- Stage 007 has not started.

Otherwise return `BLOCKED`, preserve evidence, and do not commit.

## 24. Final Console Output

Print only:

```text
implementation_status=<READY_FOR_ARCHITECT_REVIEW|BLOCKED>
architect_status=PENDING
commit_authorization=false
stage_007_started=false
stage_005_prerequisite_commit=<hash>
evidence_directory=<exact path>
validation_status=<PASS|BLOCKED>
final_review_bundle=<exact path>
approved_future_commit_subject=refactor: consolidate forgot password controller boundary
```
