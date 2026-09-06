# Idempotency Architecture and Lifecycle

## Approved target and migration boundary

The target architecture is one session-context-neutral and workflow-neutral application capability behind one shared port and one shared Redis implementation. It owns action/key/fingerprint identity, atomic reservation, lifecycle transitions, schema-versioned terminal public-response snapshots, safe-header policy, replay, expiry, corruption handling, and common observability.

Reservation ownership is separate from request equivalence. Before attempting atomic reservation, the executor generates a 256-bit opaque token. Redis stores it with `PROCESSING`; only `ACQUIRED` exposes it, and every terminal transition must match it atomically. Duplicate callers never receive the token or full persistence record.

A deterministic terminal response, successful or unsuccessful, is stored as `COMPLETED`. Replay restores its original HTTP classification, success flag, public status, result, messages, errors, and allowlisted headers. `COMPLETED_REPLAY` is never a public business status.

The detailed feature-specific architecture below is retained as the legacy migration inventory. After migration and expiry of the compatibility window, stale feature-specific idempotency infrastructure must be deleted.

The shared model has no dependency on auth-flow sessions, authenticated sessions, feature DTOs, actor/account context, controllers, Spring, or Redis. Action integrations provide opaque scope identity and separately authorize or bind the current session context.

See [Shared Idempotency Consolidation Implementation Plan](shared-idempotency-consolidation-plan.md).


## 1. Architectural intent

Idempotency is implemented as an application capability behind ports and Redis adapters. Workflow services or coordinators reserve an action before side effects, then complete, fail, or mark the record uncertain.

```text
Web request with Idempotency-Key
        |
Application command and use case
        |
Action-specific idempotency coordinator
        |
Idempotency port
        |
Redis adapter + key factory + JSON mapper
```

The web layer checks that a required key is present. The application layer determines action scope and canonical request content. The Redis adapter owns atomic persistence and record serialization.

## 2. Core domain model

An idempotency record conceptually contains:

- Action
- Validated idempotency key
- Canonical request hash
- Status
- Creation/update timestamps
- Expiry/TTL
- Safe replay snapshot when completed
- Safe failure or unknown-outcome metadata where required

Typical stored statuses are:

```text
PROCESSING
COMPLETED
FAILED
UNKNOWN_OUTCOME
```

Ports expose flow-specific start/reserve, complete, fail, mark-unknown, load, and delete operations.

## 3. Action scope

The key is not globally unique by itself. The effective identity includes the action and configured capability namespace.

Examples represented in the codebase include concepts equivalent to:

- Registration start
- Registration OTP resend
- Registration OTP verify
- Registration complete
- Login credentials/start
- Login OTP send
- Login OTP resend
- Login OTP verify

A key reused for a different action is a separate operation only if the key factory and policy deliberately scope it that way. Never infer action from URL text inside the Redis adapter; pass an explicit action.

## 4. Key validation and Redis naming

Idempotency keys are opaque client-generated identifiers with strict length/character validation. The reviewed key factories accept a restricted safe form and reject blank or malformed values.

A conceptual Redis key is:

```text
{environment-prefix}:{capability}:{action}:{idempotency-key}
```

Rules:

- No username, identity value, destination, policy number, certificate number, account reference, OTP, token, or request body in the key
- Use a configured environment/application prefix
- Keep action names stable for the supported replay lifetime
- Prevent wildcard or delimiter injection through key validation
- Capability-scoped administrative operations only

## 5. Canonical request hashing

Request hash generators use SHA-256 over a canonical representation of the business inputs that define the logical operation.

The hash answers:

> Is this retry semantically the same request as the one that reserved this action/key?

### Include

- Stable fields that change the requested business effect
- Destination ID or irreversible destination identity where relevant
- Scenario/action-specific values
- Normalized inputs where normalization is part of the contract

### Exclude or handle deliberately

