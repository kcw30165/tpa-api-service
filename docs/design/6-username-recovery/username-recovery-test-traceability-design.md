# Username Recovery Test and Traceability Design

## 1. Test strategy

Implementation follows fail-first TDD and the existing auth-workflow test conventions. Coverage includes web contracts, application orchestration, APIM mapping, captcha state, Redis persistence, session lifecycle, security, Scheme-Ref, idempotency, message delivery, non-production override, exception mapping, configuration, and cumulative regressions.

## 2. Requirement traceability

| Requirement ID | Requirement | Primary design coverage | Test layers |
|---|---|---|---|
| UR-R01 | No OTP | Solution design | architecture, endpoint absence |
| UR-R02 | Username sent only | Solution/API | response contract, serialization, logging |
| UR-R03 | Reuse shared `MemberIdentityType` | Solution/API | architecture, mapper, enum boundary |
| UR-R04 | FE `STAFF_NO` maps to Java/APIM `STAFFNO` | Solution/API/APIM | DTO, validator, mapper, contract |
| UR-R05 | DOB plus exactly one contact | API | validator, controller |
| UR-R06 | Captcha verified separately | Solution/API | captcha controller, Redis integration |
| UR-R07 | Start atomically consumes VERIFIED captcha | Solution/API | service, Redis concurrency |
| UR-R08 | TAS returns all records | APIM | adapter contract |
| UR-R09 | Registered results display account selection | Solution/API | web mapper, controller |
| UR-R10 | Only unregistered schemes create a normal session and return SELECT_ACCOUNT with isRegistered=false rows | Solution/API | service, mapper, session continuity, replay, boundary |
| UR-R11 | FE receives only env/trust/registered/Scheme-Ref | API | JSON contract guard |
| UR-R12 | Scheme-Ref opaque and session-bound | Session | generator, tampering, cross-session |
| UR-R13 | Sender ACCEPTED maps candidate to REQUESTED | Solution/session | service, persistence mapping |
| UR-R14 | Remaining unrequested returns SELECT_ACCOUNT | API/session | transition and response tests |
| UR-R15 | All registered requested completes workflow | Solution/session | lifecycle, atomic completion |
| UR-R16 | Final request returns RETURN_TO_LOGIN and COMPLETED | API | mapper, controller contract |
| UR-R17 | Same key replays without resend | API/session | replay integration |
| UR-R18 | Different key after REQUESTED is rejected | API | conflict test |
| UR-R19 | 10-minute TTL | Session | clock, Redis TTL |
| UR-R20 | Cancel requires CSRF and clears session/cookie | API/session | controller, security |
| UR-R21 | Generic APIM body reused | APIM | architecture guard |
| UR-R22 | `cust-id` invariants | APIM | adapter tests |
| UR-R23 | FE owns labels | API | response text absence |
| UR-R24 | Downstream rejection never public | API/APIM | normalization tests |
| UR-R25 | Strict cookie and exact path | Session | cookie mapping |
| UR-R26 | Local Angular proxy supported | Solution/sequence | configuration documentation guard |
| UR-R27 | Non-production destination override | Solution/override | config, delivery integration |
| UR-R28 | Minimal protected session | Solution/session | JSON persistence contract |
| UR-R29 | No silent exceptions | Solution/API | architecture audit, failure tests |
| UR-R30 | Mapped exceptions become SYSTEM_FAILED | Solution/API | service and mapper tests |

## 3. Proposed test classes

### Web and contract

- `AuthUsernameRecoveryStartControllerTest`
- `AuthUsernameRecoveryRequestControllerTest`
- `AuthUsernameRecoveryCancelControllerTest`
- `UsernameRecoveryHttpResponsesTest`
- `UsernameRecoveryStartWebMapperTest`
- `UsernameRecoveryRequestWebMapperTest`
- `UsernameRecoveryCancelWebMapperTest`
- `UsernameRecoveryPublicResponseContractTest`
- `UsernameRecoveryCookieMappingTest`
- `UsernameRecoveryAllUnregisteredStartBoundaryTest`

### Application

- `StartUsernameRecoveryServiceTest`
- `RequestUsernameRecoveryServiceTest`
- `CancelUsernameRecoveryServiceTest`
- `UsernameRecoveryValidatorTest`
- `UsernameRecoveryMemberIdentityTypeMapperTest`
- `UsernameRecoverySchemeReferenceAssignmentServiceTest`
- `UsernameRecoveryRequestIdempotencyTest`
- `UsernameRecoveryCompletionPolicyTest`
- `UsernameRecoverySystemFailureMappingTest`

### Captcha

- `UsernameRecoveryVerifiedCaptchaGateTest`
- `UsernameRecoveryCaptchaConsumeConcurrencyTest`
- `UsernameRecoveryStartRequestDoesNotContainAnswerTest`

