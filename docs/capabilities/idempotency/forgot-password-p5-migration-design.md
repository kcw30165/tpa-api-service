# Forgot Password P5 Shared Idempotency Migration Design

## Status and baseline

This document records the P5-B design derived from the completed P5-A investigation. The approved repository baseline is branch `fix/relay-same-error-code-for-the-idempotent-request` at exact Git HEAD `7b06f5b7d94a6ebad0d04b1f22ed40552c48238a`.

P4 repository validation completed with 4,077 tests, 0 failures, 0 errors, and 10 skipped. The shared Login migration has not been deployed. Deployment timestamps, environment coverage, rollback-window closure, and TTL-drainage gates are therefore not applicable.

The controlling checklist remains P5-A/B/C/D/E.

## P5-A investigation outcome

The shared-executor migration scope is:

1. `START`
2. `DETAILS_AND_OTP`
3. `OTP_RESEND`
4. `OTP_VERIFY`
5. `COMPLETE`

`CANCEL` is not a separate idempotent execution. It remains revision-aware workflow cancellation and cleanup. Its tests and architecture guard must confirm that cancellation does not introduce feature-local idempotency lifecycle behavior.

The legacy Forgot Password record currently combines generic idempotency lifecycle data with feature-specific opaque commit references. P5 must separate those concerns. Shared idempotency persistence may carry only the context-neutral operation identity, fingerprint, reservation ownership, lifecycle status, and terminal response snapshot. Opaque commit references remain in protected Forgot Password workflow or commit state.

## P5-B target design

### Shared operation identity

All migrated actions use:

- capability: `forgot-password`
- action: stable action-specific value
- scope: SHA-256 of the authorized raw workflow scope
- key: trimmed client `Idempotency-Key`

The raw auth-flow session identifier, username, destination, authorization identifier, and opaque commit reference must never be persisted as the shared operation scope.

Action values are:

- `start`
- `details-and-otp`
- `otp-resend`
- `otp-verify`
- `complete`

For `START`, the authorized raw workflow scope must preserve the current pre-session semantics. The identity factory must use a stable protected scope derived from the request context without exposing the normalized username in Redis keys or logs. This mapping must be proven by fail-first tests before production wiring.

For all post-start actions, the authorized raw workflow scope is the current Forgot Password auth-flow session identifier, hashed by the feature-owned identity factory before reaching shared persistence.

### Fingerprints

Fingerprints remain action-specific and versioned. The migration must preserve current key-reuse decisions:

- `START`: action discriminator plus normalized username, versioned without storing the username.
- `DETAILS_AND_OTP`: the current normalized details payload and action discriminator.
- `OTP_RESEND`: the current constant operation fingerprint where request semantics contain no varying business payload.
- `OTP_VERIFY`: the current constant operation fingerprint. The OTP value must not be included or persisted.
- `COMPLETE`: the current constant operation fingerprint. Password material must never be included or persisted.

All fingerprints are SHA-256 values produced by a Forgot Password-owned factory. Raw personal data, OTP values, password values, CSRF tokens, cookies, authorization data, and opaque references are excluded from shared snapshots and logs.

### Execution structure

Each migrated action uses a thin shared-idempotency coordinator implementing the existing input port. The existing service becomes the protected operation or is split behind an action-specific protected-operation boundary where required to prevent nested legacy reservation.

The coordinator supplies:

- shared operation identity;
- versioned fingerprint;
- existing configured TTL;
- protected operation;
- conservative failure classifier;
- action-specific response capture;
- action-specific replay hydration.

Production composition switches one action at a time. No action may use both the legacy Forgot Password port and the shared executor in the same runtime path.

### Failure classification

Classification is evidence-based and conservative:

- a failure proven to occur before any downstream dispatch or state mutation may become `FAILED`;
- a failure after a possible OTP delivery, APIM request, session mutation, password reset dispatch, or uncertain commit becomes `UNKNOWN_OUTCOME`;
- a completed business result that is intentionally replayable is captured as `COMPLETED` with an approved terminal snapshot;
- persistence or hydration uncertainty must fail closed and must not trigger blind resend or repeat password-reset dispatch.

