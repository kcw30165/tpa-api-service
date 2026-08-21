# Prompt 003: Organize Standalone Controllers by Capability

## 1. Role and Authority

You are **M365 Copilot**, acting as the implementation agent for **Controller Refactoring Stage 003: Standalone Capability Packages**.

Nick is the Solution Owner and may explicitly accept residual validation risk. The architecture and deterministic change design in this prompt are authoritative. Do not redesign the target packages, broaden scope, consolidate journey controllers, or begin Stage 004.

Cline may be used only to apply M365 Copilot-designed deterministic changes and execute the prescribed validation and evidence workflow. Cline is not authorized to design or choose implementation changes.

## 2. Governing Documents

Read these files completely before editing:

```text
docs/refactor/controller/controller-refactoring-implementation-plan.md
docs/refactor/controller/controller-refactoring-classification.md
docs/refactor/controller/controller-refactoring-endpoint-inventory.md
docs/refactor/controller/controller-refactoring-prompt-001-classification.md
docs/refactor/controller/controller-refactoring-prompt-002-advice-internal-boundaries.md
handover.md
```

Read the Stage 001 and Stage 002 status recorded in `handover.md`. Stage 002 evidence may remain under its explicit historic path, but Stage 003 must not use newest-directory discovery.

If a governing document is missing or cumulative handover does not record Stage 002 architect PASS, stop with `BLOCKED` before production or test edits.

## 3. Stage Identity

```text
stage=003
stage_name=standalone-capability-packages
implementation_status=CLASSIFICATION_IN_PROGRESS
architect_status=PENDING
commit_authorization=false
stage_004_started=false
```

Remain on the current branch. Do not switch branches.

The exact required Stage 002 prerequisite subject is:

```text
refactor: separate controller advice and internal APIs
```

Resolve the prerequisite commit by capturing recent hashes and subjects, iterating through recent hashes, and comparing each subject in Bash. Do not use `grep` to verify the expected commit message.

The prerequisite commit must contain the approved Stage 002 package moves and must not contain unrelated changes. If the prerequisite cannot be resolved, stop with `BLOCKED`.

Commit subject allowed only after architect PASS:

```text
refactor: organize standalone controllers by capability
```

Do not commit during this prompt.

## 4. Approved Target Architecture

Move and rename exactly these standalone controllers:

```text
FROM:
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthCaptchaController.java
TO:
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/captcha/CaptchaController.java
PACKAGE:
com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha
CLASS:
CaptchaController
```

```text
FROM:
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthLogoutController.java
TO:
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/logout/LogoutController.java
PACKAGE:
com.bct.ngtpa.apiservice.adapter.in.web.auth.logout
CLASS:
LogoutController
```

```text
FROM:
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthUsernameRecoveryController.java
TO:
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/usernamerecovery/UsernameRecoveryController.java
PACKAGE:
com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery
CLASS:
UsernameRecoveryController
```

Move without renaming:

```text
ContributionController
web.controller -> web.contribution

NotificationController
web.controller -> web.notification

PersonalInformationController
web.controller -> web.personalinformation

ReferenceDataController
web.controller -> web.referencedata
```

Use the full base package:

```text
com.bct.ngtpa.apiservice.adapter.in.web
```

## 5. Explicitly Deferred Boundaries

Do not move, rename, merge, or otherwise edit:

```text
AuthLoginController
AuthLoginCancelController
AuthLoginOtpController
AuthLoginOtpVerifyController
AuthRegistrationController
AuthRegistrationOtpController
AuthRegistrationUsernameController
AuthForgotPasswordController
AuthForgotPasswordOtpController
```

Do not change the Stage 002 boundaries:

```text
web.advice.ApiExceptionHandler
web.internal.referencedate.ReferenceDateController
web.filter.AuthenticatedPortalContextWebExceptionHandler
```

Stage 004 login work must not start.

## 6. Public Contract Invariants

All controller moves and renames must preserve exactly:

