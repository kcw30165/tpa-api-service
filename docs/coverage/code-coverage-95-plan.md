# Code Coverage 95% Initiative — Plan & Playbook

> **Repository:** `api-service` (module `apim-profiles`)
> **Working branch:** `feature/increase-code-coverage-95` (cut from `dev` @ `79d094db`)
> **Goal:** Raise measured code coverage toward **95%** without destabilizing
> production authentication code.
> **Status:** In progress. Baseline measured; first implementation landed.
> **Owner:** Nick Deng (System Analyst)
> **Last updated:** 2026-09-14

---

## 1. Objective & Scope

Increase automated test coverage of the `api-service` codebase to a **95%
instruction** target (branch target under decision — see §4), prioritizing:

1. **Real business logic** (use-cases, services, validators, mappers, adapters) —
   these **must** be tested, never excluded.
2. **Cheap structural coverage** (DTO records, enums) — quick, high-yield.
3. **A defensible JaCoCo exclude set** for no-logic Spring wiring classes, to keep
   the *meaningful denominator* honest (see §5).

**Out of scope for the coverage push (handled separately):** production refactors.
Where a class is awkward to test, we **pin behavior with tests first**, then
refactor later under a green safety net (see §9).

---

## 2. Baseline (Measured)

Captured from the existing JaCoCo report (`target/site/jacoco/jacoco.csv`) in
round 01.

| Metric | Covered | Total | Coverage | Missed |
|---|---:|---:|---:|---:|
| **Instruction** | 93,816 | 122,149 | **76.80%** | 28,333 |
| **Branch** | 6,879 | 11,325 | **60.74%** | 4,446 |

- Single Maven module. JaCoCo `0.8.12` already wired (`prepare-agent` + `report`
  bound to the `test` phase). **No coverage `check` rule is enforced yet.**
- Production Java files: **1,622**; test files: **1,009**.
- **Gap to 95% instruction (before any exclude decision):** ≈ **22,225** more
  covered instructions.

---

## 3. Guiding Principles

1. **Investigation-first.** Every target is read-only-audited (source + collaborator
   shapes) before a line of test is written, so tests compile first time.
2. **Test-to-pin, then refactor.** For hard-to-test classes we write
   *characterization* tests against the current shape, then refactor separately.
   Refactoring **before** having tests on security-critical auth code is the higher
   risk and is explicitly avoided.
3. **ROI ordering.** Attack packages/classes by *missed instructions* descending,
   with cheap structural wins interleaved.
4. **Fast gates only.** Implementation scripts validate with `mvn -DskipTests
   compile` + `test-compile` and a **focused** `-Dtest=<Class>` run. The **full**
   `mvn test` suite is run **manually** (it is slow) at stabilization checkpoints.
5. **Formatter deferred.** `spotless:apply` is deferred to the stabilization stage;
   new tests are authored google-java-format-compliant to minimize drift.
6. **One commit per landed test class**, with evidence packaged per round.

---

## 4. Decision Register (open / resolved)

| # | Decision | Options | Status | Notes |
|---|---|---|---|---|
| D1 | JaCoCo exclude set for structural classes | (a) exclude Spring `*Config`/`*Properties`, `package-info`; keep DTO records (many carry real validation logic) | **RESOLVED** | Ratified and applied to `pom.xml`. See §9d. |
| D2 | Target metric | (a) 95% instruction only; (b) instruction **and** branch | **RESOLVED** | Interim target ratified: **~90% instruction** (branch tracked, not gated). See §9e. |
| D3 | When to enforce the JaCoCo `check` threshold | raise to 95% now vs. after tests land | **Recommended: after** | Enforcing now makes the build red until tests catch up. |
| D4 | First wave target | `ForgotPasswordPasswordValidator` | **RESOLVED** | Landed — 100% instr / 97.92% branch. |

> D1/D2 do **not** block WAVE-C logic targets (must-test either way). They gate the
> *aggregate re-measure* and the enforced-threshold flip (§7 WAVE E).

---

## 5. JaCoCo Exclude Candidates (proposal for D1)

Classification of low-coverage classes by name heuristic (round 03). Structural,
no-branching classes are candidates to exclude so the denominator reflects real
logic. **Everything with logic is tested, not excluded.**

