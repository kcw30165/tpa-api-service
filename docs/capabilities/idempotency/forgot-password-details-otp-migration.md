# Forgot Password Details and OTP Idempotency Migration

## Baseline

This document defines the next bounded P5-C increment after the Forgot Password START action was migrated to the shared idempotency executor. The selected action is `DETAILS_AND_OTP`. The cumulative baseline is Git HEAD `aaca1b95d07a8da6d8172341bdef0e6c4f9b3c33` on `fix/relay-same-error-code-for-the-idempotent-request`.

## Scope

Migrate only the combined details verification and initial OTP issuance action. Preserve OTP resend, OTP verify, complete, and cancel on the legacy Forgot Password idempotency port during this increment.

## Required target structure

1. Extract a protected operation that performs session locking, lock-reloaded validation, details verification, OTP creation and delivery, authoritative session binding, and replay-state preparation.
2. Add a shared-executor integration policy for the `DETAILS_AND_OTP` operation scope and TTL.
3. Add an execution bridge that converts between the shared executor payload and `ForgotPasswordDetailsOtpResult`.
4. Keep replay payloads protected and opaque. Do not place the auth-flow session ID, destination, OTP challenge ID, or mutable OTP timing data in shared idempotency metadata.
5. Switch the Spring composition only after fail-first tests cover reservation mapping, protected replay, parity, and composition ownership.
6. Remove only `DETAILS_AND_OTP` legacy calls after the new composition is proven. Preserve the legacy port, DTOs, Redis adapter, and enum values required by remaining Forgot Password actions.

## Behavioral preservation

- Preserve current validation and public status mapping.
- Preserve the mutation lock and lease-budget checks around external calls and persistence.
- Preserve accepted, failed, and unknown delivery classification.
- Preserve authoritative OTP/session binding and opaque commit-reference replay.
- Preserve request hashing semantics unless a versioned replacement is introduced with parity tests.
- Preserve the rule that downstream technical failures are never exposed as a public business rejection.

## Implementation sequence

1. Add fail-first characterization and architecture tests.
2. Add protected replay codec and persistence boundary for the action.
3. Add protected operation, policy, and execution bridge.
4. Switch composition to the shared executor.
5. Run focused tests, production compile, and test compilation.
6. Audit for remaining `DETAILS_AND_OTP` legacy calls and remove only action-local dead code.
7. Update this document and `handover.md` with the implemented cumulative state.

## Gates

Intermediate implementation scripts must run `mvn -DskipTests compile` and `mvn -DskipTests test-compile`; full Maven tests are reserved for the P5-C completion gate. Before transition from P5-C to P5-D, the full Maven suite and normal local Spring startup smoke test must both pass on the final P5-C state.

### Fail-first coverage added

- Added an architecture-level fail-first contract for the future shared coordinator, protected operation, execution bridge, replay codec, protected replay port, and atomic Spring composition switch.
- The contract forbids legacy DETAILS_AND_OTP lifecycle calls after migration while explicitly preserving OTP resend, OTP verify, complete, and cancel composition.
- Production code and production composition remain unchanged in this round, so the new contract is intentionally red until implementation.

### Replay bridge foundation implemented

- Added the action-specific protected replay resolution port, strict safe replay codec, and typed execution bridge.
- Successful results require an opaque protected replay reference. Shared snapshots exclude destination, session, challenge, and timing continuity.
- Production composition remains unchanged while the protected operation and shared coordinator are implemented.

### Protected operation implemented

- Extracted the DETAILS_AND_OTP protected operation with lock, session reload, lease budget, details verification, OTP creation/delivery, binding, and authoritative commit-state persistence.
- Added an action-specific replay resolver over the existing authoritative OTP commit/session/challenge resolver.
- The protected operation has no legacy idempotency port or legacy commit coordinator dependency. Production composition remains unchanged pending the shared coordinator round.

### Shared coordinator implemented

- Added the DETAILS_AND_OTP coordinator over the repository-wide shared executor with pre-reservation validation, session-scoped operation identity, canonical detail fingerprinting, conservative failure classification, protected replay capture, and lifecycle-result mapping.
- Added focused coordinator coverage. Production composition remains on the legacy orchestration until the atomic switch round.

### Atomic composition switch implemented

- Production DETAILS_AND_OTP composition now uses the shared coordinator, protected operation, execution bridge, safe replay codec, and authoritative protected replay resolver.
- Removed the legacy DETAILS_AND_OTP orchestration class and migrated its parity and revision-aware tests.
- OTP resend, OTP verify, complete, cancel, the legacy port, and the legacy commit coordinator remain unchanged for their current owners.

### Protected execution result contract aligned

- Renamed the protected execution carrier to `ForgotPasswordDetailsOtpProtectedExecutionResult` so the application execute method conforms to the repository-wide typed Result contract.

### Protected execution Result declaration repaired

- Corrected the accidental duplicate `ResultResult` suffix while preserving the clean-architecture-compatible `ForgotPasswordDetailsOtpProtectedExecutionResult` contract.

### P5-C startup smoke completed

- The full Maven suite passed against the cumulative formatted Result-contract repair state.
- Spring startup smoke reached the application started marker using runtime-only local overrides that disabled Redis, enabled lazy initialization, and excluded SSL metrics auto-configuration because deployment-only Redis mTLS files are unavailable in WSL.
- Production configuration was unchanged. P5-C is ready for its stabilization commit.

### P5-D validation evidence

- Revalidated the committed DETAILS_AND_OTP shared-executor composition with compile, test compilation, focused parity and architecture tests, and static boundary audits.
- Confirmed the legacy DETAILS_AND_OTP orchestration remains absent and non-selected resend, verify, complete, and cancel composition remains intact.
