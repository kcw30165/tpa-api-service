# Workflow and Error Contract Architecture

## 1. Architectural intent

The workflow contract separates application decisions from web transport concerns.

```text
Domain/application service
  decides status, errors, messages, and next step
        |
Application workflow DTOs
  framework-neutral records/enums
        |
Web mapper
  maps workflow descriptors to API descriptors
        |
HTTP response helper/controller
  chooses HTTP status, headers, and cookie instructions
        |
Frontend
  renders errors/messages and follows nextStep/pageRef
```

## 2. Application workflow result

`WorkflowResult<T>` represents a complete application outcome. Its conceptual fields are:

- `status`
- `nextStep`
- `payload`
- `pageRef`
- `messages`
- `errors`

The result is immutable and validates required combinations.

### `WorkflowResultStatus`

The shared status model includes concepts such as:

```text
SUCCESS
UPDATED
WAITING
VALIDATION_FAILED
BUSINESS_FAILED
SYSTEM_FAILED
```

Feature-specific result enums may contain more detailed statuses before they are mapped into the shared workflow result.

### Success-like behavior

Success, updated, and sometimes waiting are not identical business outcomes, but they can be success-like for envelope construction. Waiting still requires an explicit next step and should not be treated as completed business success.

## 3. Public mutation envelope

`MutationResponse<T>` standardizes the public response:

```json
{
  "success": false,
  "status": "VALIDATION_FAILED",
  "result": {
    "nextStep": "VERIFY_OTP",
    "data": null,
    "pageRef": {
      "pageId": "registration-otp-verification",
      "variant": null
    }
  },
  "messages": [],
  "errors": [
    {
      "type": "FIELD",
      "code": "err.registration.otp.invalid",
      "message": null,
      "targets": ["otpCode"],
      "severity": "ERROR",
      "source": "SERVER"
    }
  ]
}
```

The example illustrates the shape; exact field serialization follows the current DTOs and configured contract.

### `ApiStatus`

Public statuses include success and mutation states such as:

- `SUCCESS`
- `UPDATED`
- `SUBMITTED`
- `NO_CHANGE`
- `PENDING_APPROVAL`
- `PARTIAL_SUCCESS`
- `VALIDATION_FAILED`
- `BUSINESS_REJECTED`
- `DOWNSTREAM_REJECTED`
- `DOWNSTREAM_ERROR`
- `SYSTEM_ERROR`

`WorkflowMutationResponseMapper` maps the application `WorkflowResultStatus` to an appropriate `ApiStatus`; for example, validation failure maps to `VALIDATION_FAILED`, business failure to `BUSINESS_REJECTED`, and system failure to `SYSTEM_ERROR`.

## 4. Error descriptors

### Application descriptor

`WorkflowErrorDescriptor` carries:

- Error type
- Stable code
- Optional safe/localized message
- Frontend targets
- Severity
- Source

### Public error

The mapper converts the application descriptor to `ApiError` without losing contract metadata.

### Error types

The codebase supports categories such as:

```text
FIELD
CROSS_FIELD
FORM
BUSINESS
SYSTEM
DOWNSTREAM_BUSINESS
DOWNSTREAM_SYSTEM
```

Use the narrowest accurate type:

- `FIELD` — one frontend field
- `CROSS_FIELD` — relationship among fields
- `FORM` — blocking form/page validation not attributable to one field
- `BUSINESS` — valid request rejected by business rules
- `DOWNSTREAM_BUSINESS` — APIM/TAS processed and rejected the business request
- `DOWNSTREAM_SYSTEM` — downstream availability/technical failure
- `SYSTEM` — BFF/internal technical failure

### Severity

Shared severities include `ERROR`, `WARNING`, and `INFO`. Blocking errors are normally `ERROR`; warnings and informational conditions are better represented as messages unless the API contract specifically requires an error record.

### Source

Source identifies the owning boundary, such as the BFF/server or APIM/TAS. It is not a free-form stack trace or internal class name.

### Targets

Targets must be frontend-facing identifiers:

```text
otpCode
username
password
captchaChallengeId
PAGE
```

APIM field names must be translated before reaching the public contract.

## 5. Error keys and validation

Error keys implement a workflow-error-key contract and are centralized in enums or constants such as Login, Registration, Shared Workflow, and Logout error keys.

`WorkflowKeyValidator` enforces key format and rejects unsafe or malformed values. Stable error codes generally follow an `err.*` convention.

Error codes are API contracts. Renaming one affects the frontend, localization/configuration, tests, monitoring, idempotency replay snapshots, and support procedures.

## 6. Configured error definitions

`WorkflowErrorContractProperties` holds configured definitions containing:

- Code
- Type
- Severity
- Source
- Targets
- Next step

It also holds navigation entries that map `WorkflowNextStep` to page ID and optional variant.

### Resolver

`WorkflowErrorContractResolver` resolves a code into `ResolvedWorkflowErrorDefinition`, including the associated `WorkflowPageRef` derived from navigation.

