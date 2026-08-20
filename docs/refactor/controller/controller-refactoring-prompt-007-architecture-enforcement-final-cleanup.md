# Prompt 007: Enforce Inbound Web Architecture and Complete Final Cleanup

## 1. Role and Authority

You are **M365 Copilot**, acting as the implementation agent for **Controller Refactoring Stage 007: Architecture Enforcement and Final Cleanup**.

Nick is the Solution Owner. M365 Copilot owns the architecture, implementation design, deterministic changes, validation design, evidence requirements, and final review. Cline may only apply M365 Copilot-generated deterministic changes and execute the prescribed validation and evidence workflow. Cline must not design, improvise, broaden scope, or select alternative architecture rules.

Stage 007 is the final implementation stage in the controller-refactoring plan. Do not start any unplanned Stage 008.

## 2. Governing Documents

Read these files completely before any change:

```text
docs/refactor/controller/controller-refactoring-implementation-plan.md
docs/refactor/controller/controller-refactoring-classification.md
docs/refactor/controller/controller-refactoring-endpoint-inventory.md
docs/refactor/controller/controller-refactoring-prompt-001-classification.md
docs/refactor/controller/controller-refactoring-prompt-002-advice-internal-boundaries.md
docs/refactor/controller/controller-refactoring-prompt-003-standalone-capabilities.md
docs/refactor/controller/controller-refactoring-prompt-004-login-boundary.md
docs/refactor/controller/controller-refactoring-prompt-005-registration-boundary.md
docs/refactor/controller/controller-refactoring-prompt-006-forgot-password-boundary.md
handover.md
```

Use the committed cumulative repository state after Stage 006. Never reconstruct files from the initial codebase or an earlier refactoring stage.

Stop with `BLOCKED` before source or test changes if a governing document is missing or `handover.md` does not record Stage 006 architect PASS.

## 3. Stage Identity and Prerequisite

```text
stage=007
stage_name=architecture-enforcement-and-final-cleanup
implementation_status=CLASSIFICATION_IN_PROGRESS
architect_status=PENDING
commit_authorization=false
controller_refactoring_complete=false
unplanned_stage_008_started=false
```

Remain on the current branch. Do not switch branches.

Resolve the Stage 006 prerequisite commit whose exact subject is:

```text
refactor: consolidate forgot password controller boundary
```

Do not use `grep` to verify the expected commit subject. Capture recent Git subjects and use Bash substring matching. Resolve the commit hash by iterating recent hashes and comparing each subject in Bash.

Verify that the Stage 006 commit contains:

- `web.auth.forgotpassword.ForgotPasswordController`;
- deletion of the two legacy Forgot Password controllers;
- the Stage 006 architecture test;
- directly affected tests;
- canonical controller-refactoring documentation;
- no Stage 007 architecture-enforcement implementation.

Stop with `BLOCKED` if the prerequisite cannot be resolved or its committed scope is inconsistent.

Commit subject allowed only after architect PASS:

```text
test: enforce inbound web architecture boundaries
```

Do not commit during this prompt.

## 4. Stage Objective

Complete the controller-refactoring initiative by adding executable architecture rules and removing only obsolete empty controller-package structure.

The final architecture must enforce these boundaries:

```text
adapter.in.web.advice
adapter.in.web.auth.captcha
adapter.in.web.auth.forgotpassword
adapter.in.web.auth.login
adapter.in.web.auth.logout
adapter.in.web.auth.registration
adapter.in.web.auth.usernamerecovery
adapter.in.web.contribution
adapter.in.web.notification
adapter.in.web.personalinformation
adapter.in.web.referencedata
adapter.in.web.internal.referencedate
adapter.in.web.filter
```

The old flat production package must contain no endpoint controller or REST advice source:

```text
com.bct.ngtpa.apiservice.adapter.in.web.controller
```

Tests may remain in historically named test packages when moving them would add noise, provided tests import and validate the final production owners.

## 5. Final Production Ownership

Before implementation, prove the exact current final owner for every endpoint and advice type. At minimum, verify:

```text
ApiExceptionHandler
-> web.advice.ApiExceptionHandler

CaptchaController
-> web.auth.captcha.CaptchaController

ForgotPasswordController
-> web.auth.forgotpassword.ForgotPasswordController

LoginController
-> web.auth.login.LoginController

LogoutController
-> web.auth.logout.LogoutController

RegistrationController
-> web.auth.registration.RegistrationController

UsernameRecoveryController
-> web.auth.usernamerecovery.UsernameRecoveryController

ContributionController
-> web.contribution.ContributionController

NotificationController
-> web.notification.NotificationController

PersonalInformationController
-> web.personalinformation.PersonalInformationController

ReferenceDataController
-> web.referencedata.ReferenceDataController

ReferenceDateController
-> web.internal.referencedate.ReferenceDateController

AuthenticatedPortalContextWebExceptionHandler
-> web.filter.AuthenticatedPortalContextWebExceptionHandler
```

