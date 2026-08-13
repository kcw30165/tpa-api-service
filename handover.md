# Username Recovery Handover

## Completed fixes

### Protected sidecar persistence

- Complete workflow payload protected with AES-GCM.
- Redis retains only opaque identity, revision, key version, algorithm, and ciphertext.

### Retained cancellation lifecycle

- Envelope and sidecar transition to `CANCELLED` through independent revision-aware CAS operations.
- Normal cancellation deletes neither record and preserves expiry.

### Original verified destination masking

- Non-production override is used only as the physical recipient.
- Live and replay responses mask the original verified destination.

### Final-request envelope completion

- A completed sidecar triggers revision-aware auth-flow envelope completion.
- Partial workflows leave the envelope `STARTED`.
- Completion preserves the original expiry and remaining Redis TTL.
- Idempotency completion is deferred until envelope completion succeeds.
- Envelope completion uncertainty records stable `UNKNOWN_OUTCOME`, does not complete idempotency, and never resends.
- Replay validates envelope/sidecar lifecycle consistency.

## Runtime configuration

```bash
export USERNAME_RECOVERY_CONTEXT_SECRET="replace-with-a-secret-of-at-least-32-characters"
export USERNAME_RECOVERY_CONTEXT_KEY_VERSION="v1"
```

## Known baseline failures

Three unrelated controller architecture failures may remain for existing controller logging and exception-message inspection.
