# Idempotency Capability

> **Migration status:** P0 target architecture is under documentation review. Current feature-scoped implementations remain legacy migration inputs until each action family moves to the shared capability.

## Target consolidation

The approved target is one shared idempotency lifecycle, terminal-response snapshot contract, and Redis persistence implementation. `COMPLETED_REPLAY` is internal lifecycle metadata and must never replace the original public outcome.

Read [Shared Idempotency Consolidation Implementation Plan](shared-idempotency-consolidation-plan.md) before implementation. The final migration gate requires deletion of stale feature-specific ports, records, adapters, coordinators, serializers, key factories, lifecycle enums, configuration, and obsolete tests.


> **Status:** Documents the idempotency implementation represented by the reviewed codebase snapshot.  
> **Audience:** New team members, backend developers, reviewers, testers, support engineers, and solution designers.  
> **Scope:** Request deduplication, request hashing, Redis reservation records, completed-result replay, failure/unknown outcomes, TTL policy, and reuse by Login, Registration, and OTP workflows.

## Start here

Idempotency ensures that retrying the **same logical mutation** does not execute it twice. The client supplies an `Idempotency-Key`; the server combines the action, key, and a canonical request hash to coordinate processing in Redis.

```text
Mutation request
  + action
  + Idempotency-Key
  + canonical request hash
        |
        v
Idempotency coordinator / port
        |
        v
Redis record
  PROCESSING -> COMPLETED + replay snapshot
             -> FAILED
             -> UNKNOWN_OUTCOME
```

Read the documents in this order:

1. **[Architecture and lifecycle](architecture-and-lifecycle.md)** — understand actions, keys, request hashes, Redis status, replay snapshots, TTLs, and failure semantics.
2. **[Integration and reuse guide](integration-and-reuse-guide.md)** — follow the implementation recipe when a new mutation needs idempotency.
3. **[Operations and testing](operations-and-testing.md)** — configure, test, troubleshoot, monitor, and release idempotent operations safely.

## What idempotency does

- Prevents simultaneous duplicate execution
- Replays an already-completed result without repeating side effects
- Rejects reuse of the same key for a different logical request
- Preserves explicit failed or uncertain outcomes
- Bounds records with an operation-specific TTL

## What idempotency does not do

Idempotency is not:

- Authentication
- Authorization
- CSRF protection
- Rate limiting
- A distributed transaction across all downstream systems
- Proof that an unknown provider/downstream operation did or did not execute
- Permission to retry every mutation automatically

These controls are often used together but solve different risks.

## Current consumers

The reviewed implementation applies idempotency patterns across operations including:

- Login credential/start processing
- Login OTP send
- Login OTP resend
- Login OTP verification
- Registration start
- Registration OTP resend
- Registration OTP verification
- Registration completion

Action names and storage ports differ by flow, but they share the same core concepts: validated key, action scope, canonical SHA-256 request hash, atomic reservation, explicit status, bounded TTL, and safe completed-result replay.

## Core outcomes

```text
STARTED
  This caller reserved the operation and may execute it.

DUPLICATE_IN_PROGRESS
  The same logical request is already processing.

COMPLETED_REPLAY
  Return the prior safe result; do not repeat side effects.

KEY_REUSED_WITH_DIFFERENT_REQUEST
  Same action/key but a different request hash; reject it.

FAILED_EXISTING
  A prior attempt reached a known failed state.

UNKNOWN_OUTCOME_EXISTING
  A prior attempt may have produced a side effect; do not retry blindly.
```

Exact enum names vary by flow, but the semantics must remain consistent.

## Reuse rules

1. Scope every idempotency record by action.
2. Validate keys at the trust boundary.
3. Generate the request hash from canonical business inputs.
4. Never include raw credentials, OTPs, CSRF tokens, session IDs, or PII in Redis keys.
5. Reserve before the first side effect.
6. Complete only after the operation reaches a replay-safe result.
7. Store only a safe replay snapshot—not arbitrary response headers or sensitive payloads.
8. Mark uncertain side effects as unknown outcome rather than failed.
9. Do not delete an unknown record merely to make retry possible.
10. Retry/replay must preserve the original business semantics and correlation evidence.
11. TTL must cover the retry/replay window without becoming indefinite retention.
12. Test concurrency and crash boundaries, not only sequential duplicate calls.

## Current configuration concepts

The reviewed codebase uses strict key validation, SHA-256 request hash generators, Redis-backed records, action-specific key factories, and operation-specific TTLs. Several observed idempotency TTLs are 300 seconds, but these are deployable policy values rather than universal constants. citeturn8search34turn8search35