The repository is authoritative. If additional current inbound endpoint or advice types exist, classify them before continuing.

## 6. Required Architecture Rules

Create one cohesive architecture test class unless the repository's existing conventions require a small set of focused classes. The rules must be executable and deterministic.

### 6.1 No production controller in the old flat package

Prove that no production class directly in:

```text
com.bct.ngtpa.apiservice.adapter.in.web.controller
```

is annotated with:

```text
@RestController
@Controller
@RestControllerAdvice
@ControllerAdvice
```

Also prove no Java source remains directly under the old production controller directory after final cleanup.

### 6.2 Controllers depend only on approved layers

Endpoint controllers may depend on:

- application input ports;
- application command and result types;
- web-owned DTOs, mappers, response policies, cookie writers, and transport helpers;
- shared web correlation and request-context types;
- Spring WebFlux and Java platform types;
- approved logging annotations or APIs.

Endpoint controllers must not directly depend on:

```text
adapter.out
redis implementation packages
APIM clients or executors
persistence implementations
infrastructure repositories
WebClient-based outbound clients
TAS transport implementations
```

Do not ban legitimate application command or result packages merely because package names contain a feature name.

### 6.3 Advice boundary

Prove:

- REST advice resides below `adapter.in.web.advice`;
- endpoint controllers do not reside in the advice package;
- filter-stack exception handling remains below `adapter.in.web.filter`;
- `AuthenticatedPortalContextWebExceptionHandler` is not treated as controller advice;
- the accepted `ApiExceptionHandler -> LoggingSanitizer` dependency remains unchanged and is explicitly exempted only for this established cross-cutting dependency.

Do not create a new logging port in Stage 007.

### 6.4 Internal API boundary

Prove:

- internal Reference Date administration resides below `web.internal.referencedate`;
- the three internal routes remain owned by `ReferenceDateController`;
- customer-facing `ReferenceDataController` remains below `web.referencedata`;
- Reference Data and Reference Date ownership cannot be conflated.

### 6.5 Authentication capability boundaries

Prove final ownership for:

```text
captcha
forgotpassword
login
logout
registration
usernamerecovery
```

Prove that no legacy controller FQCN or legacy source path owns a production boundary.

### 6.6 Single-owner journey boundaries

Prove:

- one `LoginController` owns the complete Login HTTP route set;
- one `RegistrationController` owns the complete Registration HTTP route set;
- one `ForgotPasswordController` owns the complete Forgot Password HTTP route set;
- no compatibility controller or duplicate Spring bean owns the same route;
- post-login Change Password is not part of `ForgotPasswordController`.

### 6.7 Effective route uniqueness

Build an annotation-based or Spring-mapping-aware inventory that proves no duplicate effective HTTP method/path pair among endpoint controllers.

Do not parse arbitrary string literals as routes. Resolve class-level and method-level mappings correctly. Support `value` and `path` attributes and the request-method semantics used in the repository.

### 6.8 Endpoint inventory preservation

Compare the final executable route inventory with the canonical endpoint inventory. Preserve the accepted total unless repository evidence proves that the canonical count was updated in an approved prior stage.

Record:

```text
expected_endpoint_count
actual_endpoint_count
missing_method_path_pairs
unexpected_method_path_pairs
duplicate_method_path_pairs
```

Any missing, unexpected, or duplicate pair is a blocker.

## 7. Cleanup Scope

Remove only obsolete empty production directories created by the controller moves. Git does not track empty directories, so cleanup may require no Git-controlled deletion.

Do not move or rename tests merely to eliminate old test package names.

Do not delete historical prompts, evidence, architecture tests, or source-path guards that remain valid.

Do not perform unrelated import cleanup, naming cleanup, formatting cleanup, DTO movement, mapper movement, service refactoring, dependency inversion, or package redesign.

## 8. Allowed Git-Controlled Changes

### 8.1 Tests

You may create or update only architecture tests needed to enforce the final controller boundaries and exact endpoint inventory.

Prefer one new test:

```text
src/test/java/com/bct/ngtpa/apiservice/architecture/InboundWebControllerArchitectureTest.java
```

Update an existing architecture test only when the new final rule would otherwise duplicate or conflict with it. Preserve stronger existing assertions.

### 8.2 Production

No production behavior change is planned.

