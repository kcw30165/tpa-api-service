# CAPTCHA Integration and Reuse Guide

## 1. Decide whether CAPTCHA is appropriate

Use CAPTCHA only when the security threat model calls for an automation-resistance challenge. It should not be added merely because a workflow is unauthenticated.

Before implementation, document:

- Automated abuse scenario
- Protected operation and side effect
- Existing rate limits and challenge controls
- When CAPTCHA is issued
- When it is verified
- When it is consumed
- Session/scenario binding
- Accessibility alternative
- Privacy and logging behavior
- Failure and Redis-outage behavior
- Security owner approval

## 2. Reuse decision

### Reuse the existing capability when

- A workflow needs the same image-based text challenge
- Existing policy fields are sufficient
- Create/refresh/verify/consume lifecycle applies
- Redis-backed challenge state is appropriate

### Extend policy/configuration when

- The workflow needs different length, alphabet, TTL, attempt count, or case behavior
- It needs a distinct rate-limit policy
- It needs different public errors/navigation

### Add a new challenge mechanism only when

- An approved accessibility alternative requires another channel
- A third-party anti-bot service is adopted
- Risk scoring or advanced bot detection changes the security model

Do not overload `CaptchaChallenge` with an unrelated proof mechanism.

## 3. Integration sequence

### Step 1 — Add scenario policy

Define:

- Enabled/required behavior
- Length
- Alphabet
- Case sensitivity
- TTL
- Maximum attempts
- Refresh policy
- Rate limits

Validate policy at startup. Required security controls must not silently default to disabled in production.

### Step 2 — Reuse create, refresh, and verify APIs

Prefer the shared CAPTCHA endpoints and DTOs where the frontend interaction is the same. A new workflow should not create its own answer generator or image endpoint.

The frontend stores only the opaque challenge ID and displays the returned image. It submits the answer to the verify endpoint; it never receives the generated answer.

### Step 3 — Bind the challenge to the workflow

Define how the verified challenge is associated with the intended scenario and request. At minimum, consumption must occur in the protected workflow gate and within TTL.

Where stronger binding is required, use protected server-side context rather than client-supplied identity fields.

### Step 4 — Consume before the protected operation

Place consumption before the first expensive or security-sensitive side effect, such as identity lookup, OTP issuance, or downstream account search.

```text
validate basic request
  -> rate-limit start attempt
  -> consume verified CAPTCHA
  -> execute protected lookup/start operation
```

Do not perform the downstream lookup and consume afterward; concurrent reuse could repeat the protected operation.

### Step 5 — Map all consume outcomes

Handle explicitly:

- Consumed
- Not found
- Not verified
- Expired
- Locked
- Already consumed
- Refreshed
- Redis/system failure

Each outcome needs an approved public error, next step, and restart/refresh behavior.

### Step 6 — Apply rate limiting

Rate limit CAPTCHA creation/refresh and/or the protected workflow start according to the threat model. CAPTCHA itself is not a request-volume control.

Avoid unlimited refresh because it can become a challenge-harvesting or resource-exhaustion path.

### Step 7 — Add security audit events

Record sanitized outcome categories and correlation IDs. Do not include the answer, hash, image, or raw user identifiers.

### Step 8 — Add frontend accessibility behavior

The frontend should:

- Render the image with an approved accessible label
- Offer refresh without retaining the old challenge ID
- Explain expiry/lock behavior without exposing security details
- Support the approved alternative/recovery route
- Avoid caching the challenge image beyond its lifecycle
- Never log the submitted answer

## 4. Registration integration

Registration already uses CAPTCHA as a pre-start gate. A new developer should reuse:

- Policy resolved from `AuthWorkflowPolicyPort`
- `RegistrationCaptchaGateService`
- Challenge consumption outcomes
- Registration error keys and navigation
- Redis challenge state
- Rate-limit/audit patterns

Do not add CAPTCHA validation to the APIM Registration lookup adapter. The gate belongs before the downstream call in application orchestration.

## 5. Forgot Password example

A proposed Forgot Password integration is:

```text
GET/POST create CAPTCHA
  -> return challengeId + image

POST verify CAPTCHA
  -> mark challenge VERIFIED

POST Forgot Password start
  -> apply start/client rate limit
  -> consume verified CAPTCHA
  -> perform enumeration-resistant identity/account verification
  -> create auth-flow session and CSRF context
  -> return available masked OTP destinations
```

The response must not reveal whether the submitted identity exists merely because CAPTCHA succeeded. OTP, session, CSRF, idempotency, rate limits, and TAS password-policy behavior remain separate controls.

Post-login Change Password remains outside Forgot Password scope.

## 6. Refresh design guidance

A refresh request should provide only the current challenge ID and required request context. It should not carry identity or account data.

On success:

- Old challenge transitions to `REFRESHED`
- New challenge has a new ID, answer hash, timestamps, and image
- Frontend discards old ID/image/answer input

On partial failure, return an explicit refresh/system error and prevent ambiguity about which challenge remains usable.

## 7. Error-contract guidance

Suggested distinctions:

```text
CAPTCHA_REQUIRED
  missing challenge ID or required proof

CAPTCHA_INVALID
  wrong answer with attempts remaining

CAPTCHA_EXPIRED
  challenge lifetime ended

CAPTCHA_LOCKED
  maximum attempts reached

CAPTCHA_CONSUMED
  one-time proof already used

CAPTCHA_REFRESHED
  old challenge replaced

CAPTCHA_NOT_FOUND
  invalid/unknown challenge without leaking internals

CAPTCHA_REFRESH_FAILED
  replacement could not be created safely
```

Map errors through the common workflow/error contract; do not expose Redis/provider details.

## 8. Pull-request checklist

- [ ] Threat model and security owner identified
- [ ] CAPTCHA necessity justified
- [ ] Existing generators/hashers/Redis adapter reused
- [ ] Policy externalized and validated
- [ ] Raw answer never persisted or returned
- [ ] Constant-time comparison retained
- [ ] Exact lock boundary tested
- [ ] Refresh invalidates old challenge
- [ ] Verified challenge consumed once
- [ ] Consumption occurs before protected side effect
- [ ] Rate limits and audit events defined
- [ ] Failure/Redis-outage behavior defined
- [ ] Errors/navigation configured
- [ ] Accessibility alternative reviewed
- [ ] Logs contain no challenge secret/image/answer
- [ ] Fail-first TDD evidence included
- [ ] Operations document updated
- [ ] Commit message included

Suggested commit message:

```text
docs(security): document CAPTCHA architecture, reuse, operations, and testing
```
