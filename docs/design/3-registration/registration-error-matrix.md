# Registration Error Matrix

## 1. Purpose

Single source of truth for registration failure behavior.

This document defines:

- response category;
- event type;
- log level;
- state impact;
- idempotency behavior;
- FE behavior guidance.

Tech-lead log-level requirement:

```text
Validation/business validation failure: ERROR
System failure: FATAL
```

---

## 2. Event Types

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

Definitions:

- `VALIDATION_FAILURE`: Java request-shape/config validation failed.
- `BUSINESS_REJECTION`: Java-owned business rule rejected request.
- `SECURITY_REJECTION`: CSRF, Origin/Referer, captcha, or suspicious request rejected.
- `RATE_LIMIT_REJECTED`: configured rate limit exceeded.
- `DOWNSTREAM_REJECTION`: APIM/TAS was reachable and returned a valid business/error-message response.
- `DOWNSTREAM_FAILURE`: APIM/TAS technical or availability failure.
- `INFRASTRUCTURE_FAILURE`: Redis/session/idempotency/config infrastructure failure.
- `ENCRYPTION_FAILURE`: protected context encryption/decryption failed.
- `MESSAGE_DELIVERY_FAILURE`: SMS/email delivery failure.

---

## 3. Idempotency Behavior Categories

```text
SNAPSHOT_CACHEABLE
NON_CACHEABLE_REJECTION
RETRYABLE_FAILED
UNKNOWN_OUTCOME
NO_IDEMPOTENCY
```

Rules:

- Java request-shape validation failures may be `SNAPSHOT_CACHEABLE`.
- APIM/TAS business rejections are `NON_CACHEABLE_REJECTION` by default unless page DSL marks them cacheable.
- Username taken/unavailable may be cacheable for short TTL if configured.
- Technical failure before side effect starts may be `RETRYABLE_FAILED`.
- Technical failure after side effect may have started is `UNKNOWN_OUTCOME`.
- Same idempotency key with different request hash is always rejected.

---

## 4. Registration Start

| Condition | Response category | Event type | Log level | State impact | Idempotency behavior |
|---|---|---|---|---|---|
| identityType missing/invalid | validation error | VALIDATION_FAILURE | ERROR | no AuthFlowSession created | SNAPSHOT_CACHEABLE |
| identityValue invalid | validation error | VALIDATION_FAILURE | ERROR | no AuthFlowSession created | SNAPSHOT_CACHEABLE |
| DOB missing/invalid format | validation error | VALIDATION_FAILURE | ERROR | no AuthFlowSession created | SNAPSHOT_CACHEABLE |
| both email and mobile missing | validation error | VALIDATION_FAILURE | ERROR | no AuthFlowSession created | SNAPSHOT_CACHEABLE |
| both email and mobile provided | validation error | VALIDATION_FAILURE | ERROR | no AuthFlowSession created | SNAPSHOT_CACHEABLE |
| email format invalid | validation error | VALIDATION_FAILURE | ERROR | no AuthFlowSession created | SNAPSHOT_CACHEABLE |
| mobile format invalid | validation error | VALIDATION_FAILURE | ERROR | no AuthFlowSession created | SNAPSHOT_CACHEABLE |
| captcha missing when required | validation/security error | SECURITY_REJECTION | ERROR | no AuthFlowSession created | SNAPSHOT_CACHEABLE |
| captcha invalid/expired/locked/consumed | validation/security error | SECURITY_REJECTION | ERROR | no AuthFlowSession created | SNAPSHOT_CACHEABLE |
| rate limit exceeded | validation/security error | RATE_LIMIT_REJECTED | ERROR | no AuthFlowSession created | NON_CACHEABLE_REJECTION |
| APIM/TAS `err.tpa.member.not.found` | verification failure | DOWNSTREAM_REJECTION | ERROR | no AuthFlowSession created | NON_CACHEABLE_REJECTION by default |
| APIM/TAS business error-message | business failure | DOWNSTREAM_REJECTION | ERROR | no AuthFlowSession created unless explicitly designed otherwise | page DSL decides cacheability |
| APIM/TAS unavailable/timeout before reliable response | system error | DOWNSTREAM_FAILURE | FATAL | no reliable progression | RETRYABLE_FAILED or UNKNOWN_OUTCOME depending on whether request was sent |
| Redis unavailable | system error | INFRASTRUCTURE_FAILURE | FATAL | operation failed | RETRYABLE_FAILED |
| destination encryption failure | system error | ENCRYPTION_FAILURE | FATAL | AuthFlowSession must not proceed | RETRYABLE_FAILED |
| OTP creation failure | system error | INFRASTRUCTURE_FAILURE | FATAL | AuthFlowSession must not proceed to OTP_SENT | RETRYABLE_FAILED |
| SMS/email gateway unavailable | system/downstream error | MESSAGE_DELIVERY_FAILURE | FATAL | OTP may be created but delivery failed/unknown | UNKNOWN_OUTCOME or delivery-specific failure |
| message delivery business rejection | delivery failure | MESSAGE_DELIVERY_FAILURE | ERROR | OTP delivery failed | NON_CACHEABLE_REJECTION |

