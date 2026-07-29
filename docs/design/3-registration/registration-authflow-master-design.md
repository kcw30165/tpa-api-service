# Registration AuthFlow Master Design

## 1. Purpose

This master document summarizes the finalized registration and pre-auth session-management design.

Detailed executable contracts are split into companion documents:

```text
registration-state-contract.md
authflow-session-security-contract.md
registration-api-contract.md
registration-apim-contract.md
registration-error-matrix.md
registration-implementation-checklist.md
```

This document should remain a readable architecture baseline and decision record. Detailed schemas, matrices, and task checklists should live in the companion documents.

---

## 2. Scope

This design covers:

- registration flow;
- lightweight workflow foundation;
- AuthFlowSession pre-auth session lifecycle;
- captcha;
- OTP resend and verify;
- username check;
- registration complete;
- multi-trust continuation;
- idempotency;
- page-specific downstream error classification;
- APIM/TAS integration boundary;
- logging severity and event classification.

Current checkpoint:

```text
P0-P8 implemented
P9 Username Check
P10 Registration Complete
```

---

## 3. Boundary Decisions

Java BFF is an orchestration layer between Angular FE and APIM/TAS.

Java owns:

- workflow/session orchestration;
- AuthFlowSession lifecycle;
- CSRF validation;
- cookie handling;
- request-shape validation;
- idempotency;
- rate limiting;
- OTP challenge orchestration;
- captcha challenge orchestration;
- message delivery orchestration;
- protected session context;
- MutationResponse mapping;
- bff-pages DSL response composition;
- logging event classification.

APIM/TAS owns:

- member existence;
- identity/contact matching;
- registration eligibility;
- not-yet-registered trust/account lookup;
- username availability;
- credential setup;
- already-registered rejection;
- final registration outcome.

Java must not reimplement APIM/TAS business decisions.

---

## 4. Session Model

Session Management is a reusable container capability.

```text
Session Management
├── AuthFlowSession        // pre-auth temporary workflow session
└── AuthenticatedSession   // future post-login real authenticated session
```

Rules:

- AuthFlowSession is pre-auth only.
- AuthFlowSession must never grant portal access.
- AuthFlowSession must not be mutated into AuthenticatedSession.
- Future post-login workflows use AuthenticatedSession / PortalAccessContext, not AuthFlowSession.

---

## 5. AuthFlowSession Cookie and CSRF

Cookie:

```http
Set-Cookie: auth_flow_sid=afs_xxx; HttpOnly; Secure; SameSite=None; Path=/api/v1/auth; Max-Age=1800
```

Rules:

- All AuthFlowSession-protected endpoints must be under `/api/v1/auth`, unless cookie path is changed.
- Cross-site FE/BFF mode uses body-delivered CSRF token.
- CSRF token is created at `registration/start` and remains stable for AuthFlowSession lifetime unless explicitly returned as `result.csrfToken` later.
- FE stores CSRF token in memory and sends `X-XSRF-TOKEN` for protected POSTs.
- `auth/flow/resume` is out of current BRD scope.
- If FE refreshes and loses CSRF, FE restarts registration.

---

## 6. Registration Flow Summary

### 6.1 Start

- FE submits identity, DOB, and exactly one contact field.
- Captcha is required if policy enables it.
- Java validates request shape.
- Java calls APIM/TAS `TRPSearchUnregisteredMember`.
- On APIM/TAS success, Java creates AuthFlowSession, stores protected registration context, creates OTP, sends OTP, sets cookie, and returns CSRF token.
- Java does not return APIM/TAS `data[]` to FE from start.

### 6.2 OTP

- FE verifies OTP using `otp/verify`.
- Java uses server-side `activeOtpChallengeId`.
- On success, Java calls APIM/TAS again to get latest not-yet-registered trusts.
- One trust: next step `USERNAME_CHECK`.
- Multiple trusts: next step `SELECT_TRUST`.

### 6.3 Username Check

- FE sends selectedTrust and username.
- Java checks username availability through APIM/TAS.
- On success, Java stores selectedTrust + username in `usernameCheck` and transitions to `USERNAME_VERIFIED`.
- Username check is not action-idempotent because username availability is first-come, first-served.

### 6.4 Complete

- FE sends password and confirmPassword only.
- Java loads selectedTrust + username from AuthFlowSession.
- Java calls APIM/TAS registration completion.
- On success with no remaining trusts: transition to `COMPLETED` and return noRemainingTrusts success page.
- On success with remaining trusts: transition to `TRUST_SELECTION_READY`, clear usernameCheck, return hasRemainingTrusts success page and trust list for immediate FE rendering.

