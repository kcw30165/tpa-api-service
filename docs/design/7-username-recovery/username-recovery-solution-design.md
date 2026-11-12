# Username Recovery Solution Design

## 1. Purpose

This document defines the approved pre-login Username Recovery solution for the ORSO member portal. It preserves the agreed workflow design and introduces a generic HTTP-response foundation for Username Recovery only. Existing Login, Registration, Forgot Password, Captcha, and Logout response implementations are not refactored in this phase.

## 2. Canonical decisions

- BRD Version A is authoritative.
- Username Recovery does not use OTP.
- A recovered username is sent only. It is never returned to Angular or displayed in the browser.
- Registration, Forgot Password, and Username Recovery reuse the existing shared `MemberIdentityType` enum.
- FE public identity values are `GPN`, `GPFS`, `STAFF_NO`, and `ID`.
- Java maps public `STAFF_NO` to `MemberIdentityType.STAFFNO`, stores `STAFFNO`, and sends APIM `search-by=STAFFNO`.
- FE verifies captcha through the existing captcha endpoints before calling Username Recovery start.
- `/username-recovery/start` receives `captchaChallengeId` but no captcha answer. Java requires Redis status `VERIFIED` and atomically changes it to `CONSUMED`.
- A result page is shown for every valid, non-empty lookup result, including when only one scheme exists and when every scheme is unregistered.
- FE receives only `schemeRef`, `accountEnv`, `trustCode`, and `isRegistered`.
- After sender acceptance, Java marks the candidate `REQUESTED`.
- If another registered candidate is unrequested, `/request` returns `SELECT_ACCOUNT`.
- When every registered candidate is `REQUESTED`, the session becomes `COMPLETED` and `/request` returns `RETURN_TO_LOGIN` with result status `COMPLETED`.
- A session, CSRF token, opaque Scheme-Refs and cookie are created for every valid, non-empty lookup result, including when no scheme is registered. Only member-not-found (including empty lookup data) creates no session and returns `RESTART_FLOW`. FE displays unregistered rows but disables their username-request action; `/request` for an unregistered Scheme-Ref returns `UNREGISTERED_CANDIDATE` without delivery.
- FE owns rendered labels. Java returns codes only.
- Session TTL is configurable and defaults to 600 seconds.
- Non-production delivery uses dedicated Username Recovery destination overrides and fails closed when the applicable override is missing.
- Username Recovery has no silent exception paths. Mapped exceptions become internal `SYSTEM_FAILED` and public `SYSTEM_ERROR`; explicit delivery uncertainty remains `UNKNOWN_OUTCOME`.

## 3. Scope

### In scope

- identity type/value, DOB, and exactly one email/mobile input;
- existing captcha challenge, verify, and refresh APIs;
- verified captcha consumption at start;
- APIM/TAS member search;
- matched registered/unregistered scheme classification;
- protected session, CSRF, cookie, Scheme-Refs, expiry, and cancellation;
- per-scheme username delivery and `REQUESTED` state;
- idempotency, rate limiting, audit, masking, and message localization;
- non-production destination override;
- generic HTTP-response helpers for Username Recovery.

### Out of scope

- OTP;
- password reset or username change;
- Account Registration completion;
- authenticated portal session, ActorContext, or PortalAccessContext;
- delivery-status callback;
- username disclosure to FE;
- migration of existing workflow controllers to the new response helpers.

## 4. Identity boundary

The shared enum is reused:

```java
public enum MemberIdentityType {
    GPN,
    GPFS,
    STAFFNO,
    ID
}
```

| Meaning | FE/public | Java/session | APIM |
|---|---|---|---|
| Staff number | `STAFF_NO` | `STAFFNO` | `STAFFNO` |
| GPN | `GPN` | `GPN` | `GPN` |
| GPFS identifier | `GPFS` | `GPFS` | `GPFS` |
| Identity number | `ID` | `ID` | `ID` |

The web mapper performs `STAFF_NO -> MemberIdentityType.STAFFNO`. Identity-value validation reuses Registration behavior.

## 5. Captcha design

