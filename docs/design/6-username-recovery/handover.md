# Username Recovery Handover

## Current stage

The Username Recovery design baseline now includes an incremental generic HTTP-response foundation. No implementation has started, and no existing auth workflow is being refactored in this phase.

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

Plan implementation subtasks beginning with generic HTTP-response helper tests and the Username Recovery web-contract harness.
