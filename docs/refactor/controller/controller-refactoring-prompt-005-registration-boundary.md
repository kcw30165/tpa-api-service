# Prompt 005: Consolidate the Registration Controller Boundary

## 1. Role and Authority

You are **M365 Copilot**, acting as the implementation agent for **Controller Refactoring Stage 005: Registration HTTP Boundary**.

Nick is the Solution Owner. M365 Copilot owns the architecture, implementation design, deterministic code changes, validation design, and final review. Cline may only apply M365 Copilot-generated deterministic changes and execute the prescribed validation and evidence workflow. Cline must not design, improvise, broaden scope, or select an alternative implementation.

Do not begin Stage 006.

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
handover.md
```

Use the committed cumulative repository state. Do not reconstruct any file from the original pre-refactoring state.

Stop with `BLOCKED` before production or test changes if a governing document is missing or `handover.md` does not record Stage 004 architect PASS.

## 3. Stage Identity and Prerequisite

```text
stage=005
stage_name=registration-http-boundary
implementation_status=CLASSIFICATION_IN_PROGRESS
architect_status=PENDING
commit_authorization=false
stage_006_started=false
```

Remain on the current branch. Do not switch branches.

Resolve the exact Stage 004 prerequisite commit whose subject is:

```text
refactor: consolidate login controller boundary
```

Do not use `grep` to verify the subject. Capture recent hashes and subjects, iterate through the recent hashes in Bash, compare each subject, and retain the resolved hash.

Verify that the Stage 004 commit contains:

- the consolidated `web.auth.login.LoginController`;
- deletion of the four legacy Login controllers;
- the Login boundary architecture test;
- directly affected Login tests;
- canonical controller-refactoring documentation;
- no Stage 005 production work.

Stop with `BLOCKED` if the prerequisite cannot be resolved or its committed scope is inconsistent.

Commit subject allowed only after architect PASS:

```text
refactor: consolidate registration controller boundary
```

Do not commit during this prompt.

## 4. Architectural Decision

Consolidate these three HTTP controllers:

```text
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthRegistrationController.java
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthRegistrationOtpController.java
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthRegistrationUsernameController.java
```

into exactly one controller:

```text
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/RegistrationController.java
```

with package and class:

```java
package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration;

