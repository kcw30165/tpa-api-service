<!-- P6-C-AUTHORITATIVE-STATE-BEGIN -->
#### P6-C Authoritative Current State
- Branch: `fix/relay-same-error-code-for-the-idempotent-request`.
- P5 is complete and closed.
- P6-C Username Recovery START migration is complete at the repository level.
- Production START uses repository-wide shared idempotency through `IdempotentStartUsernameRecoveryService`.
- REQUEST and CANCEL remain on the Username Recovery feature-local lifecycle pending their dedicated P6 stages.
- The complete Maven suite passed with 4,099 tests, zero failures, zero errors, and 12 skipped tests.
- The local-profile Spring application started on Netty port 8888 and remained running through the stability window without bean or configuration errors.
- The next permitted stage is P6-D from the exact commit produced by this closure.
<!-- P6-C-AUTHORITATIVE-STATE-END -->

### Shared Idempotency Handover

#### Authoritative current state
- Branch: fix/relay-same-error-code-for-the-idempotent-request
- Exact validated P5 and P6 starting Git HEAD: 49fb6adc3cab82b753b9bbd7e20c05b42fc7820f
- Exact origin/dev ancestor: 83728f39d186dbd614d134e2c41825811232ddc7
- P5 status: complete and closed
- Current phase: P6-C Username Recovery START replay foundation
- Proven in-scope actions: START, REQUEST, and CANCEL
- Current production routes: all three actions remain on the Username Recovery feature-local response-idempotency lifecycle
- Current uncommitted scope: P6-B shared action, identity, fingerprint, classifier, strict envelope, focused tests, and aligned documentation
- Next gate: commit P6-B and pass the full Maven and local-profile Spring startup transition gate before P6-C
- Next permitted implementation after that gate: P6-C Username Recovery START migration foundation
- Planned final implementation commit: refactor(username-recovery): migrate idempotency to shared capability

#### Current production constraint

P6-A changes documentation only. START, REQUEST, CANCEL, controllers, public mappings, cookies, protected auth-flow context, Redis writers, and Spring production composition remain unchanged. No feature-local Username Recovery idempotency component may be deleted until all consumers migrate and the compatibility and stale-component deletion gates pass.

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

The approved future shared Login Start operation scope is the versioned anonymous constant `login-start-anonymous-v1`. The operation identity hashes that constant through `LoginIdempotencyIdentityFactory`; request equivalence remains the version-1 fingerprint of normalized username plus the one-way digest of the exact password. This ensures same-key requests with a changed username or password map to key reuse without treating an unverified username as an authorized scope. P4-B2B1B does not yet add or switch to the shared Login Start coordinator.

#### P4-B2B1B reactive committer closure

The protected replay committer now defers validation, reference generation, protection, and persistence through `Mono.defer`, so invalid inputs and owned-step failures are emitted consistently through the reactive boundary. Compile-checked coverage includes null, zero, and negative session and maximum TTL values, reference-generation failure, protection failure, save failure, and proof that no later owned step runs after an earlier failure. The legacy coordinator delegation, constructor compatibility, replay behavior, and `StartLoginService` production route remain unchanged.

### P4-B2B parallel shared Login Start coordinator

P4-B2B adds `SharedStartLoginService` in parallel with the active legacy `StartLoginService`. It reproduces credential policy validation and normalization before shared executor invocation, uses the exact anonymous scope `login-start-anonymous-v1`, and fingerprints the normalized username with the one-way digest of the exact password.

Only the shared executor acquired path invokes `LoginStartProtectedOperation`. A `STARTED` result is persisted through `LoginStartProtectedReplayCommitter` before safe response capture; deterministic credential rejections skip protected replay persistence; `UNAVAILABLE` enters the possible-side-effect failure path. Completed replay hydrates through `LoginStartExecutionBridge`, while duplicate, key reuse, failed-existing, and unknown-existing outcomes map to the existing public Login Start statuses.

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

### P4-G final validated repository outcome

Final committed-state validation completed successfully at Git HEAD `4afbc2726a6140cf41d7168e8ef301451bbe76f1`. Spotless check, production compilation, test compilation, the Maven full suite, and Git diff hygiene all passed. Surefire reported 956 suites, 4,077 tests, zero failures, zero errors, and 10 skipped tests. Repository-level P4-G validation is complete. No deployment, environment-coverage, rollback-window, or TTL-drainage claim is made because the shared Login migration has not been deployed.

## P5-C Forgot Password shared-idempotency integration

- Baseline: `ff1b7269cc5f1287efa520d11e3164b415c510ba`.
- Added action-specific integration policy for the five scoped Forgot Password actions over the single repository-wide shared foundation.
- Production composition remains unchanged.
- The next stage is the START protected-operation and shared-coordinator migration.
- P5-A/B/C/D/E remains authoritative.
- Deployment timestamps, environment coverage, rollback-window closure, and TTL-drainage remain not applicable.

### P5-C Forgot Password START protected replay foundation

- Added the START protected replay state and storage boundary outside shared idempotency persistence.
- Added a secure opaque-reference generator with 256 bits of entropy.
- No Redis adapter, protected-state encryption adapter, shared coordinator, or production composition switch is included yet.
- The next stage implements protected persistence and the START response codec/bridge before switching composition.
- P5-A/B/C/D/E remains authoritative.

### P5-C Forgot Password START protected persistence

- Added capability-scoped Redis persistence under `forgot-password:start-protected-replay`.
- Added strict protected-wire JSON mapping and AES-GCM protection bound to the opaque replay reference through AAD.
- Added conditional runtime beans and dedicated environment-backed key-version and secret properties.
- Production `StartForgotPasswordUseCase` composition remains unchanged.
- The next stage adds the strict START replay codec and shared execution bridge.
- P5-A/B/C/D/E remains authoritative.

### P5-C Forgot Password START codec and bridge

- Added strict safe-snapshot capture for STARTED, USERNAME_NOT_FOUND, and AUTH_FLOW_INVALID terminal results.
- STARTED capture stores protected continuity separately and persists only the opaque replay reference in the shared snapshot.
- Replay hydration checks protected-state expiry and recreates a fresh CSRF token through the authorized application boundary.
- Added transient response conversion and fail-closed unit coverage.
- Production `StartForgotPasswordUseCase` composition remains unchanged.
- The next stage splits the protected START operation and introduces the shared coordinator before the composition switch.
- P5-A/B/C/D/E remains authoritative.

### P5-C shared-boundary consolidation

- Confirmed that the repository has one shared idempotency foundation.
- Centralized opaque operation identity construction, canonical fingerprint hashing, constant failure classifiers, and strict action-tagged response envelopes.
- Replaced the duplicate Forgot Password generic helpers with `ForgotPasswordIdempotencyIntegrationPolicy`, which supplies only capability, action, authorized scope, and action-specific canonical fields.
- Retained START protected persistence, typed response capture, replay hydration, and fresh-CSRF behavior as action-specific integration responsibilities.
- Login and Registration production behavior is unchanged in this increment; their equivalent duplicate helpers are recorded for later compatibility-preserving consolidation.
- Production Forgot Password START composition remains unchanged.
- P5-A/B/C/D/E remains authoritative.

### P5-C Login and Registration central primitive adoption phase 1

- Converted the existing Login and Registration identity, fingerprint, and constant-classifier APIs into compatibility delegates over the central shared primitives.
- Preserved existing operation IDs and fingerprint byte sequences, including Registration's established action-first fingerprint format and Login's capability/action-prefixed format.
- Existing constructors, Spring bean types, production selection, Redis records, and public behavior remain unchanged.
- Envelope migration is intentionally deferred to a separate compatibility stage because Registration codecs have direct strict-ObjectMapper contracts.
- Forgot Password production START composition remains unchanged.
- P5-A/B/C/D/E remains authoritative.

### P5-C central response-envelope adoption

- Converted Login and Registration response-envelope compatibility APIs into thin delegates over `ActionTaggedIdempotencyResponseEnvelope`.
- Preserved exact `idempotencyAction` and `payload` wire names, stable action values, strict unknown-field rejection, flow-specific diagnostics, and existing constructor signatures.
- Registration action-specific codecs retain their strict direct payload parsing and validation.
- Persisted terminal snapshot schema, Redis records, public responses, and production bean selection remain unchanged.
- Forgot Password production START composition remains unchanged.
- P5-A/B/C/D/E remains authoritative.

### P5-C Forgot Password START protected operation extraction

