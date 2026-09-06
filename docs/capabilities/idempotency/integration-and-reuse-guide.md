# Idempotency Integration and Reuse Guide

## Mandatory shared-capability rule

New and migrated actions in anonymous, pre-authentication, authenticated, or approved service contexts must use the shared capability. Creating another feature-specific idempotency port, Redis adapter, key factory, persistence record, serializer, lifecycle coordinator, or replay-status model is prohibited.

Each action defines only its stable capability/action identity, opaque operation scope, versioned canonical fingerprint, TTL, protected side-effect boundary, terminal classification, optional context authorization callback, and optional opaque protected-state reference. The shared executor generates reservation ownership; action integrations must never derive, reuse, log, persist outside the shared record, or return that token publicly. Every deterministic terminal response is persisted through the shared snapshot and replayed without re-execution.

The detailed guidance below remains applicable where it does not conflict with the shared target. See [Shared Idempotency Consolidation Implementation Plan](shared-idempotency-consolidation-plan.md).


## 1. Decide whether the operation needs idempotency

Use idempotency when a client retry, browser double-submit, network retry, concurrent request, or server response loss could repeat a mutation or external side effect.

Typical examples:

- Start an auth workflow
- Send/resend a message
- Verify an OTP and establish a session
- Complete Registration
- Submit a downstream update

A read-only operation usually does not need client idempotency, although caching or retry controls may still apply.

## 2. Define the logical operation before coding

Document:

- Action name
- Required key source/header
- Inputs defining semantic equality
- Canonicalization rules
- Reservation point
- First external/irreversible side effect
- Completion point
- Replay-safe result
- Known failure behavior
- Unknown-outcome behavior
- TTL
- Concurrency and lock requirements

If these are unclear, do not add a generic “idempotency wrapper” around the method.

## 3. Implementation sequence

### Step 1 — Add an explicit action

Add or reuse a stable action enum/value. The action should describe the business mutation, not a class name or transient route path.

Examples:

```text
FORGOT_PASSWORD_START
FORGOT_PASSWORD_OTP_SEND
FORGOT_PASSWORD_OTP_RESEND
FORGOT_PASSWORD_OTP_VERIFY
FORGOT_PASSWORD_COMPLETE
```

Post-login Change Password remains a separate operation/action family from Forgot Password.

### Step 2 — Validate the key

Use the existing value-object/key validation pattern. Reject missing, blank, too-short, too-long, or unsafe-character keys at the web/application boundary with the standard error contract.

Do not generate a replacement key silently on the server. The caller must know which key identifies the request it may retry.

### Step 3 — Define canonical request material

Create an operation-specific request hash generator. List every field and normalization rule in tests.

Example conceptual material:

```text
version=1
scenario=FORGOT_PASSWORD
operation=OTP_SEND
destinationId={normalized opaque destination ID}
```

Do not concatenate fields ambiguously. Prefer a length-delimited, structured, or otherwise collision-safe canonical representation before SHA-256.

### Step 4 — Reserve before side effects

Call the idempotency port/coordinator before:

- Creating externally visible state that cannot be safely repeated
- Sending SMS/email
- Calling a non-safe APIM/TAS mutation
- Establishing an authenticated session

Handle every reservation outcome explicitly.

### Step 5 — Execute only for `STARTED`

Only the caller that atomically reserved `PROCESSING` may execute the protected operation.

```text
STARTED
  -> execute

DUPLICATE_IN_PROGRESS
  -> return waiting/conflict; do not execute

COMPLETED_REPLAY
  -> return snapshot; do not execute

KEY_REUSED_WITH_DIFFERENT_REQUEST
  -> reject

FAILED_EXISTING / UNKNOWN_OUTCOME_EXISTING
  -> apply the operation's recovery policy
```

### Step 6 — Build a safe replay snapshot

Store the minimum result required to reproduce the approved response. Prefer typed snapshots over arbitrary serialized HTTP responses.

Allowed data can include:

- Stable public status/outcome
- Workflow `nextStep`
- Masked destination
- Timing/cooldown values
- Opaque safe business reference
- Approved non-sensitive headers

Never store raw tokens, cookies, credentials, OTPs, passwords, or PII.

### Step 7 — Complete atomically enough for the operation

After the business effect and required state persistence succeed, mark the record completed with the replay snapshot. If completion storage fails after a side effect occurred, classify the outcome as uncertain and follow reconciliation policy.

Do not return final success if the operation requires replay protection but the completion record could not be persisted, unless the approved design explicitly accepts that risk.

### Step 8 — Mark known failure or unknown outcome

Use known failure only when the operation can prove its effect did not complete or the policy defines the failed result as replayable/terminal.

Use unknown outcome when the effect may have occurred. Preserve enough safe metadata for support/reconciliation without storing customer secrets.

### Step 9 — Configure TTL

The TTL should exceed:

```text
maximum operation duration
+ bounded internal retry/backoff
+ expected client retry window
+ safety margin
```

It must also respect privacy/retention rules. Document why the value is adequate.

### Step 10 — Map outcomes to the public contract

Define a matrix:

```text
Idempotency outcome
  -> application result
  -> workflow status and nextStep
  -> public error code
  -> HTTP status
  -> retry/restart behavior
  -> log severity
```

Do not expose internal Redis status or request hashes.

## 4. Integrating message delivery

For an OTP/message send:

```text
validate session/CSRF/state
  -> reserve idempotency
  -> rate limit/cooldown checks
  -> create challenge
  -> call MessageDeliveryPort
  -> bind challenge/session
  -> complete snapshot
```

A completed replay returns masked destination and timing data. It never generates or sends a second code.

If the provider times out after transmission, mark unknown rather than automatically send again.

## 5. Integrating APIM/TAS mutations

Before classifying an APIM mutation as retryable/idempotent, confirm downstream semantics. BFF Redis idempotency prevents duplicate entry through this BFF while the record exists; it cannot by itself guarantee that TAS did not execute after a timeout.

For a downstream mutation:

- Reserve before the call
- Propagate the same `X-Request-Id` across bounded internal attempts
- Use a downstream idempotency/business reference where the APIM/TAS contract supports it
- Mark unknown on ambiguous timeout
- Reconcile or guide the user rather than retry blindly

## 6. Integrating completion locks

Use a session-scoped completion lock when two different keys or operations could concurrently enter the same critical completion section.

Acquire after preliminary validation and according to the documented ordering with idempotency. Release only with the ownership token. Define behavior for lock contention and lease expiry.

Idempotency and locks are not substitutes:

- Idempotency coordinates repeated logical requests by action/key/hash.
- A lock serializes critical work for a shared resource/session.

## 7. Client contract

The frontend/client must:

- Generate a sufficiently unique key for each new logical mutation
- Reuse the same key only when retrying the exact same request
- Generate a new key when any semantic input changes
- Avoid parallel requests with different keys for the same user action
- Preserve the key across a network retry
- Handle conflict/waiting, replayed success, and restart guidance
- Never place credentials or PII inside the key

## 8. Worked example: Forgot Password

