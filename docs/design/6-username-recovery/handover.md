# Username Recovery Handover

## Current stage

Task 021, **Cancel Username Recovery Use Case**, is implemented, independently reviewed, repaired directly, and approved after final focused and cumulative validation.

Final review-fix evidence:

```text
target/evidence/username-recovery/021-cancel-use-case-20260810-002721/
task021-review-fix-focused-6: PASSED, Maven exit 0
task021-review-fix-cumulative-6: PASSED, Maven exit 0
```

Task 021 must be committed with:

```text
feat(username-recovery): implement cancel use case
```

After that commit, the exact full Task 021 hash becomes the authoritative `trackedBaselineHead` for Task 022.

Canonical Task 022, **Username Recovery Web DTOs and Web Mappers**, has a complete `.cline` package and is proposed for an audit-only sufficiency session. Task 022 implementation is not authorized until the audit result is reviewed. Tasks 023 through 025 have not started.

The full Username Recovery feature remains incomplete. Existing Login, Registration, Forgot Password, Captcha, and Logout response implementations remain outside the migration boundary.

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
| 021 | Cancel Username Recovery use case | Approved after direct review fixes; commit pending/current |
| 022 | Web DTOs and web mappers | Package ready; audit not yet reviewed |
| 023 | Feature web mappers | Not started |
| 024 | Controllers | Not started |
| 025 | Integration and stabilization | Not started |

## Authoritative baseline

The exact full Task 021 commit hash must be recorded in:

```text
.cline/username-recovery-state.md
```

Task 022 audit and implementation must verify:

```text
HEAD == trackedBaselineHead
previousTask = 021-cancel-use-case
previousTaskStatus = COMMITTED_AND_VERIFIED
previousTaskCommitSubject = feat(username-recovery): implement cancel use case
```

Never infer the baseline from abbreviated history or evidence timestamps.

## Canonical workflow decisions

- Username Recovery is pre-login and does not use OTP.
- A recovered username is delivered only through the configured sender and is never returned to Angular.
- Public `STAFF_NO` maps at the web boundary to shared `MemberIdentityType.STAFFNO` and APIM `STAFFNO`.
- `/start` receives `captchaChallengeId` only and atomically consumes a `VERIFIED` captcha.
- Zero registered schemes create no protected session, CSRF token, or cookie and return `RESTART_FLOW`.
- FE receives only opaque Scheme-Ref, account environment, trust code, and registration state.
- Sender `ACCEPTED` becomes candidate `REQUESTED` only after the required persistence transition.
- The final registered candidate changes the workflow to `COMPLETED` and returns `RETURN_TO_LOGIN`.
- Non-production delivery uses dedicated Username Recovery overrides and fails closed when the applicable override is missing.
- The protected session retains the TAS-verified destination; the effective destination is resolved immediately before delivery.
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

Shared helpers own transport responses and HTTP translation. Username Recovery owns feature next steps, error keys, Scheme-Ref behavior, scheme projections, workflow status, and masked effective-destination payloads.

Do not introduce an all-purpose `UsernameRecoveryHttpResponses` duplicate. Existing Login, Registration, Forgot Password, Captcha, and Logout response classes remain unchanged.

## Protected session, idempotency, and replay

The protected session stores only workflow data needed after matching:

- scenario, status, revision, language, timestamps;
- CSRF hash, never raw CSRF;
- verified destination and channel;
- minimal candidates with opaque Scheme-Ref, environment, trust code, registration state, registered username, and delivery state.

The session excludes raw identity, DOB, policy/certificate/scheme termination data, captcha answer, cookie, and APIM DTOs.

Idempotency supports `START`, `REQUEST`, and `CANCEL`, opaque keys/fingerprints/operation references, `IN_PROGRESS`, `COMPLETED`, stable `UNKNOWN_OUTCOME`, ownership-checked transitions, and bounded expiry.

Replay snapshots must never contain username, raw destination, identity, DOB, raw CSRF, CSRF hash, cookie, Scheme-Ref secret, idempotency key, fingerprint, operation reference, or raw exception text.

## Task 019 Start summary