- Added `ForgotPasswordStartProtectedOperation` containing the existing username lookup, protected context, session concurrency, session creation, and CSRF workflow without idempotency reservation or persistence dependencies.
- Preserved username normalization, actor-role propagation, identity-type mapping, session-replacement conflict handling, and public START result mappings.
- Retained the legacy `StartForgotPasswordService` and current production composition unchanged during this extraction stage.
- The next stage adds the thin shared-executor coordinator and tests it before the atomic production composition switch.
- P5-A/B/C/D/E remains authoritative.

### P5-C Forgot Password START idempotent coordinator

- Added `IdempotentStartForgotPasswordService` as the thin START integration over the repository-wide `IdempotencyExecutor`.
- The coordinator alone supplies the START operation identity, normalized-username fingerprint, TTL, conservative possible-side-effect classifier, protected operation, response capture, replay hydration, and lifecycle result mapping.
- `SYSTEM_FAILED` from the protected operation is raised as an execution failure rather than persisted as a terminal public snapshot.
- The coordinator has no legacy `ForgotPasswordIdempotencyPort`, Redis, or web dependency.
- Legacy `StartForgotPasswordService` and production composition remain unchanged pending a separate atomic switch and validation stage.
- P5-A/B/C/D/E remains authoritative.

### P5-C flow-local idempotency package simplification

- Removed the ambiguous `shared` package segment from Login and Forgot Password flow-specific idempotency code.
- Login integration types now live directly under `login.idempotency` because they are Login policy and adapters, not a second shared foundation.
- Forgot Password capability policy now lives under `forgotpassword.idempotency`; START-specific replay, protection, and execution bridge types live under `forgotpassword.idempotency.start`.
- Removed flow-local `Shared` type prefixes where they only meant “uses the repository-wide shared executor.”
- Renamed the pending START coordinator to `IdempotentStartForgotPasswordService`.
- The only cross-flow shared foundation remains `application.idempotency`.
- Production Forgot Password START composition remains unchanged.
- P5-A/B/C/D/E remains authoritative.

### P5-C flow package correction follow-up

- Moved `ForgotPasswordIdempotencyAction` to `forgotpassword.idempotency` because it defines all capability actions, not only START.
- Kept START replay and protection types under `forgotpassword.idempotency.start`.
- Removed remaining Login idempotency `SharedExecutionBridge` names in favor of direct `ExecutionBridge` names.
- Renamed ambiguous integration tests to responsibility-based names.
- The only cross-flow shared package remains `application.idempotency`.

### P5-C Forgot Password START production composition switch

- The sole production START bean now constructs `IdempotentStartForgotPasswordService` over the repository-wide `IdempotencyExecutor`.
- START production composition no longer receives `ForgotPasswordIdempotencyPort` or constructs the legacy START service.
- Non-START Forgot Password actions retain their current lifecycle until separately migrated.
- The legacy START service remains non-composed source for parity and rollback analysis.
- P5-A/B/C/D/E remains authoritative.

### P5-C Forgot Password START replacement parity tests

- Added protected-operation behavioral parity coverage for normalized username and actor-role propagation, protected context, STARTED session construction, existing-session replacement input, deterministic lookup outcomes, technical lookup failure, and revision-conflict restart behavior.
- Added idempotent coordinator coverage for stable anonymous scope, normalized fingerprint input, TTL, conservative possible-side-effect classification, acquired execution, completed replay, all existing lifecycle outcomes, and protected-operation system failure propagation.
- Existing replay-codec, controller, and composition tests remain the authoritative coverage for safe snapshots, protected continuity, fresh CSRF, cookies, public mappings, and single production composition.
- Legacy START source remains present pending a separate cleanup stage.
- P5-A/B/C/D/E remains authoritative.

### P5-C Forgot Password START legacy cleanup

- Removed the non-composed legacy `StartForgotPasswordService` and its dedicated legacy lifecycle test after replacement parity coverage was committed.
- Production START remains exclusively composed through `IdempotentStartForgotPasswordService` and the repository-wide shared executor.
- Preserved the negative composition guard that asserts the legacy constructor is absent from production configuration.
- Preserved the legacy Forgot Password idempotency port, DTOs, Redis adapter, and action values because non-START Forgot Password operations still depend on them.
- Replacement protected-operation, coordinator, replay, controller, and composition tests remain the authoritative START coverage.
- Deployment timestamps, environment coverage, rollback-window closure, and TTL-drainage gates remain not applicable before repository cleanup completes.
- P5-A/B/C/D/E remains authoritative.

### P5-C full-test checkpoint repair

- Removed the Registration application package dependency on the outbound JSON adapter by using an application-owned transient idempotency JSON codec.
- Enabled registered Java time modules for Forgot Password START protected replay serialization while retaining reference-bound AES-GCM authentication and fail-closed behavior.
- Corrected the START replay expiry test so the stored protected state is made expired after capture rather than overwritten by capture.
- Narrowed the composition guard to reject the exact legacy constructor instead of the replacement service name containing the legacy suffix.
- Architecture rules remain unchanged and authoritative.
- P5-A/B/C/D/E remains authoritative.

### P5-C Forgot Password DETAILS_AND_OTP migration baseline

- Baseline: `aaca1b95d07a8da6d8172341bdef0e6c4f9b3c33` on `fix/relay-same-error-code-for-the-idempotent-request`.
- The next bounded P5-C action is `DETAILS_AND_OTP`.
- START migration, cleanup, full-test repair, and normal local startup evidence remain preserved.
- OTP resend, OTP verify, complete, cancel, and their legacy idempotency dependencies remain intentionally preserved.
- The detailed migration sequence is defined in `forgot-password-details-otp-migration.md`.
- Next step: add fail-first characterization and architecture tests before implementing the shared-executor path.
- Before P5-C to P5-D, rerun the full Maven suite and normal local Spring startup on the final P5-C state.

#### P5-C DETAILS_AND_OTP fail-first boundary

- Added a compile-safe, intentionally red architecture contract for the DETAILS_AND_OTP shared-executor migration.
- The contract requires the shared coordinator, protected operation, response bridge, replay codec, protected replay boundary, and atomic composition switch.
- It also requires removal of action-local legacy lifecycle calls while preserving OTP resend, OTP verify, complete, and cancel.
- Production behavior remains unchanged. The next round implements the deterministic target required by this contract.

#### P5-C DETAILS_AND_OTP replay bridge foundation

- Added the DETAILS_AND_OTP protected replay resolution boundary, strict action-tagged replay codec, and typed transient execution bridge.
- The shared snapshot persists only safe status/targets plus an opaque protected reference for successful OTP continuity.
- Production composition and remaining legacy actions are unchanged.
- Next step: implement the protected replay resolver over authoritative commit/session/challenge state, then extract the protected operation and coordinator.

#### P5-C DETAILS_AND_OTP protected operation

- Added the extracted protected operation and authoritative protected replay resolver.
- The new path preserves lock, session, lease, verification, OTP, delivery, binding, and commit-state behavior without calling the legacy idempotency lifecycle.
- Production composition and OTP resend, OTP verify, complete, and cancel remain unchanged.
- Next step: add the shared coordinator and atomically switch DETAILS_AND_OTP composition.

#### P5-C DETAILS_AND_OTP shared coordinator

- Added and focused-tested the shared DETAILS_AND_OTP coordinator.
- Validation occurs before reservation; only ACQUIRED executes the protected operation; replay and non-terminal outcomes do not.
- Production composition remains unchanged. Next step is the atomic composition switch and removal of the legacy DETAILS_AND_OTP orchestration only.

#### P5-C DETAILS_AND_OTP atomic composition switch

- Switched production DETAILS_AND_OTP composition to the shared executor path and removed its legacy orchestration.
- Migrated direct parity, endpoint architecture, and revision-aware assertions to the shared target.
- Preserved OTP resend, OTP verify, complete, cancel, and their legacy lifecycle collaborators.
- Next step: commit the switch, then run feature-level stabilization, formatting, full Maven tests, and startup smoke before P5-D.

#### P5-C protected execution Result alignment

- Renamed the DETAILS_AND_OTP protected execution carrier with a `Result` suffix to satisfy the clean-architecture execute-method contract.
- The cumulative Spotless changes from the stabilization attempt are preserved.

#### P5-C protected execution Result compile repair

- Corrected the protected execution record declaration to match its filename and all cumulative references.
- Preserved the cumulative formatting and migration changes.

#### P5-C startup smoke completed

