# Account Endpoints -- Error / Status Matrix

## POST /api/v1/accounts (list all)

| Scenario | HTTP status | ApiStatus | Notes |
|---|---|---|---|
| Missing/invalid `portal_sid` cookie | 401 | BUSINESS_REJECTED | Short-circuits before session validation, mirrors LogoutController |
| Missing `X-XSRF-TOKEN` header | 401 | BUSINESS_REJECTED | Same short-circuit |
| Invalid/expired CSRF token | 403 | VALIDATION_FAILED | Form-level error |
| Valid session, zero accounts | 200 | SUCCESS | `"accounts": []`, not an error |
| Valid session, N accounts | 200 | SUCCESS | Full list returned |

There is no "account not found" case for this endpoint -- it never takes an
`accountRef` input.

## POST /api/v1/account (get one)

| Scenario | HTTP status | ApiStatus | Notes |
|---|---|---|---|
| Missing/invalid `portal_sid` cookie | 401 | BUSINESS_REJECTED | Short-circuits before session validation |
| Missing `X-XSRF-TOKEN` header | 401 | BUSINESS_REJECTED | Same short-circuit |
| Invalid/expired CSRF token | 403 | VALIDATION_FAILED | Form-level error |
| Malformed `accountRef` (fails `@Pattern`) | 400 | VALIDATION_FAILED | Standard `@Valid` rejection, before any session/account lookup |
| Valid session, but `accountRef` not in session's account list | 422 | BUSINESS_REJECTED | `AccountNotInSessionException`, via the existing `BUSINESS_REJECTED -> UNPROCESSABLE_ENTITY` mapping |
| Valid session, matching account found | 200 | SUCCESS | Returns the account's display fields |

Carried over unchanged from the prior design: session-invalid (401) and
account-not-found (422) remain distinct, non-conflated failure modes.
