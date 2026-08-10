# Username Recovery Handover

## Current stage

Canonical Task 023, **Username Recovery Feature Web Mappers**, has completed implementation and independent review.

Review decision:

```text
TASK_023_APPROVED
```

Task 023 implements an exhaustive, typed feature response-policy boundary around the committed Task 022 projections. The implementation is additive and remains uncommitted until the approved commit step is completed.

Validation evidence:

```text
target/evidence/username-recovery/023-feature-web-mappers-20260810-123040/

task023-focused-pass-repair3: PASSED, Maven exit 0, 40 tests
task023-cumulative-regression: PASSED, Maven exit 0, 581 tests
```

The complete Maven milestone remains pending. A full `mvn test` was not run during Task 023 because it is the separately controlled gate between Tasks 023 and 024. `mvn clean verify` remains deferred until after Task 025.

The intended Task 023 commit subject is:

```text
feat(username-recovery): add feature response policies
```

The exact full Task 023 commit hash becomes authoritative only after that commit and must then be recorded in `.cline/username-recovery-state.md`.

Task 024, **Username Recovery Controllers**, has not started. Do not authorize Task 024 implementation until Task 023 is committed, the full-test milestone passes, and the complete Task 024 package and controller-boundary sufficiency audit are reviewed.

## Completed implementation sequence

| Task | Scope | Current state |
|---|---|---|
| 001 | Shared idempotency and system HTTP responses | Committed |
| 002 | HTTP status classification and override | Committed |
| 003 | Cookie and required-header HTTP responses | Committed |
| 004 | Typed workflow HTTP response translation | Committed |
| 005 | Username Recovery error keys | Committed |
| 006 | Username Recovery domain models | Committed |
| 007 | Application commands and results | Committed |
| 008 | APIM transport DTOs and mapping boundary | Committed |
| 009 | TRPSearchMember APIM adapter and lookup port | Committed |
| 010 | Protected session domain model | Committed |
| 011 | Session persistence outbound ports | Committed after review repair |
| 012 | Redis protected-session adapter | Committed after review repair |
| 013 | Idempotency persistence outbound port | Committed after ownership-reference repair |
| 014 | Redis idempotency adapter | Committed |
| 015 | CSRF and protected-session reservation integration | Committed after review repair |
| 016 | Username Recovery configuration extension | Committed after review repair |
| 017 | Inbound use-case ports | Committed |
| 018 | Lookup outbound-port reconciliation | Committed and verified |
| 018A | Command-context contract repair | Committed and verified |
| 019A | Inbound-port architecture guard reconciliation | Committed and verified |
| 019B | Secure Start replay contract | Committed and verified |
| 019 | Start Username Recovery use case | Committed and verified after review fix |
| 020A | Effective destination resolution | Committed and verified |
| 020 | Request Username Recovery use case | Committed and verified after review fix |
| 021 | Cancel Username Recovery use case | Committed and verified after direct review fixes |
| 022A | Use-case architecture guard scoping | Committed and verified |
| 022 | Web DTOs and web mappers | Committed and verified after Repair 022A |
| 023 | Feature web response policies | Approved; commit and full-test milestone pending |
| 024 | Controllers | Not started |
| 025 | Integration and stabilization | Not started |

## Authoritative baseline chain

The committed predecessor values used for Task 023 were:

```text
Repair 022A:
7a4a60441ce3fc50938bf08f9e2d20672454026a
test(username-recovery): scope use case architecture guards

Task 022:
9616016aec91ca5fcac00cf5ba6a1a01de0e8639
feat(username-recovery): add web DTOs and mappers

Task 023 implementation baseline:
1cfa7f94cc6a0e1b93d8b0a9cb79ba4963997367
docs(username-recovery): update handover after task 022
```

After Task 023 is committed, the coordinator must use:

```text
previousTask = 023-feature-web-mappers
previousTaskStatus = COMMITTED_AND_VERIFIED
previousTaskCommitSubject = feat(username-recovery): add feature response policies
previousTaskCommitHash = <full Task 023 commit hash>
trackedBaselineHead = <full clean current HEAD accepted for the milestone>
```

Do not infer the Task 023 hash from evidence timestamps or the pre-implementation baseline.

## Canonical workflow decisions

