# Shared Idempotency Handover

## Current approved baseline

- Branch: `fix/relay-same-error-code-for-the-idempotent-request`
- Git HEAD: `cf2aab8b5561f790691d554aec4493f86aebbaa9`
- Commit: `refactor(idempotency): add shared redis lifecycle adapter`
- P2 status: committed and approved
- P3 audit: passed on the exact committed baseline

## Current decision

P3 migrates all Registration idempotent actions to the shared capability in this order:

1. P3-A Registration shared bridge
2. P3-B Registration Start
3. P3-C OTP Resend
4. P3-D OTP Verify
5. P3-E Registration Complete
6. P3-F bounded legacy compatibility and removal
7. P3-G stabilization and final evidence

No P3 production code has been applied yet. The next implementation artifact must start with P3-A and preserve the committed P2 contracts.

## Non-negotiable rules

- Only `ACQUIRED` executes a protected action.
- Replay hydrates the original terminal result and never re-runs side effects.
- Every deterministic terminal response is captured, including business and validation failures.
- `COMPLETED_REPLAY` is internal lifecycle metadata, not a public success classification.
- Failure classification is conservative. Ambiguity becomes `UNKNOWN_OUTCOME`.
- Ownership tokens and protected response material are never exposed to callers, logs, or public DTOs.
- Shared runtime composition remains disabled until deployment supplies the enable flag and dedicated HMAC secret.
- No legacy Registration idempotency type is removed before all four actions migrate and the compatibility window closes.
- Every stage updates the relevant files in `docs/capabilities/idempotency`.

## Runtime activation

```text
SHARED_IDEMPOTENCY_ENABLED=true
SHARED_IDEMPOTENCY_PERSISTENCE_HMAC_SECRET=<Base64 value decoding to at least 32 bytes>
```

Before activation, the default remains disabled and no shared HMAC secret is required.

## Planned P3 commit

```text
refactor(registration): migrate idempotency to shared capability
```
