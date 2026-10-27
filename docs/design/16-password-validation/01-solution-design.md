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

### 6. Storage and concurrency specification (corrected decision, source-grounded -- supersedes prior pass after independent review)

Chosen mechanism: reuse the existing `AuthenticatedSession.featureState` map (`Map<String,String>`), mutated exclusively through `AuthenticatedSessionConcurrencyService.updateIfUsable(AuthenticatedSessionId, UnaryOperator<AuthenticatedSession>, String correlationId)`. No new Redis key, no new Lua script, and no separate grant record are introduced.

**Grant key includes Account-Ref (corrects reviewer finding 2, major).** The feature-state key is `"password-validation:" + accountRef.value() + ":" + purpose`, where `accountRef` is the caller's selected `AuthenticatedAccountRef` from `PortalAccessContext`. This gives one grant slot per session/account/purpose, matching the approved D02/D03 scope exactly -- not per session/purpose only, as the prior pass incorrectly specified. Because the key includes the account, two different accounts selected within the same session hold independent grants in independent map entries; replacement and coexistence across accounts require no special-casing.

**Token strength (restored; omitted in error from the prior pass).** Generate at least 256 random bits using the platform cryptographic generator (`SecureRandom` or equivalent); return a base64url opaque value. This token is not a JWT and contains no client-readable claims. Only its digest, never the raw token, is stored in the feature-state entry.

**Session-resurrection race (corrects reviewer finding 1, BLOCKING).** The prior pass's claim that resurrection was "structurally impossible" was wrong. `AuthenticatedSessionConcurrencyService.attemptOnce` checks `session.isUsable(now)` in Java *before* calling the CAS port, not inside the Lua script -- `update-if-current.lua` only compares the `sessionRevision` field and checks a positive TTL; it has no concept of session status. Separately, `AuthenticatedSessionLifecycleService.copy(...)` -- used by `persistTerminal` for `logout`, `revoke`, and expiry transitions -- deliberately carries the session's existing revision forward **unchanged** when writing a terminal status via the blind `port.touch` path. Counterexample: (1) a grant mutation loads an ACTIVE session at revision R; (2) logout concurrently writes LOGGED_OUT but preserves revision R; (3) the grant mutation's CAS write, built from the originally-loaded ACTIVE session, still carries expected revision R, so `update-if-current.lua` reports a match and the write succeeds, silently overwriting the terminal LOGGED_OUT status back to ACTIVE in Redis.

**Required fix: cross-cutting prerequisite, explicitly flagged.** `AuthenticatedSessionLifecycleService`'s terminal-transition path (`persistTerminal`, used by `logout`, `revoke`, and expiry detection) must be changed to generate and write a **fresh `SessionRevision`** on every terminal transition, instead of carrying the prior revision forward unchanged. This is a change to existing, already-shipped session-lifecycle code, not new password-validation-specific code, and must land as its own prerequisite subtask -- with its own fail-first tests -- before any password-validation grant-issuance/consumption code is implemented. With this fix, a terminal transition always strictly advances the revision, so any concurrent grant-mutation CAS attempt whose expected revision predates the transition receives `REVISION_MISMATCH`, triggers the existing retry-with-reload logic in `updateIfUsable`, reloads the now-terminal session, finds `isUsable(now) == false`, and returns `SESSION_NOT_USABLE` without writing -- preventing resurrection by construction of the revision chain, not by an unproven ordering assumption. This fix is narrow: it does not add a CAS-port call to the lifecycle service and does not alter idle/absolute expiry semantics or any other field of `copy(...)`.

**Deterministic race tests required at implementation (supplement T06/T08/T19).** (a) Load a session at revision R inside a grant-mutation mutator; before the CAS write executes, externally transition the same session to a terminal status (which must bump the revision per the fix above); assert the grant's CAS write returns `REVISION_MISMATCH`, the retry-reload returns `SESSION_NOT_USABLE`, and the final stored session's status remains terminal. (b) Confirm a terminal transition's revision differs from the pre-transition revision in every `persistTerminal` code path (logout, revoke, expiry).

**Grant consumption, TTL, and concurrency (unchanged from prior pass).** Consumption reads the current feature-state entry for the scope key, verifies token digest/expiry, and calls `withFeatureState(scopeKey, <tombstone>)` atomically as part of the same CAS write. The grant's `expiresAt` (issuedAt + 300s) is an application-checked value inside the feature-state entry, never a Redis key TTL, so it can never outlive the session's own `idleExpiresAt`/`absoluteExpiresAt`. Exactly one concurrent consumer wins; a Redis timeout after a possible write is an uncertain outcome and must return 503 without dispatching or retrying the consume operation to infer success.

**Correction to "never thrown" claim.** Not every failure path inside `attemptOnce` is converted to a typed result: only the revision-conflict-exhaustion path is explicitly caught via `onErrorResume`. Failures from `sessions.load(...)`, `protector.unprotect(...)`, or the mutator itself propagate as raw `Mono` errors to the caller. The password-validation use case must explicitly catch and map these to `SYSTEM_ERROR` itself, rather than assuming `AuthenticatedSessionConcurrencyService` fully contains every failure mode.

Source references: `AuthenticatedSession.java` (`featureState`, `withFeatureState`), `AuthenticatedSessionConcurrencyService.java` (`updateIfUsable`, `attemptOnce`), `AuthenticatedSessionCasUpdatePort.java`, `RedisAuthenticatedSessionCasUpdateAdapter.java`, `AuthenticatedSessionConcurrencyConfig.java`, `src/main/resources/redis/authenticated-session/update-if-current.lua`, `AuthenticatedSessionLifecycleService.java` (`copy`, `persistTerminal`, `logout`, `revoke`, `transitionTerminal`, `loadAndTouch`).

## 7. Security, logging and operations
Disable request/response body logging for issuance and protected Personal Information PUT independently of configurable opt-in body logging. Exclude the token header from header logging and sanitize DTO/command toString. Do not log password, token, raw cust-id, session cookie, CSRF, payload, or payload digest. APIM request/response logging must be tested in encrypted and plain modes. Safe events contain request ID, purpose, outcome, HTTP status and bounded reason codes only.

No-store responses for issuance and authorization failures. FE keeps token in memory, not a URL, and discards it on submission, replacement, expiry or logout. FE must not interpret wrong-password 401 as unconditional session loss; account-locked 423 requires login again. After a mutation timeout, do not automatically revalidate and resubmit: first resolve the existing business outcome through the existing operational process. Fresh authorization is not business idempotency.
