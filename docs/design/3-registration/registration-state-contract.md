# Registration State Contract

## 1. Purpose

This document is the single source of truth for registration-related runtime state.

It defines:

- Redis/session value shapes for registration auth flow state.
- Captcha challenge state.
- OTP challenge state.
- Idempotency record state.
- Registration workflow status transitions.
- Sensitive-data storage rules.
- Current selected-trust / username-check ownership rules.

This document is intentionally state-focused. Endpoint request/response contracts, APIM/TAS mapping, and detailed error behavior are documented separately.

---

## 2. Core State Ownership Principles

Java BFF owns only temporary workflow state. APIM/TAS remains the source of truth for:

- member existence;
- identity/contact matching;
- registration eligibility;
- not-yet-registered trust/account status;
- username availability;
- final credential registration result.

`AuthFlowSession` must not be treated as proof of login. It is a pre-auth workflow session only.

---

## 3. Redis Key Families

Use opaque IDs and safe hashes only. Do not put PII in Redis keys.

```text
{prefix}:auth:flow-session:{authFlowSessionId}
{prefix}:auth:captcha:{captchaChallengeId}
{prefix}:auth:otp:{otpChallengeId}
{prefix}:auth:idempotency:{action}:{idempotencyKey}
{prefix}:auth:rate:{rateLimitBucket}:{opaqueBucketId}
```

Forbidden in Redis keys:

- HKID;
- passport number;
- mobile number;
- email address;
- policy number;
- certificate number;
- username;
- raw OTP;
- raw CSRF token;
- raw session ID in logs.

---

## 4. Captcha Challenge State

### 4.1 Implemented Shape

```json
{
  "captchaChallengeId": "cap_3fb1df17f3e546fe95b45464a9e30961",
  "answerHash": "a60ac34627b8e83348d40d91d35f38fc573b885c9cd91588544e4b4e951f771a",
  "status": "CREATED",
  "failedAttemptCount": 0,
  "maxAttempts": 5,
  "createdAt": "2026-07-09T10:48:51.743568338Z",
  "expiresAt": "2026-07-09T10:53:51.743568338Z",
  "verifiedAt": null,
  "consumedAt": null,
  "refreshedAt": null,
  "lockedAt": null
}
```

### 4.2 Required Status Values

```text
CREATED
VERIFIED
CONSUMED
REFRESHED
EXPIRED
LOCKED
```

### 4.3 Runtime Rules

- `captchaChallengeId` must be high entropy and unguessable.
- `answerHash` stores a safe hash only; raw answer must not be persisted or logged.
- A verified captcha is one-time-use.
- `registration/start` must atomically consume the verified captcha when captcha is required.
- A consumed, expired, locked, or already-used captcha must be rejected.
- Captcha should preferably be bound to the same origin/browser context or temporary captcha session marker if available.
- Current implemented schema is sufficient for the P8 server-side lifecycle. Future hardening may add a browser/session/origin binding hash if required.

---

## 5. AuthFlowSession State

### 5.1 Implemented Shape

```json
{
  "authFlowSessionId": "afs_e6_5-kPD3tRAoHCi23AZWQ",
  "scenario": "REGISTRATION",
  "status": "OTP_VERIFIED",
  "csrfTokenHash": "6e1f033653834abb2f181eff363f388ceed5ef11ee8646f956c585722dc248af",
  "activeOtpChallengeId": "otp_d9fef46044aa48bab4bfaae9623679fa",
  "data": {
    "contactType": "EMAIL",
    "maskedDestination": "d****k@bcthk.com",
    "usernameCheck": null,
    "attributes": {
      "registrationContextCiphertext": "IGBTS7dp2BY1ofRpvQLWanI36fxrrwF6WsbqgZmyoWUgc307fG4OPU2YxnhMNPNqJ2tOVjH1Avd-_dPm5F3eOTV6Qg65NtAaI9D_GldJmyurk-s9yHV-uKIa5_Uh_8vR_nDYqRMACzzQt7TKHF7BJ_6NeM2KiLb_wWRQm1eSVf98ktBewnC0sSuMZr7XKTlJCydRKyLrEoCxcihGGGfDSlHDW1yQwSw",
      "registrationContextKeyVersion": "dev-v1",
      "registrationContextAlgorithm": "AES_GCM_V1"
    }
  },
  "createdAt": "2026-07-09T03:24:00.896497098Z",
  "updatedAt": "2026-07-09T03:26:18.992337502Z",
  "expiresAt": "2026-07-09T03:54:00.896497098Z"
}
```

