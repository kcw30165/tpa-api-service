# Username Recovery Handover

## Current stage

Canonical Task 024, **Username Recovery Controllers**, has completed implementation, review repair, and independent review.

Final decision:

```text
TASK_024_APPROVED
TASK_024_COMMIT_AUTHORIZED
TASK_025_PROPOSED_NOT_AUTHORIZED
```

Implementation evidence:

```text
target/evidence/username-recovery/024-controllers-20260810-210043/
```

Final review-fix validation:

```text
task024-review-fix-fail-first:
  EXPECTED_FAILURE_CAPTURED, Maven exit 1
  5 malformed/null conversion cases failed as intended

task024-review-fix-focused:
  PASSED, Maven exit 0
  49 tests, 0 failures, 0 errors, 0 skipped

task024-review-fix-cumulative:
  PASSED, Maven exit 0
  1394 tests, 0 failures, 0 errors, 0 skipped
```

Task 024 adds one thin reactive controller, three direct controller/contract/security test classes, one controller architecture guard, and an approved update to the Repair 024A prerequisite guard. The controller exposes the three canonical POST endpoints and delegates application behavior through committed inbound ports, Task 022 mappers, Task 023 response policies, shared HTTP helpers, and the Repair 024A cookie instructions.

The review repair places transport conversion inside narrow `Mono.defer` boundaries. Null or malformed Start bodies, identity types, session IDs, and Scheme-Refs map to approved typed responses. The repair does not use broad reactive exception handling and does not convert unexpected application defects or Java `Error` values into validation failures.

Task 024 may now be committed. Task 025 has not started and remains unauthorized until its complete package and sufficiency audit are prepared.

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
| 023 | Feature web response policies | Committed, approved, and full-test milestone passed |
| 024 | Controllers | Implemented, repaired, approved; commit authorized |
| 024A | Controller prerequisites | Committed and verified |
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

## Task 024 audit escalation and Repair 024A boundary

The Task 024 audit concluded `ESCALATION_REQUIRED` without modifying source or running Maven.

### Confirmed sufficient areas

- three POST endpoint paths and body shapes;
- empty-body Request and Cancel;
- `STAFF_NO -> STAFFNO` through the committed mapper;
- missing cookie/CSRF through shared 401 helpers;
- missing Idempotency-Key and Scheme-Ref through shared helpers;
- Task 023 policy translation through typed body and optional override;
- 403, 409, 422, and 503 behavior without error-string inspection;
- stable unknown outcome with `WAIT` and no blind resend;
- unexpected runtime defects and Java `Error` propagation;
- one-controller or three-controller repository conventions;
- no predecessor controller-name guard conflict.

### Repair 024A ownership

Repair 024A owns only:

1. a typed, transport-safe `AuthFlowSessionId` in the Start application result while preserving unchanged public JSON and replay secrecy;
2. a Username Recovery-specific auth-flow and XSRF cookie writer/policy using exact path, Strict SameSite, TTL 600, approved Secure behavior, and exact-path clearing;
3. richer Request and Cancel inbound-port methods carrying raw CSRF, Origin, and Referer transiently outside commands;
4. Spring composition for committed services, Task 022 mappers, Task 023 policies, and the scenario-specific cookie writer.

Repair 024A must not implement controllers, expose session ID in JSON, add raw CSRF to commands, persist raw CSRF/session ID in replay snapshots, or change cookie behavior for other workflows.

### Replay caution

The Repair 024A audit must prove that completed Start replay can provide a valid live session reference for cookie publication. If replay cannot do so without storing a forbidden session ID or returning a stale reference, the repair must stop with `ESCALATION_REQUIRED` rather than fabricate a cookie value.

## Repair 024A implementation result

### Transport-safe Start session reference

`UsernameRecoveryStartResult` now carries `AuthFlowSessionId` as a server/transport-only component. The four-argument compatibility constructor remains available for legacy non-transport fixtures. Real `SESSION_CREATED` results produced by `StartUsernameRecoveryService` contain the exact generated or replay-loaded session ID.

The public Task 022 mapper continues to project only:

```text
csrfToken
expiresInSeconds
schemes
```

Serialization tests confirm that `authFlowSessionId`, session IDs, cookies, CSRF hashes, usernames, and raw destinations do not enter public JSON. `toString()` redacts both the session ID and raw CSRF token.

### Replay preservation

The existing completed Start replay continues to decode the real protected session ID, load the live session, reject invalid terminal/expired states, rotate the CSRF hash through expected-revision CAS, and return a fresh raw token without extending TTL. Repair 024A adds the live session ID to the application result only. It does not add raw CSRF or new secret material to replay snapshots.

### Cookie policy

`UsernameRecoveryCookieWriter` is Spring-free and produces `SessionCookieInstruction` values. Task 024 will apply those instructions through `HttpSessionCookieAdapter`.

Canonical policies:

```text
auth_flow_sid
  path: /api/v1/auth/username-recovery
  maxAge: 600
  httpOnly: true
  secure: true by default
  sameSite: Strict
  domain: omitted

XSRF-TOKEN
  path: /api/v1/auth/username-recovery
  maxAge: 600
  httpOnly: false
  secure: true by default
  sameSite: Strict
  domain: omitted
```

Clear instructions use the same exact path with Max-Age 0. Existing shared Login, Registration, Forgot Password, Captcha, and Logout cookie configuration and writers are unchanged.

### Richer inbound ports