- Spring startup reached the application started marker and remained running during the post-start stability window.
- Runtime-only local overrides disabled Redis, enabled lazy initialization, and excluded SSL metrics auto-configuration so deployment-only Redis mTLS files were not loaded.
- No production configuration was changed. The full Maven suite had already passed against the same cumulative code state.
- Next action is the P5-C stabilization commit. P5-D remains blocked until commit evidence is reviewed.

#### P5-D committed-baseline validation

- Exact baseline `b88e2e5d534fd39895db07e73e4a73145a67dd69` passed compile, test compilation, focused replay and architecture tests, and static migration-boundary audits.
- START and DETAILS_AND_OTP remain on the shared executor. Remaining Forgot Password actions remain intentionally composed through their existing services.
- The prior full Maven and startup-smoke gates remain the transition evidence for this committed state.
- Next action is to commit the P5-D documentation evidence, then perform P5-E closure review.

#### P5-C OTP_RESEND fail-first boundary

- Exact baseline: `e8092ff875b492a129faeff27c1d6a9eca358823`.
- Added the fail-first architecture boundary for the OTP_RESEND shared-executor migration.
- The red test requires `IdempotentResendForgotPasswordOtpService`, `ForgotPasswordOtpResendProtectedOperation`, `ForgotPasswordOtpResendExecutionBridge`, `ForgotPasswordOtpResendReplayCodec`, and `ForgotPasswordOtpResendProtectedReplayPort`.
- Production composition remains on `ResendForgotPasswordOtpService` until replacement behavior is implemented and proven.
- Preserve no-blind-resend, committed replay, delivery uncertainty, challenge/session binding, cooldown, resend limits, remaining attempts, and all other action compositions.

#### P5-C OTP_RESEND replay bridge foundation

- Added `ForgotPasswordOtpResendExecutionBridge`, `ForgotPasswordOtpResendReplayCodec`, `ForgotPasswordOtpResendProtectedReplayPort`, and `DefaultForgotPasswordOtpResendProtectedReplayResolver`.
- Protected resend continuity remains outside the safe response body and is resolved through the existing protected commit-state boundary.
- The fail-first boundary remains intentionally red only for the not-yet-implemented shared coordinator and protected operation. Production composition is unchanged.

#### P5-C OTP_RESEND protected-operation parity boundary

- Added the red parity boundary for `ForgotPasswordOtpResendProtectedOperation` and `ForgotPasswordOtpResendProtectedExecutionResult`.
- Required behavior includes direct lock-scoped mutation, protected commit-state persistence before returning the opaque reference, deterministic delivery-failure cleanup, and unknown-outcome signaling after the session is blocked.
- The operation may not delegate to `ResendForgotPasswordOtpService` or use feature-local lifecycle persistence. Production composition remains unchanged.

#### P5-C OTP_RESEND protected Result contract

- Added the typed protected execution Result invariant for OTP_RESEND.
- `RESENT` requires a `ProtectedReplayReference`; non-RESENT outcomes reject one.
- The parity boundary remains red only for the not-yet-implemented protected operation. The shared coordinator and production switch remain deferred.

#### P5-C OTP_RESEND protected-operation extraction

- Added the direct protected operation over the existing lock, session, challenge, delivery, and separately protected commit-state boundaries.
- Legacy idempotency lifecycle ownership remains only in the composed legacy service.
- Production composition remains unchanged. Next action is compile repair if required, then focused parity validation and shared coordinator implementation.

#### P5-C OTP_RESEND protected-operation compile repair

- Repaired the generated protected-operation fallback that caused `Mono<Record>` inference.
- All protected-operation branches now return the typed execution Result. Production composition remains unchanged.

#### P5-C OTP_RESEND protected-operation branch type repair

- Corrected the second mixed branch responsible for `Mono<Record>` inference.
- Both the eligibility result branch and error fallback now return the typed protected execution Result. Production composition remains unchanged.

#### P5-C OTP_RESEND shared-coordinator fail-first boundary

- Added the red source-level behavior boundary for `IdempotentResendForgotPasswordOtpService`.
- Required routing is: duplicate in progress to system failure, key reuse to idempotency-key-reused, failed existing to previous-failed, and unknown existing to unknown-outcome.
- Acquired and completed replay must use the typed response bridge, and protected execution must propagate its opaque reference to capture. Production composition remains unchanged.

#### P5-C OTP_RESEND shared coordinator implementation

- Added the thin shared coordinator over the extracted protected operation and typed replay bridge.
- The coordinator has no dependency on feature-local idempotency persistence.
- Production composition remains unchanged. Next action is focused validation and production composition readiness before switching the bean.

#### P5-C OTP_RESEND production composition switch

- Production OTP_RESEND now selects the repository-wide shared idempotency coordinator.
- The switch composes the shared executor, protected operation, typed bridge, strict codec, and protected replay resolver using the existing configured TTL and lock lease.
- Legacy resend code is intentionally retained for post-switch audit and rollback review. All non-selected Forgot Password actions remain unchanged.
- Next action is post-switch reference and behavioral parity review before any legacy cleanup or feature stabilization.

#### P5-C OTP_RESEND production-switch assertion repair

- Corrected the formatting-sensitive production composition assertion after the switch.
- The assertion still requires the shared coordinator and forbids construction of the legacy resend service. No production code was changed in this repair.

#### P5-C OTP_RESEND authorized legacy cleanup

- Deleted only the exact legacy OTP_RESEND unit authorized by the exact-symbol audit.
- Removed stale configuration imports and the obsolete request-hash bean, and retargeted the two identified architecture guards.
- Shared production composition and cross-action collaborators remain preserved.
- Next action: validate the cleaned cumulative change set, then prepare the OTP_RESEND stabilization gate before any stage transition.

#### P5-C OTP_RESEND feature stabilization

- Applied Spotless at the feature-level stabilization boundary after production switch and legacy cleanup.
- The cumulative OTP_RESEND implementation remains on the shared executor path with the retired lifecycle unit removed.
- Next action: run the full Maven suite and application startup smoke gate. Do not begin the next migration action until both pass.

#### P5-C OTP_RESEND stabilization assertion repair

- Updated only the formatting-sensitive coordinator test after Spotless wrapped `operation.execute(command)`.
- The assertion still verifies the exact invocation after whitespace normalization. Production behavior is unchanged.
- Next action remains the full Maven suite and application startup smoke gate after focused stabilization validation passes.

#### P5-C OTP_VERIFY fail-first shared boundary

- Added the first fail-first OTP_VERIFY migration boundary without changing production composition.
- Required future assets are `IdempotentVerifyForgotPasswordOtpService`, `ForgotPasswordOtpVerifyProtectedOperation`, `ForgotPasswordOtpVerifyProtectedExecutionResult`, `ForgotPasswordOtpVerifyExecutionBridge`, and `ForgotPasswordOtpVerifyReplayCodec`.
- Keep the legacy verify service active until coordinator, replay, protected-operation, and behavioral parity tests pass. Preserve all previously migrated actions and COMPLETE/CANCEL.

#### P5-C OTP_VERIFY replay foundation

- Added and tested the OTP_VERIFY replay codec and typed transient bridge only.
- Production still composes the legacy verify service. The coordinator and protected operation/result remain intentionally absent for the next increment.

#### P5-C OTP_VERIFY exact sensitive-token guard

- Repaired only the fail-first guard false positive caused by substring matching of `code` inside codec method and class names.
- The replay foundation is unchanged. The boundary must now remain red only for the absent shared coordinator and protected operation/result.

#### P5-C OTP_VERIFY protected operation

- Added the protected OTP_VERIFY operation/result and parity guard while retaining the legacy production service.
- Next action is the shared coordinator. Do not switch production composition yet.

#### P5-C OTP_VERIFY protected-operation compile repair

- Removed the stale `ForgotPasswordIdempotencyReplaySnapshot` helper from the extracted protected operation.
- The cumulative replay foundation, protected operation/result, parity guard, and legacy production composition remain preserved.
- Next action remains the shared OTP_VERIFY coordinator after this cumulative increment validates.

#### P5-C OTP_VERIFY authorization-reference compile repair

- Restored `opaqueRef` only for protected reset-authorization creation after the earlier broad helper removal.
- The stale replay helper remains removed, and the protected operation remains free of feature-local idempotency lifecycle calls.
- Next action remains the shared OTP_VERIFY coordinator after cumulative validation passes.

#### P5-C OTP_VERIFY shared coordinator

