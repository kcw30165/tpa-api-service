# Username Recovery Handover

## Current stage

Task 002 (`HttpStatusClassification` + `HttpStatusClassificationOverride`) is
implemented and tested (uncommitted).

### Completed tasks

| Task | Description | Status | Commit |
|------|-------------|--------|--------|
| 001 | `IdempotencyHttpResponses` + `SystemHttpResponses` | committed | `3e95814d` |
| 002 | `HttpStatusClassification` + `HttpStatusClassificationOverride` | implemented, uncommitted | — |

The remaining shared helpers (`CookieHttpResponses`, `RequestHeaderHttpResponses`,
`WorkflowHttpResponses`) and the full Username Recovery feature are not yet
implemented. No existing auth workflow has been refactored.

## Task 002 completion

### Requirements covered

- **R002-01:** Exhaustive typed switch mapping all 11 `ApiStatus` values to `HttpStatus`. PASS.
- **R002-02:** No default branch; every `ApiStatus` value explicitly handled. PASS.
- **R002-03:** `DOWNSTREAM_REJECTED` and `DOWNSTREAM_ERROR` fail closed with `IllegalArgumentException`. PASS.
- **R002-04:** Override enum contains exactly `CONFLICT` (409), `TOO_MANY_REQUESTS` (429), `FORBIDDEN` (403). PASS.
- **R002-05:** `classify()` returns `HttpStatus` only; no response construction, logging, or side effects. PASS.
- **R002-06:** Shared-versus-feature ownership boundary preserved; no feature types imported. PASS.
- **R002-07:** No error-code strings, `WorkflowErrorKey`, `SharedWorkflowErrorKey`, or Username Recovery types. PASS.
- **R002-08:** Stateless utility pattern matches existing `IdempotencyHttpResponses`/`SystemHttpResponses` style. PASS.

### New files

```
src/main/java/…/web/response/HttpStatusClassification.java           (62 lines)
src/main/java/…/web/response/HttpStatusClassificationOverride.java   (29 lines)
src/test/java/…/web/response/HttpStatusClassificationTest.java       (106 lines)
src/test/java/…/web/response/HttpStatusClassificationOverrideTest.java (48 lines)
```

### Validation performed

| Validation | Result |
|------------|--------|
| Fail-first focused tests | exit 1 (compilation failure — production types absent) |
| Focused tests (post-impl) | exit 0, 18 tests, 0 failures |
| Cumulative shared-response regressions | exit 0, 26 tests, 0 failures |
| Compile validation | exit 0, BUILD SUCCESS |
| Architecture guard (PublicDownstreamStatusIsolationGuardTest) | exit 0, 3 tests, 0 failures |
| `git diff --check` | clean (no whitespace errors) |
| Full `mvn test` | NOT_RUN (not an approved milestone) |

### Evidence directory

```
target/evidence/username-recovery/002-http-status-classification-20260806-230424/
```

### Status

- Implementation complete, all files uncommitted.
- No existing file modified; only the four approved new files.
- `stash@{0}` preserved and untouched.
- Baseline HEAD: `3e95814d` (unchanged; no commits made).

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

Proceed to Task 003 (`username-recovery-workflow-http-responses`) after Task 002
commit authorization is granted. Do not start Task 003 before commit.
