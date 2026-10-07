# SonarQube BUG Remediation Plan

Title: SonarQube BUG Remediation Plan

Purpose: Provide an authoritative, implementation-ready plan to remediate exactly the 10 unresolved SonarQube BUG findings reported by the `006-sonarqube-bug-source-mapping-investigation` evidence archive. The plan prescribes a strict, one-logical-unit-at-a-time workflow, validation gates, review checkpoints, and final stabilization requirements.

Baseline branch: `fix/sonarqube-bug-remediation`
Baseline HEAD: `965fdd40e4769990110ddf245a87e55eb923f26a`

Current branch HEAD before the stabilization-document commit: `2728632c82c3871a80c1e9c8c7f95c501042afea`

The branch HEAD will advance as approved implementation units are committed.

---

## Strict scope
- Scope is exactly the 10 unresolved SonarQube BUG findings mapped in evidence archive `006-sonarqube-bug-source-mapping-investigation-20261005T072553Z`.
- Excluded from scope: VULNERABILITY findings, java:S5542, APIM payload cryptography, and CODE_SMELL findings.
- No production behavior changes may be made without the per-unit validation and review described below.

---

## Status legend
- NOT_STARTED: Implementation has not begun.
- PENDING_INVESTIGATION: The implementation approach requires evidence-based investigation before code changes begin.
- INVESTIGATING: The unit is under active source and test investigation.
- BLOCKED: The unit cannot proceed safely without a decision or additional evidence.
- IMPLEMENTED: The scoped code change is complete but validation is not yet complete.
- VALIDATED: Compile, test-compile, and authorized targeted-test gates have passed.
- COMMITTED: The independently reviewed logical unit has been committed.

---

## High-level summary — Sonar BUGs (authoritative keys)
The 10 Sonar BUG issue keys addressed by this plan (from the evidence archive):
- 712adbc4-af75-4a4c-a77f-ce487911ab48 (java:S3923)
- f96fdb4f-8379-403c-9ea2-b7ebe80f67fa (java:S3923)
- c618c372-a6ed-4fe5-b418-ed50192b5c71 (java:S3655)
- 805e10b7-1b74-4441-972a-ebee2879cdc1 (java:S5998)
- bcff7d71-9a43-4913-a078-6fec5bd883f4 (java:S6218)
- 4b516506-27d8-4167-9639-f4233fa62e08 (java:S1751)
- 874ae16c-d1c7-482e-b4cc-207bce163927 (java:S1872)
- 0b63b37e-07c2-448c-b79e-842e2e495a04 (java:S1872)
- cb468a7d-9667-4c0b-8bb9-dd52f7c3ee67 (java:S1872)
- 7512930a-a155-4e3a-b292-f3c55042d969 (java:S1872)

---

## Seven sequential implementation units (authoritative order)
1. Four related Reference Date java:S1872 findings.
2. ApimAdapterReflectionBranchProbeTest java:S1751.
3. ForgotPasswordCompleteWebMapper java:S3923.
4. ApimGeneralMetadataSourceAdapter java:S3655.
5. WorkflowKeyValidator java:S5998.
6. TrpGetAccountListAdapter java:S3923.
7. ExecutionLoggingAspect java:S6218.

Each unit is implemented and validated independently in sequence. Do not modify other units while a unit is open.

---

## Summary table: seven implementation units

