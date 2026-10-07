# Unit 03 — ForgotPasswordCompleteWebMapper (java:S3923) Investigation

Date: 2026-10-06T09:38:01.330+08:00

Scope: investigate the java:S3923 finding in
src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/forgotpassword/mapper/ForgotPasswordCompleteWebMapper.java

Summary
- Sonar issue key: 712adbc4-af75-4a4c-a77f-ce487911ab48 (java:S3923) — "Identical branches in conditional" found in the mapper's switch arm.
- Flagged method: public MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordCompleteDataResponse>> toResponse(CompleteForgotPasswordResult result)
- Flagged expression: the switch arm

```java
case DUPLICATE_IN_PROGRESS, BUSINESS_REJECTED ->
    result.status() == CompleteForgotPasswordStatus.DUPLICATE_IN_PROGRESS
        ? ApiStatus.BUSINESS_REJECTED
        : ApiStatus.BUSINESS_REJECTED;
```

1) Exact flagged method and expression
- The method `toResponse(CompleteForgotPasswordResult result)` performs a switch over `result.status()` and maps business/completion statuses to ApiStatus values. The specific switch arm above returns ApiStatus.BUSINESS_REJECTED in both branches of the ternary and is therefore redundant.

2) Complete mapper inspection (surrounding switch)
- The relevant mapping logic in `toResponse` is:

```java
if (result.status() == CompleteForgotPasswordStatus.SUCCESS) {
  return MutationResponse.success(ApiStatus.SUCCESS, payload, List.of());
}
ApiStatus status =
    switch (result.status()) {
      case VALIDATION_FAILED, INVALID_AUTH_FLOW, CSRF_INVALID, IDEMPOTENCY_KEY_REUSED ->
          ApiStatus.VALIDATION_FAILED;
      case DUPLICATE_IN_PROGRESS, BUSINESS_REJECTED ->
          result.status() == CompleteForgotPasswordStatus.DUPLICATE_IN_PROGRESS
              ? ApiStatus.BUSINESS_REJECTED
              : ApiStatus.BUSINESS_REJECTED;
      case UNKNOWN_OUTCOME, SYSTEM_FAILED -> ApiStatus.SYSTEM_ERROR;
      case SUCCESS -> ApiStatus.SUCCESS;
    };
```

- After determining `status`, the mapper builds `errors` from `result.errors()` and returns `MutationResponse.failure(status, payload, errors)`.

3) The two statuses with identical branches
- DUPLICATE_IN_PROGRESS
- BUSINESS_REJECTED

Both currently map to ApiStatus.BUSINESS_REJECTED (the ternary returns the same value).

4) Confirm existing public response for mapped completion statuses
- SUCCESS -> MutationResponse.success(ApiStatus.SUCCESS, payload, List.of()) (no errors/messages)
- VALIDATION_FAILED -> ApiStatus.VALIDATION_FAILED (mapped in switch arm); errors are populated from result.errors()
- DUPLICATE_IN_PROGRESS -> ApiStatus.BUSINESS_REJECTED (as implemented)
- BUSINESS_REJECTED -> ApiStatus.BUSINESS_REJECTED (as implemented)
- UNKNOWN_OUTCOME -> ApiStatus.SYSTEM_ERROR
- SYSTEM_FAILED -> ApiStatus.SYSTEM_ERROR

Note: the public mutation response shape is defined by `MutationResponse<T>`: success(...) returns success=true, given status, payload, messages; failure(...) returns success=false, status, result (may be present), and errors. The mapper uses MutationResponse.failure(status, payload, errors) for non-success statuses, so the returned response will have success=false, the mapped ApiStatus, the same payload (nextStep + empty data), and errors mapped from the workflow errors.

5) Existing tests that exercise this mapping
- Public contract and controller tests reference mapper behavior; specifically:
  - src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/forgotpassword/mapper/ForgotPasswordPublicResponseContractSubtask008FailFirstTest.java exercises several mappers including the complete mapper for VALIDATION_FAILED and systemUnavailable cases.
  - src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordCompleteControllerTest.java uses a RecordingUseCase that returns SUCCESS and asserts success-path behavior.
  - src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/forgotpassword/mapper/ForgotPasswordCompletionActorRoleProvenanceTest.java tests command mapping and role.
- No existing focused mapper-level unit test specifically asserts the mapping for DUPLICATE_IN_PROGRESS and BUSINESS_REJECTED in `ForgotPasswordCompleteWebMapper`.

6) Git history for the duplicate branch
- `git blame` shows the switch and its ternary exist since 2026-08-19 (commit 90e14ec7, author Nick Deng). The file was originally added earlier (4ef68981), and the ternary appears in the later formatting/coverage commit. No commit message documents an intentional differentiation between the two cases; the ternary appears accidental or leftover from an earlier edit.

7) Is the proposed behavior-preserving correction valid?
- Proposed correction (behavior-preserving): replace the ternary with a consolidated single arm covering both statuses and returning `ApiStatus.BUSINESS_REJECTED`.

Consolidation example (approved):

```java
case DUPLICATE_IN_PROGRESS, BUSINESS_REJECTED -> ApiStatus.BUSINESS_REJECTED;
```

- This is behavior-preserving because both branches already returned ApiStatus.BUSINESS_REJECTED.
- It does not change how errors are mapped or the payload being returned; only the redundant ternary is removed.

8) Mapper-level test existence
- No focused mapper-level test exists that explicitly asserts that DUPLICATE_IN_PROGRESS and BUSINESS_REJECTED both map to ApiStatus.BUSINESS_REJECTED. Therefore adding a small mapper test is recommended to protect the behavior and prevent regressions.

