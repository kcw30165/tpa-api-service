# Shared Idempotency Consolidation Implementation Plan

## 1. Decision

The BFF will replace feature-scoped and action-scattered idempotency controls with one shared idempotency capability. Every idempotent action will use the same lifecycle contract, persistence contract, terminal-response snapshot contract, compatibility rules, and safety policy.

At the end of the migration, the repository must contain only:

- the shared idempotency capability;
- action-specific canonical request material and terminal classification supplied through shared extension points;
- protected replay-state capabilities that are genuinely separate from response idempotency;
- action-focused tests and configuration.

Stale feature-specific idempotency records, ports, adapters, coordinators, serializers, key factories, replay-status enums, and duplicate lifecycle logic must be deleted after their consumers migrate.

## 2. Governing invariant

For the same action, validated idempotency key, and canonical request fingerprint, a completed replay must preserve the original terminal public response semantics without executing the protected operation again.

The persisted terminal response includes:

- HTTP classification;
- success flag;
- public API status;
- workflow next step;
- public result data;
- public messages;
- public errors, including type, code, targets, severity, and source;
- explicitly allowlisted safe headers;
- snapshot schema version.

`COMPLETED_REPLAY` is internal lifecycle metadata. It is not a public success status and must never replace the original public outcome.

## 3. Scope

The shared capability is session-context neutral and workflow neutral. It is infrastructure for any mutation or externally visible operation that requires client-key idempotency.

It supports operations executed in:

- no-session and anonymous contexts;
- pre-authentication or auth-flow sessions;
- authenticated member sessions;
- authenticated non-member or delegated sessions;
- internal or service-to-service contexts where the caller supplies an approved idempotency identity.

Current migration consumers include Registration, Login, Forgot Password, and Username Recovery, but these are the first adopters rather than the capability boundary. Future consumers can include authenticated profile mutations, account or instruction updates, document or message submissions, payment-like operations, and other protected commands.

The shared record must not depend on an auth-flow session type, authenticated-session type, workflow enum, feature package, controller DTO, or transport framework. Session binding, authorization, actor context, account context, and protected replay continuity are supplied by action adapters and remain outside the shared persistence model.

Transport failures that happen before a valid action, key, and canonical request can be formed are outside the completed-operation replay boundary. Duplicate-in-progress, key-reuse conflict, and unknown-outcome responses remain protocol outcomes and are not persisted as completed business responses.

## 4. Target architecture

```text
HTTP adapter
  -> maps transport input to action command
  -> shared idempotency executor
       -> validate action/key/fingerprint
       -> atomic reserve
       -> execute action only for ACQUIRED
       -> classify terminal / retry-safe failure / unknown
       -> persist terminal public response snapshot
       -> replay terminal public response snapshot
  -> HTTP adapter emits response and approved cookie instructions
```

The shared capability owns:

- action/key/fingerprint identity;
- atomic first-writer reservation;
- common lifecycle states;
- schema-versioned terminal response snapshots;
- safe-header validation;
- Redis key construction and serialization;
- completed replay;
- mismatch, in-progress, failed, unknown, expiry, and corrupt-snapshot behavior;
- observability vocabulary.

Each action integration supplies only:

- stable capability and action identity;
- canonical request material;
- TTL policy;
- protected operation callback;
- terminal classification when the outcome cannot be inferred from the normalized public response;
- optional opaque protected replay-state reference;
- optional session-context binding or authorization callback;
- optional approved post-response transport instructions such as cookie rotation.

The shared contract does not know whether the caller is pre-authenticated or authenticated. It accepts opaque scope and operation identities and stores no clear session identifier, actor identity, account reference, or authorization context.

## 5. Shared model

The shared lifecycle uses:

```text
PROCESSING
COMPLETED
FAILED
UNKNOWN_OUTCOME
```

Reservation outcomes use:

```text
ACQUIRED
DUPLICATE_IN_PROGRESS
COMPLETED_REPLAY
KEY_REUSED_WITH_DIFFERENT_REQUEST
FAILED_EXISTING
UNKNOWN_OUTCOME_EXISTING
```