- Username Recovery is pre-login and does not use OTP.
- A recovered username is delivered only through the configured channel and is never returned to Angular.
- Public `STAFF_NO` maps at the web boundary to shared `MemberIdentityType.STAFFNO` and APIM `STAFFNO`.
- `/start` receives `captchaChallengeId` only and atomically consumes a `VERIFIED` captcha.
- Zero registered schemes create no protected session, CSRF token, or cookie and return `RESTART_FLOW`.
- FE receives only opaque Scheme-Ref, account environment, trust code, and registration state.
- Sender `ACCEPTED` becomes candidate `REQUESTED` only after the required persistence transition.
- The final registered candidate changes the workflow to `COMPLETED` and returns `RETURN_TO_LOGIN`.
- Non-production delivery uses dedicated Username Recovery overrides and fails closed when the applicable override is missing.
- The protected session retains the TAS-verified destination. The effective delivery destination is resolved immediately before delivery.
- Expected conditions use typed outcomes. Unexpected programming defects remain eligible for the central exception boundary.
- Delivery uncertainty remains stable `UNKNOWN_OUTCOME` and must never lead to blind resend.

## Shared HTTP-response boundary

Username Recovery uses the generic response foundation under:

```text
com.bct.ngtpa.apiservice.adapter.in.web.response
```

Shared components include:

```text
CsrfHttpResponses
CookieHttpResponses
IdempotencyHttpResponses
RequestHeaderHttpResponses
WorkflowHttpResponses
SystemHttpResponses
HttpStatusClassification
HttpStatusClassificationOverride
```

The ownership split is:

```text
Task 022:
public request/response DTOs and safe success projections

Task 023:
typed feature response policies, MutationResponse composition,
ApiStatus, messages, errors, next steps, and classification overrides

Task 024:
controllers, routes, transport extraction, ResponseEntity/reactive flow,
shared immediate-response helper orchestration, and cookie writing/clearing
```

Do not introduce an all-purpose `UsernameRecoveryHttpResponses` class. Existing Login, Registration, Forgot Password, Captcha, and Logout response implementations remain unchanged.

## Protected session, idempotency, and replay

The protected session stores only workflow data required after matching:

- scenario, workflow status, revision, language, and timestamps;
- CSRF hash, never raw CSRF;
- verified destination and channel;
- minimal candidates with opaque Scheme-Ref, account environment, trust code, registration state, registered username, and delivery state.

The session excludes raw identity, DOB, policy/certificate/scheme termination data, captcha answer, cookie, and APIM transport DTOs.

Idempotency supports:

- actions `START`, `REQUEST`, and `CANCEL`;
- opaque client keys, semantic fingerprints, and operation references;
- `IN_PROGRESS`, `COMPLETED`, and stable `UNKNOWN_OUTCOME` records;
- ownership-checked completion and unknown-outcome transitions;
- safe completed replay snapshots;
- bounded expiry without TTL extension.

Replay snapshots must never contain username, raw destination, identity, DOB, raw CSRF, CSRF hash, cookie, Scheme-Ref secret, idempotency key, fingerprint, operation reference, or raw exception text.

## Task 019 Start summary

Task 019 implements validation, semantic START fingerprinting including language, captcha consumption, lookup, zero-registered handling, protected-session creation, opaque Scheme-Refs, hashed CSRF, and secure completed replay with CSRF rotation through expected-revision CAS.

Raw CSRF is never persisted. Replay revision conflict remains distinct. Unexpected Java `Error` values propagate to the central boundary.

## Task 020 Request summary

Task 020 implements:

- semantic REQUEST fingerprinting and idempotency classification;
- origin/referer and raw-CSRF validation through the richer application method;
- candidate reservation through expected-revision CAS;
- effective-destination resolution through Repair 020A;
- localized username delivery with opaque correlation metadata;
- at-most-once sender invocation;
- accepted, definite-failure, and stable unknown-outcome handling;
- session-first persistence of `REQUESTED` and workflow status;
- completed replay without resend;
- masked effective destination only.

The final direct review fix ensures pre-sender destination-resolution failure is settled as a definite failure, reactive delivery failure becomes stable unknown outcome, and the candidate is never stranded in `RESERVED`.

## Task 021 Cancel summary

Task 021 implements:

- absent-session idempotent success without CSRF or origin/referer requirements;
- live-session origin/referer and CSRF validation before deletion;
- definitive `DELETED` or `ALREADY_ABSENT` success;
- stable unknown outcome after uncertain load, deletion, or completion;
- exactly one unknown-outcome settlement attempt;
- completed replay without another load or deletion;
- replay validation for action, key, status, and constant versioned descriptor;
- no cookie or HTTP behavior in the application layer;
- Java `Error` propagation and no automatic retry.

Final Task 021 validation:

```text
task021-review-fix-focused-6: PASSED, Maven exit 0
task021-review-fix-cumulative-6: PASSED, Maven exit 0
```

## Repair 022A and Task 022 summary

Repair 022A scoped the Start, Request, and Cancel predecessor ArchUnit name selectors to their application packages. It changed only three architecture tests and preserved the original application-layer dependency rules.

