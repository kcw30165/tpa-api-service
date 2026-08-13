# Username Recovery Single-Envelope Migration Handover

## Approved target

One revisioned `AuthFlowSession` Redis record owns identity, lifecycle, CSRF, expiry, TTL, and concurrency. Feature-sensitive Username Recovery state is encrypted inside `AuthFlowSessionData.attributes`.

## Stage A completed

- Added `UsernameRecoveryAuthFlowContext` without session identity or independent revision.
- Added capability-specific encrypted context and attribute names under `usernameRecoveryContext.*`.
- Added AES-GCM protection bound by AAD to capability, key version, and auth-flow session ID.
- Added framework-free envelope binding/load support.
- Added round-trip, randomized IV, tamper, wrong-key, session-rebinding, missing-context, and envelope-field preservation tests.
- Stage A is additive. Existing sidecar runtime behavior remains unchanged until Stage B.

## Remaining stages

1. Stage B: Start flow writes the protected context directly into the newly created envelope and stops creating the sidecar.
2. Stage C: Reservation and request mutate context and envelope lifecycle through one envelope CAS.
3. Stage D: Cancellation and replay use only the envelope.
4. Stage E: Remove sidecar ports, adapters, scripts, configuration, and tests; add one-record architecture guards.

## Invariants

- No dual write after Stage B cutover.
- No plaintext or sidecar fallback.
- One `sessionRevision`, one `expiresAt`, one Redis TTL.
- Final candidate request and envelope completion occur in one CAS.
- Non-production override remains transport-only.
