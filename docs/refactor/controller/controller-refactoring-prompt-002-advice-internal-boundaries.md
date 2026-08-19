# Prompt 002: Advice and Internal API Boundaries

## 1. Role and Authority

You are the repository implementer for **Controller Refactoring Stage 002: Advice and Internal API Boundaries**.

You may apply only the deterministic changes defined in this prompt. You must not redesign the architecture, broaden scope, choose alternative package boundaries, refactor unrelated dependencies, or begin Stage 003.

## 2. Governing Documents

Read these files completely before changing the repository:

```text
docs/refactor/controller/controller-refactoring-implementation-plan.md
docs/refactor/controller/controller-refactoring-classification.md
docs/refactor/controller/controller-refactoring-endpoint-inventory.md
handover.md
```

Also inspect the Stage 001 evidence retained at:

```text
target/evidence/controller-refactoring-001-20260819-160419/
```

Use this corrected-evidence precedence:

```text
target/evidence/controller-refactoring-001-20260819-160419/082-superseded-evidence-manifest.txt
```

If any governing document is missing, if the Stage 001 evidence path is missing, or if the evidence-precedence file is missing, stop with `BLOCKED` before editing production or test code.

## 3. Stage Identity

```text
stage=002
stage_name=advice-and-internal-api-boundaries
implementation_status=CLASSIFICATION_IN_PROGRESS
architect_status=PENDING
commit_authorization=false
stage_003_started=false
```

Remain on the current branch. Do not switch branches.

The exact commit subject authorized for Stage 001 was:

```text
docs: classify controller refactoring boundaries
```

Stage 002 may proceed only if that subject exists in recent Git history and the corresponding commit contains the approved Stage 001 documentation scope. Resolve the commit without `grep`: capture recent hashes and subjects, iterate over the hashes in Bash, compare each subject, and retain the resolved hash as the explicit Stage 002 prerequisite.

If the approved Stage 001 commit cannot be resolved, stop with `BLOCKED`.

Commit subject allowed only after architect PASS for this stage:

```text
refactor: separate controller advice and internal APIs
```

Do not commit during this prompt.

## 4. Approved Target Architecture

Apply exactly these production moves:

```text
FROM:
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ApiExceptionHandler.java

TO:
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/advice/ApiExceptionHandler.java

PACKAGE FROM:
com.bct.ngtpa.apiservice.adapter.in.web.controller

PACKAGE TO:
com.bct.ngtpa.apiservice.adapter.in.web.advice
```

```text
FROM:
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ReferenceDateController.java

TO:
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/internal/referencedate/ReferenceDateController.java

PACKAGE FROM:
com.bct.ngtpa.apiservice.adapter.in.web.controller

PACKAGE TO:
com.bct.ngtpa.apiservice.adapter.in.web.internal.referencedate
```

The following boundaries are fixed:

- `ApiExceptionHandler` remains a `@RestControllerAdvice` component.
- `ReferenceDateController` remains the sole owner of the three existing internal reference-date endpoints.
- `ReferenceDataController` remains unchanged in the old controller package during Stage 002.
- `AuthenticatedPortalContextWebExceptionHandler` remains unchanged under `adapter.in.web.filter`.
- The existing `ApiExceptionHandler -> infrastructure.logging.LoggingSanitizer` dependency is preserved exactly for this stage.
- No new port, adapter, façade, wrapper, or logging abstraction may be introduced.

## 5. Public Contract Invariants

The move must preserve exactly:

### 5.1 `ReferenceDateController` endpoints

```text
POST   /api/v1/internal/reference-date/refresh
GET    /api/v1/internal/reference-date/all
DELETE /api/v1/internal/reference-date/all
```

Preserve:

- class-level and method-level mappings;
- request and response Java types;
- JSON shapes;
- validation annotations and validation outcomes;
- HTTP statuses;
- generic API and `MutationResponse` contracts;
- `deletedCount=0` success behavior when no matching reference-date entries exist;
- sorting of GET results by `accountEnv`;
- all application input-port calls;
- internal-route authentication behavior from active `SecurityConfig`;
- OpenAPI annotations, if present.

