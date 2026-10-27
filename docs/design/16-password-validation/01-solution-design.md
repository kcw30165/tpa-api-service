# 01 Solution design

## 1. Approved scope
One POST /api/v1/password-validation endpoint. Initially the only supported purpose is PERSONAL_INFORMATION_UPDATE. Change of Investment and Switch Fund are future consumers, not implemented or accepted purposes now. Protect existing PUT /api/v1/personal-information. GET remains unchanged. Preserve existing update response and downstream update semantics.

Approved binding: authenticated session + selected Account-Ref + purpose + exact reviewed operation payload. A token expires after 300 seconds, is not renewable, and is consumed on the first authenticated submission, even if JSON is malformed, payload differs, or subsequent validation fails. One active token per session/account/purpose; successful replacement invalidates the previous token. Different scopes may coexist.

## 2. Identity and reuse
E1 findings: LoginOtpVerifyService constructs ActorContext from context.normalizedUsername(); ActorContext.actorUserId therefore carries that login value in the captured code. AuthenticatedSessionAccount contains account routing/details, not a separate username. Reuse CurrentPortalAccessContextResolver after session/CSRF/account authorization; do not accept cust-id from FE or add an Account-Ref-only lookup. This finding is specific to the captured commit; recheck at implementation HEAD. Do not rename or broaden ActorContext as part of this feature.

Reuse existing authenticated session validation, account ownership resolution, cookie writer, APIM executor/crypto/profile routing, Redis key factory and failure rendering. Existing CAS capability is a candidate, not proof that token lifecycle is already supported. Proposed new application capabilities: password-validation use case, downstream password-validation port, authorization-grant store, payload-binding service and purpose registry. Names are design roles, not mandated existing classes.

## 3. Issuance sequence
1. Enforce required authenticated transport and account authorization; no body logging.
2. Decode bounded JSON, validate supported purpose, password presence/login length bounds, and operation payload structure. Do not trim or change password case. No registration complexity policy.
3. Apply username-wide throttling, shared across sessions, accounts and purposes. Default 10 admitted requests in a 60-second window. Redis failure fails closed with 503.
4. Calculate a versioned binding over the complete operation payload; do not accept an FE hash as authoritative.
5. Call TRPPwdValidation once with server-resolved cust-id and unchanged password. No automatic retry or token issuance on uncertain response.
6. For success, generate an opaque random token and atomically replace the grant for this scope, only while the session remains usable. Return the token only after storage succeeds. Expiry starts at committed issuance, not password-request arrival.
7. For wrong password, do not replace an existing grant. For account locked, clear cookie and attempt current-session invalidation; issue no token.

Operation structure is checked at issuance to ensure a bindable supported request. Existing YAML/access/business validation remains authoritative at mutation time; successful issuance is not a promise that the update will be accepted.

## 4. Consumption sequence
Use a purpose-specific protected-route gate for PUT /api/v1/personal-information. Purpose comes from the route, never a mutation-body override.
1. Require authenticated session, CSRF and selected account. Missing/invalid ownership must not consume another scope's grant.
2. Read exactly one X-Password-Validation-Token header.
3. Atomically check current grant, token digest, scope and expiry, and delete it. No read-then-delete race. A wrong token must not delete the valid current grant.
4. Carry the consumed grant's binding in request-local state only. Do not log it.
5. Decode JSON; malformed/empty/invalid body returns 400 with grant already consumed.
6. Recalculate and compare binding. A mismatch returns 403 with grant already consumed.
7. Run existing structural/YAML/access validation, including cache refresh behavior. Preserve existing errors and update result contract.
8. Dispatch the existing update once through its existing orchestration. No new result-replay capability or automatic resubmission is introduced.

The gate must execute before body decoding and must not depend on a controller @RequestBody having been parsed. A downstream business rejection or technical failure never restores a consumed token.

## 5. Payload equality specification (authored technical design)
Use a shared versioned canonicalizer on issuance and mutation raw JSON trees, before lossy DTO mapping. Include formVersion, applyToAllAccounts and the entire fields object, including any submitted keys. Exclude password, token, transport headers and envelope purpose from operationPayload itself; purpose and scope are separately bound.

Object member order is irrelevant recursively. Array order is significant. Strings compare by decoded Unicode content without trimming, case folding or Unicode normalization. Types remain distinct: string, number, boolean, null, array, object. Absent differs from explicit null. Reject duplicate JSON object members, non-JSON numeric values and trailing content. Numeric design: compare exact decimal values, so 1 and 1.0 are equal, but neither equals "1". Avoid binary floating-point conversion. Freeze canonicalizer version and test vectors; changing it invalidates old grants rather than interpreting them differently.

Compute a cryptographic digest of the versioned canonical representation. Store the digest, not raw personal information. Include applyToAllAccounts so it cannot be changed after review. This binds the submitted apply-all instruction, not a snapshot of every related account downstream might resolve. A fixed target-account-set authorization is NOT claimed; if required later, it needs a separate contract change.

### 6. Storage and concurrency specification (confirmed decision, source-grounded)