- class and method mapping annotations;
- effective HTTP methods and paths;
- request headers, cookies, query parameters, and request bodies;
- request and response Java types;
- JSON contracts;
- HTTP status selection;
- validation behavior;
- security and authentication behavior;
- application input-port calls;
- response mapper and response-policy behavior;
- cookie publication and clearing behavior;
- request correlation;
- `Accept-Language` behavior;
- error handling through `web.advice.ApiExceptionHandler`;
- OpenAPI annotations, if present.

The approved endpoint inventory to preserve includes:

```text
POST  /api/v1/auth/captcha/challenge
POST  /api/v1/auth/captcha/verify
POST  /api/v1/auth/captcha/refresh
POST  /api/v1/auth/logout
POST  /api/v1/auth/username-recovery/start
POST  /api/v1/auth/username-recovery/request
POST  /api/v1/auth/username-recovery/cancel
GET   /api/v1/contributions
GET   /api/v1/contributions/export
GET   /api/v1/notifications
PATCH /api/v1/notifications
GET   /api/v1/personal-information
PUT   /api/v1/personal-information
GET   /api/v1/reference-data/countries
```

No endpoint may be added, removed, or changed.

## 7. Allowed Production Scope

Only the seven approved controller moves may change production source:

```text
AuthCaptchaController.java -> auth/captcha/CaptchaController.java
AuthLogoutController.java -> auth/logout/LogoutController.java
AuthUsernameRecoveryController.java -> auth/usernamerecovery/UsernameRecoveryController.java
ContributionController.java -> contribution/ContributionController.java
NotificationController.java -> notification/NotificationController.java
PersonalInformationController.java -> personalinformation/PersonalInformationController.java
ReferenceDataController.java -> referencedata/ReferenceDataController.java
```

Production edits are limited to:

- path movement;
- package declaration changes;
- the three approved class-name changes;
- constructor-name changes caused by class rename;
- mechanically required imports or direct references.

No method body, mapping, annotation, field, dependency, or behavior change is authorized.

## 8. Allowed Test Scope

You may update existing tests only where required by:

- moved or renamed production classes;
- source-path assertions;
- fully qualified class-name assertions;
- package ownership assertions;
- direct imports or constructor calls.

Add one focused architecture test that proves all seven target classes exist in their approved packages, the three renamed classes have their approved names, and the seven old source owners are absent.

Do not weaken behavior, contract, security, or architecture assertions. Do not delete tests merely because package names changed.

Relevant Stage 001 tests include, but are not limited to:

```text
AuthCaptchaControllerTest
AuthCaptchaControllerArchitectureGuardTest
AuthLogoutControllerTest
Task0043AuthenticatedLogoutHttpArchitectureTest
Task0043AuthenticatedLogoutContractArchitectureTest
AuthUsernameRecoveryControllerTest
UsernameRecoveryControllerContractTest
UsernameRecoveryControllerSecurityTest
UsernameRecoveryFeatureWebArchitectureGuardTest
ContributionControllerTest
ContributionCurrentContextBoundaryGuardTest
NotificationControllerTest
NotificationReferenceDateFlowTest
NotificationCurrentContextBoundaryGuardTest
PersonalInformationControllerContractTest
PersonalInformationControllerTest
UpdatePersonalInformationControllerContractTest
UpdatePersonalInformationControllerTest
PersonalInformationControllerCurrentContextArchitectureTest
ReferenceDataControllerTest
ReferenceDataCountriesControllerContractTest
GetReferenceDataCountriesCommandBoundaryGuardTest
SecurityConfigAuthDisabledTest
SecurityConfigAuthTest
```

Discover every exact reference before editing. The repository is authoritative.

## 9. Documentation Scope

Update only:

```text
handover.md
docs/refactor/controller/controller-refactoring-implementation-plan.md
docs/refactor/controller/controller-refactoring-classification.md
docs/refactor/controller/controller-refactoring-endpoint-inventory.md
```

Required documentation corrections:

- mark Stage 002 as committed and record its exact commit hash;
- mark Stage 003 implementation status and exact evidence path;
- update controller owners to the new fully qualified names;
- preserve all endpoint counts and public contracts;
- retain earlier stage history;
- record `stage_004_started=false`.

Do not rewrite later-stage architecture.