```text
/start
  action FORGOT_PASSWORD_START
  hash identity-verification inputs in canonical protected form

/otp/send
  action FORGOT_PASSWORD_OTP_SEND
  hash opaque selected destination ID and scenario

/otp/resend
  action FORGOT_PASSWORD_OTP_RESEND
  hash session-bound resend semantics

/otp/verify
  action FORGOT_PASSWORD_OTP_VERIFY
  prevent duplicate verification/reset-authorization creation

/complete
  action FORGOT_PASSWORD_COMPLETE
  protect the TAS/APIM password-reset mutation
```

Each action needs its own TTL, replay snapshot, and unknown-outcome policy. Do not include the clear password, OTP, identity value, session ID, or CSRF token in the persisted snapshot or Redis key.

## 9. Pull-request checklist

- [ ] Mutation and duplicate-side-effect risk identified
- [ ] Stable action defined
- [ ] Key validation reused
- [ ] Canonical hash fields documented and tested
- [ ] Reservation occurs before side effect
- [ ] Every reservation outcome handled
- [ ] Replay snapshot is typed and safe
- [ ] Sensitive headers/payloads excluded
- [ ] Known failure and unknown outcome distinguished
- [ ] TTL justified
- [ ] Downstream idempotency limitations documented
- [ ] Lock requirements reviewed
- [ ] Concurrent duplicate tests included
- [ ] Crash-boundary tests included
- [ ] Fail-first TDD evidence included
- [ ] Operations document updated
- [ ] Commit message included

Suggested commit message:

```text
docs(idempotency): document lifecycle, reuse, operations, and testing
```

Action integrations provide validated logical identities only. They do not construct Redis keys. The shared adapter derives the complete locator with HMAC-SHA-256 and owns schema-versioned persistence. Action integrations never receive the HMAC secret or construct persistence identities.

Action code must retain the ACQUIRED reservation token only in memory and supply it to the terminal transition. Duplicate callers never receive ownership and cannot transition the record.

Every action integration must provide a conservative failure classifier. It may choose PROVEN_NO_SIDE_EFFECT only when dispatch and externally visible acceptance are disproved; otherwise it must choose POSSIBLE_SIDE_EFFECT.

Consumers inject IdempotencyExecutor. They do not instantiate Redis components, access the HMAC secret, or inject feature-specific idempotency ports into the shared executor.

The first migrated consumer must be deployed with shared idempotency enabled and a valid dedicated HMAC secret. Environments with no migrated consumer keep the capability disabled and do not need the secret.

<!-- P3-REGISTRATION-MIGRATION-BASELINE -->
## P3 Registration integration rules

Each Registration action integration must provide the following action-owned components around the shared executor:

1. A canonical `IdempotencyOperationId` factory.
2. A versioned `IdempotencyFingerprint` factory based only on normalized request equivalence inputs.
3. An `IdempotentResponseCapture` that stores every deterministic terminal action result, not only success.
4. A `ProtectedReplayResolver` that reconstructs the action result without re-running side effects.
5. An `IdempotencyFailureClassifier` aligned to the action's actual dispatch boundary.
6. A mapping from shared lifecycle outcomes to the existing Registration result and error contracts.

### Action-specific boundaries

- Start protects registration lookup, auth-flow creation or mutation, OTP creation, challenge persistence, and message delivery.
- OTP Resend protects resend policy acceptance, OTP generation, challenge update, session mutation, and message delivery.
- OTP Verify protects OTP verification, verified-state persistence, trust lookup, and eligible-trust persistence.
- Complete protects completion locking, APIM completion, completion-state persistence, continuation trust lookup, and cancellation or finalization effects.

Existing controllers continue validating required headers and request bodies. Existing web mappers continue owning public `MutationResponse`, `ApiStatus`, and HTTP mapping. Shared infrastructure must not emit Registration-specific public responses.

P3-A introduces bridge contracts without changing action orchestration. Subsequent stages migrate one action at a time in the order Start, OTP Resend, OTP Verify, Complete. No Registration-specific Redis class is deleted until all four actions and the legacy-record policy are complete.

P3-A1 bridge factories are action-owned. Later action writers must pass normalized equivalence fields in an explicit stable order and choose either the proven-pre-dispatch or possible-side-effect classifier only at a documented boundary.

P3-A2 requires each later action migration to encode its deterministic public result with the stable action tag before capture. Protected continuity may be referenced only through an opaque ProtectedReplayReference; unsafe headers remain rejected by the shared snapshot contract.

Replay implementations must use RegistrationSharedResponseEnvelope decoding for action validation. They must not infer an action with string containment, regular expressions, or nested payload content.

P3-B1 preserves the existing Start web mapper by hydrating StartRegistrationResult with its original status. Deterministic failures require no protected reference, while STARTED requires separately protected CSRF and session continuity.

Registration Start capture now supplies a positive TTL to RegistrationStartProtectedReplayPort. Protected replay storage uses secure-random references, bounded collision retry, authenticated encryption, strict decoding, and fail-closed loading.

Action capture integrations may perform asynchronous protected persistence and must emit exactly one captured response. Empty or failed capture causes an ownership-checked UNKNOWN_OUTCOME transition.

RegistrationStartProtectedOperation contains no reserve, complete, fail, replay, Redis, Spring, or executor dependency. P3-B2C will invoke it only through IdempotencyExecutor for ACQUIRED.

RegistrationStartSharedExecutionBridge converts typed Start results to and from the context-neutral executor response, delegates reactive safe capture to RegistrationStartReplayCodec, and rehydrates protected replay state without invoking RegistrationStartProtectedOperation.

Registration Start uses a constant anonymous operation scope that is hashed by RegistrationSharedIdempotencyIdentityFactory. Its version-1 fingerprint preserves the legacy six-field normalization order and excludes language, request ID, and session cookie.

The legacy StartRegistrationService and its regex/current-state replay helpers are removed. RegistrationIdempotencyPort remains only for OTP Resend, OTP Verify, and Registration Complete until their later migration rounds.

When a protected workflow is extracted from a coordinator, source-path guards must move to the class that now owns the asserted behavior. Guards must not rely on deleted paths or conditional file existence that can silently bypass assertions.

OTP Resend keeps auth-flow and CSRF authorization before shared reservation. Its shared operation scope will be the opaque hash of the authorized auth-flow session, while the version-1 fingerprint contains only that same session-bound resend semantic.

OTP Resend has no protected replay reference because its approved result is already limited to masked destination and public timing or error metadata. Raw OTP, challenge, session, CSRF, destination, language, and correlation values remain excluded.

OTP Resend uses the possible-side-effect classifier because unexpected failures can occur after challenge replacement, session mutation, or message dispatch. Deterministic policy and known delivery results are normal captured terminal responses.

Production wiring may switch before legacy test harness removal when the old coordinator is no longer referenced by configuration. This keeps each stage compile-clean while allowing deterministic harness migration next.

Shared Resend tests retain the original RESENT or deterministic rejection status on replay and prove that replay performs no OTP replacement, session mutation, or message delivery.

After migration, source guards must target live shared components. Guards that inspect deleted legacy coordinators are invalid because missing-source behavior can silently weaken architecture coverage.

RegistrationOtpVerifyProtectedOperation requires an already validated command and already authorized protected context. It propagates unexpected infrastructure failures for conservative classification by the future shared coordinator.

The OTP Verify safe replay contract permits only accountEnv and trustCode for each public trust. It rejects malformed payloads, unknown fields, action mismatch, unsupported schema or enum values, missing collections, and blank trust fields.

