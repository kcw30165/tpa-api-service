# Registration API Contract

## 1. Purpose

This document defines the FE-facing API contract for the registration auth flow.

All mutation-style endpoints return the project-style `MutationResponse`:

```json
{
  "success": true,
  "status": "SUCCESS",
  "result": {},
  "messages": [],
  "errors": []
}
```

User-facing messages must come from YAML/config/bff-pages and be resolved by `Accept-Language` into one language only.

---

## 2. Common Headers and Cookies

### 2.1 Request Headers

```http
X-Request-Id: req_xxx
Accept-Language: en
Idempotency-Key: uuid-value
X-XSRF-TOKEN: csrf_xxx
```

`Idempotency-Key` is required for:

- `registration/start`
- `registration/otp/resend`
- `registration/otp/verify`
- `registration/complete`

`X-XSRF-TOKEN` is required after AuthFlowSession has been created.

### 2.2 Cookie

```http
Cookie: auth_flow_sid=afs_xxx
```

Required for protected auth-flow requests after `registration/start`.

---

## 3. Endpoint List

### Captcha

```text
POST /api/v1/auth/captcha/challenge
POST /api/v1/auth/captcha/verify
POST /api/v1/auth/captcha/refresh
```

### Registration

```text
POST /api/v1/auth/registration/start
POST /api/v1/auth/registration/username/check
POST /api/v1/auth/registration/complete
POST /api/v1/auth/registration/cancel
```

### Registration OTP

```text
POST /api/v1/auth/registration/otp/resend
POST /api/v1/auth/registration/otp/verify
```

Removed / out of scope:

```text
POST /api/v1/auth/flow/resume
```

---

## 4. Captcha APIs

### 4.1 Challenge

```http
POST /api/v1/auth/captcha/challenge
```

Returns captcha challenge metadata and display payload according to implementation.

Expected result fields may include:

```json
{
    "success": true,
    "status": "SUCCESS",
    "result": {
        "captchaChallengeId": "cap_3fb1df17f3e546fe95b45464a9e30961",
        "imageBase64": "{imageBase64}",
        "mimeType": "image/png",
        "expiresInSeconds": 300
    },
    "messages": [],
    "errors": []
}
```

### 4.2 Verify

```http
POST /api/v1/auth/captcha/verify
```

Request:

```json
{
  "captchaChallengeId": "cap_xxx",
  "answer": "AbC123x"
}
```

Response:

```json
{
    "success": true,
    "status": "SUCCESS",
    "result": {
        "verified": true,
        "captchaChallengeId": "cap_127c3ebd7a384c598969f18b0520dff7",
        "remainingAttempts": 4
    },
    "messages": [],
    "errors": []
}
```

Success marks captcha challenge as `VERIFIED`.

### 4.3 Refresh

```http
POST /api/v1/auth/captcha/refresh
```

Request:

```json
{
  "captchaChallengeId": "cap_xxx"
}
```

Response:

```json
{
    "success": true,
    "status": "SUCCESS",
    "result": {
        "captchaChallengeId": "cap_127c3ebd7a384c598969f18b0520dff7",
        "imageBase64": "{refreshed-imageBase64}",
        "mimeType": "image/png",
        "expiresInSeconds": 300
    },
    "messages": [],
    "errors": []
}
```

---

## 5. Registration Start

```http
POST /api/v1/auth/registration/start
Idempotency-Key: idem_xxx
X-Request-Id: req_xxx
Accept-Language: en
```

### 5.1 Request Without Captcha

```json
{
  "identityType": "GPFS",
  "identityValue": "1234567890",
  "dateOfBirth": "31/01/1980",
  "email": "member@example.com",
  "mobile": null
}
```

### 5.2 Request With Captcha

```json
{
  "identityType": "HKID",
  "identityValue": "A1234567",
  "dateOfBirth": "15/01/1980",
  "email": null,
  "mobile": "91234567",
  "captchaChallengeId": "cap_xxx"
}
```

