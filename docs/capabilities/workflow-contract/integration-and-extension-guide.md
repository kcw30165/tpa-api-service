# Workflow and Error Contract Integration Guide

## 1. Start from the workflow decision

Before adding a status or error, define:

- Current stable step
- Business operation
- Success result
- Expected validation/business/security/downstream/system failures
- Whether each failure blocks the operation
- Frontend targets
- Recovery behavior
- Next step and page/variant
- HTTP semantics
- Idempotency replay/cache behavior
- Logging/audit severity

Do not start by adding a string to YAML.

## 2. Adding a new workflow operation

### Step 1 — Define command and detailed result

Create typed application command/result records and an operation-specific status enum. Keep web DTOs, application DTOs, and APIM DTOs separate.

The detailed result should carry only data needed for orchestration and mapping, such as:

- Status
- Next-step candidate
- Safe payload
- Errors/messages or enough information to produce them
- Retry timing
- Cookie-cleanup instructions where applicable

### Step 2 — Map into `WorkflowResult<T>`

For every detailed status, decide:

```text
WorkflowResultStatus
WorkflowNextStep
WorkflowPageRef
WorkflowErrorDescriptor(s)
WorkflowMessageDescriptor(s)
Payload
```

Handle every enum value explicitly. Avoid a default branch that silently maps a future status to system failure without a test.

### Step 3 — Add stable error keys

Add an error key to the appropriate feature/shared enum only when a distinct public condition is needed.

A new code should answer:

- Can the frontend handle it differently?
- Does it target a different field/page?
- Does it have different navigation or retry behavior?
- Does support/monitoring need to distinguish it?

Do not create multiple public codes for indistinguishable internal exceptions.

### Step 4 — Add the configured definition

Provide:

- Exact code
- Error type
- Severity
- Source
- Frontend targets
- Next step

If the next step needs a page, ensure the navigation map contains the required page ID and variant.

### Step 5 — Use frontend targets

Map backend/APIM fields to frontend IDs at the appropriate adapter/mapper layer.

Example:

```text
APIM: addr1|addr2
Public targets: residentialAddressLine1, residentialAddressLine2
```

Do not expose transport field names.

### Step 6 — Add messages separately

Use message descriptors for successful/non-blocking events. Preserve stable message keys where the frontend/content system resolves display text.

### Step 7 — Map detailed result to the shared contract

Use the common mutation mapper. Do not manually construct a different top-level response shape for Login, Registration, OTP, CAPTCHA, or another security workflow.

### Step 8 — Add HTTP mapping

Update the feature HTTP response helper with explicit rules for the new outcome/error. Consider:

- HTTP status
- `Retry-After`
- Cookie set/clear
- Request-ID echo
- Whether the body is replay-safe

Do not put HTTP status decisions in the application service.

### Step 9 — Add exception mapping only when needed

Expected outcomes belong in results. Add exception-handler behavior for genuinely exceptional infrastructure/application failures or reusable validation exceptions.

Ensure public messages are safe and diagnostics are sanitized.

### Step 10 — Run startup validation and tests

The application must start with the full contract and fail with a clear configuration error if the definition/navigation is incomplete.

## 3. Adding a new error to an existing workflow

Use this sequence:

1. Add or reuse the application error key.
2. Produce a `WorkflowErrorDescriptor` at the application boundary.
3. Add the configured error definition.
4. Add navigation if required.
5. Update result-to-workflow mapping.
6. Update HTTP mapping if semantics differ.
7. Add localization/content entries where applicable.
8. Add unit, startup-validation, mapper, HTTP, and frontend-contract tests.
9. Update support/error matrix documentation.

## 4. Adding a new next step or page

A new `WorkflowNextStep` is a cross-layer contract change.

Review:

- Enum addition
- Terminal/wait/restart policy
- Stable-state transition rules
- Navigation configuration
- Page ID and variant
- Frontend route/page implementation
- Error definitions that use the step
- Success actions that use the step
- Replay snapshot compatibility
- All switch expressions and tests

