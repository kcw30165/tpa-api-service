# Captcha Verification End-to-End Refactor Implementation Plan

## 1. Purpose

This plan addresses the BFF defect where `POST /api/v1/auth/captcha/verify` returns HTTP 200 with a successful verification result when the stored captcha status is already `VERIFIED`, regardless of the newly submitted answer.

The implementation must preserve the agreed two-stage lifecycle:

```text
Captcha verification: CREATED -> VERIFIED
Protected-flow consumption: VERIFIED -> CONSUMED
```

The immediate goal is to make captcha verification valid only when the stored status is `CREATED`. The broader goal is to give the captcha capability clear end-to-end ownership instead of relying on registration-specific HTTP response behavior.

No implementation should begin until the public HTTP-status decision in Section 5 is approved.

## 2. Confirmed Current-State Findings

The investigation established the following current behavior and structure:

1. `AuthCaptchaController` owns the captcha challenge, verify, and refresh endpoints.
2. The controller delegates application work to captcha use cases and maps results through `CaptchaWebMapper`.
3. Final HTTP translation is delegated to `RegistrationHttpResponses`.
4. An already `VERIFIED` captcha is currently treated as an idempotent successful verification.
5. Because the existing `VERIFIED` branch returns a success outcome, the later submitted answer is irrelevant and the API returns HTTP 200.
6. Registration and other protected workflows use a separate consumption operation to transition `VERIFIED -> CONSUMED`.
7. The captcha verification flow and captcha consumption flow must remain separate.

## 3. Target Behavior

### 3.1 Verification eligibility

The verify operation may evaluate the submitted answer only when:

```text
stored status == CREATED
```

The service must validate the lifecycle state before hashing or comparing the submitted answer.

### 3.2 State behavior

```text
CREATED + correct answer
    -> return success
    -> atomically transition to VERIFIED

CREATED + incorrect answer, attempts remain
    -> return approved verification failure
    -> increment failed attempts once
    -> remain CREATED

CREATED + incorrect answer, attempt limit reached
    -> return approved locked failure
    -> transition to LOCKED

VERIFIED + any answer
    -> reject without evaluating answer
    -> remain VERIFIED
    -> do not increment attempts
    -> do not extend TTL
    -> do not consume

CONSUMED / REFRESHED / EXPIRED / LOCKED + any answer
    -> reject without evaluating answer
    -> do not revive or regress state
```

### 3.3 Consumption behavior

Protected workflows such as registration must continue to use a separate atomic operation:

```text
VERIFIED -> CONSUMED
```

The captcha verify endpoint must never perform this transition. A failed repeated verification must leave a `VERIFIED` captcha available for one legitimate later consumption.

## 4. Scope

### 4.1 In scope

- `AuthCaptchaController`
- Captcha HTTP response translation
- `CaptchaWebMapper`
- `VerifyCaptchaUseCase` and its implementation
- `CaptchaVerificationOutcome` or equivalent application result model
- Captcha lifecycle validation
- Redis captcha verification operation and atomicity
- Captcha error definitions and frontend targets
- Captcha controller, mapper, use-case, Redis adapter, architecture, and integration tests
- Registration consumption regression tests
- Username Recovery consumption regression tests if they share the same captcha capability
- Documentation and contract traceability affected by the behavior change

### 4.2 Out of scope

- Changing the agreed `VERIFIED -> CONSUMED` protected-flow model
- Folding captcha consumption into captcha verification
- Redesigning registration, Username Recovery, login, OTP, or session workflows beyond compatibility changes
- Changing captcha generation algorithms or image rendering
- Changing answer hashing unless the investigation reveals a correctness defect
- Introducing general idempotency to captcha verification unless separately approved
- Broadly replacing every feature-specific HTTP response helper in the BFF

## 5. Contract Decision Gate

Before implementation, approve the exact public contract for an already `VERIFIED` captcha.

### Proposed contract

```text
HTTP status: 422 Unprocessable Content
API status: VALIDATION_FAILED
success: false
result: absent/null, or the established failure projection
errors: one blocking captcha verification error
state change: none
attempt change: none
TTL change: none
```

This pairing must be intentional because the existing generic mapping appears to associate `VALIDATION_FAILED` primarily with HTTP 400 and `BUSINESS_REJECTED` primarily with HTTP 422.

The approved contract must specify:

1. HTTP status, proposed as `422`.
2. API status, proposed as `VALIDATION_FAILED`.
3. Public error code.
4. Error target, likely `captchaAnswer`, `captchaChallengeId`, or a page-level target.
5. Whether all non-`CREATED` verification states use one indistinguishable public error.
6. Whether the failure result is `null` or contains `verified=false`.
7. Whether `remainingAttempts` is omitted from terminal or ineligible-state failures.

### Recommended privacy rule

Use one public error for ineligible verification states so callers cannot distinguish whether a challenge is already verified, consumed, refreshed, expired, locked, or unknown. Internal logs and audit events may retain the precise reason without exposing it to the frontend.

## 6. Target Architecture

### 6.1 HTTP boundary

Replace the registration-owned response dependency:

```text
AuthCaptchaController
    -> CaptchaWebMapper
    -> RegistrationHttpResponses
```

with captcha-owned HTTP translation:

```text
AuthCaptchaController
    -> CaptchaWebMapper
    -> CaptchaHttpResponses
```

`CaptchaHttpResponses` should own only captcha transport concerns:

- challenge response mapping;
- verify response mapping;
- refresh response mapping;
- approved HTTP status classification;
- captcha-specific rate-limit headers if applicable;
- no registration cookies or registration workflow behavior.

If a generic existing helper already provides feature-neutral mechanics, `CaptchaHttpResponses` may delegate to it. It must not depend on `RegistrationHttpResponses`.

### 6.2 Application boundary

The verification use case must return explicit outcomes that distinguish:

- successful first verification;
- incorrect answer;
- locked challenge;
- ineligible lifecycle state;
- not found or invalid challenge;
- infrastructure failure.

An already `VERIFIED` challenge must not be represented by the same success outcome used for a successful `CREATED -> VERIFIED` transition.

### 6.3 Domain and persistence boundary

Lifecycle transition rules should be centralized and explicit. The Redis operation must prevent concurrent requests from violating these rules.

The implementation should prefer an atomic state transition that verifies the expected current status:

```text
expected status: CREATED
success status: VERIFIED
```

Incorrect-answer attempt updates must also avoid lost updates under concurrency. A plain application-level read, compare, and write is insufficient unless the repository already guarantees optimistic locking or an equivalent atomic condition.

## 7. Implementation Sequence

### Phase 1: Freeze the contract and add fail-first tests

1. Record the approved `422 + VALIDATION_FAILED` contract and exact error descriptor.
2. Add a use-case test proving that `VERIFIED` is rejected for both correct and incorrect submitted answers.
3. Add a controller contract test proving the exact HTTP status and response envelope.
4. Add assertions that status, failed-attempt count, and TTL do not change.
5. Add an architecture test proving that `AuthCaptchaController` no longer depends on `RegistrationHttpResponses`. This test should initially fail until the HTTP-boundary refactor is applied.
6. Preserve fail-first evidence before production changes.

### Phase 2: Correct application lifecycle semantics

1. Change verification eligibility to require `CaptchaStatus.CREATED`.
2. Introduce or reuse a typed failure outcome for an ineligible captcha state.
3. Ensure lifecycle validation happens before answer hashing or comparison.
4. Remove the branch that maps stored `VERIFIED` to verification success.
5. Ensure a rejected `VERIFIED` request leaves the stored record unchanged.
6. Ensure the verify service never invokes the consume operation.

### Phase 3: Make verification state changes atomic

1. Inspect the active Redis operation used by verification.
2. If it is read-modify-write, replace it with an atomic conditional transition or equivalent concurrency-safe mechanism.
3. Atomically enforce `CREATED -> VERIFIED` for a correct answer.
4. Atomically enforce one failed-attempt increment for an incorrect answer.
5. Atomically enforce `CREATED -> LOCKED` at the configured attempt boundary.
6. Preserve the existing TTL unless the approved policy explicitly refreshes it.
7. Translate Redis/script failures into the existing typed infrastructure-failure contract.

### Phase 4: Introduce captcha-owned HTTP translation

1. Add `CaptchaHttpResponses` in the captcha web boundary.
2. Move or delegate only the response mechanics required by captcha endpoints.
3. Implement the approved captcha-specific `422 + VALIDATION_FAILED` mapping.
4. Update `AuthCaptchaController` to use `CaptchaHttpResponses`.
5. Remove the controller import and dependency on `RegistrationHttpResponses`.
6. Keep `RegistrationHttpResponses` unchanged for registration controllers unless a small compatibility extraction is necessary.

