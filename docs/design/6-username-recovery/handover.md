# Username Recovery Handover

## Current baseline

Repair 025C-3B1 extends Model B reservation into Request delivery settlement. New Request sends use the workflow-state sidecar as the candidate delivery-state authority. The legacy Username Recovery session remains only for completed Request replay until Repair 025C-3C.

## Completed before this round

- Repair 025B: workflow-state Redis create, load, CAS update with TTL preservation, and delete.
- Repair 025C-1: Start envelope and sidecar creation.
- Repair 025C-2: Start replay migration to Model B.

## Current round

- Model B envelope and sidecar validation during reservation.
- Sidecar CAS reservation.
- Post-reservation sidecar reload.
- Sender invocation only for the reserved sidecar candidate.
- Sidecar settlement to REQUESTED, FAILED, or UNKNOWN_OUTCOME.
- No CAS retry and no blind resend.

## Deferred

- Repair 025C-3C: completed Request replay migration and legacy Request session dependency removal.
- Repair 025C-4: Cancel migration.
- Repair 025D: remaining legacy cleanup and architecture closure.

## Repair 025C-3C Request replay Model B migration

Completed Request replay now decodes the canonical `AuthFlowSessionId`, loads and validates the generic `AuthFlowSession` envelope, then loads the independent `UsernameRecoveryWorkflowState` sidecar. Replay reconstructs accepted, failed, and unknown outcomes without delivery, mutation, retry, or TTL extension. Request no longer uses `UsernameRecoverySessionPort` or the legacy aggregate for completed replay. `UsernameRecoverySessionId` remains only as the temporary reservation-command bridge, and Cancel continues to use the legacy session boundary until its separately authorized migration.

Validation passed the focused Request suite, cumulative Username Recovery regression suite, architecture guards, and `git diff --check`. Independent replay fixtures cover missing or mismatched envelope and sidecar state, wrong scenario, expiry, terminal envelope status, accepted replay, failed replay, unknown-outcome replay, and fail-closed inconclusive state.

## Repair 025C-4 Cancel Model B migration

Cancel now uses the canonical `AuthFlowSessionId`, revision-aware `AuthFlowSessionPort`, and independent `UsernameRecoveryWorkflowStatePort`. A live envelope requires allowed origin or referer validation and a valid raw CSRF token. Expired or terminal envelopes can be cleaned up without the CSRF gate. Envelope deletion uses the exact loaded revision, followed by idempotent workflow-sidecar deletion. Missing envelopes still trigger sidecar cleanup. Uncertain envelope or sidecar deletion records a stable unknown outcome without automatic retry, and completed replay performs no envelope or sidecar I/O.

The Spring composition now injects the generic envelope and workflow-state ports into Cancel. Cancel has been removed from the legacy session identifier allowlist, while the legacy Redis/session boundary remains only for the separately deferred final-removal scope. Focused and cumulative validation, architecture guards, legacy-usage guards, and `git diff --check` passed.

## Repair 025D-1 Workflow-state migration and reservation bridge removal

Approval date: 2026-08-12. Decision: Repair 025D1 accepted. The independent review observations are accepted as follow-up work and were not merge blockers for Repair 025D1.

Accepted scope (exactly seven files):

- `UsernameRecoveryReservationCommand` uses canonical `AuthFlowSessionId`; the deprecated `UsernameRecoverySessionId` bridge constructor and accessor are removed.
- `RequestUsernameRecoveryService` uses the typed workflow-state sidecar and canonical session identity; the `sessionIdOf` legacy bridge is removed.
- `RedisUsernameRecoveryWorkflowStateAdapter` moves UPDATE/DELETE Lua ownership to `redis/username-recovery-workflow-state/`, supporting revision-controlled update and idempotent delete.
- `redis/username-recovery-workflow-state/update.lua` performs atomic revision compare-and-set and preserves the remaining TTL (PTTL -> PSETEX; never extends).
- `redis/username-recovery-workflow-state/delete.lua` performs idempotent deletion.
- Reservation-command, Redis mutation, request-service, legacy-usage guard, and workflow-state architecture tests are present and passing.

