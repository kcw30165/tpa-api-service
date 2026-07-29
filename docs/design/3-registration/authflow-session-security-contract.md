# AuthFlow Session Security Contract

## 1. Purpose

This document defines the security contract for pre-auth `AuthFlowSession` usage in registration and other future auth flows.

It covers:

- cookie transport;
- CSRF behavior;
- CORS and Origin/Referer validation;
- protected session context;
- logging and Redis sanitization;
- captcha binding;
- rate-limit runtime contract;
- session replacement behavior.

---

## 2. Session Type Boundary

`AuthFlowSession` is a temporary pre-auth workflow session.

It must never be treated as proof of login and must never grant protected portal access.

Future `AuthenticatedSession` is separate and must not be merged with `AuthFlowSession`.

---

## 3. Cookie Contract

### 3.1 Cookie Name and Policy

```http
Set-Cookie: auth_flow_sid=afs_xxx; HttpOnly; Secure; SameSite=None; Path=/api/v1/auth; Max-Age=1800
```

Rules:

- Name: `auth_flow_sid`
- HttpOnly: `true`
- Secure: `true`
- SameSite: `None`
- Path: `/api/v1/auth`
- Value: opaque high-entropy AuthFlowSession ID
- No PII in cookie value
- Cookie value must never be logged

### 3.2 Path Rule

All AuthFlowSession-protected endpoints must be under `/api/v1/auth`, unless the `auth_flow_sid` cookie path is changed.

---

## 4. CORS Contract

Because FE and Java BFF are cross-site, CORS must allow credentials for the exact FE origin only.

Server behavior:

```http
Access-Control-Allow-Origin: https://exact-fe-origin.example
Access-Control-Allow-Credentials: true
```

FE behavior:

```typescript
{ withCredentials: true }
```

Wildcard origins must not be used with credentials.

---

## 5. CSRF Contract

### 5.1 Current BRD Rule

For current BRD scope, CSRF token is created at `registration/start` and remains stable for the AuthFlowSession lifetime, unless explicitly returned as `result.csrfToken` in a later response.

### 5.2 Delivery

In true cross-site FE/BFF deployments:

- Java returns `csrfToken` in response body when AuthFlowSession is created.
- FE stores token in memory.
- FE sends token in `X-XSRF-TOKEN` for protected auth-flow POST requests.

Example:

```http
X-XSRF-TOKEN: csrf_xxx
```

Optional same-site/readable-cookie mode may use a readable `XSRF-TOKEN` cookie only when FE can actually read it.

### 5.3 Validation

For protected auth-flow POST requests, Java validates:

- `auth_flow_sid` cookie exists;
- AuthFlowSession exists in Redis;
- submitted `X-XSRF-TOKEN` exists;
- submitted token matches `csrfTokenHash` stored in AuthFlowSession;
- Origin/Referer is allowed.

### 5.4 Refresh / Lost CSRF Behavior

`auth/flow/resume` is out of current BRD scope.

If FE refreshes or loses in-memory CSRF token:

- FE must restart the registration flow.
- FE must not call `registration/cancel`, because cancel is CSRF-protected.
- The old AuthFlowSession will expire naturally or be replaced by a new registration/start flow.

---

## 6. Origin and Referer Validation

Origin/Referer validation is required for protected auth-flow requests.

Rules:

- allowed origins must be configured;
- validation failures are security rejections;
- response must use `MutationResponse.errors[]`;
- log level follows tech-lead rule: `ERROR` for validation/security rejection;
- eventType should be `SECURITY_REJECTION`.

---

## 7. Protected Session Context

The implementation stores registration context as protected encrypted session context.

Rules:

- Raw identity/contact payload must not be stored as plaintext Redis fields.
- Raw destination must not be stored as plaintext Redis field.
- Ciphertext and key material must never be logged.
- `maskedDestination` may be stored for display.
- Protected context may be used for registration continuation and OTP resend.

Current protected registration context fields:

