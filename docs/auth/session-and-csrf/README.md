# Session and CSRF Security

> **Status:** Documents the implementation represented by the reviewed codebase snapshot.  
> **Audience:** New team members, backend developers, reviewers, testers, frontend developers, and operations staff.  
> **Scope:** Pre-authentication auth-flow sessions, authenticated portal sessions, session-bound CSRF/XSRF protection, cookie transport, Origin/Referer validation, and associated lifecycle controls.

## Start here

Session and CSRF must be designed and used together in this codebase. They remain separate controls, but they form one browser security context:

```text
Session cookie
    identifies server-side session state

CSRF token
    proves possession of the token bound to that session

Origin / Referer validation
    checks that the browser request came from an approved web origin

Idempotency key
    prevents duplicate business processing; it does not replace CSRF
```

Read the documents in this order:

1. **[Architecture and lifecycle](architecture-and-lifecycle.md)** — understand both session families, CSRF binding, cookies, Redis, transitions, and security boundaries.
2. **[Integration and reuse guide](integration-and-reuse-guide.md)** — follow the implementation recipe for a new workflow or authenticated endpoint.
3. **[Operations and testing](operations-and-testing.md)** — configure, test, troubleshoot, monitor, and support the capability.

## Two security contexts

### 1. Pre-authentication auth-flow context

Used while a user completes Login, Registration, Forgot Password, Username Recovery, or another approved pre-authentication workflow.

It combines:

- `AuthFlowSession` stored server-side in Redis
- HttpOnly `auth_flow_sid` cookie
- Session-bound CSRF token hash
- Readable XSRF token transport and `X-XSRF-TOKEN` header
- Scenario and workflow status
- Protected workflow context
- Origin/Referer validation
- Scenario TTL and concurrency policy
- Completion, cancellation, replacement, and expiry

### 2. Authenticated portal context

Created after successful Login authentication and account authorization.

It combines:

- `AuthenticatedSession` stored server-side in Redis
- HttpOnly `portal_sid` cookie
- Session-bound authenticated CSRF token hash
- Raw authenticated CSRF token returned once through the approved Login response
- `ActorContext`
- Authorized account list and generated `Account-Ref` values
- Idle and absolute expiry
- Logout, revocation, expiry, and terminal retention

## Security-context transition

```text
Login starts
    -> create AuthFlowSession
    -> issue auth_flow_sid and pre-auth XSRF state
    -> validate session + CSRF throughout Login

Login OTP succeeds and accounts are resolved
    -> create AuthenticatedSession
    -> generate a new authenticated CSRF token
    -> store only its hash
    -> set portal_sid
    -> return authenticated CSRF token once
    -> clear pre-auth auth-flow/XSRF cookies
    -> terminate pre-auth session
```

The pre-auth CSRF token must not be reused after the authenticated context is established.

## Main architectural boundaries

```text
Controllers / Web filters / Cookie writers
                    |
Protected context loaders and validation services
                    |
Session lifecycle, CSRF, concurrency, and logout use cases
                    |
AuthFlowSessionPort / AuthenticatedSessionPort / protection ports
                    |
Redis, AES-GCM, and technical transport adapters
```

## Reuse rules

1. Select the correct session family before implementation.
2. Treat session creation and CSRF binding as one security-context operation.
3. Persist only the CSRF hash, never the raw token.
4. Keep the session cookie HttpOnly and the XSRF cookie non-HttpOnly.
5. Validate session, CSRF, scenario/status, and Origin/Referer together.
6. Apply idempotency separately to duplicate-processing risk.
7. Do not put PII, tokens, account data, or hashes in Redis keys.
8. Never log session IDs, cookies, CSRF tokens/hashes, or protected context.
9. Rotate CSRF when transitioning to a new authenticated security context.
10. Clear the appropriate cookies when a flow is completed, cancelled, replaced, logged out, revoked, or otherwise unusable.
11. Do not extend a session beyond its absolute expiry.
12. Resolve `PortalAccessContext` only from a validated authenticated session and a matching `Account-Ref`.

## Current configurable conventions

The reviewed configuration uses:

- Auth-flow cookie: `auth_flow_sid`, path `/api/v1/auth`
- Pre-auth XSRF cookie: `XSRF-TOKEN`
- CSRF request header: `X-XSRF-TOKEN`
- Authenticated session cookie: `portal_sid`, path `/api`
- Secure cookies with SameSite `None`
- Scenario-specific auth-flow TTLs
- Authenticated idle timeout, absolute timeout, and terminal retention
- Redis Sentinel with SSL/mTLS

These are current configuration values, not architectural constants. Confirm the active deployment profile before diagnosing or changing behavior.
