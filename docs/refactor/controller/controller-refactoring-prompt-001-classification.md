# Prompt 001: Controller Repository Classification and Baseline

## 1. Role and Authority

You are the repository implementer for **Controller Refactoring Stage 001: Repository Classification and Baseline**.

Microsoft 365 Copilot is the architect and implementation designer. Nick is the solution owner. You are not authorized to redesign the solution, choose a different target architecture, edit production code, merge controllers, move packages, rename classes, or begin Stage 002.

Your job is to gather exact repository evidence, preserve a trustworthy baseline, update governance documentation, and produce a complete final review bundle for architect review.

## 2. Governing Document

Read this document before doing anything else:

```text
docs/refactor/controller/docs/refactor/controller/controller-refactoring-implementation-plan.md
```

Also read the complete cumulative:

```text
handover.md
```

If either file is missing, stop with `BLOCKED`. Do not reconstruct either document from memory.

## 3. Stage Identity

```text
stage=001
stage_name=controller-classification-and-baseline
implementation_status=CLASSIFICATION_IN_PROGRESS
architect_status=PENDING
commit_authorization=false
stage_002_started=false
```

Expected branch: remain on the current branch. Do not switch branches.

Expected prerequisite: no controller-refactoring implementation commit is required. Record the current HEAD and recent commit subjects as the baseline.

Commit subject allowed only after architect PASS:

```text
docs: classify controller refactoring boundaries
```

Do not commit during this prompt.

## 4. Absolute Scope Boundary

### 4.1 Allowed Git-controlled changes

You may change or create only documentation and non-production classification artifacts required for this stage:

```text
handover.md
docs/refactor/controller/docs/refactor/controller/controller-refactoring-classification.md
docs/refactor/controller/docs/refactor/controller/controller-refactoring-endpoint-inventory.md
```

If the repository has an established architecture-documentation directory, place the two new classification documents there and record the exact paths. Do not create a new documentation convention when an established one exists.

### 4.2 Prohibited changes

Do not modify, create, rename, move, or delete:

- any file under `src/main/**`;
- any file under `src/test/**`;
- Maven POM files;
- Gradle files;
- application configuration;
- DTOs;
- controllers;
- exception advice;
- application services or ports;
- outbound adapters;
- `.cline` scripts or prompt files, unless the supplied repository already requires an architect-provided evidence writer for this exact stage;
- CI/CD configuration.

Do not run an autoformatter over repository source code.

### 4.3 Evidence output

Evidence under `target/evidence/**` is generated and must remain untracked unless the repository explicitly tracks evidence. Do not add evidence to Git.

## 5. Pre-State and Safety Gate

Before inspecting implementation details:

1. Record the current working directory.
2. Record the current branch.
3. Record `HEAD`.
4. Record recent commit hashes and subjects.
5. Record tracked modifications.
6. Record staged modifications.
7. Record all untracked files.
8. Record a stash fingerprint without applying, dropping, creating, or modifying a stash.
9. Record the hashes of the implementation plan and `handover.md`.
10. Classify every pre-existing modified or untracked file.

Do not use `grep` to verify an expected commit subject. Capture recent Git subjects and use Bash substring matching. If a commit hash must be resolved from a subject, iterate through recent hashes and compare each subject in Bash.

Stop with `BLOCKED` if:

- unrelated changes cannot be confidently classified;
- any pre-existing change overlaps the allowed documentation files and its ownership is unclear;
- stash state changes during execution;
- the branch changes;
- a required governing file is missing.

Do not clean, reset, stash, checkout, or overwrite unrelated work.

## 6. Exact Evidence Directory

Create the timestamp once at the beginning and retain the exact resulting path in a variable and in the stage documents:

```text
target/evidence/controller-refactoring-001-<yyyyMMdd-HHmmss>/
```

After creation, use only this explicit path. Do not discover or infer the newest evidence directory. Do not create a second evidence directory for retries or corrections.

The validation status file must be:

```text
<exact-evidence-directory>/controller-refactoring-001-validation-status.txt
```