- Added and focused-tested `IdempotentVerifyForgotPasswordOtpService` without changing production composition.
- The coordinator delegates protected OTP verification to the extracted operation and lifecycle ownership to the shared executor.
- Next action: audit readiness for the production composition switch. Keep the legacy service until that dedicated switch increment.

#### P5-C OTP_VERIFY production switch

- Production now composes the shared OTP_VERIFY coordinator and no longer injects `ForgotPasswordIdempotencyPort` into the OTP_VERIFY bean.
- Updated composition, reactive, hash, parity, shared-boundary, and cross-action preservation assertions.
- Retain the legacy service and tests until the next exact-symbol cleanup audit authorizes deletion. Spotless remains deferred until feature stabilization.

#### P5-C OTP_VERIFY composition-test compile repair

- Corrected only the Java string escaping in the OTP_VERIFY composition architecture test.
- The shared production composition remains active, and legacy source cleanup remains deferred to the exact-symbol audit.

#### P5-C OTP_VERIFY canonical-fingerprint guard repair

- Updated the fingerprint architecture guard to validate the migrated shared coordinator and policy rather than the retained legacy service implementation.
- The production switch remains active, and legacy cleanup remains deferred to the exact-symbol audit.

#### P5-C OTP_VERIFY hash-guard compile correction

- Replaced the malformed fingerprint-guard source with compile-safe structural assertions while preserving all cumulative production-switch changes.
- Legacy OTP_VERIFY cleanup remains deferred to the exact-symbol audit.

#### P5-C OTP_VERIFY legacy cleanup

- Deleted the retired OTP_VERIFY feature-local lifecycle implementation and its obsolete tests.
- Production remains composed exclusively through `IdempotentVerifyForgotPasswordOtpService` and the repository-wide shared executor.
- Exact-identifier and compilation gates prevent the deleted lifecycle from remaining referenced.

#### P5-C COMPLETE fail-first shared boundary

- Added the initial fail-first boundary for migrating Forgot Password COMPLETE to the repository-wide shared executor.
- Required future assets are the thin coordinator, protected operation/result, execution bridge, and strict replay codec.
- Password and confirmation values must never enter the operation fingerprint or persisted replay snapshot.
- Production composition is intentionally unchanged. Next step is replay-contract and protected-operation readiness before implementation.

#### P5-C COMPLETE fail-first compile correction

- Replaced the malformed COMPLETE fail-first test source with compile-safe structural assertions.
- The boundary remains intentionally red because the shared COMPLETE implementation assets do not exist yet.

#### P5-C COMPLETE replay bridge foundation

- Added the COMPLETE replay codec and typed execution bridge against the captured repository contracts.
- Production remains on the legacy COMPLETE service pending protected-operation and coordinator work.

#### P5-C COMPLETE replay bridge focused coverage

- Added executable replay codec and execution bridge tests for the cumulative script 657 foundation.
- Success and deterministic business rejection round-trip without protected data; unsafe and lifecycle-only forms fail closed.
- Production remains on `CompleteForgotPasswordService`. Next action is protected-operation extraction readiness.

#### P5-C COMPLETE protected-operation foundation

- Added the transient protected-operation input and classified execution-result foundation.
- The input redacts command, protected context, and session snapshot from logs.
- The result type keeps terminal replay, technical failure, and unknown outcome distinct before the shared coordinator is introduced.
- Next step is the idempotency-neutral protected operation implementation.

#### P5-C COMPLETE protected-operation fail-first parity boundary

- Added the intentionally red protected-operation implementation, parity, and architectural-neutrality boundary.
- The existing replay bridge and protected-operation foundation remain preserved. Production remains on the legacy COMPLETE service.
- Next action is deterministic protected-operation implementation against the captured contracts.

#### P5-C COMPLETE protected-operation implementation

- Added and validated the idempotency-neutral COMPLETE protected operation while preserving the cumulative replay bridge and fail-first foundations.
- Production remains composed with the legacy COMPLETE service. Next action is focused behavioral parity and coordinator readiness.

#### P5-C COMPLETE protected-operation parity correction

- Corrected the protected-operation parity boundary by making RESET handling explicit rather than relying on the residual branch.
- Existing REJECTED, TECHNICAL_FAILURE, UNKNOWN_OUTCOME, replay-safety, and production-composition behavior remains preserved.

#### P5-C COMPLETE protected-operation behavior guards

- Added executable guards for the audited COMPLETE protected-operation behavior matrix.
- Existing replay, foundation, parity, and neutrality coverage remains preserved. Production remains on the legacy COMPLETE service.
- Next action is shared-coordinator contract readiness.

#### P5-C COMPLETE behavior-guard assertion correction

- Replaced the invalid `authorization.consumedAt()` source-text expectation with assertions for immutable consumed-authorization construction and application.
- The protected operation itself remains unchanged.

#### P5-C COMPLETE shared-coordinator fail-first boundary

- Added the intentionally red shared-coordinator boundary using the exact contracts captured by script 669.
- Protected operation, replay bridge, behavior guards, and documentation remain preserved. Production remains on the legacy COMPLETE service.
- Next action is deterministic shared-coordinator implementation.

#### P5-C COMPLETE shared-coordinator implementation

- Added the shared COMPLETE coordinator and preserved all cumulative protected-operation, replay, behavior-guard, and fail-first work.
- Production remains on the legacy COMPLETE service pending focused coordinator tests and the composition switch.

#### P5-C COMPLETE shared-coordinator guard correction

- Repaired only the whitespace-sensitive operation-invocation assertion.
- The shared coordinator implementation remains unchanged. Next action is focused lifecycle and protected-execution tests.

#### P5-C COMPLETE shared-coordinator lifecycle coverage

- Added executable shared-coordinator lifecycle and protected-execution routing tests.
- Cumulative replay, protected-operation, architecture, and documentation work remains preserved.
- Production remains on the legacy COMPLETE service. Next action is the production composition switch.

#### P5-C COMPLETE production composition switch

- Spring production composition now uses the validated shared COMPLETE coordinator.
- The legacy COMPLETE service remains in source temporarily for stabilization comparison but is no longer instantiated.
- Next action is feature-level formatting, cumulative stabilization, and P5-C completion evidence.

#### P5-C COMPLETE stabilization

- Feature-level formatting, production compile, test compilation, composition audits, replay-safety audits, and documentation alignment are complete.
- P5-C must not transition until a separate gate records the full Maven suite and Spring startup smoke test.

#### P5-C transition-gate guard correction

- Repaired only the formatted lock-acquisition assertion. Production behavior and composition remain unchanged.

#### P5-C COMPLETE transition-gate closure

- Full Maven suite: 4,169 tests, zero failures, zero errors, 10 skipped.
- Spring context and Netty startup completed successfully. The later local runner failure was limited to unavailable deployment Redis Sentinel connectivity after startup.
- P5-C is complete. Next stage: P5-D.

#### P5-D feature-local lifecycle cleanup

- Removed the exact retired feature-local lifecycle assets authorized by scripts 684 and 685.
- Protected OTP commit state and all shared production routes remain intact.
- Next action is an exact unused DTO and residual-reference audit before further cleanup.

#### P5-D unused lifecycle DTO cleanup

- Removed the seven zero-external-reference lifecycle DTOs identified by script 687.
- No public command boundary, shared coordinator, protected replay, or protected commit-state type was removed.
- Next action is a fresh residual-closure audit before further deletion.

#### P5-D residual lifecycle DTO cluster cleanup

- Removed the isolated legacy lifecycle DTO cluster and its obsolete tests based on script 689 evidence.
- Active public command contracts, replay continuity, protected commit state, and shared production composition remain intact.
- Next action is P5-D stabilization and focused regression before transition gates.

#### P5-D cleanup stabilization

- Deferred formatting and cumulative focused cleanup regression are complete.
- Shared START, DETAILS_AND_OTP, OTP_RESEND, OTP_VERIFY, and COMPLETE routes remain intact; CANCEL remains outside shared execution.
- Next action is the dedicated full-suite and startup transition gate, followed by commit if green.

#### P5-D transition-gate architecture guard correction

- Repaired only the stale lifecycle coordinator test expectation. Production behavior remains unchanged.

#### P5-D transition-gate closure

- Full Maven suite: 4,148 tests, zero failures, zero errors, 10 skipped.
- Spring context, Netty, and application startup marker: passed.
- Retired feature-local lifecycle infrastructure and isolated DTOs are removed; shared execution and protected OTP commit state remain intact.
- P5-D is complete. Next stage: P5-E.

#### P5-E closure documentation alignment

