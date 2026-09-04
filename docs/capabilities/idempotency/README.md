# Idempotency Capability

> **Migration status:** P0 target architecture is under documentation review. Current feature-scoped implementations remain legacy migration inputs until each action family moves to the shared capability.

## Target consolidation

The approved target is one session-context-neutral and workflow-neutral idempotency lifecycle, terminal-response snapshot contract, and Redis persistence implementation. Reservation ownership is enforced with an opaque per-attempt token that is returned only to the executor that acquires `PROCESSING`. Current auth workflows are initial migration consumers, not the capability boundary. `COMPLETED_REPLAY` is internal lifecycle metadata and must never replace the original public outcome.

Read [Shared Idempotency Consolidation Implementation Plan](shared-idempotency-consolidation-plan.md) before implementation. The final migration gate requires deletion of stale feature-specific ports, records, adapters, coordinators, serializers, key factories, lifecycle enums, configuration, and obsolete tests.


> **Status:** Documents the idempotency implementation represented by the reviewed codebase snapshot.  
> **Audience:** New team members, backend developers, reviewers, testers, support engineers, and solution designers.  
> **Scope:** Request deduplication, request hashing, Redis reservation records, completed-result replay, failure/unknown outcomes, TTL policy, and reuse by Login, Registration, and OTP workflows.

## Start here

Idempotency ensures that retrying the **same logical mutation** does not execute it twice. The client supplies an `Idempotency-Key`; the server combines the action, key, and a canonical request hash to coordinate processing in Redis.

```text
Mutation request
  + action
  + Idempotency-Key
  + canonical request hash
        |
        v
Idempotency coordinator / port
        |
        v
Redis record
  PROCESSING -> COMPLETED + replay snapshot
             -> FAILED
             -> UNKNOWN_OUTCOME
```

Read the documents in this order:

1. **[Architecture and lifecycle](architecture-and-lifecycle.md)** — understand actions, keys, request hashes, Redis status, replay snapshots, TTLs, and failure semantics.
2. **[Integration and reuse guide](integration-and-reuse-guide.md)** — follow the implementation recipe when a new mutation needs idempotency.
3. **[Operations and testing](operations-and-testing.md)** — configure, test, troubleshoot, monitor, and release idempotent operations safely.

## What idempotency does

- Prevents simultaneous duplicate execution
- Replays an already-completed result without repeating side effects
- Rejects reuse of the same key for a different logical request
- Preserves explicit failed or uncertain outcomes
- Bounds records with an operation-specific TTL

## What idempotency does not do

Idempotency is not:

- Authentication
- Authorization
- CSRF protection
- Rate limiting
- A distributed transaction across all downstream systems
- Proof that an unknown provider/downstream operation did or did not execute
- Permission to retry every mutation automatically

These controls are often used together but solve different risks.

## Current consumers

The reviewed implementation applies idempotency patterns across operations including:

- Login credential/start processing
- Login OTP send
- Login OTP resend
- Login OTP verification
- Registration start
- Registration OTP resend
- Registration OTP verification
- Registration completion

Action names and storage ports differ by flow, but they share the same core concepts: validated key, action scope, canonical SHA-256 request hash, atomic reservation, explicit status, bounded TTL, and safe completed-result replay.

## Core outcomes

```text
STARTED
  This caller reserved the operation and may execute it.

DUPLICATE_IN_PROGRESS
  The same logical request is already processing.

COMPLETED_REPLAY
  Return the prior safe result; do not repeat side effects.

KEY_REUSED_WITH_DIFFERENT_REQUEST
  Same action/key but a different request hash; reject it.

FAILED_EXISTING
  A prior attempt reached a known failed state.

UNKNOWN_OUTCOME_EXISTING
  A prior attempt may have produced a side effect; do not retry blindly.
```

Exact enum names vary by flow, but the semantics must remain consistent.

## Reuse rules

1. Scope every idempotency record by action.
2. Validate keys at the trust boundary.
3. Generate the request hash from canonical business inputs.
4. Never include raw credentials, OTPs, CSRF tokens, session IDs, or PII in Redis keys.
5. Reserve before the first side effect.
6. Complete only after the operation reaches a replay-safe result.
7. Store only a safe replay snapshot—not arbitrary response headers or sensitive payloads.
8. Mark uncertain side effects as unknown outcome rather than failed.
9. Do not delete an unknown record merely to make retry possible.
10. Retry/replay must preserve the original business semantics and correlation evidence.
11. TTL must cover the retry/replay window without becoming indefinite retention.
12. Test concurrency and crash boundaries, not only sequential duplicate calls.

