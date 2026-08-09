# Username Recovery Handover

## Current stage

Canonical Task 021, **Cancel Username Recovery Use Case**, has completed its contract-sufficiency audit with:

```text
TASK_021_CONTRACT_SUFFICIENT
```

Task 021 implementation is ready to be authorized on the exact committed and verified Task 020 baseline recorded in `.cline/username-recovery-state.md`. No Task 021 production or test files have been created yet, no Maven stage was launched during the audit, and Task 022 has not started.

Audit evidence:

```text
target/evidence/username-recovery/021-cancel-use-case-audit-20260809-235220/
```

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
| 021 | Cancel Username Recovery use case | Contract sufficient, implementation not yet committed |
| 022 | Web DTOs and web mappers | Not started |
| 023 | Feature web mappers | Not started |
| 024 | Controllers | Not started |
| 025 | Integration and stabilization | Not started |

## Verified recent baseline chain

```text
7a6e0361eb9515b3251473a141d5034f113d313d
feat(username-recovery): implement start use case

7e7a515215c7184155d6ab57af6fa83ba408beb1
feat(username-recovery): add effective destination resolution
```

The exact full Task 020 commit hash and current `trackedBaselineHead` are authoritative in:

```text
.cline/username-recovery-state.md
```

Task 021 must start only when that exact Task 020 baseline is clean and matches `HEAD`.

## Canonical workflow decisions

- Username Recovery is a pre-login workflow and does not use OTP.
- A recovered username is sent through the configured delivery channel and is never returned to Angular or rendered in the browser.
- Public `STAFF_NO` maps to shared `MemberIdentityType.STAFFNO` and APIM `STAFFNO`.
- Captcha is verified through the shared captcha API. `/username-recovery/start` receives only `captchaChallengeId` and atomically consumes `VERIFIED` to `CONSUMED`.
- At least one registered scheme is required to create a recovery session.
- Zero registered schemes create no session, CSRF token, or cookie and return `RESTART_FLOW`.
- FE receives only opaque `schemeRef`, `accountEnv`, `trustCode`, and registration state.
- Sender `ACCEPTED` becomes candidate `REQUESTED` only after the required persistence transition.
- Remaining unrequested registered candidates return `SELECT_ACCOUNT`.
- The final registered candidate changes the workflow to `COMPLETED` and returns `RETURN_TO_LOGIN`.
- Unregistered candidates never block completion.
- Non-production delivery uses dedicated Username Recovery destination overrides and fails closed if the applicable override is missing.
- The protected session retains the TAS-verified destination. The effective delivery destination is resolved immediately before delivery.
- No exception is silently discarded. Expected conditions use typed outcomes; infrastructure failures propagate through sanitized persistence/application failures; uncertain delivery remains `UNKNOWN_OUTCOME`.

## Generic HTTP-response boundary

Username Recovery uses the shared response foundation under:

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

Shared helpers own common transport responses and HTTP translation. Username Recovery owns Scheme-Ref eligibility, registered/unregistered behavior, workflow lifecycle, feature error keys, next steps, schemes, masked effective destination, and completion payloads.

Do not introduce an all-purpose `UsernameRecoveryHttpResponses` duplicate. Do not migrate existing workflow response classes as part of Username Recovery.

## Protected-session and idempotency boundaries

The protected session retains only data needed after matching:

- session/scenario identity;
- lifecycle status and revision;
- CSRF hash, never the raw token;
- verified destination and channel;
- language, creation, and expiry timestamps;
- candidates with opaque Scheme-Ref, account environment, trust code, registration state, registered username, and delivery state.

The model excludes identity value, DOB, policy number, certificate number, scheme type, termination data, captcha answer, HTTP data, cookie data, and APIM DTOs.

Session persistence provides typed create/load/update/delete outcomes. Compare-and-set updates require:

```text
updated revision = expected revision + 1
```

The idempotency contract supports:

- actions `START`, `REQUEST`, and `CANCEL`;
- opaque client keys, semantic fingerprints, and operation references;
- `IN_PROGRESS`, `COMPLETED`, and stable `UNKNOWN_OUTCOME` records;
- ownership-checked completion and unknown-outcome transitions;
- safe completed replay snapshots;
- bounded expiry without TTL extension.

Replay snapshots must never contain username, raw destination, identity, DOB, raw CSRF, CSRF hash, cookie, Scheme-Ref secrets, idempotency key, fingerprint, operation reference, or raw exception data.