Action implementations must not reuse a generic classifier merely because another capability uses it. Tests must prove each pre-dispatch and possible-side-effect boundary.

### Replay and protected references

`COMPLETED_REPLAY` remains an internal lifecycle outcome. Existing public mapper behavior is preserved:

- Start replay follows the existing successful start response and cookie behavior.
- Details and OTP replay resolves the protected commit reference and reconstructs the existing successful response without sending another OTP.
- OTP resend replay resolves protected commit state and does not resend.
- OTP verify replay resolves protected authorization or workflow state and does not consume the OTP again.
- Complete replay reconstructs the existing terminal business result and never invokes reset-password dispatch again.

Opaque references are represented only by the shared snapshot protected-replay-reference facility or by protected workflow state, according to the action bridge. They must not be added as feature-specific fields to the shared record.

Blocked headers and values remain excluded from snapshots, including cookies, authorization headers, CSRF material, bearer values, and auth-flow cookie values.

### Public contract preservation

HTTP routes, required headers, cookies, response envelopes, error targets, `Accept-Language`, and request-correlation behavior remain unchanged.

The migration must preserve current public mapping for duplicate-in-progress, key reuse, prior failure, unknown outcome, business rejection, and system failure. Recognized business rejections retain the approved public business status. Technical and uncertain failures map to `SYSTEM_ERROR`. `ApiStatus.DOWNSTREAM_REJECTED` must never be exposed to the frontend.

### Configuration and persistence

Existing action TTL configuration remains authoritative during migration. P5 does not introduce new hardcoded TTLs.

Shared Redis keys use the shared key factory and persistence ID derivation. Legacy Forgot Password keys are not read by the new shared executor. Because no shared Login deployment exists, deployment coexistence, TTL drainage, and rollback windows remain not applicable at this stage. Repository cleanup occurs only after every P5 action is migrated and validated.

## P5-B acceptance matrix

### Global acceptance

- Shared identity is opaque and action-isolated.
- Fingerprints preserve existing key-reuse semantics without secret or personal-data persistence.
- Reservation ownership is enforced by the shared executor.
- Completed replay does not repeat side effects.
- Unknown outcomes fail closed.
- Public HTTP and mapper contracts remain unchanged.
- Every implementation stage updates documents under `docs/capabilities/idempotency`.
- Compile and test-compile pass after each deterministic implementation stage.

### START

- Shared coordinator handles all reservation outcomes.
- Existing username normalization and anti-enumeration behavior are preserved.
- Completed replay restores the expected workflow response and cookie behavior.
- No raw username or session identifier appears in shared keys, snapshots, or logs.

### DETAILS_AND_OTP

- The details fingerprint retains current normalized-request semantics.
- OTP delivery is protected from duplicate dispatch.
- Pending and committed opaque references live only in protected commit/workflow state.
- Replay returns the existing successful details-and-OTP response without another send.

### OTP_RESEND

- Cooldown, resend limits, committed replay, and unknown-outcome blocking are preserved.
- Duplicate execution cannot send another OTP.
- Protected commit-state hydration remains revision-aware.

### OTP_VERIFY

- OTP material is excluded from fingerprint and persistence.
- Successful replay does not consume or verify the OTP again.
- Authorization replay uses protected state and preserves current public routing.

### COMPLETE

- Password material is excluded from fingerprint and persistence.
- Pre-dispatch business validation and post-dispatch uncertainty are classified separately.
- Completed success and approved business-result replay never repeat downstream password-reset dispatch.
- Session `UNKNOWN_OUTCOME_BLOCKED` behavior remains aligned with idempotency unknown outcome.

### CANCEL and cleanup

- Cancel remains outside shared execution.
- Cancellation continues to invalidate active OTP state and clear workflow state as currently defined.
- The legacy `CANCEL` enum value disappears only when the complete feature-local idempotency contract is removed.

