> Status: Design baseline v0.1
> Date: 20 July 2026
> Scope: Subsequent member login plus reusable authentication foundations
> Source baseline: Login BRD V0.3, discovery decisions in 02-brd.md through 10-brd.md, and the supplied current codebase snapshot
> Open external dependency: detailed `TRPGetAccountList` business failure-code mapping remains TBC.

# Login API Contract

All mutation endpoints use `MutationResponse` with `result.nextStep`; `pageRef` is not used.

## 1. Common envelope

The envelope is common; `result.data` is endpoint-specific and has no fixed common schema.

```json
{
  "success": true,
  "status": "SUCCESS",
  "result": {
    "nextStep": "SOME_NEXT_STEP",
    "data": {}
  },
  "messages": [],
  "errors": []
}
```

`success` remains temporarily and is planned for later removal. `pageRef` is not returned.

### 1.1 Endpoint-specific fields

Fields such as `csrfToken`, `actions`, `destinations`, `failCount`, `accounts`, timing values, and counters appear only in endpoint-specific data contracts.

### 1.2 CSRF-token inclusion rule

`csrfToken` is **not** a standard field of `result.data`. Java returns it only when establishing or rotating a session-to-CSRF binding:

- Successful `POST /api/v1/auth/login`, when the pre-auth session is established.
- Successful `POST /api/v1/auth/login/otp/verify`, when the authenticated portal session is established and CSRF is rotated.

It is absent from OTP send/resend responses, OTP verification failures, credential failures, account-list failures, cancellation, logout, and ordinary authenticated responses.

### 1.3 Actions

`actions` is endpoint-specific. When present, it is always an array and defaults to `[]`. Each item contains only `code`, whose value is `WorkflowNextStep.toString()`.

```json
{
  "code": "RETURN_TO_LOGIN"
}
```

## 2. Start login

```http
POST /api/v1/auth/login
Content-Type: application/json
Idempotency-Key: <opaque-key>
X-Request-Id: <request-id>
```

```json
{
  "username": "member01",
  "password": "exact-case-sensitive-value"
}
```

Success establishes pre-auth state:

```json
{
  "success": true,
  "status": "SUCCESS",
  "result": {
    "nextStep": "SELECT_OTP_DESTINATION",
    "data": {
      "csrfToken": "<plaintext-token-returned-once>",
      "destinations": [
        {
          "destinationId": "dst_opaque",
          "channel": "email",
          "maskedDestination": "m***@example.com"
        },
        {
          "destinationId": "dst_opaque_2",
          "channel": "sms",
          "maskedDestination": "****5678"
        }
      ],
      "actions": []
    }
  },
  "messages": [],
  "errors": []
}
```

Failed response

```json
{
  "success": false,
  "status": "BUSINESS_REJECTED",
  "result": {
    "nextStep": "SIGN",
    "data": {}
  },
  "messages": [],
  "errors": [
        {
            "type": "BUSINESS",
            "code": "err.login.incorrect.password",
            "targets": [
                "password"
            ],
            "severity": "ERROR",
            "source": "SERVER"
        }]
}
```

Errors for different cases:

- incorrect password: code: err.login.incorrect.password, target: password
- incorrect username: code: err.login.incorrect.username, target: username
- no valid account: code: err.login.invalid.account, target: username
- locked account: code: err.login.locked.account, target: PAGE

The response sets HttpOnly `auth_flow_sid`. `failCount` is the Java/API name mapped from APIM `fail-count`.

Unknown username and wrong password both return HTTP 401 and `err.login.credentials.invalid`. Locked account returns HTTP 423 and `err.login.account.locked`.

## 3. Select destination and send OTP

```http
POST /api/v1/auth/login/otp/send
Cookie: auth_flow_sid=<opaque>
X-XSRF-TOKEN: <token>
Idempotency-Key: <opaque-key>
```

```json
{
  "destinationId": "dst_opaque"
}
```

Response data reuses existing shared OTP timing and masking semantics. It does not return `csrfToken`. APIM `mobile-num` is exposed to FE as `sms`.

## 4. Resend OTP

```http
POST /api/v1/auth/login/otp/resend
Cookie: auth_flow_sid=<opaque>
X-XSRF-TOKEN: <token>
Idempotency-Key: <opaque-key>
```

The endpoint uses the existing shared challenge-replacement lifecycle. Login has no resend count limit, so its response does not expose a remaining-resend count. Cooldown, expiry, idempotency, delivery, origin/referer, CSRF, and previous-code invalidation still apply. It does not return `csrfToken`.

## 5. Verify OTP and establish session

```http
POST /api/v1/auth/login/otp/verify
Cookie: auth_flow_sid=<opaque>
X-XSRF-TOKEN: <pre-auth-token>
Idempotency-Key: <opaque-key>
Content-Type: application/json
```

```json
{
  "code": "123456"
}
```

Successful verification, account retrieval and session creation return:

```json
{
  "success": true,
  "status": "SUCCESS",
  "result": {
    "nextStep": "SELECT_ACCOUNT",
    "data": {
      "accounts": [
        {
          "accountRef": "acc_opaque",
          "policyNo": "00000008802",
          "certNo": "0001"
        }
      ],
      "csrfToken": "<new-authenticated-token>",
      "actions": []
    }
  },
  "messages": [],
  "errors": []
}
```

Response sets HttpOnly `portal_sid`, clears `auth_flow_sid`, and rotates CSRF state.

### 5.1 Verification limit reached

```json
{
  "success": false,
  "status": "BUSINESS_REJECTED",
  "result": {
    "nextStep": "RETURN_TO_LOGIN",
    "data": {
      "actions": [
        {
          "code": "RETURN_TO_LOGIN"
        }
      ]
    }
  },
  "messages": [],
  "errors": [
    {
      "type": "BUSINESS",
      "code": "err.login.otp.verifyAttemptsExceeded",
      "targets": ["PAGE"],
      "severity": "ERROR",
      "source": "SERVER"
    }
  ]
}
```

HTTP 429. The response does not contain `csrfToken`; the OTP challenge and pre-auth session are invalidated and transport is cleared.

## 6. Cancel login

```http
POST /api/v1/auth/login/cancel
Cookie: auth_flow_sid=<opaque-if-present>
X-XSRF-TOKEN: <token-if-session-present>
```

Cancellation is idempotent. It invalidates OTP/pre-auth state, clears pre-auth transport, and returns `RETURN_TO_LOGIN`. It does not return `csrfToken`.

## 7. Logout

```http
POST /api/v1/auth/logout
Cookie: portal_sid=<opaque>
X-XSRF-TOKEN: <authenticated-token>
```

Logout revokes the authenticated session, removes its active index when owned by that session, and clears portal transport. It does not return `csrfToken`.

## 8. Internal revocation

```http
POST /api/v1/internal/authenticated-sessions/revoke
Content-Type: application/json
```

```json
{
  "username": "member01"
}
```

The restricted operation is idempotent and returns `revokedCount` without echoing the username.

## 9. Subsequent request contract

```http
GET /api/v1/personal-information
Cookie: portal_sid=<opaque>
Account-Ref: acc_opaque
```

Unsafe methods additionally require `X-XSRF-TOKEN`. `Session-Id` is not accepted.
