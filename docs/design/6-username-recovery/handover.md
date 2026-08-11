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

## Completed and committed

```text
21f5c414ea1295483de6380b091c6853fb8a1641  workflow-state sidecar contract
cf335ddf40056db5938c1d7966936cd9d537572e  workflow-state Redis mapping
7a142957e6815d490eb586924c249671099d108b  create expiry contract
32cff70efd24a4a03cee6e32d40790b88005952b  Redis create and load
53c354a7a23f62b88fdf47f1e185cdcef7473c92  Redis CAS update
4a7e6f2d6d10c5f6309d644e8f281b4e198f56ac  complete Redis persistence
dad276bef5aabef2d241556c596c2b13e0672a75  Start envelope and sidecar creation
```

## Approved implementation, pending commit: Repair 025C-2

Repair 025C-2 migrates completed Start replay to Model B.

Changed boundary:

```text
M  StartUsernameRecoveryService.java
M  UsernameRecoveryStartReplayCodec.java
M  UsernameRecoveryFeatureConfig.java
M  StartUsernameRecoveryServiceTest.java
M  UsernameRecoveryStartReplayCodecTest.java
```

Implemented behavior:

- replay descriptors carry canonical `AuthFlowSessionId`;
- replay loads a revision-aware generic envelope and workflow-state sidecar;
- envelope scenario, status, terminal state, expiry, and sidecar identity/status are validated fail-closed;
- CSRF rotation uses revision-aware envelope CAS and preserves authoritative expiry;
- replay results are rebuilt from the envelope and sidecar;
- revision conflict is distinct and is not retried;
- Start no longer depends on the legacy `UsernameRecoverySessionPort`.

Evidence:

```text
target/evidence/username-recovery/025C-2-20260811-214807/
Focused: 60 tests, 0 failures, 0 errors, 0 skipped
Cumulative: 102 tests, 0 failures, 0 errors, 0 skipped
Focused Maven exit code: 0
Cumulative Maven exit code: 0
git diff --check: 0
```

Proposed commit:

```text
refactor(username-recovery): migrate start replay to model b
```

## Next bounded task

After this commit is verified, prepare the next Request orchestration migration as a complete Git-controlled `.cline` package. Request, Cancel, Repair 025D, and canonical Task 025 remain unauthorized until separately approved.

## Execution policy

- One repair equals one complete Git-controlled `.cline` package.
- Each package includes writer, prompt, validation, review, handover, finalization, and state automation.
- DeepSeek executes the prepared writer only.
- Maven runs synchronously through `.cline/scripts/run-maven-evidence.ps1`.
- No loose follow-up scripts unless delivered through a versioned package-repair installer.