---

## 7. Multi-Trust Continuation

If more than one not-yet-registered trust remains, the same verified AuthFlowSession can continue to trust selection without repeating captcha, registration/start, or OTP, while session remains valid.

State sequence:

```text
OTP_SENT
  -> OTP_VERIFIED
  -> USERNAME_VERIFIED
  -> COMPLETING
  -> TRUST_SELECTION_READY
  -> USERNAME_VERIFIED
  -> COMPLETING
  -> TRUST_SELECTION_READY
  -> USERNAME_VERIFIED
  -> COMPLETING
  -> COMPLETED
```

Rules:

- `TRUST_SELECTION_READY` means previous trust registration succeeded and the user may choose another trust.
- On entering `TRUST_SELECTION_READY`, Java clears previous `usernameCheck` and selected trust.
- `AuthFlowSession` stores only the current registration target, not authoritative long-term remaining trust state.
- Remaining trust list can be returned to FE for immediate rendering.
- APIM/TAS remains the source of truth for current trust/account registration state.

---

## 8. Protected Registration Context

The current implementation stores registration context using protected encrypted session context.

Rules:

- Reuse this existing protected context approach.
- Do not invent another storage mechanism for identity/contact continuation data.
- Raw identity/contact values must not be stored as plaintext Redis fields.
- Ciphertext and key material must not be logged.

---

## 9. Downstream Error Classification

APIM/TAS business/error-message responses are classified as:

```text
eventType=DOWNSTREAM_REJECTION
logLevel=ERROR
```

APIM/TAS technical/availability failures are classified as:

```text
eventType=DOWNSTREAM_FAILURE
logLevel=FATAL
```

Downstream error classification must be page/step-specific through bff-pages DSL where possible.

Resolution order:

```text
1. Page/step-specific mapping
2. Scenario-level mapping
3. Global fallback
4. Generic system/downstream fallback
```

Java must not hardcode APIM/TAS `err-message` classification except wiring to the resolver.

---

## 10. Logging Contract

Tech-lead required log levels:

```text
Validation/business validation failure: ERROR
System failure: FATAL
```

Structured event types:

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

If native `FATAL` logging is unavailable, log at `ERROR` with structured `severity=FATAL`.

---

## 11. Idempotency Contract

Action-scoped idempotency is required for:

```text
registration/start
otp/resend
otp/verify
registration/complete
```

Rules:

- Same idempotency key with different requestHash is rejected.
- Java request-shape validation failures may be snapshotted.
- APIM/TAS business rejections are not cacheable by default because downstream truth may change.
- Page DSL may mark specific downstream rejections cacheable.
- Username unavailable/taken may be cacheable for short TTL if configured.
- `otp/verify` replay must not re-run OTP verification or increment counters.
- `otp/resend` replay must not create/send another OTP.
- APIM/TAS timeout after request sent is `DOWNSTREAM_FAILURE` for logging and `UNKNOWN_OUTCOME` for idempotency safety.

---

## 12. Captcha Contract

Captcha is confirmed.

Rules:

- challenge/verify/refresh endpoints exist;
- challenge ID must be high entropy;
- answer is stored as hash only;
- verified captcha is one-time-use;
- registration/start atomically consumes verified captcha;
- captcha invalid/expired/locked/consumed errors are security rejections.

---

## 13. Rate Limit Contract

Rate limiting is required for registration and OTP-related auth flows.

Protected operations include:

- registration/start;
- OTP resend;
- OTP verify;
- captcha verify;
- captcha refresh if enabled;
- username check if APIM/TAS cost or enumeration risk is material.

Rate-limit keys must use safe hashes or opaque IDs only. Rate-limit rejection logs as `ERROR` with `eventType=RATE_LIMIT_REJECTED`.

---

## 14. Implementation Documents

Use these documents for detailed implementation:

- `registration-state-contract.md` for runtime state shapes and transitions.
- `authflow-session-security-contract.md` for cookie/CSRF/security/rate-limit rules.
- `registration-api-contract.md` for FE-facing endpoint contracts.
- `registration-apim-contract.md` for APIM/TAS mapping and downstream classification.
- `registration-error-matrix.md` for failure behavior.
- `registration-implementation-checklist.md` for P9/P10 tasks and tests.