---

## 5. OTP Resend

| Condition | Response category | Event type | Log level | State impact | Idempotency behavior |
|---|---|---|---|---|---|
| missing auth_flow_sid | validation/security error | SECURITY_REJECTION | ERROR | no change | SNAPSHOT_CACHEABLE |
| invalid CSRF | validation/security error | SECURITY_REJECTION | ERROR | no change | SNAPSHOT_CACHEABLE |
| AuthFlowSession missing/expired | validation/session error | SECURITY_REJECTION | ERROR | no change | NON_CACHEABLE_REJECTION |
| state not OTP_SENT/OTP_VERIFIED as allowed | validation/workflow error | VALIDATION_FAILURE | ERROR | no change | SNAPSHOT_CACHEABLE |
| cooldown not elapsed | validation/business error | BUSINESS_REJECTION | ERROR | no new OTP | NON_CACHEABLE_REJECTION |
| max resend attempts reached | validation/business error | BUSINESS_REJECTION | ERROR | no new OTP | NON_CACHEABLE_REJECTION |
| protected context decrypt failed | system error | ENCRYPTION_FAILURE | FATAL | no safe resend | UNKNOWN_OUTCOME if partial side effect occurred |
| old OTP invalidated and new OTP created | success side effect | n/a | n/a | activeOtpChallengeId updated | replay must return snapshot |
| message delivery unavailable/timeout | system/delivery error | MESSAGE_DELIVERY_FAILURE | FATAL | replacement OTP outcome may be uncertain | UNKNOWN_OUTCOME or delivery-specific failure |

Required replay rule:

```text
Same idempotency key + same requestHash must not generate another OTP, invalidate another OTP, send another message, or increment resend counters again.
```

---

## 6. OTP Verify

| Condition | Response category | Event type | Log level | State impact | Idempotency behavior |
|---|---|---|---|---|---|
| missing auth_flow_sid | validation/security error | SECURITY_REJECTION | ERROR | no change | SNAPSHOT_CACHEABLE |
| invalid CSRF | validation/security error | SECURITY_REJECTION | ERROR | no change | SNAPSHOT_CACHEABLE |
| OTP missing/format invalid | validation error | VALIDATION_FAILURE | ERROR | no OTP verification | SNAPSHOT_CACHEABLE |
| OTP wrong | business/security error | BUSINESS_REJECTION | ERROR | failedAttemptCount increments once | snapshot for same replay only |
| OTP expired | business/security error | BUSINESS_REJECTION | ERROR | no session progression | NON_CACHEABLE_REJECTION |
| OTP locked | business/security error | BUSINESS_REJECTION | ERROR | no session progression | NON_CACHEABLE_REJECTION |
| OTP correct | success | n/a | n/a | session -> OTP_VERIFIED | response snapshot for replay |
| APIM/TAS trust lookup business rejection | business failure | DOWNSTREAM_REJECTION | ERROR | OTP may be verified; no trust progression | page DSL decides cacheability |
| APIM/TAS trust lookup unavailable/timeout | system error | DOWNSTREAM_FAILURE | FATAL | OTP may be verified; trust lookup unknown | UNKNOWN_OUTCOME if downstream request was sent |

Required replay rule:

```text
For otp/verify, an idempotency replay with the same requestHash must return the original response snapshot and must not re-run OTP verification or increment attempt counters.
```

---