public final class RegistrationController
```

If repository evidence proves that finality or visibility must differ for Spring or existing package-sensitive tests, preserve the minimum effective behavior and record the precise reason. Do not create compatibility controllers.

The resulting controller owns only the existing Registration HTTP transport boundary. Existing application ports, commands, mappers, response policies, cookie writers, session components, captcha gates, OTP services, username checks, completion services, and cancellation services remain separate.

## 5. Repository-Derived Consolidated Design

Before implementation, derive the exact target from the three committed source files. Do not invent endpoint methods, constructor dependencies, annotations, mappings, status policies, helper behavior, or workflow decisions.

The consolidated `RegistrationController` must faithfully preserve the current controllers' complete Registration journey, including every repository-proven operation for:

- registration start;
- captcha-gated registration initiation;
- OTP send, resend, and verification where currently exposed;
- username availability or username-check operations;
- registration completion;
- registration cancellation;
- any additional Registration endpoint currently owned by the three source controllers.

The canonical endpoint inventory and current source annotations are authoritative. Do not infer routes from filenames or from this prompt.

Copy faithfully:

- class and method annotations;
- all endpoint methods;
- constructor dependencies and qualifiers;
- private constants and transport helpers;
- request headers, cookies, Origin, Referer, CSRF, idempotency, request-ID, and language handling;
- captcha input and transport mapping;
- OTP destination, retry, and response-header handling;
- auth-flow and XSRF cookie publication or clearing;
- response mapping and status selection;
- synchronous and reactive exception behavior;
- structured logging behavior.

## 6. Collision and Deduplication Rules

Preserve dependency order by first appearance in this source order:

```text
AuthRegistrationController
AuthRegistrationOtpController
AuthRegistrationUsernameController
```

For repeated dependencies:

1. Deduplicate only when the exact same type, qualifier, bean, and responsibility are used.
2. Retain distinct fields when the same type represents distinct workflow responsibilities.
3. Record every deduplication decision in the consolidated design manifest.

For colliding helpers, fields, constants, or method names:

1. Compare signatures and behavior.
2. Reuse one implementation only when semantically identical.
3. Otherwise retain both under deterministic, endpoint-specific private names.
4. Do not alter behavior merely to reduce duplication.
5. Do not rename public endpoint methods unless a Java signature collision makes this unavoidable and reflection-sensitive tests are updated without changing HTTP behavior.

## 7. Registration Contract Invariants

Preserve exactly:

- effective HTTP methods and paths;
- consumes and produces values;
- request and response DTO types;
- JSON shapes and public envelopes;
- required and optional headers, cookies, query parameters, and bodies;
- validation annotations and validation results;
- HTTP status selection;
- `Accept-Language` behavior;
- `X-Request-Id` behavior;
- idempotency behavior;
- CSRF, Origin, and Referer behavior;
- captcha one-time-use behavior behind the application boundary;
- OTP destination selection and frontend response behavior;
- OTP send, resend, retry-after, verification, and attempt behavior;
- auth-flow session handling and revision behavior;
- registration username-check behavior;
- registration completion and cancellation behavior;
- cookie publication and clearing;
- downstream error normalization;
- public status isolation, including never exposing `ApiStatus.DOWNSTREAM_REJECTED` to the frontend;
- security and rate-limit behavior;
- OpenAPI annotations if present.

### 7.1 Eligible trust preservation

Registration must continue to allow additional trust registration when eligible unregistered trusts remain. Do not change eligibility, trust-list projection, account mapping, completion, or return-to-login behavior.

### 7.2 Password and downstream ownership

Do not move or redesign TAS-owned password-policy behavior, APIM contracts, downstream operations, or error classification. The controller remains an HTTP adapter only.

### 7.3 Configurability preservation

OTP controls, rate limits, message-destination overrides, and workflow policies must remain configuration-driven wherever currently implemented. Do not hardcode policy values in the controller.

## 8. Thin-Controller Boundary

The consolidated controller may only:

1. acquire HTTP input;
2. perform existing declarative or transport-level validation;
3. map input to application commands;
4. invoke application input ports;
5. map results to approved responses;
6. publish or clear cookies and approved response headers;
7. select existing HTTP statuses.

It must not absorb or redesign:

- registration eligibility;
- captcha generation, validation, consumption, or persistence;
- OTP generation, hashing, storage, resend, verification, delivery, or attempt policy;
- username availability logic;
- password policy;
- session persistence or Redis access;
- APIM execution or TAS interpretation;
- trust eligibility or account-selection policy;
- localization ownership;
- public error normalization already owned by mappers or advice.

## 9. Allowed Production Scope

Production changes are limited to:

```text
DELETE/MOVE:
AuthRegistrationController.java
AuthRegistrationOtpController.java
AuthRegistrationUsernameController.java