| Unit | Sonar key(s) | Rule | Affected class/test | Status | Commit hash | Validation evidence | Notes / Blocker |
|------|--------------:|------|---------------------|--------|-------------|---------------------|-----------------|
| 1 | 874ae16c, 0b63b37e, cb468a7d, 7512930a | java:S1872 | ReferenceDateUseCaseBoundaryTest, RefreshReferenceDateServiceTest | COMMITTED | c0b467bff4a3ff66c13b4aa14be73ed022fc035c | mvn -DskipTests compile: SUCCESS; mvn -DskipTests test-compile: SUCCESS; targeted tests: 9 run, 0 failures | Files changed: src/test/java/com/bct/ngtpa/apiservice/application/usecase/ReferenceDateUseCaseBoundaryTest.java, src/test/java/com/bct/ngtpa/apiservice/application/usecase/RefreshReferenceDateServiceTest.java. |
| 2 | 4b516506 | java:S1751 | ApimAdapterReflectionBranchProbeTest | COMMITTED | 328a6754050d9c3753b86f8cbd50ce70d6802282 | mvn -DskipTests compile: SUCCESS; mvn -DskipTests test-compile: SUCCESS; targeted tests: 5 run, 0 failures | File changed: src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimAdapterReflectionBranchProbeTest.java — replaced the single-iteration constructor loop with explicit first-constructor access. |
| 3 | 712adbc4 | java:S3923 | ForgotPasswordCompleteWebMapper | COMMITTED | 22af9b02e26091f330ae15993bcf824554ef377e | mvn -DskipTests compile: SUCCESS; mvn -DskipTests test-compile: SUCCESS; focused mapper tests: 2 run, 0 failures, 0 errors, 0 skipped; related controller/contract tests: 5 run, 0 failures, 0 errors, 0 skipped | Files changed: src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/forgotpassword/mapper/ForgotPasswordCompleteWebMapper.java, src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/forgotpassword/mapper/ForgotPasswordCompleteWebMapperTest.java. |
| 4 | c618c372 | java:S3655 | ApimGeneralMetadataSourceAdapter | COMMITTED | 59195c7e1a5174c0752e361cb48248effe5a2c2b | mvn -DskipTests compile: SUCCESS; mvn -DskipTests test-compile: SUCCESS; mvn -Dtest=ApimGeneralMetadataSourceAdapterTest test: SUCCESS (tests run: 6, failures: 0, errors: 0, skipped: 0) | Files changed: src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/metadata/ApimGeneralMetadataSourceAdapter.java. |
| 5 | 805e10b7 | java:S5998 | WorkflowKeyValidator | COMMITTED | 440379cf6c5ff95c703f3c946f1104e7e1fb2f3b | mvn -DskipTests compile: SUCCESS; mvn -DskipTests test-compile: SUCCESS; focused tests: 5 run, 0 failures, 0 errors, 0 skipped; related tests: 5 run, 0 failures, 0 errors, 0 skipped | Files changed: src/main/java/com/bct/ngtpa/apiservice/application/dto/workflow/WorkflowKeyValidator.java, src/test/java/com/bct/ngtpa/apiservice/application/dto/workflow/WorkflowKeyValidatorLongDigitsTest.java. |
| 6 | f96fdb4f | java:S3923 | TrpGetAccountListAdapter | COMMITTED | 8c0257caf673d85c77af6aafd8bcdfc4a085600a | mvn -DskipTests compile: SUCCESS; mvn -DskipTests test-compile: SUCCESS; Task0043TrpGetAccountListAdapterArchitectureTest: SUCCESS; TrpGetAccountListAdapterTest: SUCCESS | File changed: src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/login/account/TrpGetAccountListAdapter.java. |
| 7 | bcff7d71 | java:S6218 | ExecutionLoggingAspect (InvocationContext record) | COMMITTED | 2728632c82c3871a80c1e9c8c7f95c501042afea | compile: SUCCESS; test-compile: SUCCESS; focused equality test: 5 run, 0 failures, 0 errors, 0 skipped; ExecutionLoggingAspectTest: SUCCESS; LoggingSanitizerTest: SUCCESS | Files changed: src/main/java/com/bct/ngtpa/apiservice/infrastructure/logging/ExecutionLoggingAspect.java, src/test/java/com/bct/ngtpa/apiservice/infrastructure/logging/ExecutionLoggingAspectInvocationContextTest.java. |

Notes:
- Commit hash and validation evidence fields will be populated per-unit after validation and commit.

---

## Implementation units — detailed per-unit plan

For each unit below the plan records: Sonar keys, exact files in scope, root cause, intended correction (no implementation diffs in this document), behavior to preserve, files explicitly excluded from changes, compile/test commands, separately authorized targeted tests, review checkpoint, proposed commit message, and blocked/escalation conditions.

