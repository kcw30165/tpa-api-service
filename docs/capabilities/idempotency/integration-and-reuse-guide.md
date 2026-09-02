# Idempotency Integration and Reuse Guide

## Mandatory shared-capability rule

New and migrated actions must use the shared capability. Creating another feature-specific idempotency port, Redis adapter, key factory, persistence record, serializer, lifecycle coordinator, or replay-status model is prohibited.

Each action defines only its stable identity, versioned canonical fingerprint, TTL, protected side-effect boundary, terminal classification, and optional opaque protected-state reference. Every deterministic terminal response is persisted through the shared snapshot and replayed without re-execution.

The detailed guidance below remains applicable where it does not conflict with the shared target. See [Shared Idempotency Consolidation Implementation Plan](shared-idempotency-consolidation-plan.md).


## 1. Decide whether the operation needs idempotency

Use idempotency when a client retry, browser double-submit, network retry, concurrent request, or server response loss could repeat a mutation or external side effect.

Typical examples:

- Start an auth workflow
- Send/resend a message
- Verify an OTP and establish a session
- Complete Registration
- Submit a downstream update

A read-only operation usually does not need client idempotency, although caching or retry controls may still apply.

## 2. Define the logical operation before coding

Document:

- Action name
- Required key source/header
- Inputs defining semantic equality
- Canonicalization rules
- Reservation point
- First external/irreversible side effect
- Completion point
- Replay-safe result
- Known failure behavior
- Unknown-outcome behavior
- TTL
- Concurrency and lock requirements

If these are unclear, do not add a generic “idempotency wrapper” around the method.

## 3. Implementation sequence

### Step 1 — Add an explicit action

Add or reuse a stable action enum/value. The action should describe the business mutation, not a class name or transient route path.

Examples:

```text
FORGOT_PASSWORD_START
FORGOT_PASSWORD_OTP_SEND
FORGOT_PASSWORD_OTP_RESEND
FORGOT_PASSWORD_OTP_VERIFY
FORGOT_PASSWORD_COMPLETE
```

Post-login Change Password remains a separate operation/action family from Forgot Password.

### Step 2 — Validate the key

Use the existing value-object/key validation pattern. Reject missing, blank, too-short, too-long, or unsafe-character keys at the web/application boundary with the standard error contract.

Do not generate a replacement key silently on the server. The caller must know which key identifies the request it may retry.

### Step 3 — Define canonical request material

Create an operation-specific request hash generator. List every field and normalization rule in tests.

Example conceptual material:

```text
version=1
scenario=FORGOT_PASSWORD
operation=OTP_SEND
destinationId={normalized opaque destination ID}
```

Do not concatenate fields ambiguously. Prefer a length-delimited, structured, or otherwise collision-safe canonical representation before SHA-256.

### Step 4 — Reserve before side effects

Call the idempotency port/coordinator before:

- Creating externally visible state that cannot be safely repeated
- Sending SMS/email
- Calling a non-safe APIM/TAS mutation
- Establishing an authenticated session

Handle every reservation outcome explicitly.

### Step 5 — Execute only for `STARTED`

Only the caller that atomically reserved `PROCESSING` may execute the protected operation.

```text
STARTED
  -> execute

DUPLICATE_IN_PROGRESS
  -> return waiting/conflict; do not execute

COMPLETED_REPLAY
  -> return snapshot; do not execute

KEY_REUSED_WITH_DIFFERENT_REQUEST
  -> reject

FAILED_EXISTING / UNKNOWN_OUTCOME_EXISTING
  -> apply the operation's recovery policy
```

### Step 6 — Build a safe replay snapshot

Store the minimum result required to reproduce the approved response. Prefer typed snapshots over arbitrary serialized HTTP responses.

Allowed data can include:

- Stable public status/outcome
- Workflow `nextStep`
- Masked destination
- Timing/cooldown values
- Opaque safe business reference
- Approved non-sensitive headers

Never store raw tokens, cookies, credentials, OTPs, passwords, or PII.

### Step 7 — Complete atomically enough for the operation

After the business effect and required state persistence succeed, mark the record completed with the replay snapshot. If completion storage fails after a side effect occurred, classify the outcome as uncertain and follow reconciliation policy.

