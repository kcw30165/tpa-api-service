# Prompt 004: Consolidate the Login Controller Boundary

## 1. Role and Authority

You are **M365 Copilot**, acting as the implementation agent for **Controller Refactoring Stage 004: Login HTTP Boundary**.

Nick is the Solution Owner. M365 Copilot owns the architecture, implementation design, deterministic code changes, validation design, and final review. Cline may only apply M365 Copilot-generated deterministic changes and run the prescribed validation and evidence workflow. Cline must not design, improvise, broaden scope, or choose an alternative implementation.

Do not begin Stage 005.

## 2. Governing Documents

Read these files completely before changing the repository:

```text
docs/refactor/controller/controller-refactoring-implementation-plan.md
docs/refactor/controller/controller-refactoring-classification.md
docs/refactor/controller/controller-refactoring-endpoint-inventory.md
docs/refactor/controller/controller-refactoring-prompt-001-classification.md
docs/refactor/controller/controller-refactoring-prompt-002-advice-internal-boundaries.md
docs/refactor/controller/controller-refactoring-prompt-003-standalone-capabilities.md
handover.md
```

Use the committed cumulative repository state. Do not reconstruct files from the original pre-refactoring state.

Stop with `BLOCKED` before production or test changes if a governing document is missing or `handover.md` does not record Stage 003 architect PASS.

## 3. Stage Identity and Prerequisite

```text
stage=004
stage_name=login-http-boundary
implementation_status=CLASSIFICATION_IN_PROGRESS
architect_status=PENDING
commit_authorization=false
stage_005_started=false
```

Remain on the current branch.

Resolve the exact Stage 003 prerequisite commit whose subject is:

```text
refactor: organize standalone controllers by capability
```

Do not use `grep` to verify the subject. Capture recent hashes and subjects, iterate through the recent hashes in Bash, compare each subject, and retain the resolved hash.

Verify that the Stage 003 commit contains the seven approved controller ownership changes, the Stage 003 architecture test, directly affected tests, and controller-refactoring documentation, with no Stage 004 production work.

Stop with `BLOCKED` if the prerequisite cannot be resolved or its scope is inconsistent.

Commit subject allowed only after architect PASS:

```text
refactor: consolidate login controller boundary
```

Do not commit during this prompt.

## 4. Architectural Decision

Consolidate these four HTTP controllers:

```text
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthLoginController.java
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthLoginCancelController.java
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthLoginOtpController.java
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthLoginOtpVerifyController.java
```

into exactly one controller:

```text
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/login/LoginController.java
```

with package and class:

```java
package com.bct.ngtpa.apiservice.adapter.in.web.auth.login;

public final class LoginController
```

If repository evidence proves that one or more current classes are not `final`, preserve the effective visibility and finality necessary for Spring and tests, but do not create compatibility classes. Record any finality adjustment in evidence.

The resulting controller owns only the existing Login HTTP transport boundary. Existing application ports, commands, mappers, response policies, cookie writers, session components, and use cases remain separate.

## 5. Mandatory Repository-Derived Method Design

Before implementation, derive the exact consolidated controller from the four committed source files. Do not invent endpoint methods, constructor dependencies, annotations, status policies, or helper behavior.

The consolidated `LoginController` must contain faithful copies of the four controllers' existing:

- class and method mapping annotations;
- endpoint methods;
- constructor dependencies;
- private constants;
- private transport helpers;
- request-header, cookie, origin, referer, CSRF, idempotency, and request-ID handling;
- response mapping and HTTP status selection;
- session-cookie publication and clearing;
- typed exception translation;
- reactive behavior.

Where two source controllers contain identically named private helpers or constants:

1. Compare behavior and signatures.
2. Reuse one helper only when implementations are semantically identical.
3. Otherwise retain both with deterministic descriptive private names.
4. Do not alter endpoint behavior to reduce duplication.

Where constructor dependencies repeat, declare one field and one constructor parameter for the same exact type and responsibility. Preserve dependency order deterministically by first appearance in this controller order:

```text
AuthLoginController
AuthLoginCancelController
AuthLoginOtpController
AuthLoginOtpVerifyController
```

If two dependencies share a type but represent different responsibilities or qualifiers, retain distinct fields and constructor parameters.