UNIT 1 — Reference Date java:S1872 (four findings)
- Status: `COMMITTED`
- Sonar keys: `874ae16c-d1c7-482e-b4cc-207bce163927`, `0b63b37e-07c2-448c-b79e-842e2e495a04`, `cb468a7d-9667-4c0b-8bb9-dd52f7c3ee67`, `7512930a-a155-4e3a-b292-f3c55042d969`
- Files changed:
  - `src/test/java/com/bct/ngtpa/apiservice/application/usecase/ReferenceDateUseCaseBoundaryTest.java`
  - `src/test/java/com/bct/ngtpa/apiservice/application/usecase/RefreshReferenceDateServiceTest.java`
- Investigation record: `docs/sonarqube/unit-01-reference-date-s1872-investigation.md`
- Root cause: The tests used class-name string comparisons. `ConfigServicePort` still exists and can be checked with direct class identity. `ReferenceDateConfigPort` was intentionally removed in commit `de55e83b`, leaving two stale assertions against an absent type.
- Implemented correction:
  - replaced the two `ConfigServicePort` name-based comparisons with direct class identity using `ConfigServicePort.class`;
  - removed the two obsolete `ReferenceDateConfigPort` assertions;
  - retained the remaining architecture checks for current approved and forbidden ports.
- Preserved behavior: Reference Date architecture boundaries and functional refresh tests remain intact. No production behavior changed.
- Explicitly not used: `Class.forName(...)`, empty exception handling, SonarQube suppression, compile-time reference to the removed port, or Java comments containing Git commit hashes.
- Validation evidence:
  - `mvn -DskipTests compile`: SUCCESS
  - `mvn -DskipTests test-compile`: SUCCESS
  - `mvn -Dtest=ReferenceDateUseCaseBoundaryTest,RefreshReferenceDateServiceTest test`: SUCCESS
  - Targeted tests: 9 run, 0 failures, 0 errors, 0 skipped
  - `git diff --check`: SUCCESS
- Review checkpoint: Unit 1 is committed.
- Proposed commit message: `test(reference-date): replace name-based type checks`
- Commit hash: c0b467bff4a3ff66c13b4aa14be73ed022fc035c

UNIT 2 — ApimAdapterReflectionBranchProbeTest java:S1751
- Status: `COMMITTED`
- Sonar key: `4b516506-27d8-4167-9639-f4233fa62e08`
- File changed:
  - `src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimAdapterReflectionBranchProbeTest.java`
- Investigation record: `docs/sonarqube/unit-02-apim-adapter-reflection-s1751-investigation.md`
- Root cause: `updateReadStatusCommand()` iterated over declared constructors but returned unconditionally during the first iteration, so the loop could execute at most once.
- Implemented correction:
  - obtains the declared-constructor array once;
  - returns `null` when the array is empty;
  - selects the first declared constructor explicitly;
  - preserves the existing accessibility, argument construction, invocation, and exception behavior.
- Preserved behavior: The same first constructor is attempted, the same synthetic arguments are supplied, invocation failures still propagate, and all reflection probes and assertions remain unchanged.
- Explicitly not used: fallback to later constructors, exception swallowing, or changes to production code and unrelated reflection helpers.
- Validation evidence:
  - `mvn -DskipTests compile`: SUCCESS
  - `mvn -DskipTests test-compile`: SUCCESS
  - `mvn -Dtest=ApimAdapterReflectionBranchProbeTest test`: SUCCESS
  - Targeted tests: 5 run, 0 failures
  - `git diff --check`: SUCCESS
- Review checkpoint: Unit 2 is committed.
- Proposed commit message: `test(apim-adapter): replace single-iteration constructor loop`
- Commit hash: 328a6754050d9c3753b86f8cbd50ce70d6802282

UNIT 3 — ForgotPasswordCompleteWebMapper java:S3923
- Status: `COMMITTED`
- Sonar key: `712adbc4-af75-4a4c-a77f-ce487911ab48`
- Files changed:
  - `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/forgotpassword/mapper/ForgotPasswordCompleteWebMapper.java`
  - `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/forgotpassword/mapper/ForgotPasswordCompleteWebMapperTest.java`
