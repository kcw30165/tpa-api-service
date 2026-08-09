# Username Recovery Handover

## Current stage

Task 016, **Username Recovery Configuration Extension**, is implemented, repaired, and independently reviewed as **PASS**. Its approved configuration, message-model, runtime, YAML, and test changes remain uncommitted pending the production commit.

Tasks 001 through 015 are committed predecessors. The verified Task 015 baseline for Task 016 is:

```text
3f7f914b9f286cb7725026b6085e62e59d258f9d
feat(username-recovery): integrate CSRF session reservation
```

Task 017 has not started. The full Username Recovery feature is not yet complete. Existing Login, Registration, Forgot Password, Captcha, and Logout workflows have not been migrated to the new Username Recovery response foundation.

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
| 016 | Username Recovery configuration extension | **Approved after review repair, uncommitted** |

## Canonical workflow decisions

- Username Recovery is a pre-login workflow and does not use OTP.
- A recovered username is sent through the configured delivery channel. It is never returned to Angular or rendered in the browser.
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

Username Recovery is the first workflow to use the shared response foundation under:

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

Shared helpers own common transport responses and HTTP translation. Username Recovery continues to own Scheme-Ref eligibility, registered/unregistered behavior, workflow lifecycle, feature error keys, next steps, schemes, masked effective destination, and completion payloads.

Do not introduce an all-purpose `UsernameRecoveryHttpResponses` duplicate. Do not migrate existing workflow response classes as part of Username Recovery.

## Task 009 APIM boundary

- `UsernameRecoveryLookupPort` exposes application/domain outcomes only.
- `TrpSearchMemberAdapter` reuses the shared APIM facade, certificate, crypto, credential, envelope, request-ID, timeout, and retry infrastructure.
- Only `X-Request-Id` is propagated to APIM.
- Registered records require a nonblank TAS customer ID/username; unregistered records must not carry one.
- Duplicate `accountEnv + trustCode`, malformed success data, invalid dates, or contract violations become internal system failures.
- `DOWNSTREAM_REJECTED` is never exposed publicly.

## Tasks 010 to 012 protected-session boundary

### Domain and port

The protected session model retains only workflow data needed after matching:

- session/scenario identity;
- lifecycle status and revision;
- CSRF hash, never the raw CSRF token;
- verified destination and channel;
- language, creation, and expiry timestamps;
- candidates with opaque Scheme-Ref, account environment, trust code, registration state, registered username, and delivery state.

The model excludes identity value, DOB, policy number, certificate number, scheme type, termination data, captcha answer, HTTP data, cookie data, and APIM DTOs.

Session persistence uses typed create/load/update/delete outcomes. Compare-and-set updates require:

```text
updated revision = expected revision + 1
```

### Redis session adapter

- Create derives TTL from `expiresAt` and does not overwrite an existing record.
- Load distinguishes loaded, absent, and infrastructure failure.
- Update performs atomic expected-revision CAS and preserves remaining TTL.
- Delete is idempotent by session ID.
- Infrastructure and serialization exceptions propagate as sanitized `CacheException` failures.
- Shared `redis/auth-flow-session/create-revisioned.lua` is reused for create.
- Feature-specific `username-recovery-session/update.lua` remains because the shared replacement script requires whole-value CAS while the port supplies expected revision.
- Feature-specific `username-recovery-session/delete.lua` remains because shared deletion requires an expected whole value while the port requires idempotent deletion by session ID.

## Tasks 013 and 014 idempotency boundary

### Application contract

The Username Recovery idempotency contract supports:

- actions `START`, `REQUEST`, and `CANCEL`;
- an opaque idempotency key;
- an opaque SHA-256 semantic fingerprint;
- an opaque operation reference proving reservation ownership;
- `IN_PROGRESS`, `COMPLETED`, and stable `UNKNOWN_OUTCOME` records;
- completed replay snapshots;
- bounded expiry;
- reserve, resolve, complete, and record-unknown-outcome operations.

Only `RESERVED` carries a reservation. A persisted idempotency record includes the operation reference so completion and unknown-outcome transitions can prove ownership atomically.

### Redis idempotency adapter

Task 014 implements:

- native Redis SET-NX reservation with TTL;
- exactly one owner for an absent action/key record;
- same key plus same fingerprint classification by stored state;
- same key plus a different fingerprint as key-reused conflict, including when stored state is unknown outcome;
- native GET resolution, with `Optional.empty()` only for genuine absence;
- atomic ownership-checked completion and unknown-outcome transitions;
- completion replay-snapshot persistence;
- stable unknown outcome with no replay snapshot;
- sanitized `CacheException` propagation for Redis, serialization, and script failures.

Feature Lua policy:

- No feature `reserve.lua` is needed because reservation uses the established native SET-NX convention.
- No feature `resolve.lua` is needed because resolution uses native GET.
- One feature script remains: `redis/username-recovery-idempotency/transition.lua`.
- The transition script is justified because shared auth-flow scripts use whole-value CAS and session scripts use revision CAS, while idempotency completion must verify action, key, fingerprint, operation reference, and `IN_PROGRESS` state.
- The transition preserves remaining expiry using `PTTL` to `PSETEX` and never extends retention.

## Task 015 CSRF and protected-session boundary

Task 015 adds an application-only reservation boundary with no Redis, HTTP, cookie, APIM, configuration, or delivery dependency.

The integration:

- loads through `UsernameRecoverySessionPort` using typed load outcomes;
- rejects absent and expired sessions without mutation;
- rejects terminal workflows before CSRF or candidate mutation;
- accepts the raw CSRF token only as a service method parameter;
- validates through the existing constant-time CSRF abstraction;
- never stores the raw token in commands, results, fields, logs, exceptions, or string output;
- resolves the opaque Scheme-Ref only inside the loaded session;
- distinguishes invalid Scheme-Ref, registration required, already requested, and otherwise unavailable candidates;
- reserves through the committed aggregate transition;
- persists through expected-revision compare-and-set;
- distinguishes revision conflict from persistence failure;
- performs no automatic CAS retry.

The review repair changed the result to a minimal outcome-only projection. `UsernameRecoveryReservationResult` no longer exposes the protected session or any accessor to session ID, CSRF hash, Scheme-Ref, username, destination, or sender reference.

The review repair also removed the global exception-normalization behavior. Expected persistence errors are mapped at the session-port boundary to `SYSTEM_FAILED`, while unexpected programming defects remain eligible to reach the central exception boundary.

### Task 015 review result

**Verdict: PASS after review repair**

Evidence directory:

```text
target/evidence/username-recovery/015-csrf-session-integration-20260808-225320/
```

Approved changed-file boundary:

- 4 new production application files;
- 4 new focused application test files;
- 1 new architecture guard test;
- no existing Java, Redis/Lua, APIM, configuration, YAML, web, or controller files modified.

Validation:

```text
Initial focused suite:                  PASSED, 31 tests
Initial cumulative regression:          PASSED, 192 tests
Review-fix focused stage:               PASSED, Maven exit 0
Review-fix cumulative regression stage: PASSED, Maven exit 0
git diff --check:                       clean before repair review
```

Task 015 was validated using the controlled PowerShell Maven worker. Both repair stages completed with atomic `PASSED` results and Maven exit code `0`.

### Approved Task 015 commit

Stage only the nine approved Task 015 files and commit with:

```text
feat(username-recovery): integrate CSRF session reservation
```

After committing, verify the full Task 015 hash and use it as the `trackedBaselineHead` for Task 016.

## Task 016 configuration and delivery boundary

Task 016 extends established shared configuration and message-delivery policy without implementing Username Recovery use cases or web endpoints.

The approved configuration provides:

- `auth-workflows.username-recovery.session.ttl-seconds` with the canonical default of 600 seconds and positive validation;
- generic auth-flow Username Recovery scenario TTL aligned to 600 seconds;
- dedicated email and mobile destination overrides backed by `USERNAME_RECOVERY_MESSAGE_OVERRIDE_EMAIL` and `USERNAME_RECOVERY_MESSAGE_OVERRIDE_MOBILE`;
- dedicated Username Recovery email subject, email body, and SMS body configuration;
- required `{username}` placeholders in delivery bodies;
- `MessageDeliveryScenario.USERNAME_RECOVERY`;
- `MessageDeliveryType.USERNAME_RECOVERY`, avoiding any OTP classification;
- centralized non-production recipient replacement in `MessageDeliveryRuntimeConfig`;
- PROD and DR use of the verified member destination;
- fail-closed dedicated overrides for blank, local, DEV, SIT, UAT, QA, unknown, and every other non-production profile;
- preservation of scenario, channel, message type, subject/body, template variables, locale, and correlation metadata while changing only the recipient;
- property-name-only configuration errors with no destination or configured-value disclosure.

The review repair corrected two issues:

1. Username Recovery delivery no longer masquerades as `LOGIN_OTP`; it uses its own non-OTP message type and dedicated templates.
2. The generic auth-flow Username Recovery TTL was aligned from 900 seconds to the canonical 600 seconds.

### Task 016 review result

**Verdict: PASS after review repair**

Evidence directory:

```text
target/evidence/username-recovery/016-config-extension-20260809-001249/
```

Validation:

```text
Initial focused suite:                  PASSED, 30 tests
Initial repaired cumulative suite:      PASSED, 76 tests
Review-fix focused stage:               PASSED, Maven exit 0
Review-fix cumulative stage:            PASSED, Maven exit 0
```

