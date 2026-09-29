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

## Deep-Dive: Four Zero-Coverage Classes

- Generated: 2026-09-29T06:15:15Z
- Scope chosen per script 034's ranked recommendation: smallest,
  self-contained, likely-fastest path to closing gaps.
- Targets:
  - `RedisForgotPasswordStartProtectedReplayKeyFactory`
  - `RedisLoginStartReplayKeyFactory`
  - `RedisRegistrationStartProtectedReplayKeyFactory`
  - `RedisUsernameRecoveryStartProtectedReplayJsonMapper`

### Per-class call-site investigation

```
======================================================================
TARGET: RedisForgotPasswordStartProtectedReplayKeyFactory
======================================================================
Located: src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/forgotpassword/replay/RedisForgotPasswordStartProtectedReplayKeyFactory.java
Full source copied to: src_dumps/RedisForgotPasswordStartProtectedReplayKeyFactory.java (18 lines)

--- Call sites in src/main/java (grep -rn "RedisForgotPasswordStartProtectedReplayKeyFactory") ---
src/main/java/com/bct/ngtpa/apiservice/config/auth/forgotpassword/ForgotPasswordStartProtectedReplayConfig.java:6:import com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword.replay.RedisForgotPasswordStartProtectedReplayKeyFactory;
src/main/java/com/bct/ngtpa/apiservice/config/auth/forgotpassword/ForgotPasswordStartProtectedReplayConfig.java:43:        new RedisForgotPasswordStartProtectedReplayKeyFactory(keyFactory),
src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/forgotpassword/replay/RedisForgotPasswordStartProtectedReplayAdapter.java:17:  private final RedisForgotPasswordStartProtectedReplayKeyFactory keys;
src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/forgotpassword/replay/RedisForgotPasswordStartProtectedReplayAdapter.java:24:      RedisForgotPasswordStartProtectedReplayKeyFactory keys,

--- Call sites in src/test/java (grep -rn "RedisForgotPasswordStartProtectedReplayKeyFactory") ---
src/test/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/forgotpassword/replay/RedisForgotPasswordStartProtectedReplayAdapterTest.java

======================================================================
TARGET: RedisLoginStartReplayKeyFactory
======================================================================
Located: src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/login/replay/RedisLoginStartReplayKeyFactory.java
Full source copied to: src_dumps/RedisLoginStartReplayKeyFactory.java (16 lines)

--- Call sites in src/main/java (grep -rn "RedisLoginStartReplayKeyFactory") ---
src/main/java/com/bct/ngtpa/apiservice/config/auth/login/LoginStartReplayConfig.java:28:  RedisLoginStartReplayKeyFactory redisLoginStartReplayKeyFactory(
src/main/java/com/bct/ngtpa/apiservice/config/auth/login/LoginStartReplayConfig.java:30:    return new RedisLoginStartReplayKeyFactory(prefix);
src/main/java/com/bct/ngtpa/apiservice/config/auth/login/LoginStartReplayConfig.java:35:      RedisLoginStartReplayKeyFactory keys,
src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/login/replay/RedisLoginStartReplayAdapter.java:12:  private final RedisLoginStartReplayKeyFactory keys;
src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/login/replay/RedisLoginStartReplayAdapter.java:17:      RedisLoginStartReplayKeyFactory keys,

--- Call sites in src/test/java (grep -rn "RedisLoginStartReplayKeyFactory") ---
src/test/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/login/replay/RedisLoginStartReplayAdapterTest.java
src/test/java/com/bct/ngtpa/apiservice/architecture/Task0043LoginStartReplayArchitectureTest.java

======================================================================
TARGET: RedisRegistrationStartProtectedReplayKeyFactory
======================================================================
Located: src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/registration/replay/RedisRegistrationStartProtectedReplayKeyFactory.java
Full source copied to: src_dumps/RedisRegistrationStartProtectedReplayKeyFactory.java (18 lines)

--- Call sites in src/main/java (grep -rn "RedisRegistrationStartProtectedReplayKeyFactory") ---
src/main/java/com/bct/ngtpa/apiservice/config/registration/RegistrationStartProtectedReplayConfig.java:6:import com.bct.ngtpa.apiservice.adapter.out.redis.auth.registration.replay.RedisRegistrationStartProtectedReplayKeyFactory;
src/main/java/com/bct/ngtpa/apiservice/config/registration/RegistrationStartProtectedReplayConfig.java:43:        new RedisRegistrationStartProtectedReplayKeyFactory(keyFactory),
src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/registration/replay/RedisRegistrationStartProtectedReplayAdapter.java:17:  private final RedisRegistrationStartProtectedReplayKeyFactory keys;
src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/registration/replay/RedisRegistrationStartProtectedReplayAdapter.java:24:      RedisRegistrationStartProtectedReplayKeyFactory keys,

--- Call sites in src/test/java (grep -rn "RedisRegistrationStartProtectedReplayKeyFactory") ---
src/test/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/registration/replay/RedisRegistrationStartProtectedReplayAdapterTest.java

======================================================================
TARGET: RedisUsernameRecoveryStartProtectedReplayJsonMapper
======================================================================
Located: src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/usernamerecovery/replay/RedisUsernameRecoveryStartProtectedReplayJsonMapper.java
Full source copied to: src_dumps/RedisUsernameRecoveryStartProtectedReplayJsonMapper.java (29 lines)

--- Call sites in src/main/java (grep -rn "RedisUsernameRecoveryStartProtectedReplayJsonMapper") ---
src/main/java/com/bct/ngtpa/apiservice/config/auth/usernamerecovery/UsernameRecoveryStartProtectedReplayConfig.java:5:import com.bct.ngtpa.apiservice.adapter.out.redis.auth.usernamerecovery.replay.RedisUsernameRecoveryStartProtectedReplayJsonMapper;
src/main/java/com/bct/ngtpa/apiservice/config/auth/usernamerecovery/UsernameRecoveryStartProtectedReplayConfig.java:54:        new RedisUsernameRecoveryStartProtectedReplayJsonMapper(),
src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/usernamerecovery/replay/RedisUsernameRecoveryStartProtectedReplayAdapter.java:28:  private final RedisUsernameRecoveryStartProtectedReplayJsonMapper json;
src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/usernamerecovery/replay/RedisUsernameRecoveryStartProtectedReplayAdapter.java:39:      RedisUsernameRecoveryStartProtectedReplayJsonMapper json,

--- Call sites in src/test/java (grep -rn "RedisUsernameRecoveryStartProtectedReplayJsonMapper") ---
src/test/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/usernamerecovery/replay/RedisUsernameRecoveryStartProtectedReplayAdapterTest.java

```

