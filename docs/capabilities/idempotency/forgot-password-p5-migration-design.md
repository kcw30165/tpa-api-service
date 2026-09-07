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