### 5.2 `ApiExceptionHandler`

Preserve:

- every handled exception type;
- exception-handler precedence;
- HTTP statuses;
- public error codes and response envelopes;
- `Accept-Language` behavior;
- request-ID behavior;
- account-context resolution behavior;
- logging event shape and severity;
- logging sanitization;
- the existing `LoggingSanitizer` dependency;
- behavior for malformed requests, validation failures, application failures, APIM failures, cache failures, authentication failures, authorization failures, and unknown exceptions.

No runtime behavior change is authorized.

## 6. Allowed Scope

### 6.1 Production files

Only these production paths may change:

```text
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ApiExceptionHandler.java
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/advice/ApiExceptionHandler.java
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ReferenceDateController.java
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/internal/referencedate/ReferenceDateController.java
```

Changes to the old paths must be deletions caused by the two approved moves. Changes to the new paths must be faithful moved content plus the required package declaration change only.

### 6.2 Test files

You may update or create only tests directly required to:

- update imports or package declarations affected by these two moves;
- prove `ApiExceptionHandler` is in `web.advice`;
- prove `ReferenceDateController` is in `web.internal.referencedate`;
- prove the old flat package no longer owns either moved type;
- preserve the three internal reference-date endpoint contracts;
- preserve exception-advice behavior.

Prefer moving package-aligned tests rather than rewriting behavior tests. Do not rename existing test classes unless package alignment requires a deterministic move.

Candidate existing tests discovered in Stage 001 include:

```text
src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ApiExceptionHandlerLoggingTest.java
src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ApiExceptionHandlerMutationResponseContractTest.java
src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ApiExceptionHandlerTest.java
src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ApiResponseExceptionHandlingBoundaryTest.java
src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ReferenceDateAllControllerTest.java
src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ReferenceDateAllMutationResponseContractTest.java
src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ReferenceDateRefreshControllerTest.java
src/test/java/com/bct/ngtpa/apiservice/config/SecurityConfigAuthDisabledTest.java
src/test/java/com/bct/ngtpa/apiservice/config/SecurityConfigAuthTest.java
```

Inspect exact imports and package-sensitive assertions before editing. An existing test may remain in its current test package if it imports the moved production type and no package-private access is required.

### 6.3 Documentation

Update only:

```text
handover.md
docs/refactor/controller/controller-refactoring-implementation-plan.md
```

The implementation plan update is limited to Stage 002 status and accepted package facts. Do not rewrite other stages or earlier architectural decisions.

### 6.4 Cline package

If using Cline CLI, create or update the complete Git-controlled `.cline` Stage 002 package only as needed to apply the architect-defined deterministic writer and run the prescribed evidence workflow. The package must not contain design discretion.

## 7. Prohibited Scope

Do not modify:

- `ReferenceDataController`;
- `AuthenticatedPortalContextWebExceptionHandler`;
- any authentication controller;
- contribution, notification, or personal-information controllers;
- DTOs;
- application ports or use cases;
- Redis code;
- APIM code;
- `LoggingSanitizer`;
- security policy or `SecurityConfig`;
- error codes or message configuration;
- endpoint paths;
- Maven dependencies;
- application configuration;
- Stage 003 or later-stage files.

Do not consolidate controllers in this stage.

## 8. Mandatory Pre-Change Classification Checkpoint

Before editing, record:

1. Exact contents and hashes of both source controllers.
2. Every production and test reference to each fully qualified class name.
3. Every test that uses package-private access to either class.
4. Every reflection, string-literal, architecture-rule, Spring configuration, component-scan, or serialization dependency on the old packages.
5. The exact annotations and mappings on both production classes.
6. The exact constructor dependencies.
7. The exact response Java types and status selectors for all three reference-date endpoints.
8. The current exception-handler methods and exception precedence.
9. The exact active security rules for `/api/v1/internal/**`.
10. Whether the two moves are implementable without changing any prohibited file.

Classification must conclude with one of:

```text
IMPLEMENTABLE_AS_DESIGNED
BLOCKED
```