| Category | Example pattern | Rationale | Approx. missed instr |
|---|---|---|---:|
| Spring config | `*ApplicationConfig`, `*WebConfig`, `*AdapterConfig` | bean wiring, no logic | large share of 3,778 |
| Spring properties | `*Properties` (`@ConfigurationProperties`) | binding holders | included above |
| Pure DTO / response records | `*Request`, `*Response`, `*Snapshot` records | data carriers | included above |
| `package-info` / generated | — | non-executable / generated | — |

**Total exclude-candidate classes:** ~268, holding ~3,778 missed instructions.

**Recommendation:** Approve excluding **config + properties + `package-info`**;
keep DTO records *in scope* where they carry compact validation logic (several
have non-trivial compact constructors), otherwise exclude the pure ones. Final
list to be ratified before WAVE E re-measure.

---

## 6. Coverage Gap — Top ROI Targets

### 6.1 Top packages by missed instructions (round 02)

| Missed | Coverage | Package |
|---:|---:|---|
| 1,812 | 23.0% | `…usecase.auth.forgotpassword.otp.resend` |
| 1,274 | 21.8% | `…usecase.auth.forgotpassword.complete` |
| 763 | 27.7% | `…adapter.out.registration` |
| 733 | 79.1% | `…usecase.auth.usernamerecovery` |
| 706 | 58.4% | `…usecase.auth.registration.complete.idempotency` |
| 684 | 80.3% | `…adapter.out.redis.auth` |
| 645 | 0.0% | `…config.auth.forgotpassword` |
| 631 | 31.2% | `…usecase.auth.forgotpassword.otp.verify` |
| 591 | 11.1% | `…adapter.out.redis.auth.login.otp.verify` |

### 6.2 Top must-test 0% logic classes (rounds 02–03)

| Missed | Class |
|---:|---|
| 1,361 | `ForgotPasswordOtpResendProtectedOperation` |
| 617 | `ForgotPasswordOtpVerifyProtectedOperation` |
| 614 | `ForgotPasswordCompleteProtectedOperation` |
| 591 | `RedisLoginOtpVerifyPersistenceAdapter` |
| 395 | `ForgotPasswordOtpSessionBindingAdapter` |
| 390 | `VerifyForgotPasswordDetailsService` |
| 370 | `RedisAuthenticatedLoginHandoffAdapter` |
| 368 | `VerifyForgotPasswordIdentityService` |
| 319 | `IdempotentCompleteForgotPasswordService` |
| 315 | `ForgotPasswordOtpReplayResolver` |
| 203 | `ForgotPasswordPasswordValidator` ✅ **done** |

---

## 7. Execution Waves

> Waves are ROI-ordered. Cheap structural waves (A) can be interleaved to bank
> quick percentage while logic waves (C/D) do the heavy lifting.

### WAVE A — Structural quick wins
- **Targets:** DTO/response records not excluded, enums, simple value objects.
- **Approach:** compact constructor + accessor/`equals`/`toString` tests, or exclude
  per D1.
- **Yield:** fast, high instruction %, low branch value.

### WAVE B — Mappers / Assemblers / Normalizers
- **Targets:** `*Mapper`, `*Assembler`, `*Normalizer`.
- **Approach:** deterministic, table-driven unit tests.
- **Yield:** high missed-instruction density, moderate branch value.

### WAVE C — Use-cases / Services / Validators / Resolvers  *(in progress)*
- **Targets:** the 0% logic classes in §6.2, ForgotPassword-first.
- **Approach:** Mockito for ports; `reactor.test.StepVerifier` for `Mono`/`Flux`;
  fixed `Clock` for determinism; cover **every branch** (error paths) to lift the
  60.74% branch number.
- **Landed / next:**
  1. ✅ `ForgotPasswordPasswordValidator` — 100% instr / 97.92% branch.
  2. ▶ `ForgotPasswordCompleteProtectedOperation` (614) — reactive, prep complete.
  3. `VerifyForgotPasswordDetailsService` (390) / `VerifyForgotPasswordIdentityService` (368).
  4. `ForgotPasswordOtp{Resend,Verify}ProtectedOperation` (1,361 / 617).
  5. Idempotency `*ReplayCodec` / `Idempotent*Service` classes.

### WAVE D — Controllers / Adapters / Transport
- **Targets:** the many 0% web-layer + Redis/APIM adapter classes.
- **Approach:** `@WebMvcTest`/slice tests; `WebClient`/executor stubs; StepVerifier.
- **Yield:** clears large 0% web/adapter surface.