- Investigation record: `docs/sonarqube/unit-03-forgot-password-mapper-s3923-investigation.md`
- Root cause: The switch arm used a ternary whose two outcomes were both `ApiStatus.BUSINESS_REJECTED`, triggering `java:S3923`.
- Implemented correction: Consolidated `DUPLICATE_IN_PROGRESS` and `BUSINESS_REJECTED` into one switch arm returning `ApiStatus.BUSINESS_REJECTED`.
- Preserved behavior: All other status mappings, payload construction, workflow step, errors, codes, messages, targets, ordering, and public response fields remain unchanged.
- Focused coverage: Added mapper-level tests for both `DUPLICATE_IN_PROGRESS` and `BUSINESS_REJECTED`, including status, success flag, payload presence, next step, and representative error mapping.
- Validation evidence:
  - `mvn -DskipTests compile`: SUCCESS
  - `mvn -DskipTests test-compile`: SUCCESS
  - `mvn -Dtest=ForgotPasswordCompleteWebMapperTest test`: SUCCESS, 2 tests, 0 failures, 0 errors, 0 skipped
  - `mvn -Dtest=AuthForgotPasswordCompleteControllerTest,ForgotPasswordPublicResponseContractSubtask008FailFirstTest test`: SUCCESS, 5 tests, 0 failures, 0 errors, 0 skipped
  - `git diff --check`: SUCCESS
- Review checkpoint: Unit 3 is committed.
- Proposed commit message: `test(forgot-password): consolidate rejected status mapping`
- Commit hash: 22af9b02e26091f330ae15993bcf824554ef377e

UNIT 4 — ApimGeneralMetadataSourceAdapter java:S3655
- Status: `COMMITTED`
- Sonar key: `c618c372-a6ed-4fe5-b418-ed50192b5c71`
- Changed file:
  - src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/metadata/ApimGeneralMetadataSourceAdapter.java
- Investigation record:
  - docs/sonarqube/unit-04-apim-general-metadata-s3655-investigation.md
- Root cause: repeated access to the stable `result.resolutionSource()` record accessor
- Implemented correction (exact): captured the Optional once per loop iteration and reused the local Optional:

```java
Optional<GeneralMetadataResolutionSource> resultSource = result.resolutionSource();
```

- Preserved behavior: conflict detection, exception message, source assignment, entry merging, entry ordering, and final `FetchGeneralMetadataResult` construction remain unchanged.
- No fallback or default behavior introduced.
- Validation evidence (exact):
  - mvn -DskipTests compile: SUCCESS
  - mvn -DskipTests test-compile: SUCCESS
  - mvn -Dtest=ApimGeneralMetadataSourceAdapterTest test: SUCCESS
    - Tests run: 6
    - Failures: 0
    - Errors: 0
    - Skipped: 0
  - git diff --check: SUCCESS
- Review checkpoint: Unit 4 is committed.
- Proposed commit message: `fix(apim-metadata): capture resolution source once`
- Commit hash: `59195c7e1a5174c0752e361cb48248effe5a2c2b`

UNIT 5 — WorkflowKeyValidator java:S5998
- Status: `COMMITTED`
- Sonar key: `805e10b7-1b74-4441-972a-ebee2879cdc1`
- Files changed:
  - `src/main/java/com/bct/ngtpa/apiservice/application/dto/workflow/WorkflowKeyValidator.java`
  - `src/test/java/com/bct/ngtpa/apiservice/application/dto/workflow/WorkflowKeyValidatorLongDigitsTest.java`
- Investigation record: `docs/sonarqube/unit-05-workflow-key-validator-s5998-investigation.md`
- Root cause: `LONG_DIGITS` used leading and trailing wildcard quantifiers with `matches()` to implement a contains predicate, creating avoidable regex-complexity risk.
- Implemented correction: Replaced the pattern with `\d{8,}` and changed evaluation to `matcher.find()`, preserving detection of eight or more consecutive digits anywhere in a valid workflow key.
- Preserved behavior: Null, blank, trimming, `CODE_LIKE`, whitespace, unsafe-token, password-like, and safe error-message behavior remain unchanged.
- Focused coverage:
  - seven consecutive digits accepted;
  - eight and nine consecutive digits rejected;
  - separated short digit groups accepted;
  - bounded long syntactically valid non-match accepted.
