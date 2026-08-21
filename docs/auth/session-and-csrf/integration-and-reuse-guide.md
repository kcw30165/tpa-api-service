# Session and CSRF Integration and Reuse Guide

## 1. Choose the security context

Use an **auth-flow security context** when the user is not authenticated and must complete a bounded, multi-step workflow.

Use an **authenticated portal security context** only after identity verification and authorization have succeeded.

Do not create an authenticated session merely because an OTP was valid; complete all required Login account authorization first.

## 2. Adding a new auth-flow scenario

### Step 1 — Define the state machine

Document:

- Entry condition
- All permissible states
- Allowed transitions
- Terminal states
- Restart/recovery behavior
- Successful completion authorization
- Cleanup behavior

Example for Forgot Password:

```text
STARTED
  -> DESTINATIONS_AVAILABLE
  -> OTP_SENT
  -> OTP_VERIFIED
  -> PASSWORD_RESET_AUTHORIZED
  -> COMPLETED

Terminal:
  CANCELLED
  EXPIRED
  LOCKED
  FAILED
```

Post-login Change Password is excluded from Forgot Password. It uses an authenticated portal security context and a different authorization model.

### Step 2 — Configure the scenario

Define and approve:

- Enabled/required behavior
- Auth-flow TTL
- Single-active or multi-flow behavior
- Replace-existing or allow-parallel policy
- Completion-lock lease
- Auth-flow cookie scope
- XSRF cookie/header policy
- Allowed Origin/Referer behavior

### Step 3 — Define protected context

Store only the continuation state required by the scenario. Protect sensitive context through the established protection port. Do not place identities, destinations, account fields, session IDs beyond the opaque key part, CSRF values, or business data in Redis keys.

### Step 4 — Create session and CSRF together

Reuse the lifecycle and CSRF services:

1. Enforce concurrency policy.
2. Generate a new opaque auth-flow session ID.
3. Generate a raw CSRF token.
4. Hash the token.
5. Persist the session and hash with scenario TTL.
6. Write the HttpOnly auth-flow cookie.
7. Expose the raw token through the approved non-HttpOnly XSRF transport.
8. Return only approved public workflow data.

Do not implement token generation or hashing in a controller.

### Step 5 — Protect every state-changing operation

The controller should perform transport presence checks and build a command containing:

- Auth-flow session ID from cookie
- Submitted `X-XSRF-TOKEN`
- Origin and Referer
- `X-Request-Id`
- Idempotency key where the operation requires one
- Scenario input

The protected context loader/application service must then validate:

- Session existence and expiry
- Session scenario
- Permitted workflow state
- CSRF session binding
- Origin/Referer policy
- Protected context integrity

Only after validation may the service touch/refresh the session and execute the mutation.

### Step 6 — Apply idempotency separately

CSRF prevents cross-site request forgery. Idempotency prevents duplicate execution. A protected mutation may require both.

The idempotency request hash must not include raw session or CSRF secrets in stored replay material. Use action-scoped records and safe replay snapshots.

### Step 7 — Update through ports

Use `AuthFlowSessionPort` or a scenario adapter. Controllers must not load JSON, construct Redis keys, update counters, or perform manual state mutation.

Plan for:

- Concurrent requests
- Existing terminal session
- Missing session after replacement
- Persistence failure
- Unknown outcome
- Completion-lock contention

### Step 8 — Complete, cancel, or replace

Define a single cleanup policy for server and browser state:

- Complete/cancel/delete/expire session
- Clear `auth_flow_sid` where required
- Clear `XSRF-TOKEN` using the same cookie path/policy
- Reject stale tokens from old tabs
- Emit security audit events without session/token values

## 3. Establishing an authenticated security context

After verified Login and accessible-account resolution:

1. Generate a new authenticated session ID.
2. Generate a new authenticated CSRF token; do not reuse pre-auth CSRF.
3. Store only the new hash.
4. Build actor and authorized-account state.
5. Calculate idle and absolute expiry.
6. Protect sensitive state.
7. Store through `AuthenticatedSessionPort`.
8. Set `portal_sid` as HttpOnly.
9. Return the authenticated CSRF value once through the approved result.
10. Clear pre-auth auth-flow and XSRF cookies.
11. Terminate the pre-auth session.

If session persistence fails, do not report successful authentication or set a usable cookie.

## 4. Protecting an authenticated endpoint

### Account-specific endpoint

