# Username Recovery Handover

## Completed fixes

### Protected sidecar persistence

- Complete workflow payload protected with AES-GCM.
- Minimal Redis wrapper retains opaque identity, revision, key version, algorithm, and ciphertext.
- Existing Redis CAS and remaining TTL behavior is preserved.

### Retained cancellation lifecycle

- Auth-flow envelope and encrypted sidecar transition to `CANCELLED` through independent revision-aware CAS operations.
- Normal cancellation physically deletes neither record and does not extend expiry.
- Candidate delivery facts remain unchanged.

### Original verified destination masking

- The effective destination override is used only as the physical email/SMS recipient.
- Initial accepted responses mask the original verified destination from protected workflow state.
- Completed idempotency replay masks the original verified destination and does not resolve the current override.
- Destination type derives from the original verified channel.

## Runtime configuration

```bash
export USERNAME_RECOVERY_CONTEXT_SECRET="replace-with-a-secret-of-at-least-32-characters"
export USERNAME_RECOVERY_CONTEXT_KEY_VERSION="v1"
```

## Known baseline failures

Three unrelated controller architecture failures may remain for existing controller logging and exception-message inspection.