### 5.3 Rules

- Exactly one of `email` or `mobile` is required.
- FE sends real form fields: `email` and `mobile`.
- Java derives `contactType` and `contactValue`.
- `dateOfBirth` format is `dd/MM/yyyy`.
- `identityType` maps directly to APIM/TAS `search-by` for registration start.
- On success, Java creates AuthFlowSession and OTP challenge.
- AuthFlowSession ID is returned only in HttpOnly cookie, not JSON.
- `csrfToken` is returned in response body.

### 5.4 Success Response

Response header:

```http
Set-Cookie: auth_flow_sid=afs_xxx; HttpOnly; Secure; SameSite=None; Path=/api/v1/auth; Max-Age=1800
```

Response body:

```json
{
    "success": true,
    "status": "SUCCESS",
    "result": {
        "nextStep": "VERIFY_OTP",
        "data": {
            "maskedDestination": "d****k@bcthk.com",
            "expiresInSeconds": 300,
            "resendCooldownSeconds": 60,
            "remainingResendAttempts": 5,
            "csrfToken": "7WS6Tf8gfjJkK2hR5QVu00k2VhI16k0sRQ3aUqmYKLQ"
        },
        "pageRef": {
            "pageId": "registration-otp-verification",
            "variant": null
        }
    },
    "messages": [],
    "errors": []
}
```

---

## 6. OTP Resend

```http
POST /api/v1/auth/registration/otp/resend
Cookie: auth_flow_sid=afs_xxx
X-XSRF-TOKEN: csrf_xxx
Idempotency-Key: idem_xxx
X-Request-Id: req_xxx
Accept-Language: en
```

Request:

```json
{}
```

Success response:

```json
{
    "success": true,
    "status": "SUCCESS",
    "result": {
        "nextStep": "VERIFY_OTP",
        "data": {
            "maskedDestination": "d****k@bcthk.com",
            "expiresInSeconds": 300,
            "resendCooldownSeconds": 60,
            "remainingResendAttempts": 4,
            "retryAfterSeconds": null
        },
        "pageRef": {
            "pageId": "registration-otp-verification",
            "variant": null
        }
    },
    "messages": [],
    "errors": []
}
```

Rules:

- FE does not send OTP challenge ID.
- Java uses `activeOtpChallengeId` from AuthFlowSession.
- Resend decrypts protected registration context / destination context server-side.
- Resend creates replacement OTP, invalidates old OTP, updates `activeOtpChallengeId`, and sends a new message.
- Same idempotency replay must not create another OTP or send another message.

---

## 7. OTP Verify

```http
POST /api/v1/auth/registration/otp/verify
Cookie: auth_flow_sid=afs_xxx
X-XSRF-TOKEN: csrf_xxx
Idempotency-Key: idem_xxx
X-Request-Id: req_xxx
Accept-Language: en
```

Request:

```json
{
  "code": "123456"
}
```

### 7.1 Success When One Trust Exists

```json
{
    "success": true,
    "status": "SUCCESS",
    "result": {
        "nextStep": "USERNAME_CHECK",
        "data": {
            "unregisteredTrusts": [
                {
                    "accountEnv": "DB",
                    "trustCode": "DBA"
                }
            ],
            "remainingVerifyAttempts": null
        },
        "pageRef": {
            "pageId": "registration-username-check",
            "variant": null
        }
    },
    "messages": [],
    "errors": []
}
```

### 7.2 Success When Multiple Trusts Exist

```json
{
    "success": true,
    "status": "SUCCESS",
    "result": {
        "nextStep": "SELECT_TRUST",
        "data": {
            "unregisteredTrusts": [
                {
                    "accountEnv": "DB",
                    "trustCode": "DBA"
                },
                {
                    "accountEnv": "DB",
                    "trustCode": "DBB"
                }
            ],
            "remainingVerifyAttempts": null
        },
        "pageRef": {
            "pageId": "registration-select-trusts",
            "variant": null
        }
    },
    "messages": [],
    "errors": []
}
```