### Sibling reference classes (already tested, pattern templates)

```
======================================================================
SIBLINGS for: RedisForgotPasswordStartProtectedReplayKeyFactory
======================================================================
sibling located: src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/usernamerecovery/replay/RedisUsernameRecoveryStartProtectedReplayKeyFactory.java
  test file dumped: sibling_test_dumps/RedisUsernameRecoveryStartProtectedReplayKeyFactoryTest.java (20 lines)
sibling located: src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/registration/replay/RedisRegistrationStartProtectedReplayKeyFactory.java
  (no RedisRegistrationStartProtectedReplayKeyFactoryTest.java found -- sibling may be covered only indirectly)

======================================================================
SIBLINGS for: RedisLoginStartReplayKeyFactory
======================================================================
sibling located: src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/usernamerecovery/replay/RedisUsernameRecoveryStartProtectedReplayKeyFactory.java
  test file dumped: sibling_test_dumps/RedisUsernameRecoveryStartProtectedReplayKeyFactoryTest.java (20 lines)
sibling located: src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/registration/replay/RedisRegistrationStartProtectedReplayKeyFactory.java
  (no RedisRegistrationStartProtectedReplayKeyFactoryTest.java found -- sibling may be covered only indirectly)

======================================================================
SIBLINGS for: RedisRegistrationStartProtectedReplayKeyFactory
======================================================================
sibling located: src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/usernamerecovery/replay/RedisUsernameRecoveryStartProtectedReplayKeyFactory.java
  test file dumped: sibling_test_dumps/RedisUsernameRecoveryStartProtectedReplayKeyFactoryTest.java (20 lines)

======================================================================
SIBLINGS for: RedisUsernameRecoveryStartProtectedReplayJsonMapper
======================================================================
sibling located: src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/forgotpassword/replay/RedisForgotPasswordStartProtectedReplayJsonMapper.java
  test file dumped: sibling_test_dumps/RedisForgotPasswordStartProtectedReplayJsonMapperTest.java (35 lines)
sibling located: src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/registration/replay/RedisRegistrationStartProtectedReplayJsonMapper.java
  test file dumped: sibling_test_dumps/RedisRegistrationStartProtectedReplayJsonMapperTest.java (35 lines)

```

