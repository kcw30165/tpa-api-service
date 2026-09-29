# Registration Mapper Package — Investigation Baseline

- Generated: 2026-09-28T08:37:34Z
- Post-rebase HEAD: a2ebb84806f297ca09e8ac2216cbe7e3fbeb58d6
- Review branch: chore/registration-mapper-coverage-investigation
- Target package: `com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper`
- Test run exit code: 0

## Physical class inventory (on disk, this investigation)

Total .java files found: 7

See evidence file `03-package-contents.txt` for the full listing.

## Existing test coverage of this package

Untested classes (zero test-file references found via grep):
```
=== Classes in target package with ZERO test-file references found ===
RegistrationWorkflowMutationResponseMapper
```

## Coverage data (from fresh post-rebase jacoco.xml)

```
Found 7 class(es) with coverage data for package 'com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper'.
```

Full per-class coverage: see evidence file
`08-target-package-coverage.csv`.

## Disk-vs-jacoco cross-check

See evidence file `09-disk-vs-jacoco-crosscheck.txt` for classes
physically present but never exercised by any test (would not
appear in the coverage report at all).

## Status
This is INVESTIGATION ONLY. No tier decision, no test code, and no
production code changes have been made. Next step requires review
of this evidence before any design or implementation begins.

## Per-Method Branch Breakdown

- Generated: 2026-09-28T08:42:28Z
- Source: same jacoco.xml as script 007 (no mvn re-run; report is
  still valid since no source files changed since that run).
- Total methods across all 7 classes: 94
- Total source lines with at least one missed branch: 51

### Per-class rollup

```
=== Summary ===
Classes found in package: 7
Total methods across all 7 classes: 94
Total lines with at least one missed branch: 51

=== Per-class rollup (for cross-check against script 007 class-level numbers) ===
CancelRegistrationWebMapper: instruction=55.84% (86/154), branch=40.00% (4/10), methods=10
CompleteRegistrationWebMapper: instruction=63.88% (145/227), branch=40.91% (9/22), methods=10
RegistrationWorkflowMutationResponseMapper: instruction=84.00% (42/50), branch=50.00% (1/2), methods=3
RegistrationUsernameCheckWebMapper: instruction=80.62% (208/258), branch=51.11% (23/45), methods=16
RegistrationOtpVerifyWebMapper: instruction=76.12% (204/268), branch=55.26% (21/38), methods=17
RegistrationStartWebMapper: instruction=85.75% (367/428), branch=66.18% (45/68), methods=22
RegistrationOtpResendWebMapper: instruction=93.92% (247/263), branch=70.73% (29/41), methods=16
```

### Evidence files
- Full per-method breakdown (all methods, all 7 classes, sorted
  worst-branch-%-first within each class): see evidence file
  `03-per-method-branch-detail.csv`.
- Full per-line uncovered-branch detail (every source line with at
  least one missed branch, with instruction/branch miss counts):
  see evidence file `04-uncovered-branch-lines.csv`.

### Status
This is INVESTIGATION ONLY. No test code and no production code
changes have been made. This data identifies exactly which methods
and source lines have uncovered branches, to inform test design
once implementation begins.

## Full-Source Evidence Dump

- Generated: 2026-09-28T08:48:09Z
- Purpose: full source of all 7 registration mapper
  classes, plus full source of all 24 existing test
  files that reference any of them (re-derived via fresh grep, not
  reused from prior script evidence), to enable correct test design
  once implementation begins.
- Source content is NOT committed to git history. It exists only
  in the evidence zip (`src_main/`, `src_test/`, and
  `06-manifest.md` for navigation).

### Status
This is INVESTIGATION ONLY. No test code and no production code
changes have been made. This completes the pre-implementation
understanding phase for this package: baseline coverage (script
007), per-method/per-line branch detail (script 008), and now full
source of both production and test code (script 009).

## Status Enum Evidence Dump

- Generated: 2026-09-28T08:53:52Z
- Purpose: full source of the 6 *ResultStatus
  enums that the 7 registration mapper classes switch over (found
  via script 009's source dump, but not themselves captured
  there). Enum locations were found via a fresh `find` by
  filename, not assumed.
