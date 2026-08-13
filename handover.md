# Username Recovery Single-Envelope Migration Handover

## Stage A completed

Capability-specific protected context, AES-GCM protection, attribute namespace, and framework-free envelope binding are implemented and tested.

## Stage B completed

- START creates one revisioned auth-flow envelope containing the encrypted Username Recovery context.
- START creates no workflow-state sidecar and has no sidecar dependency.
- START replay reads only the protected envelope context and rotates CSRF through envelope CAS without extending expiry.
- Expired protected envelopes fail closed before context loading or CSRF rotation.
- Old sidecar-only START replay fails closed.
- Reservation, request, and cancellation retain temporary sidecar dependencies until Stages C and D.

## Remaining

- Stage C: reservation and request use one envelope CAS, including atomic final-candidate and `COMPLETED` transition.
- Stage D: cancellation and replay use only the envelope.
- Stage E: remove sidecar infrastructure and add one-record architecture guards.
