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

## 6. Storage and concurrency specification (authored technical design)
Prefer a capability-scoped grant record separate from the session payload to avoid rewriting the full encrypted session for each token. Reuse the configured Redis prefix and key factory; never introduce a per-worktree prefix. Store a token digest, opaque session/account scope identifiers, purpose, payload digest, canonicalizer version, issuedAt and expiresAt. No raw password, token or operation payload in Redis. Generate at least 256 random bits using the platform cryptographic generator; return a base64url opaque value. This token is not a JWT and contains no client-readable claims.

Grant replacement and compare-and-consume are atomic Redis operations. At replacement/consumption, verify session existence/usability at the operation's linearization point, not just an earlier filter check. Reuse existing session serialization/protection conventions without assuming encrypted fields are Lua-readable. Implementation gate: choose and test the exact session-state check and atomic Redis script before coding consumers. If this cannot be achieved safely with separate records, use the existing session CAS/concurrency service instead; no blind session overwrite.

TTL is at most 300 seconds and never authorizes beyond session expiry. The response advertises the configured 300-second grant lifetime; session expiry can end usability earlier. No session TTL extension solely to keep a grant usable. Successful replacement is ordered by atomic commit; the last committed grant is the sole valid grant. Concurrent responses may arrive out of order, so FE serializes confirmation requests per scope and treats stale tokens as invalid.

Exactly one concurrent consumer wins. Redis timeout after possible deletion is an uncertain consumption: return 503, do not dispatch and do not retry the consume operation to infer success. Session logout/revocation makes all associated grants unusable even if physical records remain until TTL. A mutation already authorized before a concurrent logout is not claimed to be cancelled; test and document that linearization boundary.

## 7. Security, logging and operations
Disable request/response body logging for issuance and protected Personal Information PUT independently of configurable opt-in body logging. Exclude the token header from header logging and sanitize DTO/command toString. Do not log password, token, raw cust-id, session cookie, CSRF, payload, or payload digest. APIM request/response logging must be tested in encrypted and plain modes. Safe events contain request ID, purpose, outcome, HTTP status and bounded reason codes only.

No-store responses for issuance and authorization failures. FE keeps token in memory, not a URL, and discards it on submission, replacement, expiry or logout. FE must not interpret wrong-password 401 as unconditional session loss; account-locked 423 requires login again. After a mutation timeout, do not automatically revalidate and resubmit: first resolve the existing business outcome through the existing operational process. Fresh authorization is not business idempotency.
