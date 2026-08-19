# Username Recovery API Contract Design

## 1. General contract

Base path:

```text
/api/v1/auth/username-recovery
```

Common headers:

```http
Accept-Language: en
X-Request-Id: <correlation-id>
```

Mutation endpoints use the existing `MutationResponse<T>` envelope. HTTP status is carried by the HTTP response status line and is not duplicated in JSON.

## 2. Captcha dependency

Username Recovery reuses:

```text
POST /api/v1/auth/captcha/challenge
POST /api/v1/auth/captcha/verify
POST /api/v1/auth/captcha/refresh
```

FE verifies the answer through `/captcha/verify`. A successful verification stores `VERIFIED`. Username Recovery `/start` receives only `captchaChallengeId` and atomically changes `VERIFIED -> CONSUMED`.

## 3. Public identity values

FE submits:

```text
GPN
GPFS
STAFF_NO
ID
```

Java converts public `STAFF_NO` to shared `MemberIdentityType.STAFFNO`. APIM receives `STAFFNO`.

## 4. POST `/start`

```http
POST /api/v1/auth/username-recovery/start
Accept-Language: en
X-Request-Id: req-123
Idempotency-Key: idem-123
Content-Type: application/json
```

Request:

```json
{
  "identityType": "STAFF_NO",
  "identityValue": "3030609",
  "dateOfBirth": "04/07/1978",
  "email": "HULLCS.QGLYBCY@UC.KJI",
  "mobile": null,
  "captchaChallengeId": "cap_f67f65980e944119b8b81c74d9a1834a"
}
```

There is no `captchaAnswer`.

Validation:

- `identityType` is one of the approved public values.
- `STAFF_NO` is converted to shared `MemberIdentityType.STAFFNO`.
- Identity-value validation reuses Registration.
- DOB is strict `dd/MM/yyyy`.
- Exactly one of email/mobile is nonblank.
- Email/mobile validation reuses Registration.
- Captcha ID is mandatory and must atomically consume a `VERIFIED` challenge.

### Successful response with registered schemes

```http
HTTP/1.1 200 OK
Set-Cookie: auth_flow_sid=afs_opaque; Path=/api/v1/auth/username-recovery; Max-Age=600; Secure; HttpOnly; SameSite=Strict
```

```json
{
  "success": true,
  "status": "SUCCESS",
  "result": {
    "nextStep": "SELECT_ACCOUNT",
    "data": {
      "csrfToken": "csrf-value",
      "expiresInSeconds": 600,
      "schemes": [
        {
          "schemeRef": "sch_opaque-1",
          "accountEnv": "DB",
          "trustCode": "DBA",
          "isRegistered": true
        },
        {
          "schemeRef": "sch_opaque-2",
          "accountEnv": "DB",
          "trustCode": "DBB",
          "isRegistered": false
        }
      ]
    }
  },
  "messages": [],
  "errors": []
}
```

A session is created because at least one registered scheme exists.

### Member not found

```http
HTTP/1.1 422 Unprocessable Entity
```

```json
{
  "success": false,
  "status": "BUSINESS_REJECTED",
  "result": {
    "nextStep": "RESTART_FLOW",
    "data": null
  },
  "messages": [],
  "errors": [
    {
      "type": "FORM",
      "code": "err.usernamerecovery.verification.failed",
      "targets": [],
      "severity": "ERROR",
      "source": "BFF"
    }
  ]
}
```

### Only unregistered schemes

No Username Recovery session, cookie, or CSRF token is created.

```http
HTTP/1.1 422 Unprocessable Entity
```

```json
{
  "success": false,
  "status": "BUSINESS_REJECTED",
  "result": {
    "nextStep": "RESTART_FLOW",
    "data": {
      "schemes": [
        {
          "schemeRef": null,
          "accountEnv": "DB",
          "trustCode": "DBA",
          "isRegistered": false
        }
      ]
    }
  },
  "messages": [
    {
      "type": "INFO",
      "code": "label.usernamerecovery.complete.registration"
    }
  ],
  "errors": []
}
```

The response is terminal for Username Recovery and gives FE enough information to render the Registration reminder.

## 5. POST `/request`

```http
POST /api/v1/auth/username-recovery/request
Accept-Language: en
X-Request-Id: req-124
Idempotency-Key: idem-124
X-XSRF-TOKEN: csrf-value
Scheme-Ref: sch_opaque-1
Cookie: auth_flow_sid=afs_opaque
Content-Length: 0
```

### Successful request with usernames still unrequested