Write sequentially numbered evidence files. At minimum include:

```text
001-working-directory.txt
002-branch.txt
003-head.txt
004-recent-commits.txt
005-git-status-pre.txt
006-git-diff-pre.txt
007-git-diff-cached-pre.txt
008-untracked-pre.txt
009-stash-fingerprint-pre.txt
010-governing-document-hashes.txt
011-source-roots-and-modules.txt
012-controller-files.txt
013-controller-annotations-and-mappings.txt
014-controller-dependencies.txt
015-controller-tests.txt
016-effective-endpoint-inventory.txt
017-reference-date-duplicate-analysis.txt
018-package-and-component-scan-analysis.txt
019-security-and-openapi-analysis.txt
020-layer-dependency-analysis.txt
021-proposed-migration-map.txt
022-baseline-compile-command.txt
023-baseline-compile-output.txt
024-baseline-compile-exit.txt
025-bounded-tests-command.txt
026-bounded-tests-output.txt
027-bounded-tests-exit.txt
028-audits.txt
029-git-diff-final.txt
030-git-diff-cached-final.txt
031-git-status-final.txt
032-stash-fingerprint-final.txt
033-diff-stat.txt
034-name-status.txt
035-command-exit-codes.txt
036-final-status.txt
controller-refactoring-001-validation-status.txt
controller-refactoring-001-final-review-bundle.txt
```

If a command fails, preserve the complete failure output and exit code. Do not erase or overwrite historical failure evidence. Use an additional sequentially numbered file if a corrective run is architecturally permitted.

## 7. Mandatory Repository Classification

Inspect all modules and source roots. Do not assume the screenshot contains the complete inventory.

Find every Java type that is any of the following:

- annotated with `@RestController`;
- annotated with `@Controller` and exposes request mappings;
- annotated with `@ControllerAdvice` or `@RestControllerAdvice`;
- named as a controller but not annotated, so its actual role requires classification.

For every discovered class, record:

```text
fully qualified class name
module
source root
source file
class annotations
class-level request mapping
each endpoint method
effective HTTP method and path
request headers
path variables
query parameters
request body type
response type
HTTP status behavior
validation annotations
security annotations
OpenAPI annotations
constructor dependencies
dependency layer of each constructor dependency
directly invoked application method or port
direct outbound-adapter or infrastructure dependency
exception behavior
focused tests
contract tests
package-sensitive or reflection-sensitive tests
recommended target package
recommended target class name
decision: KEEP, MOVE, RENAME, CONSOLIDATE, or BLOCKED
proposed consolidation group
evidence-based rationale
```

Do not infer an endpoint's effective path from a method name. Derive the effective path from class-level and method-level mappings, including inherited interfaces if present.

## 8. Required Controller Groups

At minimum, locate and classify repository equivalents of:

```text
ApiExceptionHandler
AuthCaptchaController
AuthForgotPasswordController
AuthForgotPasswordOtpController
AuthLoginCancelController
AuthLoginController
AuthLoginOtpController
AuthLoginOtpVerifyController
AuthLogoutController
AuthRegistrationController
AuthRegistrationOtpController
AuthRegistrationUsernameController
AuthUsernameRecoveryController
ContributionController
NotificationController
PersonalInformationController
ReferenceDateController
```

If a named class does not exist, record `NOT FOUND` and the search evidence. Do not create it.

If additional controllers exist, include them. The repository is authoritative.

## 9. Duplicate Reference-Date Checkpoint

The screenshot visually showed two `ReferenceDateController.java` entries. Determine exactly whether this represents:

- two modules;
- two source roots;
- two different packages;
- case or Unicode differences;
- a symbolic link or duplicate filesystem entry;
- generated and handwritten sources;
- a stale IDE artifact not present in the repository;
- two similarly named but distinct files;
- another proven explanation.

Record for every match:

```text
absolute or repository-relative path
module
source root
package declaration
class name
file hash
Git tracking state
annotations
route mappings
purpose
```

If two active classes expose conflicting mappings or cannot be safely distinguished, set the stage to `BLOCKED`.