### APIM

- `TrpSearchMemberApimRequestTest`
- `TrpSearchMemberApimDataItemTest`
- `TrpSearchMemberMapperTest`
- `ApimUsernameRecoveryLookupAdapterTest`
- `UsernameRecoveryApimContractInvariantTest`
- `UsernameRecoveryGenericApimBodyArchitectureTest`

### Redis and security

- `RedisUsernameRecoveryWorkflowStateJsonMapperTest`
- `RedisUsernameRecoveryIdempotencyAdapterTest`
- `UsernameRecoveryProtectedContextTest`
- `UsernameRecoverySchemeRefGeneratorTest`
- `UsernameRecoveryCsrfTest`
- `UsernameRecoverySessionTtlTest`
- `UsernameRecoveryConcurrentRequestIntegrationTest`
- `UsernameRecoveryMinimalSessionPersistenceTest`

### Message delivery

- `UsernameRecoveryMessageTemplateTest`
- `UsernameRecoveryMessageDeliveryTest`
- `UsernameRecoveryDeliveryUnknownOutcomeTest`
- `UsernameRecoveryNoResendTest`
- `UsernameRecoveryNonProductionDestinationOverrideTest`

### Architecture and logging

- `UsernameRecoveryNoSilentCatchArchitectureTest`
- `UsernameRecoverySensitiveLoggingTest`
- `UsernameRecoveryExceptionBoundaryTest`

## 4. Mandatory acceptance scenarios

### Captcha

1. `/captcha/challenge` creates `CREATED`.
2. Correct `/captcha/verify` changes `CREATED -> VERIFIED`.
3. `/start` request DTO has no captcha-answer property.
4. `/start` accepts `VERIFIED` and atomically changes it to `CONSUMED`.
5. `/start` rejects missing, `CREATED`, `CONSUMED`, `REFRESHED`, `EXPIRED`, and `LOCKED` challenges.
6. Two concurrent starts using one verified challenge permit at most one successful consume.
7. APIM is not called after captcha consume rejection.
8. Captcha remains consumed after subsequent APIM or session failure.
9. Captcha answer is absent from start hashes, session, response, audit, and logs.

### Start

1. Public `STAFF_NO` maps to shared `MemberIdentityType.STAFFNO`.
2. Internal/session identity type is `STAFFNO` where retention is required.
3. APIM receives `search-by=STAFFNO`.
4. GPN, GPFS, and ID map directly.
5. No feature-specific Username Recovery identity enum exists.
6. Invalid public identity type fails before APIM.
7. Identity validation matches Registration.
8. Invalid DOB is rejected before APIM.
9. Both contacts are rejected.
10. Neither contact is rejected.
11. Invalid email/mobile is field-targeted.
12. APIM member-not-found returns 422, `BUSINESS_REJECTED`, `RESTART_FLOW`, and `err.usernamerecovery.verification.failed`.
13. No session or cookie is created for no records.
14. Only unregistered schemes create the normal session, CSRF token, cookie and opaque Scheme-Refs and return `SELECT_ACCOUNT` with `isRegistered=false` rows.
15. One registered record returns `SELECT_ACCOUNT` and requires a click.
16. Multiple records preserve only env, trust, registration state, and Scheme-Ref publicly.
17. Public response excludes username, destination, policy, certificate, DOB, identity, and termination data.
18. Session-creation failure creates no cookie.
19. Completed start idempotent replay returns the same safe projection.

### APIM invariants

1. Registered plus nonblank username succeeds.
2. Registered plus blank username maps to `SYSTEM_FAILED`.
3. Unregistered plus blank username is structurally valid.
4. Unregistered plus username maps to `SYSTEM_FAILED`.
5. Blank/missing data maps to not found.
6. Missing env/trust/registration flag maps to `SYSTEM_FAILED`.
7. Duplicate env/trust maps to `SYSTEM_FAILED`.
8. Unknown response fields are ignored without exposure.
9. Generic `ApimResponseBody<T>` is used and no feature response-body envelope exists.
10. Every adapter exception maps to `SYSTEM_FAILED`; none is swallowed.

### Request and completion

