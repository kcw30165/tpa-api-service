# CSRF token refresh returns 409 CSRF_REFRESH_CONFLICT

Branch: `fix/csrf-token-refresh-conflict` (from `origin/dev` `a08d2c0d`).
Endpoint: `POST /api/v1/auth/csrf-token/refresh`.

## 1. Symptom

Every refresh returns HTTP 409, `BUSINESS_REJECTED`, `CSRF_REFRESH_CONFLICT`
(`err.security.csrf.refresh-conflict`). Reported for both the authenticated
(`portal_sid`) and the pre-auth (`auth_flow_sid`) session. Present on `origin/dev`,
not a branch regression (evidence 001).

## 2. Root cause

Labels: confirmed = read in source; suspected = consistent with source, not yet reproduced.

### 2.1 Authenticated path (suspected, strong)

`AuthenticatedSessionCsrfRefreshService` loads the session through
`RedisAuthenticatedSessionAdapter.load` (JSON -> `ProtectedAuthenticatedSession`) and
rotates through `RedisAuthenticatedLoginReplaySessionAdapter.rotateCsrfHashIfCurrent`
(confirmed). That adapter re-serializes the loaded envelope and runs

```lua
if redis.call('get', KEYS[1]) == ARGV[1] then ... return 1 else return 0 end
```

i.e. a whole-string compare of the stored value against a re-serialization (confirmed).
`ProtectedAuthenticatedSession.Data` stores the encrypted attributes in `Map.copyOf(...)`
(confirmed), whose iteration order is unspecified, depends on a per-JVM salt and on
insertion order. The store path inserts in protector order; the load path inserts in
stored-JSON order. The re-serialized string can therefore differ from the stored string
in attribute order only, the compare fails, `0` maps to `CONFLICT`, HTTP 409.
Across pods or restarts the order differs whenever the salt differs.

Keys are not the cause: all three adapters use `<prefix>:auth:authenticated-session:<sid>`
(confirmed, evidence 002).

`RecoverCommittedLoginService` (committed-login replay) uses the same rotation port and
has the same defect (confirmed).

### 2.2 Pre-auth path (no code cause found)

`AuthFlowSessionCsrfService.bindToken` -> `RevisionAwareAuthFlowSessionWriter.updateExpected`
-> `replace-revisioned.lua` compares only `sessionRevision` (confirmed). No defect was
found. The controller always selects `portal_sid` when that cookie is present
(confirmed), so a pre-auth call made with a leftover `portal_sid` cookie takes the
authenticated path and hits 2.1 (suspected). Pending: cookie names of the failing
pre-auth request.

## 3. Decision

Replace the whole-string CAS with the existing revision-based CAS
(`AuthenticatedSessionConcurrencyService` + `RedisAuthenticatedSessionCasUpdateAdapter`
+ `redis/authenticated-session/update-if-current.lua`), already wired and used by
Fund Price. It compares only the top-level plaintext `sessionRevision`, preserves the
remaining TTL (PTTL), and retries a genuine revision conflict against reloaded state.
This supersedes the earlier "do not refactor working login/CSRF code" decision
(approved by Nick, 2026-10-14).

Refresh semantics kept: lifecycle timestamps and expiry unchanged, only the CSRF hash
changes (plus a new revision); raw token returned only after the write is applied.

Outcome mapping (authenticated refresh):

| Concurrency outcome | Refresh status | HTTP |
|---|---|---|
| `APPLIED` | `SUCCESS` | 200 |
| `SESSION_NOT_FOUND` | `SESSION_MISSING` | 401 |
| `SESSION_NOT_USABLE` | `SESSION_NOT_USABLE` | 401 |
| `REVISION_CONFLICT_EXHAUSTED` | `CONFLICT` | 409 |
| `SYSTEM_FAILURE` / error | `SYSTEM_FAILURE` | 503 |
| envelope cannot be decrypted | `SESSION_INVALID` | 401 |

