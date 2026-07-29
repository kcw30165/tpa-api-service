> Status: Design baseline v0.1  
> Date: 20 July 2026  
> Scope: Subsequent member login plus reusable authentication foundations  
> Source baseline: Login BRD V0.3, discovery decisions in 02-brd.md through 10-brd.md, and the supplied current codebase snapshot  
> Open external dependency: detailed `TRPGetAccountList` business failure-code mapping remains TBC.


# Login Workflow Contract

## 1. Principle

The lightweight workflow helpers are not a workflow engine. Runtime authority remains in `AuthFlowSession` status plus protected login context, OTP challenge state and authenticated-session state.

## 2. Login steps

```text
LOGIN_CREDENTIALS
SELECT_OTP_DESTINATION
VERIFY_OTP
SELECT_ACCOUNT
LOGIN_CREDENTIALS (restart/terminal redirect)
```

OTP-specific next steps, statuses, errors and HTTP mappings must reuse the existing shared registration OTP contract. Login must not invent a second OTP state vocabulary.

## 3. Commands and guards

### Submit credentials

Allowed without pre-auth session. On verified credentials, creates LOGIN AuthFlowSession.

### Send OTP

Requires LOGIN scenario, valid session, CSRF, origin/referer, idempotency and valid opaque destination ID.

### Resend OTP

Uses existing challenge replacement lifecycle and configured login policy.

### Verify OTP

Uses existing verification lifecycle. On success, account retrieval and authenticated-session establishment occur in the same login orchestration.

### Cancel

Idempotent. Invalidates OTP, deletes pre-auth state, clears transport and returns `LOGIN_CREDENTIALS`.

## 4. Terminal/error transitions

- Resend exhaustion -> terminate pre-auth -> `LOGIN_CREDENTIALS`.
- Account-list unavailable/unusable -> terminate pre-auth -> `LOGIN_CREDENTIALS`.
- Authenticated-session persistence failure -> terminate pre-auth -> `LOGIN_CREDENTIALS`.
- Successful authenticated-session creation -> consume pre-auth -> `SELECT_ACCOUNT`.

## 5. Idempotency

Credential submission, OTP send/resend/verify and authenticated-session establishment require action-appropriate idempotency. Replays return the previously committed response without duplicate APIM calls, OTP deliveries or session replacement.

Unknown outcomes must not be blindly replayed when a downstream mutation may have committed; safe read login calls follow the shared policy.
