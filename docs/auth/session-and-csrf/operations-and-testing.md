# Session and CSRF Operations, Configuration, and Testing

## 1. Configuration reference

### 1.1 Auth-flow session

Review per environment:

- Session ID prefix and entropy bytes
- Cookie name and path
- Cookie max age
- HttpOnly, Secure, and SameSite policy
- Scenario enabled/required flags
- Scenario TTLs
- Concurrency mode
- New-flow policy
- Completion-lock lease
- Sliding expiry behavior

The reviewed configuration uses `auth_flow_sid` on `/api/v1/auth`, with Secure, HttpOnly, SameSite `None`, and scenario-specific TTLs. Confirm the deployed profile.

### 1.2 Pre-auth XSRF

Review:

- Cookie name
- Header name
- Cookie path and max age
- Secure and SameSite policy
- Mandatory non-HttpOnly behavior
- Origin validation enabled flag
- Allowed frontend origins and proxy provenance rules

The reviewed convention is `XSRF-TOKEN` plus `X-XSRF-TOKEN`, scoped to `/api/v1/auth` for the pre-auth cookie.

### 1.3 Authenticated session

Review:

- Authenticated ID prefix and entropy
- Cookie name and path
- Cookie max age, HttpOnly, Secure, and SameSite
- Idle timeout
- Absolute timeout
- Terminal retention
- AES-GCM key version and secret source
- Key rotation/migration policy

The reviewed configuration uses `portal_sid` on `/api`, with a sliding idle timeout, absolute maximum lifetime, and brief terminal retention.

### 1.4 Redis

Review:

- Sentinel master and nodes
- SSL/mTLS bundle and certificate validity
- Authentication secret source
- Key prefix
- Command timeout
- Connection-pool capacity/wait
- Memory and eviction policy
- Time synchronization
- Failover and latency alerts

Every session record, completion lock, and related temporary security artifact must have an appropriate TTL.

### 1.5 CORS and proxy

Confirm exact allowed origins, credentialed-request support, allowed headers including `X-XSRF-TOKEN`, public scheme/host, forwarded-header trust, TLS termination, and cookie domain/path behavior. Never permit wildcard credentialed origins.

## 2. Endpoint security expectations

### Protected pre-auth mutations

Login OTP send/resend/verify and Registration OTP, username, completion, and cancellation operations use the auth-flow cookie and XSRF header. Origin/Referer and idempotency requirements depend on the operation. The global API contract also requires `X-Request-Id`.

### Authenticated requests

Authenticated context is resolved from `portal_sid`; protected mutations validate authenticated CSRF. Account-specific APIs also require an `Account-Ref` that belongs to the session.



### CSRF token refresh

`POST /api/v1/auth/csrf-token/refresh` rotates the token for the selected authenticated or pre-authentication session. The endpoint requires browser credentials, `X-Request-Id`, and approved provenance, but not the previous `X-XSRF-TOKEN`. Success returns `sessionType` and `csrfToken` under `result.data`. Every response is non-cacheable, and the endpoint does not set an XSRF cookie.

### Logout

Logout validates authenticated session/CSRF when a session exists, transitions terminal state, and clears the authenticated cookie. Missing-session behavior follows the idempotent logout contract.

## 3. Troubleshooting runbook

### Auth-flow cookie missing

Check:

- Browser credentials mode
- Cookie path/domain
- Secure over HTTPS
- SameSite and cross-origin behavior
- Whether the flow was replaced
- Session/cookie expiry
- Proxy handling of `Set-Cookie`

Do not bypass server-side session validation.

### XSRF cookie not visible to frontend

Check that it is intentionally non-HttpOnly, uses the expected path/domain, was issued over HTTPS when Secure, and is not blocked by SameSite/CORS/browser policy.

### XSRF header present but rejected

Likely causes:

- Token belongs to a replaced or different session
- Pre-auth token used after authenticated-session creation
- Session expired or became terminal
- Header value was altered/encoded
- Origin or Referer failed validation
- Server-side CSRF hash is absent/corrupt

Do not log token values while investigating.

### Works locally but fails behind proxy

Check external origin, scheme, host, TLS termination, forwarded-header trust, cookie scope, CORS credentials, and allowed headers. Do not weaken Origin validation as a quick fix.

### Intermittent failures across tabs

A single-active-flow/replacement policy can invalidate the older tab's session and CSRF token. The expected client recovery is usually to restart the flow.

### Session expires unexpectedly

Compare:

- Application clock and Redis TTL
- Scenario TTL
- Idle timeout
- Absolute timeout
- Sliding touch behavior
- Whether validation failed before touch