`COMPLETED` can represent success, validation failure, business rejection, rate/cooldown rejection, known delivery rejection, or any other deterministic terminal public response.

`FAILED` is not a generic unsuccessful-response bucket. It is reserved for an explicitly approved retry-safe or non-replay state where the protected effect is proven not to have occurred.

`UNKNOWN_OUTCOME` is used when an externally visible effect may have occurred and must never trigger blind re-execution.

## 6. Snapshot contract

The shared terminal snapshot is schema-versioned and transport-neutral. It stores a canonical representation of the normalized public BFF response plus the HTTP classification and approved safe headers.

It must never persist:

- cookies or `Set-Cookie`;
- authorization headers;
- CSRF/XSRF tokens in clear storage;
- session identifiers in clear storage;
- passwords or OTP values;
- raw identity or contact values;
- raw downstream responses;
- arbitrary headers.

If workflow continuity requires sensitive state, the snapshot may carry only an opaque protected replay reference. Protected replay state remains separately encrypted or otherwise protected.

## 7. Compatibility

Existing Redis records can survive deployments during migration. The shared reader must:

1. write only the new schema after an action migrates;
2. detect legacy records by namespace and shape;
3. use a temporary action-specific legacy reader only while that action is migrating;
4. fail closed when a legacy record is ambiguous or corrupt;
5. never interpret an ambiguous legacy record as success;
6. remove the legacy reader after the maximum legacy TTL and rollout window expire.

No Redis flush or broad deletion is part of the migration.

## 8. Delivery phases and gates

### P0: Documentation and inventory

- commit this plan;
- update all capability documents to describe the target contract;
- record the complete action inventory and stale-component removal rule;
- establish acceptance criteria and sequencing.

Gate: documentation agrees on lifecycle, snapshot safety, compatibility, and deletion policy before production code changes.

### P1: Shared contract foundation

- add shared capability/action identity, key, fingerprint, lifecycle, reservation outcome, record, and terminal-response snapshot models;
- add explicit opaque operation scope without coupling to pre-authentication or authenticated session models;
- add an unpredictable reservation ownership token generated before reservation and required for every terminal transition;
- ensure duplicate outcomes never expose the stored ownership token or full persistence record;
- add canonical serializer and safe-header policy;
- add shared port and executor contracts;
- add schema compatibility tests and architecture guards;
- make no endpoint behavior changes.

Gate: shared foundation compiles and test-compiles; has no dependency on a feature, auth-flow session, authenticated-session, controller, Spring, or Redis package; and can represent anonymous, pre-authentication, authenticated, and service contexts through opaque identities.

### P2 ownership amendment

Atomic fingerprint checks alone do not prove which executor acquired `PROCESSING`. The shared record therefore stores a cryptographically unpredictable reservation token. `ACQUIRED` returns that token only to the owner; duplicate, replay, conflict, failed, and unknown outcomes return no token and no full persistence record. `complete`, `fail`, and `markUnknown` must atomically match identity, fingerprint, token, and source status.

### P2: Shared Redis implementation

- add one Redis adapter, key factory, JSON mapper, and atomic transition mechanism;
- preserve TTL and ownership checks;
- add failure sanitization, corruption handling, and concurrency tests;
- keep old adapters only as live migration dependencies.

Gate: shared adapter passes contract, atomicity, serialization, TTL, and safety tests.

### P3: Registration migration

Migrate all four registration actions as one feature slice. For every deterministic terminal outcome, prove first response equals replay response and APIM/message/session side effects run once.

Delete registration-specific idempotency infrastructure only after all four actions use the shared capability and legacy-record compatibility is addressed.

### P4: Login migration

Migrate credential/start, OTP send, OTP resend, and OTP verify. Preserve separately protected session replay where needed. Delete login-specific idempotency infrastructure after all login actions migrate.

### P5: Forgot Password migration

Migrate start, details and OTP, OTP resend, OTP verify, and complete. Preserve opaque commit references only as protected workflow state. Delete Forgot Password-specific idempotency infrastructure after all actions migrate.

### P6: Username Recovery migration

