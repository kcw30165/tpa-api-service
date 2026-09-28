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