Rules:

- Java verifies OTP using server-side `activeOtpChallengeId`.
- After OTP verification succeeds, Java calls APIM/TAS again to get latest not-yet-registered trust list.
- Idempotency replay with same request hash must return original response and must not re-run OTP verification or increment attempt counters.

---

## 8. Username Check

```http
POST /api/v1/auth/registration/username/check
Cookie: auth_flow_sid=afs_xxx
X-XSRF-TOKEN: csrf_xxx
X-Request-Id: req_xxx
Accept-Language: en
```

Request:

```json
{
  "selectedTrust": {
    "trustCode": "TL",
    "accountEnv": "ENV1"
  },
  "username": "abc1@"
}
```

Success response:

```json
{
  "success": true,
  "status": "SUCCESS",
  "result": {
    "nextStep": "PASSWORD_SETUP"
  },
  "messages": [],
  "errors": []
}
```

Failure response:

```json
{
  "success": false,
  "status": "VALIDATION_FAILED",
  "result": {
    "nextStep": "USERNAME_CHECK"
  },
  "messages": [],
  "errors": [
    {
      "code": "registration.username.incorrect",
      "message": "The username format inputted is incorrect. Please try again.",
      "target": "PAGE"
    }
  ]
}
```

```json
{
  "success": false,
  "status": "DOWNSTREAM_REJECTION",
  "result": {
    "nextStep": "USERNAME_CHECK"
  },
  "messages": [],
  "errors": [
    {
      "code": "registration.username.isregistered",
      "message": "Please try a different username. ",
      "target": "PAGE"
    }
  ]
}
```

Rules:

- `username/check` is not protected by action-scoped idempotency.
- Username availability is first-come, first-served and must be checked against APIM/TAS at request time.
- Allowed states: `OTP_VERIFIED` and `TRUST_SELECTION_READY`.
- If state is `USERNAME_VERIFIED` and same selectedTrust + username is submitted again, Java may return success without changing state.
- If state is `USERNAME_VERIFIED` and a different selectedTrust or username is submitted before `registration/complete` starts, Java should allow replacement by rechecking username availability.
- Once state is `COMPLETING`, username mutation is not allowed.
- On success, Java stores selectedTrust + username in `usernameCheck` and transitions to `USERNAME_VERIFIED`.

---

## 9. Registration Complete

```http
POST /api/v1/auth/registration/complete
Cookie: auth_flow_sid=afs_xxx
X-XSRF-TOKEN: csrf_xxx
Idempotency-Key: idem_xxx
X-Request-Id: req_xxx
Accept-Language: en
```

Request:

```json
{
  "password": "Password1!",
  "confirmPassword": "Password1!"
}
```

Rules:

- Request does not include selectedTrust.
- Java loads selectedTrust + username from `usernameCheck` in AuthFlowSession.
- Java validates password policy before APIM/TAS call.
- Java transitions `USERNAME_VERIFIED -> COMPLETING` when complete starts.
- On success, Java returns success page from bff-pages DSL.

### 9.1 Failure Response Example

```json
{
  "success": false,
  "status": "VALIDATION_FAILED",
  "result": {
    "nextStep": "PASSWORD_SETUP"
  },
  "messages": [],
  "errors": [
    {
      "code": "registration.complete.failed",
      "message": "We are unable to complete your registration. Please review and try again.",
      "target": "PAGE"
    }
  ]
}
```

### 9.2 Success With No Remaining Trusts