- Request/correlation ID
- Timestamps generated per attempt
- Transport ordering or JSON whitespace
- Raw session and CSRF secrets
- Passwords or OTPs in stored/replay material
- Values that legitimately vary while the business request remains identical

Some credential/verification operations necessarily use sensitive submitted values when deriving a request hash. The implementation must use one-way hashing/canonicalization and must never persist or log the clear input.

### Canonicalization rule

The same logical request must hash identically across retries, while a materially different request must not. Field order, trimming, case normalization, null versus empty, and collection ordering must be explicitly specified and tested.

## 6. Reservation lifecycle

```text
No record
  -> atomic set-if-absent PROCESSING
  -> outcome STARTED

Existing PROCESSING + same hash
  -> DUPLICATE_IN_PROGRESS

Existing COMPLETED + same hash
  -> COMPLETED_REPLAY

Existing FAILED + same hash
  -> FAILED_EXISTING

Existing UNKNOWN_OUTCOME + same hash
  -> UNKNOWN_OUTCOME_EXISTING

Any existing record + different hash
  -> KEY_REUSED_WITH_DIFFERENT_REQUEST
```

The initial reservation must be atomic. A read-then-write implementation permits duplicate execution under concurrency.

## 7. Completion and replay snapshots

On successful/replay-safe completion, the coordinator stores a snapshot sufficient to reconstruct the approved application result without calling external systems again.

Snapshot concepts in the codebase include:

- Outcome/status code
- Commit or replay reference
- Serialized safe body/result
- Masked destination
- Expiry and resend-cooldown values
- Approved safe headers

### Snapshot safety

Do not persist:

- `Set-Cookie`
- `Authorization`
- CSRF/XSRF values
- Session identifiers
- Raw destinations
- Passwords or OTPs
- Provider credentials
- Arbitrary inbound/outbound headers

Snapshot constructors and serializers should validate header allowlists/denylists and payload shape.

### Replay behavior

A replay returns the same business meaning and safe public data as the original completion. It does not:

- Regenerate an OTP
- Resend a message
- Reinvoke APIM/TAS
- Create another session
- Reapply a mutation

## 8. Failure versus unknown outcome

### Known failure

Use `FAILED` only when the implementation knows the protected side effect did not complete or when the action's policy deliberately records a terminal known failure.

### Unknown outcome

Use `UNKNOWN_OUTCOME` when the process cannot prove whether a non-idempotent or externally visible side effect occurred—for example, a timeout after message/APIM request transmission.

Unknown outcomes require deliberate reconciliation, expiry, or user recovery. Automatically converting unknown to “not executed” risks duplicate side effects.

### Crash boundaries

The design must account for crashes:

```text
After reservation, before side effect
After side effect, before completion record
After completion record, before HTTP response
During replay-snapshot serialization
```

The third case is the normal reason completed replay exists. The second case is the dangerous unknown-outcome boundary.

## 9. TTL lifecycle

Every idempotency record has a positive TTL. The TTL must:

- Cover the maximum client retry interval
- Cover operation timeout and bounded internal retry
- Cover expected delayed duplicate submissions
- Not retain sensitive metadata indefinitely

A record expiring permits a future key reuse because the server no longer has evidence of the prior operation. Therefore, TTL is part of the external reliability contract and must not be changed casually.

Do not refresh TTL indefinitely on every duplicate request unless that is an explicit policy.

## 10. Login patterns

### Credential/start idempotency

The Login credential coordinator hashes the logical credential request, reserves processing, creates/replays protected start state, and distinguishes duplicate, completed, failed, and unknown outcomes.

### OTP send/resend

OTP delivery idempotency prevents repeated messages for the same logical send. Completed replay returns masked destination/timing information rather than sending again. Delivery uncertainty must be represented as unknown where duplicate delivery is possible.

### OTP verification

Verification idempotency prevents repeated success side effects such as duplicate authenticated-session establishment or repeated account-list processing.

## 11. Registration patterns

