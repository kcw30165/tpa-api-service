# Login E2E Scenario Pack

Canonical Login start endpoint:

```text
POST /api/v1/auth/login
```

Mandatory request headers are `Idempotency-Key`, `Accept-Language`, `X-Request-Id`, and `Content-Type: application/json`.

Implemented scenarios:

- `missing-request-id`: omits only `X-Request-Id` and expects required-header rejection.
- `missing-idempotency-key`: omits only `Idempotency-Key` and expects required-header rejection.
- `invalid-request`: sends both mandatory identifiers with an empty JSON request and preserves the approved validation expectation.
- `valid-start`: uses runtime-only credentials, performs Login start, and captures the resulting pre-authentication contract without sending OTP.

Credentials, cookies, CSRF values, destinations, and account data must never be committed or retained unredacted.
