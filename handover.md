# Username Recovery Single-Envelope Migration Handover

## Completed

### Stage A

Protected Username Recovery context, AES-GCM protection, envelope attributes, and fail-closed binding.

### Stage B

START and START replay use one protected revisioned auth-flow envelope and create no sidecar.

### Stage C

Request owns internal reservation and delivery outcomes through envelope CAS, with no sidecar dependency.

### Stage D

- Cancel uses only the protected auth-flow envelope.
- Live cancellation validates origin/referer, CSRF, and protected context.
- `cancelRevisioned` is deferred with `Mono.defer`, so no mutation-port invocation occurs when protected-context validation fails.
- One successful revision-aware envelope mutation changes lifecycle to `CANCELLED` while retaining encrypted context, `createdAt`, `expiresAt`, and remaining Redis TTL.
- Revision conflicts record stable unknown outcome without retry.
- Completed replay validates the retained cancelled envelope without mutation.

## Remaining

Stage E removes all unused sidecar infrastructure and adds final one-record architecture guards.