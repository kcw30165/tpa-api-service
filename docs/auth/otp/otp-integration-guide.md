# OTP Integration and Reuse Guide

> **Audience:** Developers adding OTP to a new pre-authentication workflow.  
> **Worked example:** **Forgot Password**.  
> **Explicit exclusion:** Post-login Change Password is not part of Forgot Password scope.

## 1. Reuse principle

Reuse the OTP capability through shared contracts and infrastructure. Do not copy a Login or Registration controller/service and rename its classes.

The shared capability answers:

- How is a secure OTP generated and hashed?
- How is challenge state persisted and expired?
- How are attempts, cooldowns, and resends enforced?
- How is a message delivered safely?
- How are CSRF, idempotency, rate limiting, and auditing integrated?

The new workflow must answer:

- Who may request the OTP?
- Which destinations are eligible, and who owns them?
- What does successful verification authorize?
- What is the next workflow state?
- Which downstream operation completes the business action?

## 2. When the capability is a good fit

Use the shared OTP capability when the workflow needs a short-lived possession check over an approved SMS/email destination, with server-side challenge state, attempt limits, resend controls, session binding, and auditability.

Do not force this capability into a requirement that needs cryptographic transaction signing, device binding, delivery confirmation, or an authenticator protocol. Those require a separate security design.

## 3. Reuse map

### Use existing concepts directly

- OTP challenge ID generator
- OTP code generator
- OTP code hasher
- OTP challenge state port/adapter
- Message-delivery port
- Destination masking utility
- Auth-flow session port and lifecycle services
- CSRF generation, hashing, and validation
- Origin/referer validator
- Idempotency record storage and replay behavior
- Rate-limit port and irreversible bucket-ID generation
- Security-audit port
- Standard workflow result and mutation response model

### Add configuration or small extensions

- New scenario value where the generic model requires one
- Scenario-specific OTP policy
- SMS/email templates
- Auth-flow scenario TTL and concurrency behavior
- Idempotency actions and TTLs
- Rate-limit rules
- Error-contract definitions and navigation
- Spring composition beans

### Implement for the new workflow

- Start command/result and service
- Destination lookup/eligibility adapter
- Send/resend/verify orchestration as needed
- Protected workflow context
- Success authorization/context
- Downstream completion call
- HTTP controllers, DTOs, mappers, and status translation
- Scenario-specific tests

## 4. Implementation sequence

### Step 1 — Define the workflow contract

Before writing code, document:

- Entry point and required identity evidence
- Destination source and ownership
- Whether destination selection is mandatory
- State transitions
- OTP policy
- Successful-verification authorization
- Authorization lifetime and one-time-use behavior
- Error/recovery/navigation behavior
- Audit events
- Downstream ownership

Do not start from classes. Start from this contract.

### Step 2 — Define states and transitions

Create a state model that is explicit enough to reject out-of-order calls.

Example for Forgot Password:

```text
STARTED
  -> DESTINATIONS_AVAILABLE
  -> OTP_SENT
  -> OTP_VERIFIED
  -> PASSWORD_RESET_AUTHORIZED
  -> COMPLETED

Terminal/recovery states:
  CANCELLED
  EXPIRED
  LOCKED
  FAILED
```

Decide whether OTP verification both verifies possession and grants reset authorization, or whether another check is required.

### Step 3 — Define policy

Add policy under the established auth-workflow configuration model. Include:

- Length and character set
- TTL
- Resend cooldown
- Verification-attempt limit
- Resend limit
- Previous-code invalidation
- Attempt reset behavior
- SMS/email templates

Validate policy at startup. Avoid silent defaults for security-sensitive values in production-like profiles.

### Step 4 — Define commands and results

Use strong application-layer records/classes. Validate required fields at construction boundaries.

Typical operations:

- Start workflow
- Send OTP, if destination selection is separate
- Resend OTP
- Verify OTP
- Complete business action
- Cancel workflow

Results should use explicit statuses. Avoid using exceptions for expected outcomes such as invalid OTP, cooldown active, or expired challenge.

### Step 5 — Implement protected context

Store only the information needed to continue the flow. Protect sensitive context using the established context-protection port/adapter. Bind it to:

- Scenario
- Auth-flow session ID
- CSRF hash
- Current workflow status
- Expiry
- Authorized destination reference/hash
- OTP challenge ID

Do not store a raw OTP or an unprotected destination in the session record.

### Step 6 — Resolve destinations

Implement an outbound port that returns approved destinations in domain form. It should separate:

- Internal destination value used for delivery
- Public destination ID used by the frontend
- Channel
- Masked display value

The public ID must not expose the email address or mobile number. Even when only one destination is available, return it and let the frontend select it if that is the agreed pre-auth interaction rule.

### Step 7 — Orchestrate send

The service should:

1. Load and validate the protected auth-flow context.
2. Validate CSRF and origin/referer.
3. Reserve idempotency using action plus request hash.
4. Resolve the selected destination.
5. Enforce rate limits.
6. Call the shared OTP challenge capability.
7. Call the message-delivery port.
8. Bind the new challenge to the session.
9. Complete idempotency with a safe replay snapshot.
10. Return masked destination and timing values.

Define compensation/unknown-outcome behavior for failures between challenge creation, delivery, and session update.

### Step 8 — Orchestrate resend