Shared OTP Verify request equivalence uses opaque session scope plus SHA-256 of the submitted OTP. Raw OTP, context data, language, and correlation metadata are excluded from persisted identity and replay state.

RegistrationOtpVerifyUseCaseConfig no longer injects RegistrationIdempotencyPort or RegistrationRequestHashGenerator. It composes the shared executor with the OTP Verify protected operation and safe replay components.

Shared OTP Verify harnesses compare operation identity and fingerprint, invoke response capture before storing completion, and invoke the replay resolver for completed records. Same key with a different OTP digest maps to key reuse.

The Registration lookup boundary guard now targets RegistrationOtpVerifyProtectedOperation, the active owner of the APIM registration lookup call. No source guard depends on the deleted legacy coordinator.

RegistrationCompleteProtectedOperationInput is constructed only after context authorization, expiry refresh, state checks, username-state loading, and password validation. Its string representation redacts every component rather than delegating to nested values.

The extracted Registration Complete operation accepts RegistrationCompleteProtectedOperationInput and deliberately has no idempotency persistence dependency. Pre-dispatch and ambiguous failures propagate for later shared classification instead of being converted into completed responses.

RegistrationCompleteProtectedOperation is now behaviorally characterized without production switching. Callers may proceed to typed replay design with the operation boundary stable.

RegistrationCompleteReplayCodec reconstructs RegistrationLookupAccount with blank protected contact fields because the web contract exposes only accountEnv and trustCode. No encrypted protected replay store is required for Complete.

SharedCompleteRegistrationService uses the auth-flow session as opaque operation scope and fingerprints the session plus SHA-256 password digest. Plaintext password and other protected values never enter persisted identity or snapshot material.

RegistrationCompleteUseCaseConfig no longer injects RegistrationIdempotencyPort or RegistrationRequestHashGenerator. It injects IdempotencyExecutor plus the shared Registration identity and fingerprint factories.

Registration Complete tests must not instantiate CompleteRegistrationService. Coordinator tests inspect prerequisite and shared lifecycle ownership; acquired business workflow tests exercise RegistrationCompleteProtectedOperation.

Do not introduce RegistrationIdempotencyPort or RegistrationRequestHashGenerator in new integrations. Registration actions must use IdempotencyExecutor with the shared Registration identity, fingerprint, response, and replay contracts.

P3-E6 repair retains RegistrationIdempotencyKey, RegistrationIdempotencyStartOutcome, and RegistrationIdempotencyResponseSnapshot as active shared/public compatibility vocabulary. The removed legacy scope is limited to the action-specific persistence lifecycle, coordinator, port, record/status/request-hash types, Redis lifecycle adapter, commands, and dedicated stale tests.

P3-E6 final guard cleanup remaps the Registration Complete unknown-outcome architecture assertion from the removed RegistrationIdempotencyUnknownOutcomeCommand to the shared UNKNOWN_OUTCOME_EXISTING lifecycle route. Exact audits ignore historical comments and approved negative guards while rejecting active legacy imports, declarations, mocks, constructors, and source-path dependencies. P3-E6 is complete when compile and test-compile pass.

## P4 Login migration baseline

P4 migrates the complete Login idempotent action family from Login-specific lifecycle persistence to the shared idempotency capability. The committed starting baseline is Git HEAD `53aad36a1e0916a237d86ee67d7bc331cb3e190f` on `fix/relay-same-error-code-for-the-idempotent-request`. No Login production implementation may begin until this documentation baseline is committed.

The in-scope action sequence is:

1. P4-A: shared Login identity, fingerprint, failure-classification, response, and replay bridge contracts.
2. P4-B: Login Start and credential authentication migration.
3. P4-C: Login OTP Send migration.
4. P4-D: Login OTP Resend migration.
5. P4-E: Login OTP Verify migration with committed-login recovery preservation.
6. P4-F: bounded compatibility closure and removal of obsolete Login-specific idempotency lifecycle code.
7. P4-G: one formatting pass, clean-target cumulative compile and test-compile, architecture and security audits, documentation alignment, then the implementation commit.

Login Cancel is excluded because it has no approved idempotency contract. Adding it to the shared executor requires a separate design decision.

### Mandatory P4 invariants

- The shared capability remains independent of Login, sessions, actors, accounts, Spring, web, APIM, Redis implementation details, and protected workflow models.
- Only `ACQUIRED` may invoke credential authentication, OTP creation or replacement, message delivery, OTP consumption, account lookup, Account-Ref assignment, authenticated-session creation, or auth-flow cleanup.
- `COMPLETED_REPLAY` hydrates the original typed public result. It is lifecycle metadata and must not replace the original public result status.
- Same idempotency key with a different versioned fingerprint maps to key reuse and executes no protected operation.
- Deterministic terminal business outcomes are completed and replayable. They are not persisted as failed merely because the public outcome is unsuccessful.
- Only a failure proven to occur before any side effect may use `PROVEN_NO_SIDE_EFFECT`. Any dispatched or uncertain credential, OTP, message, session, account, or cleanup effect uses `POSSIBLE_SIDE_EFFECT`.
- Raw username, password, OTP, session identifiers, CSRF values, destination values, challenge identifiers, account details, authorization material, cookies, and internal downstream details must not enter the shared safe snapshot or diagnostics.
- Every P4 implementation stage updates all six documents in `docs/capabilities/idempotency`, including `handover.md`.

### Action-specific replay boundary

Login Start requires separately protected replay continuity. The shared safe snapshot may contain only deterministic public metadata and an opaque protected replay reference. The existing AES-GCM Login Start replay store is retained initially to recover the auth-flow session, CSRF token, destination choices, expiry, and cookie continuity without repeating credential authentication or creating a second auth-flow session.

Login OTP Send and OTP Resend use strict typed safe snapshots for masked destination and approved timing or retry metadata. Raw destination, generated OTP, challenge identity, auth-flow session, CSRF data, request correlation, and language are excluded. Message-delivery unknown outcome remains an unknown idempotency outcome.

Login OTP Verify uses a two-layer design. The shared executor owns outer reservation and terminal response lifecycle. OTP consumption proof, authenticated-login handoff, authenticated-session recovery, and auth-flow cleanup proof remain protected recovery evidence until a replacement proves equivalent crash recovery. Successful replay must not consume OTP again, repeat account lookup, assign new Account-Refs, create another authenticated session, or repeat auth-flow cleanup.

### Compatibility and commit policy

P4 must identify the exact Login-specific Redis namespaces and record schemas before routing changes. Existing records receive an explicit bounded policy: read compatibility, namespace isolation until TTL expiry, or fail-closed invalidation. Legacy readers and writers are removed only after all four Login actions use the shared executor and recovery evidence proves no remaining consumer.

The documentation baseline commit is:

```text
docs(idempotency): define login migration baseline
```

After that commit, all P4 implementation stages remain cumulative and uncommitted until P4-G. The final implementation commit is:

```text
refactor(login): migrate idempotency to shared capability
```

## P4-A1 Login identity, fingerprint, and classifier bridge

P4-A1 adds a Login-owned bridge over the shared idempotency contracts without changing production routing. The stable action identities are `login/credentials`, `login/otp-send`, `login/otp-resend`, and `login/otp-verify`. Raw authorized session scope is transformed to an opaque SHA-256 value before entering `IdempotencyOperationScope`; the shared persistence HMAC remains a separate outer protection.

