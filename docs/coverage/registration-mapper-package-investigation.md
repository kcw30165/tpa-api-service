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
