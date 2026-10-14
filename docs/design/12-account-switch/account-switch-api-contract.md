# Account Switch FE-BFF API Contract

## Endpoint

```http
POST /api/v1/account/switch
```

## Request context

| Header / cookie | Purpose |
|---|---|
| `portal_sid` cookie | Authenticated session. Read directly by `AccountSwitchController` via `AuthenticatedSessionCookieReader`, not via `PortalAccessContextWebFilter`. |
| `X-XSRF-TOKEN` (`RequestHeaderContextKeys.XSRF_TOKEN_HEADER`) | Required on every call. Validated via `ValidateAuthenticatedSessionUseCase.validate(...)`, mirroring `LogoutController`'s sequencing. |
| `X-Request-Id` | Request correlation; echoed back on the response if present. |

`Account-Ref` is **not required** for this endpoint. `PortalAccessContextWebFilter` only performs
its account-resolution/CSRF-requirement logic when that header is present on a request; if absent,
the filter passes the request through untouched. This controller resolves the session itself via
the cookie and takes the switch target from the request body, not a header.

## Request body

```json
{
  "accountRef": "acc_xzxMWFN5aVMVyOTThpAe4wN0kObWotWb"
}
```

| Field | Type | Rule |
|---|---|---|
| `accountRef` | string | `@NotBlank` + `@Pattern(regexp = "^acc_[A-Za-z0-9_-]{20,124}$")`, matching `AuthenticatedAccountRef`'s confirmed format. Must belong to the account list of the **same session** identified by the `portal_sid` cookie. |

## Success response (HTTP 200)

```json
{
  "success": true,
  "status": "SUCCESS",
  "result": {
    "data": {
      "accountRef": "acc_xzxMWFN5aVMVyOTThpAe4wN0kObWotWb",
      "policyNo": "1111111111111111111111111",
      "certNo": "1",
      "accountEnv": "AA",
      "trust": "BB",
      "schemeType": "CC"
    }
  },
  "messages": [],
  "errors": []
}
```

## Response fields

| Field | Type | Source |
|---|---|---|
| `accountRef` | string | `AccountContext.accountRef()` |
| `policyNo` | string | `AccountContext.policyNo()` |
| `certNo` | string | `AccountContext.certNo()` |
| `accountEnv` | string | `AccountContext.accountEnv()` |
| `trust` | string | `AccountContext.trustCode()` |
| `schemeType` | string | `AccountContext.schemeType()` |

This field set and naming mirror the original post-OTP-verify account list shape
(`LoginOtpAccountResponse`), so FE can reuse its existing account-rendering code. `trust` was
confirmed as the field name to use for this endpoint (not `trustCode`, despite the underlying
`AccountContext` field being named `trustCode`).

## Statelessness

No session mutation occurs. FE must continue sending the `Account-Ref` header on every subsequent
authenticated call, exactly as before calling this endpoint. This endpoint only resolves and returns
display metadata for the target account; it does not change which account the session considers
"current" at the Redis/session-state level.

## Failure responses

See `account-switch-error-matrix.md` for the full cause-to-response mapping.