- Enums dumped:
  - `ResendRegistrationOtpResultStatus` (`src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/otp/ResendRegistrationOtpResultStatus.java`)
  - `VerifyRegistrationOtpResultStatus` (`src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/otp/VerifyRegistrationOtpResultStatus.java`)
  - `StartRegistrationResultStatus` (`src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/StartRegistrationResultStatus.java`)
  - `CheckRegistrationUsernameResultStatus` (`src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/username/CheckRegistrationUsernameResultStatus.java`)
  - `CancelRegistrationResultStatus` (`src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/cancel/CancelRegistrationResultStatus.java`)
  - `CompleteRegistrationResultStatus` (`src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/complete/CompleteRegistrationResultStatus.java`)
- Source content is NOT committed to git history. It exists only
  in the evidence zip (`src_main_enums/`, plus
  `04-enum-constants-quickref.txt` and `05-manifest.md`).

### Status
This is INVESTIGATION ONLY. No test code and no production code
changes have been made. This closes the last gap in
pre-implementation understanding for this package: baseline
coverage (007), per-method/per-line branch detail (008), mapper
source (009), and now the complete enum constant lists these
mappers switch over (010). Test design can now proceed against
the real, complete set of enum values for each status type.

## Result Record Evidence Dump

- Generated: 2026-09-28T08:59:06Z
- Trigger: comparing existing test call sites for
  `ResendRegistrationOtpResult` revealed inconsistent constructor
  argument counts (10 vs 11) across two different test files,
  which could not be resolved without seeing the real record
  definition. Writing new test code against a guessed constructor
  shape would risk producing code that does not compile.
- Purpose: full source of the 6 *Result record
  classes that the 7 registration mapper classes consume.
  Locations found via a fresh `find` by filename, not assumed.
- Records dumped:
  - `ResendRegistrationOtpResult` (`src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/otp/ResendRegistrationOtpResult.java`)
  - `VerifyRegistrationOtpResult` (`src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/otp/VerifyRegistrationOtpResult.java`)
  - `StartRegistrationResult` (`src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/StartRegistrationResult.java`)
  - `CheckRegistrationUsernameResult` (`src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/username/CheckRegistrationUsernameResult.java`)
  - `CancelRegistrationResult` (`src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/cancel/CancelRegistrationResult.java`)
  - `CompleteRegistrationResult` (`src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/complete/CompleteRegistrationResult.java`)
- Source content is NOT committed to git history. It exists only
  in the evidence zip (`src_main_records/`, plus
  `04-record-components-quickref.txt` and `05-manifest.md`).

### Status
This is INVESTIGATION ONLY. No test code and no production code
changes have been made. This resolves the constructor-shape
ambiguity discovered while preparing to design new test cases,
and completes the full set of evidence needed before writing any
new test code: baseline coverage (007), per-method/per-line
branch detail (008), mapper source (009), status enum constants
(010), and now the exact result-record shapes these mappers
consume (011).

## Page Mapper Collaborator Evidence Dump

- Generated: 2026-09-28T09:04:21Z
- Trigger: CancelRegistrationWebMapper and
  CompleteRegistrationWebMapper have no dedicated unit test file
  and depend on page-mapper collaborators fed by
  `BffPagesProperties`. Before writing new unit tests, it must be
  known whether these collaborators are safely constructible
  outside a Spring context, or require YAML-bound configuration.
- Files dumped:
  - `RegistrationCancellationPageMapper` (`src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/pageconfig/registration/RegistrationCancellationPageMapper.java`)
  - `RegistrationSuccessPageMapper` (`src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/pageconfig/registration/RegistrationSuccessPageMapper.java`)
  - `BffPagesProperties` (`src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/pageconfig/BffPagesProperties.java`)
- Source content is NOT committed to git history. It exists only
  in the evidence zip (`src_main_collaborators/`, plus
  `04-configuration-binding-risk-signals.txt` and
  `05-manifest.md`).

### Status
This is INVESTIGATION ONLY. No test code and no production code
changes have been made. Once reviewed, this determines whether
CancelRegistrationWebMapper/CompleteRegistrationWebMapper can get
new plain unit test files, or whether their branch-coverage gaps
must instead be closed by extending the existing
Spring-context-backed controller test
(AuthRegistrationCompleteCancelControllerTest).

## Workflow Mutation Response Mapper Evidence Dump

- Generated: 2026-09-28T09:56:02Z
- Trigger: closing the remaining switch-statement branch gaps in
  RegistrationOtpResendWebMapper/RegistrationOtpVerifyWebMapper
  requires exercising enum values that map to
  WorkflowResultStatus.BUSINESS_FAILED, but no existing test
  confirms what ApiStatus/success() value BUSINESS_FAILED (or
  WAITING) produces at the response level.
