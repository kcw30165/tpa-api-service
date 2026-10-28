# Session and CSRF Architecture and Lifecycle

## 1. Architectural intent

The capability protects browser workflows by combining server-side session state with a CSRF token bound to that state. Session, CSRF, Origin/Referer validation, cookie transport, idempotency, and account context solve different threats and must not be substituted for one another.

```text
adapter/in/web
  Controllers, filters, request mapping, cookie writers, exception handlers
        |
application services
  Session lifecycle, protected context loading, CSRF validation,
  concurrency, authenticated-session establishment, logout/revoke
        |
application ports
  AuthFlowSessionPort, AuthenticatedSessionPort, protection,
  expiry refresh, cancellation, lock, and context ports
        |
adapter/out
  Redis persistence, JSON mapping, AES-GCM protection
```

## 2. Security controls and responsibilities

### Session

Identifies and stores the server-side workflow or authenticated security context. Cookie possession alone does not prove the session is active or authorized.

### CSRF/XSRF token

Proves that the browser request possesses the token bound to the loaded session. The server stores a hash rather than the raw value.

### Origin/Referer validation

Checks request provenance as defense in depth. A valid token does not permit an unapproved Origin.

### Idempotency

Prevents duplicate processing and supports safe replay of a completed result. It does not prove browser provenance and is not a CSRF control.

### Account-Ref

Selects one account already authorized inside the authenticated session. It does not authenticate the user and must be matched against server-side session state.

## 3. Pre-authentication auth-flow context

`AuthFlowSession` is a temporary, scenario-aware session used before portal authentication.

Typical state includes:

- Strong opaque session ID
- Scenario such as Login or Registration
- Workflow status
- CSRF token hash
- Active OTP challenge ID
- Protected scenario context or attributes
- Created, updated, interaction, and expiry timestamps

The session port supports create, load, update, touch, complete, cancel, and delete. Scenario adapters add OTP binding, verification transitions, expiry refresh, and cancellation behavior.

### 3.1 Creation and CSRF binding

```text
Start workflow
   -> enforce concurrency policy
   -> generate auth-flow session ID
   -> generate raw CSRF token with SecureRandom
   -> hash CSRF token with SHA-256
   -> store session and hash with TTL
   -> set HttpOnly auth_flow_sid
   -> expose raw token through approved XSRF transport
```

The raw CSRF token is not persisted. The session ID and CSRF token are separate values with separate browser exposure rules.

### 3.2 Protected mutation

```text
Request contains:
  auth_flow_sid cookie
  X-XSRF-TOKEN header
  Origin / Referer
  Idempotency-Key where required
  X-Request-Id
        |
Protected context loader:
  load session
  reject expired/terminal/missing session
  validate scenario and workflow status
  hash and validate CSRF token
  validate Origin/Referer
  decrypt and validate protected context
  optionally refresh sliding expiry
        |
Application operation executes
```

Controller presence checks provide deterministic transport errors, but application-layer validation is authoritative.

### 3.3 Lifecycle

```text
STARTED
  -> scenario states such as OTP_SENT / OTP_VERIFIED
  -> COMPLETED
  -> CANCELLED
  -> EXPIRED
```

Terminal sessions cannot be revived by cookie reuse or touch. Sliding expiry, where enabled, is refreshed only after successful validation.

### 3.4 Concurrency and completion

Configuration supports single-active-flow or multi-flow behavior and policies such as replacing an existing flow or allowing parallel flows. The reviewed configuration uses a single-active-flow/replacement approach.

Final operations that must execute once use a completion lock. The lock has an ownership token and atomic release to avoid one worker releasing another worker's lock.

### 3.5 Completion, cancellation, and replacement

The lifecycle result controls server state and browser cleanup. Depending on the transition:

- Complete, cancel, expire, or delete the session
- Clear `auth_flow_sid`
- Clear the pre-auth XSRF cookie
- Reject requests from an older/replaced browser tab