Production Java changes are prohibited unless classification discovers an obsolete compatibility controller or stale endpoint/advice class left in the old flat package by an earlier stage. If such a source exists, stop with `BLOCKED` and return it for architect decision. Do not delete or move it automatically.

### 8.3 Documentation

Update only:

```text
handover.md
docs/refactor/controller/controller-refactoring-implementation-plan.md
docs/refactor/controller/controller-refactoring-classification.md
docs/refactor/controller/controller-refactoring-endpoint-inventory.md
```

Also add this prompt as:

```text
docs/refactor/controller/controller-refactoring-prompt-007-architecture-enforcement-final-cleanup.md
```

## 9. Preserved Scope

Do not modify:

- any final controller production source;
- application ports or use cases;
- DTOs or mappers;
- Redis, APIM, TAS, persistence, or infrastructure code;
- `SecurityConfig`;
- application configuration;
- Maven dependencies or plugins;
- CI/CD files;
- Stage 001 through Stage 006 prompt files.

No Stage 008 is authorized.

## 10. Mandatory Pre-Change Classification

Before editing, record:

1. Exact Stage 006 prerequisite hash and committed subject.
2. Current branch, HEAD, status, staged changes, untracked files, and stash fingerprint.
3. Every production class annotated as controller or advice.
4. Every effective HTTP method/path pair and current owner.
5. Every Java source directly under the old flat production controller directory.
6. Every direct controller dependency on forbidden outbound or infrastructure packages.
7. Existing architecture tests that overlap the proposed final rules.
8. Current final controller source hashes.
9. Current canonical endpoint count and method/path set.
10. Exact proposed test files and rule methods.
11. Exact documentation updates.
12. Confirmation that no production edit is required.

Conclude with exactly one:

```text
IMPLEMENTABLE_AS_DESIGNED
```

or:

```text
BLOCKED
```

Stop before test or documentation changes unless classification is `IMPLEMENTABLE_AS_DESIGNED`.

## 11. Stop Conditions

Stop with `BLOCKED` if:

- Stage 006 is not committed with the exact approved subject;
- a legacy production controller or advice source remains in the old flat package;
- route uniqueness cannot be proven;
- the canonical endpoint inventory and runtime annotations disagree;
- a public endpoint appears missing or unexpected;
- an existing controller has a forbidden direct outbound dependency requiring production redesign;
- architecture enforcement requires changing production behavior;
- an existing stronger architecture rule would be weakened;
- unrelated working-tree changes cannot be classified;
- branch or stash state changes;
- unplanned Stage 008 work is present.

Do not invent exceptions beyond the accepted `ApiExceptionHandler -> LoggingSanitizer` boundary.

## 12. Genuine Fail-First TDD

Before changing production or documentation, create the final architecture test with one intentionally unsatisfied rule that characterizes a currently missing architecture guarantee, not a fabricated product defect.

The preferred fail-first rule is the absence of an executable final architecture-enforcement class or final endpoint-inventory parity guard. The new test must compile and fail against the pre-Stage-007 state because the final enforcement marker or complete rule set is not yet implemented.

A valid approach is:

1. Create a test that expects a final architecture marker resource generated by the deterministic writer, alongside executable package and route rules.
2. Run the test before creating the marker.
3. Retain the expected nonzero exit and assertion failure.
4. Create the marker only after genuine fail-first evidence is retained.

The marker must be test-owned or documentation-owned and must not alter runtime behavior.

Do not intentionally break a valid controller rule. Do not recreate or backdate fail-first evidence.

Retain:

```text
exact command
complete failure diagnostics
nonzero exit
test totals
pre-change hashes
timestamp proving chronology
```

## 13. Deterministic Implementation Artifacts

M365 Copilot must generate a deterministic Python writer invoked by an executable Bash orchestrator. Do not manually edit repository files.

The writer must:

1. Verify branch and Stage 006 prerequisite hash.
2. Verify pre-state hashes for governing documents and existing overlapping architecture tests.
3. Verify classification is `IMPLEMENTABLE_AS_DESIGNED`.
4. Create the final architecture test or complete its post-fail-first rule set deterministically.
5. Create any test-owned marker required by the genuine fail-first design.
6. Update only explicitly listed existing architecture tests when necessary.
7. Update the four canonical documents and cumulative `handover.md`.
8. Reject unlisted replacement counts or unexpected markers.
9. Produce a changed-path manifest.
10. Stop safely on a pre-state mismatch.

The Bash orchestrator must invoke `python`, not `python3`.

## 14. Maven Environment and Validation Policy

Use exactly:

```bash
export JAVA_HOME="C:/Java/OpenJDK/jdk-21"
export M2_HOME="/d/Tools/apache-maven-3.9.15"
export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH"
```

