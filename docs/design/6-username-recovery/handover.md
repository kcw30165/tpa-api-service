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