- Updated all six capability documents to describe the committed post-P5 Forgot Password architecture.
- All five idempotent actions use shared execution; CANCEL remains outside it.
- Protected START replay and OTP commit state remain; the retired feature-local response lifecycle remains absent.
- Next action is the final P5-E full Maven, Spring startup, documentation-diff, and commit gate.

<!-- P6-A-USERNAME-RECOVERY-HANDOVER:START -->
### P6-A Username Recovery migration documentation baseline

- Exact P6 starting baseline: `49fb6adc3cab82b753b9bbd7e20c05b42fc7820f` on `fix/relay-same-error-code-for-the-idempotent-request`.
- P5 remains closed. No P5 work is reopened.
- The read-only audit proved three existing idempotent endpoints and production consumers: START, REQUEST, and CANCEL.
- CANCEL remains in P6 because the current API contract, command, controller, service, fingerprint, replay codec, Redis lifecycle, and tests already require idempotency.
- START consumes one-time captcha before APIM lookup and auth-flow session creation. Successful replay requires protected session/CSRF continuity outside the shared safe snapshot.
- REQUEST is distinct. It reserves session candidate state before one message dispatch containing the username, then commits post-delivery state. Any accepted or uncertain dispatch must remain unknown outcome with no blind resend.
- CANCEL is revision-aware and preserves encrypted workflow context until original expiry. It needs shared response idempotency but no new protected replay store for its public success body.
- The current direct session-ID START/REQUEST replay descriptors are not acceptable shared snapshot content. Migration must use opaque protected replay references.
- The legacy Redis namespace is isolated from the shared HMAC namespace. Use one writer per action, no dual-write, and fail closed on ambiguous legacy state. Confirm exact maximum idempotency TTL before P6-F deletion.
- P6-A design: `docs/capabilities/idempotency/username-recovery-p6-migration-design.md`.
- Next permitted stage: P6-B shared Username Recovery integration contracts without production routing changes.
- Planned final implementation commit: `refactor(username-recovery): migrate idempotency to shared capability`.
<!-- P6-A-USERNAME-RECOVERY-HANDOVER:END -->

#### P6-B Username Recovery shared integration contracts
- Added START, REQUEST, and CANCEL shared action identities under capability username-recovery.
- Added opaque shared operation identity and exact established-byte-compatible version-1 fingerprints.
- Added explicit proven-pre-dispatch and possible-side-effect classifier accessors.
- Added a strict action-tagged response envelope through TransientJsonPort.
- Production composition, legacy lifecycle persistence, replay codecs, protected workflow continuity, controllers, and side-effect ordering remain unchanged.
- Next: action-specific safe snapshot and protected replay-reference contracts before any production switch.


##### P6-C Username Recovery START replay foundation
- Added the action-strict START safe codec, transient bridge, and separately protected replay boundary.
- SESSION_CREATED safe snapshots carry only an opaque protected replay reference.
- Production START remains on the feature-local lifecycle; no Spring composition or route changed.
- Next: protected persistence implementation and focused integration before shared coordination.

##### P6-C Username Recovery START protected persistence
- Added a 256-bit opaque protected replay reference generator and an isolated `username-recovery:start-protected-replay` Redis namespace.
- Storage uses SET-NX with bounded collision retries and the exact caller-supplied TTL.
- Protected continuity uses AES-GCM with reference-bound AAD, strict schema, key-version, algorithm and JSON validation, and redacted diagnostics.
- Resolution authorizes the retained Username Recovery auth-flow session and encrypted context, validates expiry and consistency, and rotates CSRF through the existing application boundary.
- Replay performs no captcha validation, APIM lookup, session creation, delivery, or START production operation.
- Conditional Spring composition uses external key-version and secret properties. Production START routing remains on the feature-local lifecycle.

###### P6-C Username Recovery START protected-operation fail-first boundary
- Added a compile-safe fail-first contract for a parallel, idempotency-neutral `UsernameRecoveryStartProtectedOperation`.
- The future operation must preserve captcha consumption before APIM lookup, then protected auth-flow session creation, while owning no reserve, replay, completion, failed, or unknown lifecycle transition.
- Production START remains on `StartUsernameRecoveryService`. REQUEST, CANCEL, controllers, cookies, public mappings, and all cumulative replay/persistence work remain unchanged.

###### P6-C START protected-operation fail-first boundary repair
- Removed the stale assertion for a literal `idempotencyPort.reserve(` call because the current coordinator owns the lifecycle through different method names.
- The boundary remains intentionally red only because `UsernameRecoveryStartProtectedOperation` is absent.
- Production composition, cumulative replay and protected persistence, REQUEST, CANCEL, and public behavior remain unchanged.

###### P6-C Username Recovery START protected operation
- Added `UsernameRecoveryStartProtectedOperation` as a parallel idempotency-neutral acquired-execution boundary.
- It preserves captcha consumption before APIM lookup, deterministic member outcomes, protected context binding, revisioned auth-flow session creation, CSRF generation and hashing, opaque Scheme-Refs, and the established result contract.
- It owns no legacy or shared idempotency reservation, replay, completion, failure, or unknown-outcome transition.
- Production START remains composed through `StartUsernameRecoveryService`; REQUEST, CANCEL, controllers, cookies, mappings, and cumulative replay and persistence work remain unchanged.
- Next: direct protected-operation behavioral parity before introducing a shared START coordinator.

###### P6-C Username Recovery START protected-operation parity
- Added direct executable coverage for captcha rejection and consumption ordering, member-not-found, zero-registered, registered protected-session creation, CSRF hashing, exact TTL, opaque Scheme-Refs, lookup failures, persistence failure, transient result continuity, and diagnostic redaction.
- The test invokes `UsernameRecoveryStartProtectedOperation` directly and does not delegate to the legacy coordinator.
- Production START remains on `StartUsernameRecoveryService`; REQUEST, CANCEL, public mappings, cookies, and cumulative replay and protected persistence remain unchanged.
- Next: shared START coordinator contract and implementation in parallel before any production switch.

###### P6-C START protected-operation architecture-guard alignment
- Updated the START architecture guard so the extracted `UsernameRecoveryStartProtectedOperation` may own the established CSRF generator and hasher abstractions alongside the still-active legacy coordinator.
- All other Username Recovery START helper classes remain prohibited from depending on the auth-flow CSRF package.
- Direct protected-operation parity coverage, production routing, REQUEST, CANCEL, controllers, cookies, replay continuity, and protected persistence remain unchanged.

###### P6-C START architecture-guard repair closure
- Corrected the previously unapplied guard edit using an exact single-method replacement.
- The extracted protected operation and active legacy coordinator are the only START execution owners permitted to use the established CSRF generator and hasher abstractions.
- Every other Username Recovery START helper remains prohibited from depending on the auth-flow CSRF package. Production composition remains unchanged.

###### P6-C Username Recovery START shared-coordinator fail-first boundary
- Added a compile-safe, intentionally red contract for `IdempotentStartUsernameRecoveryService`.
- The future coordinator must validate before shared reservation, use the established START identity and fingerprint policy, classify unexpected acquired failures conservatively, invoke the protected operation only through ACQUIRED execution, and use the protected replay bridge for capture and hydration.
- Existing lifecycle outcomes remain mapped through the established typed START exceptions. Production remains on `StartUsernameRecoveryService`; REQUEST, CANCEL, public mappings, cookies, and cumulative replay and persistence remain unchanged.

###### P6-C Username Recovery START parallel shared coordinator
- Added `IdempotentStartUsernameRecoveryService` in parallel with the active legacy START coordinator.
- Validation and idempotency-key validation occur before shared reservation. The coordinator uses the versioned anonymous START scope, the established seven canonical fingerprint fields, the configured 600-second START/session TTL supplied by composition, and conservative possible-side-effect classification.
- Only the shared executor ACQUIRED callback invokes `UsernameRecoveryStartProtectedOperation`. Safe capture and completed replay use `UsernameRecoveryStartExecutionBridge`, including separately protected SESSION_CREATED continuity.
- Duplicate, key-reuse, failed-existing, and unknown-existing outcomes retain the established typed START exception behavior. Production composition remains on `StartUsernameRecoveryService`; REQUEST and CANCEL remain unchanged.