## 7. Username Check

`username/check` is not action-idempotent because username availability is first-come, first-served.

| Condition | Response category | Event type | Log level | State impact | Idempotency behavior |
|---|---|---|---|---|---|
| missing auth_flow_sid | validation/security error | SECURITY_REJECTION | ERROR | no change | NO_IDEMPOTENCY |
| invalid CSRF | validation/security error | SECURITY_REJECTION | ERROR | no change | NO_IDEMPOTENCY |
| state not OTP_VERIFIED/TRUST_SELECTION_READY/allowed USERNAME_VERIFIED repeat | workflow error | VALIDATION_FAILURE | ERROR | no change | NO_IDEMPOTENCY |
| selectedTrust invalid shape | validation error | VALIDATION_FAILURE | ERROR | no change | NO_IDEMPOTENCY |
| username invalid format | validation error | VALIDATION_FAILURE | ERROR | no change | NO_IDEMPOTENCY |
| username unavailable/taken | business/downstream rejection | DOWNSTREAM_REJECTION | ERROR | no usernameCheck stored | cacheable short TTL if page DSL says so |
| username available | success | n/a | n/a | store usernameCheck, session -> USERNAME_VERIFIED | NO_IDEMPOTENCY |
| APIM/TAS unavailable/timeout | system error | DOWNSTREAM_FAILURE | FATAL | no reliable update | NO_IDEMPOTENCY |

Repeat behavior:

- Same selectedTrust + username while `USERNAME_VERIFIED`: Java may return success without changing state.
- Different selectedTrust or username before `COMPLETING`: Java should recheck username availability and replace `usernameCheck` on success.
- From `COMPLETING`: reject username mutation.

---

## 8. Registration Complete

| Condition | Response category | Event type | Log level | State impact | Idempotency behavior |
|---|---|---|---|---|---|
| missing auth_flow_sid | validation/security error | SECURITY_REJECTION | ERROR | no change | SNAPSHOT_CACHEABLE |
| invalid CSRF | validation/security error | SECURITY_REJECTION | ERROR | no change | SNAPSHOT_CACHEABLE |
| missing usernameCheck | validation/workflow error | VALIDATION_FAILURE | ERROR | no complete | SNAPSHOT_CACHEABLE |
| state not USERNAME_VERIFIED | validation/workflow error | VALIDATION_FAILURE | ERROR | no complete | SNAPSHOT_CACHEABLE |
| password invalid | validation error | VALIDATION_FAILURE | ERROR | no APIM/TAS call | SNAPSHOT_CACHEABLE |
| confirmPassword mismatch | validation error | VALIDATION_FAILURE | ERROR | no APIM/TAS call | SNAPSHOT_CACHEABLE |
| APIM/TAS username already taken | downstream rejection | DOWNSTREAM_REJECTION | ERROR | no success page | cacheable short TTL if DSL says so |
| APIM/TAS registration business failure | downstream rejection | DOWNSTREAM_REJECTION | ERROR | no success page | page DSL decides cacheability |
| APIM/TAS timeout after request sent | system error | DOWNSTREAM_FAILURE | FATAL | outcome unknown | UNKNOWN_OUTCOME |
| success, no remaining trusts | success | n/a | n/a | COMPLETING -> COMPLETED | response snapshot for replay |
| success, remaining trusts exist | success | n/a | n/a | COMPLETING -> TRUST_SELECTION_READY; clear usernameCheck | response snapshot for replay |

FE must not auto-submit a new idempotency key after `UNKNOWN_OUTCOME` for registration complete. The user should follow approved UX such as retry with same key, return to login, or contact support.

---

## 9. Cancel

| Condition | Response category | Event type | Log level | State impact | Idempotency behavior |
|---|---|---|---|---|---|
| missing auth_flow_sid | validation/security error | SECURITY_REJECTION | ERROR | no change | NO_IDEMPOTENCY |
| invalid CSRF | validation/security error | SECURITY_REJECTION | ERROR | no change | NO_IDEMPOTENCY |
| valid cancel | success | n/a | n/a | session -> CANCELLED, cookies cleared | NO_IDEMPOTENCY |
| FE lost CSRF | n/a | n/a | n/a | FE must not call cancel; old session expires/replaced | NO_IDEMPOTENCY |