Use `mvn`, not `mvnw`.

After formatting, run only these compilation gates in the implementation workflow:

```bash
mvn -DskipTests compile
mvn -DskipTests test-compile
```

Then run only explicitly selected architecture and cumulative controller tests required by this stage. Do not run `mvn test` as part of this implementation script. The full Maven suite remains a manual post-implementation gate when Nick requires it.

Stop immediately on production compile or test-compile failure. Repair all compile errors cumulatively before continuing.

## 15. Evidence Directory

Create one exact timestamped directory before validation:

```text
target/evidence/controller-refactoring-007-<yyyyMMdd-HHmmss>/
```

Retain and reuse the explicit path. Do not use newest-directory discovery or create a second evidence directory.

The validation status file must be inside that directory:

```text
<exact-evidence-directory>/controller-refactoring-007-validation-status.txt
```

Retain sequential evidence for:

```text
001-working-directory.txt
002-branch.txt
003-head.txt
004-recent-commits.txt
005-stage-006-prerequisite.txt
006-git-status-pre.txt
007-git-diff-pre.txt
008-git-diff-cached-pre.txt
009-untracked-pre.txt
010-stash-fingerprint-pre.txt
011-governing-document-hashes.txt
012-final-controller-hashes-pre.txt
013-controller-advice-inventory.txt
014-effective-endpoint-inventory-pre.txt
015-old-flat-package-audit-pre.txt
016-forbidden-dependency-audit-pre.txt
017-overlapping-architecture-tests.txt
018-classification-checkpoint.txt
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
033-final-architecture-command.txt
034-final-architecture-summary.txt
035-final-architecture-exit.txt
036-prior-stage-preservation-command.txt
037-prior-stage-preservation-summary.txt
038-prior-stage-preservation-exit.txt
039-cumulative-controller-command.txt
040-cumulative-controller-summary.txt
041-cumulative-controller-exit.txt
042-controller-advice-inventory-post.txt
043-effective-endpoint-inventory-post.txt
044-endpoint-parity-audit.txt
045-route-uniqueness-audit.txt
046-old-flat-package-audit-post.txt
047-forbidden-dependency-audit-post.txt
048-production-scope-audit.txt
049-test-scope-audit.txt
050-documentation-scope-audit.txt
051-final-controller-hashes-post.txt
052-diff-check.txt
053-git-diff-final.txt
054-git-diff-cached-final.txt
055-git-status-final.txt
056-untracked-final.txt
057-stash-fingerprint-final.txt
058-diff-stat.txt
059-name-status.txt
060-command-exit-codes.txt
061-final-status.txt
controller-refactoring-007-validation-status.txt
controller-refactoring-007-final-review-bundle.txt
```

Do not duplicate complete Maven output in summary evidence. Build reports and Surefire XML are authoritative. Retain exact commands, exits, concise summaries, manifests, and failure diagnostics where needed.

Preserve every historical failure. Corrections must append higher-numbered evidence in the same exact directory and define explicit precedence.

## 16. Required Validation

Use explicit test class names only. Do not use wildcard test selection.

### 16.1 Final architecture enforcement

Run the new final architecture test and any existing architecture tests directly updated by Stage 007.

### 16.2 Prior-stage preservation

Run the architecture protection tests from Stages 002 through 006, including the final ownership tests for:

```text
ControllerAdviceAndInternalBoundaryArchitectureTest
StandaloneCapabilityControllerArchitectureTest
LoginHttpBoundaryConsolidationArchitectureTest
Registration controller consolidation architecture test
Forgot Password controller consolidation architecture test
```

Discover and use the exact Stage 005 and Stage 006 class names from the committed repository.

### 16.3 Cumulative controller regression

Run the accepted cumulative controller suite from the committed handover or prior evidence. Update test selection only for final class ownership. Do not remove tests because controllers moved.

### 16.4 No full-suite execution in implementation script

Do not run:

```bash
mvn test
```

Record:

```text
full_suite_run=false
full_suite_reason=manual post-implementation gate outside Stage 007 implementation script
```

## 17. Automatic Background Validation

The complete Git-controlled `.cline` Stage 007 package must:

1. Apply the deterministic M365 Copilot-generated writer.
2. Launch validation exactly once in the background after writer success.
3. Use the explicit known Stage 007 evidence path.
4. Write `RUNNING` to the status file inside that directory.
5. Redirect command output directly to prescribed evidence files.
6. Poll the explicit status path until `PASS` or `BLOCKED`.
7. Avoid newest-directory discovery.
8. Avoid duplicate launches.
9. Store no validation status under `.cline/state`.
10. Require no manual validation by Nick.