Version-1 fingerprints are length-delimited and action-separated. Credentials preserve normalized username plus a one-way password digest. OTP Send preserves authorized auth-flow session plus destination ID. OTP Resend preserves authorized auth-flow session only. OTP Verify preserves authorized auth-flow session plus a one-way submitted-OTP digest. CSRF, language, origin, referer, correlation ID, cookies, and raw destinations remain excluded unless explicitly part of the established action semantic.

The bridge exposes only `provenPreDispatch()` and `possibleSideEffect()` classifiers. Selecting either remains the responsibility of the later action coordinator at a documented dispatch boundary. P4-A1 does not modify Login Start, OTP Send, OTP Resend, or OTP Verify production composition and does not remove any Login-specific persistence or recovery type.

Next stage: P4-A2 adds strict typed response envelopes, safe capture, protected replay references, and original-result hydration. Production routing remains unchanged until each action migration stage.

## P4-A1 OTP Verify fingerprint correction

The P4-A2 contract capture confirmed that Login OTP Verify has an established constant request-equivalence marker, `LOGIN\u0000OTP_VERIFY\u0000v1`. The shared Login fingerprint now preserves that contract. The authorized auth-flow session remains the opaque operation scope and is not duplicated in the fingerprint. The submitted OTP, including any digest of it, is excluded from shared request equivalence.

This separation is required so a completed or committed Login verification remains recoverable after OTP consumption and auth-flow cleanup. It preserves the existing `LoginOtpConsumptionProof`, authenticated-login handoff, authenticated-session replay, and CSRF rotation design. Production Login routing remains unchanged. P4-A2 response bridges may proceed after this repair passes.

## P4-A2A strict Login response envelope

P4-A2A adds the Login-owned strict response envelope used by later action-specific transient and safe replay codecs. The envelope contains exactly `idempotencyAction` and `payload`. It recognizes only `credentials`, `otp-send`, `otp-resend`, and `otp-verify`, validates the expected action by exact decoded value, and never infers action identity from nested payload text.

Malformed JSON, blank input, missing or null payload, unsupported action, wrong expected action, unknown top-level fields, and unknown typed payload fields fail closed with sanitized diagnostics. P4-A2A does not yet define an action result codec, create a terminal snapshot, store protected replay state, hydrate Login results, or alter production composition.

Next stage: P4-A2B introduces the Login Start typed transient bridge and safe snapshot using the existing separately protected Login Start replay reference. Production routing remains unchanged.

## P4-A2B Login Start typed and protected replay bridge

P4-A2B adds a typed transient execution bridge and strict safe replay codec for Login Start. The transient representation exists only in memory and may carry the auth-flow session ID, CSRF token, destination choices, expiry, and existing replay commitment reference. Capture persists only the original public status and fail count under the `credentials` action envelope. A successful `STARTED` snapshot carries one opaque `ProtectedReplayReference` mapped to the existing `rpl_...` AES-GCM replay record.

Hydration loads and decrypts the existing protected replay record, validates expiry, and reconstructs `StartLoginStatus.STARTED`. It never returns `COMPLETED_REPLAY` as the public status and never authenticates credentials, creates a session, generates CSRF, sends a message, or writes a cookie. Deterministic credential rejections are captured without protected state. Lifecycle and protocol outcomes, including `COMPLETED_REPLAY`, `DUPLICATE_IN_PROGRESS`, `KEY_REUSED`, `FAILED_EXISTING`, `UNKNOWN_OUTCOME`, and technical `UNAVAILABLE`, are not capturable terminal business results.

The existing Login Start replay port, AES-GCM protector, Redis adapter, reference generator, coordinator, Spring composition, controller, and production `StartLoginService` routing remain unchanged. Next stage: P4-A2C adds the OTP Send safe typed bridge.

## P4-A2B acquired-response separation repair

P4-A2B preserves two distinct response representations. The acquired caller receives the original transient hydrated response, including the protected values required by the existing controller and mapper. The shared terminal record persists only the stripped safe action envelope and the optional opaque protected replay reference. Capture requires matching HTTP status, media type, and safe headers between those representations.

The authoritative Login Start transport mapping is `STARTED` to 200, `INCORRECT_PASSWORD`, `INCORRECT_USERNAME`, and `INVALID_ACCOUNT` to 401, and `ACCOUNT_LOCKED` to 423. Completed replay hydrates the protected record into an equivalent transient response while retaining the original `STARTED` public status. Production routing and the existing protected replay infrastructure remain unchanged.

## P4-A2B strict snapshot hydration hardening

Login Start hydration validates shared snapshot schema version 1 and `application/json` media type before decoding the safe body or loading protected replay. Unsupported schema and invalid media type fail closed without protected storage access. Successful bridge-level replay proves that the acquired transient response is preserved, the shared snapshot remains stripped, and the protected replay record reconstructs the original `STARTED` status, auth-flow session, CSRF token, destinations, replay reference, and positive remaining expiry.

The P4-A2B contract also includes an actual expired protected-record test. Expired, missing, malformed, or unauthenticated protected replay never falls through to credential authentication or a fresh Login Start operation. Production routing and all existing protected replay persistence remain unchanged. P4-A2B is complete after this hardening passes.

## P4-A2C Login OTP Send safe typed bridge

P4-A2C adds the strict `otp-send` codec and transient execution bridge. It preserves `SENT`, `AUTH_FLOW_INVALID`, `INVALID_DESTINATION`, `DELIVERY_FAILED`, and `CSRF_INVALID` as deterministic terminal results. `COMPLETED_REPLAY`, duplicate, key-reuse, failed-existing, and unknown-outcome statuses remain lifecycle outcomes and are not captured as public results.

The acquired caller retains its original transient response. The shared snapshot contains only status, masked destination, expiry, resend cooldown, and retry-after data under the strict action envelope. It has empty safe headers and no protected replay reference. OTP Send does not introduce a `Retry-After` header. Hydration validates schema, media type, action, status, HTTP classification, empty headers, null protected reference, and typed payload before restoring the original public status.

The OTP Send bridge performs no authorization, destination lookup, OTP generation, challenge mutation, session update, message delivery, or legacy persistence operation. Production `IdempotentLoginOtpSendService`, `LoginOtpSendIdempotencyPort`, Redis persistence, controller, and mapper remain unchanged. Next stage: P4-A2D adds the OTP Resend safe typed bridge.

## P4-A2D Login OTP Resend safe typed bridge

P4-A2D adds the strict `otp-resend` codec and transient execution bridge. It captures `RESENT`, `AUTH_FLOW_INVALID`, `COOLDOWN_ACTIVE`, `EXPIRED`, `LOCKED`, `NOT_REPLACEABLE`, `DELIVERY_FAILED`, and `CSRF_INVALID` as deterministic terminal responses. Lifecycle statuses, `UNKNOWN_OUTCOME`, and technical `SYSTEM_FAILED` are not capturable public results.

The acquired caller retains its original transient response. The terminal snapshot contains only the strict typed envelope and, when `retryAfterSeconds` is positive, the exact canonical `Retry-After` header. Payload and header values must agree. Missing, additional, malformed, non-positive, or mismatched retry headers fail closed. The snapshot carries no protected replay reference.