## Task 019 Start use case

Task 019 implements:

- Registration-compatible validation;
- semantic START fingerprinting, including normalized language;
- idempotency reservation and classification;
- verified captcha consumption before lookup;
- APIM lookup and matched/not-found/zero-registered handling;
- protected-session creation with hashed CSRF and opaque Scheme-Refs;
- secure completed replay with CSRF rotation through expected-revision CAS;
- no raw CSRF persistence;
- distinct replay revision conflict;
- propagation of unexpected programming defects to the central boundary.

Task 019 review-fix validation passed both focused and cumulative Maven stages with exit code `0`.

## Repair 020A effective destination boundary

Repair 020A adds a framework-free application outbound contract and configuration-layer implementation for resolving the actual delivery destination:

- PROD and DR return the verified member destination;
- all other environments use the dedicated Username Recovery override;
- missing or blank applicable overrides fail closed;
- Login and Forgot Password overrides are not reused;
- the protected session's verified destination remains unchanged;
- the application receives the effective destination only for delivery and masking;
- the existing `MessageDeliveryRuntimeConfig` remains defense in depth.

Repair 020A validation passed 26 focused tests and a 577-test cumulative regression.

## Task 020 Request use case

Task 020 implements:

- semantic REQUEST fingerprinting over session and Scheme-Ref semantics;
- idempotency reservation and completed replay without resend;
- origin/referer and raw-CSRF validation through the richer application boundary;
- candidate reservation through expected-revision CAS;
- effective-destination resolution through Repair 020A;
- localized Username Recovery delivery commands;
- at-most-once sender invocation;
- `ACCEPTED`, definite `FAILED`, and `UNKNOWN_OUTCOME` classification;
- session-first persistence of `REQUESTED` and workflow status;
- stable idempotency unknown outcome after uncertain sender or post-acceptance persistence failure;
- masked effective destination in the result;
- no automatic sender or CAS retry.

The final review repair additionally ensures:

- pre-sender effective-destination failure persists a definite failure instead of stranding the candidate in `RESERVED`;
- reactive delivery failure is treated conservatively as stable `UNKNOWN_OUTCOME`;
- the delivery command carries the opaque reservation operation reference as sanitized correlation metadata.

Final Task 020 focused and cumulative review-fix stages passed with Maven exit code `0`.

## Task 021 Cancel sufficiency decision

The Task 021 audit concluded that existing contracts are sufficient without a predecessor repair.

Approved conclusions:

1. A genuinely absent session returns idempotent cancellation success without requiring CSRF.
2. A present live session requires valid CSRF before mutation or deletion.
3. Session deletion has definitive `DELETED`, `ALREADY_ABSENT`, and failure outcomes.
4. Uncertain deletion must become stable `UNKNOWN_OUTCOME` without false success or automatic retry.
5. Completed replay returns cancellation success without deleting again.
6. The completed replay snapshot can be a constant versioned descriptor containing no sensitive value.
7. A different key after uncertain deletion is safe only when a fresh session load proves genuine absence; uncertainty must not be bypassed by assumption.
8. No predecessor repair is required.

Task 021 remains application-only. Cookie clearing and exact-path `Set-Cookie` behavior belong to later web/controller work.

## Next action

1. Authorize Task 021 implementation in `.cline/username-recovery-state.md` on the exact committed Task 020 baseline.
2. Start a fresh session with:

```bash
cline \
  --tui \
  --auto-approve true \
  --model deepseek-v4-flash \
  "$(cat .cline/prompts/implement-username-recovery-task021.md)"
```

3. Use exactly one new Task 021 implementation evidence directory.
4. Do not start Task 022 until Task 021 is independently reviewed and committed.

## Deferred work and risks

- Real Redis execution of feature Lua remains unproven where earlier adapter tests used mocks; cover it at the approved integration or milestone gate.
- Full `mvn test` and `mvn clean verify` remain milestone gates.
- Task 021 must not report successful cancellation after uncertain deletion.
- Task 021 must not require CSRF to replay an already completed cancellation.
- Cookie clearing remains a web/controller responsibility.
- Existing Login, Registration, Forgot Password, Captcha, and Logout response implementations remain unchanged until a separately approved refactor proves public-contract equivalence.
- `stash@{0}` remains protected and must not be inspected, applied, popped, dropped, or cleared without explicit authorization.