9) Exact new test file and cases required (if none exist)
- New test file (recommended):
  - src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/forgotpassword/mapper/ForgotPasswordCompleteWebMapperTest.java
- Exact test cases (minimal to protect this change):
  1. mapsDuplicateInProgressToBusinessRejected():
     - Create `CompleteForgotPasswordResult` with status = DUPLICATE_IN_PROGRESS, nextStep = WorkflowNextStep.NONE (or other), errors = List.of(new WorkflowErrorDescriptor(...))
     - Call `new ForgotPasswordCompleteWebMapper().toResponse(result)`
     - Assert `response.status() == ApiStatus.BUSINESS_REJECTED`
     - Assert `response.errors()` contains the mapped ApiError with code equal to error.code(), message == null, targets trimmed as before.
  2. mapsBusinessRejectedToBusinessRejected():
     - Same as above but status = BUSINESS_REJECTED
     - Same assertions.
- Optionally add: assert that both responses are equal in status and error content to safeguard parity.

10) Smallest expected file set for implementation
- Production: no production Java change beyond consolidating the switch arm in the single mapper file.
- Tests: add the new mapper test file above.
- Docs: update Unit 3 status in docs/sonarqube/sonarqube-bug-remediation-plan.md when implementing and validating.

11) Compile and test-compile gates
- mvn -DskipTests compile
- mvn -DskipTests test-compile

12) Exact targeted-test commands
- mvn -Dtest=ForgotPasswordCompleteWebMapperTest test (new test class)
- Recommended broader validation after mapper test passes:
  - mvn -Dtest=AuthForgotPasswordCompleteControllerTest,ForgotPasswordPublicResponseContractSubtask008FailFirstTest test

13) Risks, blocked conditions, preserved behavior, review checkpoints
- Risks:
  - If other tests (integration or controller-level) implicitly expect different public treatment for DUPLICATE_IN_PROGRESS vs BUSINESS_REJECTED (e.g., different messages or workflow steps), consolidation could mask an intended difference. No such tests were found, and git history gives no evidence of intentional distinct mapping.
  - If downstream clients rely on distinct error codes within the errors() payload rather than ApiStatus, ensure errors mapping remains unchanged (the mapper builds errors from `result.errors()` unchanged).
- Blocked conditions:
  - If a controller-level or contract test expects different behavior for these two statuses, implementation must pause and reconcile with product owner.
- Review checkpoints:
  1. Show the exact code diff for the single-line consolidation.
  2. Add the new mapper test file and show its diff.
  3. Run compile and test-compile gates.
  4. Run targeted test(s) and present exact results.

14) Provisional conventional commit message
- `test(forgot-password): consolidate rejected status mapping`

15) Decision
- Recommendation: VALIDATED
  - Consolidation to `case DUPLICATE_IN_PROGRESS, BUSINESS_REJECTED -> ApiStatus.BUSINESS_REJECTED;` is safe and behavior-preserving.
  - Add a focused mapper test (ForgotPasswordCompleteWebMapperTest) with two cases described above.

Files to change when authorized (minimal):
- src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/forgotpassword/mapper/ForgotPasswordCompleteWebMapper.java (single-line switch-arm consolidation)
- src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/forgotpassword/mapper/ForgotPasswordCompleteWebMapperTest.java (new test with two cases)
- docs/sonarqube/sonarqube-bug-remediation-plan.md (update Unit 3 status to IMPLEMENTED then VALIDATED after tests)
- docs/sonarqube/unit-03-forgot-password-mapper-s3923-investigation.md (this document)

Investigation author: Copilot CLI

Status: VALIDATED

Validation evidence (commands run and outcomes):
- mvn -DskipTests compile: SUCCESS
- mvn -DskipTests test-compile: SUCCESS
- mvn -Dtest=ForgotPasswordCompleteWebMapperTest test: 2 tests run, 0 failures, 0 errors, 0 skipped
- mvn -Dtest=AuthForgotPasswordCompleteControllerTest,ForgotPasswordPublicResponseContractSubtask008FailFirstTest test: 5 tests run, 0 failures, 0 errors, 0 skipped

Files changed in this unit (working tree, intended commit):
- src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/forgotpassword/mapper/ForgotPasswordCompleteWebMapper.java
- src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/forgotpassword/mapper/ForgotPasswordCompleteWebMapperTest.java

Provisional commit message:

``
test(forgot-password): consolidate rejected status mapping
```

## Implementation and validation record

Implemented files:

- `src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/forgotpassword/mapper/ForgotPasswordCompleteWebMapper.java`
- `src/test/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/forgotpassword/mapper/ForgotPasswordCompleteWebMapperTest.java`

Implemented correction:

```java
case DUPLICATE_IN_PROGRESS, BUSINESS_REJECTED -> ApiStatus.BUSINESS_REJECTED;
```

The focused mapper test verifies both statuses and preserves the failure-response shape, including `success`, `ApiStatus`, payload presence, `WorkflowNextStep.NONE`, and representative error mapping.

Validation commands and results:

```bash
mvn -DskipTests compile
mvn -DskipTests test-compile
mvn -Dtest=ForgotPasswordCompleteWebMapperTest test
mvn -Dtest=AuthForgotPasswordCompleteControllerTest,ForgotPasswordPublicResponseContractSubtask008FailFirstTest test
```

```text
Compile: SUCCESS
Test-compile: SUCCESS
Focused mapper tests: 2 run, 0 failures, 0 errors, 0 skipped
Related controller/contract tests: 5 run, 0 failures, 0 errors, 0 skipped
git diff --check: SUCCESS
```

## Final decision

**VALIDATED**

Unit 3 is ready for independent pre-commit approval. The commit hash remains empty until the approved commit exists.

Approved commit message:

```text
test(forgot-password): consolidate rejected status mapping
```
