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
- add canonical serializer and safe-header policy;
- add shared port and executor contracts;
- add schema compatibility tests and architecture guards;
- make no endpoint behavior changes.

Gate: shared foundation compiles and test-compiles; has no dependency on a feature, auth-flow session, authenticated-session, controller, Spring, or Redis package; and can represent anonymous, pre-authentication, authenticated, and service contexts through opaque identities.

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