Hydration validates schema version, JSON media type, exact action, status, HTTP mapping, headers, protected-reference absence, and result-field invariants. Replay restores the original status rather than `COMPLETED_REPLAY`. The bridge performs no context authorization, challenge replacement, OTP generation, delivery, session binding, or persistence operation. Production `IdempotentLoginOtpResendService`, `LoginOtpSendIdempotencyPort`, Redis persistence, controller, and mapper remain unchanged. Next stage: P4-A2E captures the Login OTP Verify response and protected recovery contract.

#### P4-A2E1 Login OTP Verify protected recovery resolver

P4-A2E1 adds an application-layer resolver from the shared snapshot's opaque OTP Verify protected replay reference to the existing committed-login recovery boundary. The protected reference is validated as the existing authenticated-login handoff reference.

The protected resolver delegates the opaque authenticated-login handoff reference to a dedicated committed-login recovery entry point. Recovery loads and validates the persisted handoff and authenticated session directly. It requires an `AUTH_FLOW_DELETED` handoff with an authenticated session and cleanup proof, enforces handoff expiry through the repository clock, validates authenticated-session identity and usability, and completes only after atomic CSRF-hash rotation succeeds. It does not reconstruct or synthesize a `LoginOtpConsumptionProof`.

Successful recovery returns the original `ACCOUNTS_READY` status with the existing authenticated session and assigned accounts plus a freshly rotated authenticated CSRF token. Missing, malformed, non-cleaned, conflicting, expired, unavailable, or failed recovery state fails closed.

The existing proof-based `RecoverCommittedLoginUseCase` remains available to the legacy `LoginOtpVerifyService` and continues validating authentic proof identity. The direct-handoff resolver never verifies or consumes an OTP, loads pre-authentication context, queries accounts, validates accounts, assigns Account-Refs, creates another authenticated session, repeats auth-flow cleanup, writes cookies, or accesses Redis adapters directly. P4-A2E1 does not add the safe response codec, alter production composition, or remove `LoginOtpVerifyPersistencePort`, consumption-proof persistence, handoff persistence, replay-session persistence, or CSRF-rotation infrastructure. P4-A2E2 will add the strict OTP Verify codec and typed transient bridge.

#### P4-A2E2 Login OTP Verify strict replay bridge

P4-A2E2 adds the strict `otp-verify` safe replay codec and typed transient execution bridge. The acquired caller retains the original transient response. The shared terminal snapshot contains only the original status, remaining verify attempts, and auth-flow-cookie clearing instruction. It has empty safe headers. Accounts, Account-Refs, authenticated-session identity, authenticated CSRF, cookie values, and protected recovery evidence never enter the safe body.

`ACCOUNTS_READY` requires exactly one opaque protected replay reference identifying the committed authenticated-login handoff. Completed replay delegates that reference to the P4-A2E1 direct-handoff recovery boundary, validates the recovered result, and returns the original `ACCOUNTS_READY` status with the existing session and accounts plus a freshly rotated authenticated CSRF token. Deterministic unsuccessful results carry no protected reference and replay directly from the safe typed payload.

Hydration validates schema version, JSON media type, exact action, supported status, HTTP classification, empty headers, protected-reference presence, and result invariants. `SYSTEM_FAILED` and inactive `ALREADY_VERIFIED` remain non-capturable. The bridge performs no OTP verification or consumption, protected-context loading, account lookup, Account-Ref assignment, authenticated-session creation, auth-flow cleanup, cookie emission, shared lifecycle transition, or Redis access. Production `LoginOtpVerifyService`, `LoginOtpVerifyPersistencePort`, controller, mapper, and proof-based recovery route remain unchanged. P4-A2E3 will complete focused contract coverage and cumulative review before action migration.

#### P4-A2E3 OTP Verify bridge contract completion

P4-A2E3 completes the compile-checked OTP Verify replay matrix before production migration. Strict tests reject unknown, missing, null, and unsupported safe payload fields and statuses; invalid remaining-attempt and cookie-clearing combinations; malformed transient fields; and successful protected recovery that is empty, unsuccessful, incomplete, or failed. Malformed snapshots and deterministic unsuccessful results never invoke committed-login recovery.

The production P4-A2E1 direct-handoff resolver, P4-A2E2 codec, and transient bridge remain unchanged in this stage. Architecture audits continue to prohibit OTP verification, account lookup, Account-Ref assignment, authenticated-session establishment, auth-flow cleanup, Redis adapters, Spring, and legacy OTP Verify persistence from the response bridge. Production `LoginOtpVerifyService`, proof-based recovery, and legacy persistence routing remain active until the later action migration stage.

### P4-B1B Login Start protected operation extraction

P4-B1B adds `LoginStartProtectedOperation` as a persistence-neutral acquired-execution boundary. Its input is the credential command already validated and normalized by the Login credential policy. Credential authentication is its first external dispatch. Deterministic credential rejections are returned directly without creating protected context, auth-flow session, CSRF state, destination projections, or replay state. `UNAVAILABLE` remains an indeterminate result for the later shared coordinator to classify conservatively.

For authenticated credentials, the operation preserves the existing sequence: create and protect the Login pre-authentication context, store the four encrypted context attributes in a revisioned Login auth-flow session, establish CSRF continuity through `CreateAuthFlowSessionCsrfUseCase`, retain destination insertion order, and return `STARTED` with no replay commitment reference. Unexpected failures propagate unchanged.

The operation owns no idempotency reservation, completion, failure, unknown transition, or replay persistence. It has no dependency on the legacy credential idempotency coordinator, shared executor, Redis adapters, Spring, controllers, or HTTP contracts. `StartLoginService`, `LoginApplicationConfig`, `LoginStartReplayCoordinator`, all existing production routes, and the protected replay single-write ordering remain unchanged. P4-B1C will characterize and reuse this operation from the legacy service before any shared production switch.


### P4-B1C Login Start legacy delegation

`StartLoginService` remains the active production Login Start lifecycle coordinator. It continues to validate and normalize credentials, derive the legacy request hash, reserve the legacy idempotency record, route every reservation outcome, finalize deterministic results, commit and replay protected Login Start continuity, and mark ambiguous acquired failures as unknown.

Only acquired protected execution now delegates to `LoginStartProtectedOperation`. Direct credential-authentication, protected-context, auth-flow-session, CSRF, destination-projection, clock, and session-policy dependencies have been removed from `StartLoginService` and are composed once in the protected-operation bean. The protected operation retains its empty-result fail-closed guard and remains independent of both legacy and shared idempotency lifecycle persistence.

Production still exposes `StartLoginService` as `StartLoginUseCase`. The existing protected replay save-before-legacy-complete ordering, public Login Start mapping, cookie behavior, OTP Send, OTP Resend, OTP Verify, and Login Cancel routes are unchanged. No shared-executor production switch or legacy-code removal occurs in P4-B1C. The next approved stage may add the shared Login Start coordinator in parallel.

### P4-B2A1 shared executor terminal-transition separation

P4-B2A1 corrects the shared executor before Login Start shared coordination is introduced. The action failure classifier now applies only to failures emitted by the protected operation. Once a protected operation has returned a response, response-capture failure, empty capture, and shared completion-persistence failure enter the ownership-checked unknown-outcome path exactly once and cannot fall through to a later failed transition.