## 6. Contract Preservation

Stage 004 must preserve every Login endpoint currently proven by the canonical endpoint inventory. Before writing the consolidated class, extract the exact effective HTTP method/path set from the four source controllers and write it to evidence.

At minimum, the audit must cover the current Login lifecycle categories:

- login start/authentication;
- login cancellation;
- OTP send or resend;
- OTP verification;
- authenticated session transition and cookie publication where currently applicable.

The repository inventory and annotations are authoritative. Do not infer paths from filenames or this prompt.

Preserve exactly:

- HTTP method and effective route;
- consumes and produces values;
- request DTOs and response DTOs;
- headers and cookies;
- required versus optional inputs;
- HTTP status codes;
- `MutationResponse` and other public envelopes;
- validation behavior;
- `Accept-Language` behavior;
- `X-Request-Id` behavior;
- idempotency behavior;
- CSRF, Origin, and Referer behavior;
- authentication and security behavior;
- session creation, transition, cookie publication, and cookie clearing;
- Account-Ref semantics;
- APIM and TAS contract behavior behind application ports;
- error-code and public-status mapping;
- OpenAPI annotations if present.

No public API change is authorized.

## 7. Thin-Controller Boundary

The consolidated class may adapt HTTP input and output only. It must not absorb or redesign:

- authentication policy;
- OTP generation, storage, resend, verification, or attempt policy;
- captcha policy;
- Redis access;
- APIM execution;
- TAS response interpretation;
- account selection;
- session persistence;
- password lock policy;
- business eligibility;
- localization ownership;
- error normalization already owned elsewhere.

Do not move application services, ports, mappers, response policies, DTOs, or cookie components into `LoginController`.

## 8. Allowed Production Scope

Production changes are limited to:

```text
DELETE/MOVE:
AuthLoginController.java
AuthLoginCancelController.java
AuthLoginOtpController.java
AuthLoginOtpVerifyController.java

CREATE:
auth/login/LoginController.java
```

Mechanically required direct references to the four old types may be updated only if discovered in production configuration or explicit imports. No unrelated production class may change.

The new controller must be deterministic content assembled from the four committed controllers, with only these structural changes:

- target package declaration;
- target class name;
- merged imports;
- merged fields and constructor;
- endpoint methods and required private helpers copied faithfully;
- duplicate identical imports removed;
- collisions resolved without behavior change.

Do not create old-package aliases, forwarding controllers, deprecated wrappers, duplicate Spring beans, inheritance bridges, or temporary compatibility classes.

## 9. Deferred and Preserved Production Scope

Do not modify:

- Registration controllers;
- Forgot Password controllers;
- Captcha, Logout, Username Recovery controllers;
- Contribution, Notification, Personal Information, Reference Data controllers;
- `web.advice.ApiExceptionHandler`;
- `web.internal.referencedate.ReferenceDateController`;
- `AuthenticatedPortalContextWebExceptionHandler`;
- `SecurityConfig`;
- application ports, services, DTOs, mappers, response policies, Redis, APIM, or configuration.

Stage 005 must not start.

## 10. Allowed Test Scope

Update only tests directly affected by:

- imports or constructors of the four old controllers;
- source-path assertions;
- class-name or package assertions;
- Spring test configuration importing an old controller;
- architecture ownership rules;
- endpoint ownership assertions.

Add one Stage 004 architecture test that proves:

- `web.auth.login.LoginController` exists;
- it is a Spring REST controller;
- it owns the complete effective mapping set extracted before implementation;
- all four old source paths are absent;
- all four old controller FQCNs have no source owner;
- there is only one Login controller bean owner;
- the consolidated controller does not directly depend on outbound adapters, Redis implementations, APIM clients, or persistence implementations.

Do not weaken existing tests. Keep existing test class names unless a move is required for package-private access. Prefer imports over moving tests.

## 11. Mandatory Pre-Change Classification

Before any edit, record for all four source controllers:

1. SHA-256 and exact source path.
2. Package, class visibility, and finality.
3. Class annotations.
4. Every method mapping and effective route.
5. Every public endpoint signature.
6. Constructor and field dependencies, including qualifiers.
7. Private constants and helpers.
8. Cookie, CSRF, idempotency, request-ID, locale, session, and error behavior.
9. Every production and test reference to the source path, FQCN, and simple class name.
10. Package-private access risks.
11. Reflection and source-text tests.
12. Duplicate dependency, import, field, constant, method, and helper-name collisions.
13. Exact proposed consolidated constructor.
14. Exact proposed endpoint-method inventory.
15. Exact list of permitted test updates.

Record hashes of every deferred and previously moved controller boundary for final preservation comparison.

Conclude with exactly one:

```text
IMPLEMENTABLE_AS_DESIGNED
```

```text
BLOCKED
```

Do not edit if classification is `BLOCKED`.

## 12. Stop Conditions

Stop with `BLOCKED` if:

- the Stage 003 prerequisite is absent or incorrectly scoped;
- effective route uniqueness cannot be proven;
- consolidation would change a mapping, input, response, status, session, cookie, security, or error contract;
- a helper collision cannot be resolved mechanically without behavior design;
- package-private production access requires unrelated redesign;
- an application service or outbound adapter must change;
- distinct Spring beans or qualifiers cannot be preserved in one constructor;
- a non-Login feature must change;
- an unrelated working-tree change cannot be classified;
- stash state or branch changes;
- Stage 005 work is present.

Do not invent a workaround.

## 13. Genuine Fail-First TDD

Before production changes, create the Stage 004 architecture test using reflection and filesystem checks.

The test must compile in the pre-change repository and fail because:

```text
com.bct.ngtpa.apiservice.adapter.in.web.auth.login.LoginController
```

does not yet exist and the four old source owners still exist.

Retain:

- exact Maven command;
- complete output;
- test totals;
- nonzero exit;
- failure proving the pre-change architecture defect.

Use filesystem checks for old source absence to avoid stale `target/classes` false positives. Do not recreate fail-first evidence later.

## 14. Deterministic Implementation Artifacts

M365 Copilot must generate a deterministic Python writer and an executable Bash orchestrator. Do not manually edit repository files.

The writer must:

1. Verify branch and Stage 003 prerequisite hash.
2. Verify all four source hashes and expected structural markers.
3. Verify the target does not exist.
4. Parse and assemble the target from the current four sources or write a complete architect-generated replacement whose content is verified against extracted method bodies.
5. Preserve endpoint method bodies exactly except qualification/import changes required by consolidation.
6. Resolve duplicate fields and helpers only according to the classification record.
7. Update exact test references and source paths from a closed replacement manifest.
8. Reject unlisted replacements and unexpected match counts.
9. Delete the four old source files only after the target is fully written and validated structurally.
10. Update the four canonical controller-refactoring Markdown documents and cumulative `handover.md`.
11. Produce a changed-path manifest.
12. Stop safely on any pre-state mismatch.

The Bash orchestrator must invoke `python`, not `python3`.

## 15. Maven Environment

Use exactly:

```bash
export JAVA_HOME="C:/Java/OpenJDK/jdk-21"
export M2_HOME="/d/Tools/apache-maven-3.9.15"
export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH"
```

Use `mvn`, not `mvnw`.

## 16. Evidence Directory

Create one exact timestamped directory:

```text
target/evidence/controller-refactoring-004-<yyyyMMdd-HHmmss>/
```

Retain and reuse the explicit path. Do not use newest-directory discovery.

The validation status must be:

```text
<exact-evidence-directory>/controller-refactoring-004-validation-status.txt
```

Retain sequential evidence, including at minimum:

```text
001-working-directory.txt
002-branch.txt
003-head.txt
004-recent-commits.txt
005-stage-003-prerequisite.txt
006-git-status-pre.txt
007-git-diff-pre.txt
008-git-diff-cached-pre.txt
009-untracked-pre.txt
010-stash-fingerprint-pre.txt
011-governing-document-hashes.txt
012-login-source-hashes-pre.txt
013-preserved-boundary-hashes-pre.txt
014-classification-checkpoint.txt
015-login-reference-inventory.txt
016-login-endpoint-baseline.txt
017-consolidated-design-manifest.txt
018-fail-first-command.txt
019-fail-first-output.txt
020-fail-first-exit.txt
021-writer-command.txt
022-writer-output.txt
023-writer-exit.txt
024-login-source-hashes-post.txt
025-structural-audit.txt
026-focused-architecture-command.txt
027-focused-architecture-output.txt
028-focused-architecture-exit.txt
029-login-start-command.txt
030-login-start-output.txt
031-login-start-exit.txt
032-login-cancel-command.txt
033-login-cancel-output.txt
034-login-cancel-exit.txt
035-login-otp-command.txt
036-login-otp-output.txt
037-login-otp-exit.txt
038-login-otp-verify-command.txt
039-login-otp-verify-output.txt
040-login-otp-verify-exit.txt
041-login-security-session-command.txt
042-login-security-session-output.txt
043-login-security-session-exit.txt
044-stage-002-preservation-command.txt
045-stage-002-preservation-output.txt
046-stage-002-preservation-exit.txt
047-stage-003-preservation-command.txt
048-stage-003-preservation-output.txt
049-stage-003-preservation-exit.txt
050-cumulative-controller-command.txt
051-cumulative-controller-output.txt
052-cumulative-controller-exit.txt
053-full-tests-command.txt
054-full-tests-output.txt
055-full-tests-exit.txt
056-compile-command.txt
057-compile-output.txt
058-compile-exit.txt
059-endpoint-diff-audit.txt
060-production-scope-audit.txt
061-test-scope-audit.txt
062-documentation-scope-audit.txt
063-preserved-boundary-hashes-post.txt
064-deferred-controller-hashes-post.txt
065-diff-check.txt
066-git-diff-final.txt
067-git-diff-cached-final.txt
068-git-status-final.txt
069-untracked-final.txt
070-stash-fingerprint-final.txt
071-diff-stat.txt
072-name-status.txt
073-command-exit-codes.txt
074-final-status.txt
controller-refactoring-004-validation-status.txt
controller-refactoring-004-final-review-bundle.txt
```

Preserve all failures. Corrections must append higher-numbered evidence in the same exact directory and state precedence.

## 17. Required Validation

Discover exact current test names and use explicit names only.

### 17.1 Focused architecture

Run the new Login boundary architecture test.

### 17.2 Login start

Run all tests that directly cover or inspect `AuthLoginController`, updated to `LoginController` where required.

### 17.3 Login cancellation

Run all controller, contract, source-path, CSRF, cookie, and architecture tests directly covering `AuthLoginCancelController`.

### 17.4 Login OTP send/resend

Run all controller, mapper, policy, lifecycle, rate-limit, delivery, session, and architecture tests directly covering `AuthLoginOtpController`.

### 17.5 Login OTP verification

Run all controller, transport, cookie-publication, authenticated-session-transition, replay, idempotency, and architecture tests directly covering `AuthLoginOtpVerifyController`.

### 17.6 Login security and session boundary

Run applicable Login security, CSRF, session-cookie, authenticated-session, account-list, and portal-context boundary tests discovered during classification.

### 17.7 Stage 002 preservation

Run the Stage 002 architecture, advice, internal reference-date, and security preservation suites.

### 17.8 Stage 003 preservation

Run `StandaloneCapabilityControllerArchitectureTest` and focused tests for Captcha, Logout, Username Recovery, Contribution, Notification, Personal Information, and Reference Data.

### 17.9 Cumulative controller regression

Run the accepted controller suite from prior stages with test selection updated only for current class/package references.

### 17.10 Full suite

Run:

```bash
mvn test
```

The full suite must run after the last production or test edit. No validation exception is pre-authorized.

### 17.11 Compile

After the final full suite, run:

```bash
mvn -DskipTests compile
```

Any production edit after validation invalidates every final gate. Any test edit requires rerunning affected focused gates, cumulative regression, full suite, and compile.

## 18. Automatic Background Validation

The complete Git-controlled `.cline` Stage 004 package must:

1. Apply the deterministic M365 Copilot-generated writer.
2. Launch validation exactly once in the background after writer success.
3. Use the explicit known evidence path.
4. Write `RUNNING` to the status file inside that evidence directory.
5. Redirect Maven output directly to evidence files.
6. Poll the explicit status path until `PASS` or `BLOCKED`.
7. Avoid newest-directory discovery.
8. Avoid duplicate validation launches.
9. Store no validation status under `.cline/state`.
10. Require no manual validation by Nick.