## P5-C cumulative implementation status
The repository has one shared idempotency foundation. Forgot Password contributes only action-specific integration policy and adapters; it does not define a second foundation. Login and Registration retain compatibility APIs that delegate identity, fingerprint, and constant-classifier mechanics to the same central foundation; their established persisted values remain unchanged. Their action-tagged response-envelope compatibility APIs also delegate the generic `idempotencyAction` and `payload` wire protocol to the central foundation while retaining flow-specific action mapping and diagnostics.


- Integration foundation increment: retained Forgot Password action and policy inputs while centralizing opaque identity, canonical fingerprint, constant classifier, and action-tagged envelope mechanics in the single repository-wide shared foundation.
- START protected-replay foundation: added the protected state, storage boundary, and secure opaque-reference generator; no adapter or production composition switch yet.
- START protected persistence: added AES-GCM protected Redis persistence, reference-bound authentication, strict JSON mapping, and conditional runtime configuration; production composition remains unchanged.
- START codec and bridge: added strict safe-snapshot capture, separately protected continuity hydration, fresh-CSRF recreation, transient conversion, and fail-closed unit coverage; production composition remains unchanged.
- Production composition: unchanged. All five actions continue to use the legacy runtime path until their individual migration stages.
- CANCEL: remains outside shared execution.
- Protected references: remain outside the shared lifecycle record and are handled by action-specific protected replay integrations.

## P5-C implementation sequence

1. Add Forgot Password action-specific integration policy, protected replay, response capture, and replay hydration over the repository-wide shared idempotency foundation with fail-first tests.
2. Migrate `START` using parallel composition and contract tests.
3. Migrate `DETAILS_AND_OTP`, including protected opaque commit-state separation.
4. Migrate `OTP_RESEND`, preserving no-blind-resend and committed replay behavior.
5. Migrate `OTP_VERIFY`, preserving protected authorization replay.
6. Migrate `COMPLETE`, preserving pre-dispatch classification and downstream uncertainty handling.
7. Switch production composition cumulatively and add cross-action namespace and replay architecture guards.
8. Remove Forgot Password-specific idempotency port, DTOs, Redis adapter, serializer, key factory, lifecycle enums, wiring, and obsolete tests only after replacement coverage is proven.
9. Run feature stabilization, compile, test-compile, targeted tests as separately authorized, and repository validation.

## P5-D validation evidence

Each implementation stage must create a timestamped directory under `target/evidence`, capture pre-state, intentionally deferred formatting where applicable, compile, test-compile, audits, diff checks, exit codes, final status, diff stat, and name-status. Full Maven output is not duplicated into evidence files. Failure diagnostics are retained only when needed.

## P5-E closure

P5 closes only when all five scoped actions use shared idempotency in production composition, protected references are absent from shared persistence, the feature-local idempotency infrastructure is removed, documentation is current, repository validation passes, and the working tree is clean.

Until an actual deployment occurs, deployment timestamps, environment coverage, rollback-window closure, and TTL-drainage gates remain not applicable.

### P5-C Forgot Password START cleanup status

- START production composition uses `IdempotentStartForgotPasswordService` over the repository-wide shared executor. Replacement parity is committed, and the non-composed legacy `StartForgotPasswordService` and its dedicated test are removed.

### P5-C full-test checkpoint repair status

- The START replay protector registers Java time support before serializing protected continuity state.
- Expired protected replay remains fail-closed, and reference substitution remains authenticated through AES-GCM additional data.
- Production composition continues to reject the exact legacy START constructor while allowing the shared replacement service.

### P5-D committed-baseline validation evidence

- Validated exact committed baseline `b88e2e5d534fd39895db07e73e4a73145a67dd69`.
- Production compile, test compilation, focused shared-boundary tests, replay parity tests, composition guards, and clean-architecture validation passed.
- Static audits confirm START and DETAILS_AND_OTP use the shared executor, DETAILS_AND_OTP legacy orchestration is absent, and resend, verify, complete, and cancel remain composed without accidental cleanup.
- The P5-C transition evidence already records a passing 4,123-test full Maven suite and a passing Spring startup smoke.

