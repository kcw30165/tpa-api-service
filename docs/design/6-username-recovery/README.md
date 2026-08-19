# Username Recovery Design Documentation

## Overview

This documentation set defines the approved pre-login Username Recovery solution. Username Recovery is also the first workflow to adopt a new generic HTTP-response foundation. Existing Login, Registration, Forgot Password, Captcha, and Logout response implementations remain unchanged during this feature.

## Documents

1. [`username-recovery-solution-design.md`](username-recovery-solution-design.md)  
   Canonical workflow, shared identity model, captcha, session lifecycle, delivery, no-silent-exception policy, and generic HTTP-response foundation.

2. [`username-recovery-api-contract-design.md`](username-recovery-api-contract-design.md)  
   `/start`, `/request`, `/cancel`, response examples, HTTP mappings, and error matrix.

3. [`username-recovery-apim-integration-design.md`](username-recovery-apim-integration-design.md)  
   `TRPSearchMember`, generic `ApimResponseBody<T>` reuse, field mapping, and invariants.

4. [`username-recovery-session-redis-design.md`](username-recovery-session-redis-design.md)  
   Protected session, Scheme-Ref, CSRF, TTL, idempotency, and concurrency.

5. [`username-recovery-test-traceability-design.md`](username-recovery-test-traceability-design.md)  
   Comprehensive tests, traceability, regressions, audits, and evidence.

6. [`sequence-diagrams.md`](sequence-diagrams.md)  
   Runtime and local Angular proxy flows.

7. [`username-recovery-nonprod-destination-override-design.md`](username-recovery-nonprod-destination-override-design.md)  
   Non-production recipient override.

8. [`handover.md`](handover.md)  
   Cumulative decisions and deferred refactor boundary.

## Generic HTTP-Response Foundation

Username Recovery uses shared components under:

```text
com.bct.ngtpa.apiservice.adapter.in.web.response

CsrfHttpResponses
CookieHttpResponses
IdempotencyHttpResponses
RequestHeaderHttpResponses
WorkflowHttpResponses
SystemHttpResponses
```

### Responsibilities

- `CsrfHttpResponses`: missing CSRF transport response.
- `CookieHttpResponses`: missing/unusable auth-flow cookie response, without writing cookies.
- `IdempotencyHttpResponses`: missing key, in-progress, key-reused, and common replay transport behavior.
- `RequestHeaderHttpResponses`: missing request ID or required generic header such as Scheme-Ref.
- `WorkflowHttpResponses`: typed `MutationResponse<T>` to HTTP response translation, including typed `Retry-After`.
- `SystemHttpResponses`: generic public `SYSTEM_ERROR` response for internal `SYSTEM_FAILED`.

The shared helpers own transport concerns only. Username Recovery continues to own Scheme-Ref rules, registered/unregistered outcomes, candidate `REQUESTED`, workflow completion, workflow next steps, feature errors, schemes, and masked destination data.

### Incremental boundary

This phase does not refactor:

- `LoginHttpResponses`;
- `RegistrationHttpResponses`;
- `ForgotPasswordHttpResponses`;
- existing Login, Registration, Forgot Password, Captcha, or Logout controllers.

A future refactor may migrate those workflows after public-contract equivalence and full cumulative regressions are proven.

### New-code rules

Username Recovery controllers do not:

- construct repeated generic failure envelopes inline;
- duplicate status-selection logic;
- create an all-purpose `UsernameRecoveryHttpResponses`;
- use reflection, raw maps, or unchecked casts for response translation;
- silently catch exceptions or map unexpected failures to invalid request.

Mapped exceptions become internal `SYSTEM_FAILED`; shared system responses emit public `SYSTEM_ERROR`. Explicit `UNKNOWN_OUTCOME` remains separate for duplicate-send safety.

## Core Workflow Summary

```text
FE STAFF_NO -> MemberIdentityType.STAFFNO -> APIM STAFFNO
captcha verify: CREATED -> VERIFIED
start: VERIFIED -> CONSUMED
sender ACCEPTED -> candidate REQUESTED
remaining usernames -> SELECT_ACCOUNT
all requested -> COMPLETED + RETURN_TO_LOGIN
zero registered -> no session + RESTART_FLOW
```

## API Paths

```text
POST /api/v1/auth/captcha/challenge
POST /api/v1/auth/captcha/verify
POST /api/v1/auth/captcha/refresh
POST /api/v1/auth/username-recovery/start
POST /api/v1/auth/username-recovery/request
POST /api/v1/auth/username-recovery/cancel
```

## Cookie Policy

```text
Name: auth_flow_sid
Path: /api/v1/auth/username-recovery
TTL: 600 seconds
Secure: true in deployed environments
HttpOnly: true
SameSite: Strict
Domain: omitted
```

`CookieHttpResponses` owns the response envelope and status. The existing cookie writer owns `Set-Cookie` creation and clearing.

## Local Angular Development

Use relative `/api` calls through the Angular development-server proxy. Prefer HTTPS localhost. If HTTP is unavoidable, only the local Java profile may set `Secure=false`.

## Maintenance

The generic foundation is approved for new Username Recovery code only until the future cross-workflow refactor is separately planned and validated.

## Canonical captcha boundary

Username Recovery follows [`../captcha/captcha-verification-lifecycle-contract.md`](../captcha/captcha-verification-lifecycle-contract.md). Start receives only `captchaChallengeId`, never the answer, consumes through the shared application gate, performs no later side effect after rejection, and does not use OTP.