Touch must never extend beyond absolute expiry.

### Account-Ref rejected

Verify that the account reference was returned by the current authenticated session and remains present in its server-side account list. Do not resolve access using client-supplied business account fields.

### Logout/cancellation does not clear cookies

Ensure clear uses the same cookie name and path as issuance, with zero max age and matching security attributes. Verify the use-case result asked the web layer to clear the correct cookies.



### CSRF refresh rejected

Check the selected cookie family, duplicate cookie values, session lifecycle state, Origin/Referer policy, request ID, concurrent rotation conflicts, and Redis availability. A present `portal_sid` always takes precedence and cannot downgrade to `auth_flow_sid`. The request must not rely on the previous CSRF token. Never log the cookie value, raw token, stored hash, or persistence key while diagnosing the failure.

### Redis unavailable

Check Sentinel health/failover, mTLS, certificate expiry, DNS/network, pool exhaustion, and timeout. Public errors and logs must not expose addresses, credentials, certificate paths, keys, or raw Redis keys.

## 4. Logging and observability

### Safe fields

- Request/correlation ID
- Scenario
- Operation
- Session status category
- Validation outcome category
- Public error code
- HTTP status
- Duration
- Aggregate account count where approved

### Forbidden fields

- Session ID or cookie value
- Raw CSRF token or hash
- XSRF cookie/header value
- Actor or account identifiers
- Protected context/ciphertext diagnostics
- Encryption keys/nonces/tags
- Redis credentials or certificate material

Recommended structured events:

```text
auth_flow.session.created
auth_flow.session.replaced
auth_flow.session.expired
auth_flow.session.completed
auth_flow.session.cancelled
csrf.token.issued
csrf.validation.failed
csrf.origin.rejected
csrf.token.rotated
csrf.cookie.cleared
authenticated_session.created
authenticated_session.touched
authenticated_session.expired
authenticated_session.logged_out
authenticated_session.revoked
session.context.rejected
```

Events must use correlation IDs, not security tokens or session identifiers.

## 5. Test strategy

### 5.1 Domain and configuration tests

- Session ID format and entropy constraints
- CSRF generator entropy
- Deterministic hashing for identical input
- Different hashes for different tokens
- Cookie-policy validation
- XSRF cookie must be non-HttpOnly
- Session cookie must be HttpOnly
- Positive TTLs and timeout ordering
- Duplicate account-reference rejection
- Timestamp validation
- Configuration startup failure for unsafe/missing values

### 5.2 Auth-flow lifecycle tests

- Create session and bind CSRF hash
- Load/update/touch
- Scenario mismatch
- Invalid workflow state
- Exact expiry boundary
- Sliding refresh only after successful validation
- Completion and cancellation
- Terminal session cannot be revived
- Existing-flow replacement
- Missing/already-terminal existing flow

### 5.3 CSRF validation tests

- Valid token and session hash
- Missing submitted token
- Missing stored hash
- Mismatch
- Token from another session
- Stale token after flow replacement
- Pre-auth token rejected after authenticated transition
- Expired/terminal session
- Origin accepted/rejected
- Referer fallback where policy permits
- Malformed or missing provenance

### 5.4 Authenticated lifecycle tests

- Establish after verified Login and account resolution
- New CSRF token generated at transition
- Raw token returned once; hash persisted
- Load and touch
- Idle-expiry boundary
- Absolute-expiry boundary
- Touch capped by absolute expiry
- Logout
- Revoke
- Terminal retention
- Missing/already-terminal behavior

### 5.5 Concurrency and locking tests

- Single-active-flow creation
- Replace-existing decision outcomes
- Concurrent session updates
- Concurrent completion attempts
- One completion-lock owner
- Ownership-safe lock release
- Concurrent authenticated touches and logout
- Old-tab request after replacement

The shared-session prerequisite has an opt-in real-Redis suite:
`AuthenticatedSessionConditionalWriteRedisTest`. Supply
`-Dm15.redis.server=/absolute/path/to/redis-server`; if needed, supply
`-Dm15.redis.libraryPath=/absolute/path/to/shared/libraries`. It starts a dedicated
loopback process on a free port, disables persistence, uses only synthetic
sessions, and terminates that process after the suite. It never uses the
application's Redis configuration. Without the binary property the suite is
skipped; milestone evidence must explicitly enable it.

