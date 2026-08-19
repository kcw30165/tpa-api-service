# Controller Refactoring Implementation Plan

## 1. Document Control

- **Initiative:** Inbound REST controller architecture refactoring
- **Architect and implementation designer:** Microsoft 365 Copilot
- **Solution owner and approval authority:** Nick
- **Implementation model:** Architect-designed, implementer-applied, evidence-gated stages
- **Status:** Planned
- **Primary objective:** Replace the flat controller package with capability-oriented inbound web boundaries without changing externally observable API behavior

## 2. Problem Statement

The current controller package is flat and mixes several different concerns:

- authentication journeys such as login, registration, forgot password, and username recovery;
- workflow-step controllers such as OTP send and OTP verification;
- authenticated and pre-authentication operations;
- customer-facing business APIs;
- internal administration APIs;
- cross-cutting exception handling.

The visible controller inventory includes:

```text
ApiExceptionHandler.java
AuthCaptchaController.java
AuthForgotPasswordController.java
AuthForgotPasswordOtpController.java
AuthLoginCancelController.java
AuthLoginController.java
AuthLoginOtpController.java
AuthLoginOtpVerifyController.java
AuthLogoutController.java
AuthRegistrationController.java
AuthRegistrationOtpController.java
AuthRegistrationUsernameController.java
AuthUsernameRecoveryController.java
ContributionController.java
NotificationController.java
PersonalInformationController.java
ReferenceDateController.java
ReferenceDateController.java
```

The screenshot is sufficient to identify an architectural smell, but not sufficient to authorize production changes. Exact packages, mappings, dependencies, tests, source roots, and the apparent duplicate `ReferenceDateController.java` entry must be proven from repository evidence before implementation.

## 3. Goals

1. Organize inbound HTTP code by business capability and security boundary.
2. Keep controllers thin and limited to HTTP adaptation.
3. Consolidate controllers that represent steps in one cohesive journey when repository evidence supports consolidation.
4. Separate cross-cutting advice from endpoint controllers.
5. Separate internal administration APIs from customer-facing APIs.
6. Preserve every public HTTP contract unless a later, separately approved change explicitly modifies it.
7. Add automated architecture controls that prevent the flat structure and forbidden dependencies from returning.
8. Deliver the refactor in small, testable, independently reviewable stages.

## 4. Non-Goals

This initiative does not authorize:

- URL redesign;
- request or response schema redesign;
- changes to HTTP methods or status codes;
- new authentication or authorization behavior;
- OTP, captcha, password-policy, session, Redis, or APIM behavior changes;
- changes to downstream contracts;
- broad DTO package relocation;
- application-service redesign unrelated to controller boundary correction;
- replacement of existing public statuses or error contracts;
- opportunistic cleanup outside the active stage.

## 5. Architectural Principles

### 5.1 Package by capability

Inbound web classes shall be grouped by the capability or journey they expose, not by individual technical actions such as `sendOtp` or `verifyOtp`.

### 5.2 One HTTP boundary per cohesive journey

Separate endpoint methods may remain backed by separate application input ports. Consolidating controller classes does not consolidate business rules or application use cases.

### 5.3 Preserve meaningful security boundaries

- Login and other pre-authentication journeys remain separate from logout.
- Forgot Password remains separate from post-login Change Password.
- Username Recovery remains separate from Forgot Password.
- Internal reference-date administration remains separate from customer-facing APIs.
- Captcha remains a shared pre-authentication capability unless repository evidence proves feature-specific ownership.

### 5.4 Thin controllers

Controllers may only:

1. acquire HTTP input;
2. trigger declarative validation;
3. map HTTP data to an application command;
4. call application input ports;
5. map application results to approved API responses;
6. select the approved HTTP status.

Controllers must not own:

- OTP policy or generation;
- captcha validation or consumption policy;
- registration eligibility;
- password policy;
- session creation or persistence;
- Redis access;
- APIM transport execution;
- downstream response interpretation;
- account-selection policy;
- customer-facing error normalization beyond the approved web mapping boundary.

### 5.5 Dependency direction

The inbound web layer may depend on application input ports and web-owned mapping components. It must not depend directly on outbound adapters, Redis repositories, APIM clients, persistence implementations, or infrastructure configuration.

## 6. Provisional Target Structure

The exact Java base package will be retained from the repository. The target structure below is relative to the existing inbound web adapter package.