###### P6-C Username Recovery START shared-coordinator executable coverage
- Added direct executable coverage for validation short-circuiting, exact anonymous operation scope, client key, established seven-field START fingerprint input, configured TTL, conservative classifier, acquired-only protected execution, completed replay, all non-terminal lifecycle mappings, and protected-operation failure propagation.
- Production remains composed through `StartUsernameRecoveryService`. The shared coordinator remains parallel pending the dedicated composition switch.
- REQUEST, CANCEL, controllers, cookies, public mappings, replay continuity, and protected persistence remain unchanged.

###### P6-C START shared-coordinator test-fixture repair
- Corrected the direct coordinator tests so the invalid business command passes record construction and is rejected by `UsernameRecoveryStartValidator` before shared reservation.
- Supplied the mandatory possible-side-effect classifier in every executor-reaching test fixture.
- Production code, production composition, fingerprint semantics, replay continuity, REQUEST, and CANCEL remain unchanged.

###### P6-C Username Recovery START atomic production composition switch
- Production now selects `IdempotentStartUsernameRecoveryService` as the sole `StartUsernameRecoveryUseCase` and supplies the authoritative Username Recovery session TTL to both shared lifecycle capture and protected session creation.
- The active START bean composes the repository-wide shared executor, Username Recovery integration policy, protected operation, strict replay codec, transient bridge, and separately protected replay port.
- The feature-local idempotency port and fingerprint beans remain because REQUEST and CANCEL still use them. The legacy START source remains temporarily for post-switch parity and cleanup review.
- Controller paths, public mapping, cookie behavior, REQUEST, CANCEL, legacy namespaces, and protected replay persistence remain unchanged. Spotless and the full Maven suite remain deferred to P6-C stabilization.

###### P6-C START post-switch guard repair closure
- Completed the partially applied post-switch guard alignment using distinct invariants for the coordinator and protected-operation tests.
- Both guards require the shared START coordinator. The protected-operation guard additionally requires its production composition, while retained legacy-source characterization remains independent of production selection.
- Production code and all cumulative P6-C behavior remain unchanged.

###### P6-C Username Recovery START authorized legacy cleanup
- Removed the non-composed `StartUsernameRecoveryService`, its dedicated legacy lifecycle harness, and the isolated `UsernameRecoveryStartFingerprintGenerator` with its dedicated test after exact-symbol review.
- Remapped affected ownership guards to `IdempotentStartUsernameRecoveryService` or `UsernameRecoveryStartProtectedOperation` according to current responsibility.
- Retained the feature-local idempotency port, Redis adapter and mapper, records, reservation vocabulary, REQUEST/CANCEL fingerprint generators, and REQUEST/CANCEL replay codecs because REQUEST and CANCEL still depend on them.
- The production START route remains exclusively on the repository-wide shared executor. Legacy START replay codec and snapshot cleanup remains deferred to a separate exact-reference review.

###### P6-C START legacy-cleanup guard repair
- Removed the stale fail-first read of the deleted legacy START coordinator while preserving assertions for shared production composition and the composed protected operation.
- Updated the captcha regression guard to verify consume-before-lookup ordering inside the protected operation and to reject lifecycle ownership there.
- Production code, shared START composition, protected replay, REQUEST, CANCEL, controllers, and cookies remain unchanged.

###### P6-C Username Recovery START feature stabilization
- Applied the single deferred Spotless pass after the production switch, replacement coverage, authorized legacy cleanup, and guard repair.
- START remains exclusively composed through `IdempotentStartUsernameRecoveryService`; the protected operation, strict safe replay codec, separately protected replay store, controller contract, and cookie behavior remain intact.
- REQUEST and CANCEL remain on the retained feature-local lifecycle and Redis persistence pending their dedicated migration stages.
- This stabilization does not satisfy the P6-C transition gate. A separate full Maven suite and Spring application startup smoke test must pass before P6-D begins.

###### P6-C Username Recovery START feature stabilization
- Applied the single deferred Spotless pass after the production switch, replacement coverage, authorized legacy cleanup, and guard repair.
- START remains exclusively composed through `IdempotentStartUsernameRecoveryService`; the protected operation, strict safe replay codec, separately protected replay store, controller contract, and cookie behavior remain intact.
- REQUEST and CANCEL remain on the retained feature-local lifecycle and Redis persistence pending their dedicated migration stages.
- This stabilization does not satisfy the P6-C transition gate. A separate full Maven suite and Spring application startup smoke test must pass before P6-D begins.

###### P6-C Username Recovery START transition-gate closure
- The complete Maven suite passed with 4,099 tests, zero failures, zero errors, and 12 skipped tests.
- The local-profile Spring application started successfully on Netty port 8888 and remained running through the post-start stability window without bean or configuration errors.
- START is exclusively composed through `IdempotentStartUsernameRecoveryService`; the retired legacy START coordinator and isolated START fingerprint generator are absent.
- REQUEST and CANCEL remain on their retained feature-local lifecycle pending their dedicated P6 stages.
- P6-C is complete. The next permitted stage is P6-D only from the exact committed baseline produced by this closure.

###### P6-D Username Recovery REQUEST fail-first boundary
- Added a compile-safe fail-first contract for `IdempotentRequestUsernameRecoveryService`, `UsernameRecoveryRequestProtectedOperation`, its typed protected result, and strict REQUEST response bridge/codec.
- The future protected operation must preserve candidate reservation, one message dispatch, and authoritative post-delivery session transition while owning no legacy or shared idempotency lifecycle.
- Any dispatched or uncertain delivery failure remains conservatively classified as possible side effect and must never permit a blind resend.
- The shared safe snapshot must contain only approved public REQUEST result fields and must not contain a direct auth-flow session ID, raw username, destination, CSRF material, delivery payload, or ownership token.
- Production REQUEST remains on `RequestUsernameRecoveryService`; shared START and legacy CANCEL remain unchanged.

###### P6-D Username Recovery REQUEST protected result contract
- Added `UsernameRecoveryRequestProtectedExecutionResult` as the transient boundary between the future protected operation and shared coordinator.
- Deterministic terminal outcomes carry the existing public `UsernameRecoveryRequestResult` and no failure.
- Failures carry no terminal result and are classified explicitly as proven pre-dispatch or possible side effect. Dispatched, accepted, empty, cancelled, timed-out, or otherwise uncertain delivery paths must use possible side effect.
- The contract contains no auth-flow session identity, username, destination, CSRF material, message payload, idempotency ownership token, or replay state, and its diagnostics expose only the failure type.
- Production REQUEST remains on `RequestUsernameRecoveryService`; shared START and legacy CANCEL remain unchanged. The fail-first migration boundary remains red for the absent protected operation, replay bridge/codec, coordinator, and production switch.

###### P6-D Username Recovery REQUEST protected-operation fail-first boundary
- Added an intentionally red contract for `UsernameRecoveryRequestProtectedOperation` after establishing its typed execution-result boundary.
- The operation must preserve origin and CSRF authorization, protected-context loading, candidate reservation before one delivery dispatch, effective-destination resolution, and authoritative requested, failed, or unknown session transitions.
- Pre-dispatch and possible-side-effect failures remain explicit; delivery or post-dispatch ambiguity must never permit blind resend.
- Production REQUEST remains on `RequestUsernameRecoveryService`; shared START and legacy CANCEL remain unchanged.

###### P6-D Username Recovery REQUEST protected operation
- Added `UsernameRecoveryRequestProtectedOperation` in parallel with the active legacy coordinator.
- It preserves origin and CSRF authorization, revision-aware candidate reservation, effective destination resolution, one message dispatch, and authoritative requested, failed, or unknown session transitions.
- The operation owns no feature-local or shared idempotency lifecycle, fingerprint, replay, Redis, Spring, controller, or HTTP responsibility.
- Delivery and post-dispatch ambiguity are returned as possible-side-effect failures; deterministic pre-dispatch failure remains separately classified.
- Production REQUEST remains on `RequestUsernameRecoveryService`; shared START and legacy CANCEL remain unchanged. Direct behavioral parity remains the next gate before replay and shared coordination.

###### P6-D REQUEST protected-operation guard repair closure
- Replaced the compact source guard with a formatting-tolerant equivalent that normalizes fluent calls before checking effective-destination resolution, single message dispatch, and revisioned session mutation.
- The protected operation, typed result, failure classification, single-dispatch ordering, lifecycle neutrality, and legacy REQUEST production composition remain unchanged.

