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