- Deferred observation: A similar regex in `RegistrationIdempotencyKey` remains outside Unit 5 and was not modified.
- Validation evidence:
  - `mvn -DskipTests compile`: SUCCESS
  - `mvn -DskipTests test-compile`: SUCCESS
  - `mvn -Dtest=WorkflowKeyValidatorLongDigitsTest test`: SUCCESS, 5 tests, 0 failures, 0 errors, 0 skipped
  - `mvn -Dtest=WorkflowKeyValidatorLoginErrorPrefixTest,WorkflowKeyValidatorRegistrationErrorPrefixTest test`: SUCCESS, 5 tests, 0 failures, 0 errors, 0 skipped
  - `git diff --check`: SUCCESS
- Review checkpoint: Unit 5 is committed.
- Proposed commit message: `fix(workflow-key): simplify long-digit detection`
- Commit hash: 440379cf6c5ff95c703f3c946f1104e7e1fb2f3b

UNIT 6 — TrpGetAccountListAdapter java:S3923
- Status: `COMMITTED`
- Sonar key: `f96fdb4f-8379-403c-9ea2-b7ebe80f67fa`
- File changed:
  - `src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/login/account/TrpGetAccountListAdapter.java`
- Investigation record: `docs/sonarqube/unit-06-trp-get-account-list-s3923-investigation.md`
- Root cause: The error branch read `errorMappings` and used a ternary whose two outcomes were both `LoginAccountAuthorizationResult.unavailable()`, triggering `java:S3923`.
- Implemented correction: Replaced the complete `error != null` branch with one direct fail-closed return and removed the unused lookup.
- Preserved behavior:
  - configured and unknown downstream errors remain unavailable;
  - errors with non-empty data remain unavailable;
  - success mapping, malformed-envelope handling, mapper-failure handling, and technical-failure handling remain unchanged;
  - raw downstream messages remain unexposed.
- Configuration ownership preserved: The `errorMappings` field, constructor assignment, `properties.validate()`, `Map.copyOf(...)`, YAML configuration, and architecture contract remain unchanged. No artificial runtime read was retained.
- Validation evidence:
  - `mvn -DskipTests compile`: SUCCESS
  - `mvn -DskipTests test-compile`: SUCCESS
  - `mvn -Dtest=Task0043TrpGetAccountListAdapterArchitectureTest test`: SUCCESS
  - `mvn -Dtest=TrpGetAccountListAdapterTest test`: SUCCESS
  - `git diff --check`: SUCCESS
- Test totals: not recorded because the supplied validation summary did not include exact Surefire counts.
- Review checkpoint: Unit 6 is committed.
- Proposed commit message: `fix(login-account): simplify fail-closed error mapping`
- Commit hash: 8c0257caf673d85c77af6aafd8bcdfc4a085600a

UNIT 7 — ExecutionLoggingAspect java:S6218
- Status: `COMMITTED`
- Sonar key: `bcff7d71-9a43-4913-a078-6fec5bd883f4`
- Files changed:
  - `src/main/java/com/bct/ngtpa/apiservice/infrastructure/logging/ExecutionLoggingAspect.java`
  - `src/test/java/com/bct/ngtpa/apiservice/infrastructure/logging/ExecutionLoggingAspectInvocationContextTest.java`
- Investigation record: `docs/sonarqube/unit-07-execution-logging-aspect-s6218-investigation.md`
- Root cause: The private `InvocationContext` record contains `Object[] args`; generated record equality and hashing would compare the array by identity rather than content.
- Runtime assessment: No current production code compares, hashes, stores, or stringifies `InvocationContext`. The finding is a valid future-safe value-semantics concern rather than an observed runtime defect.
- Implemented correction:
  - added explicit `equals(Object)` covering all six record components;
  - scalar components use value or direct comparison;
  - `args` uses `Arrays.deepEquals`;
  - added consistent `hashCode()` using `Arrays.deepHashCode` for `args`.