Registration uses action-specific records for start, OTP resend, OTP verify, and completion. The snapshot model can preserve a safe workflow result and approved headers while excluding cookie/token security material.

Registration completion is especially race-sensitive because it can call TAS/APIM and mutate registration state; it combines idempotency with a session completion lock. These mechanisms complement each other:

```text
Idempotency
  identifies repeated logical request

Completion lock
  prevents concurrent critical-section execution for the session
```

## 12. HTTP and workflow mapping

Typical public behavior includes:

- Missing idempotency key → validation error
- Duplicate in progress → conflict/waiting response
- Key reused with different request → conflict/validation failure
- Completed replay → normal success with replayed result
- Failed existing → scenario-specific failure
- Unknown outcome → safe system/unavailable response and recovery guidance

The codebase maps idempotency conflicts into structured workflow errors and HTTP semantics such as `409 Conflict`. Exact status and `nextStep` are scenario-specific.

## 13. Security and logging invariants

- Keys and hashes are not authentication credentials.
- Clear request inputs are never logged by hash generators/coordinators.
- Redis keys contain no PII or secrets.
- Replay snapshots exclude security headers and sensitive values.
- Request IDs and action/status categories are safe correlation fields.
- Hash mismatch logs do not include either clear request.
- Redis exceptions are sanitized.
- Administrative deletion is capability-scoped and audited.

## 14. Extension boundaries

### Reuse directly

- Key validation/value objects
- SHA-256 hashing utilities/patterns
- Redis reservation/status model
- Replay-snapshot safety rules
- Coordinator outcome semantics
- Safe exception and logging patterns

### Configure or extend

- Action enum
- TTL
- Canonical hash fields
- Replay snapshot type
- Failure/unknown policy
- Public error and navigation mapping

### Implement per operation

- What constitutes the logical request
- First irreversible side effect
- Replay-safe result
- Reconciliation behavior
- Whether failed records are retryable
- Whether downstream mutation is truly idempotent

The shared Redis namespace is `{prefix}:idempotency:v1:{hmac-sha256(secret, locator)}`. The dedicated runtime HMAC secret is separate from Login configuration, Base64 encoded, at least 32 decoded bytes, never logged, and never persisted. The persisted record retains the validated logical identity for collision defense. Initial reservation uses atomic `SET NX` with TTL; later transitions use whole-value compare-and-set while preserving the remaining TTL.

The lifecycle adapter permits only PROCESSING to COMPLETED, FAILED, or UNKNOWN_OUTCOME. It validates identity, fingerprint, ownership token, expiry, and source status before whole-value atomic compare-and-set.

Protected-operation failures are classified as PROVEN_NO_SIDE_EFFECT or POSSIBLE_SIDE_EFFECT. Null, thrown, or uncertain classification fails closed to UNKNOWN_OUTCOME. Completion or response-capture failure after a returned operation response also becomes UNKNOWN_OUTCOME.

Spring composition remains outside the application package. It validates the dedicated shared-idempotency secret during bean construction and wires the reactive Redis operations, HMAC key factory, lifecycle adapter, secure reservation-token generator, and executor.

Runtime composition is guarded by `shared-idempotency.enabled`. Disabled mode is behavior-neutral and creates no shared Redis port or executor. Enabled mode validates the dedicated HMAC secret before composing the shared capability.

<!-- P3-REGISTRATION-MIGRATION-BASELINE -->
## P3 Registration target architecture

Registration action code will depend on `IdempotencyExecutor`, not `RegistrationIdempotencyPort`. Registration-specific bridge components may translate action results to and from the shared context-neutral contracts, but the shared application package must not import Registration DTOs, session types, Spring, Redis, APIM, or web response classes.

### Canonical operation identity

All Registration operations use capability `registration` and one stable action value:

- `start`
- `otp-resend`
- `otp-verify`
- `complete`

