# Username Recovery Handover

## Current stage

Task 005 (`UsernameRecoveryErrorKey`) is implemented and reviewed
(uncommitted). Tasks 002–005 are uncommitted and ready for commit
authorization.

The full Username Recovery feature is not yet implemented. No existing auth
workflow has been refactored.

### Completed tasks

| Task | Description | Status | Commit |
|------|-------------|--------|--------|
| 001 | `IdempotencyHttpResponses` + `SystemHttpResponses` | committed | `3e95814d` |
| 002 | `HttpStatusClassification` + `HttpStatusClassificationOverride` | implemented, uncommitted | — |
| 003 | `CookieHttpResponses` + `RequestHeaderHttpResponses` | implemented, reviewed, uncommitted | — |
| 004 | `WorkflowHttpResponses` | implemented, reviewed, uncommitted | — |
| 005 | `UsernameRecoveryErrorKey` | implemented, reviewed, uncommitted | — |

## Task 003 completion

### Review verdict

**PASS** — Independent DeepSeek V4 Pro review completed 2026-08-07. All
checkpoints verified: fail-first TDD, changed-file allowlist, no
`err.usernamerecovery.*` codes, no workflow-next-step semantics, no
`Set-Cookie` output, no `missingRequestId()` duplication, no unsafe
error-code string API, no reflection/raw maps/silent catches, no
modification to Tasks 001/002/004 or existing auth workflows.

### Requirements covered

- **R003-01:** `CookieHttpResponses.missingCookie()` returns 401 UNAUTHORIZED + BUSINESS_REJECTED + `err.security.authentication.required` — identical generic pre-auth transport artifact pattern to `CsrfHttpResponses`. PASS.
- **R003-02:** No `err.usernamerecovery.*` codes; uses only approved `ErrorCodes` constants. PASS.
- **R003-03:** No `Set-Cookie` header; test confirms empty headers. PASS.
- **R003-04:** No call to `AuthFlowCookieWriter`. PASS.
- **R003-05:** `RequestHeaderHttpResponses.missingRequiredHeader(String target)` returns 400 BAD_REQUEST + VALIDATION_FAILED + `err.request.validation.failed` + caller-supplied header target. PASS.
- **R003-06:** No `missingRequestId()` — `RequiredRequestIdWebFilter` owns this. PASS.
- **R003-07:** No `missingSchemeRef()` — feature-owned semantics excluded. PASS.
- **R003-08:** No Username Recovery next steps (`SELECT_ACCOUNT`, `RESTART_FLOW`, `RETURN_TO_LOGIN`). PASS.
- **R003-09:** Uses typed `ApiError.field()` factory; no arbitrary error-code string API. PASS.
- **R003-10:** No reflection, raw maps, unchecked casts, silent catches, or sensitive logging. PASS.
- **R003-11:** Fail-first TDD: test written → compile failure → production class → test green. PASS.
- **R003-12:** Generic `MutationResponse<T>` typing preserved. PASS.

### New files

```
src/main/java/…/web/response/CookieHttpResponses.java              (45 lines)
src/main/java/…/web/response/RequestHeaderHttpResponses.java       (52 lines)
src/test/java/…/web/response/CookieHttpResponsesTest.java          (80 lines)
src/test/java/…/web/response/RequestHeaderHttpResponsesTest.java   (77 lines)
```

### Validation performed

| Validation | Result |
|------------|--------|
| Cookie fail-first | exit 1 (compilation failure — production class absent) |
| Cookie focused (post-impl) | exit 0, 4 tests, 0 failures |
| Header fail-first | exit 1 (compilation failure — production class absent) |
| Header focused (post-impl) | exit 0, 4 tests, 0 failures |
| Cumulative shared-response regressions (6 classes) | exit 0, 29 tests, 0 failures |
| Architecture guard tests (4 classes) | exit 0, 14 tests, 0 failures |
| Security/contract grep audits | no forbidden patterns found |
| `git diff --check` | clean (no whitespace errors) |
| Full `mvn test` | NOT_RUN (not an approved milestone) |

