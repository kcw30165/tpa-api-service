# CSRF Token Refresh Phase 1 Review and Design Lock

## Status

Phase 1 static characterization passed and did not modify tracked repository content. The evidence establishes enough of the current design to lock the principal implementation direction, while a small number of source-level details must still be verified when preparing the deterministic implementation script.

## Confirmed current-state findings

### 1. Payload transport is the canonical current contract

The current public contracts return a raw `csrfToken` in response data for Login start, Login OTP verification after authentication, Registration start, Forgot Password start, and Username Recovery start. Their `toString()` implementations redact the token. The new endpoint must therefore return the rotated raw token in the payload and must not introduce an XSRF cookie.

### 2. Pre-auth rotation already exists

`AuthFlowSessionCsrfService` already exposes `rotateForSession(RotateAuthFlowSessionCsrfCommand)`. It loads the auth-flow session, generates a new token, hashes it, reconstructs the session with the replacement hash, and writes through `RevisionAwareAuthFlowSessionWriter`.

This means Phase 2 should reuse the existing pre-auth rotation service rather than introduce a second pre-auth rotation port or duplicate token generation and hashing.

### 3. Auth-flow persistence is revision-aware

`AuthFlowSessionPort` exposes snapshot loading and revisioned mutation contracts. Production `RedisAuthFlowSessionAdapter` uses revision operations, secure revisions, and mutation outcomes. Existing revision adapter and contract tests are available.

The refresh orchestration must treat a non-applied revision outcome as a conflict. It must not return the generated raw token unless the revisioned replacement was confirmed as applied.

### 4. Authenticated CSRF compare-and-set capability already exists, but its boundary is too narrow

The codebase already contains:

- `RotateAuthenticatedSessionCsrfHashCommand`
- `AuthenticatedSessionCsrfRotationResult`
- `AuthenticatedSessionCsrfRotationStatus`
- `AuthenticatedLoginReplaySessionPort.rotateCsrfHashIfCurrent(...)`
- `RedisAuthenticatedLoginReplaySessionAdapter.rotateCsrfHashIfCurrent(...)`
- `RecoverCommittedLoginService`, which generates a new token and uses expected-hash compare-and-set rotation

The Redis adapter uses a compare-and-set script and preserves the existing TTL. However, the capability is named and packaged as Login replay infrastructure. The refresh endpoint should not depend directly on a Login replay port.

Design lock: extract or introduce a capability-scoped authenticated-session CSRF rotation port and adapt the existing Redis compare-and-set implementation to it. Preserve the Login replay behavior through the new shared boundary rather than creating a second Redis implementation.

### 5. The canonical provenance validator exists

`OriginRefererValidator` is the canonical application service. It validates exact normalized origins, supports Referer fallback, rejects malformed values, and fails when neither value is available. The refresh application service must reuse it.

Allowed origins should come from the existing configured CORS/auth-flow policy source. They must not be hardcoded in the controller.

### 6. Spring Security framework CSRF is disabled intentionally

`SecurityConfig` disables framework CSRF. The application implements session-bound CSRF itself. The refresh endpoint therefore requires no Spring Security CSRF migration.

### 7. Existing authenticated validation cannot be reused directly

`AuthenticatedSessionValidationService` requires a submitted CSRF token and invokes `loadAndTouch`. The refresh endpoint cannot call this service because FE has lost the raw token.

The new authenticated rotation path must:

1. Load and validate the authenticated session without requiring the old raw token.
2. Reject non-active, idle-expired, absolute-expired, missing, or corrupt sessions.
3. Generate and hash a new token.
4. Compare-and-set the stored hash using the current hash as the expected value.
5. Return the raw token only after confirmed rotation.

### 8. Authenticated rotation should preserve expiry rather than touch

The existing Login replay rotation compares and replaces the stored representation while preserving its TTL. This is the closest existing behavior to page-refresh token recovery.

Design lock: CSRF refresh does not count as authenticated business activity and must not extend idle or absolute expiry. The operation validates expiry at the current instant, rotates the hash, and preserves lifecycle timestamps and Redis TTL.

The pre-auth service currently accepts a TTL parameter. The implementation must pass the remaining session lifetime or otherwise prove that the existing rotation path does not extend the flow beyond its existing expiry. It must not reset a scenario to its full configured TTL.

### 9. Portal cookie precedence and no fallback remain required

If `portal_sid` is present, authenticated resolution is selected. Any malformed, missing, terminal, expired, corrupt, conflicting, or failed authenticated-session outcome is returned without trying `auth_flow_sid`.

The pre-auth path is selected only when `portal_sid` is absent and `auth_flow_sid` is present.

### 10. Existing filter behavior requires route-specific handling