Full source of all 4 targets and their siblings (plus siblings'
existing test files where found) preserved in the evidence zip
under `src_dumps/`, `sibling_dumps/`, and `sibling_test_dumps/`.

### Status

This is INVESTIGATION ONLY. No test or production code changes
have been made. Next step: use this evidence (real source +
sibling test patterns) to write real, validation-passing tests
for these 4 classes, following the same reflective/pre-validation
discipline established after the earlier registration-mapper
stub-fabrication incident (scripts 029/031/032) -- i.e. base new
tests strictly on the real dumped source, never on assumed shapes.

## Dependency-Shape Investigation for Four Zero-Coverage Classes

- Generated: 2026-09-29T06:19:36Z
- Trigger: script 035's evidence showed the JsonMapper targets/
  siblings apparently reject schemaVersion=2 in their sibling
  tests, but that validation is not present in the JsonMapper
  source itself -- meaning it must live in the wrapped record's
  own compact constructor, which had never been dumped.
- Dependency classes dumped (real source, not inferred from call
  sites or assumed identical to a same-shaped sibling):
  - `ProtectedUsernameRecoveryStartReplay`
  - `LoginStartReplayCommitRef`
  - `RedisCacheKeyFactory`
  - `ProtectedReplayReference`

### Dependency source dumps and locations

```
======================================================================
DEPENDENCY: ProtectedUsernameRecoveryStartReplay
======================================================================
Located (primary): src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/usernamerecovery/idempotency/start/ProtectedUsernameRecoveryStartReplay.java
Full source copied to: dependency_src_dumps/ProtectedUsernameRecoveryStartReplay.java (35 lines)

======================================================================
DEPENDENCY: LoginStartReplayCommitRef
======================================================================
Located (primary): src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/login/replay/LoginStartReplayCommitRef.java
Full source copied to: dependency_src_dumps/LoginStartReplayCommitRef.java (18 lines)

======================================================================
DEPENDENCY: RedisCacheKeyFactory
======================================================================
Located (primary): src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/RedisCacheKeyFactory.java
Full source copied to: dependency_src_dumps/RedisCacheKeyFactory.java (68 lines)

======================================================================
DEPENDENCY: ProtectedReplayReference
======================================================================
Located (primary): src/main/java/com/bct/ngtpa/apiservice/application/idempotency/ProtectedReplayReference.java
Full source copied to: dependency_src_dumps/ProtectedReplayReference.java (21 lines)

```

Full source of all 4 dependency classes preserved in the
evidence zip under `dependency_src_dumps/`.

### Existing test usage cross-reference