### P5-C OTP_RESEND fail-first boundary

- Selected `OTP_RESEND` as the next incomplete action after the P5-E closure-readiness review.
- Added a fail-first source boundary for the shared coordinator, protected operation, typed response bridge, replay codec, and protected replay resolver.
- The boundary requires repository-wide `IdempotencyExecutor` ownership while preserving no-blind-resend, separately protected commit-state replay, and the existing production composition until replacement parity is proven.
- START, DETAILS_AND_OTP, OTP_VERIFY, COMPLETE, and CANCEL remain unchanged.

### P5-C OTP_RESEND replay bridge foundation

- Added the typed transient response bridge, strict action-tagged safe snapshot codec, protected replay port, and adapter over the existing protected OTP commit-state resolver.
- Safe shared persistence contains only the OTP_RESEND action and public status. Masked destination and timing continuity are hydrated through the opaque protected replay reference.
- Production composition, the legacy resend service, and all other action composition remain unchanged.

### P5-C OTP_RESEND protected-operation parity boundary

- Added fail-first source-level parity coverage for lock-scoped mutation ownership, accepted-delivery protected commit persistence, deterministic delivery failure, post-dispatch unknown-outcome signaling, and the protected execution Result invariant.
- The test forbids delegation to the legacy service and forbids legacy idempotency lifecycle types inside the extracted operation.
- Production composition remains unchanged.

### P5-C OTP_RESEND protected Result contract

- Added `ForgotPasswordOtpResendProtectedExecutionResult` as the typed boundary between the acquired protected mutation and the shared coordinator.
- The contract requires an opaque protected replay reference exactly when the protected result is `RESENT`; every other result must omit it.
- The protected operation and shared coordinator remain intentionally absent, and production composition is unchanged.

### P5-C OTP_RESEND protected-operation extraction

- Extracted direct lock-scoped OTP_RESEND mutation ownership into `ForgotPasswordOtpResendProtectedOperation`.
- The extracted operation excludes the feature-local lifecycle port and persists accepted protected commit state directly before returning its opaque replay reference.
- Production composition remains unchanged pending shared-coordinator and behavioral parity completion.

### P5-C OTP_RESEND protected-operation compile repair

- Corrected the extracted operation error fallback to return `ForgotPasswordOtpResendProtectedExecutionResult` rather than mixing the raw resend result into the reactive branch.
- This preserves the typed protected-operation boundary and keeps production composition unchanged.

### P5-C OTP_RESEND protected-operation branch type repair

- Wrapped the non-eligible `mapEligibility` branch in `ForgotPasswordOtpResendProtectedExecutionResult`, eliminating the remaining mixed reactive branch type while preserving the typed error fallback.
- Production composition remains unchanged.

### P5-C OTP_RESEND shared-coordinator fail-first boundary

- Added fail-first coordinator coverage for shared executor ownership, session-scoped operation identity, constant OTP_RESEND fingerprinting, conservative failure classification, protected-reference propagation, replay hydration, and all shared lifecycle routes.
- The production bean remains on the legacy service until the coordinator implementation and focused parity validation are green.

### P5-C OTP_RESEND shared coordinator implementation

- Added `IdempotentResendForgotPasswordOtpService` as the thin shared-executor coordinator.
- It uses session-scoped OTP_RESEND identity, the constant action fingerprint, conservative failure classification, typed protected execution, protected-reference capture, replay hydration, and explicit lifecycle routing.
- Production composition remains on the legacy service until configuration-switch parity is validated.

### P5-C OTP_RESEND production composition switch

- Replaced only the production `ResendForgotPasswordOtpUseCase` bean graph with `IdempotentResendForgotPasswordOtpService`, the extracted protected operation, typed response bridge, strict replay codec, and protected replay resolver.
- START, DETAILS_AND_OTP, OTP_VERIFY, COMPLETE, CANCEL, web contracts, and public status mapping remain unchanged.
- The legacy resend service and feature-local collaborators remain available until post-switch reference and parity evidence authorizes cleanup.