Do not buffer complete Maven output in a shell variable.

## 18. Required Audits

Prove:

- all current endpoint and advice types have approved final owners;
- no production Java source remains directly in the old flat controller package;
- no legacy controller FQCN owns a source boundary;
- all accepted method/path pairs exist exactly once;
- endpoint count matches the canonical inventory;
- no unexpected endpoint exists;
- no endpoint controller directly depends on forbidden outbound or infrastructure types;
- the accepted advice and filter exception boundaries remain distinct;
- internal Reference Date and customer Reference Data boundaries remain distinct;
- Login, Registration, and Forgot Password each have one controller owner;
- no Change Password endpoint was added to Forgot Password;
- Stage 002 through Stage 006 controller hashes remain unchanged;
- no production source changed in Stage 007;
- every test and documentation change is authorized;
- every untracked file is classified;
- branch and stash fingerprint are unchanged;
- `git diff --check` passes;
- no unplanned Stage 008 has started.

## 19. Documentation Completion

Update the canonical documents to describe the achieved final architecture, not a provisional target.

### 19.1 Implementation plan

Mark Stage 007:

```text
implementation_status=READY_FOR_ARCHITECT_REVIEW
controller_refactoring_complete=false
```

The complete flag remains false until final architect PASS and commit.

### 19.2 Classification

Record every final owner and mark all moves and consolidations completed. Remove or clearly supersede provisional wording that presents implemented legacy owners as active.

### 19.3 Endpoint inventory

Ensure every endpoint points to the final controller FQCN and source path. Preserve the exact accepted method/path set and count.

### 19.4 Cumulative handover

Append Stage 007 with:

```text
Stage 006 architect PASS and prerequisite commit hash
Stage 007 identity and implementer status
exact evidence directory
final architecture test and rule summary
final endpoint count and parity result
route uniqueness result
old flat package result
forbidden dependency result
prior-stage preservation totals
cumulative controller totals
compile and test-compile results
full_suite_run=false and reason
scope, branch, stash, and diff results
controller_refactoring_complete=false
unplanned_stage_008_started=false
architect_status=PENDING
commit_authorization=false
```

Do not claim architect approval.

## 20. Final Review Bundle

Create:

```text
<exact-evidence-directory>/controller-refactoring-007-final-review-bundle.txt
```

Include complete evidence for:

1. stage identity and final implementer status;
2. branch, HEAD, recent commits, and Stage 006 prerequisite;
3. pre-state Git and stash evidence;
4. final controller/advice inventory;
5. pre-change endpoint inventory and canonical comparison;
6. classification and final rule design;
7. genuine fail-first command, diagnostics, chronology, totals, and exit;
8. writer and formatting evidence;
9. production compile and test-compile evidence;
10. final architecture test command, Surefire summary/manifest, and exit;
11. prior-stage preservation evidence;
12. cumulative controller regression evidence;
13. final endpoint parity and route uniqueness audits;
14. old flat package and forbidden dependency audits;
15. faithful test and documentation diffs;
16. production immutability evidence;
17. final Git status, untracked files, diff stat, and name-status;
18. branch, stash, scope, and diff-check results;
19. command exit summary and validation status;
20. explicit full-suite non-execution record;
21. cumulative documentation updates;
22. `controller_refactoring_complete=false` pending architect PASS;
23. `unplanned_stage_008_started=false`;
24. `architect_status=PENDING`;
25. `commit_authorization=false`.

## 21. Completion Rules

Use `READY_FOR_ARCHITECT_REVIEW` only when:

- classification is `IMPLEMENTABLE_AS_DESIGNED`;
- genuine fail-first evidence is retained;
- production and test compilation pass;
- final architecture enforcement passes;
- prior-stage preservation passes;
- cumulative controller regression passes;
- endpoint parity and route uniqueness pass;
- old flat package and forbidden dependency audits pass;
- no production source changed;
- documentation is complete and cumulative;
- all scope, branch, stash, and diff audits pass;
- no unplanned Stage 008 started.

Otherwise return `BLOCKED`, preserve evidence, and do not commit.

## 22. Final Console Output

Print only:

```text
implementation_status=<READY_FOR_ARCHITECT_REVIEW|BLOCKED>
architect_status=PENDING
commit_authorization=false
controller_refactoring_complete=false
unplanned_stage_008_started=false
stage_006_prerequisite_commit=<hash>
evidence_directory=<exact path>
validation_status=<PASS|BLOCKED>
full_suite_run=false
final_review_bundle=<exact path>
approved_future_commit_subject=test: enforce inbound web architecture boundaries
```
