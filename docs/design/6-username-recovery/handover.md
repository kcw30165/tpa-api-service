# Username Recovery Handover

## Current stage

Repair 022A, **Use-Case Architecture Guard Scoping**, and canonical Task 022, **Username Recovery Web DTOs and Web Mappers**, have both passed independent review and final validation.

Repair 022A corrected three predecessor ArchUnit selectors that unintentionally included the new Task 022 web classes. The repair preserves the original application-layer dependency rules while restricting both class-name branches to the applicable application package.

Final Repair 022A evidence:

```text
target/evidence/username-recovery/022-web-dtos-mappers-audit-20260810-091923/
022a-guard-scope-focused: PASSED, Maven exit 0
022a-guard-scope-cumulative: PASSED, Maven exit 0
```

Task 022 is approved for commit after Repair 022A. The intended commit subjects are:

```text
test(username-recovery): scope use case architecture guards
feat(username-recovery): add web DTOs and mappers
```

The exact full Repair 022A and Task 022 commit hashes are authoritative in Git history and `.cline/username-recovery-state.md`. Do not infer or fabricate either hash in this handover.

Canonical Task 023, **Feature Web Mappers**, is the next proposed task. Task 023 is not authorized until its complete package and response-policy sufficiency audit are prepared and reviewed. Tasks 024 and 025 have not started.

The full Username Recovery feature remains incomplete. Existing Login, Registration, Forgot Password, Captcha, and Logout response implementations remain outside the Username Recovery migration boundary.

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
| 022A | Use-case architecture guard scoping | Approved and ready/expected to be committed separately |
| 022 | Web DTOs and web mappers | Approved after Repair 022A |
| 023 | Feature web mappers | Proposed, not authorized |
| 024 | Controllers | Not started |
| 025 | Integration and stabilization | Not started |

## Authoritative baseline

After the separate Repair 022A and Task 022 commits, the exact full Task 022 commit hash becomes the baseline for Task 023.

The coordinator state must record:

```text
previousTask = 022-web-dtos-mappers
previousTaskStatus = COMMITTED_AND_VERIFIED
previousTaskCommitSubject = feat(username-recovery): add web DTOs and mappers
trackedBaselineHead = <full Task 022 commit hash>
```

Before Task 023 begins, verify:

```text
HEAD == trackedBaselineHead
tracked Java worktree is clean
stash@{0} remains untouched
Task 023 audit is explicitly authorized
Task 023 implementation is not yet authorized
```

Never infer the baseline from abbreviated history, evidence timestamps, or the Repair 022A commit.

## Canonical workflow decisions

- Username Recovery is pre-login and does not use OTP.
- A recovered username is delivered only through the configured delivery channel and is never returned to Angular.
- Public `STAFF_NO` maps at the web boundary to shared `MemberIdentityType.STAFFNO` and APIM `STAFFNO`.
- `/start` receives `captchaChallengeId` only and atomically consumes a `VERIFIED` captcha.
- Zero registered schemes create no protected session, CSRF token, or cookie and return `RESTART_FLOW`.
- FE receives only opaque Scheme-Ref, account environment, trust code, and registration state.
- Sender `ACCEPTED` becomes candidate `REQUESTED` only after the required persistence transition.
- The final registered candidate changes the workflow to `COMPLETED` and returns `RETURN_TO_LOGIN`.
- Non-production delivery uses dedicated Username Recovery overrides and fails closed when the applicable override is missing.
- The protected session retains the TAS-verified destination. The effective destination is resolved immediately before delivery.
- Expected conditions use typed outcomes. Unexpected programming defects remain eligible for the central exception boundary. Delivery uncertainty remains `UNKNOWN_OUTCOME`.

## Shared HTTP-response boundary

Username Recovery uses shared transport helpers under:

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

Shared helpers own common transport responses and HTTP translation. Username Recovery owns feature next steps, error keys, Scheme-Ref behavior, scheme projections, workflow status, and masked effective-destination payloads.

Do not introduce an all-purpose `UsernameRecoveryHttpResponses` duplicate. Existing Login, Registration, Forgot Password, Captcha, and Logout response implementations remain unchanged.

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

- a committed inbound-port method that fails closed when raw security context is unavailable;
- a richer application method receiving raw CSRF and origin/referer as transient parameters;
- CANCEL semantic fingerprinting and idempotency classification;
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

## Repair 022A architecture guard scoping

Task 022 introduced correctly named web classes such as:

```text
UsernameRecoveryStartWebMapper
UsernameRecoveryRequestWebMapper
UsernameRecoveryCancelWebMapper
```

Three predecessor guards used unparenthesized ArchUnit selectors in which the second `.or()` name branch was not package-scoped. As a result, the guards incorrectly selected the new web classes and reported six false architecture failures.

Repair 022A scopes both name-prefix branches to their respective application packages in:

```text
UsernameRecoveryStartUseCaseArchitectureGuardTest
UsernameRecoveryRequestUseCaseArchitectureGuardTest
UsernameRecoveryCancelArchitectureGuardTest
```

Repair 022A changes no production code and no Task 022 DTO or mapper. The application-layer dependency prohibitions remain unchanged.

Final Repair 022A validation:

```text
022a-guard-scope-focused: PASSED, Maven exit 0
022a-guard-scope-cumulative: PASSED, Maven exit 0
```