### P5-C OTP_RESEND production-switch assertion repair

- Repaired the shared composition architecture assertion to verify the selected coordinator independently of Java continuation indentation.
- Production composition and runtime behavior are unchanged.

### P5-C OTP_RESEND authorized legacy cleanup

- Removed the retired `ResendForgotPasswordOtpService`, its feature-local lifecycle classifier, request-hash generator, obsolete configuration bean, and two legacy-owned tests after exact-symbol analysis found no live production references.
- Retargeted the reactive architecture guard to the extracted protected operation and updated the DETAILS_AND_OTP composition guard to require the shared OTP_RESEND coordinator.
- Preserved the shared coordinator graph and all cross-action idempotency, protected replay, commit-state, OTP_VERIFY, COMPLETE, CANCEL, START, and DETAILS_AND_OTP collaborators.

### P5-C OTP_RESEND feature stabilization

- Applied repository formatting after the shared production switch and authorized legacy cleanup were complete.
- Revalidated production compilation, test compilation, the complete focused OTP_RESEND regression set, retained cross-action guards, and clean architecture.
- Full Maven tests and application startup smoke remain explicit stage-transition gates and are not replaced by this stabilization evidence.

### P5-C OTP_RESEND stabilization assertion repair

- Repaired the coordinator source assertion to tolerate Spotless line wrapping while continuing to require the exact protected-operation invocation.
- Production code, shared composition, protected replay handling, and legacy cleanup remain unchanged.

### P5-C OTP_VERIFY fail-first shared boundary

- Added fail-first architecture evidence requiring a shared OTP_VERIFY coordinator, extracted protected operation/result, typed response bridge, and strict replay codec.
- The boundary requires the repository-wide executor, canonical constant OTP_VERIFY fingerprint, conservative possible-side-effect classification, and removal of feature-local lifecycle calls from the future coordinator.
- Replay persistence is limited to the public result fields and must exclude OTP, CSRF, transport, challenge, reset-subject, authorization, and destination data.
- At that checkpoint, production composition remained on the legacy OTP verification service until implementation and parity evidence passed.

### P5-C OTP_VERIFY replay foundation

- Added the typed OTP_VERIFY execution bridge and strict action-tagged replay codec.
- Safe replay contains only status, nextStep, and remainingVerifyAttempts. OTP, CSRF, transport identifiers, challenge state, reset-subject data, authorization identifiers, and protected destination data are excluded.
- Reset authorization remains exclusively in protected server-side session state. Production composition and feature-local lifecycle ownership remain unchanged.

### P5-C OTP_VERIFY exact sensitive-token guard

- Corrected the replay safety architecture assertion to match forbidden Java identifiers exactly. This prevents the forbidden identifier `code` from falsely matching safe framework terms such as `codec`, `decodePayload`, and `encode`.
- The safe replay contract remains unchanged and still excludes the exact OTP field identifier and all other protected or transport identifiers.

### P5-C OTP_VERIFY protected operation

- Extracted the lock-scoped OTP verification and reset-authorization mutation into a lifecycle-free protected operation and result wrapper.
- Preserved all public outcomes, remaining-attempt semantics, revision-aware updates, reset-subject backfill, and server-side-only authorization. Production composition remains unchanged.

### P5-C OTP_VERIFY protected-operation compile repair

- Removed the stale feature-local replay snapshot helper left behind during protected-operation extraction.
- The protected operation now contains only lock-scoped OTP verification and reset-authorization mutation logic. Production composition remains on the legacy service.

### P5-C OTP_VERIFY authorization-reference compile repair

- Restored the local opaque reset-authorization reference generator required by the extracted protected operation.
- The generator creates only the server-side authorization identifier and does not restore any feature-local replay or idempotency lifecycle ownership. Production composition remains unchanged.

### P5-C OTP_VERIFY shared coordinator

