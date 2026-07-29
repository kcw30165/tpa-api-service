> Status: Design baseline v0.1  
> Date: 20 July 2026  
> Scope: Subsequent member login plus reusable authentication foundations  
> Source baseline: Login BRD V0.3, discovery decisions in 02-brd.md through 10-brd.md, and the supplied current codebase snapshot  
> Open external dependency: detailed `TRPGetAccountList` business failure-code mapping remains TBC.


# Login Session Security Contract

## 1. Cookies and CSRF

### Pre-auth

- `auth_flow_sid`: HttpOnly, Secure, SameSite=None, path `/api/v1/auth`.
- CSRF token returned in successful credential/session-establishment response body.
- FE keeps token in memory and submits `X-XSRF-TOKEN`.
- Server stores only the CSRF hash in AuthFlowSession.

### Authenticated

- `portal_sid`: HttpOnly, Secure, configurable path, SameSite and max age.
- New CSRF token generated after authenticated-session creation.
- Token returned once in the success body and kept by FE in memory.
- Server stores only the authenticated CSRF hash.

Safe methods do not require CSRF. Unsafe methods require valid session, CSRF and origin/referer policy.

## 2. Session fixation prevention

Pre-auth and authenticated sessions use different IDs and cookies. Authentication always generates a new session ID and CSRF token. `auth_flow_sid` is never promoted into `portal_sid`.

## 3. Sliding timeout

- Pre-auth baseline: configurable 15 minutes.
- Authenticated baseline: configurable 30 minutes.
- No Java hardcoded fallback; missing/invalid security configuration fails startup.
- No absolute authenticated lifetime.
- Only a valid authenticated request refreshes expiry.
- Invalid cookie, CSRF, foreign AccountRef, replaced session and unauthenticated requests do not refresh expiry.

## 4. Complete-context protection

The logical authenticated-session context is serialized and encrypted as one versioned authenticated-encryption envelope before Redis persistence.

Requirements:

- Dedicated authenticated-session key purpose/configuration.
- No reuse of the registration secret as an implicit default.
- Versioned keys to support rotation.
- Integrity/authentication failure is a system error.
- Plaintext session context is never logged.

## 5. Concurrent sessions

- Key: normalized username.
- Policy: last successfully committed login wins.
- New session creation and old-session revocation are atomic.
- Replaced session receives HTTP 401 and `err.login.session.replaced`.
- No administrator/service exemption.

## 6. Account authorization

Every account-scoped request must provide AccountRef. The AccountRef must exist in the decrypted session map. A value from another session is forbidden.

## 7. Logout and revocation

Logout:

- Requires `portal_sid` and CSRF.
- Revokes server-side session.
- Removes active index when owned by that session.
- Clears portal transport.

Internal revocation by username is restricted and idempotent. It revokes the active session and creates the same ordinary revoked/replaced handling required by clients.

## 8. Sensitive-data rules

Never log or place in keys:

- Password or OTP
- Raw destination
- Session or CSRF token
- Username
- Policy/certificate number
- AccountRef
- Protected session plaintext
- APIM tokens, keys or certificates