Prefer reusing an existing next step when the workflow intent is the same. Do not create next steps named after controller routes.

## 5. Mapping downstream APIM/TAS errors

### Explicit mapping

For every known raw downstream code define:

```text
Raw code
Failure category
Public error code
HTTP status
Frontend target(s)
Next step
Idempotency cacheable
Cache TTL
Log/event severity
```

### Fallback

Every endpoint family requires a safe fallback for unknown codes. Fallback must not expose raw downstream messages. Track whether fallback was used for monitoring and contract governance.

### Validation errors

When APIM returns field or cross-field failures:

1. Normalize the raw error type/code.
2. Split/interpret affected fields according to the APIM contract.
3. Map each field to a frontend ID.
4. Deduplicate public errors.
5. Keep blocking errors in `errors[]`.
6. Preserve `Accept-Language` behavior without returning bilingual messages in one response.

## 6. Status selection guide

### Validation failure

Use when input fails syntactic, field, cross-field, or form validation before/within the approved business operation.

### Business failure/rejection

Use when the request is valid but cannot proceed because of business state or policy—such as account lock, username taken, or an invalid workflow state that is represented as a public business condition.

### Security failure

Use approved public errors/navigation for CSRF, origin, session misuse, challenge lock, rate limiting, and anti-abuse controls. Avoid revealing sensitive validation detail.

### Downstream business rejection

Use when APIM/TAS processed the request and returned a recognized business rejection.

### Downstream/system failure

Use for unavailability, timeout, invalid downstream envelope, or provider failure.

### System failure

Use for BFF internal/infrastructure errors not more accurately classified as downstream.

### Waiting

Use when work is already in progress or the caller must wait. Include stable recovery/navigation and retry timing where applicable.

## 7. Worked example: Forgot Password

A Forgot Password workflow might define:

```text
SELECT_OTP_DESTINATION
VERIFY_OTP
PASSWORD_RESET
RETURN_TO_LOGIN
WAIT
RESTART_FLOW
```

Example public errors:

```text
err.forgotPassword.authFlow.invalid
  type: BUSINESS
  target: PAGE
  nextStep: RESTART_FLOW

err.forgotPassword.otp.invalid
  type: FIELD
  target: otpCode
  nextStep: VERIFY_OTP

err.forgotPassword.reset.rejected
  type: DOWNSTREAM_BUSINESS
  target: PAGE
  nextStep: PASSWORD_RESET

err.forgotPassword.system.unavailable
  type: SYSTEM or DOWNSTREAM_SYSTEM
  target: PAGE
  nextStep: WAIT or RESTART_FLOW
```

The exact contract requires the approved business, TAS, security, and solution-design decisions. Post-login Change Password remains a separate workflow.

## 8. Client/frontend contract

The frontend should:

- Branch on stable code/status/next-step contracts, not English text
- Render errors against `targets[]`
- Treat `PAGE` as a page/form-level condition
- Render non-blocking `messages[]` separately
- Navigate using `nextStep` and resolved page/action data
- Respect `Retry-After` and retry timing
- Restart on explicit restart/invalid-flow outcomes
- Avoid inferring success solely from HTTP 200

## 9. Pull-request checklist

- [ ] Detailed status mapped explicitly
- [ ] Correct shared workflow/API status selected
- [ ] Stable error/message keys used
- [ ] Error type, severity, source, and targets defined
- [ ] Targets are frontend IDs
- [ ] Next step and pageRef are consistent
- [ ] HTTP status/headers/cookies mapped centrally
- [ ] Raw downstream errors not exposed
- [ ] Unknown downstream fallback defined
- [ ] Idempotency replay/cache behavior reviewed
- [ ] Startup validator passes
- [ ] Mapper and HTTP contract tests added
- [ ] Frontend navigation/error handling updated
- [ ] Fail-first TDD evidence included
- [ ] Operations/error matrix updated
- [ ] Commit message included

Suggested commit message:

```text
docs(workflow): document response, error, and navigation contracts
```