### WAVE E — Stabilize & enforce
- Run **full** `mvn test` + fresh JaCoCo report → **re-measure aggregate**.
- Ratify the D1 exclude list; apply to `pom.xml`.
- Once green at/above target, **raise the JaCoCo `check` threshold to 95%** (D3)
  so regressions are enforced going forward.
- `spotless:apply`, final compile/test-compile, MR evidence pack.

---

## 8. Testing Conventions

- **Frameworks:** JUnit 5, Mockito, AssertJ, Reactor Test (`StepVerifier`);
  ArchUnit present for architecture tests.
- **Reactive:** wrap `Mono`/`Flux` under `StepVerifier`; use `assertNext(...)` /
  `expectErrorSatisfies(...)` / `verifyComplete()`.
- **Determinism:** inject `Clock.fixed(...)`; never `Instant.now()` in assertions.
- **Package-private targets:** place the test in the **same package** (e.g.
  `ForgotPasswordCompleteErrorKeys` is package-private).
- **Naming:** `<ClassUnderTest>Test`; one behavior per `@Test`; branch-complete.
- **Secrets:** never assert on redacted `toString()`; assert on typed accessors.

---

## 9. Refactor Candidates (recorded, NOT done during coverage push)

Per the agreed order (pin first, refactor later under green):

| Class | Observation | Suggested refactor | Risk if done now |
|---|---|---|---|
| `ForgotPasswordCompleteProtectedOperation` | Instantiates `new RevisionAwareAuthFlowSessionWriter(sessions)` inside private methods (hidden collaborator) | Inject the writer (or a factory) via constructor | Behavior change on security-critical completion path with no net yet |
| " | Large number of private result-builder methods (`success()`, `passwordInvalid()`, …) | Extract a `CompleteForgotPasswordResults` factory | Cosmetic; defer |
| ForgotPassword OTP operations | Many ctor params (10+) | Consider a parameter/config object | Broad blast radius; defer |

> These become **low-risk, single-purpose MRs** once WAVE-C pinning tests are green.

---

## 10. Per-Round Workflow & Evidence Protocol

Each round is a single Bash script run in WSL with the validated toolchain
(Maven 3.9.15 @ `/home/nd2887/tools/apache-maven`, Adoptium JDK 21.0.12.1 @
`/home/nd2887/tools/jdk-21.0.12.1+1`).

**Every script emits a timestamped `target/evidence/<ts>-<name>/` with:**
pre-state · formatting decision · writer/audit output · compile · test-compile ·
focused audit · diff-check · command exit codes · final status · diff-stat ·
name-status · updated handover — then packages the directory to
`<name>.zip.txt` under `/mnt/d/Projects/ORSO/llm-scripts/api-service`.

- Status is emitted **only** by the script as `STATUS: PASS` / `STATUS: FAIL`.
- Full Maven output is **not** duplicated into evidence; failure diagnostics are
  preserved only on failure.
- **Naming:** `NN-review-<brief>.sh` (read-only) / `NN-implement-<brief>.sh` (writes code).
- **Review shorthand:** upload the `.zip.txt` and type `r` → next script only.
- **Escalation:** stop & explain after **3** consecutive failures, or if **10**
  scripts pass without a commit.

---

## 11. Progress Log

| Round | Type | Summary | Result |
|---|---|---|---|
| 01 | review | Branch from `dev`; measured baseline; JaCoCo audit | PASS — 76.80% / 60.74% |
| 02 | review | Gap analysis; per-package/class ROI; wave plan | PASS |
| 03 | review | Exclude-candidate resolution; target-source skeletons | PASS |
| 04 | review | Extract validator + collaborators (first target) | PASS |
| 05 | review | Extract validator collaborator **types** | PASS |
| 06 | **implement** | `ForgotPasswordPasswordValidatorTest` (16 tests) | **PASS — target 100% instr / 97.92% branch** (`a16a719`) |
| 07 | review | Extract `…CompleteProtectedOperation` source + ports | PASS |
| 08 | review | Extract COMPLETE operation IO/domain type shapes | PASS |
| 09 | implement (next) | `ForgotPasswordCompleteProtectedOperationTest` (StepVerifier) | pending |

---

## 12. Risks & Mitigations

| Risk | Mitigation |
|---|---|
| Refactoring auth code without a net | Pin behavior with tests first (§3, §9) |
| Full `mvn test` too slow per round | Focused `-Dtest=` runs; full suite at WAVE-E checkpoints |
| Exclude list hides real logic | Only exclude no-branch structural classes; ratify list (§5) |
| Branch target underestimated | Track branch % separately; decide D2 before WAVE E |
| Enforced threshold turns build red prematurely | Flip `check` to 95% only after green (D3) |

