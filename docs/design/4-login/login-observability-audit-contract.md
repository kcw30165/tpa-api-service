> Status: Design baseline v0.1  
> Date: 20 July 2026  
> Scope: Subsequent member login plus reusable authentication foundations  
> Source baseline: Login BRD V0.3, discovery decisions in 02-brd.md through 10-brd.md, and the supplied current codebase snapshot  
> Open external dependency: detailed `TRPGetAccountList` business failure-code mapping remains TBC.


# Login Observability and Audit Contract

## 1. Events

Recommended semantic events:

```text
LOGIN_CREDENTIALS_VERIFIED
LOGIN_CREDENTIALS_REJECTED
LOGIN_PREAUTH_CREATED
LOGIN_OTP_DESTINATION_SELECTED
LOGIN_OTP_SENT
LOGIN_OTP_VERIFIED
LOGIN_OTP_VERIFY_EXHAUSTED
LOGIN_OTP_RESEND_EXHAUSTED
LOGIN_ACCOUNT_LIST_RETRIEVED
LOGIN_ACCOUNT_RECORD_REJECTED
LOGIN_AUTH_SESSION_CREATED
LOGIN_AUTH_SESSION_CREATE_FAILED
LOGIN_SESSION_REPLACED
LOGIN_LOGOUT
LOGIN_SESSION_REVOKED
```

## 2. Safe fields

Allowed operational metadata includes:

- Request/correlation ID
- Event type and outcome
- HTTP status
- Workflow scenario/action
- Internal validation category
- APIM operation logical name
- Attempt number and latency
- Count of returned/valid/rejected records
- Exception type

## 3. Forbidden fields

Do not log:

- Username/cust-id
- Password/OTP
- Raw or masked destination when not required
- Policy/certificate number
- AccountRef
- Session ID or CSRF token
- Raw APIM request/response
- Token, certificate, key or protected session context

## 4. Severity

- Expected validation/business 4xx: WARN
- APIM transport, contract, crypto and Redis failures: ERROR
- Malformed account record: ERROR with safe record index/category
- Session replacement: INFO or WARN according to security operations policy

## 5. Metrics

- Credential submissions by outcome
- OTP sends/resends/verifications by outcome and channel
- OTP exhaustion totals
- APIM latency, retry count and final outcome by operation
- Account records returned/omitted
- Authenticated-session create latency/failures
- Session replacement/revocation totals
- Session resolution failures by type

Metrics labels must not contain personal or account identifiers.
