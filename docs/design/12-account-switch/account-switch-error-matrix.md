# Account Switch Error Matrix

## Controller sequencing (mirrors `LogoutController`)

1. Read `portal_sid` cookie via `AuthenticatedSessionCookieReader.read(exchange)`.
2. Require `X-XSRF-TOKEN` header present.
3. Validate request body `accountRef` format (`@Valid`).
4. `sessionValidation.validate(...)` — session + CSRF check.
5. `useCase.switchAccount(...)` — business lookup against the session's account list.

A failure at step 1 or 2 short-circuits before any session validation is attempted.

## Cause-to-response mapping

| Cause | HTTP | `status` | `errors[0].type` | `errors[0].code` |
|---|---|---|---|---|
| Missing or malformed `portal_sid` cookie | 401 | `BUSINESS_REJECTED` | `AUTHENTICATION_REQUIRED` | `err.security.authentication.required` |
| Missing `X-XSRF-TOKEN` header | 401 | `BUSINESS_REJECTED` | `AUTHENTICATION_REQUIRED` | `err.security.authentication.required` |
| Malformed `accountRef` (fails `@Pattern`) | Validation error | — | — | — (standard `@Valid` rejection, before any session/account lookup) |
| CSRF token invalid/mismatched (`AuthenticatedCsrfValidationException`) | 403 | `VALIDATION_FAILED` | `FORM` (`ApiError.form(...)`) | `err.account.switch.csrf.invalid` |
| Session itself invalid/expired/not found (`AuthenticatedPortalContextResolutionException`) | 401 | `BUSINESS_REJECTED` | `AUTHENTICATION_REQUIRED` | `err.security.authentication.required` |
| Session valid, but target `accountRef` not in session's account list (`AccountNotInSessionException`) | 422 | `BUSINESS_REJECTED` | `BUSINESS` (`ApiError.business(...)`) | `err.account.switch.not.found` |
| Success | 200 | `SUCCESS` | — | — |

## The 401 vs. 422 split (rationale)

**Original behavior (first implementation pass):** both "session invalid" and "target account not
in session" produced an identical 401/`AUTHENTICATION_REQUIRED` response. This was flagged during
manual testing as misleading: a user with a perfectly valid session, who simply requested an
`accountRef` they don't have access to, would see a message implying they need to re-authenticate.

**Resolution:** the two causes were split into two distinct exception types inside
`AuthenticatedAccountSwitchLookupAdapter`:

- `AuthenticatedPortalContextResolutionException` (existing, `application.usecase.session.authenticated`)
  — reserved strictly for session-level invalidity: session not found, not `ACTIVE`, or malformed
  session/account-ref input before any account-list lookup is attempted.
- `AccountNotInSessionException` (new, `application.usecase.accountswitch`) — thrown only when the
  session itself is confirmed valid and `ACTIVE`, but `session.findAccount(ref)` finds no match.

The 422 mapping is **not** a new convention: `HttpStatusClassification.classify(ApiStatus.BUSINESS_REJECTED)`
already resolves to `HttpStatus.UNPROCESSABLE_ENTITY` project-wide, and this exact pairing is
already used by `AccountBalance`, `AccountGainLoss`, `ForgotPassword`, `Login`, and `Registration`.
This feature reuses that existing classification rather than introducing a new HTTP-status
convention. The error entry uses the existing `ApiError.business(...)` factory, distinct from
`.form(...)` (used for the CSRF case).

## Information-disclosure consideration

Distinguishing "account not in session" (422) from "session invalid" (401) does reveal slightly
more information than the original merged response — specifically, that the caller's session is
valid even though the requested account is not theirs. This was judged an acceptable, mild
disclosure: it does not reveal whether the requested `accountRef` exists at all, only that it is
not reachable from the caller's own session.