Migrate start, request, and cancel. Reuse useful versioned-codec concepts through the shared snapshot contract, then delete Username Recovery-specific idempotency infrastructure.

### P7: Repository consolidation

- remove all legacy idempotency ports, records, adapters, coordinators, serializers, key factories, feature-local replay lifecycle enums, duplicate configuration, and obsolete tests;
- retain only shared capability files and action-specific extension implementations;
- add repository-wide architecture guards preventing feature-local idempotency infrastructure;
- verify no public mapper treats `COMPLETED_REPLAY` as public success;
- update all documentation to current state.

### P8: Stabilization and release evidence

- run `mvn spotless:apply` once;
- run compile and test-compile;
- run focused behavioral, concurrency, corrupt-snapshot, compatibility, and no-repeat-side-effect tests;
- produce Redis impact, rollback, compatibility-expiry, and production monitoring evidence.

## 9. Mandatory action acceptance tests

For every migrated action and every deterministic terminal outcome:

```text
first HTTP status == replay HTTP status
first response body == replay response body
protected operation invocation count == 1
external side-effect invocation count == 1
```

Also test:

- same key and different fingerprint;
- concurrent duplicate requests;
- failure before the first side effect;
- failure after possible side-effect acceptance;
- completion persistence failure;
- corrupt snapshot;
- unsupported snapshot version;
- unsafe header rejection;
- legacy record compatibility during the rollout window;
- expiry and key reuse after expiry.

## 10. Stale-component deletion rule

A legacy component can be deleted only when:

- all of its production consumers use the shared contract;
- no Spring configuration creates it;
- no Redis key writer depends on it;
- legacy records are either readable through the approved compatibility path or have exceeded their maximum TTL and rollout window;
- replacement behavioral tests pass;
- repository search and architecture guards prove there is no remaining dependency.

Deletion is mandatory, not optional cleanup. The final repository must not retain parallel idempotency implementations.

## 11. Rollback strategy

Each feature migration is separately revertible. During migration, new and legacy readers may coexist, but only one writer is active for an action. A rollback may restore the legacy writer only while compatible legacy code and namespaces remain. P7 removal begins only after all migration rollback windows close.

## 12. Planned commit sequence

```text
docs(idempotency): plan shared capability consolidation
refactor(idempotency): add shared terminal response contract
refactor(idempotency): add shared redis lifecycle adapter
refactor(registration): migrate idempotency to shared capability
refactor(login): migrate idempotency to shared capability
refactor(forgot-password): migrate idempotency to shared capability
refactor(username-recovery): migrate idempotency to shared capability
refactor(idempotency): remove legacy feature implementations
test(idempotency): stabilize shared replay contract
```

P2-B is delivered cumulatively: the persistence foundation adds HMAC-derived keys, enforced wire-record redaction, strict schema mapping, reactive SET-NX/get/CAS primitives, and tests before the lifecycle adapter and executor are connected.

P2-B2A connects the shared persistence foundation to an ownership-checked Redis lifecycle adapter. Executor orchestration and Spring composition follow cumulatively in P2-B2B.

P2-B2B implements shared executor orchestration and conservative failure classification. Runtime Spring composition remains the final cumulative P2-B2C step.

P2-B2C completes P2 runtime composition without migrating endpoints or changing legacy adapters. P2 is ready for cumulative stabilization and focused review before its single commit.

P2 runtime composition is disabled by default to preserve behavior before migration. P3 enables the shared capability when the first Registration action family is migrated and the dedicated runtime secret is available.

<!-- P3-REGISTRATION-MIGRATION-BASELINE -->
## P3 detailed delivery plan: Registration

P3 uses one cumulative feature branch and one final commit. Intermediate stages are reviewed through timestamped evidence archives and are not committed separately unless a repair baseline is explicitly approved.

### P3-A: Shared Registration bridge

Add Registration-owned operation identity, fingerprint, response capture, replay hydration, and failure-classification components. Preserve all four existing use cases and the legacy port during this stage. Add architecture tests preventing feature leakage into the shared package.

### P3-B: Registration Start