## 4. Milestones

### M1 - Authenticated refresh on revision CAS

- 1.1 Fail-first: reproduce the 409 with real `RedisAuthenticatedSessionJsonMapper`,
  real `AesGcmAuthenticatedSessionProtector`, real Redis adapters, and a Redis fake that
  emulates the Lua semantics; the stored record carries the same attributes in a
  different order (as written by another JVM). Expected today: `CONFLICT`.
  Implement: `AuthenticatedSessionCsrfRefreshService` uses
  `AuthenticatedSessionConcurrencyService`; update `CsrfTokenRefreshApplicationConfig`
  and existing unit tests; the reproduction then passes.
- Gates: sub-task gates, then milestone gates (full suite, coverage, smoke, Sonar).

### M2 - Committed-login replay on revision CAS and dead-code removal

- 2.1 Fail-first: same reproduction for `RecoverCommittedLoginService`.
  Implement on the revision CAS.
- 2.2 Remove the whole-string CAS: `rotateCsrfHashIfCurrent`, the inline `ROTATE` script,
  and, where no longer referenced, `AuthenticatedSessionCsrfRotationPort`,
  `RotateAuthenticatedSessionCsrfHashCommand`, `AuthenticatedSessionCsrfRotation*`.
  `AuthenticatedLoginReplaySessionPort` keeps `loadForReplay`. Update architecture tests
  that pin the removed names and `docs/auth/session-and-csrf/*`.

### M3 - Pre-auth (conditional)

Only if the cookie evidence shows a pre-auth 409 without `portal_sid`: separate
investigation and sub-task. Otherwise closed by M1.

## 5. Out of scope

Retry-After or response contract changes; frontend behavior; registration
Origin/Referer finding recorded in `csrf-refresh-after-expiry-investigation.md`.

## 6. M4 - Explicit session-type hint for ambiguous dual-session refresh (new milestone)

### 6.1 Scenario