### Evidence directory

```
target/evidence/username-recovery/003-cookie-requestheader-http-helpers-20260807-011812/
```

### Status

- Implementation complete, all files uncommitted.
- No existing file modified; only the four approved new files.
- `stash@{0}` preserved and untouched.
- Baseline HEAD: `fb9d436c1c19afc6d82e1a9f943f71adeded5397` (unchanged; no commits made).

## Canonical workflow state

- No OTP and no username disclosure to FE.
- Shared `MemberIdentityType`; `STAFF_NO -> STAFFNO -> APIM STAFFNO`.
- Captcha is verified separately; start atomically consumes `VERIFIED`.
- At least one registered scheme is required to create a session.
- Zero registered schemes create no session/cookie and return `RESTART_FLOW`.
- Sender `ACCEPTED` maps candidate to `REQUESTED`.
- Remaining usernames return `SELECT_ACCOUNT`.
- Final request returns `RETURN_TO_LOGIN` with result status `COMPLETED`.
- Non-production recipient override is mandatory and fails closed.
- No silent exceptions. Mapped exceptions become internal `SYSTEM_FAILED` and public `SYSTEM_ERROR`; `UNKNOWN_OUTCOME` remains separate.

## Generic HTTP-response decision

Username Recovery is the first workflow to use shared helpers under:

```text
com.bct.ngtpa.apiservice.adapter.in.web.response
```

Planned components:

```text
CsrfHttpResponses
CookieHttpResponses
IdempotencyHttpResponses
RequestHeaderHttpResponses
WorkflowHttpResponses
SystemHttpResponses
HttpStatusClassification
HttpStatusClassificationOverride
```

### Shared ownership

- missing CSRF, cookie, idempotency key, request ID, and required generic headers;
- common idempotency conflict/replay transport semantics;
- typed `MutationResponse<T>` to HTTP response translation;
- common success, validation, business, conflict, rate-limit, and system statuses;
- typed `Retry-After` support;
- generic public system failure.

### Username Recovery ownership

- Scheme-Ref ownership/eligibility;
- registered/unregistered outcomes;
- candidate `REQUESTED`;
- `ACTIVE`, `PARTIALLY_REQUESTED`, `COMPLETED`;
- `SELECT_ACCOUNT`, `RESTART_FLOW`, `RETURN_TO_LOGIN`;
- feature codes, schemes, masked effective destination, and completion payload.

### Controller policy

Username Recovery controllers delegate generic response construction and HTTP translation. They do not duplicate status logic, build common failure envelopes inline, create an all-purpose feature HTTP translator, silently catch exceptions, or map arbitrary exceptions to invalid requests.

```text
CookieHttpResponses: response envelope/status
AuthFlowCookieWriter: cookie creation/clearing
```

## Deferred migration

The following remain unchanged:

- `LoginHttpResponses`;
- `RegistrationHttpResponses`;
- `ForgotPasswordHttpResponses`;
- current Login, Registration, Forgot Password, Captcha, and Logout controllers/mappers.

A future refactor must preserve current public contracts, compare old/new HTTP mappings, run full cumulative auth regressions, and remove feature-specific translators only after equivalence is demonstrated.

## Implementation tests to add

- exact helper statuses, API statuses, codes, targets, sources, messages, and errors;
- generic/reactive typing;
- controller delegation for cookie, CSRF, idempotency, request ID, and Scheme-Ref;
- common HTTP mappings and typed `Retry-After` without reflection;
- `SYSTEM_FAILED -> SystemHttpResponses -> SYSTEM_ERROR`;
- no raw maps, unchecked casts, silent catches, or sensitive logging;
- architecture guard preventing an all-purpose `UsernameRecoveryHttpResponses` duplicate.

## Documents updated

- `username-recovery-solution-design.md`
- `README.md`
- `handover.md`

Other documents remain unchanged because this decision adds an implementation architecture foundation without changing the approved public API, APIM contract, session semantics, or sequence flow.