- Files dumped:
  - `WorkflowMutationResponseMapper` (`src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/workflow/WorkflowMutationResponseMapper.java`)
  - `WorkflowResultPayload` (`src/main/java/com/bct/ngtpa/apiservice/application/dto/workflow/WorkflowResultPayload.java`)
- Source content is NOT committed to git history. It exists only
  in the evidence zip (`src_main_delegate/`, plus
  `04-status-mapping-quickref.txt` and `05-manifest.md`).

### Status
This is INVESTIGATION ONLY. No test code and no production code
changes have been made. Once reviewed, this determines exactly
which ApiStatus/success() assertions are safe to write for
BUSINESS_FAILED-mapped and WAITING-mapped enum values across all
registration mapper classes that share this delegate (not just
Resend/Verify -- also relevant to RegistrationStartWebMapper's
own untested BUSINESS_FAILED-mapped statuses, e.g.
VERIFICATION_FAILED, DOWNSTREAM_REJECTED, IDEMPOTENCY_KEY_REUSED).

## WorkflowResult Evidence Dump

- Generated: 2026-09-28T10:00:29Z
- Trigger: script 014 showed toMutationResponse() branches on
  `result.isSuccessLike()`, not a direct status equality check.
  No existing test confirms this method's return value for any
  BUSINESS_FAILED-producing status.
- File dumped: `WorkflowResult` (`src/main/java/com/bct/ngtpa/apiservice/application/dto/workflow/WorkflowResult.java`)
- Source content is NOT committed to git history. It exists only
  in the evidence zip (`src_main_workflow_result/`, plus
  `04-is-success-like-quickref.txt` and `05-manifest.md`).

### Status
This is INVESTIGATION ONLY. No test code and no production code
changes have been made. Once reviewed, this gives the final fact
needed to correctly assert response.success() for
BUSINESS_FAILED-mapped enum values in the next implementation
script extending RegistrationOtpResendWebMapperTest and
RegistrationOtpVerifyWebMapperTest.

## Start Registration Error Key Evidence Dump

- Generated: 2026-09-29T01:05:17Z
- Trigger: extending RegistrationStartWebMapperTest requires
  constructing StartRegistrationError with a blank target and
  with error keys for DOWNSTREAM_REJECTED/IDEMPOTENCY_KEY_REUSED
  statuses. Only 4 real call sites (none with a blank target)
  have been observed across existing tests.
- Files dumped:
  - `StartRegistrationError` (`src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/StartRegistrationError.java`)
  - `RegistrationErrorKey` (`src/main/java/com/bct/ngtpa/apiservice/application/dto/workflow/RegistrationErrorKey.java`)
  - `SharedWorkflowErrorKey` (`src/main/java/com/bct/ngtpa/apiservice/application/dto/workflow/SharedWorkflowErrorKey.java`)
- Source content is NOT committed to git history. It exists only
  in the evidence zip (`src_main_error_key/`, plus
  `04-error-key-quickref.txt` and `05-manifest.md`).

### Status
This is INVESTIGATION ONLY. No test code and no production code
changes have been made. Once reviewed, this gives the final fact
needed to safely extend RegistrationStartWebMapperTest with
blank-target and DOWNSTREAM_REJECTED/IDEMPOTENCY_KEY_REUSED test
cases without risking a compile-clean-but-runtime-broken test.

## RegistrationStartWebMapper: Two Branches Documented As Unreachable

- Generated: 2026-09-29T01:19:53Z
- `normalizeTarget`'s `target == null || target.isBlank()` check:
  `StartRegistrationError`'s own compact constructor (script 017
  dump) already normalizes a null/blank target to `"PAGE"` before
  the object is stored. `normalizeTarget` is only ever called with
  `error.target()`, which can therefore never be null/blank at that
  call site. Justification: unreachable given upstream validation.
- `toWorkflowError`'s `target == null ? List.of() : List.of(target)`
  ternary: `normalizeTarget` itself never returns null (only
  `TARGET_PAGE` or a trimmed non-blank string), so this ternary's
  null-branch is unreachable for the same underlying reason.
- Both are intentionally NOT force-tested; per this project's own
  coverage-tiering policy, uncovered branches may be justified as
  "unreachable given upstream validation" rather than covered.
