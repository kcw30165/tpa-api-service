# OTP Architecture and Lifecycle

> **Status:** Current implementation guide derived from the reviewed codebase snapshot.  
> **Scope:** Login and Registration OTP architecture. Proposed workflows are explicitly labelled.

## 1. Architectural intent

OTP is implemented using a ports-and-adapters architecture. Shared domain contracts and infrastructure provide the security primitive; Login and Registration application services coordinate those primitives within their own workflows.

```text
adapter/in/web
  Controllers, request/response DTOs, web mappers, HTTP status mapping
        |
application/port/in
  Send, resend, verify, start, cancel, and completion use-case interfaces
        |
application/service and application/usecase
  Scenario orchestration, state validation, idempotency, transitions
        |
application/port/out
  OTP challenge, session, message, policy, rate-limit, audit, protection ports
        |
adapter/out
  Redis, SMS, email, APIM, cryptography, and other infrastructure adapters
```

### Boundary rule

The web layer validates transport-level requirements and maps commands/results. It must not implement OTP generation, verification, attempt accounting, resend policy, or persistence logic.

The application layer must depend on ports. It must not construct Redis keys, call an SMS provider directly, or expose infrastructure records through API DTOs.

## 2. Primary components

### 2.1 Web adapters

Login OTP entry points are implemented by:

- `AuthLoginOtpController` for send and resend
- `AuthLoginOtpVerifyController` for verification
- `LoginOtpSendWebMapper`
- `LoginOtpResendWebMapper`
- `LoginOtpVerifyWebMapper`
- Login-specific request and response DTOs

Registration OTP entry points are implemented by:

- `AuthRegistrationController` for registration start, which sends the initial OTP
- `AuthRegistrationOtpController` for resend and verify
- `RegistrationStartWebMapper`
- `RegistrationOtpResendWebMapper`
- `RegistrationOtpVerifyWebMapper`
- Registration-specific request and response DTOs

Both web surfaces return the standard mutation envelope and a workflow payload containing `nextStep` and endpoint-specific data.

### 2.2 Inbound use cases

The codebase exposes strongly typed use-case interfaces for operations such as:

- Start Login
- Send Login OTP
- Resend Login OTP
- Verify Login OTP
- Start Registration
- Resend Registration OTP
- Verify Registration OTP

Commands carry normalized transport inputs such as session ID, CSRF token, OTP code, selected destination, idempotency key, language, request ID, origin, and referer. Results use explicit status enums rather than exceptions for expected business outcomes.

### 2.3 OTP domain and policy

The shared OTP model includes concepts equivalent to:

- OTP scenario
- OTP channel
- Challenge identifier
- Challenge status
- OTP policy
- Replacement outcome
- Verification outcome

Policy includes:

- Code length
- Numeric-only behavior
- Challenge TTL
- Resend cooldown
- Maximum verification attempts
- Whether resend limiting is enabled
- Maximum resend attempts
- Whether resend invalidates the previous code
- Whether verification attempts reset on resend
- SMS template
- Email subject and body

These are policy values. Services must read them through the appropriate policy/configuration boundary rather than duplicate them.

### 2.4 Challenge implementation

The reusable challenge implementation separates:

- Challenge ID generation
- OTP code generation
- OTP hashing
- Challenge state persistence
- Scenario-specific orchestration

Secure implementations use a cryptographically secure random source for generated values and SHA-256-based hashing for OTP comparison. Persistence stores state, hashes, counters, timestamps, and status—not the clear-text code.

### 2.5 Auth-flow session

The pre-authentication session binds workflow state to a server-managed identifier transported by the `auth_flow_sid` cookie. OTP-related state can include the selected destination, protected workflow context, challenge ID, current status, CSRF hash, and expiry metadata.

Session responsibilities include:

- Start and load
- State update
- OTP challenge binding
- Expiry refresh where the scenario permits sliding behavior
- Completion
- Cancellation
- Deletion or terminal retention
- Concurrency policy enforcement

The auth-flow session is not the authenticated portal session. Login OTP verification establishes the authenticated session only after successful verification and account resolution.

### 2.6 Idempotency

OTP operations use action-scoped idempotency records. A record includes an action, key, request hash, processing status, timestamps, and—when complete—a safe replay snapshot.

Typical outcomes are:

- Started
- Duplicate in progress
- Completed replay
- Failed existing
- Unknown outcome existing
- Key reused with a different request

Idempotency protects the operation, not merely the route. The request hash must include the fields that define the logical action, while excluding unstable or sensitive values that are not needed to detect semantic reuse.

### 2.7 Message delivery

Application services submit a message-delivery command through an outbound port. Adapters render and deliver SMS or email messages.

The delivery boundary must:

- Receive the clear OTP only for the duration needed to render/send
- Avoid logging content containing the OTP
- Return typed delivery status
- Mask destinations in diagnostics
- Map provider failures into safe workflow outcomes
- Avoid claiming delivery confirmation when the provider supplies none

### 2.8 Redis adapters

Redis adapters persist auth-flow sessions, OTP challenge state, idempotency records, replay snapshots, locks, and rate-limit buckets. Key factories provide deterministic namespacing and reject malformed key parts.

Security rules:

- No raw email, mobile number, identity value, OTP, CSRF token, or business identifier in a key
- TTL on all temporary auth artifacts
- Atomic operation for counters and lock release where needed
- Safe exception translation without Redis credentials or topology details
- Capability-specific key prefixes to prevent accidental cross-feature deletion

## 3. OTP lifecycle

A conceptual challenge lifecycle is:

```text
CREATED
  |-- valid code --------------------> VERIFIED
  |-- invalid code ------------------> CREATED (attempt incremented)
  |-- max attempts reached ----------> LOCKED
  |-- TTL elapsed -------------------> EXPIRED
  |-- resend/replacement policy -----> INVALIDATED or REPLACED
  |-- workflow cancellation ---------> INVALIDATED/DELETED
```

Exact persisted status names may differ, but these business states must remain distinguishable.

### 3.1 Create/send

1. Load and validate the workflow context.
2. Validate session, scenario, CSRF, and flow state.
3. Reserve idempotency.
4. Resolve/validate the destination.
5. Apply issuance and rate-limit policy.
6. Generate challenge ID and code.
7. Hash the code and persist challenge state with TTL.
8. Deliver the message.
9. Bind the challenge to the auth-flow session.
10. Complete idempotency with a safe replay snapshot.
11. Return only masked destination and timing metadata.

Delivery and persistence ordering must be handled deliberately. If an outcome is uncertain, mark it as unknown rather than blindly retrying and potentially sending multiple codes.

### 3.2 Resend

1. Validate session, CSRF, state, and idempotency.
2. Load the current challenge.
3. Enforce cooldown, send-rate, and maximum resend policy.
4. Generate a replacement code/challenge according to policy.
5. Invalidate or replace the previous challenge.
6. Deliver to the already-authorized destination.
7. Update session binding and replay snapshot.
8. Return expiry, cooldown, remaining resend attempts, and `retryAfterSeconds` where applicable.

When previous-code invalidation is enabled, the old OTP must fail after a successful resend.

### 3.3 Verify

1. Validate session, CSRF, flow state, request, and idempotency.
2. Load the session-bound challenge.
3. Reject expired, locked, replaced, consumed, or mismatched challenges.
4. Hash the submitted code and compare securely.
5. On failure, increment the attempt counter and expose only the permitted remaining-attempt information.
6. On the limit boundary, lock the challenge and return restart/recovery behavior.
7. On success, mark verified/consumed as required and transition the scenario.
8. Complete idempotency with a replay-safe result.

### 3.4 Completion and cancellation

Cancellation clears auth-flow and XSRF cookies when appropriate and transitions or deletes the server-side state. Successful Login OTP verification clears pre-auth flow state and writes the authenticated session cookie. Registration continues within the auth-flow session until registration completion or cancellation.

## 4. Login-specific orchestration

Login differs from generic OTP in these ways:

- Credential authentication happens first.
- APIM/TAS returns available OTP destinations.
- The BFF returns the available destinations to the frontend even when there is only one.
- The frontend selects a destination and calls the send endpoint.
- Verification proceeds to accessible-account resolution.
- Successful verification establishes the authenticated session.
- The response can include a CSRF token and an account-selection list.

Login result statuses distinguish invalid code, expiry, attempt exhaustion, invalid auth flow, account-list failure, no accessible accounts, and system failure.

## 5. Registration-specific orchestration

Registration differs in these ways:

- The initial OTP is created and sent during registration start.
- Registration start can include CAPTCHA and identity/member lookup.
- Resend and verify are separate protected operations.
- Successful verification moves to eligible-trust/username/password steps, not to an authenticated portal session.
- The verified result can expose remaining unregistered trusts.
- Registration operations use registration-specific idempotency actions and TTLs.

## 6. Error and HTTP model

OTP endpoints use:

- `MutationResponse`
- API status such as `SUCCESS`, `VALIDATION_FAILED`, `BUSINESS_REJECTED`, `DOWNSTREAM_ERROR`, or `SYSTEM_ERROR`
- Workflow payload with `nextStep`
- Structured errors with code, type, targets, severity, and source

Examples of transport semantics include:

- `400 Bad Request` for invalid/missing request values
- `403 Forbidden` for CSRF/origin failure
- `409 Conflict` for idempotency conflict
- `429 Too Many Requests` for cooldown/rate/attempt limits
- `502 Bad Gateway` for delivery or downstream failures
- `503 Service Unavailable` for system/session-creation failure

The exact status is determined by the scenario-specific HTTP response mapper. Preserve that mapper as the single HTTP translation point.

## 7. Security invariants

The following are non-negotiable:

- Clear OTPs are never persisted or logged.
- `toString()` methods redact OTPs, passwords, tokens, session identifiers, and destination identifiers.
- Response body logging must remain disabled for sensitive auth routes unless redaction is demonstrably safe.
- Session and challenge TTLs are positive and bounded.
- CSRF is validated for protected pre-auth mutations.
- Origin/referer validation is applied according to the auth-flow security policy.
- Idempotency keys are validated and scoped by action.
- Redis keys contain no PII.
- Rate-limit identifiers are irreversible bucket IDs, not raw destinations.
- Errors do not reveal whether an untrusted identity or destination exists unless the approved workflow contract permits it.
- A successful OTP proves possession of the destination; it does not by itself establish every business authorization.

## 8. Extension boundaries

### Reuse directly

- Generators and hashers
- Challenge persistence and lifecycle
- Message-delivery ports
- Auth-flow session infrastructure
- CSRF services
- Idempotency record concepts
- Rate-limit and audit ports
- Masking and redaction utilities

### Configure or extend

- Scenario policy
- Templates
- TTLs and limits
- Rate-limit rules
- Idempotency actions
- Error-contract definitions
- Workflow navigation

### Implement per workflow

- Destination discovery
- Entry conditions
- State machine
- Success authorization
- Downstream operation
- Controller DTOs and mappers
- Scenario-specific result/error statuses
