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

## Guarded OTP journey

`otp-send` performs a fresh Login start, keeps `auth_flow_sid`, CSRF, and destination ID only in a private temporary directory, explicitly selects a returned destination, and deletes private state at exit. The scenario fails closed unless `E2E_ALLOW_EXTERNAL_EFFECTS=true` is supplied. OTP resend and verification are not implemented in this step.

## Guarded OTP verification

`otp-send-and-verify` performs one fresh Login start, one OTP send, hidden OTP input, and one OTP verification attempt in the same process. It expects `SELECT_ACCOUNT`, records only the account count, retains authenticated cookie and CSRF values only in private runtime state, and stops before account selection.

Real execution requires both `E2E_ALLOW_EXTERNAL_EFFECTS=true` and `E2E_ALLOW_OTP_VERIFICATION=true`. OTP resend and account selection remain disabled.

## Post-login account selection model

Successful OTP verification establishes the authenticated portal session and returns `nextStep=SELECT_ACCOUNT` with an account list. There is no separate Login account-selection mutation endpoint in the confirmed controller contract.

The frontend selects an account locally and sends only the selected opaque `Account-Ref` on a subsequent business request together with the `portal_sid` cookie. Unsafe subsequent requests additionally send the authenticated `X-XSRF-TOKEN`. The BFF resolves the selected account from the authenticated session. `Session-Id` is not accepted.

Therefore, Login Gate D treats `SELECT_ACCOUNT` and the returned account-list structure as the terminal Login result. Feature-specific post-login Gate D packs validate selected-account resolution on their own business endpoints.

## Guarded invalid OTP

`otp-send-and-invalid-verify` performs a fresh Login start, one OTP send, and one deliberately invalid OTP verification attempt. It expects `BUSINESS_REJECTED`, continuation at `VERIFY_OTP`, remaining-attempt information, no `portal_sid`, and no account list.

Real execution requires `E2E_ALLOW_EXTERNAL_EFFECTS=true`, `E2E_ALLOW_OTP_VERIFICATION=true`, and `E2E_EXPECT_INVALID_OTP=true`. Resend, account selection, cancel, and logout remain disabled.

### Confirmed live invalid-OTP response

The development runtime returns HTTP `422 Unprocessable Entity` for a well-formed but incorrect Login OTP. The public response remains `BUSINESS_REJECTED` with `nextStep=VERIFY_OTP`, a numeric `remainingVerifyAttempts`, no authenticated portal cookie, and no accounts.

## Guarded immediate OTP resend

`otp-send-and-resend` performs a fresh Login start, one initial OTP send, and one immediate resend request. It expects cooldown enforcement through HTTP 429, `BUSINESS_REJECTED`, `VERIFY_OTP`, and matching positive `Retry-After` and `retryAfterSeconds` values. It performs no automatic wait or retry.

Real execution requires `E2E_ALLOW_EXTERNAL_EFFECTS=true` and `E2E_ALLOW_OTP_RESEND=true`. OTP verification, additional resend attempts, cancel, and logout remain disabled.