```text
adapter/in/web
├── advice
│   └── ApiExceptionHandler.java
├── auth
│   ├── captcha
│   │   └── CaptchaController.java
│   ├── forgotpassword
│   │   └── ForgotPasswordController.java
│   ├── login
│   │   └── LoginController.java
│   ├── logout
│   │   └── LogoutController.java
│   ├── registration
│   │   └── RegistrationController.java
│   └── usernamerecovery
│       └── UsernameRecoveryController.java
├── contribution
│   └── ContributionController.java
├── notification
│   └── NotificationController.java
├── personalinformation
│   └── PersonalInformationController.java
└── internal
    └── referencedate
        └── ReferenceDateController.java
```

This structure is provisional until Stage 001 proves the current route roots, security boundaries, dependencies, and duplicate reference-date classification.

## 7. Public Contract Preservation Invariants

Every stage after classification must preserve, unless the stage prompt explicitly says otherwise:

- class-level and method-level route paths;
- HTTP methods;
- required and optional headers;
- request parameters and path variables;
- request and response DTO JSON shape;
- validation annotations and validation outcomes;
- HTTP status codes;
- security annotations and authorization behavior;
- OpenAPI operation and schema behavior;
- exception-to-response mappings;
- localization and `Accept-Language` behavior;
- request correlation behavior, including `X-Request-Id` where applicable;
- contract-test expectations;
- application-port invocation semantics.

Controller renaming and movement must not alter Spring component discovery.

## 8. Provisional Controller Decisions

The following decisions are hypotheses to be accepted or corrected by Stage 001 evidence.

### 8.1 Cross-cutting advice

```text
ApiExceptionHandler
```

Target:

```text
web/advice/ApiExceptionHandler
```

### 8.2 Login journey

Candidates:

```text
AuthLoginController
AuthLoginOtpController
AuthLoginOtpVerifyController
AuthLoginCancelController
```

Provisional target:

```text
web/auth/login/LoginController
```

`LoginCancellationController` is allowed only if evidence proves an independently governed route or materially distinct boundary.

### 8.3 Registration journey

Candidates:

```text
AuthRegistrationController
AuthRegistrationOtpController
AuthRegistrationUsernameController
```

Provisional target:

```text
web/auth/registration/RegistrationController
```

### 8.4 Forgot Password journey

Candidates:

```text
AuthForgotPasswordController
AuthForgotPasswordOtpController
```

Provisional target:

```text
web/auth/forgotpassword/ForgotPasswordController
```

### 8.5 Standalone authentication capabilities

```text
AuthCaptchaController          -> web/auth/captcha/CaptchaController
AuthLogoutController           -> web/auth/logout/LogoutController
AuthUsernameRecoveryController -> web/auth/usernamerecovery/UsernameRecoveryController
```

### 8.6 Business-facing capabilities

```text
ContributionController        -> web/contribution/ContributionController
NotificationController        -> web/notification/NotificationController
PersonalInformationController -> web/personalinformation/PersonalInformationController
```

### 8.7 Internal reference-date administration

```text
ReferenceDateController -> web/internal/referencedate/ReferenceDateController
```

Stage 001 must explain why two entries with the same visible filename appear and identify their fully qualified names, modules, source roots, and purposes.

## 9. Stage Plan

### Stage 001: Repository Classification and Baseline

**Purpose:** Establish the exact current architecture and an evidence-backed migration map before production edits.

**Production changes:** Prohibited.

**Required outputs:**

- complete controller inventory;
- exact mappings and route collision analysis;
- constructor dependencies and dependency-layer classification;
- security and OpenAPI annotation inventory;
- tests and coverage inventory;
- duplicate reference-date determination;
- current package tree;
- proposed target package and keep, rename, move, or consolidate decision for each class;
- baseline compile and bounded test evidence;
- architecture blockers and required stop decisions;
- cumulative `handover.md` update;
- final review bundle.

**Commit subject after architect PASS:**

```text
docs: classify controller refactoring boundaries
```

### Stage 002: Advice and Internal API Boundaries
**Implementation status:** READY_FOR_ARCHITECT_REVIEW
**Evidence:** `target\evidence\controller-refactoring-002-20260819-163644`
**Accepted package facts:** `ApiExceptionHandler` under `web.advice`; `ReferenceDateController` under `web.internal.referencedate`.