`RequestUsernameRecoveryUseCase` and `CancelUsernameRecoveryUseCase` now expose the richer four-argument method as the single abstract method:

```text
command
rawCsrfToken
origin
referer
```

The old single-argument method remains as a fail-closed default returning `SECURITY_CONTEXT_REQUIRED`. Raw CSRF, Origin, and Referer remain transient and are not added to command records.

### Spring composition

`UsernameRecoveryFeatureConfig` composes the Redis-backed session/idempotency ports, CSRF validation/reservation services, Start/Request/Cancel use cases, Task 022 mappers, Task 023 response policies, and the Spring-free cookie writer. Beans use conditional creation to avoid duplicate definitions. No controller bean is introduced.

### Validation and audit

The Repair 024A validation includes compile, focused, and cumulative stages. The cumulative regression ran 297 tests with zero failures and zero errors. Architecture checks confirm:

- no Username Recovery controller was added;
- the cookie writer remains Spring-free;
- inbound ports are framework-free interfaces;
- raw security context is absent from commands;
- public DTOs contain no session/cookie/security fields;
- configuration respects clean-architecture package rules.

Approved changed-file boundary:

```text
6 production files
8 tests/guards
14 files total
```

Proposed commit:

```text
feat(username-recovery): add controller prerequisites
```

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

## Task 024 controller result

### Endpoints

```text
POST /api/v1/auth/username-recovery/start
POST /api/v1/auth/username-recovery/request
POST /api/v1/auth/username-recovery/cancel
```

`/start` binds the committed six-field body plus language, request ID, and Idempotency-Key. `/request` and `/cancel` remain empty-body endpoints and bind the auth-flow cookie and required security/transport headers.

### Thin-controller delegation

The controller depends on:

```text
StartUsernameRecoveryUseCase
RequestUsernameRecoveryUseCase
CancelUsernameRecoveryUseCase
Task 022 web mappers
Task 023 response policies
WorkflowHttpResponses
shared missing-artifact helpers
UsernameRecoveryCookieWriter
HttpSessionCookieAdapter
```

It does not depend on concrete services, Redis, APIM, message delivery, configuration internals, or another workflow controller.

### Cookie behavior

- Start `SESSION_CREATED` writes `auth_flow_sid` and `XSRF-TOKEN` at `/api/v1/auth/username-recovery` with TTL 600 and SameSite Strict.
- All other Start outcomes write no cookie.
- Request does not write or refresh cookies.
- Cancel `SUCCESS` clears both cookies at the exact path.
- Cancel system/security failure does not clear cookies.

### Shared transport behavior

- missing auth-flow cookie uses `CookieHttpResponses` and returns 401;
- missing CSRF uses `CsrfHttpResponses` and returns 401;
- missing Idempotency-Key uses `IdempotencyHttpResponses` and returns 400;
- missing Scheme-Ref uses `RequestHeaderHttpResponses` and returns 400;
- completed typed policies are translated through `WorkflowHttpResponses` with an optional classification override;
- no error string is inspected to select HTTP status.

### Review repair

Five fail-first cases proved synchronous conversion escaped the prior reactive boundary:

```text
null Start body
unsupported Start identity type
malformed Request session ID
malformed nonblank Request Scheme-Ref
malformed Cancel session ID
```

The final implementation defers conversion before constructing commands and catches only the known conversion exceptions in that narrow scope. It maps malformed session IDs and Scheme-Refs through committed typed policies without inspecting exception messages. Unexpected use-case `RuntimeException` and Java `Error` values remain eligible for the central exception boundary.

### Validation

```text
initial focused pass: 38 tests
post-guard focused pass: 43 tests
initial cumulative repaired pass: 1388 tests
review-fix focused pass: 49 tests
review-fix cumulative pass: 1394 tests
final failures/errors/skips: 0/0/0
```

Approved changed-file boundary:

```text
1 production controller
5 test/architecture files
6 files total
```

Proposed commit:

```text
feat(username-recovery): add controllers
```

## Next action

1. Stage and commit the six approved Task 024 files only.
2. Capture the full Task 024 commit hash and subject.
3. Replace the repository handover with this generated file and commit it separately.
4. Close Task 024 in `.cline/username-recovery-state.md` using the exact Task 024 implementation hash and current clean baseline.
5. Generate the complete Task 025 integration/stabilization package.
6. Authorize only the Task 025 sufficiency audit.
7. Do not begin Task 025 implementation or run `mvn clean verify` before the audit is independently reviewed.

## Deferred work and risks

- Task 024 remains blocked until Repair 024A is implemented, reviewed, committed, and cumulatively validated.
- Start replay must not gain a persisted session ID or raw CSRF merely to support cookie publication.
- Username Recovery cookie changes must be scenario-specific and must not alter other workflows.

- Full `mvn test` is still required before Task 024.
- Full `mvn clean verify` remains the final milestone after Task 025.
- Real Redis execution of feature Lua remains unproven where earlier adapter tests used mocks.
- Task 024 must preserve the ownership split between transport handling, Task 023 response policies, and application services.
- Task 024 must apply `401` through the shared missing-cookie/CSRF transport helpers where appropriate.
- Cookie clearing must use the exact Username Recovery path `/api/v1/auth/username-recovery`.
- Existing Login, Registration, Forgot Password, Captcha, and Logout response/controller implementations remain unchanged until a separately approved refactor proves public-contract equivalence.
- `stash@{0}` remains protected and must not be inspected, applied, popped, dropped, or cleared without explicit authorization.