The original task evidence also records and preserves invalid/failed stages resulting from a Maven argument construction defect, a focused-test type error, and the expected enum contract update. Every repair used a unique stage and the final focused and cumulative states are green.

### Approved Task 016 commit

Stage only the reviewed Task 016 production/configuration and directly corresponding test files, plus this cumulative handover, and commit with:

```text
feat(username-recovery): add workflow configuration
```

After committing, verify the full Task 016 hash and use it as Task 017's `trackedBaselineHead`.

## Task 018 outbound-port reconciliation

Task 018 reconciled the canonical outbound-port task with the `UsernameRecoveryLookupPort` introduced early during Task 009.

The reviewed boundary confirms:

- exactly one production `UsernameRecoveryLookupPort` exists;
- exactly one production implementation exists, `TrpSearchMemberAdapter`;
- the port exposes `Mono<UsernameRecoveryLookupResult> search(UsernameRecoveryLookupCommand)`;
- only Reactor and application DTOs cross the port;
- APIM, HTTP, Redis, Spring, raw maps, and generic `Object` values do not cross the boundary;
- lookup outcomes remain `MATCHED`, `NOT_FOUND`, `CONTRACT_INVALID`, `UNAVAILABLE`, and `UNKNOWN_OUTCOME`;
- `DOWNSTREAM_REJECTED` and APIM error details never enter the application boundary;
- candidate and command string output remains redacted;
- no production modification or duplicate port was required.

### Task 018 review result

**Verdict: PASS, `EXISTING_BOUNDARY_CONFIRMED`**

Evidence directory:

```text
target/evidence/username-recovery/018-outbound-ports-20260809-095751/
```

Validation:

```text
Reconciliation repair stage: PASSED, 16 tests
Existing lookup/APIM stage:   PASSED, 64 tests
Architecture regression:     PASSED, 26 tests
Consolidated:                 106 tests, 0 failures/errors/skips
```

The initial reconciliation stage failed only because the new test used an unsupported ArchUnit API. The test-only defect was repaired with a unique stage and no production code was changed.

### Approved Task 018 commit

Stage only the three new reconciliation tests and this handover, then commit with:

```text
test(username-recovery): close lookup outbound port boundary
```

After committing, verify the full Task 018 commit and use it as Task 019's `trackedBaselineHead`.

## Task 014 review result

**Verdict: PASS**

Evidence directory:

```text
target/evidence/username-recovery/014-redis-idempotency-adapter-20260808-212329/
```

Approved changed-file boundary:

- 4 new production Java files;
- 1 new Lua resource;
- 8 new focused test files;
- no existing tracked Java, configuration, YAML, application-port, domain, use-case, or web files modified.

Validation:

```text
Task 014 focused suite: 52 tests, 0 failures, 0 errors, 0 skipped
Task 014 regressions:   136 tests, 0 failures
git diff --check:       clean
Maven:                  3.9.15
Java:                   21
```

Audits passed for minimal persistence, sensitive-value redaction, framework boundaries, sanitized exception messages, ownership verification, TTL preservation, shared-script compatibility, and exact changed-file allowlist.

Not run by design:

- full `mvn test`;
- `mvn clean verify`;
- Failsafe integration tests;
- tests against a real Redis instance;
- web/controller/security tests;
- Task 015 work.

These remain milestone or later-task validations.

## Approved Task 014 commit

Stage only the 13 approved Task 014 files and commit with:

```text
feat(username-recovery): add Redis idempotency persistence
```

Do not commit `.cline/**`, `target/evidence/**`, generated review exports, or temporary compatibility files.

After committing, verify the full Task 014 hash and use that hash as the `trackedBaselineHead` for Task 015.

## Next task

Task 019 is the canonical `start-use-case` task from the implementation map. It implements `StartUsernameRecoveryService` only after Task 018 is committed and verified.

Task 019 must not implement Request or Cancel orchestration, web controllers, cookies, or HTTP mapping.

## Deferred work and risks

- Real Redis execution of `transition.lua` remains unproven in Task 014 because Redis interactions were mocked. Cover this at the next approved integration or milestone gate.
- Full-suite and clean-verify validation remain milestone gates.
- Application orchestration must preserve the distinction between ordinary system failure and `UNKNOWN_OUTCOME` to prevent duplicate sending.
- Replay snapshots must contain only the approved safe response projection and must never contain username, raw destination, identity, DOB, CSRF, cookie, Scheme-Ref secrets, or raw exception data.
- Existing Login, Registration, Forgot Password, Captcha, and Logout response implementations remain unchanged until a separately approved refactor proves public-contract equivalence.
- `stash@{0}` remains protected and must not be inspected, applied, popped, dropped, or cleared without explicit authorization.
