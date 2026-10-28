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

M1 version-one encoding: `JsonOperationPayloadCanonicalizer` implements the
framework-free `OperationPayloadBindingPort.digest(String rawJson)`, returning
`PayloadDigest(version, sha256)`. It parses raw JSON with strict duplicate
detection independently of the global mapper. The SHA-256 input begins with a
big-endian 32-bit version (`1`). Each value has a one-byte ASCII type tag:
`{` object, `[` array, `s` string, `n` number, `t` true, `f` false, `0` null.
Objects encode a 32-bit member count followed by name/value pairs sorted by
Java UTF-16 code-unit name order; arrays encode count then ordered values.
Text encodes a 32-bit code-unit count and big-endian UTF-16 code units (including
lone surrogate escapes, avoiding replacement collisions). Numbers encode
`BigDecimal.stripTrailingZeros().toString()` as text, without binary floating
point conversion. Only an object root is accepted; trailing tokens, malformed
JSON and duplicate members at any depth fail with a fixed, value-free exception.
The empty-object digest is
`1e08b461b9803ab3c74e8791fb16c2b9b490b3a8754bbcb50a59b98d7c0f662b`.
Digest comparison requires matching versions; unknown versions are not reinterpreted.
This parser is not an inbound HTTP decoder or a claim that deployment body limits
have been resolved. Both future call sites must supply raw JSON before DTO mapping.

### 6. Storage and concurrency specification (corrected decision, source-grounded -- supersedes prior pass after independent review)

Chosen mechanism: store grants in the existing AuthenticatedSession.featureState map. Grant mutations retain the updateIfUsable application contract; the M1.5 prerequisite generalizes the underlying conditional-write mechanism for lifecycle operations. No separate grant Redis key is introduced. The existing preserve-current-TTL Lua script remains for grant mutations; a sibling absolute-deadline CAS script is introduced for lifecycle writes. All relevant session writers must participate in revision-checked writes before grant work begins.

**Grant key includes Account-Ref (corrects reviewer finding 2, major).** The feature-state key is `"password-validation:" + accountRef.value() + ":" + purpose`, where `accountRef` is the caller's selected `AuthenticatedAccountRef` from `PortalAccessContext`. This gives one grant slot per session/account/purpose, matching the approved D02/D03 scope exactly -- not per session/purpose only, as the prior pass incorrectly specified. Because the key includes the account, two different accounts selected within the same session hold independent grants in independent map entries; replacement and coexistence across accounts require no special-casing.

**Token strength (restored; omitted in error from the prior pass).** Generate at least 256 random bits using the platform cryptographic generator (`SecureRandom` or equivalent); return a base64url opaque value. This token is not a JWT and contains no client-readable claims. Only its digest, never the raw token, is stored in the feature-state entry.