Existing endpoints:

```text
POST /api/v1/auth/captcha/challenge
POST /api/v1/auth/captcha/verify
POST /api/v1/auth/captcha/refresh
```

Existing statuses:

```java
public enum CaptchaStatus {
    CREATED,
    VERIFIED,
    CONSUMED,
    REFRESHED,
    EXPIRED,
    LOCKED
}
```

Canonical transitions:

```text
challenge: CREATED
verify: CREATED -> VERIFIED
username-recovery/start: VERIFIED -> CONSUMED
```

FE enablement is a usability control only. Java remains authoritative. Start accepts only `VERIFIED`, atomically consumes it, and rejects missing or all other statuses. Once consumed, the challenge remains consumed even if a later APIM or session operation fails.

## 6. End-to-end flow

1. Member clicks **Forgot Username**.
2. FE captures identity type and value.
3. FE obtains and renders a captcha challenge.
4. FE calls captcha verify with the answer.
5. Java stores `VERIFIED`; FE enables Continue.
6. Member enters DOB and exactly one contact.
7. FE calls `/username-recovery/start` with the challenge ID and no answer.
8. Java validates input, maps the identity type, reserves idempotency, and atomically consumes the verified captcha.
9. Java calls APIM `TRPSearchMember` and validates the generic response envelope and `cust-id` invariants.
10. Member not found returns `RESTART_FLOW` with no session/cookie.
11. A valid, non-empty result (registered-only, mixed or all unregistered) causes session, CSRF, cookie, and opaque Scheme-Ref creation.
12. FE shows registered request buttons and unregistered Registration reminders.
13. `/request` validates cookie, CSRF, origin/referer, idempotency, and Scheme-Ref ownership.
14. Java resolves the effective destination and calls `MessageDeliveryPort`.
15. Sender `ACCEPTED` becomes candidate `REQUESTED`.
16. Remaining unrequested registered candidates return `SELECT_ACCOUNT`.
17. Final requested registered candidate atomically changes the session to `COMPLETED` and returns `RETURN_TO_LOGIN` with result status `COMPLETED`.

## 7. Session lifecycle and minimal data

```text
ACTIVE -> PARTIALLY_REQUESTED -> COMPLETED
ACTIVE/PARTIALLY_REQUESTED -> CANCELLED or EXPIRED
```

Unregistered candidates do not contribute to completion counts. A session whose candidates are all unregistered has no registered candidate to request, so it never becomes `COMPLETED`; it ends by cancel or expiry.

Store only fields required by later operations:

- scenario, status, session revision, CSRF hash, language, timestamps;
- protected verified destination and channel;
- Scheme-Ref, accountEnv, trustCode, registration flag;
- username for registered candidates only;
- candidate state such as `AVAILABLE`, `RESERVED`, `REQUESTED`, `FAILED`, or `UNKNOWN_OUTCOME`;
- requested timestamp and sender/idempotency reference required for replay safety.

Do not persist unused policy, certificate, scheme, termination, DOB, or raw identity fields.

## 8. APIM boundary

Use the existing generic body:

```java
ApimResponseBody<TrpSearchMemberApimDataItem>
```

Do not create `TrpSearchMemberApimResponseBody`.

Confirmed invariant:

```text
isRegistered=true  => cust-id is nonblank
isRegistered=false => cust-id is blank or absent
```

Violations become `SYSTEM_FAILED`. Only the minimal candidate projection crosses into the application/session model.

## 9. Message delivery and non-production override

Sender status and candidate status are distinct:

```text
sender: ACCEPTED
candidate: REQUESTED
```

Same-key retry replays without sending again. A different key after `REQUESTED` is rejected. Unknown sender outcome is persisted as `UNKNOWN_OUTCOME` and never blindly resent.

Configuration:

```yaml
auth-workflows:
  username-recovery:
    message:
      destination-override:
        mobile: ${USERNAME_RECOVERY_MESSAGE_OVERRIDE_MOBILE:}
        email: ${USERNAME_RECOVERY_MESSAGE_OVERRIDE_EMAIL:}
```