- Focused coverage: A narrow reflection-based test verifies simple arrays, nested primitive arrays, changed scalar values, changed nested-array contents, `null`, and another object type without widening record visibility.
- Privacy and behavior preserved:
  - no `toString()` override;
  - no raw argument logging or printing;
  - no visibility change;
  - no constructor, factory, accessor, event, sanitizer, masking, or logging-output change.
- Constraint: Deliberately cyclic arrays are unsupported for equality/hash testing; no cyclic fixtures or handling were introduced.
- Validation evidence:
  - `mvn -DskipTests compile`: SUCCESS
  - `mvn -DskipTests test-compile`: SUCCESS
  - `mvn -Dtest=ExecutionLoggingAspectInvocationContextTest test`: SUCCESS, 5 tests, 0 failures, 0 errors, 0 skipped
  - `mvn -Dtest=ExecutionLoggingAspectTest test`: SUCCESS
  - `mvn -Dtest=LoggingSanitizerTest test`: SUCCESS
  - `git diff --check`: SUCCESS
- Review checkpoint: Unit 7 is committed.
- Proposed commit message: `fix(logging): define invocation context value semantics`
- Commit hash: 2728632c82c3871a80c1e9c8c7f95c501042afea

## Workflow enforcement checklist
For every implementation unit:
1. Modify only the files explicitly authorized for that unit.
2. Show the complete unified diff before validation.
3. Run `mvn -DskipTests compile`.
4. Run `mvn -DskipTests test-compile`.
5. Run only the separately authorized targeted tests after both compile gates pass.
6. Record validation evidence in this plan and set the unit status to `VALIDATED`.
7. Stop for independent review before committing.
8. Create one logical commit only after approval.
9. Do not begin the next unit automatically.

Commit hashes cannot be recorded before their commits exist. Record validation evidence before each unit commit, and populate the resulting unit commit hashes during final stabilization to avoid an extra documentation-only commit after every unit.

---

## Final stabilization gates (required)
1. Full compile: mvn -DskipTests compile
2. Full test-compile: mvn -DskipTests test-compile
3. Full test suite: mvn test
4. Inspect Surefire reports: target/surefire-reports/*.txt
5. Required Spring application startup smoke test (repository-approved startup command) — this is a required final gate
6. Rerun SonarQube analysis and confirm the 10 scoped BUG findings are resolved or explicitly documented
7. Confirm the completed vulnerability workstream has not regressed;
8. Record CODE_SMELL count for comparison only; CODE_SMELL remediation remains out of scope.

---

## Decision records and constraints (summary)
- Reference Date S1872: must not add unintended compile-time dependency; direct class identity checks (ConfigServicePort.class) were used and obsolete ReferenceDateConfigPort assertions removed; no compile-time dependency on the removed type was introduced.
- WorkflowKeyValidator: must preserve detection of eight consecutive digits anywhere in value (use pattern + matcher.find semantics).
- TrpGetAccountListAdapter: preserve fail-closed unavailable behavior and configuration validation ownership (do not change YAML or properties validation in this work).
- ExecutionLoggingAspect: do not expose raw arguments, do not change toString() or widen private record visibility solely for testing; Arrays.deepEquals / Arrays.deepHashCode semantics were approved and implemented for InvocationContext equality/hashCode.

---

## Document governance
- Authoritative branch: `fix/sonarqube-bug-remediation`.
- Baseline HEAD: `965fdd40e4769990110ddf245a87e55eb923f26a`.
- Implementation HEAD before the stabilization-document commit: `2728632c82c3871a80c1e9c8c7f95c501042afea`.
- Units 1–7 are COMMITTED and their exact commit hashes are recorded in this plan.
- Stabilization evidence belongs in `docs/sonarqube/sonarqube-bug-remediation-stabilization.md`.
- Implementation diffs and per-unit validation evidence remain recorded per-unit in their respective investigation docs and diffs.

---