Do not edit production or tests unless the result is `IMPLEMENTABLE_AS_DESIGNED`.

## 9. Stop Conditions

Stop with `BLOCKED` and do not invent a workaround if:

- Stage 001 is not committed with the approved subject;
- either source class differs materially from the Stage 001 classification;
- another production file must change beyond import/reference updates authorized here;
- moving either class requires a public API or behavioral change;
- package-private collaborators make the approved move impossible without broader redesign;
- `LoggingSanitizer` must be changed or replaced;
- `SecurityConfig` must change;
- `ReferenceDataController` must move at the same time;
- a route, status, response body, validation rule, error mapping, or security behavior would change;
- unrelated tracked or untracked changes cannot be classified;
- the stash fingerprint changes;
- the branch changes;
- Stage 003 work has started.

## 10. Genuine Fail-First TDD

Before moving production classes, add focused architecture tests that assert:

```text
com.bct.ngtpa.apiservice.adapter.in.web.advice.ApiExceptionHandler exists
com.bct.ngtpa.apiservice.adapter.in.web.internal.referencedate.ReferenceDateController exists
old flat package does not own ApiExceptionHandler after implementation
old flat package does not own ReferenceDateController after implementation
ApiExceptionHandler retains @RestControllerAdvice
ReferenceDateController retains @RestController
```

The fail-first test must fail against the unchanged production state because the approved target classes do not yet exist.

Retain:

- exact Maven command;
- complete output;
- nonzero exit code;
- test totals;
- exact assertion or compilation failure proving the pre-change architecture defect.

Do not recreate or simulate fail-first evidence after production changes.

If a compile-time class reference prevents the fail-first test from compiling, use reflection by fully qualified class name so the test compiles and fails as an assertion against the pre-change package layout.

## 11. Deterministic Implementation

After genuine fail-first evidence is retained:

1. Move `ApiExceptionHandler.java` to the exact approved path.
2. Change only its package declaration from `web.controller` to `web.advice`.
3. Preserve all imports, annotations, fields, methods, and behavior except import adjustments mechanically required by the package move.
4. Move `ReferenceDateController.java` to the exact approved path.
5. Change only its package declaration from `web.controller` to `web.internal.referencedate`.
6. Preserve all imports, annotations, fields, methods, mappings, and behavior except import adjustments mechanically required by the package move.
7. Update exact production and test references to the new fully qualified names.
8. Update package-sensitive tests without weakening assertions.
9. Add or finalize the focused architecture test created for fail-first.
10. Update `handover.md` cumulatively.
11. Update only the Stage 002 status section of the implementation plan.

Use a deterministic writer or executable Bash script for all changes. Do not manually edit repository files. The writer must verify expected pre-state hashes or exact source markers before replacement and stop safely if the expected state does not match.

The writer must preserve cumulative expected state from the committed Stage 001 documentation. It must not reconstruct files from the original pre-Stage-001 state.

## 12. Validation Environment

Use exactly:

```bash
export JAVA_HOME="C:/Java/OpenJDK/jdk-21"
export M2_HOME="/d/Tools/apache-maven-3.9.15"
export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH"
```

Use `mvn`, not `mvnw`.

## 13. Exact Evidence Directory

Create the timestamp once and retain the exact path:

```text
target/evidence/controller-refactoring-002-<yyyyMMdd-HHmmss>/
```

Use only this explicit path throughout the stage. Do not use newest-directory discovery.

The validation status file must be inside that exact directory:

```text
<exact-evidence-directory>/controller-refactoring-002-validation-status.txt
```

At minimum retain sequential evidence for:

```text
001-working-directory.txt
002-branch.txt
003-head.txt
004-recent-commits.txt
005-stage-001-prerequisite.txt
006-git-status-pre.txt
007-git-diff-pre.txt
008-git-diff-cached-pre.txt
009-untracked-pre.txt
010-stash-fingerprint-pre.txt
011-governing-document-hashes.txt
012-source-hashes-pre.txt
013-classification-checkpoint.txt
014-reference-inventory.txt
015-mapping-and-contract-baseline.txt
016-security-baseline.txt
017-fail-first-command.txt
018-fail-first-output.txt
019-fail-first-exit.txt
020-writer-command.txt
021-writer-output.txt
022-writer-exit.txt
023-source-hashes-post.txt
024-focused-tests-command.txt
025-focused-tests-output.txt
026-focused-tests-exit.txt
027-reference-date-preservation-command.txt
028-reference-date-preservation-output.txt
029-reference-date-preservation-exit.txt
030-advice-preservation-command.txt
031-advice-preservation-output.txt
032-advice-preservation-exit.txt
033-security-preservation-command.txt
034-security-preservation-output.txt
035-security-preservation-exit.txt
036-cumulative-regression-command.txt
037-cumulative-regression-output.txt
038-cumulative-regression-exit.txt
039-full-tests-command.txt
040-full-tests-output.txt
041-full-tests-exit.txt
042-compile-command.txt
043-compile-output.txt
044-compile-exit.txt
045-architecture-audits.txt
046-contract-diff-audit.txt
047-production-scope-audit.txt
048-test-scope-audit.txt
049-documentation-scope-audit.txt
050-diff-check.txt
051-git-diff-final.txt
052-git-diff-cached-final.txt
053-git-status-final.txt
054-untracked-final.txt
055-stash-fingerprint-final.txt
056-diff-stat.txt
057-name-status.txt
058-command-exit-codes.txt
059-final-status.txt
controller-refactoring-002-validation-status.txt
controller-refactoring-002-final-review-bundle.txt
```

Preserve every failed output. Later passing evidence supersedes a historical failure only when the final bundle explicitly records the precedence.

## 14. Validation Commands

Discover the exact test-class package names after deterministic moves, then use explicit test classes. Do not use wildcard test selection.

### 14.1 Focused architecture and move tests

Run the Stage 002 architecture test plus package-sensitive tests directly affected by the move.

### 14.2 Reference-date preservation

At minimum run:

```text
ReferenceDateAllControllerTest
ReferenceDateAllMutationResponseContractTest
ReferenceDateRefreshControllerTest
```

### 14.3 Advice preservation

At minimum run:

```text
ApiExceptionHandlerLoggingTest
ApiExceptionHandlerMutationResponseContractTest
ApiExceptionHandlerTest
ApiResponseExceptionHandlingBoundaryTest
```

### 14.4 Security preservation

At minimum run:

```text
SecurityConfigAuthDisabledTest
SecurityConfigAuthTest
```

### 14.5 Cumulative Stage 001 bounded regression

Rerun the explicit 191-test Stage 001 bounded suite from the accepted evidence, updating test package names only where the two approved moves changed them. Do not remove tests from the suite merely because imports changed.

### 14.6 Full test suite

Because this stage moves a global `@RestControllerAdvice`, run the full Maven test suite after all focused and cumulative gates pass:

```bash
mvn test
```

### 14.7 Compile

After the final production or test edit and after full tests, run:

```bash
mvn -DskipTests compile
```

If any test source changes after a successful run, rerun every affected focused gate, the cumulative suite, and the full suite. If production changes after validation, rerun all final validation gates.

## 15. Background Validation Ownership

The complete Git-controlled `.cline` Stage 002 package must:

1. Apply the deterministic architect-designed writer.
2. Launch validation exactly once in the background after writer success.
3. Know the exact evidence directory before validation starts.
4. Write `RUNNING` to the exact status file.
5. Redirect command output directly to the prescribed evidence files.
6. Poll the explicit status path until `PASS` or `BLOCKED`.
7. Avoid newest-directory discovery.
8. Avoid duplicate launches.
9. Avoid asking Nick to run Maven manually.
10. Store no validation status under `.cline/state`.

Do not buffer complete Maven output in a shell variable.

## 16. Required Audits

### 16.1 Endpoint preservation

Prove the exact three reference-date HTTP method/path pairs remain unchanged.

### 16.2 Advice behavior preservation

Prove handled exceptions, statuses, public codes, envelopes, locale selection, correlation behavior, and sanitized logging remain unchanged.

### 16.3 Production scope