The operation scope is an opaque, non-secret correlation boundary appropriate to the action. It must prevent cross-session collision without placing raw session IDs, identity values, trust codes, usernames, OTP values, passwords, CSRF values, or contact values in Redis keys or logs. The shared HMAC persistence locator remains the only Redis key derivation mechanism.

### Execution and replay rule

Only `ACQUIRED` may invoke the protected action. `COMPLETED_REPLAY` must hydrate the stored Registration action result without invoking APIM, OTP generation or verification, message delivery, session mutation, eligible-trust persistence, completion locking, or cancellation again. Duplicate, reused-key, failed-existing, and unknown-existing outcomes must not execute the protected action.

`COMPLETED_REPLAY` is lifecycle metadata, not a public business result. The bridge must reconstruct the original Registration terminal result before the existing web mapper determines the public response.

### Failure classification

A Registration failure classifier may classify `PROVEN_NO_SIDE_EFFECT` only when dispatch and externally visible acceptance are disproved. Possible APIM acceptance, OTP delivery, OTP verification state change, session mutation, eligible-trust persistence, or uncertain completion persistence must classify as `POSSIBLE_SIDE_EFFECT`. Classifier failure or ambiguity fails closed to `UNKNOWN_OUTCOME`.

### Protected replay material

Safe snapshots contain only deterministic public response material and allow-listed headers. Cookies, session identifiers, CSRF values, OTP values, passwords, authorization material, protected auth-flow context, and other secrets are never serialized into the shared Redis record. If replay requires protected continuity, the terminal snapshot carries only an opaque `ProtectedReplayReference`, and the resolver loads the protected state from its owning store.

P3-A1 derives an opaque 64-character operation scope before entering the shared identity and uses a length-delimited, version-1 SHA-256 request-equivalence fingerprint. The Redis locator remains independently protected by the shared runtime HMAC.

P3-A2 uses a strict action-tagged schema-version-1 public-body envelope. Hydration verifies the expected Registration action before reconstructing the shared hydrated response and performs no protected operation or feature side effect.

The P3-A2 action envelope is validated by strict JSON deserialization rather than text search. The decoded top-level action must exactly equal the expected action, and the payload node must be present.

P3-B1 separates Start replay into a safe public envelope and protected continuity. STARTED snapshots carry only an opaque ProtectedReplayReference; CSRF and auth-flow session values are loaded through RegistrationStartProtectedReplayPort and fail closed when unavailable.

P3-B1A uses AES-GCM with associated data bound to registration, start, schema version, key version, and the opaque reference. Reference substitution therefore fails authentication. Protected persistence uses SET NX and the exact Start idempotency TTL.

IdempotentResponseCapture returns Mono<IdempotentCapturedResponse>. The executor awaits capture persistence before completing the shared record. Failed or empty capture after the protected operation becomes UNKNOWN_OUTCOME and never falls through to re-execution.

P3-B2B separates the Start business workflow from lifecycle persistence. The protected operation returns deterministic public results normally and propagates transport or infrastructure exceptions for the outer shared coordinator to classify.

P3-B2C1 explicitly separates the transient hydrated execution response from the persisted terminal snapshot. The transient representation is never stored; RegistrationStartReplayCodec removes CSRF and session values before shared completion.

Production Start now reserves through IdempotencyExecutor, executes RegistrationStartProtectedOperation only for ACQUIRED, hydrates completed replay through the protected bridge, and maps all non-executable lifecycle outcomes explicitly.

Registration Start migration is complete: production, unit, captcha, and HTTP integration paths use the shared coordinator or persistence-free protected operation. Completed replay hydrates the original authorized cookie state without repeating business side effects.

After the Start coordinator split, source-text architecture guards follow responsibility ownership: RegistrationStartProtectedOperation owns captcha, lookup, session context, and business workflow; SharedStartRegistrationService owns shared lifecycle orchestration.

The OTP Resend protected boundary begins after authorized auth-flow and CSRF context loading plus shared acquisition. It owns OTP resend policy acceptance, challenge replacement, session mutation, and message delivery.

