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

Repair 025B-1 added the workflow-state Redis record, JSON mapper, key factory, and direct tests.

### Repair 025B-2A

```text
7a142957e6815d490eb586924c249671099d108b
refactor(username-recovery): carry expiry in workflow state create command
```

Repair 025B-2A introduced `UsernameRecoveryWorkflowStateCreateCommand`, carrying the generic envelope expiry across the persistence boundary without adding expiry or TTL to the sidecar aggregate.

## Approved implementation, pending commit: Repair 025B-2B

Repair 025B-2B implements Redis create-if-absent and load behavior for the workflow-state sidecar.

Changed boundary:

```text
Production:
  RedisUsernameRecoveryWorkflowStateAdapter.java
  UsernameRecoveryWorkflowStateMutationOutcome.java
  UsernameRecoveryWorkflowStateMutationResult.java

Tests:
  RedisUsernameRecoveryWorkflowStateCreateLoadTest.java
  RedisUsernameRecoveryWorkflowStateFailurePolicyTest.java
```

Implemented behavior:

- computes initial sidecar TTL from the adapter clock to the envelope expiry transported by the create command;
- rejects zero or negative TTL before Redis access;
- creates the sidecar atomically through the existing create-if-absent Redis script;
- maps successful creation to `CREATED` with state;
- maps an existing sidecar to deterministic `STATE_ALREADY_EXISTS` without state;
- maps empty and unknown script outcomes to typed `SYSTEM_FAILURE`;
- loads valid JSON as `LOADED` and missing keys as `STATE_NOT_FOUND`;
- preserves safe mapper and Redis failure messages;
- leaves update and delete fail-closed pending later repairs.

Evidence directory:

```text
target/evidence/username-recovery/025B-2B-20260811-181015/
```

Validation:

```text
Focused:   8 tests, 0 failures, 0 errors, 0 skipped
Cumulative: 59 tests, 0 failures, 0 errors, 0 skipped
CleanArchitectureDependencyTest: 23 tests passed
git diff --check: no whitespace errors
```

Proposed commit:

```text
feat(username-recovery): add workflow state redis create and load
```

## Next bounded task

After the Repair 025B-2B commit is verified, prepare **025B-3: workflow-state Redis CAS update and TTL preservation** using the prepared-writer execution model.

025B-4, 025C, 025D, and canonical Task 025 remain unauthorized.

## Execution policy

- M365 Copilot prepares exact implementation manifests and deterministic writer scripts.
- DeepSeek V4 Flash performs preflight, executes the prepared change, validates, and reports.
- No task restatement, broad discovery, or source drafting in reasoning.
- First mutation within 5 minutes and first Maven stage within 15 minutes.
- Active-agent limit is 30 minutes excluding Maven.
- One corrective attempt per distinct issue.
- A second failure requires `ESCALATION_REQUIRED`.
- Do not restore quarantined Task 025 drafts.
- Do not run full `mvn test` or `mvn clean verify` before the final approved milestone.
