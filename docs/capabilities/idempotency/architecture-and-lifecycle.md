# Idempotency Architecture and Lifecycle

## Approved target and migration boundary

The target architecture is one session-context-neutral and workflow-neutral application capability behind one shared port and one shared Redis implementation. It owns action/key/fingerprint identity, atomic reservation, lifecycle transitions, schema-versioned terminal public-response snapshots, safe-header policy, replay, expiry, corruption handling, and common observability.

A deterministic terminal response, successful or unsuccessful, is stored as `COMPLETED`. Replay restores its original HTTP classification, success flag, public status, result, messages, errors, and allowlisted headers. `COMPLETED_REPLAY` is never a public business status.

The detailed feature-specific architecture below is retained as the legacy migration inventory. After migration and expiry of the compatibility window, stale feature-specific idempotency infrastructure must be deleted.

The shared model has no dependency on auth-flow sessions, authenticated sessions, feature DTOs, actor/account context, controllers, Spring, or Redis. Action integrations provide opaque scope identity and separately authorize or bind the current session context.

See [Shared Idempotency Consolidation Implementation Plan](shared-idempotency-consolidation-plan.md).


## 1. Architectural intent

Idempotency is implemented as an application capability behind ports and Redis adapters. Workflow services or coordinators reserve an action before side effects, then complete, fail, or mark the record uncertain.

```text
Web request with Idempotency-Key
        |
Application command and use case
        |
Action-specific idempotency coordinator
        |
Idempotency port
        |
Redis adapter + key factory + JSON mapper
```

The web layer checks that a required key is present. The application layer determines action scope and canonical request content. The Redis adapter owns atomic persistence and record serialization.

## 2. Core domain model

An idempotency record conceptually contains:

- Action
- Validated idempotency key
- Canonical request hash
- Status
- Creation/update timestamps
- Expiry/TTL
- Safe replay snapshot when completed
- Safe failure or unknown-outcome metadata where required

Typical stored statuses are:

```text
PROCESSING
COMPLETED
FAILED
UNKNOWN_OUTCOME
```

Ports expose flow-specific start/reserve, complete, fail, mark-unknown, load, and delete operations.

## 3. Action scope

The key is not globally unique by itself. The effective identity includes the action and configured capability namespace.

Examples represented in the codebase include concepts equivalent to:

- Registration start
- Registration OTP resend
- Registration OTP verify
- Registration complete
- Login credentials/start
- Login OTP send
- Login OTP resend
- Login OTP verify

A key reused for a different action is a separate operation only if the key factory and policy deliberately scope it that way. Never infer action from URL text inside the Redis adapter; pass an explicit action.

## 4. Key validation and Redis naming

Idempotency keys are opaque client-generated identifiers with strict length/character validation. The reviewed key factories accept a restricted safe form and reject blank or malformed values.

A conceptual Redis key is:

```text
{environment-prefix}:{capability}:{action}:{idempotency-key}
```

Rules:

- No username, identity value, destination, policy number, certificate number, account reference, OTP, token, or request body in the key
- Use a configured environment/application prefix
- Keep action names stable for the supported replay lifetime
- Prevent wildcard or delimiter injection through key validation
- Capability-scoped administrative operations only

## 5. Canonical request hashing

Request hash generators use SHA-256 over a canonical representation of the business inputs that define the logical operation.

The hash answers:

> Is this retry semantically the same request as the one that reserved this action/key?

### Include

- Stable fields that change the requested business effect
- Destination ID or irreversible destination identity where relevant
- Scenario/action-specific values
- Normalized inputs where normalization is part of the contract

### Exclude or handle deliberately

- Request/correlation ID
- Timestamps generated per attempt
- Transport ordering or JSON whitespace
- Raw session and CSRF secrets
- Passwords or OTPs in stored/replay material
- Values that legitimately vary while the business request remains identical

Some credential/verification operations necessarily use sensitive submitted values when deriving a request hash. The implementation must use one-way hashing/canonicalization and must never persist or log the clear input.

### Canonicalization rule

The same logical request must hash identically across retries, while a materially different request must not. Field order, trimming, case normalization, null versus empty, and collection ordering must be explicitly specified and tested.

## 6. Reservation lifecycle

```text
No record
  -> atomic set-if-absent PROCESSING
  -> outcome STARTED

Existing PROCESSING + same hash
  -> DUPLICATE_IN_PROGRESS

Existing COMPLETED + same hash
  -> COMPLETED_REPLAY

Existing FAILED + same hash
  -> FAILED_EXISTING

Existing UNKNOWN_OUTCOME + same hash
  -> UNKNOWN_OUTCOME_EXISTING

Any existing record + different hash
  -> KEY_REUSED_WITH_DIFFERENT_REQUEST
```

