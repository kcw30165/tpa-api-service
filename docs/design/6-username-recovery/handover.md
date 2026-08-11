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

```text
21f5c414ea1295483de6380b091c6853fb8a1641
refactor(username-recovery): add workflow state sidecar contract

cf335ddf40056db5938c1d7966936cd9d537572e
feat(username-recovery): add workflow state redis mapping

7a142957e6815d490eb586924c249671099d108b
refactor(username-recovery): carry expiry in workflow state create command

32cff70efd24a4a03cee6e32d40790b88005952b
feat(username-recovery): add workflow state redis create and load

53c354a7a23f62b88fdf47f1e185cdcef7473c92
feat(username-recovery): add workflow state redis cas update

4a7e6f2d6d10c5f6309d644e8f281b4e198f56ac
feat(username-recovery): complete workflow state redis persistence
```

Repair 025B is complete.

## Approved implementation, pending commit: Repair 025C-1

Repair 025C-1 migrates only new registered-candidate Start creation to Model B.

Changed boundary:

```text
M  StartUsernameRecoveryService.java
M  UsernameRecoveryFeatureConfig.java
M  StartUsernameRecoveryServiceTest.java
```

Implemented behavior:

- generates one canonical `AuthFlowSessionId`;
- creates the revision-aware generic `AuthFlowSession` envelope first;
- creates the `UsernameRecoveryWorkflowState` sidecar second with the same ID and expiry;
- keeps CSRF hash, lifecycle, expiry, and generic revision in the envelope;
- keeps destination, language, candidates, Scheme-Refs, and workflow revision in the sidecar;
- completes idempotency only after both records exist;
- does not create a legacy Username Recovery session for a new Start;
- compensates sidecar creation failure exactly once using revision-aware envelope deletion;
- retains legacy Start replay and CSRF rotation temporarily for Repair 025C-2.

Evidence directory:

```text
target/evidence/username-recovery/025C-1-20260811-212007/
```

Validation:

```text
Focused:    43 tests, 0 failures, 0 errors, 0 skipped
Cumulative: 85 tests, 0 failures, 0 errors, 0 skipped
Focused Maven exit code: 0
Cumulative Maven exit code: 0
git diff --check: exit code 0
```

Non-blocking repository warnings remain for Mockito dynamic-agent loading, internal/deprecated APIs, JaCoCo execution-data mismatch, and line endings.

Proposed commit:

```text
refactor(username-recovery): create start envelope and workflow sidecar
```

## Next bounded task

After Repair 025C-1 is committed and verified, prepare **Repair 025C-2: Start replay envelope and sidecar migration** using a physically verified source bundle and a deterministic prepared writer.

Repair 025C-2 is not yet authorized. Request, Cancel, Repair 025D, and canonical Task 025 remain unauthorized.

## Execution policy

- M365 Copilot prepares exact manifests and deterministic writer scripts.
- DeepSeek executes prepared changes only; Maven validation may be run outside Cline when its command timeout is incompatible with synchronous evidence execution.
- No task restatement, broad discovery, or source drafting in agent reasoning.
- One corrective attempt per distinct issue; a second failure requires `ESCALATION_REQUIRED`.
- Maven must run synchronously through `.cline/scripts/run-maven-evidence.ps1`.
- Native stderr warnings must not override Maven's actual exit code.
- Do not restore quarantined Task 025 drafts.
- Do not run full `mvn test` or `mvn clean verify` before the final approved milestone.