The request contract should use:

- `portal_sid` cookie
- `X-XSRF-TOKEN` for protected mutations according to policy
- `Account-Ref`
- `X-Request-Id`
- `Accept-Language` where needed

The filter/application flow should:

1. Parse and validate the session ID.
2. Load and validate the authenticated session.
3. Validate CSRF for the protected operation.
4. Reject logged-out, revoked, expired, idle-expired, or absolute-expired sessions.
5. Match `Account-Ref` to the stored account list.
6. Touch the session without exceeding absolute expiry.
7. Build `PortalAccessContext` for the request duration.
8. Execute the business use case.

The client must never supply actor identity or business account attributes as authorization evidence.

### Non-account-specific endpoint

Still validate the authenticated session and CSRF where the operation is state-changing. Omit `Account-Ref` only where the business capability genuinely has no selected-account context.

## 5. Frontend contract

The frontend must:

- Send cookies using the approved credentials mode.
- Read the pre-auth XSRF cookie and copy it exactly to `X-XSRF-TOKEN`.
- Never read or store the HttpOnly session cookie.
- Replace pre-auth CSRF state with the authenticated CSRF value after Login.
- Send only an `Account-Ref` returned for the current authenticated session.
- Treat an invalid/replaced/expired flow as a restart condition.
- Never put session/CSRF values in URLs, logs, analytics, browser storage, or error telemetry.
- Handle `401`, `403`, conflict, and rate-limit responses according to the workflow contract.



## 5.1 Frontend page-refresh recovery sequence

When the frontend has a valid session cookie but no raw CSRF token in memory:

1. Send `POST /api/v1/auth/csrf-token/refresh` with browser credentials.
2. Include `X-Request-Id` and an approved `Origin` or `Referer`.
3. Do not send the lost or previous `X-XSRF-TOKEN` value.
4. Read the selected context from `response.result.data.sessionType`.
5. Read the rotated token from `response.result.data.csrfToken`.
6. Keep the token only in the approved in-memory frontend security state.
7. Submit the token through `X-XSRF-TOKEN` on subsequent protected mutations.

If `portal_sid` is present, the backend selects the authenticated session and never falls back to `auth_flow_sid`, even when the authenticated cookie is blank, malformed, expired, or otherwise unusable. A conflict may mean another tab rotated the token first; the frontend should follow the approved retry or restart behavior without logging token or cookie values.

## 6. Logout and revocation

### Logout

Validate session and authenticated CSRF, transition to `LOGGED_OUT`, retain the terminal record for the configured period, and clear `portal_sid`. Follow the existing idempotent-success behavior for a missing or already unusable session where specified.

### Revocation

A security/server action transitions the session to `REVOKED`. Define the authority, trigger, audit event, user behavior, and terminal retention before exposing a revocation workflow.

## 7. Worked example: Forgot Password

```text
POST start
  -> create auth-flow session and CSRF security context

POST destination/send
  -> session + CSRF + Origin/Referer + idempotency

POST resend
  -> same validations; update session-bound OTP state

POST verify
  -> same validations; transition to reset authorization

POST complete
  -> session + CSRF + one-time reset authorization
  -> call TAS/APIM reset operation
  -> consume authorization and complete session

POST cancel
  -> validate as required
  -> cancel server session
  -> clear auth-flow and XSRF cookies
```

Forgot Password reuses the session/CSRF infrastructure but owns its states, protected context, reset authorization, downstream completion, and error/navigation contract.

## 8. Pull-request checklist

- [ ] Correct session family selected
- [ ] State machine and terminal behavior documented
- [ ] Session and CSRF created as one security context
- [ ] Raw CSRF token not persisted
- [ ] Session cookie HttpOnly
- [ ] XSRF cookie non-HttpOnly
- [ ] Origin/Referer policy applied
- [ ] Idempotency applied separately where required
- [ ] Concurrency and completion races handled
- [ ] Cookie set/clear symmetry defined
- [ ] Idle and absolute expiry distinguished
- [ ] No PII/secrets in Redis keys
- [ ] No session/token values in logs
- [ ] Rotation from pre-auth to authenticated CSRF tested
- [ ] Account-Ref matched to server-side session accounts
- [ ] Fail-first TDD evidence included
- [ ] Operations document updated
- [ ] Commit message included

Suggested commit message:

```text
docs(auth): consolidate session and CSRF security guidance
```