The initial reservation must be atomic. A read-then-write implementation permits duplicate execution under concurrency.

## 7. Completion and replay snapshots

On successful/replay-safe completion, the coordinator stores a snapshot sufficient to reconstruct the approved application result without calling external systems again.

Snapshot concepts in the codebase include:

- Outcome/status code
- Commit or replay reference
- Serialized safe body/result
- Masked destination
- Expiry and resend-cooldown values
- Approved safe headers

### Snapshot safety

Do not persist:

- `Set-Cookie`
- `Authorization`
- CSRF/XSRF values
- Session identifiers
- Raw destinations
- Passwords or OTPs
- Provider credentials
- Arbitrary inbound/outbound headers

Snapshot constructors and serializers should validate header allowlists/denylists and payload shape.

### Replay behavior

A replay returns the same business meaning and safe public data as the original completion. It does not:

- Regenerate an OTP
- Resend a message
- Reinvoke APIM/TAS
- Create another session
- Reapply a mutation

## 8. Failure versus unknown outcome

### Known failure

Use `FAILED` only when the implementation knows the protected side effect did not complete or when the action's policy deliberately records a terminal known failure.

### Unknown outcome

Use `UNKNOWN_OUTCOME` when the process cannot prove whether a non-idempotent or externally visible side effect occurred—for example, a timeout after message/APIM request transmission.

Unknown outcomes require deliberate reconciliation, expiry, or user recovery. Automatically converting unknown to “not executed” risks duplicate side effects.

### Crash boundaries

The design must account for crashes:

```text
After reservation, before side effect
After side effect, before completion record
After completion record, before HTTP response
During replay-snapshot serialization
```

The third case is the normal reason completed replay exists. The second case is the dangerous unknown-outcome boundary.

## 9. TTL lifecycle

Every idempotency record has a positive TTL. The TTL must:

- Cover the maximum client retry interval
- Cover operation timeout and bounded internal retry
- Cover expected delayed duplicate submissions
- Not retain sensitive metadata indefinitely

A record expiring permits a future key reuse because the server no longer has evidence of the prior operation. Therefore, TTL is part of the external reliability contract and must not be changed casually.

Do not refresh TTL indefinitely on every duplicate request unless that is an explicit policy.

## 10. Login patterns

### Credential/start idempotency

The Login credential coordinator hashes the logical credential request, reserves processing, creates/replays protected start state, and distinguishes duplicate, completed, failed, and unknown outcomes.

### OTP send/resend

OTP delivery idempotency prevents repeated messages for the same logical send. Completed replay returns masked destination/timing information rather than sending again. Delivery uncertainty must be represented as unknown where duplicate delivery is possible.

### OTP verification

Verification idempotency prevents repeated success side effects such as duplicate authenticated-session establishment or repeated account-list processing.

## 11. Registration patterns

Registration uses action-specific records for start, OTP resend, OTP verify, and completion. The snapshot model can preserve a safe workflow result and approved headers while excluding cookie/token security material.

Registration completion is especially race-sensitive because it can call TAS/APIM and mutate registration state; it combines idempotency with a session completion lock. These mechanisms complement each other:

```text
Idempotency
  identifies repeated logical request

Completion lock
  prevents concurrent critical-section execution for the session
```

## 12. HTTP and workflow mapping

Typical public behavior includes:

- Missing idempotency key → validation error
- Duplicate in progress → conflict/waiting response
- Key reused with different request → conflict/validation failure
- Completed replay → normal success with replayed result
- Failed existing → scenario-specific failure
- Unknown outcome → safe system/unavailable response and recovery guidance

The codebase maps idempotency conflicts into structured workflow errors and HTTP semantics such as `409 Conflict`. Exact status and `nextStep` are scenario-specific.

## 13. Security and logging invariants

- Keys and hashes are not authentication credentials.
- Clear request inputs are never logged by hash generators/coordinators.
- Redis keys contain no PII or secrets.
- Replay snapshots exclude security headers and sensitive values.
- Request IDs and action/status categories are safe correlation fields.
- Hash mismatch logs do not include either clear request.
- Redis exceptions are sanitized.
- Administrative deletion is capability-scoped and audited.

## 14. Extension boundaries

### Reuse directly

- Key validation/value objects
- SHA-256 hashing utilities/patterns
- Redis reservation/status model
- Replay-snapshot safety rules
- Coordinator outcome semantics
- Safe exception and logging patterns

### Configure or extend

- Action enum
- TTL
- Canonical hash fields
- Replay snapshot type
- Failure/unknown policy
- Public error and navigation mapping

### Implement per operation

- What constitutes the logical request
- First irreversible side effect
- Replay-safe result
- Reconciliation behavior
- Whether failed records are retryable
- Whether downstream mutation is truly idempotent