```
======================================================================
EXISTING TEST USAGE for: ProtectedUsernameRecoveryStartReplay
======================================================================
--- Dedicated test file (find -name "ProtectedUsernameRecoveryStartReplayTest.java") ---

--- Construction call sites in tests (grep -rn "new ProtectedUsernameRecoveryStartReplay(") ---

======================================================================
EXISTING TEST USAGE for: LoginStartReplayCommitRef
======================================================================
--- Dedicated test file (find -name "LoginStartReplayCommitRefTest.java") ---

--- Construction call sites in tests (grep -rn "new LoginStartReplayCommitRef(") ---
src/test/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/replay/LoginStartProtectedReplayCommitterTest.java:155:        new LoginStartReplayCommitRef("rpl_opaque_commit_reference");

======================================================================
EXISTING TEST USAGE for: RedisCacheKeyFactory
======================================================================
--- Dedicated test file (find -name "RedisCacheKeyFactoryTest.java") ---
src/test/java/com/bct/ngtpa/apiservice/adapter/out/redis/RedisCacheKeyFactoryTest.java

--- Construction call sites in tests (grep -rn "new RedisCacheKeyFactory(") ---
src/test/java/com/bct/ngtpa/apiservice/config/idempotency/SharedIdempotencyRuntimeConfigTest.java:19:          .withBean(RedisCacheKeyFactory.class, () -> new RedisCacheKeyFactory("ngtpa"))
src/test/java/com/bct/ngtpa/apiservice/config/auth/session/AuthenticatedSessionConfigTest.java:42:            cache, new RedisCacheKeyFactory("ngtpa"), config.redisAuthenticatedSessionJsonMapper());
src/test/java/com/bct/ngtpa/apiservice/adapter/out/redis/config/RedisAdapterConfigTest.java:195:    RedisCacheKeyFactory keyFactory = new RedisCacheKeyFactory("ngtpa");
src/test/java/com/bct/ngtpa/apiservice/adapter/out/redis/config/RedisAdapterConfigTest.java:217:    RedisCacheKeyFactory keyFactory = new RedisCacheKeyFactory("ngtpa");
src/test/java/com/bct/ngtpa/apiservice/adapter/out/redis/idempotency/LoginStartIdempotencyNamespaceIsolationTest.java:36:                new RedisCacheKeyFactory("ngtpa"),
src/test/java/com/bct/ngtpa/apiservice/adapter/out/redis/idempotency/RedisIdempotencyPersistenceFoundationTest.java:95:        new RedisCacheKeyFactory("ngtpa"), new HmacSha256IdempotencyPersistenceIdDeriver(secret));
src/test/java/com/bct/ngtpa/apiservice/adapter/out/redis/idempotency/RedisIdempotencyAdapterTest.java:88:            new RedisCacheKeyFactory("ngtpa"),
src/test/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/usernamerecovery/replay/RedisUsernameRecoveryStartProtectedReplayKeyFactoryTest.java:13:        new RedisUsernameRecoveryStartProtectedReplayKeyFactory(new RedisCacheKeyFactory("ngtpa"))
src/test/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/RegistrationRedisSensitiveDataSafetyIntegrationTest.java:25:            new RedisCacheKeyFactory("ngtpa"), new AuthFlowSessionId("afs_abc123"));
src/test/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/RedisRateLimitAdapterTest.java:41:        new RedisRateLimitAdapter(redisTemplate, new RedisCacheKeyFactory("ngtpa"), FIXED_CLOCK);
src/test/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/RedisCaptchaConsumptionAtomicityTest.java:77:        new RedisCacheKeyFactory("ngtpa"), json, operations, CLOCK);
src/test/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/RedisAuthFlowSessionKeyFactoryTest.java:13:    var keyFactory = new RedisCacheKeyFactory("ngtpa");
src/test/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/RedisAuthenticatedSessionAdapterTest.java:19:          cache, new RedisCacheKeyFactory("ngtpa"), new RedisAuthenticatedSessionJsonMapper());
src/test/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/RedisOtpChallengeStateAdapterTest.java:44:            new RedisCacheKeyFactory("ngtpa"),
src/test/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/RedisAuthFlowSessionCompletionLockAdapterTest.java:28:        new RedisAuthFlowSessionCompletionLockAdapter(redis, new RedisCacheKeyFactory("ngtpa"));

======================================================================
EXISTING TEST USAGE for: ProtectedReplayReference
======================================================================
--- Dedicated test file (find -name "ProtectedReplayReferenceTest.java") ---

--- Construction call sites in tests (grep -rn "new ProtectedReplayReference(") ---
src/test/java/com/bct/ngtpa/apiservice/integration/registration/RegistrationStartWorkflowIntegrationTest.java:691:          new ProtectedReplayReference(
src/test/java/com/bct/ngtpa/apiservice/adapter/out/redis/idempotency/RedisIdempotencyPersistenceFoundationTest.java:107:            new ProtectedReplayReference("protectedReplay01"));
src/test/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/usernamerecovery/replay/RedisUsernameRecoveryStartProtectedReplayAdapterTest.java:75:      new ProtectedReplayReference("replay-ref-1234567890abcd");
src/test/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/usernamerecovery/replay/RedisUsernameRecoveryStartProtectedReplayKeyFactoryTest.java:14:            .keyFor(new ProtectedReplayReference("usernameRecoveryStartReplayReference001"));
src/test/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/forgotpassword/replay/RedisForgotPasswordStartProtectedReplayAdapterTest.java:39:      new ProtectedReplayReference("replay-ref-1234567890abcd");
src/test/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/registration/replay/RedisRegistrationStartProtectedReplayAdapterTest.java:40:      new ProtectedReplayReference("replay-ref-abcdefghijklmn");
src/test/java/com/bct/ngtpa/apiservice/adapter/out/security/usernamerecovery/replay/AesGcmUsernameRecoveryStartReplayProtectorTest.java:19:    var ref = new ProtectedReplayReference("usernameRecoveryStartReplayReference001");
src/test/java/com/bct/ngtpa/apiservice/adapter/out/security/usernamerecovery/replay/AesGcmUsernameRecoveryStartReplayProtectorTest.java:30:                    new ProtectedReplayReference("usernameRecoveryStartReplayReference002"),
src/test/java/com/bct/ngtpa/apiservice/adapter/out/security/forgotpassword/replay/AesGcmForgotPasswordStartReplayProtectorTest.java:18:    var reference = new ProtectedReplayReference("forgotPasswordStartReplayReference001");
src/test/java/com/bct/ngtpa/apiservice/adapter/out/security/forgotpassword/replay/AesGcmForgotPasswordStartReplayProtectorTest.java:30:                    new ProtectedReplayReference("forgotPasswordStartReplayReference002"),
src/test/java/com/bct/ngtpa/apiservice/adapter/out/security/registration/replay/AesGcmRegistrationStartReplayProtectorTest.java:17:    var reference = new ProtectedReplayReference("registrationStartReplayReference001");
src/test/java/com/bct/ngtpa/apiservice/adapter/out/security/registration/replay/AesGcmRegistrationStartReplayProtectorTest.java:31:    var original = new ProtectedReplayReference("registrationStartReplayReference001");
src/test/java/com/bct/ngtpa/apiservice/adapter/out/security/registration/replay/AesGcmRegistrationStartReplayProtectorTest.java:32:    var substituted = new ProtectedReplayReference("registrationStartReplayReference002");
src/test/java/com/bct/ngtpa/apiservice/adapter/out/security/registration/replay/AesGcmRegistrationStartReplayProtectorTest.java:45:    var reference = new ProtectedReplayReference("registrationStartReplayReference001");
src/test/java/com/bct/ngtpa/apiservice/application/idempotency/SharedIdempotencyContractTest.java:34:    var reference = new ProtectedReplayReference("protectedReplay01");

```