### Phase 5: Decouple public captcha errors from registration

1. Add capability-owned captcha error keys if approved.
2. Add localized messages and contract metadata for each new key.
3. Use stable frontend targets.
4. Update `CaptchaWebMapper` to emit captcha-owned errors.
5. Preserve safe public ambiguity for non-`CREATED` states.
6. Retain precise internal diagnostics through structured events rather than public error details.
7. Do not delete registration captcha-consumption errors that are still used by registration.

### Phase 6: Regression protection for consumers

1. Prove registration still accepts and atomically consumes a `VERIFIED` captcha once.
2. Prove registration rejects a second consumption attempt.
3. Prove Username Recovery retains the same one-time consumption behavior if it uses the shared capability.
4. Prove a rejected second verify leaves the captcha available for one later valid consume.
5. Prove protected consumers receive only the challenge ID and never the captcha answer.
6. Prove no APIM, OTP, session, or downstream side effect occurs after consumption rejection.

### Phase 7: Documentation and migration cleanup

1. Update captcha lifecycle documentation.
2. Update the captcha API contract with exact response examples.
3. Update error matrices and traceability entries.
4. Remove documentation that describes repeated `VERIFIED` verification as successful replay.
5. Clearly distinguish transport idempotency replay from a new verify request against an already verified challenge.
6. Document that consumption remains a separate protected-flow operation.

## 8. Required Test Matrix

### 8.1 Use-case tests

- `CREATED` and correct answer returns success and stores `VERIFIED`.
- `CREATED` and incorrect answer increments attempts once and remains `CREATED` when attempts remain.
- Incorrect answer at the attempt boundary stores `LOCKED`.
- `VERIFIED` and correct answer returns the approved failure.
- `VERIFIED` and incorrect answer returns exactly the same public failure.
- `VERIFIED` does not invoke the answer matcher.
- `VERIFIED` remains `VERIFIED`.
- `VERIFIED` does not increment attempts.
- `VERIFIED` does not extend TTL.
- `CONSUMED`, `REFRESHED`, `EXPIRED`, and `LOCKED` are rejected.
- Infrastructure failure maps to the approved system error and never to success.

### 8.2 Controller and mapper tests

- Exact HTTP 422 for an already `VERIFIED` captcha.
- Exact `VALIDATION_FAILED` API status.
- `success=false`.
- Exact approved error code, type, target, severity, source, and navigation.
- No raw answer, answer hash, internal status, Redis details, or exception text in the response.
- Correct `Accept-Language` message resolution.
- `X-Request-Id` behavior remains unchanged.
- Challenge and refresh endpoints retain their existing successful contracts.

### 8.3 Redis adapter tests

- Conditional `CREATED -> VERIFIED` succeeds once.
- Concurrent correct verification requests cannot produce inconsistent state.
- Concurrent wrong answers cannot lose attempt increments.
- Correct and incorrect concurrent requests produce an approved deterministic state.
- A `VERIFIED` record is not rewritten.
- TTL is preserved on rejection.
- Expired records cannot be revived.
- Serialization, timeout, and script failures are not swallowed.

### 8.4 Architecture tests

- `AuthCaptchaController` does not import or reference `RegistrationHttpResponses`.
- Captcha web classes do not depend on outbound Redis adapters.
- Captcha response mapping does not depend on registration cookies or registration workflow classes.
- Registration may depend only on the approved captcha consumption port, not captcha web components.

### 8.5 Consumer regression tests

- Registration consumes `VERIFIED -> CONSUMED` once.
- A second registration consume is rejected.
- A rejected repeated verify does not prevent the first legitimate registration consume.
- Username Recovery behavior remains correct if it shares the captcha port.
- No protected flow re-checks the captcha answer.

## 9. Validation Strategy

Validation should be executed in this order, with evidence captured for every stage:

1. Pre-change repository state.
2. Fail-first focused tests.
3. Application lifecycle tests.
4. Controller and mapper contract tests.
5. Redis adapter and concurrency tests.
6. Registration captcha-gate regression tests.
7. Username Recovery captcha-gate regression tests where applicable.
8. Architecture guard tests.
9. Cumulative captcha and registration tests.
10. Full test suite.
11. Compile/package validation as required by the repository.
12. Static audit for forbidden dependencies and registration-prefixed captcha errors.
13. Git diff, diff-stat, and name-status checks.
14. Final status and command exit-code summary.