### 5.2 Scenario Values

Current supported scenarios:

```text
REGISTRATION
LOGIN
FORGOT_PASSWORD
USERNAME_RECOVERY
```

This document covers `REGISTRATION` only.

### 5.3 Registration Status Values

```text
STARTED
OTP_SENT
OTP_VERIFIED
TRUST_SELECTION_READY
USERNAME_VERIFIED
COMPLETING
COMPLETED
CANCELLED
EXPIRED
```

`TRUST_SELECTION_READY` is used when one trust registration has completed successfully but at least one unregistered trust remains and the same verified registration flow may continue to another trust.

---

## 6. Registration State Transitions

### 6.1 Standard Single-Trust Flow

```text
STARTED
  -> OTP_SENT
  -> OTP_VERIFIED
  -> USERNAME_VERIFIED
  -> COMPLETING
  -> COMPLETED
```

### 6.2 Multi-Trust Continuation Flow

When more than one not-yet-registered trust remains, the valid continuation loop is:

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

### 6.3 Transition Rules

```text
OTP_SENT -> OTP_VERIFIED
```

Allowed when OTP verification succeeds.

```text
OTP_VERIFIED -> USERNAME_VERIFIED
```

Allowed when `username/check` succeeds for selected trust and username.

```text
TRUST_SELECTION_READY -> USERNAME_VERIFIED
```

Allowed when user selects another trust and `username/check` succeeds.

```text
USERNAME_VERIFIED -> COMPLETING
```

Allowed when `registration/complete` starts.

```text
COMPLETING -> TRUST_SELECTION_READY
```

Allowed when `registration/complete` succeeds and remaining unregistered trusts exist.

```text
COMPLETING -> COMPLETED
```

Allowed when `registration/complete` succeeds and no remaining unregistered trusts exist.

```text
Any active state -> CANCELLED
```

Allowed when user cancels and CSRF validation passes.

```text
Any active state -> EXPIRED
```

Allowed when Redis TTL expires.

### 6.4 Mutation Guard

Once state is `COMPLETING`, username mutation is not allowed.

---

## 7. Current Registration Target State

`AuthFlowSession` should keep only the current in-progress registration target, not authoritative long-term account/trust state.

### 7.1 `selectedTrust` Ownership

`selectedTrust` is absent before successful `username/check`.

Recommended canonical representation:

```json
{
  "usernameCheck": {
    "selectedTrust": {
      "trustCode": "TL",
      "accountEnv": "ENV1"
    },
    "username": "abc1@",
    "checkedAt": "2026-07-09T10:00:00Z",
    "expiresAt": "2026-07-09T10:30:00Z"
  }
}
```

There is no need for a separate top-level `selectedTrust` field if selected trust is stored inside `usernameCheck`.

### 7.2 Clearing Rule

On `COMPLETING -> TRUST_SELECTION_READY`:

- clear previous `usernameCheck`;
- clear previous selected trust if stored as a separate field;
- keep the AuthFlowSession active;
- keep protected registration context;
- keep OTP-verified proof within the current AuthFlowSession;
- do not keep stale current trust or stale username.

### 7.3 Remaining Trust List Rule

`remainingUnregisteredTrusts` may be returned to FE for immediate rendering after `registration/complete` succeeds.

Java should not treat the returned remaining trust list as authoritative long-term session state because the list may change externally.

When FE sends a selected trust to `username/check`, Java should validate the selected trust using APIM/TAS or a fresh downstream lookup when required by the use case.

---

## 8. Protected Registration Context

The current implementation stores the full registration context as encrypted protected session context through the registration session creation flow.

Example creation concept:

```java
RegistrationAuthFlowContext registrationContext = new RegistrationAuthFlowContext(
        validated.identityType(),
        validated.identityValue(),
        validated.dateOfBirth(),
        validated.contactType(),
        validated.contactValue());
return sessionGateway.createRegistrationSession(
        validated.maskedDestination(),
        validated.contactType(),
        registrationContext);
```

The encrypted fields are stored under `data.attributes`:

```json
{
  "attributes": {
    "registrationContextCiphertext": "enc_xxx",
    "registrationContextKeyVersion": "dev-v1",
    "registrationContextAlgorithm": "AES_GCM_V1"
  }
}
```

### 8.1 Rules

