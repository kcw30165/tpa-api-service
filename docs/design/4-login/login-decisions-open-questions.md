> Status: Design baseline v0.1
> Date: 20 July 2026
> Scope: Subsequent member login plus reusable authentication foundations
> Source baseline: Login BRD V0.3, discovery decisions in 02-brd.md through 10-brd.md, and the supplied current codebase snapshot
> Open external dependency: detailed `TRPGetAccountList` business failure-code mapping remains TBC.

# Login Decisions and Open Questions

## Confirmed decisions

- Scope is subsequent login plus shared foundations.
- Registration implementations are reused/generalized; no duplicate OTP/APIM/crypto stack.
- TAS owns password failure count and lock.
- Java owns OTP through the existing shared OTP lifecycle.
- FE always selects an OTP destination, including when only one exists.
- Destination selection uses an opaque destination ID; FE receives only masked contact data.
- Pre-auth uses `auth_flow_sid`; authenticated state uses `portal_sid`.
- Session cookies are HttpOnly.
- CSRF token is returned in successful session-establishment body, kept in FE memory, submitted as `X-XSRF-TOKEN`, and stored server-side only as a hash.
- OTP behaviour and HTTP/error semantics remain shared with registration and separately configured.
- AccountRef remains mandatory on every account-scoped request.
- Missing AccountRef is 400; foreign AccountRef is 403.
- Progress owns terminated-member grace filtering.
- Policy/certificate formatting is configurable and left-zero-padded.
- `schemeType` is nullable temporarily, and the apim response field is`sch-type`
- Authenticated session protects the complete context.
- Concurrent login is automatic replacement; last committed login wins.
- Replaced session returns `err.login.session.replaced` with HTTP 401.
- Authenticated session has configurable sliding idle timeout and no absolute lifetime.
- Temporary portal context and `Session-Id` header are removed after cutover.
- Global APIM transient retry applies to eligible safe business calls; mutations may opt out.
- `TRPMemberLogin` and `TRPGetAccountList` use three total business-call attempts for eligible transient failures; token/certificate recovery is excluded.
- FE owns displayed messages; Java returns stable codes.

## Open/TBC

### D-LOGIN-TBC-001 — `TRPGetAccountList` explicit business failures

The exact Progress/APIM failure response codes and one-to-one mappings are not yet supplied.

Interim design:

- Use the existing configuration-driven downstream-classification extension point.
- Do not hardcode mappings.
- Use `err.login.accountList.unavailable` as the safe BFF fallback.
- Update `login-apim-contract.md` and `login-error-matrix.md` when the contract arrives.

## Deferred cleanup

- Remove nullable `schemeType` after confirming no active consumer requires it.
- Decide separately whether registration should stop returning CSRF in its response body or retain its current API contract; login does not change registration transport behaviour.