Do not return final success if the operation requires replay protection but the completion record could not be persisted, unless the approved design explicitly accepts that risk.

### Step 8 — Mark known failure or unknown outcome

Use known failure only when the operation can prove its effect did not complete or the policy defines the failed result as replayable/terminal.

Use unknown outcome when the effect may have occurred. Preserve enough safe metadata for support/reconciliation without storing customer secrets.

### Step 9 — Configure TTL

The TTL should exceed:

```text
maximum operation duration
+ bounded internal retry/backoff
+ expected client retry window
+ safety margin
```

It must also respect privacy/retention rules. Document why the value is adequate.

### Step 10 — Map outcomes to the public contract

Define a matrix:

```text
Idempotency outcome
  -> application result
  -> workflow status and nextStep
  -> public error code
  -> HTTP status
  -> retry/restart behavior
  -> log severity
```

Do not expose internal Redis status or request hashes.

## 4. Integrating message delivery

For an OTP/message send:

```text
validate session/CSRF/state
  -> reserve idempotency
  -> rate limit/cooldown checks
  -> create challenge
  -> call MessageDeliveryPort
  -> bind challenge/session
  -> complete snapshot
```

A completed replay returns masked destination and timing data. It never generates or sends a second code.

If the provider times out after transmission, mark unknown rather than automatically send again.

## 5. Integrating APIM/TAS mutations

Before classifying an APIM mutation as retryable/idempotent, confirm downstream semantics. BFF Redis idempotency prevents duplicate entry through this BFF while the record exists; it cannot by itself guarantee that TAS did not execute after a timeout.

For a downstream mutation:

- Reserve before the call
- Propagate the same `X-Request-Id` across bounded internal attempts
- Use a downstream idempotency/business reference where the APIM/TAS contract supports it
- Mark unknown on ambiguous timeout
- Reconcile or guide the user rather than retry blindly

## 6. Integrating completion locks

Use a session-scoped completion lock when two different keys or operations could concurrently enter the same critical completion section.

Acquire after preliminary validation and according to the documented ordering with idempotency. Release only with the ownership token. Define behavior for lock contention and lease expiry.

Idempotency and locks are not substitutes:

- Idempotency coordinates repeated logical requests by action/key/hash.
- A lock serializes critical work for a shared resource/session.

## 7. Client contract

The frontend/client must:

- Generate a sufficiently unique key for each new logical mutation
- Reuse the same key only when retrying the exact same request
- Generate a new key when any semantic input changes
- Avoid parallel requests with different keys for the same user action
- Preserve the key across a network retry
- Handle conflict/waiting, replayed success, and restart guidance
- Never place credentials or PII inside the key

## 8. Worked example: Forgot Password

```text
/start
  action FORGOT_PASSWORD_START
  hash identity-verification inputs in canonical protected form

/otp/send
  action FORGOT_PASSWORD_OTP_SEND
  hash opaque selected destination ID and scenario

/otp/resend
  action FORGOT_PASSWORD_OTP_RESEND
  hash session-bound resend semantics

/otp/verify
  action FORGOT_PASSWORD_OTP_VERIFY
  prevent duplicate verification/reset-authorization creation

/complete
  action FORGOT_PASSWORD_COMPLETE
  protect the TAS/APIM password-reset mutation
```

Each action needs its own TTL, replay snapshot, and unknown-outcome policy. Do not include the clear password, OTP, identity value, session ID, or CSRF token in the persisted snapshot or Redis key.

## 9. Pull-request checklist

- [ ] Mutation and duplicate-side-effect risk identified
- [ ] Stable action defined
- [ ] Key validation reused
- [ ] Canonical hash fields documented and tested
- [ ] Reservation occurs before side effect
- [ ] Every reservation outcome handled
- [ ] Replay snapshot is typed and safe
- [ ] Sensitive headers/payloads excluded
- [ ] Known failure and unknown outcome distinguished
- [ ] TTL justified
- [ ] Downstream idempotency limitations documented
- [ ] Lock requirements reviewed
- [ ] Concurrent duplicate tests included
- [ ] Crash-boundary tests included
- [ ] Fail-first TDD evidence included
- [ ] Operations document updated
- [ ] Commit message included

Suggested commit message:

```text
docs(idempotency): document lifecycle, reuse, operations, and testing
```