- Do not invent a second storage mechanism for identity/contact continuation data.
- Reuse the existing protected session context approach.
- Raw `identityValue`, `dateOfBirth`, `email`, and `mobile` must not be stored as normal plaintext Redis fields.
- Protected context ciphertext, key material, raw destination, identity values, OTP, and CSRF token must never be logged.
- `maskedDestination` may be stored separately for display only.

---

## 9. OTP Challenge State

### 9.1 Implemented Shape

```json
{
  "otpChallengeId": "otp_11612c881db448938ca0cdceeda9c380",
  "scenario": "REGISTRATION",
  "workflowRef": "registration:ef4acb7683ab85cb07c9cc8a47f9db627df46476973f460fa364e50e16c47de3",
  "channel": "EMAIL",
  "destinationHash": "ef4acb7683ab85cb07c9cc8a47f9db627df46476973f460fa364e50e16c47de3",
  "codeHash": "57f567a02e69d198fc8e75f5f55c737f937967e2bebd374e445d9e2f8e42f33a",
  "status": "CREATED",
  "failedAttemptCount": 0,
  "maxVerifyAttempts": 5,
  "resendAttemptCount": 0,
  "maxResendAttempts": 5,
  "createdAt": "2026-07-09T09:20:47.044210801Z",
  "expiresAt": "2026-07-09T09:25:47.044210801Z",
  "lastSentAt": "2026-07-09T09:20:47.044210801Z",
  "invalidatedAt": null,
  "verifiedAt": null
}
```

### 9.2 Runtime Rules

- `otpChallengeId` is never returned to FE.
- FE does not send OTP challenge ID for resend or verify.
- Java loads `activeOtpChallengeId` from AuthFlowSession.
- `otp/resend` creates a replacement OTP challenge, invalidates the previous challenge, and updates `activeOtpChallengeId`.
- `otp/verify` must not increment failed-attempt counters more than once for the same idempotency replay.
- Raw OTP code must never be stored or logged.
- `codeHash` and `destinationHash` must not be reversible.

Optional future additions:

```text
invalidatedReason
lockedAt
lastFailedAttemptAt
```

---

## 10. Idempotency Record State

### 10.1 Implemented Shape

```json
{
  "action": "REGISTRATION_START",
  "idempotencyKey": "a3aaf825-b22f-405d-a89c-dc1d719d7967",
  "requestHash": "e143a70a125c59fc1ed37b6578f5db19952eb822f5dc75fba0750b85fd2b3cbe",
  "status": "FAILED",
  "responseSnapshot": null,
  "createdAt": "2026-07-09T10:50:30.388683705Z",
  "updatedAt": "2026-07-09T10:50:30.731025669Z",
  "expiresAt": "2026-07-09T10:55:30.731025669Z"
}
```

### 10.2 Required Actions

```text
REGISTRATION_START
OTP_RESEND
OTP_VERIFY
REGISTRATION_COMPLETE
```

### 10.3 Target Status Model

Current implementation may use `FAILED`, but the target contract should distinguish safety semantics:

```text
PROCESSING
COMPLETED
RETRYABLE_FAILED
BUSINESS_REJECTED_NON_CACHEABLE
UNKNOWN_OUTCOME
```

### 10.4 Snapshot Rules

- Java request-shape validation failures may store response snapshots because outcome is deterministic for the same payload/config.
- APIM/TAS business rejections must not be cached by default because downstream truth may change within the session lifecycle.
- Page DSL may mark specific downstream rejections as cacheable for short TTL.
- Username unavailable/taken may be cacheable for username-check and registration-complete flows if configured.
- Same idempotency key with different request hash must be rejected.
- Replays for `otp/verify` with the same request hash must not re-run OTP verification or increment attempt counters.

---

## 11. State Contract Tests

Required tests:

- no plaintext identity/contact values in AuthFlowSession Redis value except masked display fields;
- protected registration context ciphertext exists after `registration/start` success;
- `usernameCheck` stores selected trust and username only after successful `username/check`;
- `COMPLETING -> TRUST_SELECTION_READY` clears `usernameCheck`;
- `COMPLETING -> COMPLETED` marks final completion;
- `username/check` is allowed from `OTP_VERIFIED` and `TRUST_SELECTION_READY`;
- `username/check` is rejected from `COMPLETING` and `COMPLETED`;
- OTP resend replaces active OTP challenge and invalidates old OTP challenge;
- OTP verify idempotency replay does not increment failed attempts;
- captcha consume is atomic and one-time-use.