```json
{
  "registrationContextCiphertext": "enc_xxx",
  "registrationContextKeyVersion": "dev-v1",
  "registrationContextAlgorithm": "AES_GCM_V1"
}
```

---

## 8. Logging Sanitization

Never log:

- `auth_flow_sid` value;
- AuthFlowSession ID;
- raw CSRF token;
- OTP code;
- OTP code hash if not needed;
- plaintext destination;
- destination ciphertext;
- registration context ciphertext;
- encryption keys or key versions with sensitive metadata;
- HKID/passport/identity value;
- raw email/mobile;
- password or confirm password;
- SMS password;
- full SMS/email body if it contains OTP.

Structured logs should include safe values only:

```text
requestId
correlationId
endpoint
scenario
action
eventType
errorCode
logLevel
safeFailureCategory
```

---

## 9. Logging Level and Event Type Contract

Tech-lead required log levels:

```text
Validation/business validation failure: ERROR
System failure: FATAL
```

Event types:

```text
VALIDATION_FAILURE
BUSINESS_REJECTION
SECURITY_REJECTION
RATE_LIMIT_REJECTED
DOWNSTREAM_REJECTION
DOWNSTREAM_FAILURE
INFRASTRUCTURE_FAILURE
ENCRYPTION_FAILURE
MESSAGE_DELIVERY_FAILURE
```

`DOWNSTREAM_REJECTION` means APIM/TAS was reachable and returned a technically valid business/error-message response.

`DOWNSTREAM_FAILURE` means APIM/TAS had technical/availability failure such as timeout, unavailable service, invalid envelope, transport error, token/certificate failure, or encryption/decryption infrastructure failure.

---

## 10. Captcha Security Binding

Rules:

- `captchaChallengeId` must be high entropy.
- Captcha answer must be stored as hash only.
- Verified captcha is one-time-use.
- `registration/start` must atomically consume captcha when captcha is required.
- Captcha should preferably bind to same origin/browser context or temporary captcha session marker if available.
- Consumed, expired, locked, missing, or invalid captcha must be rejected.

---

## 11. AuthFlowSession Replacement Behavior

Default concurrency:

```text
SINGLE_ACTIVE_FLOW / REPLACE_EXISTING
```

On new `registration/start` success, if an existing `auth_flow_sid` is present:

- mark old AuthFlowSession as `CANCELLED`;
- invalidate active OTP challenge if present;
- issue the new `auth_flow_sid` cookie;
- do not rely on the old AuthFlowSession for later steps.

---

## 12. Rate Limit Runtime Contract

Rate limiting is required for registration and OTP-related auth flows.

Protected operations:

- `registration/start`
- `registration/otp/resend`
- `registration/otp/verify`
- `captcha/verify`
- `captcha/refresh`, if enabled
- `username/check`, if APIM/TAS cost or enumeration risk is material

Recommended dimensions:

- IP address;
- browser/session marker where available;
- AuthFlowSession ID where available;
- normalized destination hash for email/mobile OTP send/resend;
- identity hash for registration/start where approved;
- captchaChallengeId for captcha verification attempts.

Rules:

- Do not store plaintext email, mobile, HKID, passport, username, policy number, or certificate number in rate-limit keys.
- Use opaque IDs or safe hashes only.
- Rate-limit responses must use `MutationResponse.errors[]`.
- Rate-limit messages must come from YAML/config and respect `Accept-Language`.
- Rate-limit events follow tech-lead logging rule: log as `ERROR` with `eventType=RATE_LIMIT_REJECTED`.
- Reaching a rate limit must not reveal whether a member, destination, username, account, policy, or trust exists.
- Rate-limit keys must have TTLs aligned with configured policy windows.

---

## 13. Security Tests

Required tests:

- CSRF missing/invalid rejected;
- Origin/Referer invalid rejected;
- lost CSRF cannot call cancel;
- old AuthFlowSession replaced and old OTP invalidated on new start;
- raw sensitive data not logged;
- raw sensitive data not stored in Redis keys;
- captcha challenge cannot be reused after consume;
- rate-limit response does not reveal account/member existence.