Move Start orchestration to `IdempotencyExecutor`. Preserve captcha ordering, lookup behavior, auth-flow continuity, OTP creation, message delivery, cookie behavior, and every deterministic terminal response. Prove replay does not repeat lookup, OTP, session, or message effects.

### P3-C: OTP Resend

Move OTP Resend to the shared executor. Preserve cooldown, retry-after, send-rate-limit, maximum-attempt, invalid-flow, CSRF, delivery-failure, and system-failure semantics. Prove replay does not create or send another OTP or repeat session mutation.

### P3-D: OTP Verify

Move OTP Verify to the shared executor. Preserve invalid, expired, locked, no-eligible-trust, trust-lookup-failure, and system-failure responses. Prove OTP verification, verified-state mutation, trust lookup, and eligible-trust persistence execute once.

### P3-E: Registration Complete

Move Complete to the shared executor. Retain completion lock ordering and the existing pre-dispatch versus possible-side-effect distinction. Preserve additional-trust continuation and return-to-login behavior. Prove replay does not reacquire the lock, call APIM, refresh trusts, or repeat cancellation or completion effects.

### P3-F: Compatibility and legacy removal

The legacy key format is `{prefix}:auth:idempotency:{action}:{client-key}` and the shared format is HMAC-derived. There is no implicit cross-schema read. Before removal, choose and document one bounded policy:

1. wait for the maximum legacy TTL plus deployment safety margin before enabling the shared writer, or
2. provide a read-only, time-bounded legacy compatibility reader that never writes or renews legacy state.

Do not dual-write. Do not copy legacy `PROCESSING` ownership into the shared store. Treat ambiguous active legacy state as non-executable. Delete `RegistrationIdempotencyPort`, Registration-specific idempotency DTOs, Redis classes, configuration, and obsolete tests only after all four consumers have migrated and the bounded policy has expired.

### P3-G: Stabilization and commit

Run formatting once, restore unrelated formatter changes, compile, test-compile, execute the focused P3 behavioral suite, validate runtime composition and architecture, and package the complete evidence directory. The planned final commit subject is:

```text
refactor(registration): migrate idempotency to shared capability
```

P3-A1 completes the identity, fingerprint, and explicit classifier foundation of P3-A. Response capture and replay hydration remain P3-A2 before Registration Start orchestration changes.

P3-A2 completes the response capture and replay hydration foundation of P3-A. P3-B may next migrate Registration Start cumulatively while P3-A1 and P3-A2 bridge contracts remain action-owned.

P3-A2 is complete only with strict typed envelope decoding. The strict-envelope repair closes the substring-validation gap before P3-B.

P3-B is split into P3-B1 result and protected replay boundaries followed by P3-B2 service migration. P3-B1 introduces no Start runtime switch and retains RegistrationIdempotencyPort as the active service dependency.

P3-B1A completes the production protected replay persistence gate before P3-B2. It introduces a dedicated encrypted Redis store because the auth-flow session retains only a CSRF hash and cannot reproduce the original public token.

P3-B2A amends the shared capture contract from synchronous to reactive before Start service migration. This preserves the required protected-store-before-shared-completion ordering.

P3-B2B extracts the persistence-free Start protected operation before the coordinator switch. P3-B2C will replace the legacy Start lifecycle and wire the extracted operation through the shared executor.

P3-B2C1 completes the Start coordinator bridge without changing production routing. P3-B2C2 may now replace StartRegistrationService and Spring composition with the shared executor.

P3-B2C2A migrates the production Start bean to the shared coordinator while retaining the legacy StartRegistrationService temporarily only for existing test-harness migration. P3-B2C2B removes that legacy Start service after tests are converted.

P3-B2 is complete for Registration Start. The next migration stage targets the next Registration action while preserving the shared Start implementation and protected replay contracts.

P3-B2C2B4 closes the Start migration guard gap by remapping every stale source-text assertion to the extracted protected operation. P3-B2 can proceed to cumulative stabilization evidence.

P3-C1 extracts the persistence-free OTP Resend operation without switching production routing. P3-C2 will add typed capture and replay, followed by coordinator and test migration.