- Added the thin OTP_VERIFY coordinator over the repository-wide shared idempotency executor.
- It uses the OTP_VERIFY action, session-scoped operation identity, canonical constant fingerprint, command TTL, conservative possible-side-effect classifier, typed execution bridge, and no protected replay reference.
- Existing reservation outcomes map to the established public OTP_VERIFY statuses. Production composition remains on the legacy service until the separate switch increment.

### P5-C OTP_VERIFY production switch

- Switched the OTP_VERIFY production bean to `IdempotentVerifyForgotPasswordOtpService`.
- Composition now constructs the lifecycle-free protected operation, strict replay codec, typed execution bridge, canonical integration policy, and shared coordinator.
- Removed the feature-local idempotency port from the OTP_VERIFY bean boundary. The legacy service source remains temporarily for the exact-symbol cleanup audit.

### P5-C OTP_VERIFY composition-test compile repair

- Replaced the malformed composition architecture test with complete compilable source.
- The production switch, shared coordinator wiring, lifecycle-port removal, and temporary legacy-source retention remain unchanged.

### P5-C OTP_VERIFY canonical-fingerprint guard repair

- Replaced the stale legacy SHA-256 source assertion with checks against the shared coordinator and canonical integration policy.
- The guard now verifies that OTP_VERIFY uses `policy.otpVerify()` with the constant `v1` field and excludes OTP, CSRF, request, and session identity from fingerprint inputs.

### P5-C OTP_VERIFY hash-guard compile correction

- Corrected malformed nested Java string quoting in the canonical-fingerprint architecture guard.
- The replacement retains structural assertions for the OTP_VERIFY action, `policy.otpVerify()`, canonical field construction, and exclusion of sensitive command inputs.

### P5-C OTP_VERIFY legacy cleanup

- Removed the retired feature-local OTP verification lifecycle service and its obsolete unit test after the shared coordinator production switch.
- Replaced stale negative class-name assertions with positive shared-composition assertions.
- The shared executor, protected operation, replay codec, response bridge, public mapping, and cross-action behavior remain unchanged.

### P5-C COMPLETE fail-first shared boundary

- Added fail-first architecture evidence requiring `IdempotentCompleteForgotPasswordService`, an idempotency-neutral protected operation/result, and strict typed replay bridge/codec assets.
- The future coordinator must use the repository-wide `IdempotencyExecutor`, action `COMPLETE`, and `policy.complete()` constant `v1` fingerprint without password, confirmation, CSRF, request, or session material.
- The protected operation owns validation, authorization, APIM password-reset dispatch, and revision-aware terminal session mutation, but it must not own reservation, completion, failure, or unknown-outcome persistence.
- Deterministic success and recognized business rejection results are replayable. Any possible APIM dispatch or terminal session-mutation uncertainty remains unknown outcome and fails closed.
- Production composition remains on the legacy COMPLETE service until protected-operation parity, replay safety, coordinator behavior, and composition-switch evidence pass.

### P5-C COMPLETE fail-first compile correction

- Corrected malformed nested Java string quoting in the COMPLETE fail-first architecture boundary.
- The replacement preserves the same required coordinator, protected operation/result, strict replay boundary, constant fingerprint, sensitive-input exclusions, and unchanged production composition.

### P5-C COMPLETE replay bridge foundation

- Added the strict COMPLETE replay codec and typed transient execution bridge using the actual shared response APIs.
- Snapshots preserve the existing public result fields and reject unknown outcome, action mismatch, malformed payloads, unsafe headers, and protected references.
- Production composition remains unchanged.

### P5-C COMPLETE replay bridge focused coverage

- Added focused coverage for successful COMPLETE replay, recognized business-rejection replay, both cookie-cleanup flags, action mismatch, unknown fields, unknown-outcome exclusion, and protected-reference rejection.
- Replay payload assertions prohibit password, confirmation, CSRF, session identity, authorization identifiers, and reset subject identifiers. Production composition remains unchanged.

### P5-C COMPLETE protected-operation foundation