## Current configuration concepts

The reviewed codebase uses strict key validation, SHA-256 request hash generators, Redis-backed records, action-specific key factories, and operation-specific TTLs. Several observed idempotency TTLs are 300 seconds, but these are deployable policy values rather than universal constants. citeturn8search34turn8search35

P2 uses a versioned HMAC-SHA-256 operation locator for the Redis key, so capability, action, opaque scope, and client key are not visible in Redis key scans or recoverable by unkeyed offline guessing. The dedicated Base64 runtime secret must decode to at least 32 bytes.

P2-B2A adds the shared Redis lifecycle adapter. Reservation is SET-NX, duplicate classification is read-only, and terminal transitions require the original ownership token.

P2-B2B adds the shared executor and an action-supplied failure classifier. Only ACQUIRED executes; COMPLETED_REPLAY hydrates without execution.

P2-B2C completes runtime composition for the shared Redis port and executor. Deployment must provide SHARED_IDEMPOTENCY_PERSISTENCE_HMAC_SECRET as a dedicated Base64 value decoding to at least 32 bytes.

The shared runtime composition is disabled by default through `SHARED_IDEMPOTENCY_ENABLED=false`. Before endpoint migration it creates no shared beans and requires no HMAC secret. Enabling it requires both `SHARED_IDEMPOTENCY_ENABLED=true` and a valid `SHARED_IDEMPOTENCY_PERSISTENCE_HMAC_SECRET`.

<!-- P3-REGISTRATION-MIGRATION-BASELINE -->
## P3 Registration migration baseline

P3 starts from commit `cf2aab8b5561f790691d554aec4493f86aebbaa9`, which contains the committed shared Redis lifecycle adapter and executor foundation. The working tree must remain clean before the first P3 implementation writer is applied.

P3 migrates the complete Registration idempotency family:

- `REGISTRATION_START`
- `OTP_RESEND`
- `OTP_VERIFY`
- `REGISTRATION_COMPLETE`

The migration is action-family complete. It must not fix only selected replay error codes or move only successful responses. Every deterministic terminal response must preserve equivalent first-call and replay HTTP status, public status, payload, errors, messages, safe headers, next step, and retry metadata.

The approved sequence is P3-A shared Registration bridge, P3-B Start, P3-C OTP Resend, P3-D OTP Verify, P3-E Complete, P3-F compatibility and legacy removal, and P3-G stabilization. Each stage updates these capability documents before the next stage starts.

The shared runtime remains disabled by default until the first migrated Registration action is deployable with both `SHARED_IDEMPOTENCY_ENABLED=true` and a valid dedicated `SHARED_IDEMPOTENCY_PERSISTENCE_HMAC_SECRET`.

P3-A1 adds the Registration-owned shared identity and fingerprint bridge for all four action names. No Registration use case has switched executor yet, and the legacy port remains the active runtime path.

P3-A2 adds the Registration-owned terminal response envelope, capture, and replay hydration bridge. It supports all four actions and still leaves every Registration use case on the legacy runtime path.

P3-A2 strict-envelope repair replaces substring action detection with typed JSON decoding. Malformed envelopes, nested marker text, unknown actions, missing payloads, and unknown top-level fields now fail closed.

P3-B1 adds the typed Registration Start safe-result codec and a separately protected replay-state port. The safe snapshot excludes CSRF and auth-flow session identifiers, and hydration restores the original Start result status rather than exposing COMPLETED_REPLAY publicly.

P3-B1A adds a dedicated AES-GCM protected Registration Start replay store. A 256-bit opaque reference points to separately encrypted CSRF and auth-flow session continuity; the shared snapshot retains only the reference.

P3-B2A corrects the shared response-capture boundary to be reactive. Registration Start can now persist encrypted protected replay state before the shared terminal snapshot is completed.

P3-B2B adds a RegistrationStartProtectedOperation that owns validation, captcha, lookup, session, CSRF, OTP, challenge, and delivery behavior without any legacy or shared idempotency persistence dependency. Production routing remains unchanged until P3-B2C.

P3-B2C1 adds the Start shared execution bridge. Protected values exist only in the in-memory transient executor response, while reactive capture converts that result into a safe snapshot plus opaque protected replay reference.

P3-B2C2A switches production Registration Start composition to SharedStartRegistrationService when shared-idempotency.enabled=true. The production bean no longer injects RegistrationIdempotencyPort or RegistrationRequestHashGenerator.

P3-B2C2B3 migrates the Registration Start HTTP integration harness to the shared executor and removes the legacy StartRegistrationService. Registration Start now has no production or test dependency on RegistrationIdempotencyPort.