## Task 004 completion

### Review verdict

**PASS_WITH_NONBLOCKING_NOTES** — Independent DeepSeek V4 Pro review completed
2026-08-07. All checkpoints verified: fail-first TDD, changed-file allowlist (2
files), typed classification via Task 002 policy, exact body identity and
generic `<T>` preservation, no first-error/error-string inference, no
reflection/raw maps/unchecked casts/silent catches, no Username Recovery feature
codes or Scheme-Ref semantics, no modifications to Tasks 001–003 or existing
auth workflows, `git diff --check` clean, Maven Java 21.

Non-blocking observation: fail-first test had a secondary compilation error in a
private helper (`HttpStatus` vs `HttpStatusCode` return type) fixed alongside
production implementation. Primary expected failure (missing class) was
correctly captured. Trivial one-line fix; noted for TDD discipline only.

### Requirements covered

- **R004-01:** Typed `MutationResponse<T>` to `ResponseEntity` translation via
  `HttpStatusClassification.classify(body.status())`. PASS.
- **R004-02:** Supports override variant via
  `HttpStatusClassification.classify(body.status(), override)`. PASS.
- **R004-03:** Success-like statuses (SUCCESS, UPDATED, SUBMITTED, NO_CHANGE,
  PENDING_APPROVAL, PARTIAL_SUCCESS) map to 200 OK. PASS.
- **R004-04:** VALIDATION_FAILED → 400 BAD_REQUEST; BUSINESS_REJECTED → 422
  UNPROCESSABLE_ENTITY; SYSTEM_ERROR → 503 SERVICE_UNAVAILABLE. PASS.
- **R004-05:** Downstream unnormalized statuses
  (DOWNSTREAM_REJECTED/DOWNSTREAM_ERROR) fail closed with
  IllegalArgumentException. PASS.
- **R004-06:** Typed overrides (CONFLICT → 409, TOO_MANY_REQUESTS → 429,
  FORBIDDEN → 403) applied after normalization proof. PASS.
- **R004-07:** Exact body identity preserved (isSameAs). PASS.
- **R004-08:** Generic `<T>` type preserved across all paths. PASS.
- **R004-09:** No headers added (typed policy carries none). PASS.
- **R004-10:** Null inputs rejected with NullPointerException. PASS.
- **R004-11:** Stateless final class, private constructor, static methods, no
  Spring stereotype. PASS.
- **R004-12:** No first-error or error-string status inference (never accesses
  body.errors()). PASS.
- **R004-13:** No reflection, raw maps, unchecked casts, arbitrary payload
  inspection, or silent catches. PASS.
- **R004-14:** No Username Recovery dependency (zero imports or string
  references). PASS.

### New files

```
src/main/java/…/web/response/WorkflowHttpResponses.java              (59 lines)
src/test/java/…/web/response/WorkflowHttpResponsesTest.java         (176 lines)
```

### Validation performed

| Validation | Result |
|------------|--------|
| Fail-first (focused) | exit 1 (compilation failure — production class absent) |
| Focused (post-impl) | exit 0, 17 tests, 0 failures |
| Cumulative shared-response regressions (8 suites) | exit 0, 51 tests, 0 failures |
| Security/contract grep audits | no forbidden patterns found |
| `git diff --check` | clean (exit 0, no whitespace errors) |
| Full `mvn test` | NOT_RUN (not an approved milestone) |

### Evidence directory

```
target/evidence/username-recovery/004-workflow-http-helpers-20260807-094159/
```

All 20 evidence files (001–020) present and consistent with review findings.

## Task 005 completion

### Review verdict

**PASS** — Independent DeepSeek V4 Pro review completed 2026-08-07. All
checkpoints verified: branch and baseline HEAD match, exactly two-file
allowlist, fail-first TDD (test-compile failure → production class → 7 tests
green), seven approved error keys with complete design traceability, no missing
or unapproved keys, no shared-key duplication, `WorkflowErrorKey` +
`WorkflowKeyValidator` convention followed, no messages/HTTP status/ApiStatus/
next-steps/DTOs/behavior embedded, no reflection/raw maps/unchecked casts/silent
catches/logging, `git diff --check` clean, Maven Java 21.0.2.