**Purpose:** Move exception advice and verified internal reference-date administration into explicit packages.

**Expected production scope:**

- `ApiExceptionHandler` package move;
- verified internal reference-date controller package move;
- import and package-sensitive test updates;
- architecture tests for advice and internal boundaries.

**Prohibited:** Endpoint contract changes and authentication-controller changes.

**Commit subject after architect PASS:**

```text
refactor: separate controller advice and internal APIs
```

**Validation qualification:** The two manually repaired source-sensitive tests passed; the full suite was not rerun by explicit solution-owner direction.

### Stage 003: Standalone Capability Packages
**Implementation status:** READY_FOR_ARCHITECT_REVIEW
**Evidence:** `target\evidence\controller-refactoring-003-20260819-173419`
**Accepted package facts:** Seven standalone controllers moved into approved capability packages; three approved class renames applied.


**Purpose:** Move standalone authentication and business controllers into capability packages and remove redundant class prefixes where safe.

**Expected candidates:** Captcha, logout, username recovery, contribution, notification, and personal information.

**Prohibited:** Login, registration, or Forgot Password consolidation.

**Commit subject after architect PASS:**

```text
refactor: organize standalone controllers by capability
```

**Validation evidence:** Existing fresh Maven Surefire XML reports, all with zero failures and zero errors; Maven was not rerun during final report generation.

### Stage 004: Login HTTP Boundary
**Implementation status:** READY_FOR_ARCHITECT_REVIEW
**Evidence:** `target/evidence/controller-refactoring-004-20260819-220150`

**Purpose:** Consolidate the confirmed login lifecycle into a cohesive inbound HTTP boundary while preserving separate application use cases.

**Expected candidates:** Login start, OTP send, OTP verification, and login cancellation.

**Prohibited:** Changes to OTP policy, session behavior, APIM behavior, registration, Forgot Password, and logout.

**Commit subject after architect PASS:**

```text
refactor: consolidate login controller boundary
```

### Stage 005: Registration HTTP Boundary

**Purpose:** Consolidate the confirmed registration lifecycle into a cohesive inbound HTTP boundary.

**Expected candidates:** Registration start, OTP lifecycle, and username completion.

**Preservation requirement:** Existing additional eligible trust registration behavior must remain unchanged.

**Commit subject after architect PASS:**

```text
refactor: consolidate registration controller boundary
```

### Stage 006: Forgot Password HTTP Boundary

**Purpose:** Consolidate Forgot Password and its OTP HTTP operations without changing the feature's business or security behavior.

**Prohibited:** Post-login Change Password changes and Username Recovery changes.

**Commit subject after architect PASS:**

```text
refactor: consolidate forgot password controller boundary
```

### Stage 007: Architecture Enforcement and Final Cleanup

**Purpose:** Make the target boundaries executable as architecture rules and remove obsolete empty package structure.

**Expected scope:**

- ArchUnit or equivalent dependency and package rules;
- complete endpoint inventory comparison;
- obsolete empty package cleanup;
- final architecture documentation;
- cumulative regression and full-suite validation.

**Commit subject after architect PASS:**

```text
test: enforce inbound web architecture boundaries
```

## 10. Stage 001 Classification Schema

For every controller or advice class, Stage 001 must record:

```text
- fully qualified class name
- module
- source root
- source file
- class annotations
- class-level mapping
- each endpoint method and effective HTTP path
- request headers
- path variables and request parameters
- request body type
- response type
- declared HTTP status
- validation annotations
- security annotations
- OpenAPI annotations
- constructor dependencies
- dependency layer of each constructor dependency
- directly called application method or port
- direct outbound-adapter or infrastructure dependency, if any
- exception behavior
- focused tests
- contract tests
- package-sensitive tests
- target package
- target class name
- decision: KEEP, MOVE, RENAME, CONSOLIDATE, or BLOCKED
- consolidation group, if any
- evidence and rationale
```

## 11. Stop Conditions

An implementation stage must stop as `BLOCKED` if any of the following is discovered:

- effective route uniqueness cannot be proven;
- consolidation would change endpoint mapping or Spring resolution;
- a controller directly owns business behavior that must first be extracted;
- another feature must change to complete the active stage;
- security behavior would change;
- public DTO or error-contract changes are required;
- the apparent `ReferenceDateController` duplication cannot be classified;
- package-sensitive reflection or component scanning would break;
- unrelated tracked or untracked repository changes cannot be classified;
- stash state changes during the stage;
- baseline compilation or tests fail for reasons that prevent trustworthy validation;
- the actual architecture contradicts a provisional target decision and the prompt does not authorize the required correction.

