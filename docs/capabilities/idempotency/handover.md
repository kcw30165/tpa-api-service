# Shared Idempotency Handover

## Current approved baseline

- Branch: `fix/relay-same-error-code-for-the-idempotent-request`
- Git HEAD: `cf2aab8b5561f790691d554aec4493f86aebbaa9`
- Commit: `refactor(idempotency): add shared redis lifecycle adapter`
- P2 status: committed and approved
- P3 audit: passed on the exact committed baseline

## Current decision

P3 migrates all Registration idempotent actions to the shared capability in this order:

1. P3-A Registration shared bridge
2. P3-B Registration Start
3. P3-C OTP Resend
4. P3-D OTP Verify
5. P3-E Registration Complete
6. P3-F bounded legacy compatibility and removal
7. P3-G stabilization and final evidence

P3-A1 and P3-A2 now provide Registration-owned identity, fingerprint, explicit classifier, terminal capture, and replay hydration bridges. P3-A2 action validation uses strict typed JSON decoding and rejects malformed or ambiguous envelopes. P3-B1 now provides the typed Start safe-result codec and separately protected replay-state boundary. No Registration use case has switched to the shared executor. P3-B1A now provides conditionally composed AES-GCM protected replay persistence with opaque SET-NX references and Start-aligned TTL. P3-B1A2 adds the protected replay security contract test suite; it is test-compiled now and will be executed during the feature validation stage. P3-B2A makes shared response capture reactive so encrypted protected replay persistence completes before terminal snapshot persistence. P3-B2B adds the persistence-free RegistrationStartProtectedOperation; production Start routing remains legacy until P3-B2C. P3-B2C1 now provides the typed transient Start execution bridge and safe reactive capture boundary. P3-B2C2A switches production Start composition to SharedStartRegistrationService; the legacy StartRegistrationService remains temporarily for test-harness conversion only. P3-B2C2B1 migrates the primary Start use-case unit harness to the shared coordinator. P3-B2C2B2 migrates the captcha harness to RegistrationStartProtectedOperation. P3-B2C2B3 migrates the HTTP integration harness and removes the legacy StartRegistrationService. Registration Start is fully on the shared idempotency capability; OTP Resend, OTP Verify, and Complete remain legacy for subsequent rounds.

## Non-negotiable rules

- Only `ACQUIRED` executes a protected action.
- Replay hydrates the original terminal result and never re-runs side effects.
- Every deterministic terminal response is captured, including business and validation failures.
- `COMPLETED_REPLAY` is internal lifecycle metadata, not a public success classification.
- Failure classification is conservative. Ambiguity becomes `UNKNOWN_OUTCOME`.
- Ownership tokens and protected response material are never exposed to callers, logs, or public DTOs.
- Shared runtime composition remains disabled until deployment supplies the enable flag and dedicated HMAC secret.
- No legacy Registration idempotency type is removed before all four actions migrate and the compatibility window closes.
- Every stage updates the relevant files in `docs/capabilities/idempotency`.

## Runtime activation

```text
SHARED_IDEMPOTENCY_ENABLED=true
SHARED_IDEMPOTENCY_PERSISTENCE_HMAC_SECRET=<Base64 value decoding to at least 32 bytes>
```

Before activation, the default remains disabled and no shared HMAC secret is required.

## Planned P3 commit

```text
refactor(registration): migrate idempotency to shared capability
```

P3-B2C2B4 remaps all five stale source-text guards to the existing RegistrationStartProtectedOperation, preventing silent coverage loss after removal of StartRegistrationService. P3-B2 Start migration is now ready for cumulative stabilization evidence.

P3-C1 extracts RegistrationOtpResendProtectedOperation while production Resend remains on RegistrationIdempotencyPort. P3-C2 must add typed safe capture and replay before the production coordinator switch.

P3-C2 adds RegistrationOtpResendReplayCodec and RegistrationOtpResendSharedExecutionBridge. The safe snapshot needs no protected replay store and preserves the original terminal status, public retry metadata, messages, and complete errors. P3-C3 may add the shared coordinator.

P3-C3 adds SharedResendRegistrationOtpService. It loads and authorizes protected context before shared acquisition, uses session-only scope and fingerprint semantics, and applies possible-side-effect classification. Production remains legacy pending P3-C4 configuration and harness migration.

P3-C4A switches production OTP Resend composition to SharedResendRegistrationOtpService and removes legacy idempotency, hash-generator, and Clock dependencies from the Resend bean. The old service is retained only until P3-C4B harness migration and P3-C4C guard remapping/removal.

P3-C4B migrates both OTP Resend harnesses to the shared coordinator. Replay preserves the original result and has zero operational side effects; known delivery and rate-limit responses are completed and replayable. P3-C4C may remap guards and remove the legacy service.

P3-C4C removes ResendRegistrationOtpService after remapping RegistrationOtpRateLimitAndDeliveryOwnershipTest and RegistrationOtpResendCooldownGuardTest to live shared sources. OTP Resend is fully shared; Verify and Complete remain legacy. Run cumulative P3-C stabilization next.

P3-D1A adds the persistence-free RegistrationOtpVerifyProtectedOperation. Production Verify remains legacy. The operation returns deterministic OTP and trust outcomes normally, persists eligible accounts before VERIFIED, and leaves shared lifecycle, fingerprint, replay, and failure classification to later P3-D stages.