### Status

This is INVESTIGATION ONLY. No test or production code changes
have been made. All 4 target classes' real dependency shapes are
now confirmed. Next step: write real, validation-passing tests
for the 4 zero-coverage classes from script 035, grounded
strictly in the real dumped source of both the targets
themselves and every dependency type they construct or accept,
per the zero-assumption discipline established in this
workstream.

## Implementation: Four Zero-Coverage Classes Now Have Dedicated Tests

- Generated: 2026-09-29T06:33:15Z
- New test files added (8 tests total):
  - `RedisForgotPasswordStartProtectedReplayKeyFactoryTest` (1 test)
  - `RedisRegistrationStartProtectedReplayKeyFactoryTest` (1 test)
  - `RedisLoginStartReplayKeyFactoryTest` (3 tests)
  - `RedisUsernameRecoveryStartProtectedReplayJsonMapperTest` (3 tests)
- All 4 previously had 0.00% instruction coverage (script 034
  baseline). Each new test file directly mirrors the pattern of
  an already-passing sibling test dumped in script 035, adapted
  only for each target's own capability/package/literal values,
  all confirmed against the real dependency shapes dumped in
  script 036 (RedisCacheKeyFactory's real varargs `.key()`
  signature, ProtectedReplayReference's and
  LoginStartReplayCommitRef's real validation regexes, and
  ProtectedUsernameRecoveryStartReplay's real compact
  constructor).
- Pre-validated via a stub classpath built from the exact real
  dumped source of all 4 targets and all 4 dependencies,
  including a genuinely-behaving Jackson stub that invokes the
  real record's canonical constructor via reflection during
  deserialization -- then ACTUALLY EXECUTED (not just compiled)
  all 8 new test methods via a reflective runner. All 8 passed.

### Status

Test code committed. A follow-up verification script is still
needed to confirm via a REAL `mvn test` run (not just compile)
that these pass against the actual repo, and to measure the
real resulting coverage for these 4 classes via a fresh jacoco
report.

## FINAL Verification: Four New Redis Auth Tests (Script 037)