Non-blocking observations:
1. Evidence file numbering groups related artifacts under shared prefixes
   (`002-fail-first-*`) instead of the contract's sequential numbering — all
   content is semantically complete.
2. `ForgotPasswordErrorKey` uses an older pattern (no `WorkflowErrorKey`,
   no `WorkflowKeyValidator`); `UsernameRecoveryErrorKey` correctly follows
   the current `LoginErrorKey`/`RegistrationErrorKey`/`LogoutErrorKey` pattern.

### Requirements covered

- **R005-01:** Typed `UsernameRecoveryErrorKey` enum implements `WorkflowErrorKey` with exact two-file allowlist. PASS.
- **R005-02:** Seven approved feature-specific error keys: `AUTH_FLOW_INVALID`, `CSRF_INVALID`, `REGISTRATION_REQUIRED`, `REQUEST_ALREADY_REQUESTED`, `SCHEME_REF_INVALID`, `VERIFICATION_FAILED`, `WORKFLOW_COMPLETED`. PASS.
- **R005-03:** `VERIFICATION_FAILED` uses the exact design code string `err.usernamerecovery.verification.failed`. Remaining six correctly derived from design semantic descriptions using established naming convention. PASS.
- **R005-04:** No shared idempotency/system/CSRF/transport keys duplicated. PASS.
- **R005-05:** Uses `WorkflowKeyValidator.validate()` in constructor matching `LoginErrorKey`/`RegistrationErrorKey`/`LogoutErrorKey` pattern. PASS.
- **R005-06:** No messages, HTTP statuses, `ApiStatus`, next steps, DTOs, APIM codes, or behavior embedded. PASS.
- **R005-07:** No reflection, raw maps, unchecked casts, silent catches, or logging. PASS.
- **R005-08:** Fail-first TDD: test written → compile failure → production enum created → 7 tests green. PASS.
- **R005-09:** Every constant traced to approved design source; no unreferenced or placeholder key. PASS.

### New files

```
src/main/java/…/workflow/UsernameRecoveryErrorKey.java       (23 lines)
src/test/java/…/workflow/UsernameRecoveryErrorKeyTest.java   (111 lines)
```

### Validation performed

| Validation | Result |
|------------|--------|
| Fail-first (focused) | exit 1 (test-compile: `package UsernameRecoveryErrorKey does not exist`) |
| Focused (post-impl) | exit 0, 7 tests, 0 failures |
| Cumulative regression (5 key suites) | exit 0, 24 tests, 0 failures |
| `WorkflowKeyConventionTest` | exit 0, 7 tests, 0 failures |
| `WorkflowKeyValidatorLoginErrorPrefixTest` | exit 0, 2 tests, 0 failures |
| `WorkflowKeyValidatorRegistrationErrorPrefixTest` | exit 0, 3 tests, 0 failures |
| `WorkflowKeyArchitectureGuardTest` | exit 0, 5 tests, 0 failures |
| Key traceability audit | all 7 constants traced to design sources |
| Architecture audit | Clean Architecture compliance, no forbidden patterns |
| Security/contract audit | no secrets, no logging, safe validation messages |
| `git diff --check` | clean (exit 0, no whitespace errors) |
| Full `mvn test` | NOT_RUN (not an approved milestone) |

### Evidence directory

```
target/evidence/username-recovery/005-username-recovery-error-keys-20260807-111310/
```

All evidence artifacts present and consistent with review findings.

## Next step

Task 005 is implemented and reviewed (PASS). The following tasks are uncommitted
and await Nick's commit authorization: Tasks 002, 003, 004, and 005.

Task 006 (`domain-models`) is `PROPOSED_NOT_AUTHORIZED`. Do not start Task 006
before commit and explicit authorization.