Reuse the same authorized destination; do not accept a new raw destination on the resend API.

Enforce:

- Session and CSRF validity
- Correct workflow state
- Idempotency
- Cooldown
- Rate limit
- Maximum resend attempts
- Previous challenge invalidation/replacement

Return `retryAfterSeconds` and map it to `Retry-After` where the current HTTP pattern requires it.

### Step 9 — Orchestrate verify

The service should:

1. Validate session, CSRF, state, and idempotency.
2. Load the session-bound challenge.
3. Verify scenario and challenge association.
4. Verify the submitted code through the shared challenge port.
5. Update attempts/lock state through that capability.
6. On success, transition the session and create the scenario-specific authorization.
7. Complete idempotency with a safe replay result.

Do not accept a challenge ID from the client as authority when the server can obtain the bound ID from session state.

### Step 10 — Add error and navigation contracts

For every status, define:

- Public error code
- Error type
- Target (`otpCode`, `destinationId`, `PAGE`, header name, etc.)
- Severity
- Source
- API status
- HTTP status
- `nextStep`
- Retry/restart behavior

Add the definitions to the workflow error configuration and ensure startup validation covers them.

### Step 11 — Add web adapters

Controllers should only:

- Validate required transport fields
- Echo `X-Request-Id`
- Build the command
- Invoke the use case
- Manage approved cookie writes/clears
- Map result to response and HTTP status

Do not duplicate policy or state checks in controllers.

### Step 12 — Compose adapters

Wire the use case with established ports:

- OTP challenge
- Auth-flow session
- CSRF/origin validation
- Idempotency
- Message delivery
- Rate limiting
- Audit
- Context protection
- Scenario-specific downstream port

Only add a new infrastructure adapter where the existing contract genuinely cannot support the requirement.

### Step 13 — Add tests before production wiring

At minimum, add fail-first tests for:

- Happy path
- Invalid/expired/locked OTP
- Attempt boundary
- Cooldown and resend boundary
- Previous-code invalidation
- Idempotency replay and key misuse
- CSRF/session/state mismatch
- Delivery failure and unknown outcome
- Concurrent resend/verify
- Rate limiting
- Redaction
- Correct success authorization and expiry

## 5. Worked example: Forgot Password

This section is an integration blueprint, not a statement that the complete flow is already implemented.

### 5.1 Scope

Forgot Password is a pre-login workflow that allows an eligible user to verify identity and OTP possession before resetting a forgotten password.

Post-login Change Password is excluded. It has an authenticated-session context and a different authorization model.

### 5.2 Suggested route shape

Use the repository's established route and response conventions. A possible contract is:

```text
POST /api/v1/auth/forgot-password/start
POST /api/v1/auth/forgot-password/otp/send
POST /api/v1/auth/forgot-password/otp/resend
POST /api/v1/auth/forgot-password/otp/verify
POST /api/v1/auth/forgot-password/complete
POST /api/v1/auth/forgot-password/cancel
```

The exact contract requires business, TAS, security, and solution-design approval before implementation.

### 5.3 Suggested flow

```text
Identity/account verification
        |
        v
Return approved masked destinations
        |
        v
User selects a destination
        |
        v
Send OTP through shared capability
        |
        v
Verify OTP through shared capability
        |
        v
Create one-time password-reset authorization
        |
        v
Call TAS/APIM password-reset operation
        |
        v
Invalidate authorization and return to Login
```

### 5.4 What Forgot Password should reuse

- Auth-flow session and cookie infrastructure
- Protected context
- CSRF/origin/referer validation
- OTP challenge lifecycle
- SMS/email delivery ports
- Destination masking
- Idempotency and replay infrastructure
- Redis persistence
- Rate-limit and audit ports
- Standard mutation/error/workflow response shape

### 5.5 What Forgot Password must own

- Identity verification contract
- Destination lookup contract
- Password-reset authorization model
- Reset-password downstream port/APIM adapter
- Password-reset policy behavior owned by TAS
- OTP/anti-abuse/privacy/session controls approved by the security authority
- Workflow-specific states, errors, endpoints, and navigation
- Completion and cleanup behavior

### 5.6 Proposed authorization after OTP

Do not treat `OTP_VERIFIED` as an unlimited permission. Create a one-time, short-lived server-side reset authorization bound to:

- Auth-flow session
- Verified identity/account context
- Scenario `FORGOT_PASSWORD`
- Creation and expiry timestamps
- One-time-use status
- Correlation/request context as required

The frontend should receive only the minimum opaque reference or continue using the server-managed auth-flow session. Completion must consume the authorization atomically.

## 6. Pull-request checklist

- [ ] Workflow contract approved
- [ ] Shared capability reused through ports
- [ ] No Login/Registration service copied
- [ ] Policy externalized and startup-validated
- [ ] No PII/secrets in Redis keys
- [ ] OTP never logged or persisted in clear text
- [ ] CSRF and origin/referer behavior covered
- [ ] Idempotency defined for every mutation
- [ ] Rate limits and audit events defined
- [ ] Error codes, targets, statuses, and navigation defined
- [ ] Cookie lifecycle covered
- [ ] Concurrent behavior tested
- [ ] Fail-first TDD evidence included
- [ ] Operations document updated
- [ ] Commit message included

Suggested commit message for the documentation pack:

```text
docs(auth): document OTP architecture, reuse, operations, and testing
```
