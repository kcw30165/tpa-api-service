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

Repair 025B-2B added deterministic create-if-absent and load behavior, bounded initial TTL, and safe persistence failure mapping.

## Approved implementation, pending commit: Repair 025B-3

Repair 025B-3 implements atomic workflow-state revision compare-and-set update while preserving the remaining Redis TTL.

Changed boundary:

```text
M  RedisUsernameRecoveryWorkflowStateAdapter.java
M  RedisUsernameRecoveryWorkflowStateFailurePolicyTest.java
A  RedisUsernameRecoveryWorkflowStateUpdateTest.java
```

Implementation behavior:

- reuses `redis/username-recovery-session/update.lua` without modification;
- compares the stored numeric workflow-state revision with the expected revision;
- writes the complete revised workflow-state JSON only when CAS succeeds;
- preserves the existing remaining TTL through `PTTL` and `PSETEX`;
- maps script code `1` to `APPLIED` with updated state;
- maps `0` to `REVISION_CONFLICT`;
- maps `-1` and `-3` to `STATE_NOT_FOUND`;
- maps empty or unexpected codes to `SYSTEM_FAILURE`;
- maps Redis, Lua, and corrupt-record failures to safe `CacheException` messages;
- leaves delete fail-closed pending Repair 025B-4.

Evidence directory:

```text
target/evidence/username-recovery/025B-3-20260811-182820/
```

Validation:

```text
Focused:    8 tests, 0 failures, 0 errors, 0 skipped
Cumulative: 57 tests, 0 failures, 0 errors, 0 skipped
CleanArchitectureDependencyTest: 23 tests passed
Focused Maven exit code: 0
Cumulative Maven exit code: 0
git diff --check: no whitespace errors; line-ending warnings only
```

Proposed commit:

```text
feat(username-recovery): add workflow state redis cas update
```

## Next bounded task

After Repair 025B-3 is committed and verified, prepare **Repair 025B-4: workflow-state Redis delete and persistence closure** using the prepared-writer execution model.

Repair 025C, Repair 025D, and canonical Task 025 remain unauthorized.

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
