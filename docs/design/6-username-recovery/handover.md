# Username Recovery Handover

## Current stage

Task 003 (`CookieHttpResponses` + `RequestHeaderHttpResponses`) is
implemented, tested, and reviewed (uncommitted).

### Completed tasks

| Task | Description | Status | Commit |
|------|-------------|--------|--------|
| 001 | `IdempotencyHttpResponses` + `SystemHttpResponses` | committed | `3e95814d` |
| 002 | `HttpStatusClassification` + `HttpStatusClassificationOverride` | implemented, uncommitted | — |
| 003 | `CookieHttpResponses` + `RequestHeaderHttpResponses` | implemented, reviewed, uncommitted | — |

The remaining shared helper (`WorkflowHttpResponses`) and the full Username
Recovery feature are not yet implemented. No existing auth workflow has been
refactored.

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

## Next step

Task 003 is implemented and reviewed (PASS). Proceed to commit authorization
for Tasks 002 and 003, then to Task 004 (`WorkflowHttpResponses`) —
`PROPOSED_NOT_AUTHORIZED`. Do not start Task 004 before commit and explicit
authorization.