After sender `ACCEPTED`, candidate state becomes `REQUESTED`. If another registered candidate remains not `REQUESTED`, FE returns to the selection page.

```http
HTTP/1.1 200 OK
```

```json
{
  "success": true,
  "status": "SUCCESS",
  "result": {
    "nextStep": "SELECT_ACCOUNT",
    "data": {
      "status": "PARTIALLY_REQUESTED",
      "schemeRef": "sch_opaque-1",
      "maskedDestination": "p****c@ah.ozk",
      "destinationType": "EMAIL",
      "requestAccepted": true
    }
  },
  "messages": [
    {
      "type": "INFO",
      "code": "label.usernamerecovery.request.accepted"
    }
  ],
  "errors": []
}
```

### Final successful request

After the last registered candidate becomes `REQUESTED`, the session becomes `COMPLETED`.

```http
HTTP/1.1 200 OK
```

```json
{
  "success": true,
  "status": "SUCCESS",
  "result": {
    "nextStep": "RETURN_TO_LOGIN",
    "data": {
      "status": "COMPLETED",
      "schemeRef": "sch_opaque-2",
      "maskedDestination": "p****c@ah.ozk",
      "destinationType": "EMAIL",
      "requestAccepted": true
    }
  },
  "messages": [
    {
      "type": "INFO",
      "code": "label.usernamerecovery.request.accepted"
    },
    {
      "type": "INFO",
      "code": "label.return.to.login"
    }
  ],
  "errors": []
}
```

The top-level generic API status remains `SUCCESS`. The workflow status returned in result data is `COMPLETED`.

### Requested candidate behavior

- Same Idempotency-Key and same request: replay the original 200 response.
- Different key for a `REQUESTED` candidate: return conflict/already requested without sending again.
- Request against a completed session: same-key replay is allowed; any new mutation returns already completed.

## 6. POST `/cancel`

```http
POST /api/v1/auth/username-recovery/cancel
Accept-Language: en
X-Request-Id: req-125
Idempotency-Key: idem-125
X-XSRF-TOKEN: csrf-value
Cookie: auth_flow_sid=afs_opaque
Content-Length: 0
```

Successful response:

```http
HTTP/1.1 200 OK
Set-Cookie: auth_flow_sid=; Path=/api/v1/auth/username-recovery; Max-Age=0; Secure; HttpOnly; SameSite=Strict
```

```json
{
  "success": true,
  "status": "SUCCESS",
  "result": {
    "nextStep": "RETURN_TO_LOGIN",
    "data": null
  },
  "messages": [],
  "errors": []
}
```

## 7. HTTP mapping rules

- `200 OK`: success and completed idempotent replay
- `400 Bad Request`: malformed/missing fields or invalid state not assigned another status
- `403 Forbidden`: CSRF or origin/referer rejection
- `409 Conflict`: idempotency conflict, concurrent state conflict, already requested with a different key, or completed mutation
- `422 Unprocessable Entity`: recognized business rejection, member not found, or zero registered schemes
- `429 Too Many Requests`: configured rate limit
- `503 Service Unavailable`: internal `SYSTEM_FAILED`, public `SYSTEM_ERROR`

## 8. Authoritative error matrix