1. Valid registered Scheme-Ref sends the stored username to the effective destination.
2. Sender result `ACCEPTED` stores candidate state `REQUESTED`.
3. FE cannot override username or destination.
4. Unregistered Scheme-Ref is rejected without delivery.
5. Missing, malformed, cross-session, expired, and fabricated Scheme-Refs are rejected.
6. Missing/invalid cookie and CSRF map correctly.
7. Same Idempotency-Key replays without duplicate send.
8. Same key with another Scheme-Ref conflicts.
9. Different key after `REQUESTED` returns already requested.
10. Two concurrent requests produce at most one sender submission and one `REQUESTED` transition.
11. Sender failure or mapped exception never marks `REQUESTED`.
12. Sender/persistence unknown outcome never causes blind duplicate send.
13. Response returns only a masked effective destination.
14. Username is absent from response and logs.
15. With remaining unrequested registered schemes, nextStep is `SELECT_ACCOUNT` and session is `PARTIALLY_REQUESTED`.
16. With no remaining unrequested registered scheme, nextStep is `RETURN_TO_LOGIN` and workflow status is `COMPLETED`.
17. Unregistered schemes do not block completion.
18. One registered scheme becomes `COMPLETED` after its first successful request.
19. New mutation against a completed session is rejected with `RETURN_TO_LOGIN` guidance.
20. Same-key terminal replay returns the original final response.

### Minimal storage

Persistence tests prove the session excludes unused:

- policy number
- certificate number
- scheme type
- termination status
- termination completion date
- DOB
- raw identity value

It contains only workflow/session security metadata, verified destination/channel, language, minimal candidate fields, `REQUESTED`/reservation status, and sender/idempotency references required for safe delivery.

### Cancel

1. Valid cancel deletes/inactivates session and clears exact-path cookie.
2. Missing session is idempotent success under the existing convention.
3. Present session with missing/invalid CSRF is forbidden.
4. Scheme-Refs fail after cancellation.
5. Clear-cookie path is `/api/v1/auth/username-recovery`.

### Non-production override

1. PROD/DR use verified destination.
2. Local, DEV, SIT, UAT, QA, blank, and unknown profiles use dedicated Username Recovery override.
3. Missing applicable override fails closed.
4. Login and Forgot Password overrides are not reused.
5. Masked response represents the effective destination.
6. Template, locale, username variable, and correlation ID remain unchanged by recipient override.

### No silent exceptions

1. Static/architecture audit rejects empty catch blocks and `catch (... ignored)` in Username Recovery packages.
2. APIM exceptions map to `SYSTEM_FAILED` and public `SYSTEM_ERROR`.
3. Redis serialization, script, timeout, and context-decryption exceptions map to `SYSTEM_FAILED`.
4. Template and sender exceptions map to `SYSTEM_FAILED`, except explicitly uncertain sender outcomes.
5. Unexpected exceptions reach the central exception handler.
6. Each mapped exception creates one sanitized failure event.
7. Responses never contain raw exception text or sensitive data.
8. Cleanup failure preserves the primary failure and records a secondary failure event.
9. Logging tests confirm username, destination, DOB, identity, captcha answer, cookie, token, and Redis key absence.

## 5. Error-matrix traceability

Every row in `username-recovery-api-contract-design.md` requires:

- one mapper or HTTP mapping test;
- one service/security test where workflow logic is involved;
- one integration test for Redis, captcha, APIM, delivery, concurrency, or override behavior where applicable.

Every APIM mapping row requires an adapter test. Test names or metadata should include stable matrix IDs, for example:

```text
UR_REQ_006_finalRequestedSchemeReturnsCompleted
UR_START_011_allUnregisteredCreatesNormalSession
APIM_UR_005_registeredRecordWithoutCustIdIsSystemFailed
```

## 6. Configuration tests

Verify:

- session TTL is positive and defaults to 600 seconds;
- Scheme-Ref entropy is at least 16 bytes;
- cookie is Strict, exact-path, HttpOnly, and Secure outside local profile;
- local profile may disable Secure only for local HTTP;
- Angular proxy documentation uses relative `/api` URLs;
- Username Recovery templates exist for supported languages/channels and include the username placeholder;
- non-production override values are validated and separate from Login/Forgot Password;
- idempotency TTLs cover session and transport uncertainty;
- APIM path is nonblank;
- public-to-shared identity mapping is complete.

## 7. Regression suite

Cumulative regressions include:

- Registration start, captcha, OTP, username, complete, and cancel
- Login start, OTP, account selection, message override, and cancel
- Forgot Password start, details, OTP, complete, message override, and cancel
- shared captcha lifecycle and Redis adapter
- shared auth-flow session and persistence compatibility
- shared HTTP response normalization
- CSRF and CORS-security configuration
- message-delivery runtime
- deployment-environment policy
- APIM generic envelope deserialization
- central exception handling and sensitive logging

## 8. Evidence expectations

Implementation evidence must capture sequentially:

1. pre-state
2. fail-first focused tests
3. focused passing tests
4. feature cumulative tests
5. auth-workflow regressions
6. full tests
7. compile
8. architecture, silent-catch, logging, and contract audits
9. diff check
10. final status and command exit codes
11. diff stat and name-status
12. preserved failure logs

Use the project-required Maven/JDK command conventions during implementation.