- Added a redacted transient input binding the command, authorized protected context, and revisioned session snapshot.
- Added an idempotency-neutral execution result with explicit terminal, technical-failure, and unknown-outcome classifications.
- Terminal classification rejects `SYSTEM_FAILED` and `UNKNOWN_OUTCOME`, preventing lifecycle uncertainty from being persisted as replayable success.
- Production composition and the legacy COMPLETE behavior remain unchanged.

### P5-C COMPLETE protected-operation fail-first parity boundary

- Added an intentionally red extraction boundary requiring the COMPLETE protected operation to own session locking, request and authorization validation, password validation, APIM dispatch classification, and revision-aware terminal session mutation.
- The operation must return explicit terminal, technical-failure, or unknown-outcome execution classification and must contain no idempotency lifecycle, blocking, subscription, or protected-data logging.
- Production composition remains unchanged until the boundary is implemented and focused parity tests pass.

### P5-C COMPLETE protected-operation implementation

- Added the idempotency-neutral COMPLETE protected operation from the exact audited legacy method bodies and contracts.
- The operation owns lock handling, authorization and password validation, APIM dispatch classification, authorization consumption, revision-aware terminal session mutation, and unknown-outcome blocking.
- It owns no reservation, completion, failure, replay, fingerprint, or shared-executor lifecycle. Production composition remains unchanged.

### P5-C COMPLETE protected-operation parity correction

- Made `ForgotPasswordCompletionResult.Status.RESET` and `REJECTED` explicit protected-operation dispatch classifications.
- Unknown outcome and technical failure remain conservatively separated, while only RESET enters authorization consumption and successful terminal session mutation.
- Production composition remains on the legacy COMPLETE service.

### P5-C COMPLETE protected-operation behavior guards

- Added executable preservation guards for successful authorization consumption and completion, recognized password and member-not-found rejections, unknown-outcome session blocking, lock lifecycle, and trusted actor-role propagation.
- The guards also prohibit idempotency lifecycle ownership, blocking, subscription, and sensitive diagnostic logging inside the protected operation.
- Production composition remains unchanged.

### P5-C COMPLETE behavior-guard assertion correction

- Corrected the success behavior guard to match the actual immutable authorization-consumption implementation: a new `ForgotPasswordResetAuthorization` is constructed with the current completion time and applied through `withResetAuthorization(consumed)`.
- No production behavior or production composition changed.

### P5-C COMPLETE shared-coordinator fail-first boundary

- Added an intentionally red boundary for the thin COMPLETE coordinator over the repository-wide shared executor.
- The coordinator must use session-scoped operation identity, the constant COMPLETE fingerprint, command TTL, conservative possible-side-effect classification, the typed replay bridge, and classified protected execution.
- Existing lifecycle outcomes must preserve public statuses without invoking the protected operation. Password, confirmation, CSRF, and legacy idempotency persistence are prohibited from the coordinator.
- Production composition remains unchanged.

### P5-C COMPLETE shared-coordinator implementation

- Added the thin COMPLETE coordinator over the repository-wide shared executor using session-scoped identity, the constant COMPLETE fingerprint, command TTL, conservative possible-side-effect classification, typed response capture/hydration, and classified protected execution.
- Legacy idempotency persistence and sensitive command fields remain outside the coordinator. Production composition remains unchanged.

### P5-C COMPLETE shared-coordinator guard correction

- Corrected the structural guard to recognize the fluent `return operation` followed by `.execute(...)` invocation used by the coordinator.
- Coordinator implementation, protected execution routing, and production composition remain unchanged.

### P5-C COMPLETE shared-coordinator lifecycle coverage

- Added executable coordinator coverage for session-scoped identity, constant password-free fingerprinting, command TTL, conservative failure classification, revisioned session loading, protected execution routing, terminal replay hydration, and every existing lifecycle outcome.
- The coordinator is guarded from legacy persistence, direct downstream side effects, blocking, subscription, and sensitive fingerprint inputs. Production composition remains unchanged.

### P5-C COMPLETE production composition switch

- Switched the Spring COMPLETE bean from `CompleteForgotPasswordService` to `IdempotentCompleteForgotPasswordService`.
- Production composition now constructs the idempotency-neutral protected operation, strict replay codec, typed execution bridge, and shared integration policy over the repository-wide executor.
- The legacy COMPLETE service is no longer instantiated by production configuration.

