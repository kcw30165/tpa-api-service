# Message Service Integration and Reuse Guide

## 1. Start from the workflow need

Before adding delivery code, define:

- Why the message is sent
- Who is eligible to receive it
- How the destination is sourced and authorized
- Supported channels
- Template/content owner
- Required variables
- Language behavior
- Delivery timing and expiry
- Rate-limit and resend policy
- Idempotency boundary
- Failure and unknown-outcome behavior
- Audit/privacy requirements

Do not begin by calling an SMS or SMTP client.

## 2. Reuse decision

### Use `MessageDeliveryPort` when

- A workflow needs transactional SMS or email
- Existing template rendering can express the content
- `ACCEPTED`/`FAILED` is sufficient at the application boundary
- Existing provider ports support the channel

### Add a sender adapter when

- A new SMS/email provider is introduced
- Provider authentication or wire format changes
- Existing application delivery semantics remain valid

### Extend the central capability only when

- A genuinely new channel is required
- Delivery needs a richer lifecycle such as delivery callbacks
- Template rendering needs a new cross-cutting feature
- Attachment, binary, or rich-content behavior cannot fit current contracts

A new workflow or template is not by itself a reason to change `MessageDeliveryAdapter`.

## 3. Integrating a workflow

### Step 1 — Define the message scenario

Use an existing scenario or add a reviewed scenario value. The scenario identifies business context for policy, audit, and templates; it must not contain customer identifiers.

### Step 2 — Resolve an approved destination

Create or reuse a workflow-specific destination-resolution port. Keep separate values for:

- Raw recipient used internally for sending
- Opaque destination ID used by frontend selection
- Channel
- Masked display value
- Irreversible hash for rate limiting/binding

Never accept an arbitrary raw destination from the frontend for a protected OTP flow.

### Step 3 — Define templates and ownership

For each channel/language, define:

- Template content
- Email subject
- Required variables
- Content owner and approval
- Length/encoding constraints
- Expiry wording
- Support/contact wording if applicable

OTP templates must include `{code}` and should use configured TTL variables instead of hard-coded expiry text.

### Step 4 — Build `MessageDeliveryCommand`

Supply:

- Scenario
- Channel
- Authorized recipient
- Template/subject
- Variables
- Language where supported
- Correlation ID

Do not pass session ID, CSRF token, password, or idempotency key as template variables.

### Step 5 — Call the shared port

The workflow service calls `MessageDeliveryPort`. It interprets the normalized result and owns the subsequent workflow transition.

```text
ACCEPTED
  -> bind/update workflow state
  -> complete idempotency
  -> return masked destination

FAILED
  -> invalidate/avoid binding sensitive challenge as required
  -> mark idempotency failed or unknown according to outcome
  -> return scenario-specific downstream error
```

### Step 6 — Handle idempotency before delivery

The caller must reserve idempotency before invoking delivery. Replaying a completed request should replay the safe response rather than send another message.

Use a new idempotency key for a new delivery action. Same key plus different request semantics must be rejected.

### Step 7 — Apply rate limits and resend policy

Rate limit before delivery using opaque/hashed bucket identifiers. For OTP:

- Enforce cooldown
- Enforce maximum resend attempts
- Decide whether previous code is invalidated
- Decide whether verify attempts reset
- Return `retryAfterSeconds` where applicable

The Message Service does not enforce these workflow policies itself.

### Step 8 — Map failures

Define a matrix:

```text
Sender/provider outcome
  -> MessageDeliveryStatus
  -> workflow result status
  -> public error code
  -> API/HTTP status
  -> retry/restart behavior
  -> audit/log severity
```

Do not expose raw provider messages to the frontend.

## 4. Adding or replacing an SMS provider

1. Confirm provider contract, authentication, timeout, duplicate behavior, and response semantics.
2. Keep provider wire DTOs inside the adapter.
3. Implement `SmsSenderPort`.
4. Validate configuration at startup.
5. Normalize phone numbers only according to the approved contract.
6. Map provider acceptance/failure into `SenderResult`.
7. Sanitize response/error text.
8. Add timeout, malformed-response, and uncertain-outcome tests.
9. Verify credentials and content never enter logs.
10. Run a controlled sandbox/non-production delivery test.

Do not change workflow services when a provider can be replaced behind the existing sender port.

## 5. Adding or replacing an email provider

1. Confirm SMTP/API protocol, TLS, authentication, sender identity, timeout, and relay rules.
2. Implement `EmailSenderPort` behind the existing contract.
3. Keep provider-specific message IDs and errors inside safe sender results.
4. Preserve subject/body rendering in the central delivery flow.
5. Test Unicode, line endings, header injection, malformed address, timeout, and rejection.
6. Validate that production transport security meets organizational requirements.

## 6. Worked example: Forgot Password OTP

Forgot Password should reuse the Message Service as follows:

```text
Forgot Password workflow
  -> identity/account verification
  -> return approved masked destinations
  -> user selects destination ID
  -> reserve idempotency and apply rate limit
  -> create OTP challenge
  -> call MessageDeliveryPort with FORGOT_PASSWORD scenario
  -> on acceptance, bind challenge to auth-flow session
  -> on failure, invalidate/avoid binding the challenge
  -> return masked destination and timing metadata
```

The workflow owns identity verification, destination eligibility, reset authorization, TAS/APIM password reset, and public errors. The Message Service owns rendering and provider delivery only.

Post-login Change Password remains outside the Forgot Password workflow and should not send an OTP unless separately required and approved.

## 7. Frontend contract

The frontend receives only:

- Opaque destination ID where selection is required
- Channel
- Masked destination
- Expiry/cooldown/retry metadata
- Standard workflow result and errors

It never receives provider credentials, provider raw errors, message content, unmasked destination, or OTP through an API response.

## 8. Pull-request checklist

- [ ] Workflow purpose and destination owner identified
- [ ] Approved destination resolution used
- [ ] Shared `MessageDeliveryPort` used
- [ ] Templates externalized and approved
- [ ] Required variables validated
- [ ] No session/CSRF/password/idempotency secrets in variables
- [ ] Rate limit and idempotency applied by caller
- [ ] Failure and unknown-outcome behavior defined
- [ ] Provider retry behavior explicitly reviewed
- [ ] Only masked destination exposed
- [ ] No raw provider error exposed publicly
- [ ] Logging redaction verified
- [ ] Template, adapter, workflow, and end-to-end tests added
- [ ] Fail-first TDD evidence included
- [ ] Operations document updated
- [ ] Commit message included

Suggested commit message:

```text
docs(message): document delivery architecture, reuse, operations, and testing
```
