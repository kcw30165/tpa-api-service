# Username Recovery Handover

## Current stage

Canonical Task 025, **Integration and Stabilization**, remains blocked pending completion of the approved Model B migration.

## Binding architecture

```text
Generic AuthFlowSession
= authoritative identity, lifecycle, CSRF, expiry, cancellation, deletion,
  generic revision, Redis envelope, and TTL

UsernameRecoveryWorkflowState sidecar
= language, verified destination, candidates, Scheme-Refs, registered usernames,
  delivery transitions, workflow projection, and workflow-state revision
```

`AuthFlowSessionId` is the sole canonical identity.

## Migration sequence

```text
025A  Workflow-state application contract
025B  Workflow-state Redis persistence keyed by AuthFlowSessionId
025C  Start, Request, and Cancel orchestration migration
025D  Cookie/composition alignment and legacy-stack removal
025   Regenerated integration tests and final Maven gates
```

## Completed and committed

### Repair 025A

```text
21f5c414ea1295483de6380b091c6853fb8a1641
refactor(username-recovery): add workflow state sidecar contract
```

### Repair 025B-1

```text
cf335ddf40056db5938c1d7966936cd9d537572e
feat(username-recovery): add workflow state redis mapping
```

### Repair 025B-2A

```text
7a142957e6815d490eb586924c249671099d108b
refactor(username-recovery): carry expiry in workflow state create command
```

### Repair 025B-2B

```text
32cff70efd24a4a03cee6e32d40790b88005952b
feat(username-recovery): add workflow state redis create and load
```

### Repair 025B-3

```text
53c354a7a23f62b88fdf47f1e185cdcef7473c92
feat(username-recovery): add workflow state redis cas update
```

## Approved implementation, pending commit: Repair 025B-4

Repair 025B-4 completes workflow-state Redis persistence with idempotent delete behavior.

Changed boundary:

```text
M  RedisUsernameRecoveryWorkflowStateAdapter.java
M  RedisUsernameRecoveryWorkflowStateFailurePolicyTest.java
A  RedisUsernameRecoveryWorkflowStateDeleteTest.java
```

The committed `UsernameRecoveryWorkflowStateDeleteResult` already satisfied the required non-null, delete-outcome, and safe `toString()` contract, so no production diff was necessary for that type.

Implemented behavior:

- reuses `redis/username-recovery-session/delete.lua` without modification;
- deletes the workflow-state sidecar by canonical `AuthFlowSessionId`;
- maps script result `1` to `DELETED`;
- maps `-1` to idempotent `ALREADY_ABSENT`;
- maps empty and unexpected script results to `SYSTEM_FAILURE`;
- maps Redis and Lua failures to safe `CacheException` messages;
- exposes no Redis key, session ID, Scheme-Ref, username, stored JSON, or raw Redis detail;
- closes the persistence adapter with create, load, CAS update with TTL preservation, and idempotent delete.

Evidence directory:

```text
target/evidence/username-recovery/025B-4-20260811-184532/
```

Validation:

```text
Focused:    9 tests, 0 failures, 0 errors, 0 skipped
Cumulative: 63 tests, 0 failures, 0 errors, 0 skipped
CleanArchitectureDependencyTest: 23 tests passed
Focused Maven exit code: 0
Cumulative Maven exit code: 0
git diff --check: no whitespace errors; line-ending warnings only
```

Proposed commit:

```text
feat(username-recovery): complete workflow state redis persistence
```

## Next bounded task

After Repair 025B-4 is committed and verified, Repair 025B is complete. Prepare **Repair 025C-1: Start envelope and sidecar creation**, using a physically verified source bundle and the prepared-writer execution model.

Repair 025C-2 and later rounds, Repair 025D, and canonical Task 025 remain unauthorized.

## Execution policy

- M365 Copilot prepares exact manifests and deterministic writer scripts.
- DeepSeek V4 Flash performs preflight, executes prepared changes, validates, and reports.
- No task restatement, broad discovery, or source drafting in reasoning.
- First mutation within 5 minutes and first Maven stage within 15 minutes.
- Active-agent limit is 30 minutes excluding Maven.
- One corrective attempt per distinct issue.
- A second failure requires `ESCALATION_REQUIRED`.
- Do not restore quarantined Task 025 drafts.
- Do not run full `mvn test` or `mvn clean verify` before the final approved milestone.
