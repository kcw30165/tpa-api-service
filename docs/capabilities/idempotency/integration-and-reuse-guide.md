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