P3-C2 completes the OTP Resend typed response boundary. P3-C3 may next add the shared coordinator while retaining legacy production routing until configuration and test harnesses are migrated.

P3-C3 adds the shared OTP Resend coordinator while the legacy service remains the production bean. P3-C4 must migrate production configuration and test harnesses before deleting the old coordinator.

P3-C4A makes the shared OTP Resend coordinator the active production bean. P3-C4B will migrate unit and integration harnesses, then P3-C4C will remap source guards and remove the legacy coordinator.

P3-C4B removes legacy idempotency-port and request-hash test harnesses for OTP Resend. P3-C4C may now remap the remaining source guards and delete ResendRegistrationOtpService.

P3-C4C completes OTP Resend production and harness migration by deleting the legacy coordinator after remapping all source guards. P3-C is ready for cumulative stabilization before OTP Verify migration.

P3-D1A extracts the OTP Verify acquired workflow while production remains on VerifyRegistrationOtpService. P3-D2 will design and implement complete typed replay, including the public unregistered-trust list.

P3-D2 implements complete safe OTP Verify typed replay without a protected replay store. P3-D3 will add the shared execution bridge and coordinator while retaining legacy production routing.

P3-D3 adds the OTP Verify shared coordinator while production remains legacy. P3-D4 will capture production-switch and harness-migration contracts before routing changes.

P3-D4A switches OTP Verify production composition to shared idempotency while retaining the legacy coordinator source and legacy harnesses. P3-D4B will migrate the unit and lifecycle integration harnesses before legacy removal.

P3-D4B migrates both OTP Verify harnesses to shared execution. P3-D4C may now remap remaining source guards and remove VerifyRegistrationOtpService after a clean cumulative compile.

P3-D4C completes OTP Verify migration by remapping the APIM lookup boundary guard and deleting VerifyRegistrationOtpService. Registration Complete remains intentionally on RegistrationIdempotencyPort.

P3-E2A adds the Registration Complete protected-operation input contract without changing production routing. P3-E2B will extract lock, dispatch, continuation, and rejection behavior behind that input.

P3-E2B adds the parallel RegistrationCompleteProtectedOperation while retaining CompleteRegistrationService as the production coordinator. P3-E2C will add direct behavioral coverage before typed replay work begins.

P3-E2C completes direct protected-operation characterization. P3-E3 may now introduce the strict typed Registration Complete replay codec while production remains legacy.

P3-E3 implements the strict safe Registration Complete replay codec. P3-E4 may now introduce the shared coordinator while production remains on CompleteRegistrationService.

P3-E4 implements the parallel shared Registration Complete coordinator. P3-E5 will switch the production bean and migrate Complete harnesses only after this stage passes.

P3-E5A switches Registration Complete production composition to shared idempotency. P3-E5B will migrate remaining legacy Complete harnesses before P3-E6 removes CompleteRegistrationService and stale guards.

P3-E5B migrates Registration Complete harnesses away from the legacy coordinator. P3-E6 may remove CompleteRegistrationService and then retire the remaining legacy Registration idempotency stack and stale guards.

P3-E6 completes legacy Registration idempotency removal. P3-G is now the remaining formatting, cumulative compile/test-compile, architecture audit, documentation alignment, and single-commit gate.

P3-E6 repair retains RegistrationIdempotencyKey, RegistrationIdempotencyStartOutcome, and RegistrationIdempotencyResponseSnapshot as active shared/public compatibility vocabulary. The removed legacy scope is limited to the action-specific persistence lifecycle, coordinator, port, record/status/request-hash types, Redis lifecycle adapter, commands, and dedicated stale tests.

P3-E6 final guard cleanup remaps the Registration Complete unknown-outcome architecture assertion from the removed RegistrationIdempotencyUnknownOutcomeCommand to the shared UNKNOWN_OUTCOME_EXISTING lifecycle route. Exact audits ignore historical comments and approved negative guards while rejecting active legacy imports, declarations, mocks, constructors, and source-path dependencies. P3-E6 is complete when compile and test-compile pass.