P3-B2C2B4 remaps all five source-text guards from the removed StartRegistrationService to RegistrationStartProtectedOperation, preserving active coverage of captcha, lookup-role, protected-context, policy, and sensitive-value boundaries.

P3-C1 begins OTP Resend migration by extracting RegistrationOtpResendProtectedOperation. Production routing remains on the legacy ResendRegistrationOtpService until shared capture and coordinator stages are complete.

P3-C2 adds the strict typed OTP Resend replay codec and transient execution bridge. Safe snapshots preserve original public result fields and require no protected replay store.

P3-C3 adds SharedResendRegistrationOtpService without changing production routing. It authorizes auth-flow and CSRF context before shared reservation and executes the protected Resend operation only for ACQUIRED.

P3-C4A switches the active OTP Resend Spring bean to SharedResendRegistrationOtpService. The legacy source remains temporarily for test-harness migration and will be removed only after those harnesses and source guards are aligned.

P3-C4B migrates the OTP Resend unit and integration harnesses to SharedResendRegistrationOtpService and shared executor semantics. The legacy coordinator remains only for source-guard remapping in P3-C4C.

P3-C4C remaps the remaining OTP Resend source guards to the shared protected operation and removes ResendRegistrationOtpService. OTP Resend no longer has a legacy production or test coordinator.

P3-D1A adds RegistrationOtpVerifyProtectedOperation without changing production routing. It isolates OTP verification, session verification mutation, eligible-trust lookup, eligible-trust persistence, and deterministic result mapping from idempotency persistence.

P3-D2 adds RegistrationOtpVerifyReplayCodec. OTP Verify replay is fully reconstructable from the safe shared snapshot because the complete public trust shape is limited to accountEnv and trustCode; no protected replay store is required.

P3-D3 adds SharedVerifyRegistrationOtpService and RegistrationOtpVerifySharedExecutionBridge without changing production routing. The coordinator validates and authorizes before shared reservation, fingerprints session scope plus a one-way OTP digest, and uses conservative possible-side-effect classification.

P3-D4A switches the active OTP Verify production bean to SharedVerifyRegistrationOtpService. Production composition now uses the shared IdempotencyExecutor, protected operation, typed replay codec and bridge, and shared identity and fingerprint factories.

P3-D4B migrates the OTP Verify unit and lifecycle integration harnesses to SharedVerifyRegistrationOtpService. The harnesses exercise the protected-operation, capture, and replay callbacks instead of legacy Registration idempotency doubles.

P3-D4C removes VerifyRegistrationOtpService after production composition and both primary harnesses moved to shared execution. OTP Verify now has no legacy coordinator source.

P3-E2A introduces RegistrationCompleteProtectedOperationInput as the transient, redacted handoff from the future shared coordinator to acquired Registration Complete execution. Production remains on CompleteRegistrationService.

P3-E2B introduces RegistrationCompleteProtectedOperation as an idempotency-neutral parallel extraction. It owns completion locking, authoritative reload, downstream completion, continuation lookup, session transitions, cancellation, and canonical rejection mapping; production still uses CompleteRegistrationService.

P3-E2C adds direct behavioral coverage for RegistrationCompleteProtectedOperation, including final and continuing registrations, lock conflicts, authoritative reload guards, selected-trust routing, canonical rejections, cancellation, continuation failure, and propagated failures.

P3-E3 adds RegistrationCompleteReplayCodec. The strict safe snapshot preserves the original public status, next step, registered and cookie-clearing flags, ordered accountEnv/trustCode pairs, and complete public error descriptors without protected replay storage.

P3-E4 adds SharedCompleteRegistrationService and its typed execution bridge in parallel. All protected prerequisites still occur before shared reservation, and production remains on CompleteRegistrationService until P3-E5.

P3-E5A switches the Registration Complete production bean to SharedCompleteRegistrationService. All four Registration actions now use the shared idempotency executor in production, while the legacy CompleteRegistrationService remains temporarily for harness migration and P3-E6 removal.

P3-E5B migrates Registration Complete harness ownership from CompleteRegistrationService to SharedCompleteRegistrationService and RegistrationCompleteProtectedOperation. Duplicate legacy success and resilience harnesses are removed after TC-061, TC-062, TC-065, and TC-068 traceability is retained on direct protected-operation tests.

P3-E6 removes CompleteRegistrationService and the obsolete Registration-specific idempotency lifecycle stack. All four Registration actions now rely exclusively on the shared executor and shared persistence architecture.

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