## 10. Call-Chain and Layer Checkpoint

For every controller endpoint, trace the immediate production call chain far enough to prove:

```text
controller
-> mapper, if any
-> application input port or service
-> immediate implementation boundary
```

The purpose is classification, not redesign. Do not inspect unrelated deep implementation unless needed to determine whether the controller directly owns business behavior or depends on outbound infrastructure.

Explicitly flag:

- direct Redis dependency;
- direct APIM client or outbound adapter dependency;
- direct repository dependency;
- business decisions inside controller methods;
- large exception translation blocks;
- duplicated mapping logic;
- duplicated header or locale handling;
- application services typed as infrastructure implementations rather than ports.

Do not fix any finding in Stage 001.

## 11. Effective Endpoint Inventory

Create `docs/refactor/controller/docs/refactor/controller/controller-refactoring-endpoint-inventory.md` containing a deterministic inventory grouped by current controller and capability.

For each endpoint include:

```text
HTTP method
effective path
controller and method
security boundary
request type
response type
success status
known error mapping source
focused or contract test coverage
```

Also include:

- duplicate route analysis;
- ambiguous mapping analysis;
- count of controllers;
- count of advice classes;
- count of effective endpoints;
- count of endpoints with focused tests;
- count of endpoints without identifiable focused tests.

Do not claim runtime mappings that cannot be proven statically. Mark uncertainty explicitly.

## 12. Migration Classification Document

Create `docs/refactor/controller/docs/refactor/controller/controller-refactoring-classification.md` with these sections:

1. Executive result: `READY_FOR_ARCHITECT_REVIEW` or `BLOCKED`.
2. Repository and module structure.
3. Current controller package tree.
4. Controller-by-controller classification.
5. Endpoint ownership and route collision result.
6. Security-boundary result.
7. Advice-boundary result.
8. Internal API result.
9. Login consolidation result.
10. Registration consolidation result.
11. Forgot Password consolidation result.
12. Standalone capability result.
13. Reference-date duplicate result.
14. Direct infrastructure dependency findings.
15. Package-sensitive test and component-scan risks.
16. Baseline compile and bounded-test result.
17. Proposed Stage 002 scope.
18. Blockers and architect decisions required.

Use the implementation plan's provisional target architecture as the proposal being tested. Do not invent a different architecture. Where evidence contradicts the plan, mark the decision `BLOCKED` or `ARCHITECT DECISION REQUIRED` and provide exact evidence.

## 13. Baseline Validation

### 13.1 Maven environment

Use the following environment and do not use `mvnw`:

```bash
export JAVA_HOME="C:/Java/OpenJDK/jdk-21"
export M2_HOME="/d/Tools/apache-maven-3.9.15"
export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH"
```

### 13.2 Command discovery

Determine whether the repository is single-module or multi-module from POM evidence. Select the narrowest correct module command that compiles the affected controller source set and its required dependencies.

Record the exact command before running it.

### 13.3 Compile baseline

Run the appropriate Maven compile gate. Prefer a repository-standard command if `handover.md`, project documentation, or existing evidence proves one. Otherwise use the narrowest correct Maven command, such as:

```bash
mvn -pl <affected-module> -am -DskipTests compile
```

Do not substitute Gradle or an IDE build.

### 13.4 Bounded test baseline

Discover existing controller, web-layer, contract, architecture, and exception-handler test classes. Select an explicit bounded list that provides the broadest meaningful controller baseline without running unrelated suites.

Use explicit test class names:

```bash
mvn -pl <affected-module> -am -Dtest=<ExplicitTestClass1>,<ExplicitTestClass2> test
```

Do not use a wildcard as a substitute for classification. If upstream modules have no selected tests and Maven requires it, use only the minimum repository-compatible option and record why.

### 13.5 No artificial fail-first

Stage 001 is a classification and baseline stage. Do not edit tests to manufacture a failure. A pre-existing compile or test failure must be retained and analyzed as baseline evidence.

## 14. Validation Execution Rules

After all classification documents and the cumulative `handover.md` update are complete:

