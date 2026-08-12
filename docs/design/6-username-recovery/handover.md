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
