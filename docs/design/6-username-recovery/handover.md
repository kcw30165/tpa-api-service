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

## Model B migration sequence

```text
025A  Workflow-state application contract
025B  Workflow-state Redis persistence keyed by AuthFlowSessionId
025C  Start, Request, and Cancel orchestration migration
025D  Cookie/composition alignment and legacy-stack removal
025   Regenerated integration tests and final Maven gates
```

## Completed: Repair 025A

Repair 025A was approved and committed as:

```text
21f5c414ea1295483de6380b091c6853fb8a1641
refactor(username-recovery): add workflow state sidecar contract
```

The protected `UsernameRecoveryWorkflowState` sidecar and its revision are located under `application.dto.session.usernamerecovery`, use canonical `AuthFlowSessionId`, and passed focused 60/60 and cumulative 68/68 validation including all clean-architecture rules.

The handover completion commit is:

```text
04b51e5968b16eece229c755a116645d84b70f6a
docs(username-recovery): record repair 025A completion
```

## Completed implementation, pending commit: Repair 025B-1 v2

Repair 025B-1 v2 adds exactly three Model B Redis mapping primitives:

```text
RedisUsernameRecoveryWorkflowStateRecord
RedisUsernameRecoveryWorkflowStateJsonMapper
RedisUsernameRecoveryWorkflowStateKeyFactory
```

and their three direct tests.

The implementation:

- persists only sidecar-owned fields;
- uses `AuthFlowSessionId` directly;
- excludes scenario, CSRF, generic timestamps, expiry, generic revision, cookie data, and TTL;
- maps losslessly between the sidecar and its Redis record;
- uses safe `CacheException` messages;
- rejects malformed, invariant-breaking, and legacy session-shaped JSON;
- uses the key shape `<prefix>:username-recovery:workflow-state:<AuthFlowSessionId>`;
- adds no Redis adapter, Lua, CAS, TTL mutation, composition, or use-case migration.

Evidence directory:

```text
target/evidence/username-recovery/025B-1-v2-20260811-171341/
```

Validation:

```text
025b1-v2-fail-first:
  EXPECTED_FAILURE_CAPTURED, Maven exit 1

025b1-v2-batch1-test-compile:
  EXPECTED_FAILURE_CAPTURED, Maven exit 1

025b1-v2-test-compile:
  PASSED, Maven exit 0

025b1-v2-focused:
  FAILED, Maven exit 1, preserved

025b1-v2-focused-rerun:
  PASSED, Maven exit 0

025b1-v2-cumulative:
  PASSED, Maven exit 0
```

The final main-repository boundary is exactly:

```text
3 production files
3 test files
6 files total
```

Proposed commit:

```text
feat(username-recovery): add workflow state redis mapping
```

## Execution policy for remaining rounds

To reduce agent time and prevent output-token exhaustion:

- M365 Copilot prepares exact implementation manifests or deterministic writer scripts;
- DeepSeek V4 Flash performs preflight, executes the prepared change, validates, and reports;
- no task restatement or source drafting in reasoning;
- no broad discovery;
- at most 2 to 4 production files per round;
- first mutation within 5 minutes;
- first compile within 15 minutes;
- active agent limit of 30 minutes excluding Maven;
- one corrective attempt per distinct issue;
- a second failure requires `ESCALATION_REQUIRED`.

## Next action

1. Independently approve and commit the six Repair 025B-1 v2 Java files.
2. Update coordinator state from the exact verified implementation commit hash.
3. Prepare Repair 025B-2 as a prepared-script execution round for Redis create/load behavior.
4. Keep 025B-3, 025B-4, 025C, 025D, and canonical Task 025 unauthorized.
5. Do not restore quarantined Task 025 integration-test drafts.
6. Do not run full `mvn test` or `mvn clean verify` before the final approved milestone.