`PROD` and `DR` use the verified member destination. Other profiles use the dedicated override. The protected session retains the verified destination; the shared delivery decorator resolves the effective destination immediately before delivery. The public response masks the effective destination.

## 10. Cookie, CSRF, and local development

Production cookie:

```text
Name: auth_flow_sid
Path: /api/v1/auth/username-recovery
Max age: 600 seconds
Secure: true
HttpOnly: true
SameSite: Strict
Domain: omitted
```

`/request` and `/cancel` require cookie, CSRF, and origin/referer validation. Local FE development uses relative `/api` URLs through the Angular development-server proxy. A local-only Java profile may set `Secure=false` if HTTP localhost is unavoidable.

## 11. Error and exception policy

- no empty or ignored catches;
- no unexpected exception converted to null, empty data, invalid request, or success;
- expected validation/business conditions use typed results;
- APIM, Redis, context, idempotency, template, and sender exceptions are mapped at the appropriate boundary to `SYSTEM_FAILED`;
- web translation emits `SYSTEM_ERROR` and a generic system code;
- unexpected defects reach the central exception handler;
- logs contain sanitized correlation context but no username, identity, DOB, destination, token, cookie, Scheme-Ref, or Redis key;
- cleanup failure preserves the primary failure and creates a secondary failure event;
- `UNKNOWN_OUTCOME` remains distinct to prevent duplicate delivery.

## 12. Generic HTTP Response Foundation

### 12.1 Objective and adoption boundary

Username Recovery is the first workflow to use shared HTTP-response helpers for common transport concerns. This avoids another feature-level class containing repeated status mapping and missing-header/cookie responses.

This phase does not replace or modify the current implementations for Login, Registration, Forgot Password, Captcha, Logout, or other controllers. Their migration is deferred to a dedicated refactoring phase.

### 12.2 Current problem

Common response behavior is scattered across feature-specific classes and controllers, including:

- `LoginHttpResponses`;
- `RegistrationHttpResponses`;
- `ForgotPasswordHttpResponses`;
- controller-local missing-cookie/header checks;
- repeated `MutationResponse.failure(...)` creation;
- repeated `ResponseEntity.status(...)` mapping;
- repeated reactive `Mono.just(...)` wrappers;
- endpoint-specific system-failure `onErrorResume(...)` branches.

`CsrfHttpResponses` is the current shared starting point. Username Recovery extends this shared approach rather than creating an all-purpose `UsernameRecoveryHttpResponses` duplicate.

### 12.3 Shared package and components

Use the existing package:

```text
com.bct.ngtpa.apiservice.adapter.in.web.response
```

Add or reuse:

```text
CsrfHttpResponses
CookieHttpResponses
IdempotencyHttpResponses
RequestHeaderHttpResponses
WorkflowHttpResponses
SystemHttpResponses
```

Final names may be refined during implementation, but the responsibilities below are mandatory.

### 12.4 Responsibilities

#### `CsrfHttpResponses`

- reuse the existing missing `X-XSRF-TOKEN` response contract;
- preserve generic typing and reactive immediate-response support;
- leave invalid-token proof to the application workflow result.

#### `CookieHttpResponses`

- missing required `auth_flow_sid`;
- unusable or expired auth-flow context when no feature result exists to map;
- generic restart guidance;
- response envelope/status only.

It does not write cookies, compose `Set-Cookie`, mutate Redis, or know the Username Recovery cookie path. `AuthFlowCookieWriter` remains responsible for cookie creation and clearing.

#### `IdempotencyHttpResponses`

- missing `Idempotency-Key`;
- operation in progress;
- key reused with a different semantic request;
- shared conflict behavior;
- completed replay transport semantics when the replay snapshot is already available.

It reuses approved shared idempotency codes and knows nothing about Scheme-Ref or candidate state.

#### `RequestHeaderHttpResponses`

- missing `X-Request-Id`;
- missing required `Scheme-Ref` header;
- other generic, syntactic required-header failures;
- canonical public header targets.

A Scheme-Ref that is present but not session-bound remains a feature result.