P3-D2 adds strict safe OTP Verify replay. The complete public trust shape is accountEnv plus trustCode, so no protected replay store is needed. Replay restores the original public result and keeps COMPLETED_REPLAY only as internal idempotency outcome metadata. Production Verify remains legacy.

P3-D3 adds the shared OTP Verify coordinator and execution bridge. Production Verify remains legacy. Validation and protected-context authorization precede shared reservation; fingerprints use session scope plus an OTP digest; unexpected acquired failures use possible-side-effect classification.

P3-D4A switches the active OTP Verify bean to SharedVerifyRegistrationOtpService with IdempotencyExecutor, RegistrationOtpVerifyProtectedOperation, RegistrationOtpVerifyReplayCodec, RegistrationOtpVerifySharedExecutionBridge, and shared identity/fingerprint factories. VerifyRegistrationOtpService and its two legacy harnesses remain temporarily for P3-D4B migration.

P3-D4B migrates VerifyRegistrationOtpUseCaseTest and RegistrationOtpVerificationLifecycleIntegrationTest to SharedVerifyRegistrationOtpService. Replay now returns the original public status and ordered trust payload with internal COMPLETED_REPLAY metadata and zero repeated effects. The legacy VerifyRegistrationOtpService source remains only until P3-D4C guard remapping and removal.

P3-D4C completes OTP Verify migration. VerifyRegistrationOtpService has been removed, RegistrationLookupExecutorBoundaryTest now targets RegistrationOtpVerifyProtectedOperation, package-wide architecture guards remain active, and Registration Complete remains on the legacy RegistrationIdempotencyPort boundary.

P3-E2A adds RegistrationCompleteProtectedOperationInput under the Registration Complete idempotency package. The input carries only already-authorized transient execution values, enforces command/session identity consistency, and fully redacts nested protected values. CompleteRegistrationService remains the active legacy production coordinator for P3-E2B.

P3-E2B introduces RegistrationCompleteProtectedOperation as a parallel, idempotency-neutral extraction. It owns the completion lock, authoritative session reload, COMPLETING transition, selected-trust downstream dispatch, continuation lookup, final or continuing session transition, cancellation, rejection mapping, and reactive lock release. Unexpected failures remain propagated for the future shared classifier. Production remains on CompleteRegistrationService pending P3-E2C behavioral coverage.

P3-E2C directly characterizes RegistrationCompleteProtectedOperation. The extracted workflow preserves final return-to-login behavior, additional-trust continuation, lock contention, authoritative reload guards, protected selected-trust routing, canonical rejection handling, cancellation, deterministic continuation lookup failure, and propagation of pre-dispatch and ambiguous failures. Production remains on CompleteRegistrationService; P3-E3 typed replay is next.

P3-E3 adds RegistrationCompleteReplayCodec. It preserves original public completion semantics, cookie clearing, ordered remaining trust accountEnv/trustCode pairs, and public errors. Email and mobile are not persisted because the Complete web contract does not expose them; reconstructed internal trust values use blank protected contact fields. COMPLETED_REPLAY and UNKNOWN_OUTCOME remain lifecycle-only. Production remains legacy; P3-E4 shared coordination is next.

P3-E4 adds SharedCompleteRegistrationService and RegistrationCompleteSharedExecutionBridge in parallel. Context authorization, expiry refresh, state and username loading, and password validation all precede shared reservation. The classifier maps RegistrationCompletionPreDispatchException to PROVEN_NO_SIDE_EFFECT and all other unexpected failures to POSSIBLE_SIDE_EFFECT. Replays return the original typed result. Production remains on CompleteRegistrationService until P3-E5.

P3-E5A switches RegistrationCompleteUseCaseConfig to SharedCompleteRegistrationService with RegistrationCompleteProtectedOperation, RegistrationCompleteReplayCodec, RegistrationCompleteSharedExecutionBridge, IdempotencyExecutor, and the shared identity and fingerprint factories. RegistrationIdempotencyPort and RegistrationRequestHashGenerator are absent from the production Complete bean path. All four Registration actions now use shared idempotency in production. The legacy CompleteRegistrationService remains only for P3-E5B harness migration and P3-E6 removal. No commit is allowed before full P3 stabilization and formatting.

P3-E5B migrates Registration Complete test ownership to the shared coordinator and protected operation. Duplicate legacy success, resilience, expiry, and prerequisite fixtures are removed; TC-061, TC-062, TC-065, and TC-068 remain on direct protected-operation coverage. Architecture guards now target SharedCompleteRegistrationService or RegistrationCompleteProtectedOperation according to ownership. CompleteRegistrationService remains production-unused and is scheduled for P3-E6 removal.

P3-E6 removes CompleteRegistrationService and the obsolete Registration-specific idempotency port, models, request hash generator, Redis lifecycle adapter/configuration, and dedicated stale tests. Shared idempotency infrastructure, Registration Start protected replay, all four shared production routes, Complete failure classification, protected-operation coverage, and TC-061/062/065/068 traceability remain. The cumulative tree stays uncommitted until P3-G formatting and stabilization pass.

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