- Generated: 2026-09-29T07:05:11Z
- Stage A: `mvn test -Dtest=RedisForgotPasswordStartProtectedReplayKeyFactoryTest,RedisRegistrationStartProtectedReplayKeyFactoryTest,RedisLoginStartReplayKeyFactoryTest,RedisUsernameRecoveryStartProtectedReplayJsonMapperTest` -- PASS
  (all 8 new test methods executed for the first time against the
  real repo here, confirming script 037's reflective-harness
  pre-validation held true)
- Stage B: `mvn clean test` (full suite) -- PASS

### Coverage delta (baseline 0.00% -> TRUE final, per-class)

```
=== Coverage Delta: baseline (0.00%, script 034) -> TRUE final (script 039) ===

Class                                                      Baseline Inst/Br     FINAL Inst/Br        Meets Tier-B gate?
----------------------------------------------------------------------------------------------------------------------------------
RedisLoginStartReplayKeyFactory                            0.00/0.00            100.00/100.00        YES
RedisUsernameRecoveryStartProtectedReplayJsonMapper        0.00/0.00            86.36/100.00         NO (inst_ok=False, branch_ok=True)
RedisForgotPasswordStartProtectedReplayKeyFactory          0.00/0.00            100.00/100.00        YES
RedisRegistrationStartProtectedReplayKeyFactory            0.00/0.00            100.00/100.00        YES

RESULT: Not all 4 classes meet the full Tier-B gate yet. Any remaining gap must be closed with additional tests or documented as unreachable.
```

Full final per-class coverage: see evidence file
`07-final-target-class-coverage.csv`.

### Status

All 4 classes identified in script 034's baseline as having 0.00%
instruction coverage now have dedicated tests, verified via a real
`mvn test` execution (not just compile), with the entire 4,900+
test suite confirmed still passing. This closes the smallest,
highest-confidence scope identified in the original baseline;
the remaining redis-auth sub-packages (per script 034's ranking:
`.login`, `.login.replay`, `.forgotpassword`,
`.usernamerecovery.replay`, etc.) remain open for a future round.

## Investigation: RedisUsernameRecoveryStartProtectedReplayJsonMapper's Instruction Gap

- Generated: 2026-09-29T07:15:06Z
- Trigger: script 039's real mvn test run confirmed this class at
  86.36% instruction / 100.00% branch coverage -- the only one of
  the 4 newly-tested classes not yet meeting the Tier-B gate.
- Working hypothesis (confirmed/refuted below with real per-line
  jacoco data, not assumed): toJson()'s catch block
  (IllegalStateException path) was never exercised, since none of
  the 3 existing tests supply a value causing serialization to
  genuinely fail. A catch block's body is typically a purely
  sequential instruction sequence with no internal branch point
  for JaCoCo to track, which would explain instruction loss
  without any branch-coverage loss -- exactly the pattern
  observed.

### Per-method rollup (re-confirms script 039's class-level numbers)

```
=== com.bct.ngtpa.apiservice.adapter.out.redis.auth.usernamerecovery.replay.RedisUsernameRecoveryStartProtectedReplayJsonMapper rollup (re-confirms script 039's numbers) ===
instruction: 86.36% (38/44)
branch: 100.00% (4/4)
Lines with ANY missed instruction or branch: 2
  line 14: instructions_missed=1, instructions_covered=0, branches_missed=0, branches_covered=0
  line 15: instructions_missed=5, instructions_covered=0, branches_missed=0, branches_covered=0
```

### Annotated missed lines (real source cross-referenced)

```
=== Source lines with missed instructions/branches, annotated with actual code ===
  line 14: instructions_missed=1, instructions_covered=0, branches_missed=0, branches_covered=0
    SOURCE:     } catch (Exception exception) {
  line 15: instructions_missed=5, instructions_covered=0, branches_missed=0, branches_covered=0
    SOURCE:       throw new IllegalStateException("Username Recovery START replay serialization failed");
```

Full per-line detail (every line, not just gaps): see evidence
file `06-per-line-full-detail.csv`.

### Status

This is INVESTIGATION ONLY. No test or production code changes
have been made. Once reviewed, this identifies the exact
statement(s) needing a new test (if the hypothesis is confirmed)
to close the remaining gap and bring this class to the Tier-B
gate.