## 10. Mandatory Pre-Change Classification

Before editing, record for each of the seven controllers:

1. Current source path, package, class name, and SHA-256.
2. All class and method annotations.
3. Every effective endpoint.
4. Constructor dependencies.
5. Public method signatures and response types.
6. Every production and test reference to the current class and source path.
7. Package-private dependency or access risk.
8. Reflection, string-based architecture, Spring scan, or source-text dependency.
9. Exact target package and target class.
10. Whether the move is implementable without a prohibited change.

Also record the hashes and unchanged status of:

```text
web.advice.ApiExceptionHandler
web.internal.referencedate.ReferenceDateController
web.filter.AuthenticatedPortalContextWebExceptionHandler
all deferred login, registration, and Forgot Password controllers
```

Conclude with:

```text
IMPLEMENTABLE_AS_DESIGNED
```

or:

```text
BLOCKED
```

Do not edit production or tests unless the result is `IMPLEMENTABLE_AS_DESIGNED`.

## 11. Stop Conditions

Stop with `BLOCKED` if:

- the Stage 002 prerequisite commit is absent or incorrectly scoped;
- any target controller differs materially from the accepted Stage 001 classification;
- any public endpoint or behavior must change;
- package-private access requires unrelated production redesign;
- a non-target production class must change beyond an exact import or direct type reference;
- any login, registration, or Forgot Password controller must change;
- Stage 002 files require behavior changes;
- SecurityConfig or public error handling must change;
- unrelated repository changes cannot be classified;
- stash state changes;
- branch changes;
- Stage 004 has started.

Do not invent compatibility wrappers, duplicate controllers, deprecated aliases, forwarding classes, or temporary old-package classes.

## 12. Genuine Fail-First TDD

Before production changes, add a reflection and filesystem-based architecture test asserting:

- all seven approved target classes exist;
- all seven approved target source paths exist;
- all seven old source paths do not exist;
- renamed old fully qualified class names do not exist in source ownership;
- target annotations remain present.

The test must compile against the pre-change state and fail because the target package/classes do not yet exist. Use reflection for new runtime classes and filesystem checks for removal of old source ownership, avoiding stale `target/classes` false positives.

Retain exact command, complete output, totals, and nonzero exit. Do not recreate fail-first evidence after implementation.

## 13. Deterministic Implementation Writer

Use a M365 Copilot-designed deterministic Python writer invoked by an executable Bash script. Do not manually edit repository files.

The writer must:

1. Verify the Stage 002 prerequisite commit.
2. Verify exact pre-state SHA-256 values captured during classification.
3. Verify target files do not already exist.
4. Move each source atomically.
5. Replace only the exact package declaration.
6. Rename only the three approved classes and constructors.
7. Update exact repository references to moved or renamed types and source paths.
8. Reject broad text substitutions.
9. Preserve line endings where practical.
10. Stop on any unexpected marker, duplicate target, or hash mismatch.
11. Update the four approved Markdown documents deterministically.
12. Produce an explicit changed-path list.

Base all replacements on the cumulative expected state after the committed Stage 002 changes, not the initial repository state.

## 14. Validation Environment

Use exactly:

```bash
export JAVA_HOME="C:/Java/OpenJDK/jdk-21"
export M2_HOME="/d/Tools/apache-maven-3.9.15"
export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH"
```

Use `mvn`, not `mvnw`.

## 15. Evidence Directory

Create one timestamp at the start and retain this exact path:

```text
target/evidence/controller-refactoring-003-<yyyyMMdd-HHmmss>/
```

The exact validation status path is:

```text
<exact-evidence-directory>/controller-refactoring-003-validation-status.txt
```

Do not use newest-directory discovery. Do not create a second evidence directory.

Retain sequential evidence for:

```text
001-working-directory.txt
002-branch.txt
003-head.txt
004-recent-commits.txt
005-stage-002-prerequisite.txt
006-git-status-pre.txt
007-git-diff-pre.txt
008-git-diff-cached-pre.txt
009-untracked-pre.txt
010-stash-fingerprint-pre.txt
011-governing-document-hashes.txt
012-target-controller-hashes-pre.txt
013-preserved-boundary-hashes-pre.txt
014-classification-checkpoint.txt
015-reference-inventory.txt
016-endpoint-baseline.txt
017-fail-first-command.txt
018-fail-first-output.txt
019-fail-first-exit.txt
020-writer-command.txt
021-writer-output.txt
022-writer-exit.txt
023-target-controller-hashes-post.txt
024-focused-architecture-command.txt
025-focused-architecture-output.txt
026-focused-architecture-exit.txt
027-captcha-command.txt
028-captcha-output.txt
029-captcha-exit.txt
030-logout-command.txt
031-logout-output.txt
032-logout-exit.txt
033-username-recovery-command.txt
034-username-recovery-output.txt
035-username-recovery-exit.txt
036-business-capabilities-command.txt
037-business-capabilities-output.txt
038-business-capabilities-exit.txt
039-security-command.txt
040-security-output.txt
041-security-exit.txt
042-stage-002-preservation-command.txt
043-stage-002-preservation-output.txt
044-stage-002-preservation-exit.txt
045-cumulative-regression-command.txt
046-cumulative-regression-output.txt
047-cumulative-regression-exit.txt
048-full-tests-command.txt
049-full-tests-output.txt
050-full-tests-exit.txt
051-compile-command.txt
052-compile-output.txt
053-compile-exit.txt
054-endpoint-diff-audit.txt
055-production-scope-audit.txt
056-test-scope-audit.txt
057-documentation-scope-audit.txt
058-preserved-boundary-hashes-post.txt
059-deferred-controller-hashes-post.txt
060-diff-check.txt
061-git-diff-final.txt
062-git-diff-cached-final.txt
063-git-status-final.txt
064-untracked-final.txt
065-stash-fingerprint-final.txt
066-diff-stat.txt
067-name-status.txt
068-command-exit-codes.txt
069-final-status.txt
controller-refactoring-003-validation-status.txt
controller-refactoring-003-final-review-bundle.txt
```

Preserve all historical failures. If a correction is required, append higher-numbered evidence in the same directory and define explicit precedence.

## 16. Required Validation

Use explicit test class names, not wildcards.

### 16.1 Focused architecture

Run the Stage 003 architecture test proving all seven package boundaries and old-source absence.

### 16.2 Captcha

Run all directly affected captcha controller and architecture tests, including:

```text
AuthCaptchaControllerTest
AuthCaptchaControllerArchitectureGuardTest
CaptchaPublicErrorOwnershipGuardTest
```

Use actual current test class names after deterministic import updates.

### 16.3 Logout

Run all directly affected logout HTTP and architecture tests, including:

```text
AuthLogoutControllerTest
Task0043AuthenticatedLogoutHttpArchitectureTest
Task0043AuthenticatedLogoutContractArchitectureTest
```

### 16.4 Username Recovery

Run all controller, contract, security, composition, and feature web architecture tests directly referencing the moved controller.

### 16.5 Business capabilities

Run focused controller and contract suites for:

```text
ContributionController
NotificationController
PersonalInformationController
ReferenceDataController
```

Include current-context, contract, validation, and reference-date-flow guards directly affected by these controllers.

### 16.6 Security

Run:

```text
SecurityConfigAuthDisabledTest
SecurityConfigAuthTest
```

### 16.7 Stage 002 preservation

Run the Stage 002 architecture test plus advice and internal reference-date preservation suites. This gate must prove Stage 002 boundaries remain intact.

### 16.8 Cumulative regression

Run the accepted Stage 001 bounded controller suite, updated only for the new target class/package names. No test may be removed merely because a class moved.

### 16.9 Full suite

Because seven Spring controller components are moved or renamed, run:

```bash
mvn test
```

A full-suite exception is not pre-authorized. If the full suite fails, retain evidence and return `BLOCKED` for architect review unless Nick explicitly provides a later written Solution Owner exception.

### 16.10 Compile

After the final production or test edit and after the full suite, run:

```bash
mvn -DskipTests compile
```

Any production edit after validation invalidates all final gates. Any test edit after validation requires rerunning every affected gate, cumulative regression, and full suite.

