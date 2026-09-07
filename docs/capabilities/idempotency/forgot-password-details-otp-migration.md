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