CREATE:
auth/registration/RegistrationController.java
```

Mechanically required direct production references to the old concrete controller types may be updated only when proven during classification. No unrelated production class may change.

The target must be assembled from the committed source files with only these structural changes:

- target package declaration;
- target class name;
- merged imports;
- merged fields and constructor;
- faithful endpoint methods and private helpers;
- removal of duplicate identical imports;
- deterministic collision resolution with no behavior change.

Do not create forwarding controllers, deprecated aliases, inheritance bridges, duplicate Spring beans, or temporary old-package wrappers.

## 10. Deferred and Preserved Production Scope

Do not modify:

- Forgot Password controllers;
- `web.auth.login.LoginController`;
- Captcha, Logout, or Username Recovery controllers;
- Contribution, Notification, Personal Information, or Reference Data controllers;
- `web.advice.ApiExceptionHandler`;
- `web.internal.referencedate.ReferenceDateController`;
- `AuthenticatedPortalContextWebExceptionHandler`;
- `SecurityConfig`;
- application ports, services, DTOs, mappers, response policies, Redis, APIM, TAS integration, or configuration.

Stage 006 must not start.

## 11. Allowed Test Scope

Update only tests directly affected by:

- imports, constructors, or concrete type references to the three legacy controllers;
- source-path assertions;
- class-name and package assertions;
- Spring test imports or explicit controller construction;
- endpoint ownership assertions;
- architecture rules selecting the old controller paths or names.

Add one Stage 005 architecture test that proves:

- `web.auth.registration.RegistrationController` exists;
- the target is a Spring REST controller;
- the target owns the complete Registration endpoint set extracted before implementation;
- all three old source paths are absent;
- all three old FQCNs have no source owner;
- exactly one Registration controller owns the HTTP journey;
- the target has no direct outbound adapter, Redis implementation, APIM client, or persistence implementation dependency;
- application use cases remain separate;
- Login and Forgot Password controller ownership is unchanged.

Do not weaken existing tests. Prefer imports and deterministic constructor-fixture updates over moving tests. Retain existing test class names unless package-private access requires a move.

## 12. Mandatory Pre-Change Classification

Before any edit, record for each source controller:

1. SHA-256 and exact path.
2. Package, visibility, finality, and annotations.
3. Class-level mappings.
4. Every method mapping and effective route.
5. Public endpoint signatures.
6. Request and response types.
7. Constructor and field dependencies, including qualifiers.
8. Private constants and helpers.
9. Captcha, OTP, username, completion, cancellation, cookie, CSRF, idempotency, request-ID, locale, retry, and error behavior.
10. Every production and test reference to path, FQCN, and simple name.
11. Package-private access risk.
12. Reflection and source-text test risk.
13. Duplicate imports, dependencies, fields, constants, methods, and helpers.
14. Exact proposed constructor.
15. Exact proposed endpoint inventory.
16. Exact permitted test update manifest.

Record pre-state hashes for:

```text
web.auth.login.LoginController
web.auth.forgotpassword-related controllers
web.auth.captcha.CaptchaController
web.auth.logout.LogoutController
web.auth.usernamerecovery.UsernameRecoveryController
web.advice.ApiExceptionHandler
web.internal.referencedate.ReferenceDateController
web.filter.AuthenticatedPortalContextWebExceptionHandler
SecurityConfig
```

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

- the Stage 004 prerequisite is missing or incorrectly scoped;
- route uniqueness cannot be proven;
- consolidation changes any public mapping or HTTP contract;
- the additional eligible trust behavior cannot be proven preserved;
- password, OTP, captcha, session, security, or downstream behavior must change;
- a helper collision requires new behavior design;
- package-private access requires unrelated production redesign;
- distinct qualified beans cannot be preserved in one constructor;
- any non-Registration feature must change;
- an unrelated working-tree change cannot be classified;
- branch or stash state changes;
- Stage 006 work is present.

Do not invent a workaround.

## 14. Genuine Fail-First TDD

Before production changes, create the Stage 005 architecture test with reflection and filesystem assertions.

The test must compile in the unchanged pre-implementation repository and fail because:

```text
com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.RegistrationController
```

does not exist and the three legacy source owners still exist.

Retain:

- exact Maven command;
- complete failed output;
- test totals;
- nonzero exit code;
- assertion or exception proving the original architecture defect;
- timestamps and pre-production source hashes proving chronology.

Use filesystem checks for old source ownership to avoid stale `target/classes` false positives. Do not recreate or simulate fail-first evidence after implementation.

## 15. Deterministic Implementation Artifacts

M365 Copilot must generate a deterministic Python writer and executable Bash orchestrator. Do not manually edit repository files.

The writer must:

1. Verify branch and Stage 004 prerequisite hash.
2. Verify source hashes and structural markers for all three controllers.
3. Verify the target does not exist.
4. Assemble the target from the committed cumulative source state or write a complete M365 Copilot-generated replacement verified against extracted endpoint bodies.
5. Preserve endpoint behavior except for qualification and structural changes required by consolidation.
6. Resolve dependencies and helper collisions only according to the approved design manifest.
7. Update exact references using a closed path-and-marker manifest.
8. Reject unexpected replacement counts.
9. Write and structurally audit the target before deleting legacy sources.
10. Delete all three old sources only after target verification succeeds.
11. Update the four canonical controller-refactoring documents and cumulative `handover.md`.
12. Produce an explicit changed-path manifest.
13. Stop safely on any pre-state mismatch.

The Bash orchestrator must invoke `python`, not `python3`.

## 16. Maven Environment

Use exactly:

```bash
export JAVA_HOME="C:/Java/OpenJDK/jdk-21"
export M2_HOME="/d/Tools/apache-maven-3.9.15"
export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH"
```

Use `mvn`, not `mvnw`.

## 17. Evidence Directory

Create one timestamp once:

```text
target/evidence/controller-refactoring-005-<yyyyMMdd-HHmmss>/
```

Retain and reuse that exact path. Do not use newest-directory discovery and do not create a second evidence directory.

The validation status file must be inside the exact evidence directory:

```text
<exact-evidence-directory>/controller-refactoring-005-validation-status.txt
```

Retain sequential evidence including, at minimum:

```text
001-working-directory.txt
002-branch.txt
003-head.txt
004-recent-commits.txt
005-stage-004-prerequisite.txt
006-git-status-pre.txt
007-git-diff-pre.txt
008-git-diff-cached-pre.txt
009-untracked-pre.txt
010-stash-fingerprint-pre.txt
011-governing-document-hashes.txt
012-registration-source-hashes-pre.txt
013-preserved-boundary-hashes-pre.txt
014-classification-checkpoint.txt
015-registration-reference-inventory.txt
016-registration-endpoint-baseline.txt
017-consolidated-design-manifest.txt
018-permitted-test-update-manifest.txt
019-fail-first-command.txt
020-fail-first-output.txt
021-fail-first-exit.txt
022-writer-command.txt
023-writer-output.txt
024-writer-exit.txt
025-registration-source-hashes-post.txt
026-structural-audit.txt
027-focused-architecture-command.txt
028-focused-architecture-output.txt
029-focused-architecture-exit.txt
030-registration-start-command.txt
031-registration-start-output.txt
032-registration-start-exit.txt
033-registration-otp-command.txt
034-registration-otp-output.txt
035-registration-otp-exit.txt
036-registration-username-command.txt
037-registration-username-output.txt
038-registration-username-exit.txt
039-registration-completion-cancel-command.txt
040-registration-completion-cancel-output.txt
041-registration-completion-cancel-exit.txt
042-registration-security-session-command.txt
043-registration-security-session-output.txt
044-registration-security-session-exit.txt
045-eligible-trust-preservation-command.txt
046-eligible-trust-preservation-output.txt
047-eligible-trust-preservation-exit.txt
048-stage-002-preservation-command.txt
049-stage-002-preservation-output.txt
050-stage-002-preservation-exit.txt
051-stage-003-preservation-command.txt
052-stage-003-preservation-output.txt
053-stage-003-preservation-exit.txt
054-stage-004-preservation-command.txt
055-stage-004-preservation-output.txt
056-stage-004-preservation-exit.txt
057-cumulative-controller-command.txt
058-cumulative-controller-output.txt
059-cumulative-controller-exit.txt
060-full-tests-command.txt
061-full-tests-output.txt
062-full-tests-exit.txt
063-compile-command.txt
064-compile-output.txt
065-compile-exit.txt
066-endpoint-diff-audit.txt
067-production-scope-audit.txt
068-test-scope-audit.txt
069-documentation-scope-audit.txt
070-preserved-boundary-hashes-post.txt
071-deferred-controller-hashes-post.txt
072-diff-check.txt
073-git-diff-final.txt
074-git-diff-cached-final.txt
075-git-status-final.txt
076-untracked-final.txt
077-stash-fingerprint-final.txt
078-diff-stat.txt
079-name-status.txt
080-command-exit-codes.txt
081-final-status.txt
controller-refactoring-005-validation-status.txt
controller-refactoring-005-final-review-bundle.txt
```

Do not duplicate complete Maven output into summary TXT files. Surefire XML reports are the authoritative test evidence. Retain exact commands, exits, concise summaries, report manifests, and required failure diagnostics.

Preserve historical failures. Corrections must append higher-numbered evidence in the same directory and define explicit precedence.

## 18. Required Validation

Discover exact current test names and use explicit class names. Do not use wildcard test selection.

### 18.1 Focused architecture

Run the new Registration controller consolidation architecture test.

### 18.2 Registration start and captcha gate

Run all controller, contract, captcha-gate, cookie, request-ID, language, workflow, and integration tests directly covering Registration start.

At minimum, inspect and include applicable current tests such as:

```text
AuthRegistrationControllerCaptchaGateTest
AuthRegistrationControllerWorkflowContractTest
RegistrationStartCookieMappingTest
RegistrationStartRequestDtoTest
RegistrationStartWorkflowResponseMapperTest
P6RegistrationStartIntegrationTest
RegistrationCaptchaAndStartIntegrationTest
RegistrationStartWorkflowIntegrationTest
```

### 18.3 Registration OTP lifecycle

Run all controller, mapper, resend, verification, retry-after, rate-limit, delivery, session-revision, policy, and integration tests directly covering Registration OTP.

At minimum, inspect and include applicable current tests such as:

```text
AuthRegistrationOtpResendControllerTest
AuthRegistrationOtpVerifyControllerTest
AuthRegistrationOtpResendControllerArchitectureGuardTest
AuthRegistrationOtpVerifyControllerArchitectureGuardTest
P7RegistrationOtpIntegrationTest
P7RegistrationOtpSecurityBoundaryGuardTest
RegistrationOtpChallengeLifecycleIntegrationTest
RegistrationOtpResendServiceIntegrationTest
RegistrationOtpVerificationLifecycleIntegrationTest
```

### 18.4 Registration username lifecycle

Run all username controller, contract, mapper, architecture, boundary, and integration tests.

At minimum, inspect and include applicable current tests such as:

```text
AuthRegistrationUsernameControllerTest
AuthRegistrationUsernameControllerArchitectureGuardTest
RegistrationUsernameCheckBoundaryArchitectureTest
RegistrationUsernameAvailabilityApimBoundaryArchitectureTest
RegistrationUsernameBoundaryIntegrationTest
RegistrationUsernameLifecycleIntegrationTest
```

### 18.5 Completion and cancellation

Run all completion, password, cancellation, cookie, lock, resilience, and end-to-end Registration tests that directly cover controller transport or ownership.

At minimum, inspect and include applicable current tests such as:

```text
AuthRegistrationCompleteCancelControllerTest
RegistrationCancelLifecycleIntegrationTest
RegistrationCompletionPasswordIntegrationTest
RegistrationCompletionPrerequisiteIntegrationTest
RegistrationCompletionResilienceIntegrationTest
RegistrationCompletionSuccessIntegrationTest
RegistrationEndToEndRegressionContractTest
RegistrationCancelWebContractGuardTest
```

### 18.6 Security and session

Run applicable Registration CSRF, Origin/Referer, required-request-ID, session, protected-context, idempotency, public-error, and structured-logging tests.

### 18.7 Eligible trust behavior

Run exact tests proving that additional registration remains possible when eligible unregistered trusts remain. If no focused test exists, add a preservation architecture or controller-level characterization test before production changes and include it in fail-first only if it genuinely describes a missing architectural guarantee. Do not alter application behavior.

### 18.8 Stage 002 preservation

Run the advice, internal reference-date, and related architecture protection suites.

### 18.9 Stage 003 preservation

Run `StandaloneCapabilityControllerArchitectureTest` and focused tests for the seven standalone capability controllers.

### 18.10 Stage 004 preservation

Run `LoginHttpBoundaryConsolidationArchitectureTest` and the accepted focused Login boundary suite.

### 18.11 Cumulative controller regression

Run the accepted cumulative controller suite from prior stages, updating selection only for current package and class ownership. Do not remove a test merely because a controller moved.

### 18.12 Full suite

After the last production or test edit, run:

```bash
mvn test
```

No full-suite exception is pre-authorized. If the full suite fails, preserve evidence and return `BLOCKED` unless Nick later supplies an explicit written Solution Owner exception.

### 18.13 Compile

After the final full test suite, run:

```bash
mvn -DskipTests compile
```

Any production edit after validation invalidates all final gates. Any test edit after validation requires rerunning affected focused gates, cumulative regression, full suite, and compile.

## 19. Automatic Background Validation

The complete Git-controlled `.cline` Stage 005 package must:

1. Apply the deterministic M365 Copilot-generated writer.
2. Launch validation exactly once in the background after writer success.
3. Use the explicit known Stage 005 evidence path.
4. Write `RUNNING` to the validation status file inside that directory.
5. Redirect Maven output directly to prescribed evidence files.
6. Poll the explicit status path until `PASS` or `BLOCKED`.
7. Avoid newest-directory discovery.
8. Avoid duplicate validation launches.
9. Store no validation status under `.cline/state`.
10. Require no manual validation by Nick.

Do not buffer complete Maven output in a shell variable.

## 20. Required Audits

Prove all of the following:

- one target `RegistrationController` source exists;
- all three old Registration controller sources are absent;
- no old-package compatibility controller exists;
- before and after effective endpoint sets are identical;
- endpoint count is unchanged;
- request and response types are unchanged;
- annotations, headers, cookies, validation, and status selectors are unchanged;
- constructor dependencies equal the required union after approved deduplication;
- application use cases remain separate;
- no outbound adapter, Redis implementation, APIM client, or persistence dependency was added;
- eligible unregistered trust behavior is preserved;
- registration completion still returns users to Login as currently implemented;
- `ApiStatus.DOWNSTREAM_REJECTED` is not exposed to the frontend;
- Stage 002, Stage 003, and Stage 004 boundaries retain hashes;
- Forgot Password controllers retain hashes;
- every changed and untracked file is classified;
- stash fingerprint and branch are unchanged;
- `git diff --check` passes;
- Stage 006 has not started.

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
docs/refactor/controller/controller-refactoring-prompt-005-registration-boundary.md
```