Use the project-standard Maven environment:

```bash
export JAVA_HOME="C:/Java/OpenJDK/jdk-21" && export M2_HOME="/d/Tools/apache-maven-3.9.15" && export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH" && mvn -Dtest=... test
```

The exact focused test list must be derived from the current repository before preparing the implementation script.

## 10. Preservation Checks

The implementation must prove that it preserves:

- first-time successful captcha verification;
- failed-attempt counting and lock behavior;
- challenge refresh behavior;
- expiration and TTL behavior;
- one-time protected-flow consumption;
- registration and Username Recovery compatibility;
- answer-hash-only persistence;
- absence of captcha answers in logs, responses, sessions, and audit data;
- request-ID and language handling;
- public response envelope structure;
- Redis exception translation;
- controller isolation from outbound adapters.

## 11. Risks and Mitigations

### Risk: Contract inconsistency between HTTP 422 and `VALIDATION_FAILED`

Mitigation: approve and codify the exception in captcha-owned HTTP mapping and exact contract tests before implementation.

### Risk: Frontend currently treats repeated verification as success

Mitigation: review frontend behavior and update it to request a new challenge after the approved failure. Do not rely solely on HTTP 200 to infer verification.

### Risk: Existing documentation describes `VERIFIED` replay as idempotent

Mitigation: distinguish true idempotency replay from a new request against completed state, then update all conflicting documents and tests.

### Risk: Redis verification is not atomic

Mitigation: add concurrency tests first and implement a conditional atomic transition if current behavior is read-modify-write.

### Risk: New captcha error keys break message-contract validation

Mitigation: add the complete error definition, localization, targets, severity, source, navigation, and exhaustive mapping tests in the same change.

### Risk: Refactor unintentionally changes registration response behavior

Mitigation: introduce `CaptchaHttpResponses` without modifying registration mappings unless a compatibility-preserving shared extraction is necessary. Run cumulative registration regressions.

## 12. Deliverables

The implementation round should produce:

1. Fail-first tests and evidence.
2. Corrected verification lifecycle semantics.
3. Atomic verification-state handling where required.
4. `CaptchaHttpResponses` and a decoupled `AuthCaptchaController`.
5. Captcha-owned public error definitions if approved.
6. Updated controller, mapper, use-case, adapter, integration, concurrency, and architecture tests.
7. Updated lifecycle, API-contract, error-matrix, and traceability documentation.
8. A timestamped evidence directory containing all validation stages.
9. An updated `handover.md` recording approved decisions, implementation details, test results, and remaining issues.

## 13. Definition of Done

The work is complete only when all of the following are true:

- Only a `CREATED` captcha can be answer-verified.
- A `VERIFIED` captcha returns the approved failure for every submitted answer.
- The response is exactly HTTP 422 with `VALIDATION_FAILED`, if that contract is approved.
- Rejected verification does not compare the answer, mutate status, increment attempts, extend TTL, or consume the captcha.
- A later protected flow can still consume that `VERIFIED` captcha exactly once.
- `AuthCaptchaController` no longer depends on `RegistrationHttpResponses`.
- Captcha HTTP behavior is owned by a captcha-specific boundary.
- Concurrency tests prove safe transitions and attempt accounting.
- Registration and Username Recovery regressions pass.
- Full validation passes with no unexplained failures.
- Documentation and test expectations no longer describe state-based `VERIFIED` replay as successful verification.

## 14. Proposed Commit Sequence

Use small, reviewable commits:

```text
test(captcha): define fail-first verified replay contract
fix(captcha): reject verification outside created state
fix(captcha): enforce atomic verification transitions
refactor(captcha): introduce captcha HTTP response mapping
refactor(captcha): decouple public errors from registration
 test(captcha): add consumer and concurrency regressions
 docs(captcha): align lifecycle and verification contract
```

The leading space in the sixth example should be removed when used as an actual commit subject:

```text
test(captcha): add consumer and concurrency regressions
```

## 15. Next Step

Approve the Section 5 contract details, especially the deliberate pairing of HTTP 422 with `VALIDATION_FAILED`, the public error code, and its frontend target. After that approval, prepare a versioned fail-first implementation script based on the exact current repository state. Do not apply production changes before the fail-first evidence is captured.