### Validator

`WorkflowErrorContractValidator` runs at startup and checks:

- Required workflow keys are defined
- Codes are unique
- Code format is valid
- Type, severity, source, and targets are complete
- Next step is valid
- Required navigation exists
- Page ID and variant are valid
- Definitions do not contradict the workflow contract

Failing startup is preferable to discovering a missing error/navigation definition during a customer workflow.

## 7. Navigation model

### `WorkflowNextStep`

Examples include:

```text
NONE
SELECT_OTP_DESTINATION
VERIFY_OTP
SELECT_TRUST
USERNAME_CHECK
PASSWORD_SETUP
REGISTRATION_SUCCESS
RETURN_TO_LOGIN
WAIT
RESTART_FLOW
```

The enum expresses workflow intent, not a URL.

### `WorkflowPageRef`

Carries a page ID and optional variant. Registration centralizes page references for start, OTP verification, trust selection, username checking, password setup, success, and cancellation.

### Page variants

Variants allow the same page to represent different outcomes, such as Registration success with or without remaining unregistered trusts.

### Terminal policy

Workflow terminal-step policy distinguishes terminal, restart, and waiting steps. Services and mappers should not invent page transitions independently.

## 8. Messages versus errors

`WorkflowMessageDescriptor` and `ApiMessage` represent non-blocking UI information, for example:

- OTP sent or resent
- Username available
- Registration completed
- Registration cancelled

Use `errors[]` for conditions that block or reject the operation. Do not put successful notifications into errors merely to obtain a code.

## 9. Mutation response mapping

`WorkflowMutationResponseMapper`:

1. Maps `WorkflowResultStatus` to `ApiStatus`.
2. Maps payload and `pageRef` into `WorkflowResultPayload<T>`.
3. Maps message descriptors to `ApiMessage`.
4. Maps error descriptors to `ApiError`.
5. Preserves next-step semantics.

`RegistrationWorkflowMutationResponseMapper` wraps/sharedly applies this mapping to Registration-specific result responses.

Feature web mappers translate detailed service result enums into the shared `WorkflowResult` before invoking the common mapper.

## 10. HTTP translation

`RegistrationHttpResponses` and `LoginHttpResponses` centralize response status and header rules.

Typical mappings include:

```text
200 OK                 success or approved replay
400 Bad Request        malformed/missing input
403 Forbidden          CSRF or origin/referer failure
409 Conflict           idempotency/concurrency conflict
422 Unprocessable      business/validation outcome where configured
429 Too Many Requests  rate limit, cooldown, or attempt exhaustion
502 Bad Gateway        downstream/provider failure
503 Service Unavailable system/cache/downstream availability failure
```

The exact mapping is feature/error-code-specific. Controllers should delegate rather than duplicate conditionals.

For rate-limited outcomes, response helpers add `Retry-After` when `retryAfterSeconds` is positive.

## 11. Cookie instructions versus response data

Cookie set/clear behavior is controlled by the workflow result and the web adapter, but cookie values are not stored in public errors or idempotency replay snapshots.

Examples:

- Start success writes auth-flow and XSRF cookies.
- Successful Login OTP verification writes the authenticated-session cookie and clears pre-auth cookies.
- Invalid/terminal auth flow can request cookie cleanup.
- Logout clears the authenticated cookie.

## 12. Exception boundary

Expected workflow outcomes return typed results. Exceptions remain appropriate for unexpected infrastructure or programming failures.

`ApiExceptionHandler` maps exceptions such as application errors, authentication/context failures, APIM failures, cache failures, validation errors, and unexpected errors into the common response contract. It logs sanitized diagnostics with request ID and stable error code.

Do not throw a generic exception to represent invalid OTP, username taken, cooldown active, or a known business rejection.

## 13. Downstream error classification

`DownstreamErrorClassificationPort` and its config-backed adapter map raw downstream codes using explicit mappings and fallback definitions.

A classification can include:

- Event type
- Log level
- Public response code
- HTTP status
- Frontend target
- Whether an idempotency result is cacheable
- Cache TTL
- Whether fallback was used

Raw APIM/TAS text and field names are translated before entering the public workflow contract.

## 14. Stable-state and failure classification

The broader workflow model includes:

- Failure category
- Log severity
- Transition rule
- Current and next stable steps

Conceptual transition rules include keeping the current stable state, moving to the next or terminal state, restoring a previous state, or requiring restart. This prevents every feature service from independently deciding recovery behavior.

## 15. Security and compatibility invariants

- Error codes reveal no credentials or sensitive existence information beyond approved business behavior.
- Messages/errors never contain passwords, OTPs, CSRF tokens, sessions, or raw identity data.
- Targets use frontend IDs.
- Raw downstream messages are not passed through.
- Replay snapshots preserve safe response semantics without cookies/tokens.
- Contract changes are versioned/reviewed as API behavior changes.
- Missing configuration fails startup.
- HTTP status, API status, workflow status, and next step remain internally consistent.