Cookie clearing must use the same name, path, Secure, and SameSite policy used for issuance.

## 4. Authenticated portal context

`AuthenticatedSession` is created only after Login identity verification and accessible-account authorization succeed.

It contains:

- `AuthenticatedSessionId`
- `ActorContext`
- Authorized `AuthenticatedSessionAccount` entries
- CSRF token hash
- Status: `ACTIVE`, `LOGGED_OUT`, `REVOKED`, or `EXPIRED`
- `createdAt`
- `lastAccessedAt`
- `idleExpiresAt`
- `absoluteExpiresAt`

Account entries include generated account references and the business attributes required to construct `PortalAccessContext`. Duplicate account references are invalid.

### 4.1 Establishment after Login OTP

```text
Verified Login auth-flow
   -> resolve actor and accessible accounts
   -> generate sid_... authenticated session ID
   -> generate a new authenticated CSRF token
   -> store only the token hash
   -> calculate idle and absolute expiry
   -> protect sensitive session state
   -> persist in Redis
   -> set HttpOnly portal_sid
   -> return raw authenticated CSRF token once
   -> clear pre-auth cookies and terminate auth-flow state
```

This is a security-context rotation. The pre-auth CSRF token is not valid for the authenticated session.

### 4.2 Authenticated validation and context resolution

For an account-specific request, `PortalAccessContextWebFilter` obtains:

- The authenticated session cookie
- `X-XSRF-TOKEN`
- `Account-Ref`
- `X-Request-Id`

It validates and touches the authenticated session, matches `Account-Ref` against the accounts stored in that session, and places a per-request `PortalAccessContext` into the exchange and Reactor context.

The client cannot establish authorization by supplying policy number, certificate number, account environment, trust code, or actor identity directly.

### 4.3 Idle and absolute expiry

```text
ACTIVE session
   -> valid access: update lastAccessedAt and idleExpiresAt
   -> idle timeout reached: EXPIRED
   -> absolute timeout reached: EXPIRED
```

Touch can extend idle expiry but never extend past absolute expiry.

Authenticated sliding and terminal writes share the revision-checked
`AuthenticatedSessionConcurrencyService.updateIfEligible` path. Each conflict
reloads state and reevaluates nonterminal eligibility; an expired ACTIVE session
can therefore transition to EXPIRED, while a terminal session cannot be revived.
The lifecycle CAS script validates an absolute epoch-millisecond deadline against
Redis TIME and applies PEXPIREAT in the same operation. Network/queue delay cannot
turn the deadline into a renewed relative lifetime.

Feature mutations continue to use `updateIfUsable` with in-script PTTL
preservation. They do not slide expiry and cannot overwrite a concurrent touch,
terminal transition, CSRF rotation or other feature-state mutation without
reloading the newer revision. All replacement snapshots preserve unrelated
featureState entries.

### 4.4 Logout, revocation, and terminal retention

```text
ACTIVE
  -> LOGGED_OUT  # user-driven
  -> REVOKED     # server/security-driven
  -> EXPIRED     # time-driven

Terminal record
  -> retained for configured interval
  -> deleted
```

Terminal retention supports deterministic repeat handling and investigation without allowing session reuse. Logout clears `portal_sid`; already-missing or unusable session behavior follows the endpoint's idempotent logout contract.

Retention uses the first terminal mutation attempt's timestamp plus configured
retention, fixed across revision retries. Repeat calls observing terminal state
perform no write and do not extend retention. Missing/corrupt state, elapsed
deadlines and uncertain write outcomes fail closed; only an explicit revision
conflict is retried (at most two additional attempts).

## 5. CSRF implementation

### 5.1 Components

- `CsrfTokenGenerator` / secure random implementation
- `CsrfTokenHasher` / SHA-256 implementation
- `CsrfTokenValidationService`
- `AuthFlowSessionCsrfService`
- `OriginRefererValidator`
- XSRF transport policy and cookie instruction services
- Authenticated-session validation service