---

## 13. Definition of Done

- [ ] Aggregate **instruction ≥ 95%** on a full `mvn test` report (and branch target per D2).
- [ ] D1 exclude list ratified and applied in `pom.xml`.
- [ ] JaCoCo `check` rule enforcing the target (D3) is green in CI.
- [ ] `spotless:apply` clean; compile + test-compile + full test suite green.
- [ ] Recorded refactor candidates (§9) triaged into follow-up MRs.
- [ ] MR raised from `feature/increase-code-coverage-95` → `dev` with evidence pack.

## 9b. Resolved — Root Cause Confirmed (Test Bug, NOT a Production Defect)

**Class:** `ForgotPasswordOtpResendProtectedOperation`
**Test:** `eligibleWithDeliveryAcceptedYieldsResent` (RE-ENABLED, was `@Disabled`)

**Real exception captured** (via a local, non-committed diagnostic — a
temporary `.doOnError(Throwable::printStackTrace)` added before the
`onErrorReturn(SYSTEM_FAILED)` in `locked(...)`, per the user's own
investigation):

```
java.lang.IllegalArgumentException: protected replay reference is invalid
    at ProtectedReplayReference.<init>(ProtectedReplayReference.java:13)
    at ForgotPasswordOtpResendProtectedOperation.lambda$accepted$14(...:394)
```

**Root cause:** `ProtectedReplayReference`'s compact constructor validates
`value.matches("[A-Za-z0-9_-]{16,256}")`. The test's `refs.generate()` stub
returned `"pending-ref-1"` — only 13 characters, 3 short of the 16-character
minimum. This is a **test-authoring bug**, not a production defect:
`SecureForgotPasswordOtpCommitRefGenerator` would never emit a value that
short in real use, so the constructor's validation was working exactly as
designed and correctly rejected an unrealistic test fixture.

**Fix:** the stub value was corrected to a valid-length string
(`"pending-ref-1234567890"`, 24 chars). The test was re-enabled (removed
`@Disabled`) and now passes as part of the full 21/21 green suite.

**Investigation trail (for reference):** 4 rounds of static analysis (fixing
2 real test bugs along the way — missing session attribute population for
`decrypt()`, and a missing `protector.protect(...)` stub), a full compiled
reproduction of the domain types (zero exceptions — the bug was downstream of
where the repro stopped), and a defensive-mocking attempt, before the user's
local diagnostic surfaced the real, previously-swallowed exception. This
traces back to the operation's `.onErrorReturn(SYSTEM_FAILED)` silently
absorbing the real cause — a pattern worth keeping in mind for future
debugging of this class.

## 9c. Dead-Code Finding — `ForgotPasswordOtpVerifyProtectedOperation`

**Location:** the `blank(resetSubjectId)` sub-condition inside the combined
guard `context.phase() != OTP_SENT || blank(loaded.activeOtpChallengeId()) ||
blank(resetSubjectId)`.

