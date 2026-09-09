# Username Recovery P6 Shared Idempotency Migration Design

## 1. Decision and baseline

P6 migrates the existing Username Recovery response-idempotency lifecycle at exact starting Git HEAD `49fb6adc3cab82b753b9bbd7e20c05b42fc7820f` to the repository-wide shared idempotency capability. P5 remains closed unless P6 exposes a concrete regression.

The repository proves three distinct protected HTTP actions:

- `POST /api/v1/auth/username-recovery/start`
- `POST /api/v1/auth/username-recovery/request`
- `POST /api/v1/auth/username-recovery/cancel`

All three currently require `Idempotency-Key`, have feature-local action identities, fingerprints, ownership-checked Redis lifecycle records, replay codecs, use-case orchestration, Spring composition, and behavioral tests. CANCEL therefore remains in P6 scope. This is not a new idempotency requirement.

## 2. Action matrix

### START

- Shared identity: capability `username-recovery`, action `start`.
- Scope: anonymous versioned constant. Raw identity and contact data are not an authorized scope.
- Request equivalence: preserve the current versioned canonical semantics for identity type, normalized identity value, date of birth, supplied email/mobile semantics, and captcha challenge identity only where the existing fingerprint proves it participates. Accept-Language, request ID, cookies, CSRF, origin, and referer are excluded unless current code proves semantic participation.
- Acquired ordering: validate, reserve shared lifecycle, consume one-time captcha, dispatch APIM member lookup, build protected context, create the revisioned auth-flow session, establish CSRF continuity, then capture and complete.
- Side effects: captcha consumption, APIM identity lookup, protected context creation, auth-flow session creation/update, CSRF state, and cookie instruction.
- Protected continuity: required for `SESSION_CREATED`. The existing encrypted auth-flow session contains identity, candidates, username, verified destination, session identity, CSRF hash, and future request state. The shared snapshot may carry only an opaque protected replay reference.
- Safe snapshot: original HTTP classification, public status/outcome, next step, safe scheme projections, messages, errors, expiry metadata, and allowlisted safe headers. It must not contain raw identity, date of birth, email, mobile, username, captcha values, session ID, CSRF, cookie value, Scheme-Ref secret, or downstream payload.
- Failure classification: failures proven before captcha consumption and APIM/session effects may be `PROVEN_NO_SIDE_EFFECT`; any failure after captcha consumption, APIM dispatch, or uncertain session persistence is `POSSIBLE_SIDE_EFFECT`.

### REQUEST

- Shared identity: capability `username-recovery`, action `request`.
- Scope: opaque hash of the authorized auth-flow session identity.
- Request equivalence: session-bound selected opaque Scheme-Ref using the established versioned canonical fingerprint. Raw session ID and Scheme-Ref must not enter shared persistence.
- Acquired ordering: authorize session and scenario, validate expiry/origin/referer/CSRF, load protected context, reserve the candidate with revisioned session CAS, resolve effective destination, dispatch exactly one message containing the recovered username, persist post-delivery workflow state, then complete.
- Side effects: protected-context/session reservation, destination resolution, message delivery, post-delivery session mutation, workflow completion/blocking.
- Protected continuity: required. The current replay codec directly embeds an auth-flow session identifier and must not be carried forward into the shared terminal snapshot. Replace it with an opaque protected replay reference resolved through separately protected state or authoritative encrypted session continuity.
- Safe snapshot: original HTTP classification, public status/outcome, next step, masked destination, destination type, messages, errors, and allowlisted safe headers. It must not contain username, raw destination, sender reference, auth-flow session ID, CSRF, Scheme-Ref, cookie, or message payload.
- Failure classification: deterministic validation and business outcomes are completed; proven pre-dispatch failures may be retry-safe; delivery acceptance or uncertainty, post-delivery CAS failure, and uncertain completion are `POSSIBLE_SIDE_EFFECT` and must become `UNKNOWN_OUTCOME` without blind resend.

### CANCEL

- Shared identity: capability `username-recovery`, action `cancel`.
- Scope: opaque hash of the authorized auth-flow session identity.
- Request equivalence: preserve the existing action-separated, session-bound constant/versioned semantics.
- Acquired ordering: load and authorize the session, preserve current absent/expired/already-cancelled/terminal idempotent-success behavior, validate origin/referer/CSRF where required, validate retained protected context, perform revision-aware cancellation, verify retained protected state and original expiry, then complete.
- Side effects: revision-aware session cancellation and auth-flow cookie clearing instruction.
- Protected continuity: no new replay store is required for the successful public body. Existing encrypted context remains workflow state until original session TTL expiry and is not response-idempotency infrastructure.
- Safe snapshot: original HTTP classification, public status/outcome, `RETURN_TO_LOGIN`, messages, errors, and allowlisted safe headers. Cookie values and session identity are excluded.
- Failure classification: failures before cancellation is attempted may be proven no-side-effect only with direct evidence; uncertain revisioned cancellation or completion-persistence failure must fail closed and cannot execute cancellation again.

## 3. Terminal classification

Every deterministic public result, including validation failure, member-not-found, registration-required, invalid workflow/session/CSRF/Scheme-Ref, already-requested, completed-workflow, known delivery rejection, absent/expired/already-cancelled terminal cancellation, and successful outcomes, is completed and replayable when it occurs inside the acquired boundary.

`COMPLETED_REPLAY` is internal lifecycle metadata. Hydration restores the original public result and never maps replay to public success. `DUPLICATE_IN_PROGRESS`, key reuse, failed-existing, unknown-existing, corrupt snapshot, unsupported snapshot version, and protected-state resolution failure remain protocol or fail-closed outcomes and are not persisted as replacement business success.