Compile-checked executor coverage requires one unknown transition, zero failed transitions, and zero action-classifier invocations for capture and completion failures. Failure to persist UNKNOWN_OUTCOME returns the sanitized shared-executor error and still never attempts FAILED. Protected-operation failures continue to invoke the supplied classifier exactly once. Login Start remains routed through StartLoginService, and no Login action production route changes in this repair.

#### P4-B2B1B protected Login Start replay commit extraction

P4-B2B1B extracts `LoginStartProtectedReplayCommitter` as the lifecycle-neutral owner of protected Login Start replay persistence. It accepts only `STARTED`, requires positive session and maximum TTL values, retains the existing `min(maximum idempotency TTL, auth-flow lifetime)` save TTL, derives payload expiry from the auth-flow lifetime, generates one existing `rpl_...` reference, protects once, saves once, and returns the original result with only the replay reference added.

`LoginStartReplayCoordinator` keeps its existing public construction and production role. Its legacy commit path delegates protected persistence to the committer, then completes the legacy credential idempotency record. Protected-save or legacy-completion failure retains the existing legacy unknown-outcome behavior. No replay namespace, protector, reference generator, Redis adapter, replay loader, or production Login route changes.

The approved future shared Login Start operation scope is the versioned anonymous constant `login-start-anonymous-v1`. The operation identity hashes that constant through `LoginSharedIdempotencyIdentityFactory`; request equivalence remains the version-1 fingerprint of normalized username plus the one-way digest of the exact password. This ensures same-key requests with a changed username or password map to key reuse without treating an unverified username as an authorized scope. P4-B2B1B does not yet add or switch to the shared Login Start coordinator.

#### P4-B2B1B reactive committer closure

The protected replay committer now defers validation, reference generation, protection, and persistence through `Mono.defer`, so invalid inputs and owned-step failures are emitted consistently through the reactive boundary. Compile-checked coverage includes null, zero, and negative session and maximum TTL values, reference-generation failure, protection failure, save failure, and proof that no later owned step runs after an earlier failure. The legacy coordinator delegation, constructor compatibility, replay behavior, and `StartLoginService` production route remain unchanged.

### P4-B2B parallel shared Login Start coordinator

P4-B2B adds `SharedStartLoginService` in parallel with the active legacy `StartLoginService`. It reproduces credential policy validation and normalization before shared executor invocation, uses the exact anonymous scope `login-start-anonymous-v1`, and fingerprints the normalized username with the one-way digest of the exact password.

Only the shared executor acquired path invokes `LoginStartProtectedOperation`. A `STARTED` result is persisted through `LoginStartProtectedReplayCommitter` before safe response capture; deterministic credential rejections skip protected replay persistence; `UNAVAILABLE` enters the possible-side-effect failure path. Completed replay hydrates through `LoginStartSharedExecutionBridge`, while duplicate, key reuse, failed-existing, and unknown-existing outcomes map to the existing public Login Start statuses.

The class is not selected by Spring in this stage. Production remains on `StartLoginService`; legacy Login idempotency and replay types remain intact until the later approved switch.

#### P4-B2B behavioral coordinator test closure

The parallel `SharedStartLoginService` now has executable focused tests rather than source-text-only checks. Coverage proves validation stops before shared execution, normalized credentials and the exact password feed the approved identity and fingerprint boundary, only acquired execution invokes the protected operation, `STARTED` commits protected replay before transient conversion, deterministic rejections skip replay commitment, `UNAVAILABLE` fails conservatively, completed replay avoids protected execution, all existing lifecycle outcomes map correctly, and protected replay commitment failure propagates. Production remains wired to `StartLoginService`.

#### P4-B2C Login Start production switch

The production `StartLoginUseCase` now selects `SharedStartLoginService` and uses `auth-workflows.login.idempotency.start-ttl-seconds` through `AuthWorkflowProperties`. The shared response bridge and protected replay committer are composed in the outer Login configuration. `StartLoginService`, its legacy idempotency coordinator, request hasher, replay coordinator, Redis adapters, records, and tests remain available for a composition-only rollback. Legacy removal is deferred to a separately reviewed stage.

## P4-B2D1B focused runtime composition and web boundary validation

P4-B2D1B adds deterministic, infrastructure-free Spring composition evidence. An `ApplicationContextRunner` creates `LoginApplicationConfig` with local fake outer ports, proves exactly one `StartLoginUseCase` bean of type `SharedStartLoginService`, and proves the configured 300-second Login Start TTL reaches `IdempotentExecutionCommand`. The same evidence confirms the retained legacy coordinator, hasher, replay coordinator, and source remain available for composition-only rollback.

This is focused composition evidence, not full deployed startup or live Redis evidence. Sentinel, mTLS, APIM, live HTTP, target-environment Redis writes, rollback rehearsal, legacy TTL drainage, and legacy deletion remain deferred.

## P4-B2D1D deterministic namespace isolation validation

P4-B2D1D adds an infrastructure-free key-derivation test proving that the active shared Login Start lifecycle and retained legacy Login credential lifecycle use structurally disjoint Redis namespaces. The shared key uses `ngtpa:idempotency:v1:<HMAC>`; the legacy rollback key uses `ngtpa:auth:idempotency:login:credentials:<HMAC>`. For the same client key, the outputs differ and expose neither the raw client key, anonymous scope, username, password, auth-flow session, nor CSRF value.

This evidence validates deterministic key derivation only. Live Redis writes, Sentinel and mTLS connectivity, deployed HTTP validation, rollback rehearsal, legacy TTL drainage, and legacy deletion remain deferred. Production Java and the retained rollback path are unchanged.

## P4-B2D1L production-composed Login controller boundary validation

A focused deterministic controller-boundary test now obtains the production `StartLoginUseCase` from `LoginApplicationConfig`, exercises successful shared `STARTED` reconstruction and missing-key short-circuiting, and verifies the 300-second TTL, request ID, client key, auth-flow cookie, and public-response redaction. The test uses local collaborators only. Deployed HTTP, live Redis, rollback rehearsal, legacy TTL drainage, and legacy deletion remain deferred.

## Shared Login and Registration runtime composition: authoritative state

The shared idempotency runtime is active for the migrated Login Start and Registration workflows. Production composition provides one UTC application `Clock`, one shared `IdempotencyExecutor`, mandatory Registration Start composition, shared Registration identity and fingerprint factories, and externalized Registration Start replay key-version and secret placeholders. The active Login Start route remains `SharedStartLoginService`, while retained legacy Login components remain available only for controlled rollback until a separately approved removal stage.

The full-context startup defect was caused by the automatic execution-logging pointcut matching a private `execute(...)` orchestration helper on final `SharedCompleteRegistrationService`. With class-based Spring AOP proxies enabled, the broad match made the final class proxy-eligible and caused CGLIB startup failure. The root correction restricts convention-based logging to public `*Service.execute(..)` entry points. The ineffective Complete Registration scoped-proxy workaround and its synthetic test are removed. Registration Start replay configuration remains externalized through `SHARED_IDEMPOTENCY_REGISTRATION_START_REPLAY_KEY_VERSION` and `SHARED_IDEMPOTENCY_REGISTRATION_START_REPLAY_SECRET`; secret values are never committed or included in evidence.