#### `WorkflowHttpResponses`

Provide typed conversion from `MutationResponse<T>` to `ResponseEntity<MutationResponse<T>>`.

Common mappings:

- success to `200 OK`;
- validation failure to `400 Bad Request`;
- business rejection to `422 Unprocessable Entity`;
- shared conflicts to `409 Conflict`;
- shared rate-limit outcomes to `429 Too Many Requests`;
- `SYSTEM_ERROR` to the approved service-unavailable status;
- typed `Retry-After` support where an approved result exposes retry timing.

New code must not use reflection, raw maps, unchecked casts, or arbitrary first-error string chains to infer feature behavior. Feature-specific HTTP exceptions require an explicit typed classification or policy input.

#### `SystemHttpResponses`

- generic public `SYSTEM_ERROR` envelope;
- approved generic system code;
- `503 Service Unavailable` for Username Recovery;
- no exception or downstream detail;
- generic payload typing.

Mapped internal `SYSTEM_FAILED` uses this path. Explicit `UNKNOWN_OUTCOME` retains its typed workflow guidance while remaining public `SYSTEM_ERROR`.

### 12.5 Ownership boundary

Shared helpers own only:

- HTTP status and common response headers;
- common missing header/cookie envelopes;
- generic idempotency transport responses;
- generic system response;
- typed conversion of a completed mutation envelope.

Username Recovery continues to own:

- verification-failed and other feature codes;
- Scheme-Ref ownership and eligibility;
- registered/unregistered behavior;
- candidate `REQUESTED` state;
- workflow lifecycle and completion;
- `SELECT_ACCOUNT`, `RESTART_FLOW`, and `RETURN_TO_LOGIN`;
- schemes, masked destination, and workflow-status payloads.

### 12.6 Controller usage

```text
missing CSRF              -> CsrfHttpResponses
missing auth-flow cookie  -> CookieHttpResponses
missing Idempotency-Key   -> IdempotencyHttpResponses
missing X-Request-Id      -> RequestHeaderHttpResponses
missing Scheme-Ref        -> RequestHeaderHttpResponses
mapped SYSTEM_FAILED      -> SystemHttpResponses
feature MutationResponse  -> WorkflowHttpResponses
```

Username Recovery controllers remain thin and must not:

- recreate generic failure envelopes inline;
- duplicate HTTP status decisions;
- create a feature-local all-purpose HTTP translator;
- silently catch exceptions;
- map arbitrary exceptions to invalid request;
- use `onErrorResume` without sanitized failure recording and `SYSTEM_FAILED` mapping.

### 12.7 API design constraints

Shared helpers must:

- preserve `MutationResponse<T>` generic typing;
- support reactive immediate responses where needed;
- reuse `ApiError`, `ApiMessage`, `ApiStatus`, and approved shared error keys;
- be stateless and thread-safe;
- use small explicit methods rather than many nullable parameters;
- avoid reflection, untyped maps, raw `Object`, and unchecked casts in new code;
- be directly unit-testable without a running server.

### 12.8 Testing

Add focused tests for:

- exact HTTP status, API status, code, target, source, messages, and errors;
- generic/reactive typing;
- controller delegation for CSRF, cookie, idempotency, request ID, and Scheme-Ref;
- success, validation, business, conflict, rate-limit, and system mappings;
- typed `Retry-After` without reflection;
- cookie-response versus cookie-writer responsibility;
- `SYSTEM_FAILED -> SystemHttpResponses -> SYSTEM_ERROR`;
- no duplicate `UsernameRecoveryHttpResponses` architecture;
- no sensitive data or raw exception in responses or logs.

These tests supplement the existing comprehensive Username Recovery traceability design.

### 12.9 Deferred migration

A future refactor may migrate:

- `LoginHttpResponses`;
- `RegistrationHttpResponses`;
- `ForgotPasswordHttpResponses`;
- repeated controller-local response construction.

That work must preserve existing public contracts, compare current and new mappings, run full cumulative auth regressions, and remove feature-specific translators only after equivalence is proven. It must not be combined with unrelated business changes.