Only the two approved production moves and mechanically required direct references may appear. Any other production change is a blocker.

### 16.4 Old-package absence

Prove these old production paths no longer exist:

```text
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ApiExceptionHandler.java
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ReferenceDateController.java
```

### 16.5 New-package presence

Prove these new production paths exist:

```text
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/advice/ApiExceptionHandler.java
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/internal/referencedate/ReferenceDateController.java
```

### 16.6 Deferred-boundary preservation

Prove no change to:

```text
ReferenceDataController.java
AuthenticatedPortalContextWebExceptionHandler.java
```

### 16.7 Git and stash preservation

Classify every changed and untracked file and prove the stash fingerprint is unchanged.

## 17. Cumulative `handover.md`

Update the existing Controller Refactoring section cumulatively with:

```text
Stage 001 architect status and approved commit hash
Stage 002 identity and final implementer status
exact Stage 002 evidence directory
accepted package moves
preserved endpoint list
preserved advice behavior
LoggingSanitizer dependency decision
focused validation totals
reference-date preservation totals
advice preservation totals
security preservation totals
cumulative regression totals
full-suite totals
compile result
changed-file classification
unresolved or deferred items
stage_003_started=false
architect_status=PENDING
commit_authorization=false
```

Do not delete Stage 001 context. Do not claim Stage 002 architect approval.

## 18. Implementation Plan Status Update

Update only the Stage 002 status in:

```text
docs/refactor/controller/controller-refactoring-implementation-plan.md
```

Record the achieved implementation state and explicit evidence path. Do not rewrite Stage 003 or later stages.

## 19. Final Review Bundle

Create:

```text
<exact-evidence-directory>/controller-refactoring-002-final-review-bundle.txt
```

Include complete, untruncated evidence for:

1. stage identity and implementer status;
2. branch, HEAD, recent commits, and resolved Stage 001 prerequisite hash;
3. pre-state Git, untracked files, and stash fingerprint;
4. classification checkpoint;
5. before-and-after package paths and class hashes;
6. before-and-after endpoint mappings;
7. before-and-after constructor dependencies;
8. before-and-after advice handler inventory;
9. before-and-after security evidence;
10. genuine fail-first command, complete output, totals, and nonzero exit;
11. deterministic writer command, complete output, and exit;
12. faithful production and test diffs;
13. focused test commands, complete outputs, totals, and exits;
14. reference-date preservation command, complete output, totals, and exit;
15. advice preservation command, complete output, totals, and exit;
16. security preservation command, complete output, totals, and exit;
17. cumulative 191-test baseline command, complete output, totals, and exit;
18. full-suite command, complete output, totals, and exit;
19. compile command, complete output, and exit;
20. all audits;
21. final Git status, diff, cached diff, untracked files, diff stat, and name-status;
22. final stash fingerprint;
23. all command exit codes;
24. validation-status content;
25. cumulative `handover.md` diff;
26. implementation-plan status diff;
27. confirmation that `ReferenceDataController` and the filter exception handler were unchanged;
28. `stage_003_started=false`;
29. `architect_status=PENDING`;
30. `commit_authorization=false`.

## 20. Completion Rules

Use `READY_FOR_ARCHITECT_REVIEW` only when:

- the classification checkpoint passed;
- genuine fail-first evidence is retained;
- both exact production moves are complete;
- all mappings and contracts are preserved;
- every required validation gate passes after the final relevant edit;
- the full suite passes;
- compilation passes;
- scope and stash audits pass;
- handover and plan status are current;
- the final review bundle is complete;
- Stage 003 has not started.

Otherwise use `BLOCKED`, preserve all evidence, and do not commit.

## 21. Final Console Output

Print only:

```text
implementation_status=<READY_FOR_ARCHITECT_REVIEW|BLOCKED>
architect_status=PENDING
commit_authorization=false
stage_003_started=false
stage_001_prerequisite_commit=<hash>
evidence_directory=<exact path>
validation_status=<PASS|BLOCKED>
final_review_bundle=<exact path>
approved_future_commit_subject=refactor: separate controller advice and internal APIs
```