###### P6-D REQUEST protected-operation parity boundary
- Added an intentionally red direct-parity contract derived from the complete legacy REQUEST behavior matrix and exact constructor capture.
- The future direct harness must cover prerequisite authorization, candidate outcomes, revision conflicts, one dispatch, accepted, failed, unknown, empty, reactive-failure, and post-dispatch ambiguity paths.
- The boundary also requires empty delivery publishers to fail closed as possible side effect and prevents an outer fallback from reclassifying dispatch uncertainty as deterministic failure.
- Production REQUEST remains on `RequestUsernameRecoveryService`; shared START and legacy CANCEL remain unchanged.

###### P6-D REQUEST dispatch-boundary repair
- Scoped effective-destination resolution failure to the proven pre-dispatch path.
- Empty delivery publishers now persist the blocked unknown session state and return possible-side-effect classification.
- Dispatch and post-dispatch failures can no longer fall through to the deterministic failed-candidate path.
- Production REQUEST remains on `RequestUsernameRecoveryService`; direct protected-operation parity, replay, shared coordination, and composition switch remain deferred.

###### P6-D REQUEST dispatch-boundary compile repair
- Repaired the reactive type mismatch by materializing effective-destination resolution before branching into the protected execution result type.
- Destination-resolution failure remains pre-dispatch and persists the deterministic failed candidate state.
- Empty, failed, or otherwise uncertain delivery remains post-dispatch possible side effect, persists the blocked unknown state, and cannot fall through to deterministic failure.
- Production REQUEST remains on `RequestUsernameRecoveryService`; direct parity remains intentionally red until its executable harness is added.

###### P6-D REQUEST protected-operation direct parity
- Added direct executable coverage for prerequisite guards, deterministic candidate outcomes, revision conflict, reservation-before-dispatch, accepted and completed projections, deterministic delivery failure, unknown and empty delivery, reactive dispatch failure, accepted-persistence conflict, and diagnostic redaction.
- The harness invokes `UsernameRecoveryRequestProtectedOperation` directly and never delegates to the legacy coordinator.
- Production REQUEST remains on `RequestUsernameRecoveryService`; strict replay and shared coordination remain the next gates.

###### P6-D REQUEST direct-parity fixture repair
- Aligned prerequisite and reservation-conflict tests with the typed protected-result contract, which returns `PROVEN_PRE_DISPATCH` instead of emitting those expected failures.
- Corrected the unregistered-candidate fixture so its account environment and trust code are unique and accepted by the domain invariant.
- Aligned parity source-token guards with the executable test method names. Production code and composition remain unchanged.

###### P6-D REQUEST direct-parity closure
- Preserved the established emitted security exception for disallowed origin while retaining typed `PROVEN_PRE_DISPATCH` results for CSRF, session, revision, and unavailable-candidate failures handled inside protected execution.
- Aligned the remaining case-sensitive source guards with the executable parity method names.
- Production REQUEST remains on `RequestUsernameRecoveryService`; production code and composition are unchanged.

###### P6-D REQUEST direct-parity guard closure
- Aligned the final `AlreadyRequested` and `CandidateNotAvailable` case-sensitive source guards with the passing executable parity method.
- Direct protected-operation behavioral tests, production code, shared START, legacy REQUEST and CANCEL composition, controllers, cookies, and persistence remain unchanged.

###### P6-D REQUEST strict replay bridge foundation
- Added the strict action-tagged REQUEST safe replay codec and transient execution bridge in parallel.
- Deterministic terminal public outcomes round-trip with approved public projection fields only; `UNKNOWN_OUTCOME`, malformed payloads, wrong actions, invalid transport, headers, and protected references fail closed.
- REQUEST uses no protected replay store. The acquired response is preserved, while completed replay restores the original public result without loading a session or invoking delivery.
- Production REQUEST remains on `RequestUsernameRecoveryService`; shared coordination and composition switch remain deferred.

###### P6-D REQUEST shared-coordinator fail-first boundary
- Added an intentionally red contract for `IdempotentRequestUsernameRecoveryService` after the protected operation and strict replay bridge passed focused validation.
- The future coordinator must use the repository-wide shared executor, established REQUEST identity and fingerprint policy, typed protected failure classification, and strict REQUEST capture and hydration bridge.
- Only `ACQUIRED` may invoke the protected operation. Completed replay must restore the original public result without delivery, while non-terminal lifecycle outcomes remain internal and map through the established REQUEST contract.
- Production REQUEST remains on `RequestUsernameRecoveryService`; shared START and legacy CANCEL remain unchanged until the dedicated atomic switch.

###### P6-D REQUEST parallel shared coordinator
- Added `IdempotentRequestUsernameRecoveryService` in parallel with the active legacy REQUEST route.
- The coordinator preserves the established session-scoped operation identity and REQUEST fingerprint fields, invokes protected execution only through shared acquisition, and uses the strict REQUEST bridge for capture and hydration.
- A sanitized internal failure carrier retains only the typed pre-dispatch or possible-side-effect boundary so the shared executor applies the correct lifecycle transition without exposing protected values.
- Completed replay restores the original public result without delivery. Production REQUEST remains on `RequestUsernameRecoveryService` pending the dedicated atomic switch.

###### P6-D REQUEST shared-coordinator guard repair
- Aligned the shared-coordinator source guard with the actual `bridge::capture` and `bridge::hydrate` method references supplied to `IdempotentExecutionCommand`.
- The parallel coordinator, typed failure classification, strict replay bridge, protected operation, production legacy REQUEST selection, shared START, and legacy CANCEL remain unchanged.

###### P6-D REQUEST shared-coordinator guard closure
- Replaced the stale explicit `FailureBoundary.POSSIBLE_SIDE_EFFECT` source assertion with the implemented conservative `policy.possibleSideEffect().classify(error)` fallback.
- The parallel coordinator, typed pre-dispatch branch, strict replay bridge, protected operation, production legacy REQUEST selection, shared START, and legacy CANCEL remain unchanged.

###### P6-D REQUEST atomic production composition switch
- Production `RequestUsernameRecoveryUseCase` now selects `IdempotentRequestUsernameRecoveryService` with the repository-wide shared executor, REQUEST integration policy, protected operation, strict replay codec and execution bridge, UTC clock, and authoritative Username Recovery session TTL.
- The active REQUEST bean no longer receives the feature-local idempotency port or legacy fingerprint generator. Those shared legacy assets remain because CANCEL still depends on the feature-local lifecycle and because REQUEST source cleanup requires a separate exact-reference review.
- Shared START and legacy CANCEL composition, controllers, public mappings, cookies, protected context, message delivery, and Redis namespaces remain unchanged. Formatting remains deferred until the P6-D stabilization boundary.

###### P6-D REQUEST production-switch guard repair
- Aligned the shared migration source guard with the formatted protected-operation delivery call, the coordinator's `protectedOperation` field name, and the replay codec's generic strict `SafePayload` round-trip.
- Production REQUEST remains selected through `IdempotentRequestUsernameRecoveryService`; shared START, legacy CANCEL, controllers, public mappings, cookies, delivery ordering, and persistence remain unchanged.

###### P6-D REQUEST production-switch guard closure
- Normalized source before checking protected-operation and coordinator ownership, narrowed lifecycle exclusions to feature-local idempotency calls, narrowed protected-field exclusions to actual field declarations, and aligned callback assertions with `bridge::capture` and `bridge::hydrate`.
- Production REQUEST remains on the shared coordinator. Shared START, legacy CANCEL, public contracts, cookies, delivery ordering, and persistence remain unchanged.

###### P6-D REQUEST authorized legacy cleanup
- Removed the non-composed `RequestUsernameRecoveryService`, its dedicated legacy lifecycle harness, the isolated legacy REQUEST fingerprint generator and test, and the old direct-session REQUEST replay codec and test after exact-reference review.
- Removed the obsolete REQUEST fingerprint bean and stale legacy coordinator architecture tokens.
- Production REQUEST remains exclusively on `IdempotentRequestUsernameRecoveryService` with the strict action-tagged replay codec. Shared START remains unchanged. Legacy CANCEL, `UsernameRecoveryIdempotencyPort`, Redis lifecycle persistence, records, key factory, CANCEL fingerprint, and CANCEL replay codec remain because CANCEL still consumes them.
- Formatting remains deferred until the P6-D stabilization boundary.

###### P6-D REQUEST feature stabilization
- Applied the single deferred Spotless pass after the shared REQUEST production switch, direct replacement coverage, and authorized legacy REQUEST cleanup.
- Production START and REQUEST remain exclusively on the repository-wide shared executor. CANCEL remains on the preserved feature-local lifecycle and Redis assets pending P6-E.
- Production compilation, test compilation, focused REQUEST/CANCEL regressions, architecture checks, and diff hygiene are required in this stabilization round.
- This stabilization does not satisfy the P6-D transition gate. The complete Maven suite and Spring application startup smoke test must pass before P6-E begins.