Chosen mechanism: reuse the existing `AuthenticatedSession.featureState` map (`Map<String,String>`, already present on the record alongside `revision`, confirmed unused by any production feature today) as the grant store, mutated exclusively through the existing generic `AuthenticatedSessionConcurrencyService.updateIfUsable(AuthenticatedSessionId, UnaryOperator<AuthenticatedSession>, String correlationId)`. No new Redis key, no new Lua script, and no separate grant record are introduced. This supersedes the earlier "prefer a capability-scoped grant record" framing; a dedicated grant record was evaluated and rejected because it would require either (a) a second Lua script duplicating `update-if-current.lua`'s revision-check contract, or (b) a session-usability check performed outside the session's own atomic boundary -- both of which the gate explicitly disallowed ("no blind session overwrite").

Mechanism, as confirmed by reading the actual classes:
- `AuthenticatedSessionConcurrencyService.updateIfUsable(...)` already implements the full required sequence: load the session (`AuthenticatedSessionPort.load`), unprotect it, check `session.isUsable(now)` (status ACTIVE and both idle/absolute expiry in the future), apply a caller-supplied `UnaryOperator<AuthenticatedSession>` mutator, generate a fresh `SessionRevision`, protect the replacement, and write it via `AuthenticatedSessionCasUpdatePort.updateIfCurrent(...)`, which runs `update-if-current.lua` (GET, decode JSON, compare the plaintext top-level `sessionRevision` field, PSETEX only on match). On `REVISION_MISMATCH` the whole load-mutate-write sequence is retried (`Retry.backoff(2, 10ms)`, max 50ms) against freshly reloaded state; other outcomes (`NOT_FOUND`, `SESSION_NOT_USABLE`, exhausted conflict) are returned as typed results, never thrown.
- Grant issuance: build a `UnaryOperator<AuthenticatedSession>` that calls `session.withFeatureState("password-validation:" + purpose, <serialized grant JSON>)`, where the serialized value contains the token digest, payload digest, canonicalizer version, issuedAt and expiresAt (per Section 5) -- never the raw token or password. `withFeatureState` returns a new `AuthenticatedSession` with every other field, including `revision` and other feature-state entries, unchanged, so this cannot collide with or corrupt Fund Comparison's or any other feature's feature-state entries (different map keys).
- Grant consumption: build a `UnaryOperator<AuthenticatedSession>` that reads the current feature-state entry for the scope key, verifies token digest/expiry, and calls `withFeatureState(scopeKey, <tombstone-or-removed-marker>)` to atomically invalidate it as part of the same CAS write. Because `updateIfUsable` reloads the session and re-checks `isUsable(now)` on every attempt (including retries), this satisfies the requirement to verify session existence/usability at the operation's linearization point, not just an earlier filter check -- the usability check and the write happen inside the same atomic sequence.
- Session resurrection is structurally impossible: `updateIfUsable` only ever mutates a session that is already `isUsable(now) == true`; a logged-out, revoked, or expired session returns `SESSION_NOT_USABLE` and no feature-state write occurs.
- TTL: the grant's `expiresAt` (issuedAt + 300s) is stored as a value inside the feature-state entry and checked in application code on every consumption attempt; it is never a Redis key TTL. Because the grant lives inside the session record, it can never outlive the session's own `idleExpiresAt`/`absoluteExpiresAt` -- satisfying "never authorizes beyond session expiry" by construction.
- Session logout/revocation (`AuthenticatedSessionLifecycleService.logout`/`revoke`) writes a terminal status via the existing blind touch/terminal-transition path (`port.touch`, not the CAS port) and deliberately carries the session's existing revision forward unchanged (confirmed by the code comment in `AuthenticatedSessionLifecycleService.copy(...)`). A concurrent in-flight `updateIfUsable` grant mutation racing a logout is resolved by whichever write lands last at the storage layer; the grant feature-state entry may physically remain in a terminal session's JSON, but `isUsable(now)` on that terminal session is permanently false, so no future consumption attempt can succeed against it. This is the same linearization boundary gap already implicitly accepted by every other `updateIfUsable` consumer (e.g. CSRF rotation); it is called out, not newly introduced by this feature.

Source references: `AuthenticatedSession.java` (`featureState`, `withFeatureState`), `AuthenticatedSessionConcurrencyService.java` (`updateIfUsable`, `attemptOnce`), `AuthenticatedSessionCasUpdatePort.java`, `RedisAuthenticatedSessionCasUpdateAdapter.java`, `AuthenticatedSessionConcurrencyConfig.java` (confirms these beans are wired into the Spring context), `src/main/resources/redis/authenticated-session/update-if-current.lua`, `AuthenticatedSessionLifecycleService.java` (`copy`, logout/revoke terminal-transition behavior).

## 7. Security, logging and operations
Disable request/response body logging for issuance and protected Personal Information PUT independently of configurable opt-in body logging. Exclude the token header from header logging and sanitize DTO/command toString. Do not log password, token, raw cust-id, session cookie, CSRF, payload, or payload digest. APIM request/response logging must be tested in encrypted and plain modes. Safe events contain request ID, purpose, outcome, HTTP status and bounded reason codes only.

No-store responses for issuance and authorization failures. FE keeps token in memory, not a URL, and discards it on submission, replacement, expiry or logout. FE must not interpret wrong-password 401 as unconditional session loss; account-locked 423 requires login again. After a mutation timeout, do not automatically revalidate and resubmit: first resolve the existing business outcome through the existing operational process. Fresh authorization is not business idempotency.