Do not buffer complete Maven output in a shell variable.

## 19. Required Audits

Prove:

- one target `LoginController` source exists;
- four old Login controller sources are absent;
- no old-package compatibility class exists;
- the before and after effective endpoint sets are identical;
- endpoint count is unchanged;
- all request/response types, annotations, status selectors, and security inputs are preserved;
- constructor dependencies equal the union of required source dependencies after valid deduplication;
- application ports and use cases remain separate;
- no direct outbound adapter, Redis implementation, APIM client, or persistence dependency was added;
- Stage 002 and Stage 003 boundaries retain hashes;
- Registration and Forgot Password controllers retain hashes;
- every changed and untracked file is classified;
- stash fingerprint and branch are unchanged;
- diff check passes;
- Stage 005 has not started.

## 20. Documentation Updates

Update only:

```text
handover.md
docs/refactor/controller/controller-refactoring-implementation-plan.md
docs/refactor/controller/controller-refactoring-classification.md
docs/refactor/controller/controller-refactoring-endpoint-inventory.md
```

Also add this prompt under:

```text
docs/refactor/controller/controller-refactoring-prompt-004-login-boundary.md
```

Update controller owners in the endpoint inventory without changing contracts or endpoint counts. Mark the four old classifications as consolidated into the final `LoginController`. Retain all prior stage history.

The cumulative `handover.md` Stage 004 section must include:

```text
Stage 003 architect PASS and commit hash
Stage 004 identity and implementer status
exact evidence path
four source controllers consolidated into LoginController
preserved Login endpoint set and count
constructor and helper collision decisions
focused validation totals
Stage 002 and Stage 003 preservation totals
cumulative regression totals
full-suite totals
compile result
scope, branch, and stash results
deferred Registration and Forgot Password work
stage_005_started=false
architect_status=PENDING
commit_authorization=false
```

Do not claim architect approval.

## 21. Final Review Bundle

Create:

```text
<exact-evidence-directory>/controller-refactoring-004-final-review-bundle.txt
```

Include complete, untruncated evidence for:

1. stage identity and final implementer status;
2. branch, HEAD, recent commits, and Stage 003 prerequisite;
3. pre-state status, diff, untracked files, and stash fingerprint;
4. complete four-controller classification;
5. exact consolidated design manifest;
6. before-and-after endpoint inventory;
7. before-and-after dependencies, helpers, annotations, signatures, and source hashes;
8. genuine fail-first command, complete output, totals, and exit;
9. writer command, output, and exit;
10. faithful production, test, and documentation diffs;
11. every focused validation command, complete output, totals, and exit;
12. Stage 002 and Stage 003 preservation evidence;
13. cumulative controller regression evidence;
14. full-suite command, complete output, totals, and exit;
15. compile command, complete output, and exit;
16. endpoint, source ownership, dependency, scope, diff, branch, and stash audits;
17. final Git status, untracked files, diff stat, and name-status;
18. command exit summary;
19. validation-status content;
20. cumulative handover and documentation updates;
21. `stage_005_started=false`;
22. `architect_status=PENDING`;
23. `commit_authorization=false`.

## 22. Completion Rules

Use `READY_FOR_ARCHITECT_REVIEW` only when:

- classification is `IMPLEMENTABLE_AS_DESIGNED`;
- fail-first evidence is genuine;
- one exact `LoginController` replaces all four source controllers;
- all public contracts are preserved;
- every focused and preservation gate passes;
- cumulative regression passes;
- full suite passes after the final edit;
- compile passes;
- audits pass;
- documentation and handover are current;
- Stage 005 has not started.

Otherwise return `BLOCKED`, preserve evidence, and do not commit.

## 23. Final Console Output

Print only:

```text
implementation_status=<READY_FOR_ARCHITECT_REVIEW|BLOCKED>
architect_status=PENDING
commit_authorization=false
stage_005_started=false
stage_003_prerequisite_commit=<hash>
evidence_directory=<exact path>
validation_status=<PASS|BLOCKED>
final_review_bundle=<exact path>
approved_future_commit_subject=refactor: consolidate login controller boundary
```