Reported by Nick (2026-10-14, separate from the M1/M2/M3 investigation): a user is logged in
(valid `portal_sid`) in one browser context while concurrently running a separate pre-auth flow -
for example, Forgot Username in another tab - which holds its own valid `auth_flow_sid`. Both
cookies are sent together on a refresh call, and **both resolve to real, live sessions**. This is
not the M3 scenario (a *stale* `portal_sid` with nothing behind it); here the backend cannot tell
which of the two live sessions the caller actually means, because cookie presence alone is
ambiguous and conveys no intent (confirmed in conversation: cookies "tells the backend a session
exists" but not "which screen the user is looking at").

`RefreshCsrfTokenService.refresh()` currently commits unconditionally to the authenticated path
whenever `portal_sid` resolves (confirmed, script 020): "Selects authenticated context first and
never downgrades to pre-auth." That rule is correct for M3's stale-cookie case but gives the wrong
answer when the caller actually wanted the pre-auth session refreshed.

### 6.2 Decision (5 points, confirmed by Nick 2026-10-14)

1. Add an **optional** request field, not a required one and not a separate endpoint or URL path.
   One endpoint (`POST /api/v1/auth/csrf-token/refresh`) is touched.
2. Field name `sessionType`, JSON body, values matching the existing
   `CsrfRefreshSessionType` enum: `"AUTHENTICATED"` / `"PRE_AUTH"`. No new enum.
3. If the hint names a session type whose cookie is not actually present in the request
   (e.g. `sessionType: AUTHENTICATED` but no `portal_sid` sent), reject with `400 BAD_REQUEST`,
   following the existing `ambiguousCookie()` pattern in `CsrfTokenRefreshHttpResponses`. No
   silent fallback to whatever cookie happens to be present.
4. When the hint is omitted, behavior is byte-for-byte unchanged from M1/M2: authenticated wins
   when `portal_sid` resolves, exactly as today. This is purely additive; no existing caller is
   affected.
5. The duplicate-cookie check (`ambiguous(authenticated) || ...`) still runs first, before the
   hint is consulted. A request with two `portal_sid` cookies is still rejected at `400` before
   any hint logic executes.

### 6.3 Contract

Request body (new, optional; the endpoint currently parses no body at all - confirmed, script
020):

```json
{ "sessionType": "PRE_AUTH" }
```

- Absent field or absent body entirely -> current precedence (authenticated wins) unchanged.
- `sessionType` present and its corresponding cookie present -> that session type is refreshed,
  unconditionally, bypassing the authenticated-wins default.
- `sessionType` present but its corresponding cookie absent -> `400 BAD_REQUEST`,
  `VALIDATION_FAILED`, a new error code (name TBD in sub-task 4.1, following the
  `AMBIGUOUS_SESSION_COOKIE` / `REQUEST_VALIDATION_FAILED` pattern already in
  `CsrfTokenRefreshHttpResponses`).
- Malformed JSON or an unrecognized `sessionType` value -> `400 BAD_REQUEST` via the same path
  (Spring deserialization failure for an unknown enum constant is handled at the controller,
  following the `@RequestBody(required = false)` + manual-null-check convention already used by
  `CaptchaController`, `ForgotPasswordController`, `LoginController`, `RegistrationController` -
  no Jakarta Bean Validation is used anywhere in this area, confirmed script 020).

### 6.4 Milestone M4 sub-tasks

- **4.1** New request DTO (e.g. `CsrfTokenRefreshRequest` record with one field
  `CsrfRefreshSessionType sessionType`, nullable) in the controller's `dto` package, matching the
  sibling convention. Add `@RequestBody(required = false)` to the controller method. Add the new
  `400` response case to `CsrfTokenRefreshHttpResponses`.
- **4.2** Thread the hint through `RefreshCsrfTokenCommand` (new nullable field) and
  `RefreshCsrfTokenService.refresh()`: when present and its cookie is absent, short-circuit to the
  new validation-error outcome before calling either session service; when present and its cookie
  is present, call only that session service; when absent, keep current behavior unchanged.
- Each sub-task is fail-first: a test is written first proving the current behavior lacks the new
  branch (e.g. hint ignored / no such field exists), then implemented.
- Sub-task gates: spotless apply/check, compile, test-compile, targeted tests (extend
  `CsrfTokenRefreshControllerBehaviorTest`, `CsrfTokenRefreshHttpContractTest`,
  `RefreshCsrfTokenServiceTest`; `CsrfTokenRefreshWebArchitectureGuardTest` and
  `RefreshCsrfTokenServiceStructureTest` are expected to keep passing unchanged).
- Milestone gates at the end of M4: full suite, coverage, startup smoke test, SonarQube - same
  five gates used for M1/M2.

### 6.5 Out of scope for M4

Changing the no-fallback default when the hint is absent; any change to the pre-auth or
authenticated refresh services themselves (`AuthenticatedSessionCsrfRefreshService`,
`PreAuthSessionCsrfRefreshService`) - only the orchestration layer and the controller's request
parsing change; frontend behavior.

### 6.6 Implementation note (recorded during sub-tasks 4.1+4.2)

Section 6.4 above proposed threading the hint through `RefreshCsrfTokenCommand` and
`RefreshCsrfTokenService`. That proved unnecessary: `RefreshCsrfTokenService` already prioritizes
whichever of its two id fields has text, and the controller already holds both cookie values
before building that command. The hint is therefore resolved entirely in the controller, which
decides which single id value is allowed to flow into the existing, otherwise-untouched
`RefreshCsrfTokenCommand`. `RefreshCsrfTokenCommand` and `RefreshCsrfTokenService` are not
modified by M4; their existing tests pass unchanged. Sub-tasks 4.1 and 4.2 were implemented
together as a result (too small and coupled to usefully split).