**Session-resurrection race (corrects reviewer finding 1, BLOCKING; the first proposed fix was itself found insufficient on a second review and is corrected again here).** The original claim that resurrection was "structurally impossible" was wrong, and the first proposed fix -- bump the session revision only inside `persistTerminal` -- is also insufficient by itself. `AuthenticatedSessionLifecycleService.loadAndTouch`, which runs on effectively every authenticated request to slide the idle-expiry window, also writes a full session snapshot through the same blind, non-CAS `port.touch` path used by `persistTerminal`, via its own `copy(...)` call, and that `copy(...)` was never revision-aware either. Bumping the revision only on terminal transitions does not close the race, because `port.touch` performs an unconditional `PSETEX`, not a compare-and-swap: a `loadAndTouch` that started before a concurrent write can replay its stale, pre-transition (or pre-grant-mutation) snapshot over newer state regardless of which revision that newer state carries. Two concrete counterexamples: (1) a `loadAndTouch` call loads an ACTIVE session at revision R; logout concurrently transitions the session to LOGGED_OUT at a fresh revision R2 (even with the revision-bump fix applied); the already-in-flight `loadAndTouch`, having loaded before the transition, still blindly writes its ACTIVE copy of the session (carrying forward revision R, per `copy(...)`'s existing contract) via `port.touch`, overwriting the terminal LOGGED_OUT record back to ACTIVE. (2) A `loadAndTouch` call loads a session at revision R whose `featureState` still contains an unconsumed password-validation grant; a concurrent grant consumption tombstones that grant via the CAS path at a fresh revision R3; the already-in-flight `loadAndTouch` again blindly overwrites the session with its stale pre-consumption snapshot, restoring the already-consumed grant and the earlier revision.

**Required fix (corrected a second time, BLOCKING finding from fourth independent review): operation-specific eligibility and TTL policy, not blanket reuse of the existing CAS method as-is.** The prior pass's instruction to route loadAndTouch and persistTerminal through AuthenticatedSessionConcurrencyService.updateIfUsable unchanged is itself incorrect, because that method has a fixed contract unsuited to either call site: (1) attemptOnce checks current.isUsable(now) in Java and returns SESSION_NOT_USABLE *before* ever invoking the mutator when the session is not usable -- so it structurally cannot perform the ACTIVE-to-EXPIRED transition that loadAndTouch currently performs when it discovers an idle- or absolute-expired session; and (2) the underlying update-if-current.lua script reads the key's current Redis TTL via PTTL and writes back that exact same TTL via PSETEX -- it has no mechanism to set a different TTL, whereas loadAndTouch must slide the TTL forward to a new idle expiry and persistTerminal must shorten it to a fixed terminal-retention window.

The corrected design generalizes the mechanism along two independent axes, both decided at the CAS layer rather than hardcoding a single policy:

- Eligibility is operation-specific, not one blanket isUsable gate. The CAS service accepts a caller-supplied eligibility check evaluated against the freshly reloaded session, in place of the current hardcoded isUsable(now) check:
  - Grant issuance/consumption (this feature) keeps the exact current behavior unchanged: eligible only when isUsable(now) is true; otherwise return the existing SESSION_NOT_USABLE result without writing. This is not loosened in any way by this correction.
  - loadAndTouch is eligible whenever the session is not already in a terminal status, regardless of idle/absolute expiry -- so it can still write the ACTIVE-to-EXPIRED transition when it discovers expiry (mirroring the existing idempotency short-circuit already present in the lifecycle service, now additionally enforced atomically against a concurrent terminal writer rather than only checked at read time).
  - persistTerminal (logout, revoke, discovered expiry) is eligible whenever the session is not already terminal; if a reload shows it already terminal, the operation is a no-op that returns the existing terminal status rather than re-writing it -- preserving the exact idempotency the current code already provides.
  - A missing session continues to return SESSION_NOT_FOUND identically for every caller; this is unchanged by generalizing eligibility.
- TTL policy is explicit and operation-specific. Preserve-current grant mutations read PTTL and apply it entirely inside the existing atomic Lua operation. Lifecycle mutations use a sibling revision-checked script with an absolute epoch-millisecond expiry deadline; they do not send a Java-calculated relative TTL.
  - Grant issuance/consumption supplies "preserve the session's own current TTL" as its policy (matching today's behavior exactly -- a grant mutation must never itself change how long the session lives).
  - Successful still-usable loadAndTouch computes idleExpiresAt = min(attemptNow + idleTimeout, absoluteExpiresAt) from freshly reloaded state and passes that absolute deadline to the lifecycle CAS script. A discovered-expiry branch instead invokes the terminal transition and terminal-retention policy.
  - persistTerminal uses a fixed retention deadline derived from the terminal transition timestamp plus configured retention. Keep that transition timestamp/deadline stable across revision-conflict retries of the same operation; repeated calls observing terminal state do not write or extend retention. Terminal retention is independent of the expired authentication deadline and never makes the session usable.

Every revision-conflict retry reloads state and reevaluates eligibility and mutation against that state. Preserve existing bounded retry behavior and existing-consumer contracts. Only genuine revision conflicts are retried; ambiguous transport/write outcomes fail closed and are not retried as if they were conflicts. Missing or malformed revision and missing session fail closed; a conditional update never creates a missing key. Terminal observations return the existing lifecycle result without a write.

**TTL-mechanism clarification (selected final contract).** The grant script preserves PTTL inside Lua, with no earlier Java TTL read. The lifecycle sibling script checks key existence and expected revision, validates the supplied absolute deadline, samples Redis TIME inside the atomic operation, and rejects an already-elapsed deadline without writing. It replaces the value and applies PEXPIREAT with the supplied deadline within that same script. No relative-duration calculation outside Redis may extend the deadline through queue/network delay. The deadline is also the replacement session idleExpiresAt for successful touch. A discovered-expiry transition uses the terminal-retention deadline, not sliding expiry. Redis and application clocks must be synchronized operationally; test skew/delay handling and supported Redis command/script compatibility before M1.5 approval. The M1.5 implementation and isolated-test scope are mapped below; independent acceptance and deployment verification remain separate gates. A successful conditional write is the linearization point; no stale touch/terminal snapshot may bypass revision checks.


This is still a prerequisite change to existing, already-shipped session-lifecycle code (the generic CAS service and its Lua script), not new password-validation-specific code, and is now called out in 06-implementation-plan.md as its own subtask with its own dedicated review gate, to be completed and independently approved before any password-validation grant-issuance/consumption code is written.

Deterministic race tests required at implementation (expanded after fourth review; supplements T06/T08/T19).** (a) Load a session at revision R inside a grant-mutation mutator; before the CAS write executes, externally transition the same session to a terminal status; assert the grant's CAS write returns a revision-conflict outcome, the retry-reload returns SESSION_NOT_USABLE, and the final stored session's status remains terminal. (b) Confirm a terminal transition's revision differs from the pre-transition revision in every persistTerminal code path (logout, revoke, expiry). (c) A loadAndTouch in flight (loaded at revision R) racing a concurrent terminal transition (logout/revoke/expiry, which must land first): assert the touch's CAS write returns a revision-conflict outcome, the retry-reload evaluates touch-eligibility against the now-terminal state as ineligible, performs no write, and the final stored session remains terminal with the touch's idle-expiry change discarded. (d) A loadAndTouch in flight (loaded at revision R, with an unconsumed grant present in featureState) racing a concurrent grant consumption (which must land first): assert the touch's CAS write returns a revision-conflict outcome, the retry-reload reapplies touch semantics against the already-consumed-grant state, and the final stored session's featureState still reflects the tombstone (the grant is not restored). (e) Confirm a loadAndTouch with no concurrent conflict still correctly extends idle expiry via its CAS write, and assert the actual Redis key TTL (read back via PTTL, not just the stored idleExpiresAt field) reflects the new sliding window. (f) Confirm an ACTIVE session whose idle or absolute expiry has already passed is successfully transitioned to EXPIRED by loadAndTouch's eligibility rule (not rejected as ineligible the way a grant mutation would be), and that this transition is itself atomic against a concurrent writer. (g) Confirm a persistTerminal transition sets the actual Redis key TTL (read back via PTTL) to the configured fixed terminal-retention duration, distinct from whatever sliding TTL was in effect beforehand. (h) Confirm persistTerminal called twice against an already-terminal session is a no-op on the second call (idempotent: no write, existing terminal status returned), proving terminal-state idempotency is preserved under the generalized eligibility mechanism. (i) Confirm that a discovered-expiry transition inside loadAndTouch sets the actual Redis key TTL (read back via PTTL) to the configured fixed terminal-retention duration, identical to a persistTerminal-driven logout/revoke transition, not the sliding idle-expiry duration. (j) Confirm a successful sliding-expiry touch never produces a computed TTL that would extend the session's actual expiry past its stored absoluteExpiresAt, including when the touch is retried after a revision conflict.

Grant consumption, TTL, and concurrency (unchanged from prior pass).** Consumption reads the current feature-state entry for the scope key, verifies token digest/expiry, and calls `withFeatureState(scopeKey, <tombstone>)` atomically as part of the same CAS write. The grant's `expiresAt` (issuedAt + 300s) is an application-checked value inside the feature-state entry, never a Redis key TTL, so it can never outlive the session's own `idleExpiresAt`/`absoluteExpiresAt`. Exactly one concurrent consumer wins; a Redis timeout after a possible write is an uncertain outcome and must return 503 without dispatching or retrying the consume operation to infer success.

**Correction to "never thrown" claim.** Not every failure path inside `attemptOnce` is converted to a typed result: only the revision-conflict-exhaustion path is explicitly caught via `onErrorResume`. Failures from `sessions.load(...)`, `protector.unprotect(...)`, or the mutator itself propagate as raw `Mono` errors to the caller. The password-validation use case must explicitly catch and map these to `SYSTEM_ERROR` itself, rather than assuming `AuthenticatedSessionConcurrencyService` fully contains every failure mode.

### M1.5 implementation map (pending independent approval)

`AuthenticatedSessionConcurrencyService.updateIfEligible` accepts an explicit
eligibility predicate, a mutation receiving the freshly sampled attempt time,
and an expiry-deadline function. All are reevaluated after each genuine revision
conflict. Ineligible state returns `UNCHANGED` with the observed session and no
write. Existing `updateIfUsable` delegates with the original usability gate and
preserve-current-TTL policy, mapping an ineligible observation back to the
original session-free `SESSION_NOT_USABLE` result. The existing two-retry budget
and exception propagation remain unchanged.

`AuthenticatedSessionCasUpdateCommand.expiryDeadline` is an optional `Instant`:
null selects the original atomic PTTL-preserving script; a value selects
`update-if-current-until.lua` with its epoch-millisecond deadline. The existing
five-argument constructor retains preserve-current behavior. Both scripts reject
missing/malformed revisions. The lifecycle sibling checks the revision and
positive existing TTL, validates a canonical positive integer deadline, samples Redis TIME, then
performs SET and PEXPIREAT atomically. Invalid/elapsed deadlines return a technical
failure without a write; they are not revision conflicts and are not retried.
Leading-zero deadlines are rejected before SET because Redis expiry commands
reject that representation; script errors do not roll back earlier writes.

`AuthenticatedSessionLifecycleService.updateLifecycle` replaces the former blind
touch/persistTerminal writes. Its predicate accepts nonterminal sessions even
when authentication expiry has passed. Usable touch updates the fresh snapshot
and uses its idle deadline capped by absolute expiry. Logout/revoke/discovered
expiry preserve unrelated state and use a timestamp captured at the first
terminal mutation attempt; a per-subscription holder keeps that timestamp and
retention deadline fixed across conflicts. Terminal observations return the
existing lifecycle status without a write or retention extension. The common
writer protects the replacement and stamps a new revision for every applied
mutation. Only initial creation retains the existing store path.

The lifecycle constructor and Spring composition now require the shared
concurrency service and revision generator; no fallback blind-write constructor
was retained. The generic result adds UNCHANGED, so the existing CSRF mapper has
an explicit defensive failure mapping for that outcome, although updateIfUsable
never emits it. There is no public response or session JSON/encryption schema
change and no grant lifecycle implementation in M1.5.

Live source inspection also found Fund Price mode and committed-login recovery
as existing CAS consumers, in addition to the three listed in the earlier design.
All retain updateIfUsable and preserve-current TTL behavior. The isolated Redis
tests run actual cache-service/CSRF writes and verify unrelated feature entries
and exact expiry survive; existing Fund Price/replay regressions remain required.
Race fixtures use a generic synthetic feature entry/tombstone, not M2 grant code.

Isolated execution is tested with Redis 8.0.5. Production scripts use GET, PTTL,
PSETEX or SET/PEXPIREAT, TIME and Lua cjson. Deployment Redis version, Sentinel
failover/mTLS and clock synchronization still require environment verification;
there is no documented deployment Redis version in the inspected repository.
This implementation/test note does not certify deployment compatibility.

Source references: `AuthenticatedSession.java` (`featureState`, `withFeatureState`), `AuthenticatedSessionConcurrencyService.java` (`updateIfUsable`, `updateIfEligible`, `attemptOnce`), `AuthenticatedSessionCasUpdatePort.java`, `AuthenticatedSessionCasUpdateCommand.java`, `RedisAuthenticatedSessionCasUpdateAdapter.java`, `AuthenticatedSessionConcurrencyConfig.java`, `src/main/resources/redis/authenticated-session/update-if-current.lua`, `src/main/resources/redis/authenticated-session/update-if-current-until.lua`, `AuthenticatedSessionLifecycleService.java` (`copy`, `updateLifecycle`, `logout`, `revoke`, `transitionTerminal`, `loadAndTouch`).

## 7. Security, logging and operations
Disable request/response body logging for issuance and protected Personal Information PUT independently of configurable opt-in body logging. Exclude the token header from header logging and sanitize DTO/command toString. Do not log password, token, raw cust-id, session cookie, CSRF, payload, or payload digest. APIM request/response logging must be tested in encrypted and plain modes. Safe events contain request ID, purpose, outcome, HTTP status and bounded reason codes only.

No-store responses for issuance and authorization failures. FE keeps token in memory, not a URL, and discards it on submission, replacement, expiry or logout. FE must not interpret wrong-password 401 as unconditional session loss; account-locked 423 requires login again. After a mutation timeout, do not automatically revalidate and resubmit: first resolve the existing business outcome through the existing operational process. Fresh authorization is not business idempotency.

Additional M1.5 deadline acceptance proof: delay execution between Java command creation and Redis script execution; assert the stored absolute deadline is unchanged, a deadline already passed is rejected without a write, and preserve-current grant mutations do not extend TTL. Repeat with revision conflict and with discovered expiry.
