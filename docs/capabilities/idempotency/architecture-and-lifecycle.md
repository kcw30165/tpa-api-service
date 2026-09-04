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