**Finding:** this sub-condition is unreachable via any validly-constructed
`ForgotPasswordAuthFlowContext`. `identityValue` is a required, always-non-
blank field (enforced by the record's compact constructor via `requireText`),
and `effectiveResetSubjectId(...)` falls back to `identityValue` whenever
`resetSubjectId` is blank — so the effective value can never actually be
blank. A dedicated test for this branch was attempted (round 25) and failed
for exactly this reason (fell through to an unstubbed `otp.verifyChallenge`
call instead, surfacing as `SYSTEM_FAILED`); the test was removed rather than
forcing an impossible precondition. This is the same category of finding as
the unreachable `result == null` branch documented for the COMPLETE operation
in round 11. No action needed — this is a defensive-programming artifact, not
a missed test.

## 9d. D1 Ratification — JaCoCo Excludes Applied

**Decision:** D1 is RESOLVED. Applied to `pom.xml` (jacoco-maven-plugin
configuration), NOT to production application code.

**Excluded (structural, no branching logic, verified by class-name pattern):**
- `**/*Config.class` / `**/*Config$*.class` — Spring `@Configuration` /
  `@Bean`-wiring classes.
- `**/*Properties.class` / `**/*Properties$*.class` — `@ConfigurationProperties`
  binding holders.
- `**/package-info.class` — non-executable.

**Deliberately NOT excluded:** pure DTO/response/request records. Several
carry real compact-constructor validation (e.g. `ForgotPasswordOtpCommitState`,
`LoginIdempotencyKey`, `AuthenticatedLoginHandoffReference`, all confirmed
during WAVE C) — excluding them would hide real logic from the coverage
signal, contradicting the goal of an honest 95% target.

**Effect (measured, full `mvn test` re-run):**

| Metric | Before D1 (round 34) | After D1 (this round) |
|---|---:|---:|
| Instruction | 79.97% | **81.42%** |
| Branch | 63.31% | **64.16%** |

The single largest 0% class, `ForgotPasswordApplicationConfig` (599 missed
instructions), is now excluded from the denominator rather than requiring
tests, along with its `*Config`/`*Properties` siblings (~9,333 structural
instructions total).

## 9e. D2 Ratification — 90% Instruction Interim Target

**Decision:** D2 is RESOLVED. The near-term practical target for this
initiative is **~90% instruction coverage**, tracked against the D1-adjusted
denominator (excludes `*Config`/`*Properties`/`package-info`). **Branch
coverage continues to be measured and reported per class**, but is not gated
against a fixed aggregate threshold at this stage. The original 95%
instruction aspiration (plan §1) remains the longer-term goal but is no
longer the immediate milestone driving each round's target selection.

**Rationale (measured, round 61 full-suite re-measure):**

| Target | Instructions still needed |
|---|---:|
| 95% instruction | 12,898 |
| **90% instruction (ratified interim target)** | **7,130** |

The prior 8 targets (rounds 36-60, post-D1) delivered roughly 2,982 newly
covered instructions across ~25 rounds — a materially slower rate than the
first 6 WAVE-C targets, because remaining gaps are now spread thin across
many mid-size packages (usernamerecovery, redis adapters, idempotency replay
codecs, CSRF, registration) rather than concentrated in a few large 300+
instruction classes. 90% is judged achievable in a bounded number of further
rounds; 95% is not ruled out but is deferred as a stretch goal for a later,
separately-scoped effort (or WAVE D/E work) once 90% is reached and stable.

**Effect on WAVE C target selection going forward:** continue picking the
highest-ROI remaining 0%/low-coverage classes (per the round-61 ranked list),
prioritizing classes that make concrete progress toward the 7,130-instruction
gap, without a strict requirement to exhaustively cover every remaining
class to reach the (now deferred) 95% figure.

**Checkpoint history for reference:**

| Round | Instruction | Branch |
|---|---:|---:|
| 01 (baseline) | 76.80% | 60.74% |
| 34 (6 targets, pre-D1) | 79.97% | 63.31% |
| 35 (D1 ratified) | 81.42% | 64.16% |
| 61 (14 targets, current) | **83.82%** | **65.95%** |


## 2026-09-15 - Discovered Production Defect (NOT fixed, coverage-only phase)

**Class:** `RegistrationIdempotencyResponseSnapshot`
**Method:** `copyAndValidateHeaders(Map<String, String> headers)`
**File:** `src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/idempotency/RegistrationIdempotencyResponseSnapshot.java`

**Defect:** The method's own `unsafeHeaderValue(String value)` helper
explicitly treats a `null` header value as *safe* (`if (value == null) return
false;`), implying null values are an intentionally-supported case. However,
the method unconditionally finishes with `return Map.copyOf(copy);`, and
`java.util.Map.copyOf` throws `NullPointerException` for any entry with a
null value. The null-permitting design intent and the immutable-copy
implementation directly contradict each other, so constructing a
`RegistrationIdempotencyResponseSnapshot` with any header that has a null
value currently throws an uncaught `NullPointerException` at construction
time, not the graceful/safe handling the code appears to intend.

**Confirmed via:** a real compiled JUnit test
(`RegistrationIdempotencyResponseSnapshotTest.nullHeaderValueIsSafe`,
authored round 83, root-caused round 86) reproducing the exact
`Map.copyOf(copy)` -> `NullPointerException` stack trace during the coverage-
increase initiative. The test has been REMOVED (not fixed) per explicit user
decision, since this phase of work is test-only and does not modify
production code.

**Suggested fix (for a future, separate change):** replace the final
`return Map.copyOf(copy);` with `return Collections.unmodifiableMap(copy);`
(or equivalent), which preserves immutability without rejecting null values,
restoring consistency with `unsafeHeaderValue`'s stated null-tolerant intent.

**Status:** OPEN. Tracked here for the separate refactor/bugfix backlog
referenced in this plan's Phase 2 (post-coverage) work.