| ID | Endpoint | Scenario | HTTP | API status | Next step | Error code | Session consequence | Retry |
|---|---|---|---:|---|---|---|---|---|
| UR-START-001 | start | Missing Idempotency-Key | 400 | VALIDATION_FAILED | NONE | shared idempotency missing | none | correct request |
| UR-START-002 | start | Unsupported public identity type | 400 | VALIDATION_FAILED | NONE | identity type invalid | none | correct request |
| UR-START-003 | start | Invalid identity value | 400 | VALIDATION_FAILED | NONE | identity value invalid | none | correct request |
| UR-START-004 | start | Invalid DOB | 400 | VALIDATION_FAILED | NONE | DOB invalid | none | correct request |
| UR-START-005 | start | Both/neither contact | 400 | VALIDATION_FAILED | NONE | destination exactly one | none | correct request |
| UR-START-006 | start | Invalid email/mobile | 400 | VALIDATION_FAILED | NONE | field-specific code | none | correct request |
| UR-START-007 | start | Captcha missing | 400 | VALIDATION_FAILED | NONE | existing captcha not-found code | none | new captcha |
| UR-START-008 | start | Captcha not VERIFIED | 400 | VALIDATION_FAILED | NONE | existing status-specific captcha code | none | new/verify captcha |
| UR-START-009 | start | Captcha consume conflict | 409 | BUSINESS_REJECTED | NONE | existing consumed/conflict code | none | new captcha |
| UR-START-010 | start | Member not found | 422 | BUSINESS_REJECTED | RESTART_FLOW | err.usernamerecovery.verification.failed | none | restart |
| UR-START-011 | start | Zero registered schemes | 422 | BUSINESS_REJECTED | RESTART_FLOW | registration reminder code | no session/cookie | registration/restart |
| UR-START-012 | start | APIM malformed/unavailable | 503 | SYSTEM_ERROR | NONE | err.system.unavailable | none | after recovery |
| UR-START-013 | start | Redis session creation failed | 503 | SYSTEM_ERROR | NONE | err.system.unavailable | no cookie | idempotent retry policy |
| UR-START-014 | start | Duplicate in progress | 409 | BUSINESS_REJECTED | WAIT | shared in-progress code | existing | same key later |
| UR-START-015 | start | Key reused with different request | 409 | VALIDATION_FAILED | NONE | shared key-reused code | unchanged | new key |
| UR-REQ-001 | request | Missing/invalid/expired session | 400 | VALIDATION_FAILED | RESTART_FLOW | auth-flow invalid | clear invalid cookie | restart |
| UR-REQ-002 | request | Missing/invalid CSRF or origin | 403 | VALIDATION_FAILED | NONE | CSRF/security invalid | no mutation | correct/restart |
| UR-REQ-003 | request | Invalid Scheme-Ref | 400/422 | VALIDATION_FAILED/BUSINESS_REJECTED | SELECT_ACCOUNT | scheme-ref invalid | retain | select valid |
| UR-REQ-004 | request | Unregistered candidate | 422 | BUSINESS_REJECTED | SELECT_ACCOUNT | registration required | retain | no delivery |
| UR-REQ-005 | request | Sender accepted; more unrequested | 200 | SUCCESS | SELECT_ACCOUNT | none | candidate REQUESTED; session PARTIALLY_REQUESTED | continue |
| UR-REQ-006 | request | Sender accepted; all requested | 200 | SUCCESS | RETURN_TO_LOGIN | none | candidate REQUESTED; session COMPLETED | terminal |
| UR-REQ-007 | request | Same-key completed replay | 200 | SUCCESS | original step | none | unchanged | safe replay |
| UR-REQ-008 | request | Different key after REQUESTED | 409 | BUSINESS_REJECTED | SELECT_ACCOUNT/RETURN_TO_LOGIN | already requested | unchanged | no resend |
| UR-REQ-009 | request | New request against COMPLETED | 409 | BUSINESS_REJECTED | RETURN_TO_LOGIN | workflow completed | unchanged | no mutation |
| UR-REQ-010 | request | Sender failure or mapped exception | 503 | SYSTEM_ERROR | SELECT_ACCOUNT | err.system.unavailable | candidate not REQUESTED | policy controlled |
| UR-REQ-011 | request | Sender/persistence unknown outcome | 503 | SYSTEM_ERROR | WAIT | err.system.unavailable | UNKNOWN_OUTCOME | same key resolution only |
| UR-CANCEL-001 | cancel | Valid or absent session | 200 | SUCCESS | RETURN_TO_LOGIN | none | delete/clear | idempotent |
| UR-CANCEL-002 | cancel | Invalid CSRF with live session | 403 | VALIDATION_FAILED | RETURN_TO_LOGIN | CSRF invalid | no mutation | restart |
| UR-CANCEL-003 | cancel | Mapped infrastructure exception | 503 | SYSTEM_ERROR | RETURN_TO_LOGIN | err.system.unavailable | uncertain | same key |

Use existing shared captcha, idempotency, CSRF, and system error keys where they already express the required semantics.

## 9. Exception mapping

Every APIM, Redis, protected-context, idempotency, template, or sender exception caught by Username Recovery is logged with sanitized request context and mapped to internal `SYSTEM_FAILED`. Web mapping emits `SYSTEM_ERROR` and the standard public system error.

There are no silent catches or exception-to-null fallbacks. Unknown delivery outcome remains distinct from ordinary mapped failure to prevent duplicate sending.

## Canonical standalone captcha contract

The standalone verify HTTP and error contract is owned by [`../captcha/captcha-verification-lifecycle-contract.md`](../captcha/captcha-verification-lifecycle-contract.md). Username Recovery-owned start errors remain workflow-owned; they do not replace or redefine standalone captcha errors.