Task 022 added eight production web DTO/mapper files and eight tests. It established:

- exactly six `/start` body fields;
- strict `STAFF_NO -> STAFFNO` mapping;
- no `/request` or `/cancel` body DTO;
- safe Start, Request, and Cancel projections;
- candidate-order and Scheme-Ref preservation;
- application-masked destination passthrough;
- no username or raw/security value in public response DTOs;
- no HTTP status, cookie, controller, Redis, APIM, message, or configuration logic.

Task 022 validation completed after Repair 022A:

```text
Focused Task 022 suite: 33 tests passed
022a-guard-scope-focused: PASSED, Maven exit 0
022a-guard-scope-cumulative: PASSED, Maven exit 0
```

## Task 023 feature response-policy result

Task 023 is purely additive and introduces five production classes:

```text
UsernameRecoveryResponsePolicy
UsernameRecoveryWorkflowMapping
UsernameRecoveryStartResponsePolicy
UsernameRecoveryRequestResponsePolicy
UsernameRecoveryCancelResponsePolicy
```

It also introduces five direct policy/mapping tests and one architecture guard.

### Typed intermediate policy

Task 023 returns a typed intermediate value containing:

```text
MutationResponse<UsernameRecoveryWorkflowResultResponse<?>> body
optional HttpStatusClassificationOverride
```

Task 024 must pass the completed body and optional override to `WorkflowHttpResponses`. Task 024 must not infer HTTP status from the first error-code string.

### Exhaustive coverage

Task 023 maps:

```text
3 Start result outcomes
11 Start exception reasons
7 Request result outcomes
11 Request exception reasons
2 Cancel result outcomes
8 Cancel exception reasons
```

Every branch has an explicit public status, next step, committed message/error key, targets, source, severity, and typed HTTP classification behavior.

### Public status normalization

Task 023 constructs only:

```text
SUCCESS
VALIDATION_FAILED
BUSINESS_REJECTED
SYSTEM_ERROR
```

It never emits:

```text
DOWNSTREAM_REJECTED
DOWNSTREAM_ERROR
```

HTTP classifications validated by Task 023 include:

```text
200 success
400 validation
403 security invalid
409 conflict
422 business rejection
503 system error and unknown outcome
```

Rate-limit `429` support remains available through the shared typed override but no committed Task 023 result/exception reason currently represents rate limiting.

### Start policy

- `SESSION_CREATED` maps to success, `SELECT_ACCOUNT`, and the Task 022 projection.
- `MEMBER_NOT_FOUND` maps to `BUSINESS_REJECTED`, `RESTART_FLOW`, and `VERIFICATION_FAILED`.
- `ZERO_REGISTERED` maps to `BUSINESS_REJECTED`, `RESTART_FLOW`, the safe schemes projection, and `label.usernamerecovery.complete.registration`.
- Validation reasons preserve committed field targets and Registration validation keys.
- Captcha and idempotency reasons map exhaustively.
- Start unknown outcome maps to `SYSTEM_ERROR`, `WAIT`, and the sanitized system-unavailable code.

### Request policy

- Accepted partial request maps to success, `SELECT_ACCOUNT`, and `label.usernamerecovery.request.accepted`.
- Accepted completed request maps to success, `RETURN_TO_LOGIN`, request-accepted and return-to-login messages.
- Already requested, workflow completed, unregistered candidate, and invalid Scheme-Ref map to their committed feature keys.
- Ordinary `SYSTEM_FAILED` maps to `SYSTEM_ERROR` and `SELECT_ACCOUNT`.
- Stable `UNKNOWN_OUTCOME` maps to `SYSTEM_ERROR` and `WAIT`.
- Unknown outcome remains distinct from ordinary system failure and never instructs blind resend.

### Cancel policy

- Success maps to `SUCCESS`, `RETURN_TO_LOGIN`, and null data.
- System failure maps to `SYSTEM_ERROR`, `RETURN_TO_LOGIN`, and the sanitized system-unavailable code.
- Validation, security, idempotency, unknown-outcome, revision-conflict, and system-failure exception reasons are explicit.
- Cookie clearing is not represented in the policy.

### Messages, errors, and sensitive data

Task 023 uses only committed keys and approved values, including:

```text
label.usernamerecovery.complete.registration
label.usernamerecovery.request.accepted
label.return.to.login
Idempotency-Key
Scheme-Ref
PAGE
BFF
SERVER
```

Public messages and errors never include rendered text, raw exception messages, username, raw destination, identity, DOB, session/cookie values, CSRF, idempotency values, sender reference, or Redis keys.