The implementer must not invent a workaround or broaden scope. The blocker returns to the architect.

## 12. Test Strategy

### 12.1 Baseline and inventory protection

Before moving production classes, establish an endpoint inventory test or equivalent deterministic artifact that captures every effective route, method, request type, response type, and controller owner.

### 12.2 Focused web tests

Each moved or consolidated controller requires focused tests covering:

- mappings;
- validation;
- headers;
- response status;
- public response body;
- application-port interaction;
- common exception mappings;
- security behavior where test infrastructure supports it.

### 12.3 Contract preservation

Existing integration and contract tests are preservation gates. Tests must not be weakened merely to accommodate new package or class names.

### 12.4 Architecture tests

Add rules progressively to prove:

- inbound web controllers do not depend on outbound adapters;
- advice resides below `web.advice`;
- internal controllers reside below `web.internal`;
- authentication controllers reside below `web.auth`;
- no controller classes remain in the old flat package after final migration;
- public endpoint mappings remain unique.

### 12.5 Validation tiers

Each stage prompt will name exact test classes after repository classification. The intended tiers are:

1. fail-first architecture or behavior test;
2. focused stage tests;
3. previous-stage preservation tests;
4. related feature regression tests;
5. architecture tests;
6. compile;
7. full test suite when required by risk, invalidated evidence, or a failed bounded gate.

Maven commands must use:

```bash
export JAVA_HOME="C:/Java/OpenJDK/jdk-21"
export M2_HOME="/d/Tools/apache-maven-3.9.15"
export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH"
mvn -Dtest=... test
```

Do not use `mvnw`.

## 13. Evidence and Governance

Each stage creates one exact timestamped directory:

```text
target/evidence/controller-refactoring-<stage>-<timestamp>/
```

The path must be created once, retained explicitly, and reused. Newest-directory discovery is prohibited.

At minimum, retain sequentially numbered evidence for:

1. pre-state and branch;
2. tracked, staged, and untracked files;
3. stash fingerprint;
4. prerequisite commit evidence;
5. classification or pre-change inventory;
6. fail-first evidence when applicable;
7. focused tests;
8. preservation tests;
9. cumulative regression;
10. full tests when required;
11. compile;
12. architecture and contract audits;
13. diff check;
14. command exit codes;
15. final status;
16. diff stat;
17. name-status;
18. preserved failure logs;
19. final review bundle.

Validation must be launched exactly once by the implementer workflow, using the known evidence path. The status file must be stored inside that same directory. The workflow must poll the explicit status path and must not require Nick to run validation manually.

## 14. Handover Requirements

`handover.md` is cumulative. Every stage must append or update the current controller-refactoring section with:

- accepted classifications;
- approved boundary corrections;
- implemented changes;
- preservation decisions;
- explicit evidence path;
- validation result;
- unresolved and deferred items;
- exact next-stage gate;
- confirmation that the next stage has not started.

Earlier confirmed project context must not be discarded.

## 15. Review and Approval

The implementer leaves changes uncommitted and produces one final review bundle. Copilot independently reviews:

- classification accuracy;
- alignment with this plan;
- production and test scope;
- genuine fail-first evidence where required;
- final validation after the last relevant edit;
- preservation of public contracts;
- repository and stash fingerprints;
- untracked-file classification;
- cumulative handover;
- confirmation that the next stage was not started.

Only a Copilot architect decision of `PASS` authorizes the exact stage commit subject. An implementer-authored approval statement is not commit authorization.

## 16. Definition of Done

The initiative is complete only when:

- no endpoint controller or advice class remains in the old flat package;
- every controller is assigned to a proven capability or internal boundary;
- cohesive journeys are consolidated where approved;
- application use cases remain focused and separate;
- all public mappings and contracts are preserved;
- advice and internal APIs have explicit package boundaries;
- architecture rules prevent forbidden dependencies and flat-package regression;
- all focused, preservation, architecture, compile, and required full-suite gates pass;
- `handover.md` reflects the final cumulative state;
- every stage has received architect PASS and has been committed in order.
