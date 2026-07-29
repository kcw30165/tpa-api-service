# Registration Start Idempotency Payload Change Addendum

## Purpose

This addendum clarifies what happens when `registration/start` returns a Java-side `VALIDATION_FAILURE` and FE changes the request payload before reposting.

This content should be merged into:

- `registration-api-contract.md`
- `registration-state-contract.md`
- `registration-error-matrix.md`
- `registration-implementation-checklist.md`
- `registration-authflow-master-design.md`

---

## 1. Core Rule

For `registration/start`, FE must generate a new `Idempotency-Key` whenever the request payload changes after a validation failure.

FE should reuse the same `Idempotency-Key` only for:

- duplicate click;
- browser retry;
- network retry;
- same submit intent with the same unchanged payload.

If the user changes the payload, it is a new submit intent and requires a new `Idempotency-Key`.

---

## 2. Payload Fields That Require a New Key

FE must generate a new `Idempotency-Key` when any of these fields changes:

- `identityType`;
- `identityValue`;
- `dateOfBirth`;
- `email`;
- `mobile`;
- `captchaChallengeId`, if captcha is enabled.

---

## 3. Backend Request Hash Rule

Backend computes `requestHash` from the normalized registration-start payload.

The hash must include:

- normalized `identityType`;
- normalized `identityValue`;
- `dateOfBirth`;
- submitted contact, meaning `email` or `mobile`;
- `captchaChallengeId`, if captcha is enabled.

---

## 4. Backend Behavior

### 4.1 Same Key + Same Payload

If FE sends the same `Idempotency-Key` with the same `requestHash`, backend may return:

- previous response snapshot; or
- duplicate-in-progress response, depending on current idempotency state.

### 4.2 Same Key + Changed Payload

If FE sends the same `Idempotency-Key` with a different `requestHash`, backend must reject the request as idempotency key reuse.

No new registration-start attempt should be executed.

No AuthFlowSession should be created.

No OTP should be generated.

No APIM/TAS call should be made for the changed payload.

### 4.3 New Key + Corrected Payload

If FE sends a new `Idempotency-Key` with the corrected payload, backend treats it as a new registration-start attempt.

---

## 5. Example

### First Submit

FE sends invalid DOB format with key `K1`:

```json
{
  "identityType": "HKID",
  "identityValue": "A1234567",
  "dateOfBirth": "1980-01-15",
  "email": null,
  "mobile": "91234567"
}
```

Backend computes:

```text
requestHash = H1
```

Backend returns `VALIDATION_FAILURE`.

### Corrected Submit

User fixes DOB:

```json
{
  "identityType": "HKID",
  "identityValue": "A1234567",
  "dateOfBirth": "15/01/1980",
  "email": null,
  "mobile": "91234567"
}
```

FE must send a new key, for example `K2`.

Backend computes:

```text
requestHash = H2
```

Backend treats `K2 + H2` as a new registration-start attempt.

If FE incorrectly sends `K1 + H2`, backend must reject it as idempotency key reuse.

---

## 6. Error Matrix Addition

Add this row to the `Registration Start` section of `registration-error-matrix.md`:

| Condition | Response category | Event type | Log level | State impact | Idempotency behavior |
|---|---|---|---|---|---|
| same Idempotency-Key reused with different registration-start payload | idempotency reuse error | VALIDATION_FAILURE | ERROR | no AuthFlowSession created; no new attempt executed | reject key reuse |

---

## 7. Implementation Checklist Addition

Add these checks to idempotency hardening:

- For `registration/start`, changed payload after validation failure requires a new FE `Idempotency-Key`.
- Same key + same payload may replay previous response according to idempotency status.
- Same key + changed payload must be rejected.
- New key + corrected payload must be processed as a new attempt.
- Rejecting same key + changed payload must not create AuthFlowSession, OTP, or APIM/TAS call.