Task 023 contains no `try/catch` path. Unexpected runtime defects and Java `Error` values remain eligible for the central exception boundary.

### Architecture boundary

Task 023 contains no:

- controller or endpoint route;
- use-case invocation;
- HTTP request/header/cookie extraction;
- cookie writer or clearer;
- `ResponseEntity`, `ServerResponse`, or controller `Mono` flow;
- Redis, APIM, message, configuration, or infrastructure dependency;
- reflection, raw map, generic `Object`, or unchecked-cast contract;
- `UsernameRecoveryHttpResponses` class.

### Validation history

The final passing stages are:

```text
task023-focused-pass-repair3
PASSED, Maven exit 0
40 tests, 0 failures, 0 errors, 0 skipped

task023-cumulative-regression
PASSED, Maven exit 0
581 tests, 0 failures, 0 errors, 0 skipped
```

Earlier fail-first and focused repair stages remain preserved in the same evidence directory. The canonical fail-first stage proved the production policy classes were absent before implementation.

## Task 023 review decision

Independent review confirms:

```text
TASK_023_APPROVED
```

Approved changed-file boundary:

```text
5 new production files
6 new test files
11 files total
0 predecessor files modified
```

Proposed commit:

```text
feat(username-recovery): add feature response policies
```

## Full-test milestone

A full Maven test gate is required after Task 023 and before Task 024.

The milestone must:

- run the complete `mvn test` suite through the checked-in controlled PowerShell stage scripts;
- use one new, explicit evidence directory or an approved milestone evidence directory;
- preserve the Task 023 focused and cumulative evidence unchanged;
- capture the atomic result and Maven exit code;
- prohibit background polling loops, sleeps, PID inference, duplicate launches, and automatic retry;
- keep Task 024 blocked until the full suite passes and is reviewed.

`mvn clean verify` remains deferred until after Task 025.

## Task 024 boundary

Task 024 will own thin controllers for:

```text
POST /api/v1/auth/username-recovery/start
POST /api/v1/auth/username-recovery/request
POST /api/v1/auth/username-recovery/cancel
```

Task 024 must own:

- request body binding for `/start`;
- required header and cookie extraction;
- command construction for empty-body `/request` and `/cancel`;
- Origin/Referer and raw CSRF transport values;
- use-case invocation;
- application result/typed exception delegation to Task 023 policies;
- policy delegation through shared HTTP-response helpers;
- auth-flow and XSRF cookie creation/clearing using exact configured paths;
- `ResponseEntity` and reactive controller flow.

Task 024 must not:

- duplicate Task 023 status/message/error policy;
- inspect error strings to infer HTTP status;
- return username or raw destination;
- perform application orchestration in the controller;
- recreate generic missing-header/cookie/CSRF envelopes inline;
- introduce an all-purpose `UsernameRecoveryHttpResponses` class;
- modify existing Login, Registration, Forgot Password, Captcha, or Logout controllers.

### Task 024 transport caution

`SECURITY_CONTEXT_REQUIRED` is represented by Task 023 as an authentication-required envelope, but the committed typed classification override set has no `401` override. Task 024 must use `CookieHttpResponses` or `CsrfHttpResponses` for genuinely missing transport artifacts so the HTTP status is `401`, while preserving the approved authentication-required body.

Retry-After headers, `Set-Cookie`, and exact-path cookie clearing remain Task 024 transport concerns.

## Next action

1. Stage and commit the eleven approved Task 023 files only.
2. Capture the full Task 023 commit hash and subject.
3. Replace the repository handover with this file and commit it separately.
4. Update `.cline/username-recovery-state.md` to close Task 023 and propose the full-test milestone.
5. Run and review the full `mvn test` milestone.
6. Generate the complete Task 024 package.
7. Authorize only the Task 024 controller-boundary sufficiency audit after the milestone passes.
8. Do not start Task 024 implementation before that audit is reviewed.
9. Do not start Task 025.

## Deferred work and risks

- Full `mvn test` is still required before Task 024.
- Full `mvn clean verify` remains the final milestone after Task 025.
- Real Redis execution of feature Lua remains unproven where earlier adapter tests used mocks.
- Task 024 must preserve the ownership split between transport handling, Task 023 response policies, and application services.
- Task 024 must apply `401` through the shared missing-cookie/CSRF transport helpers where appropriate.
- Cookie clearing must use the exact Username Recovery path `/api/v1/auth/username-recovery`.
- Existing Login, Registration, Forgot Password, Captcha, and Logout response/controller implementations remain unchanged until a separately approved refactor proves public-contract equivalence.
- `stash@{0}` remains protected and must not be inspected, applied, popped, dropped, or cleared without explicit authorization.