Update endpoint owners to `RegistrationController` without changing endpoint counts or contracts. Mark the three legacy classifications as completed consolidations. Preserve all prior stage history.

The cumulative `handover.md` Stage 005 section must include:

```text
Stage 004 architect PASS and prerequisite commit hash
Stage 005 identity and implementer status
exact evidence path
three source controllers consolidated into RegistrationController
preserved Registration endpoint set and count
constructor and helper collision decisions
eligible trust preservation result
focused validation totals by Registration area
Stage 002, Stage 003, and Stage 004 preservation totals
cumulative controller regression totals
full-suite totals
compile result
scope, branch, stash, and diff results
deferred Forgot Password work
stage_006_started=false
architect_status=PENDING
commit_authorization=false
```

Do not claim architect approval.

## 22. Final Review Bundle

Create:

```text
<exact-evidence-directory>/controller-refactoring-005-final-review-bundle.txt
```

Include complete evidence for:

1. stage identity and final implementer status;
2. branch, HEAD, recent commits, and Stage 004 prerequisite resolution;
3. pre-state Git, untracked files, and stash fingerprint;
4. complete three-controller classification;
5. exact consolidated design manifest;
6. before-and-after endpoint inventory;
7. before-and-after annotations, signatures, dependencies, helpers, and hashes;
8. genuine fail-first command, failure diagnostics, totals, chronology, and exit;
9. deterministic writer command, output, and exit;
10. faithful production, test, and documentation diffs;
11. all focused commands, Surefire summaries/manifests, and exits;
12. eligible trust preservation evidence;
13. Stage 002, Stage 003, and Stage 004 preservation evidence;
14. cumulative controller regression evidence;
15. full-suite command, Surefire totals/manifest, and exit;
16. compile command, concise evidence, and exit;
17. endpoint, ownership, dependency, scope, branch, stash, and diff audits;
18. final Git status, untracked files, diff stat, and name-status;
19. command exit summary;
20. validation-status content;
21. cumulative documentation updates;
22. `stage_006_started=false`;
23. `architect_status=PENDING`;
24. `commit_authorization=false`.

## 23. Completion Rules

Use `READY_FOR_ARCHITECT_REVIEW` only when:

- classification is `IMPLEMENTABLE_AS_DESIGNED`;
- genuine fail-first evidence is retained;
- exactly one `RegistrationController` replaces all three legacy controllers;
- all Registration contracts and eligible trust behavior are preserved;
- every focused and prior-stage preservation gate passes;
- cumulative regression passes;
- full suite passes after the final source edit;
- compile passes;
- all audits pass;
- documentation and handover are current;
- Stage 006 has not started.

Otherwise return `BLOCKED`, preserve evidence, and do not commit.

## 24. Final Console Output

Print only:

```text
implementation_status=<READY_FOR_ARCHITECT_REVIEW|BLOCKED>
architect_status=PENDING
commit_authorization=false
stage_006_started=false
stage_004_prerequisite_commit=<hash>
evidence_directory=<exact path>
validation_status=<PASS|BLOCKED>
final_review_bundle=<exact path>
approved_future_commit_subject=refactor: consolidate registration controller boundary
```