Explicit evidence paths inspected:

- `target/evidence/username-recovery/025D-1-20260812-152006/01-focused-command.txt`
- `target/evidence/username-recovery/025D-1-20260812-152006/01-focused-environment.txt`
- `target/evidence/username-recovery/025D-1-20260812-152006/01-focused-exit-code.txt`
- `target/evidence/username-recovery/025D-1-20260812-152006/01-focused-output.txt`
- `target/evidence/username-recovery/025D-1-20260812-152006/02-cumulative-command.txt`
- `target/evidence/username-recovery/025D-1-20260812-152006/02-cumulative-environment.txt`
- `target/evidence/username-recovery/025D-1-20260812-152006/02-cumulative-exit-code.txt`
- `target/evidence/username-recovery/025D-1-20260812-152006/02-cumulative-output.txt`
- `target/evidence/username-recovery/025D-1-20260812-152006/03-diff-check.txt`

Focused selected result: Maven exit 0, BUILD SUCCESS, 63 tests, 0 failures, 0 errors, 0 skipped. Cumulative selected result: Maven exit 0, BUILD SUCCESS, 147 tests, 0 failures, 0 errors, 0 skipped. `git diff --check` exit 0 with only LF/CRLF line-ending conversion warnings recorded. Both validation runs use `-Dtest=` with selected classes and are not represented as an unrestricted full-suite run.

Accepted follow-up observations (non-blocking for Repair 025D1):

1. Review malformed or null message-delivery results to ensure every post-sender uncertain path records stable `UNKNOWN_OUTCOME`.
2. Define and test the idempotency-reservation lifecycle for every pre-sender terminal or validation path.
3. Consider enforcing the revised workflow-state revision and immutable identity invariants within the Redis update Lua boundary.
4. Review whether effective-destination resolution failure should be distinguished from a definitive sender failure.
5. Remove stale transitional-bridge documentation and reconcile legacy-usage guard comments with the actual allowlist.
6. Improve validation evidence by eliminating JaCoCo class/execution-data mismatch and retaining future-JDK tooling warnings as non-blocking technical debt.

Resulting approval commit hash: `82145dc66a5b4edddc8f3ffacae655418ed7c282` (subject `feat(username-recovery): finalize repair 025D1 workflow state migration`).

Next recommended work item: Repair 025D-2 - remove the old legacy Username Recovery session Lua scripts and complete the remaining legacy-boundary cleanup and architecture closure (old legacy Lua files remain for 025D-2).

## Repair 025D-2 Implementation: Remove Legacy Username Recovery Session Stack

- **Implementation date:** 2026-08-12
- **Baseline:** `features/feature-impl-username-recovery` at `9b73fcbe82fa3e559d8529ec76d0a3a9e12c211d`.
- **Objective:** physically remove the obsolete Username Recovery-specific session aggregate, persistence ports, Redis adapter/mapping/key stack, legacy Lua resources, and tests owned only by that stack.
- **Canonical runtime preserved:** generic `AuthFlowSessionPort` envelope plus typed `UsernameRecoveryWorkflowStatePort` sidecar, including revision CAS, remaining-TTL preservation, idempotency, CSRF, origin/referer, request, start, cancel, replay, and delivery behavior.
- **Configuration change:** `UsernameRecoveryFeatureConfig` no longer creates the obsolete legacy session mapper or port bean. The feature remains composed from canonical auth-flow, workflow-state, and idempotency ports.
- **Architecture closure:** `UsernameRecoveryLegacySessionUsageGuardTest` now asserts physical absence of every removed production source/resource path and contains no legacy usage allowlist.
- **Validation:** performed by the checked-in Repair 025D-2 validation workflow under `target/evidence/username-recovery/025D-2/`; independent review is required before commit.
- **Recommended commit:** `refactor(username-recovery): remove legacy session stack`.