The suite forces interleavings before the actual CAS script, rather than relying
on thread scheduling: terminal-write versus usable mutation, touch versus each
terminal state, and touch versus an independent feature tombstone. It reads
actual PTTL and exact PEXPIRETIME for sliding extension, terminal shortening,
absolute cap and preserve-current writes. Redis TIME barriers cover delayed and
elapsed commands; clock changes cover retry deadlines and negative skew. Lost
acknowledgement injection after an applied write proves fail-closed/no retry.
Actual CSRF rotation and both cache services are exercised together to prove
unrelated featureState and expiry preservation.

This suite was introduced against isolated Redis 8.0.5. PEXPIRETIME is used by
the test oracle only, not by production scripts. The production scripts use
TIME/PEXPIREAT atomically and require operationally synchronized Redis/application
clocks. The repository does not pin the deployed Redis version; verify the
supported deployment version and Sentinel/mTLS/failover separately rather than
equating the isolated suite with deployment certification.

The M1.5 contention regression batches twelve concurrent touches at the actual
CAS boundary, then waits for every Redis result in a round before releasing
retries. With twelve/eleven/ten competing requests over three rounds, only three
touch writes apply, thirty conflict, and nine requests necessarily exhaust:
all twelve must return ACTIVE by observing still-usable current state. Variants
commit logout/revoke/discovered expiry or a feature-state mutation before the
final CAS batch. Terminal state cannot be restored; applied feature state must
be present in the exhausted requests' fresh observations. Additional final-load
fixtures cover expired ACTIVE, missing/corrupt records and load errors.
Explicit exhaustion tests retain FAILED for logout/revoke and the shared
REVISION_CONFLICT_EXHAUSTED/no-session contract for existing usable mutations.
No technical/uncertain-write error is treated as touch contention.

### 5.6 Redis and protection tests

- PII-safe key format
- TTL on every write
- JSON round trip
- Auth-flow protected-context round trip
- Authenticated AES-GCM round trip
- Tampered ciphertext/AAD/tag rejected
- Unsupported/old key version behavior
- Atomic create/mutation
- Safe exception translation
- Sentinel failover in an integration environment

### 5.7 Web, filter, and cookie tests

- Cookie attributes on set and clear
- Set/clear path symmetry
- Missing session and CSRF errors
- Header presence still requires service validation
- Origin/Referer passed to command
- Correct HTTP status and mutation envelope
- `X-Request-Id` propagation
- `Account-Ref` matching
- `PortalAccessContext` exists only for request duration
- Logout idempotency

### 5.8 Browser and security tests

- Approved cross-origin credentialed request
- Malicious cross-site form submission rejected
- Malicious fetch rejected
- Session fixation attempt
- Cookie theft/replay behavior
- CSRF copied across sessions
- Account-Ref copied across sessions
- Expired/terminal replay
- Multiple tabs under concurrency policy
- Reverse-proxy and TLS deployment
- Log/APM redaction assertions
- GET endpoints have no state-changing side effects



### 5.9 CSRF refresh tests

- Success for authenticated and pre-authentication sessions
- Authenticated-cookie precedence when both cookies are present
- No fallback when the authenticated cookie is blank, malformed, missing in storage, expired, or terminal
- Duplicate selected-cookie rejection
- Missing-cookie and rejected-provenance responses
- Atomic or revision-aware conflict behavior
- Persistence failure returns no raw token
- Success shape uses `result.data.sessionType` and `result.data.csrfToken`
- `Cache-Control: no-store`, `Pragma: no-cache`, and request-ID echo
- No `Set-Cookie` response and no old `X-XSRF-TOKEN` requirement
- No session identifier, token, hash, Redis, or encryption detail leakage

## 6. Production readiness checklist

- [ ] Session scenarios and state transitions approved
- [ ] Scenario TTLs and concurrency policy approved
- [ ] Idle, absolute, and terminal durations approved
- [ ] Cookie attributes tested behind the real frontend/proxy/domain
- [ ] XSRF and session-cookie HttpOnly distinction verified
- [ ] Exact CORS origins and credentials behavior tested
- [ ] Origin/Referer policy enabled and tested
- [ ] Redis Sentinel and mTLS tested
- [ ] All temporary records have TTL
- [ ] Protection keys sourced from secret management
- [ ] Key rotation behavior documented and tested
- [ ] Session, CSRF, actor, and account values absent from logs/APM
- [ ] Auth-flow to authenticated-session rotation tested
- [ ] Context filter tested with multiple accounts
- [ ] Logout, revoke, replacement, and expiry monitoring enabled
- [ ] Failover, concurrency, and clock-skew tests complete
- [ ] Support runbook exercised by someone other than the implementer
- [ ] Fail-first TDD evidence included in the change record