OTP Resend replay persists the original terminal status, nextStep, masked destination, timing, retry-after, messages, and complete error descriptors under the strict otp-resend action envelope. Hydration never invokes the protected operation.

The shared OTP Resend coordinator derives both opaque scope and version-1 request equivalence from the authorized auth-flow session. CSRF, origin, referer, language, request ID, destination, OTP, and challenge values are excluded.

OTP Resend production composition now uses the shared IdempotencyExecutor, protected operation, typed replay codec, and shared execution bridge. The Resend bean no longer depends on RegistrationIdempotencyPort, RegistrationRequestHashGenerator, or Clock.

OTP Resend harnesses now exercise protected execution, safe response capture, persisted replay hydration, opaque scope, and session-only fingerprint semantics through IdempotencyExecutor.

OTP Resend is now fully composed through SharedResendRegistrationOtpService. RegistrationOtpResendProtectedOperation owns resend policy mapping and delivery ordering, while IdempotencyExecutor owns lifecycle persistence and replay.

OTP Verify now has a persistence-free protected operation. Deterministic OTP invalid, expired, locked, and no-eligible-trust outcomes are returned normally so the future shared coordinator can capture them as completed terminal responses.

OTP Verify typed replay preserves original status, nextStep, remaining attempts, ordered trust list, messages, and errors under the otp-verify action envelope. COMPLETED_REPLAY remains lifecycle metadata and is never captured as a public result.

OTP Verify shared coordination now explicitly routes every reservation outcome. Acquired and replay responses preserve the original public result; deterministic OTP and trust outcomes are captured as completed terminal responses.

OTP Verify production routing now enters the shared coordinator. Validation and protected-context authorization remain before reservation; acquired execution remains isolated in RegistrationOtpVerifyProtectedOperation.

OTP Verify tests now model shared lifecycle behavior: deterministic unsuccessful results are completed, replay restores the original public result, and replay performs no OTP, session, lookup, or eligible-trust persistence work.

OTP Verify orchestration is fully shared. RegistrationOtpVerifyProtectedOperation owns OTP verification, session verification mutation, trust lookup, and eligible-trust persistence; the shared coordinator owns reservation and replay routing.

The Registration Complete split begins with an authorized input containing the inbound command, protected Registration context, protected username-check state, and refreshed session. It validates session identity consistency and persists none of those values.

RegistrationCompleteProtectedOperation executes only after future shared ownership is acquired. Its reactive lock scope encloses the authoritative session reload, transition to COMPLETING, downstream registration mutation, continuation handling, and cleanup.

Direct P3-E2C coverage proves that the extracted acquired workflow releases its lock on success and failure, preserves ordered remaining trusts, and propagates pre-dispatch and ambiguous exceptions for later shared classification.

Registration Complete replay uses the shared action-tagged envelope with action complete. COMPLETED_REPLAY and UNKNOWN_OUTCOME remain lifecycle-only states and are never captured as public terminal results.

The Complete shared coordinator classifies RegistrationCompletionPreDispatchException as PROVEN_NO_SIDE_EFFECT and every other unexpected protected-operation failure as POSSIBLE_SIDE_EFFECT. Replays return the original typed result.

After P3-E5A, production Registration Start, OTP Resend, OTP Verify, and Registration Complete all compose through the shared executor. Complete composes its protected operation, strict replay codec, and typed execution bridge in RegistrationCompleteUseCaseConfig.

P3-E5B assigns prerequisite ordering and lifecycle classification to shared-coordinator guards, while lock, dispatch, selected-trust, continuation, cancellation, and downstream behavior remain direct protected-operation tests.

After P3-E6, Registration has no action-specific idempotency port, record/status model, request hash generator, or legacy Redis lifecycle adapter. Complete classification, acquired workflow, and replay remain separated across the shared coordinator, protected operation, and codec.

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