The reviewed implementation generates high-entropy tokens and records only their hashes in session state.

### 5.2 Validation outcomes

Internal validation distinguishes:

- Token missing
- Session hash missing
- Token mismatch
- Valid token

Public responses must not reveal which secret comparison failed. Workflow mappers produce approved CSRF/origin errors, normally targeting `PAGE`, while HTTP translation maps protected-request failures to the approved status such as `403 Forbidden`.

### 5.3 Cookie/header distinction

```text
Session cookie:
  HttpOnly = true
  carries opaque session ID

XSRF cookie:
  HttpOnly = false
  carries raw CSRF token for frontend header copying

Request header:
  X-XSRF-TOKEN
  carries submitted CSRF token
```

Both cookies remain Secure and use the configured SameSite policy. SameSite supplements token validation; it does not replace it.



### 5.4 Page-refresh token recovery

When frontend memory loses the raw CSRF token, the browser calls `POST /api/v1/auth/csrf-token/refresh` with credentials, `X-Request-Id`, and an approved `Origin` or `Referer`. The endpoint does not require the previous `X-XSRF-TOKEN` value.

```text
Browser loses raw CSRF token
  -> select portal_sid when present; otherwise auth_flow_sid
  -> reject duplicate selected cookies and prohibit authenticated-to-pre-auth downgrade
  -> validate active session state and request provenance
  -> atomically or revision-safely replace only the stored CSRF hash
  -> preserve unrelated session state and remaining lifetime
  -> return result.data.sessionType and result.data.csrfToken
```

The response is non-cacheable and does not publish an XSRF cookie. Protected mutations continue to submit the current raw token through `X-XSRF-TOKEN`.

## 6. Redis persistence and protection

Session adapters are reactive and expose application ports rather than Redis types. They use:

- Capability-scoped, PII-safe key factories
- TTL on every temporary record
- Explicit timestamp serialization
- Safe exception translation
- Atomic creation/mutation where required
- Distributed completion locks

Authenticated actor/account/CSRF-hash state is protected with AES-GCM before Redis persistence. Lifecycle metadata required for expiry can remain in a validated technical envelope and is authenticated as part of the protection design.

## 7. Spring Security and application CSRF

Framework-level CSRF being disabled does not mean the application is unprotected. This codebase implements application-level session-bound CSRF validation. Any change to framework security must preserve the existing application-level contract or deliberately migrate it with full browser and API compatibility testing.

## 8. Security invariants

- Session and CSRF values use cryptographically secure generation.
- Raw CSRF tokens are not persisted.
- CSRF hashes are not returned to clients.
- Session IDs, tokens, hashes, cookies, and protected contexts are never logged.
- Session cookies are HttpOnly; XSRF cookies are deliberately non-HttpOnly.
- Every token is bound to one session/security context.
- A new authenticated session receives a new CSRF token.
- Expired and terminal sessions reject protected requests.
- Idle extension never passes absolute expiry.
- Origin/Referer checks remain enabled according to policy.
- GET operations remain free of state-changing side effects.
- Redis keys contain opaque technical IDs only.
- Account authorization is resolved from server-side session state.

## 9. Extension boundaries

### Reuse directly

- Secure session and CSRF generators
- SHA-256 token hasher
- Session and CSRF validation services
- Session lifecycle and TTL calculation
- Session ports and Redis adapters
- Cookie instruction/writer patterns
- Context-protection adapters
- Concurrency and completion-lock services
- `PortalAccessContext` resolution pattern

### Configure or extend

- Scenario TTL and statuses
- Concurrency policy
- Cookie/header names and paths
- Allowed origins
- Idle/absolute/terminal durations
- Protection key/version policy
- Error and navigation contracts

### Implement per workflow

- Entry and completion conditions
- Protected context contents
- Allowed state transitions
- CSRF issuance/cleanup timing within the shared lifecycle
- Success result and downstream behavior