Validation requires compile, test-compile, the focused pointcut contract, shared Login and Registration composition regressions, and production-shaped local Spring startup with the `local` profile, LF-normalized ignored `.env`, and Redis Sentinel mTLS inputs. Deployed HTTP validation, rollback rehearsal, legacy TTL drainage, and legacy component deletion remain separate approval gates.

## P4-C1 parallel shared Login OTP Send coordinator

P4-C1 adds `SharedLoginOtpSendService` as a framework-neutral parallel coordinator. The active production route remains `IdempotentLoginOtpSendService` until P4-C2. The parallel coordinator derives the shared `login/otp-send` operation identity from the opaque auth-flow session scope and client key, fingerprints the auth-flow session and destination ID, and invokes `LoginOtpSendService` only through the shared executor acquired callback. It uses the existing strict OTP Send bridge and conservative possible-side-effect classification. Completed replay restores the original public status rather than exposing shared lifecycle metadata. Legacy Login OTP Send persistence, Redis writers, tests, and rollback composition remain retained, and global formatting remains deferred until P4-G.

## P4-C2 Login OTP Send production composition switch

P4-C2 selects `SharedLoginOtpSendService` as the sole production `SendLoginOtpUseCase`. `LoginOtpSendService` remains the protected operation and executes only for shared lifecycle acquisition. The composition uses the existing shared executor, strict OTP Send response bridge, opaque Login identity factory, versioned fingerprint factory, and configured OTP Send TTL. The legacy Login OTP Send port, Redis adapter, records, and rollback implementation remain retained until P4-F, while OTP Resend and OTP Verify remain unchanged. Global formatting remains deferred until P4-G.

## P4-D1 parallel shared Login OTP Resend coordinator

P4-D1 adds `SharedLoginOtpResendService` as a framework-neutral parallel coordinator while production remains on `IdempotentLoginOtpResendService`. The coordinator uses the shared `login/otp-resend` identity, the opaque auth-flow scope, the versioned resend fingerprint, the configured resend TTL, the strict resend execution bridge, and conservative possible-side-effect failure classification. Acquired and completed-replay results restore the original public result, including exact `Retry-After` metadata. Legacy Login OTP Send persistence and Redis assets remain retained through P4-D2 and until P4-F authorizes deletion. Global formatting remains deferred until P4-G.

## P4-D2 Login OTP Resend production composition switch

P4-D2 selects `SharedLoginOtpResendService` as the sole production `ResendLoginOtpUseCase`. `LoginOtpResendService` remains the protected operation and is invoked only after shared acquisition. Production composition uses the shared executor, strict resend response bridge, opaque Login identity factory, versioned resend fingerprint factory, and configured resend TTL. Replay preserves the original result and exact `Retry-After` metadata. Legacy Login OTP Send persistence, Redis assets, the legacy resend coordinator, records, and rollback tests remain retained until P4-F. P4-C OTP Send composition and OTP Verify behavior remain unchanged. Global formatting remains deferred until P4-G.

## P4-E1 typed OTP Verify protected-operation outcome boundary

P4-E1 introduces an internal typed protected-operation boundary for Login OTP Verify while preserving the public `VerifyLoginOtpUseCase` result contract and leaving production composition unchanged. `LoginOtpVerifyService` returns the public result together with an optional opaque `ProtectedReplayReference` only to the future shared coordinator. An `ACCOUNTS_READY` outcome resolves the reference exclusively from the committed `LoginOtpConsumptionProof.handoffReference`; deterministic rejections carry no protected reference. No reference is derived from the auth-flow session, authenticated session, correlation ID, or fresh randomness. Existing proof-based and handoff-based committed-login recovery remain authoritative. P4-C Send and P4-D Resend production composition remain unchanged, all legacy Login persistence and rollback assets remain retained until P4-F, and global formatting remains deferred until P4-G.

### P4-E1 executor protected-reference propagation

The shared in-memory hydrated response now carries an optional opaque ProtectedReplayReference across acquired execution and response capture. The existing four-argument constructor remains source-compatible. Capture requires the transient response reference to match the terminal snapshot reference, while safe serialization continues to persist only the opaque reference outside the safe body. Login OTP Verify capture can therefore consume the committed handoff reference emitted by LoginOtpVerifyProtectedOperationResult without mutable coordinator state. Deterministic rejections carry no reference. Production VerifyLoginOtpUseCase composition remains unchanged, legacy recovery assets remain retained until P4-F, and formatting remains deferred until P4-G.

## P4-E2 parallel shared Login OTP Verify coordinator

P4-E2 adds `SharedLoginOtpVerifyService` as a framework-neutral parallel coordinator while production remains on `LoginOtpVerifyService`. It uses the shared `login/otp-verify` identity, the opaque auth-flow scope, constant versioned OTP Verify fingerprint, configured Login OTP Verify TTL supplied by composition, and conservative possible-side-effect classification. Protected execution occurs only after shared acquisition through `LoginOtpVerifyProtectedOperation`. Successful acquired execution attaches only the authoritative opaque handoff reference emitted by `LoginOtpVerifyProtectedOperationResult`; deterministic rejections carry no protected reference. Completed replay hydrates through committed-login recovery using the current correlation ID. Because the current public Verify status vocabulary has no shared lifecycle-specific statuses, duplicate, key-reuse, failed-existing, and unknown-existing outcomes fail closed as `SYSTEM_FAILED` without repeating protected execution. Production composition remains unchanged until the separate P4-E3 switch. Legacy Login persistence and rollback assets remain retained until P4-F, and global formatting remains deferred until P4-G.

## P4-E3 Login OTP Verify production composition switch

P4-E3 selects `SharedLoginOtpVerifyService` as the sole production `VerifyLoginOtpUseCase`. `LoginOtpVerifyService` remains the protected operation and is invoked only after shared lifecycle acquisition. Production composition uses the shared executor, Login OTP Verify bridge, opaque identity factory, constant versioned fingerprint, and `auth-workflows.login.idempotency.otp-verify-ttl-seconds`. Successful execution propagates only the authoritative committed handoff reference; completed replay restores committed Login state through the existing protected recovery path. Legacy OTP Verify lifecycle, persistence, replay, and committed-login recovery assets remain retained until P4-F. Global formatting remains deferred until P4-G.

## P4-F repository-only legacy lifecycle cleanup

The shared Login migration was not deployed to any environment before cleanup. Deployment timestamps, environment coverage, rollback-window closure, legacy TTL drainage, and Redis-key deletion are therefore not applicable. All four Login operations use the shared idempotency executor in production. Obsolete Login credential and OTP Send/Resend response-idempotency coordinators, ports, Redis writers, factories, and rollback-only tests are removed from the repository. Login Start protected replay remains through `LoginStartProtectedReplayCommitter`, its replay ports/protectors and shared bridge. OTP Verify committed-login recovery, handoff, consumption proof, authenticated replay session, protected-reference propagation, persistence, codec, bridge, and protected operation remain mandatory. Global formatting and the final cumulative commit remain deferred to P4-G.

### P4 full-suite stabilization: captured replay-reference invariant and strict fixtures

