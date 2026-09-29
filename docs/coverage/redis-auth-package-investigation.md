# Redis Auth Package — Investigation Baseline

- Generated: 2026-09-29T06:06:59Z
- Branch: `chore/redis-auth-coverage-investigation` (new, created from `origin/dev` at HEAD `a2ebb84806f297ca09e8ac2216cbe7e3fbeb58d6`)
- Target package prefix: `com.bct.ngtpa.apiservice.adapter.out.redis.auth` (recursive -- includes ALL sub-packages)
- Test run exit code: 0

## Physical class inventory (recursive, on disk)

Total .java files found: 44

### Breakdown by exact sub-package
```
=== All .java files found under src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth (recursive) ===
Total: 44

=== Breakdown by exact sub-package (directory) ===
     18 com.bct.ngtpa.apiservice.adapter.out.redis.auth
      4 com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword
      4 com.bct.ngtpa.apiservice.adapter.out.redis.auth.compatibility
      3 com.bct.ngtpa.apiservice.adapter.out.redis.auth.usernamerecovery.replay
      3 com.bct.ngtpa.apiservice.adapter.out.redis.auth.registration.replay
      3 com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.replay
      3 com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword.replay
      2 com.bct.ngtpa.apiservice.adapter.out.redis.auth.revision
      2 com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.otp.verify
      1 com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.handoff
      1 com.bct.ngtpa.apiservice.adapter.out.redis.auth.login
```

See evidence file `02-package-contents-all.txt` for the full file listing.

## Existing test coverage of this package (fresh grep, not jacoco)

```
=== Per-class existing-test-reference check (fresh grep, simple class name) ===
Classes in target prefix with ZERO test-file references found:
  - RedisAuthFlowSessionRecord (src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/RedisAuthFlowSessionRecord.java)
  - RedisCaptchaChallengeRecord (src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/RedisCaptchaChallengeRecord.java)
  - RedisOtpChallengeRecord (src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/RedisOtpChallengeRecord.java)
  - RedisForgotPasswordOtpCommitStateRecord (src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/forgotpassword/RedisForgotPasswordOtpCommitStateRecord.java)

Total classes with zero test references: 4 / 44
```

## Coverage data (from fresh full-suite jacoco.xml, mvn clean test on
   a brand-new checkout of origin/dev)

```
=== Found 11 matching package(s), 48 total class(es) under prefix 'com.bct.ngtpa.apiservice.adapter.out.redis.auth' ===

com.bct.ngtpa.apiservice.adapter.out.redis.auth (24 classes): instruction=88.12% (3063/3476), branch=66.67% (164/246)
com.bct.ngtpa.apiservice.adapter.out.redis.auth.compatibility (3 classes): instruction=100.00% (218/218), branch=95.00% (19/20)
com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword (4 classes): instruction=84.92% (169/199), branch=62.50% (10/16)
com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword.replay (3 classes): instruction=81.48% (176/216), branch=78.57% (11/14)
com.bct.ngtpa.apiservice.adapter.out.redis.auth.login (1 classes): instruction=60.00% (27/45), branch=50.00% (5/10)
com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.handoff (1 classes): instruction=93.78% (347/370), branch=75.00% (24/32)
com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.otp.verify (1 classes): instruction=97.97% (579/591), branch=95.65% (44/46)
com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.replay (3 classes): instruction=79.45% (201/253), branch=61.11% (11/18)
com.bct.ngtpa.apiservice.adapter.out.redis.auth.registration.replay (3 classes): instruction=80.77% (168/208), branch=78.57% (11/14)
com.bct.ngtpa.apiservice.adapter.out.redis.auth.revision (2 classes): instruction=100.00% (206/206), branch=85.71% (12/14)
com.bct.ngtpa.apiservice.adapter.out.redis.auth.usernamerecovery.replay (3 classes): instruction=87.35% (359/411), branch=70.59% (24/34)

=== OVERALL across all matching packages: instruction=89.02% (5513/6193), branch=72.20% (335/464) ===
```

Full per-class coverage (every class, every matching sub-package,
sorted worst-branch-%-first within each package): see evidence file
`08-per-class-coverage.csv`.

## Disk-vs-jacoco cross-check

```
=== Disk-vs-jacoco cross-check ===
Classes physically found on disk (via find):
Classes on disk: 44
Classes in jacoco report: 48
MISMATCH: -4 class(es) on disk do not appear in the jacoco report at all.
(likely candidates: interfaces, records/enums with no instructions, or classes never loaded by any test)

Disk class names (simple):
Classes on disk but NOT in jacoco report:
AuthFlowSessionPersistenceSchemaProperties
LoginOtpVerifyRetentionProperties
```

## Coverage-tiering note

Per this project's own coverage-tiering policy: adapters and
orchestration-logic classes (which is what this entire prefix
consists of -- Redis adapters) are Tier-B, requiring >=95%
instruction coverage (gated) and 85-90% branch coverage (gated,
with any remaining uncovered branch requiring a one-line
justification such as "unreachable given upstream validation" or
"defensive, not independently testable").

## Status

This is INVESTIGATION ONLY. No tier decision beyond the general
Tier-B classification above, no test code, and no production code
changes have been made. Given the size of this prefix (44
classes across multiple sub-packages), the next step is to review
this baseline and deliberately choose a scoped starting point
(e.g. the worst-covered sub-package, or a specific small set of
classes) rather than attempting the entire prefix at once --
mirroring the lesson learned from the registration-mapper
workstream, where scope was manageable (7 classes) but even then
required multiple correction rounds.