## Task 022 web DTO and mapper result

Task 022 adds exactly eight production files:

```text
UsernameRecoveryStartRequest
UsernameRecoverySchemeResponse
UsernameRecoveryStartDataResponse
UsernameRecoveryRequestDataResponse
UsernameRecoveryWorkflowResultResponse
UsernameRecoveryStartWebMapper
UsernameRecoveryRequestWebMapper
UsernameRecoveryCancelWebMapper
```

Task 022 adds exactly eight corresponding tests, including a public-contract and architecture guard.

### Start request

The public `/start` body contains exactly:

```text
identityType
identityValue
dateOfBirth
email
mobile
captchaChallengeId
```

The body does not contain captcha answer, Idempotency-Key, request ID, language, cookie, CSRF, Scheme-Ref, or HTTP objects.

Identity mapping is strict:

```text
GPN      -> MemberIdentityType.GPN
GPFS     -> MemberIdentityType.GPFS
STAFF_NO -> MemberIdentityType.STAFFNO
ID       -> MemberIdentityType.ID
```

Unknown, blank, case-mutated, or whitespace-padded values fail closed.

### Start response

The Start mapper provides:

- `SESSION_CREATED -> SELECT_ACCOUNT` with CSRF token, expiry, and ordered schemes;
- `MEMBER_NOT_FOUND -> RESTART_FLOW` with null data;
- `ZERO_REGISTERED -> RESTART_FLOW` with scheme projections, null Scheme-Refs, and no session or CSRF fields.

Each scheme exposes only:

```text
schemeRef
accountEnv
trustCode
isRegistered
```

### Request response

For `REQUEST_ACCEPTED`, the Request mapper exposes only:

```text
status
schemeRef
maskedDestination
destinationType
requestAccepted
```

The mapper copies the application-masked destination and never receives or remasks a raw destination.

`PARTIALLY_REQUESTED` maps to `SELECT_ACCOUNT`. `COMPLETED` maps to `RETURN_TO_LOGIN`.

Non-accepted outcomes carry null success data and leave response-policy decisions to Task 023.

### Cancel response

Successful cancellation maps to:

```text
nextStep = RETURN_TO_LOGIN
data = null
```

Cookie clearing remains a Task 024 transport concern.

### Security and architecture

Task 022 response DTOs and mapper output exclude:

- username;
- raw destination;
- session ID and cookie;
- CSRF hash;
- identity value and DOB;
- idempotency values and operation references;
- sender reference;
- exception details.

Task 022 web classes depend only on approved Java, Jackson, application DTO/workflow, domain value, and same-package web projection types. Application and domain layers do not depend on the new web package.

Task 022 does not implement:

- HTTP status selection;
- `MutationResponse` assembly;
- exception-to-response policies;
- `ResponseEntity`, `ServerResponse`, or controller flows;
- header/cookie extraction;
- cookie writing or clearing;
- endpoint routes;
- Task 023 or Task 024 behavior.

### Validation

```text
Focused Task 022 suite: 33 tests passed
Initial cumulative regression: 671 tests, 6 false failures in predecessor guards
Repair 022A focused validation: PASSED, Maven exit 0
Repair 022A cumulative validation: PASSED, Maven exit 0
```

Evidence directory:

```text
target/evidence/username-recovery/022-web-dtos-mappers-audit-20260810-091923/
```

## Task 023 boundary

Task 023 owns typed Username Recovery response policies and response construction around the Task 022 projections.

Task 023 must determine and test:

- `MutationResponse` and shared workflow-envelope composition;
- public `ApiStatus`;
- messages and errors;
- feature and shared error keys;
- typed mapping of Start, Request, and Cancel outcomes and exceptions;
- shared helper delegation;
- HTTP classification inputs without duplicating controller behavior;
- distinct unknown-outcome guidance;
- no exposure of `DOWNSTREAM_REJECTED`;
- no all-purpose `UsernameRecoveryHttpResponses` class.

Task 023 must not own:

- endpoint routes;
- request header or cookie extraction;
- raw CSRF handling;
- cookie creation or clearing;
- application orchestration;
- Redis, APIM, delivery, or configuration logic.

Those transport/controller concerns remain in Task 024.

## Next action

1. Commit Repair 022A separately if not already committed.
2. Commit Task 022 separately and record the full Task 022 hash.
3. Commit this cumulative handover.
4. Close Task 022 in `.cline/username-recovery-state.md` using the exact Task 022 hash.
5. Generate the complete Task 023 package.
6. Authorize only the Task 023 response-policy sufficiency audit.
7. Do not start Task 023 implementation before that audit is reviewed.
8. Do not start Task 024.

## Deferred work and risks

- Full `mvn test` and `mvn clean verify` remain milestone gates.
- Real Redis execution of feature Lua remains unproven where earlier tests used mocks.
- Task 023 must preserve the ownership split between Task 022 projections, Task 023 response policy, and Task 024 controllers.
- Cookie clearing must use the exact Username Recovery path in Task 024.
- Existing Login, Registration, Forgot Password, Captcha, and Logout response implementations remain unchanged until a separately approved refactor proves public-contract equivalence.
- `stash@{0}` remains protected and must not be inspected, applied, popped, dropped, or cleared without explicit authorization.