## 4. Compatibility policy

The feature-local namespace is structurally separate from the shared HMAC namespace:

- legacy: `{redis-cache.key-prefix}:username-recovery:idempotency:{username-recovery-start|username-recovery-request|username-recovery-cancel}:{client-key}`
- shared: `{redis-cache.key-prefix}:idempotency:v1:{HMAC-derived-identity}`

The audit found session TTL configuration of 600 seconds and legacy transitions that preserve the existing record TTL. Before P6-F, the exact idempotency TTL source and maximum effective legacy TTL must be proven from production composition and configuration.

Because P6 begins before Username Recovery shared migration deployment, the default repository policy is namespace isolation with one active writer per action and no dual-write. Legacy records are not interpreted by the shared reader. If rollout evidence later proves an overlap requiring replay, add only a bounded read-only compatibility reader. Ambiguous, corrupt, expired, or unsupported legacy records fail closed. No Redis flush or broad key deletion is permitted.

## 5. Stale-component inventory

After all three production consumers use the shared contract and compatibility gates pass, remove the feature-local response-idempotency lifecycle:

- `UsernameRecoveryIdempotencyPort` and its reserve/complete/unknown commands;
- action, key, fingerprint, operation-reference, reservation, outcome, record, status, and replay-snapshot lifecycle DTOs that have no remaining non-legacy consumer;
- `RedisUsernameRecoveryIdempotencyAdapter`, JSON mapper, wire record, key factory, and Lua transition resource;
- feature-local operation-reference generation and duplicated reserve/resolve/complete/unknown orchestration in START, REQUEST, and CANCEL;
- obsolete fingerprint generators and replay codecs after their useful versioned concepts are represented through the shared integration contract;
- Spring beans creating the feature-local adapter;
- tests and architecture guards that assert the retired lifecycle rather than replacement behavior.

Retain the AES-GCM Username Recovery auth-flow context protector, encrypted context binding, revisioned auth-flow session state, destination resolution, message delivery, controller policies, and protected workflow tests. These are workflow/security facilities, not duplicate response-idempotency infrastructure.

## 6. Acceptance matrix

For each action and each deterministic terminal result:

- first HTTP status equals replay HTTP status;
- first public response body equals replay public response body;
- original public status, next step, messages, errors, and approved safe headers are identical;
- protected operation invocation count is exactly one;
- every externally visible side-effect invocation count is exactly one.

Cumulative required scenarios:

- same key with a different fingerprint returns key-reuse conflict and invokes no protected operation;
- concurrent duplicates invoke the protected operation once;
- failure before the first protected effect follows the explicitly proven retry-safe classification;
- failure after dispatch or possible acceptance becomes unknown outcome and never blindly retries;
- response-capture and completion-persistence failures fail closed and never re-execute;
- corrupt and unsupported-version snapshots fail closed;
- unsafe headers are rejected;
- required protected replay state missing, malformed, expired, or unavailable fails closed;
- legacy namespace isolation or bounded compatibility is proven;
- expiry permits key reuse only after authoritative expiry;
- cancellation replay preserves current revision behavior and does not repeat cancellation or cleanup;
- captcha consumption, APIM lookup, identity verification, username reveal/message delivery, session mutation, CSRF mutation, cancellation, and cleanup are never repeated.

## 7. Validated delivery sequence

- P6-A: this evidence-backed design, action matrix, compatibility policy, stale inventory, acceptance matrix, and handover baseline.
- P6-B: Username Recovery shared identity/fingerprint/classifier policy, strict action-tagged response envelope, typed transient boundaries, safe codecs, protected-reference contracts, and replay hydration. No production switch.
- P6-C: START protected-operation extraction, protected replay continuity, shared coordinator, tests, atomic composition switch, and exact legacy START coordinator cleanup.
- P6-D: REQUEST protected-operation extraction preserving session-first/no-blind-resend ordering, protected replay resolution, shared coordinator, tests, atomic switch, and exact legacy REQUEST coordinator cleanup.
- P6-E: CANCEL protected-operation extraction, shared coordinator, revision-aware replay tests, atomic switch, and exact legacy CANCEL coordinator cleanup.
- P6-F: prove compatibility closure and delete the remaining feature-local response-idempotency port, Redis adapter, records, codecs, key factory, Lua resource, Spring beans, and obsolete guards.
- P6-G: one Spotless pass, compile, test-compile, focused behavioral/concurrency/corruption/compatibility/architecture validation, full Maven suite gate, normal Spring startup gate, documentation closure, and final commit.

A full Maven suite and successful Spring startup are mandatory before every major implementation-stage transition. Intermediate rounds run only `mvn -DskipTests compile` and `mvn -DskipTests test-compile` and defer `spotless:apply` until P6-G.

## P6-B shared integration contracts

P6-B now provides Username Recovery-owned action, operation-identity, compatibility fingerprint, constant classifier, and strict response-envelope contracts over the repository-wide shared foundation. The fingerprint compatibility layer deliberately preserves the established decimal-length-plus-NUL byte sequence for START, REQUEST, and CANCEL while exposing version 1 shared fingerprints. No coordinator, replay codec, protected replay persistence, Spring bean, or production route changes in this increment.


### P6-C Username Recovery START replay foundation

- Added a strict START safe snapshot codec and transient execution bridge without changing production routing.
- SESSION_CREATED persists only an opaque ProtectedReplayReference; auth-flow session identity, CSRF, Scheme-Refs, and protected continuity remain outside the shared safe body.
- MEMBER_NOT_FOUND and ZERO_REGISTERED preserve deterministic replay-safe public fields without protected storage.
- Protected persistence adapters, shared coordinator, Spring composition, and production switching remain deferred.

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