```json
{
  "success": true,
  "status": "UPDATED",
  "result": {
    "registered": true,
    "nextStep": "RETURN_TO_LOGIN",
    "unregisteredTrusts": [],
    "successPage": {
      "pageId": "registration-success",
      "variant": "noRemainingTrusts",
      "title": "Successful Registration",
      "body": "Welcome to BCT! Your account is ready. You may now begin your experience. Please return to the login page to sign in to your account.",
      "actions": [
        {
          "id": "returnToLogin",
          "type": "PRIMARY",
          "label": "Return to Login",
          "nextStep": "RETURN_TO_LOGIN"
        }
      ]
    }
  },
  "messages": [
    {
      "type": "SUCCESS",
      "code": "registration.success",
      "message": "Registration completed successfully.",
      "target": "PAGE"
    }
  ],
  "errors": []
}
```

### 9.3 Success With Remaining Trusts

```json
{
  "success": true,
  "status": "UPDATED",
  "result": {
    "registered": true,
    "nextStep": "SELECT_TRUST",
    "unregisteredTrusts": [
      {
        "trustCode": "UB",
        "accountEnv": "ENV2",
        "displayOrder": 10
      }
    ],
    "successPage": {
      "pageId": "registration-success",
      "variant": "hasRemainingTrusts",
      "title": "Successful Registration",
      "body": "Welcome to BCT! Your account is ready. You can continue to register profile for unregistered ORSO schemes or return to the login page to sign in to your account.",
      "actions": [
        {
          "id": "continueRegistration",
          "type": "PRIMARY",
          "label": "Continue Registration for ORSO Schemes",
          "nextStep": "SELECT_TRUST"
        },
        {
          "id": "returnToLogin",
          "type": "SECONDARY",
          "label": "Return to Login",
          "nextStep": "RETURN_TO_LOGIN"
        }
      ]
    }
  },
  "messages": [
    {
      "type": "SUCCESS",
      "code": "registration.success",
      "message": "Registration completed successfully.",
      "target": "PAGE"
    }
  ],
  "errors": []
}
```

State behavior:

- If no remaining trusts: transition `COMPLETING -> COMPLETED`.
- If remaining trusts exist: transition `COMPLETING -> TRUST_SELECTION_READY`, clear previous `usernameCheck`, and keep AuthFlowSession active.
- FE uses returned `unregisteredTrusts` for immediate trust selection.
- User does not repeat registration/start, captcha, or OTP while the AuthFlowSession remains valid.

---

## 10. Registration Cancel

```http
POST /api/v1/auth/registration/cancel
Cookie: auth_flow_sid=afs_xxx
X-XSRF-TOKEN: csrf_xxx
X-Request-Id: req_xxx
Accept-Language: en
```

Request:

```json
{
  "reason": "BACK"
}
```

Response:

```json
{
  "success": true,
  "status": "UPDATED",
  "result": {
    "cancelled": true
  },
  "messages": [],
  "errors": []
}
```

After successful cancel, clear cookies:

```http
Set-Cookie: auth_flow_sid=; HttpOnly; Secure; SameSite=None; Path=/api/v1/auth; Max-Age=0
Set-Cookie: XSRF-TOKEN=; Secure; SameSite=None; Path=/api/v1/auth; Max-Age=0
```

If FE has lost CSRF, FE must not call cancel. Old session will expire or be replaced by new start.

## Canonical captcha contract

The standalone captcha lifecycle, HTTP 422 lifecycle-ineligible response, public error ownership, state concealment, and migration rules are defined by [`../captcha/captcha-verification-lifecycle-contract.md`](../captcha/captcha-verification-lifecycle-contract.md). Registration submits only `captchaChallengeId`, consumes through the shared application gate, retains `err.registration.captcha.*` workflow errors, and performs no downstream side effect after consumption rejection.

#### 5.5 Already Fully Registered

When `TRPSearchMember` returns records but none has `isRegistered=false`, Java creates the protected registration AuthFlowSession and immediately transitions it to `COMPLETED`. Java does not create or deliver an OTP. The response is successful with `result.nextStep=RETURN_TO_LOGIN`, a page-level INFO message whose code is `registration.completion`, and an empty errors array.