### P5-C COMPLETE stabilization

- Applied feature-level Spotless formatting after the COMPLETE implementation and production composition switch.
- Production compile and test compilation pass after formatting.
- Spring production composition uses the shared COMPLETE coordinator, protected operation, strict replay codec, typed bridge, constant fingerprint policy, and repository-wide executor.
- Full Maven tests and Spring startup smoke remain mandatory transition gates before P5-C may hand over to P5-D.

### P5-C transition-gate guard correction

- Corrected the Spotless-sensitive lock-acquisition guard without changing production code.
- The full Maven suite and Spring startup smoke are rerun by this repair.

### P5-C COMPLETE transition-gate closure

- The full Maven suite passed with 4,169 tests, zero failures, zero errors, and 10 skipped.
- The Spring application completed context initialization, started Netty on port 8888, and emitted the successful application-start marker.
- The subsequent local shutdown was caused only by the post-start auth-flow persistence compatibility runner being unable to reach the deployment Redis Sentinel; it was not a bean, wiring, or configuration startup failure.
- P5-C is complete and may transition to P5-D.

### P5-D feature-local lifecycle cleanup

- Removed the retired Forgot Password lifecycle port, COMPLETE legacy coordinator, lifecycle Redis adapter and records, lifecycle-coupled OTP commit coordinator, and their owned tests.
- Retained the separately protected OTP commit-state port, Redis adapter, replay resolver, session binding, and all shared-executor coordinators.
- Retargeted the stale DETAILS_AND_OTP cross-action composition guard to the shared COMPLETE coordinator.

### P5-D unused lifecycle DTO cleanup

- Removed only the seven lifecycle command/result DTOs proven by the exact-symbol audit to have no external references after feature-local lifecycle removal.
- Retained the public idempotency key and replay snapshot contracts, shared action integration policy, protected OTP commit state, and all shared production routes.
- Further DTO cleanup remains gated by a fresh exact-symbol audit because removal of these leaves may expose additional candidates.

### P5-D residual lifecycle DTO cluster cleanup

- Removed the isolated legacy lifecycle action, record, start-outcome, status, and request-hash DTO cluster after the fresh exact-symbol closure audit proved that only obsolete lifecycle tests referenced it.
- Removed those obsolete tests and retargeted the remaining protected-operation guard.
- Retained the active public idempotency key and replay snapshot, the capability-owned shared action policy, protected OTP commit state, and all shared production routes.

### P5-D cleanup stabilization

- Applied deferred Spotless formatting after lifecycle infrastructure, isolated lifecycle DTOs, and obsolete owned tests were removed.
- Revalidated compile, test compilation, shared coordinator and replay coverage, protected-operation guards, CANCEL separation, Redis protected commit-state retention, and clean architecture.
- Full Maven and Spring startup gates remain required before the P5-D cleanup commit.

### P5-D transition-gate architecture guard correction

- Retargeted the stale positive assertion for the removed lifecycle commit coordinator to the shared details-and-OTP coordinator and retained protected OTP commit-state port. Production code remains unchanged.

### P5-D transition-gate closure

- The cumulative cleanup passed the full Maven suite with 4,148 tests, zero failures, zero errors, and 10 skipped.
- Spring context initialization and Netty startup completed successfully with the application-start marker. The later local compatibility-runner failure was limited to unavailable deployment Redis Sentinel connectivity after startup.
- P5-D removed the retired feature-local lifecycle port, Redis lifecycle adapter and records, legacy COMPLETE service, lifecycle-coupled OTP commit coordinator, isolated legacy lifecycle DTO cluster, and obsolete owned tests.
- Active public idempotency-key and protected replay contracts, shared START, DETAILS_AND_OTP, OTP_RESEND, OTP_VERIFY, and COMPLETE execution, CANCEL separation, and protected OTP commit-state persistence remain intact.
- P5-D is complete and may transition to P5-E.