Registration shared response capture now places the same opaque protected replay reference in both the immediate hydrated response and persisted terminal snapshot, satisfying the shared captured-response invariant without persisting protected values. Registration OTP Verify and Redis idempotency tests now construct valid opaque keys and reservation tokens through their fixture helpers; production validation remains strict and unchanged. Global formatting remains deferred to P4-G.

### P4 full-suite stabilization: OTP Verify transient decoding and snapshot diagnostics

Registration OTP Verify transient decoding now uses registered Jackson modules while retaining strict unknown-property rejection and preserves the underlying decoding cause for diagnostics. Shared terminal snapshot rendering redacts the serialized safe body and exposes only safe header names plus the already-redacted protected reference. Unsupported schema remains rejected at the snapshot constructor boundary. Global formatting remains deferred to P4-G.

### P4 full-suite stabilization: explicit OTP Verify transient JSON composition

Registration OTP Verify no longer relies on direct Jackson serialization or hydration of the public result record. The shared execution bridge composes and validates an explicit transient JSON object and reconstructs every result field, including status, next step, trusts, attempts, messages, errors, idempotency outcome, and replay snapshot. Strict unknown transport behavior remains isolated from the terminal replay codec. Global formatting remains deferred to P4-G.

### P4 full-suite stabilization: OTP Verify replay bridge restoration

The explicit OTP Verify transient JSON migration now retains the shared executor callbacks for terminal response capture and hydration. Both callbacks delegate through the existing replay codec and the repaired transient conversion boundary. Global formatting remains deferred to P4-G.

### P4 full-suite stabilization: OTP Verify terminal-envelope decoding

OTP Verify result hydration now accepts both the immediate explicit transient JSON body and the persisted Registration shared terminal envelope. Persisted responses must carry the exact `otp-verify` action and an object payload before the explicit result fields are decoded. This keeps action separation strict while allowing acquired and completed-replay outcomes to share one result decoder. Global formatting remains deferred to P4-G.

### P4 full-suite stabilization: Start and OTP Resend terminal envelope decoding

Registration Start and OTP Resend transient decoders now accept their immediate transient result JSON and their action-scoped persisted terminal envelopes. Envelope hydration requires the exact `start` or `otp-resend` action and an object payload. Jackson modules are registered for result reconstruction while unknown properties remain rejected. Global formatting remains deferred to P4-G.

### P4 full-suite stabilization: Start immediate protected response preservation

Registration Start capture now returns the original in-memory hydrated response for the acquired request while persisting only the safe terminal snapshot produced by the replay codec. This preserves the newly issued CSRF token and auth-flow session identifier for the first response without placing either protected value in shared idempotency persistence. Completed replay continues through the protected replay reference. Global formatting remains deferred to P4-G.

### P4 full-suite stabilization: Start immediate replay-reference alignment

Registration Start capture preserves the protected immediate response body and attaches the same opaque protected replay reference carried by the safe terminal snapshot. The shared captured-response invariant therefore remains enforced, while the first response retains the CSRF token and the persisted snapshot remains free of protected values. Global formatting remains deferred to P4-G.

### P4 full-suite stabilization: Login OTP Verify committed handoff proof

Login OTP Verify keeps public idempotency-key construction at the reservation boundary and preserves the authoritative committed consumption proof in legacy fixture persistence. Successful first-request continuation can therefore reload the same opaque handoff reference required by the shared captured response, while production persistence remains the authority for committed replay. Global formatting remains deferred to P4-G.

### P4 full-suite stabilization: Registration Complete contract reconciliation

Registration Complete source-contract tests now describe the protected-operation input boundary, split prerequisite pipeline, password-bound shared request hash, and command-local completion lock acquisition. Behavioral tests reset their mutable session fixture between independent downstream scenarios so prior COMPLETING or cancelled state cannot contaminate later assertions. Production workflow behavior is unchanged. Global formatting remains deferred to P4-G.

### P4 full-suite stabilization: typed application result rule correction

The clean-architecture execute-method guard now accepts typed `Result` contracts located anywhere inside the application layer, including capability-owned result records under `application.usecase`. It continues to reject adapter, Spring web, and Jackson response types. This removes a package-location false positive without weakening the presentation-boundary prohibition. Global formatting remains deferred to P4-G.

### P4 full-suite stabilization: Login transient JSON boundary

Login shared-execution bridges and replay envelopes now depend only on the framework-neutral `TransientJsonPort`. Jackson remains in `adapter/out/json` and is supplied by Spring composition. Strict unknown-field rejection, action-tag validation, safe snapshot behavior, and protected replay handling remain unchanged. Global formatting remains deferred to P4-G.

### P4 full-suite stabilization: Login runtime JSON slice composition

The isolated Login Start, OTP Send, and OTP Resend runtime composition tests now include the shared Login idempotency configuration and its `ObjectMapper` prerequisite. This keeps the production `TransientJsonPort` adapter wiring under test while allowing narrow `ApplicationContextRunner` slices to construct the same dependency graph as the application. Global formatting remains deferred to P4-G.

### P4 repository stabilization closure

P4 stabilization is complete at the repository level. The cumulative Login shared-idempotency runtime composition is covered for Start, OTP Send, OTP Resend, OTP Verify, and the production controller boundary. The post-composition Maven full suite completed with 4,087 tests, zero failures, zero errors, and 10 skipped tests. No deployment or TTL-drainage claims are made because the shared Login migration has not been deployed. P4-G applies the deferred repository formatting and reruns compile, test compilation, focused shared-idempotency validation, architecture validation, and diff hygiene checks.

### P4-G startup closure: Login OTP Verify Redis key factory

The shared Login composition now exposes one `RedisLoginIdempotencyKeyFactory` bean using the configured `redis-cache.key-prefix`. This satisfies the retained OTP Verify protected persistence and authenticated-login handoff stores without restoring the removed legacy Login response-idempotency lifecycle. Local startup is validated with Redis TLS disabled while preserving Redis-backed ports.

### P4-G startup closure: Login OTP Verify persistence identity

The shared Login composition now exposes the retained HMAC-based `LoginIdempotencyPersistenceIdDeriver` from the existing runtime Base64 secret property. This identity remains limited to protected OTP Verify recovery evidence and authenticated-login handoff persistence. It does not restore the removed legacy Login response-idempotency lifecycle.

### P4-G startup closure: Login OTP Verify shared bridge composition

The shared Login configuration now composes the OTP Verify protected replay resolver, safe replay codec, and typed shared execution bridge. The resolver is created by Spring's constructor autowiring so its exact retained recovery dependencies remain authoritative and are not duplicated in configuration. This completes the production bean path required by `VerifyLoginOtpUseCase` without restoring the removed legacy Login response-idempotency lifecycle.

### P4-G validation closure: Login persistence secret in runtime slices

The isolated Login runtime composition tests now provide a deterministic Base64 test-only persistence HMAC secret when loading `LoginSharedIdempotencyConfig`. Production remains fail-closed when the required runtime secret is absent or invalid. This keeps narrow Start, OTP Send, OTP Resend, and controller slices aligned with the complete OTP Verify production composition.

### P4-G validation closure: Login recovery dependency in runtime slices

The narrow Login Start, OTP Send, OTP Resend, and controller composition tests now supply a mocked `RecoverCommittedLoginByHandoffUseCase` required by the production OTP Verify protected replay resolver. The mock is confined to the test slices; production continues to use the real committed-login recovery composition.