`PortalAccessContextWebFilter` rejects selected-account requests when either the portal session or `X-XSRF-TOKEN` header is missing. The refresh endpoint cannot pass through a rule that requires the old token.

The endpoint must be outside the selected-account filter scope, or the filter must explicitly bypass exactly `POST /api/v1/auth/csrf-token/refresh`. A broad `/api/v1/auth/**` bypass is already consistent with the endpoint location, but the actual filter registration/path matcher must be verified before writing replacements.

`RequiredRequestIdWebFilter` applies the existing `X-Request-Id` contract and should remain unchanged unless its path logic excludes the new route unexpectedly.

## Locked API contract

```http
POST /api/v1/auth/csrf-token/refresh
```

No request body and no `X-XSRF-TOKEN` header are required.

Successful result data:

```json
{
  "sessionType": "AUTHENTICATED",
  "csrfToken": "<new-raw-token>"
}
```

or:

```json
{
  "sessionType": "PRE_AUTH",
  "csrfToken": "<new-raw-token>"
}
```

The data is wrapped in the existing `MutationResponse<T>` success envelope. The endpoint adds `Cache-Control: no-store` and `Pragma: no-cache` and does not set an XSRF cookie.

## Locked implementation boundaries

### New inbound contract

- `RefreshCsrfTokenUseCase`
- `RefreshCsrfTokenCommand`
- `RefreshCsrfTokenResult`
- Public session type with `PRE_AUTH` and `AUTHENTICATED`

### New web boundary

- `CsrfTokenRefreshController`
- Payload DTO with `sessionType` and `csrfToken`
- Web mapper or HTTP response component following current controller architecture rules

### Reused pre-auth capability

- `AuthFlowSessionCsrfService.rotateForSession(...)`
- Existing secure generator and SHA-256 hasher
- Existing revision-aware writer and Redis revision operations

### Shared authenticated capability

Refactor the Login-replay-specific rotation boundary into a shared authenticated-session CSRF rotation capability. Reuse the existing compare-and-set Redis logic and keep Login replay behavior working through the shared abstraction.

## Fail-first test scope for the next implementation round

### Application orchestration

- Authenticated cookie only selects authenticated rotation.
- Pre-auth cookie only selects pre-auth rotation.
- Both cookies select authenticated rotation.
- Invalid authenticated cookie never falls back.
- Missing both cookies fails.
- Rejected Origin or Referer prevents rotation.
- A conflict returns no raw token.
- A persistence/system failure returns no raw token.

### Pre-auth rotation

- Existing session state is preserved except for CSRF hash and revision.
- Remaining expiry is not extended.
- Terminal and expired sessions are rejected.
- Revision conflict returns no token.
- New token validates and old token fails.

### Authenticated rotation

- Active session rotates successfully.
- Actor and account state are preserved.
- Lifecycle timestamps, absolute expiry, and Redis TTL are preserved.
- Logged-out, revoked, idle-expired, and absolute-expired sessions are rejected.
- Compare-and-set conflict returns no token.
- Concurrent logout or another rotation cannot be overwritten.

### Web contract

- Exact POST mapping.
- No request body required.
- No old CSRF header required.
- Request ID remains required.
- Payload contains `sessionType` and redacted-safe `csrfToken` DTO behavior.
- No XSRF cookie is created.
- `Cache-Control: no-store` and `Pragma: no-cache` are present.

## Remaining implementation-time checks

Before generating deterministic replacements, inspect the full current source for:

1. The exact `PortalAccessContextWebFilter` route matcher or registration.
2. The exact authenticated Redis session key and JSON/protection path used by the Login replay adapter.
3. The complete `AuthFlowSessionCsrfService.bindToken` TTL behavior.
4. Existing public session error keys and HTTP mappings suitable for refresh.
5. Existing controller architecture tests and package conventions.
6. Duplicate-cookie behavior in current cookie helpers.

These are implementation-detail checks, not unresolved architectural decisions.

## Phase 1 conclusion

Phase 1 is complete. No broad CSRF refactor is required. The codebase already contains both core rotation mechanisms:

- revision-aware pre-auth rotation in `AuthFlowSessionCsrfService`
- expected-hash authenticated rotation in Login replay infrastructure

The implementation should add one session-selecting application use case, expose it through a payload-based refresh endpoint, and refactor the authenticated rotation boundary just enough to make the existing compare-and-set capability reusable.

## Recommended Phase 1 commit message

```text
docs(auth): lock CSRF token refresh implementation design
```

## Superseded response-envelope detail

The original Phase 1 design showed `sessionType` and `csrfToken` directly under `result`. Final contract alignment supersedes that detail. Successful responses now expose the payload under `result.data`, specifically `result.data.sessionType` and `result.data.csrfToken`. The security, rotation, persistence, and no-cookie decisions in this design lock remain unchanged.
