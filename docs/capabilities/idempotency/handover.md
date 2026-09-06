## Shared Idempotency Handover

### Authoritative current state

- Branch: `fix/relay-same-error-code-for-the-idempotent-request`
- Exact cumulative Git pre-state: `f719d5f29f729f6176163d891c75fb40ce6b8d8c`
- Latest approved committed documentation baseline: `a998997c800bb5ddc7649cd58bb4006152e311d7`, `docs(idempotency): define registration migration baseline`
- Cumulative uncommitted Login stages: P4-A1 through P4-A2E3, P4-B1A through P4-B1C, P4-B2A1, and P4-B2B1B
- Current Login Start production route: `SharedStartLoginService` through the shared idempotency executor; retained `StartLoginService` is the composition-only rollback path
- Current completed implementation: P4-B2D1D deterministic shared-versus-legacy Login Start namespace isolation validation
- Current blocker: Production-composed Login controller-boundary validation remains pending
- Next permitted stage: P4-B2D1E production-composed Login controller-boundary validation
- Deferred implementation commit: `refactor(login): migrate idempotency to shared capability`

### Current production constraint

No Login action is switched to a new shared coordinator by P4-B2A1. Login Start, OTP Send, OTP Resend, OTP Verify, controllers, mappers, cookies, and public HTTP mappings remain on their current production routes. Legacy Login idempotency lifecycle code remains until the approved action migrations and compatibility window are complete.

### Historical migration record

The sections below retain earlier P2, P3, and P4 decisions as history. Historical baselines do not replace the authoritative current state above.

### Historical P2 and P3 handover snapshot

### Current approved baseline

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
