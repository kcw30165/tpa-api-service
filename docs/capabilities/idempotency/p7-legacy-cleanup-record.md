# P7 Legacy Idempotency Cleanup Record

**Branch:** `refactor/idempotency-capability`
**Scope:** Repository consolidation after the P3–P6 shared-idempotency migration. Removes orphaned
feature-local idempotency lifecycle left on disk after runtime migration, and adds a repository-wide
architecture guard preventing regression. No production flow behaviour changes.

## What P7 removed

### P7-B — Registration Redis idempotency orphan cluster (commit `d28b2679dcfdef307bad37cc0430ace7fa6c0e77`)
Deleted 4 unwired Registration Redis lifecycle files (zero `@Bean`/config consumers, zero test refs):
- `RedisRegistrationIdempotencyOperations`
- `ReactiveRedisRegistrationIdempotencyOperations`
- `RedisRegistrationIdempotencyRecord`
- `RedisRegistrationIdempotencyResponseSnapshotRecord`

### P7-C — Login idempotency DTO dead-cluster (commit `6b787bb7a1c75db46815e39bd3ebd1be03f17ee4`)
Deleted 9 unused Login idempotency lifecycle DTOs:
`LoginIdempotency{CompleteCommand, FailCommand, Record, ReplaySnapshot, StartCommand, StartOutcome,
StartResult, Status, UnknownOutcomeCommand}`.
Surgically retargeted `Task0043LoginStartReplayArchitectureTest`: removed only the method that read
the deleted `LoginIdempotencyReplaySnapshot`; preserved the 3 live protected-replay guards.

### P7-E — repository-wide lifecycle guard (commit `730530fd5f02a54d80c430f726d605bbbe4a8a86`)
Added `SharedIdempotencyNoFeatureLocalLifecycleArchitectureTest` (3 assertions, all green):
1. No feature-qualified idempotency lifecycle **type names** may exist
   (`(Registration|Login|ForgotPassword|UsernameRecovery)Idempotency(Status|Record|StartCommand|…)`
   and `Redis…Idempotency(Operations|Record|ResponseSnapshotRecord)`).
2. The legacy `:auth:idempotency:` namespace literal is confined to
   `RedisLoginIdempotencyKeyFactory` (OTP-verify/handoff persistence only).
3. Shared `IdempotencyExecutor` + `IdempotencyPort` remain present.

The guard keys on feature-qualified **type names**, deliberately **not** on the
`{PROCESSING, COMPLETED, FAILED}` enum shape — P7-D confirmed that shape is also used by
legitimate retained components (`LoginOtpVerifyReservationStatus` OTP commit-state,
`MessageDeliveryTaskStatus` messaging), which must not be flagged.

## What P7 deliberately kept (legitimate, not duplicate lifecycle)
- Shared capability under `application/idempotency` (`IdempotencyExecutor`, `IdempotencyPort`,
  `IdempotencyStatus`, `IdempotencyReservationOutcome`, identity/fingerprint factories).
- Shared public vocabulary consumed by mappers/bridges: `*IdempotencyKey`, `*IdempotencyAction`,
  `RegistrationIdempotencyStartOutcome`, `*IdempotencyPersistenceId`.
- Protected replay (AES-GCM `*ProtectedReplay*`), OTP-send reservation/replay
  (`LoginOtpSendReservation*`), OTP-verify commit-state (`LoginOtpVerifyReservation*`),
  committed-login recovery / `AuthenticatedLoginHandoff` / consumption proof.
- `RedisLoginIdempotencyKeyFactory` — sole permitted holder of the legacy namespace literal.

## Status
Registration, Login, Forgot Password, and Username Recovery use the shared `IdempotencyExecutor`
exclusively. All feature-local idempotency **lifecycle** ownership has been eliminated; remaining
idempotency-named types are shared capability, shared vocabulary, or protected-continuity
components. Regression is prevented by the P7-E guard.

_Pending at P7-G:_ `spotless:apply`, full `mvn test`, application startup smoke test, MR evidence pack.