Task 019 implements validation, semantic START fingerprinting including language, captcha consumption, lookup, zero-registered handling, protected-session creation, Scheme-Refs, hashed CSRF, and secure completed replay with CSRF rotation through expected-revision CAS.

Raw CSRF is never persisted. Replay revision conflict remains distinct. Unexpected Java `Error` values propagate to the central boundary.

## Task 020 Request summary

Task 020 implements:

- semantic REQUEST fingerprinting and idempotency classification;
- origin/referer and raw-CSRF validation through the richer application method;
- candidate reservation through expected-revision CAS;
- effective-destination resolution through Repair 020A;
- localized username delivery with opaque correlation metadata;
- at-most-once sender invocation;
- accepted, definite failure, and stable unknown-outcome handling;
- session-first persistence of `REQUESTED` and workflow status;
- completed replay without resend;
- masked effective destination only.

The final direct review fix ensures pre-sender destination-resolution failure is settled as definite failure, reactive delivery failure becomes stable unknown outcome, and the candidate is never stranded in `RESERVED`.

## Task 021 Cancel final behavior

Task 021 implements:

- a committed inbound-port method that fails closed when raw security context is unavailable;
- a richer application method receiving raw CSRF and origin/referer as transient parameters;
- CANCEL semantic fingerprinting and idempotency classification;
- absent-session idempotent success without CSRF or origin/referer requirements;
- live-session origin/referer and CSRF validation before deletion;
- definitive `DELETED`/`ALREADY_ABSENT` success;
- stable unknown outcome after uncertain load, deletion, or completion;
- exactly one unknown-outcome settlement attempt;
- completed replay without another load or deletion;
- replay validation for action, key, status, and constant versioned descriptor;
- no cookie or HTTP behavior in the application layer;
- Java `Error` propagation and no automatic retry.

Approved Task 021 changed-file boundary:

```text
4 new production application files
5 new test files
0 predecessor production files modified
```

Final validation:

```text
task021-review-fix-focused-6: PASSED, Maven exit 0
task021-review-fix-cumulative-6: PASSED, Maven exit 0
```

## Task 022 boundary

Task 022 owns only web DTOs and stateless web mappers.

Required public Start request fields:

```text
identityType
identityValue
dateOfBirth
email
mobile
captchaChallengeId
```

`/request` and `/cancel` have empty request bodies. Do not invent body DTOs for their header/cookie inputs.

Task 022 must provide safe public projections for:

- Start: CSRF token, expiry, schemes, and approved next step;
- Request: workflow status, Scheme-Ref, masked destination, destination type, acceptance flag, and next step;
- Cancel: `RETURN_TO_LOGIN` with null data.

Task 022 must not:

- choose HTTP status;
- create `ResponseEntity` or controller flows;
- read headers/cookies from request objects;
- write or clear cookies;
- catch application exceptions;
- create `UsernameRecoveryHttpResponses`;
- modify application/domain contracts;
- implement Task 023 response policies or Task 024 controllers.

Public DTOs must never contain username, raw destination, session ID, cookie, CSRF hash, identity value, DOB outside the inbound request, idempotency values, operation references, or exception details.

## Next action

1. Commit Task 021 if not already committed and record its full hash.
2. Install the Task 022 package.
3. Set coordinator state to `TASK_022_AUDIT_AUTHORIZED` using the exact Task 021 hash.
4. Start a fresh Task 022 audit session.
5. Do not run the Task 022 implementation prompt until the audit result is independently reviewed.
6. Do not start Task 023.

## Deferred work and risks

- Full `mvn test` and `mvn clean verify` remain milestone gates.
- Real Redis execution of feature Lua remains unproven where earlier tests used mocks.
- Task 022 must keep Task 023 HTTP-response policy and Task 024 controller/cookie concerns out of DTOs and mappers.
- Cookie clearing must use the exact Username Recovery path in later controller work.
- Existing workflow response implementations remain unchanged until a separate cross-workflow refactor proves contract equivalence.
- `stash@{0}` remains protected and must not be inspected, applied, popped, dropped, or cleared without explicit authorization.