###### P6-D REQUEST transition-gate closure
- The complete Maven suite and local-profile Spring application startup smoke test passed on the cumulative P6-D state.
- Production START and REQUEST remain exclusively on the repository-wide shared executor. The retired legacy REQUEST coordinator, fingerprint generator, and direct-session replay codec remain absent.
- CANCEL remains on the preserved feature-local lifecycle and Redis assets for its dedicated P6-E migration.
- P6-D is complete after this cumulative change set is committed. P6-E must start only from that exact committed baseline.

###### P6-D eager-startup TransientJsonPort composition repair
- Added a conditional `TransientJsonPort` bean backed by `JacksonTransientJsonAdapter` in Username Recovery configuration so the START and REQUEST execution bridges resolve under an eager application context.
- This corrects the transition gate's earlier false-positive startup validation, which used lazy initialization and therefore did not instantiate the failing bean path.
- Shared START and REQUEST, strict replay behavior, legacy CANCEL, controllers, cookies, and persistence remain unchanged. The complete full-suite and eager-startup transition gate must be rerun before commit.

###### P6-D shared operation-identity runtime composition repair
- Added one conditional repository-wide `IdempotencyOperationIdentityFactory` bean to `SharedIdempotencyRuntimeConfig` so Username Recovery can consume the existing canonical opaque identity algorithm during eager startup.
- No flow-local factory, identity algorithm, operation ID, fingerprint byte sequence, persistence schema, START/REQUEST behavior, or legacy CANCEL behavior changed.
- Commit remains blocked until eager startup reaches the application-started marker and the final full-suite transition gate passes.

###### P6-D final transition-gate closure
- The complete Maven suite passed on the final cumulative P6-D tree after the `TransientJsonPort` and canonical `IdempotencyOperationIdentityFactory` runtime-composition repairs.
- Eager local-profile Spring startup passed with lazy initialization disabled and runtime-only redacted key material. Netty reached port 8888 and the application remained alive through the stability window.
- Production START and REQUEST remain on shared idempotency. CANCEL and its feature-local Redis lifecycle remain preserved for P6-E.
- P6-D may now be committed. P6-E must begin only from the resulting exact committed baseline.

###### P6-E CANCEL fail-first shared boundary
- Added a compile-safe, intentionally red contract for `IdempotentCancelUsernameRecoveryService`, `UsernameRecoveryCancelProtectedOperation`, its typed protected result, and strict CANCEL replay codec/execution bridge.
- The future protected operation must preserve origin and CSRF authorization, protected-context authorization, revision-aware cancellation, and original-expiry retention while owning no feature-local or shared idempotency lifecycle.
- CANCEL requires a strict safe action-tagged snapshot and no new protected replay store because its established public result contains no protected continuity.
- Production remains on `CancelUsernameRecoveryService`. Shared START and REQUEST, runtime composition, controllers, cookies, and Redis namespaces remain unchanged until the dedicated atomic switch.

####### P6-E CANCEL protected operation and replay foundation
- Added the parallel lifecycle-neutral CANCEL protected operation and typed execution result.
- Added strict action-tagged safe replay capture and hydration with no protected replay store, headers, session identity, CSRF value, or protected context in persistence.
- The protected operation preserves absent and terminal idempotent success, retained-context validation, origin and CSRF authorization, one revision-aware cancellation, original createdAt/expiresAt/data continuity, and conservative post-mutation uncertainty classification.
- Production remains on `CancelUsernameRecoveryService`; shared START and REQUEST and every legacy CANCEL lifecycle asset remain unchanged.

####### P6-E CANCEL parallel shared coordinator
- Added `IdempotentCancelUsernameRecoveryService` in parallel over the repository-wide shared executor.
- The coordinator preserves the established session-scoped operation identity and CANCEL fingerprint, invokes the protected operation only through acquisition, routes strict capture and hydration through the CANCEL bridge, and maps protected failure boundaries conservatively.
- The lifecycle-neutral protected operation now states its revision-aware responsibility explicitly. Production remains on `CancelUsernameRecoveryService`; no legacy CANCEL lifecycle asset is removed.

####### P6-E CANCEL atomic production composition switch
- Production `CancelUsernameRecoveryUseCase` now selects `IdempotentCancelUsernameRecoveryService` with the repository-wide shared executor, existing CANCEL identity/fingerprint policy, strict safe replay bridge, lifecycle-neutral protected operation, UTC clock, and authoritative Username Recovery session TTL.
- The active CANCEL bean no longer receives `UsernameRecoveryCancelFingerprintGenerator` or `UsernameRecoveryIdempotencyPort`.
- Legacy CANCEL coordinator, fingerprint generator, replay codec, feature-local port, records, Redis adapter, and tests remain intact pending a separate exact-reference cleanup audit.
- Shared START and REQUEST composition, controllers, cookies, public mappings, protected context, and Redis namespaces remain unchanged. Formatting and full-suite validation remain deferred.

####### P6-E retired feature-local lifecycle cleanup
- Removed the non-composed legacy CANCEL coordinator, isolated legacy CANCEL fingerprint and replay codecs, obsolete direct-session START replay descriptors, feature-local idempotency port/models, Redis lifecycle adapter/mapper/record/key factory, and their dedicated tests.
- Removed the obsolete feature-local lifecycle and CANCEL fingerprint Spring beans and remapped architecture/configuration guards to current shared owners.
- Preserved shared START, REQUEST, and CANCEL production routes, START protected replay persistence, strict action-tagged replay bridges, controllers, cookies, protected auth-flow context, and unrelated Redis infrastructure.
- Formatting, full Maven validation, normal local startup, and commit remain deferred to the P6-E stabilization and transition gates.

####### P6-E cleanup architecture-guard repair
- Retargeted protected session, protected-context, revision-aware cancellation, and retained-context assertions from the thin shared coordinator to `UsernameRecoveryCancelProtectedOperation`, which owns those responsibilities.
- Replaced the deleted feature-local `UsernameRecoveryIdempotencyPort` configuration expectation with the repository-wide `IdempotencyExecutor` boundary.
- Production code, the retired lifecycle deletion set, all three shared Username Recovery routes, START protected replay, controllers, cookies, and persistence behavior remain unchanged.

####### P6-E feature stabilization
- Applied the single deferred Spotless pass after the CANCEL production switch, retired feature-local lifecycle cleanup, and architecture-guard repair.
- Production START, REQUEST, and CANCEL remain exclusively composed through the repository-wide shared executor.
- The obsolete Username Recovery response-idempotency port, models, Redis lifecycle adapter, legacy CANCEL coordinator, and direct-session replay descriptors remain absent.
- START protected replay persistence, strict action-specific replay bridges, protected operations, controllers, cookies, protected context, and unrelated Redis assets remain preserved.
- This stabilization does not close P6-E. The complete Maven suite and eager local Spring startup smoke test must pass before commit or transition.

####### P6-E full-suite stale REQUEST guard repair
- Updated the retained REQUEST protected-operation preservation assertion to require the active shared CANCEL coordinator rather than the deleted legacy CANCEL coordinator.
- The full-suite failure was confined to this stale source-text expectation. Production code, shared START/REQUEST/CANCEL composition, lifecycle cleanup, formatting, controllers, cookies, protected replay, and persistence remain unchanged.
- The complete Maven suite and eager local startup gate must be rerun before commit or transition.

####### P6-E final transition-gate closure
- The complete Maven suite passed with 3,963 tests, zero failures, zero errors, and 12 skipped tests on the final cumulative P6-E tree.
- Eager local-profile Spring startup passed with lazy initialization unchanged. Netty started on port 8888, the application reached its started marker, and it remained alive through the stability window.
- Production START, REQUEST, and CANCEL are exclusively composed through the repository-wide shared idempotency executor.
- The retired Username Recovery feature-local response-idempotency lifecycle, Redis adapter, port, records, legacy CANCEL coordinator, action-local fingerprint generator, and obsolete direct-session replay descriptors remain absent.
- START protected replay persistence, strict action-specific replay bridges, lifecycle-neutral protected operations, controllers, cookies, protected auth-flow context, and unrelated Redis infrastructure remain preserved.
- P6-E is complete and may be committed with `refactor(username-recovery): migrate cancel to shared idempotency`. No deployment, environment-coverage, rollback-window, or TTL-drainage claim is made.