1. Launch the prescribed validation workflow exactly once in the background.
2. Pass the exact evidence directory to the workflow.
3. Write `RUNNING` to the exact validation-status file before launch.
4. Do not start a second validation process.
5. Poll the exact validation-status path until it reports `PASS` or `BLOCKED`.
6. Do not discover a newest evidence directory.
7. Do not ask Nick to run Maven manually.
8. Preserve all output and exit codes inside the exact evidence directory.

If the repository contains an approved checked-in evidence runner that can execute this stage without being modified, use it. Otherwise execute the commands yourself in one background shell process with outputs redirected directly to the prescribed evidence files. Do not buffer complete Maven output in a shell variable.

The status file must end with one of:

```text
PASS
BLOCKED
```

`PASS` means the baseline commands completed successfully and documentation/evidence is complete. It does not mean architect approval.

## 15. Audits

Perform and retain these audits after validation:

### 15.1 Production immutability

Prove no file under `src/main/**` changed.

### 15.2 Test immutability

Prove no file under `src/test/**` changed.

### 15.3 Allowed-file audit

Prove every Git-controlled change is one of the allowed documentation files. Classify every untracked file.

### 15.4 Governing-plan preservation

Prove the implementation plan content was not changed by this stage.

### 15.5 Stash preservation

Compare final stash fingerprint with the pre-state fingerprint.

### 15.6 Next-stage gate

Record exactly:

```text
stage_002_started=false
```

## 16. Cumulative `handover.md` Update

Update `handover.md` without deleting prior confirmed context. Add a controller-refactoring Stage 001 section containing:

```text
stage identity
current status
exact evidence directory
repository modules and source roots
controller and endpoint counts
accepted static facts
provisional migration decisions
reference-date duplicate result
baseline compile result
bounded-test result
blockers or architect decisions required
Git-controlled files changed
stage_002_started=false
architect_status=PENDING
commit_authorization=false
```

Do not claim architect PASS.

## 17. Final Review Bundle

Create:

```text
<exact-evidence-directory>/controller-refactoring-001-final-review-bundle.txt
```

Include complete, untruncated evidence sufficient for independent architect review:

1. stage identity and implementer status;
2. exact branch and HEAD;
3. recent commit hashes and subjects;
4. pre-state Git and stash evidence;
5. module and source-root inventory;
6. complete controller inventory;
7. complete endpoint inventory;
8. controller annotations and mappings;
9. dependency and call-chain classification;
10. security and OpenAPI classification;
11. test inventory;
12. reference-date duplicate analysis;
13. target migration map and rationale;
14. exact compile command, complete output, and exit;
15. exact bounded-test command, complete output, totals, and exit;
16. all audits;
17. faithful content or diff for every changed Git-controlled file;
18. final Git status, diff, cached diff, diff stat, and name-status;
19. final stash fingerprint;
20. all command exit codes;
21. exact validation status;
22. blockers and architect decisions required;
23. confirmation that production and tests were not edited;
24. `stage_002_started=false`;
25. `architect_status=PENDING`;
26. `commit_authorization=false`.

Do not create a file or statement claiming architect approval.

## 18. Completion Status

Use `READY_FOR_ARCHITECT_REVIEW` only when:

- classification is complete;
- the duplicate reference-date question is resolved or precisely raised as an architect decision with sufficient evidence;
- baseline validation is retained;
- production and test sources are unchanged;
- all Git-controlled changes are allowed and classified;
- the final review bundle is complete;
- the next stage was not started.

Use `BLOCKED` when any stop condition is met. Preserve all evidence and explain the exact blocker. A blocked outcome does not authorize production changes, a commit, or Stage 002.

## 19. Final Console Output

When finished, print only a concise summary containing:

```text
implementation_status=<READY_FOR_ARCHITECT_REVIEW|BLOCKED>
architect_status=PENDING
commit_authorization=false
stage_002_started=false
evidence_directory=<exact path>
classification_document=<exact path>
endpoint_inventory=<exact path>
final_review_bundle=<exact path>
validation_status=<PASS|BLOCKED>
```