## 17. Automatic Background Validation

The complete Git-controlled `.cline` Stage 003 package must:

1. Apply the deterministic writer.
2. Launch validation exactly once in the background after writer success.
3. Use the explicit known Stage 003 evidence path.
4. Write `RUNNING` to the validation-status file inside that directory.
5. Redirect command outputs directly to evidence files.
6. Poll the explicit validation-status path until `PASS` or `BLOCKED`.
7. Avoid newest-directory discovery.
8. Avoid duplicate launches.
9. Avoid `.cline/state` validation status files.
10. Require no manual validation by Nick.

Do not buffer complete Maven output in a variable.

## 18. Required Audits

Prove all of the following:

- old source paths for all seven controllers are absent;
- new target source paths are present;
- target class and package names are exact;
- all 14 approved endpoint method/path pairs are unchanged;
- request/response types and status selectors are unchanged;
- constructor dependency sets are unchanged;
- Stage 002 files are unchanged;
- deferred login, registration, and Forgot Password controllers are unchanged;
- no compatibility wrapper or duplicate controller exists;
- every changed and untracked file is classified;
- stash fingerprint is unchanged;
- diff check passes;
- Stage 004 has not started.

When checking commit subjects, use captured subjects and Bash substring matching, not `grep`.

## 19. Cumulative Handover

Update `handover.md` cumulatively with:

```text
Stage 002 architect PASS and commit hash
Stage 003 identity and final implementer status
exact Stage 003 evidence path
seven accepted moves
three accepted renames
14 preserved endpoints
focused validation totals by capability
Stage 002 preservation totals
cumulative regression totals
full-suite totals
compile result
scope and fingerprint result
residual or deferred items
stage_004_started=false
architect_status=PENDING
commit_authorization=false
```

Do not delete Stage 001 or Stage 002 context. Do not claim architect approval.

## 20. Final Review Bundle

Create:

```text
<exact-evidence-directory>/controller-refactoring-003-final-review-bundle.txt
```

Include complete evidence for:

1. stage identity and final implementer status;
2. branch, HEAD, recent commits, and Stage 002 prerequisite resolution;
3. pre-state Git, untracked files, and stash fingerprint;
4. classification checkpoint;
5. before-and-after paths, packages, names, annotations, mappings, signatures, dependencies, and hashes for all seven controllers;
6. genuine fail-first command, complete output, totals, and exit;
7. deterministic writer command, complete output, and exit;
8. faithful production, test, and documentation diffs;
9. all focused commands, complete outputs, totals, and exits;
10. Stage 002 preservation command, output, totals, and exit;
11. cumulative regression command, output, totals, and exit;
12. full-suite command, output, totals, and exit;
13. compile command, output, and exit;
14. endpoint contract comparison;
15. production, test, documentation, and deferred-boundary audits;
16. final Git status, diff, cached diff, untracked files, diff stat, and name-status;
17. final stash fingerprint;
18. command exit summary;
19. validation-status content;
20. cumulative handover and implementation-plan updates;
21. `stage_004_started=false`;
22. `architect_status=PENDING`;
23. `commit_authorization=false`.

## 21. Completion Rules

Use `READY_FOR_ARCHITECT_REVIEW` only when:

- classification is `IMPLEMENTABLE_AS_DESIGNED`;
- genuine fail-first evidence exists;
- all seven moves and three renames are exact;
- all public contracts are preserved;
- every required final gate passes after the last relevant edit;
- full suite and compile pass;
- scope, diff, Git, and stash audits pass;
- documentation is cumulative and current;
- final review bundle is complete;
- Stage 004 has not started.

Otherwise use `BLOCKED`, preserve evidence, and do not commit.

## 22. Final Console Output

Print only:

```text
implementation_status=<READY_FOR_ARCHITECT_REVIEW|BLOCKED>
architect_status=PENDING
commit_authorization=false
stage_004_started=false
stage_002_prerequisite_commit=<hash>
evidence_directory=<exact path>
validation_status=<PASS|BLOCKED>
final_review_bundle=<exact path>
approved_future_commit_subject=refactor: organize standalone controllers by capability
```
