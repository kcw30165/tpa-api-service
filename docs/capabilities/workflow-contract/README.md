# Workflow and Error Contract

> **Status:** Documents the workflow-response and error-contract implementation represented by the reviewed codebase snapshot.  
> **Audience:** New team members, backend and frontend developers, reviewers, testers, support engineers, and solution designers.  
> **Scope:** Workflow results, `MutationResponse`, API status, structured errors/messages, navigation and page references, HTTP translation, downstream error classification, startup validation, and reuse.

## Start here

The codebase treats workflow outcomes as an explicit contract—not as arbitrary exceptions or controller-specific JSON. Application services return typed workflow results. Web mappers convert those results into the common mutation envelope, and HTTP response helpers translate the envelope into status codes, headers, cookies, and frontend navigation.

```text
Application service result
  WorkflowResult<T>
      + status
      + nextStep
      + payload/pageRef
      + messages
      + errors
          |
          v
WorkflowMutationResponseMapper
          |
          v
MutationResponse<WorkflowResultPayload<T>>
          |
          v
LoginHttpResponses / RegistrationHttpResponses
          |
          v
HTTP status + headers + cookies + JSON body
```

Read the documents in this order:

1. **[Architecture and contract model](architecture-and-contract-model.md)** — understand the two-layer status model, errors, messages, navigation, mapping, validation, and HTTP behavior.
2. **[Integration and extension guide](integration-and-extension-guide.md)** — follow the implementation recipe when adding a workflow step, outcome, error, or downstream mapping.
3. **[Operations and contract testing](operations-and-contract-testing.md)** — validate configuration, troubleshoot mappings, test contracts, and release changes safely.

## Core contract types

- `WorkflowResult<T>` — application-layer outcome
- `WorkflowResultStatus` — application status such as success, waiting, validation failure, business failure, or system failure
- `WorkflowNextStep` — frontend workflow direction
- `WorkflowPageRef` — resolved page ID and optional variant
- `WorkflowMessageDescriptor` — non-blocking message
- `WorkflowErrorDescriptor` — blocking or classified error
- `MutationResponse<T>` — public mutation envelope
- `ApiStatus` — public API-level status
- `ApiMessage` / `ApiError` — public message and error records

## Why the contract exists

It provides one consistent answer to these questions:

- Did the operation succeed?
- Can the user continue, wait, retry, restart, or return to Login?
- Which page and variant should the frontend display?
- Is the problem field, cross-field, form, business, downstream, security, or system related?
- Which frontend field IDs are affected?
- What HTTP status and headers should be returned?
- Is the outcome safe to cache in an idempotency replay snapshot?
- How severe is the failure and where did it originate?

## Core rules

1. Expected workflow outcomes are typed results, not uncontrolled exceptions.
2. `WorkflowResultStatus` and `ApiStatus` are related but not interchangeable.
3. Blocking failures belong in `errors[]`; non-blocking UI notifications belong in `messages[]`.
4. Error targets use frontend field IDs or `PAGE`, never APIM field names.
5. Navigation uses `nextStep` plus a resolved `pageRef` where applicable.
6. Error code, type, severity, source, targets, and navigation are contract data.
7. Unknown downstream codes use an approved fallback rather than leaking raw APIM/TAS errors.
8. HTTP mapping is centralized in response helpers, not duplicated in controllers.
9. Rate-limit responses include `Retry-After` where the mapped result provides it.
10. Startup validation must fail on missing, duplicate, malformed, or incomplete error definitions.
11. Localized display text is resolved by the approved content/message mechanism; codes remain stable.
12. Adding an enum/error key without updating contract configuration and tests is incomplete.

## Current consumers

The pattern is used across Login, Registration, OTP, CAPTCHA, username checking, completion/cancellation, and other mutation-style APIs. Registration has the most complete configured navigation and page-reference model, while Login has corresponding workflow mappers and HTTP response handling. citeturn11search44turn11search45
