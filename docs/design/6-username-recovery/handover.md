# Username Recovery Handover
## Current stage

Canonical Task 025, **Integration and Stabilization**, is blocked pending the approved Model B session-architecture migration.

The read-only reconciliation audit completed with:

```text
MODEL_B_RECONCILIATION_PLAN_READY
```

Audit evidence:

```text
target/evidence/username-recovery/025-session-architecture-reconciliation-audit-20260811-105416/
```

The binding architecture is:

```text
Generic AuthFlowSession
= authoritative identity, lifecycle, CSRF, expiry, cancellation, deletion,
  generic revision, Redis envelope, and TTL

UsernameRecoveryWorkflowState sidecar
= language, verified destination, candidates, Scheme-Refs, registered usernames,
  delivery transitions, workflow projection, and workflow-state revision
```

`AuthFlowSessionId` is the sole canonical identity. `UsernameRecoverySessionId`, `UsernameRecoveryCsrfHash`, duplicate generic lifecycle fields, and the legacy Username Recovery session ownership model are transitional and must be removed through the separately reviewed migration sequence.

The approved migration sequence is:

```text
025A  Workflow-state domain and outbound-port contract
025B  Workflow-state Redis persistence keyed by AuthFlowSessionId
025C  Start, Request, and Cancel orchestration migration
025D  Cookie/composition alignment and legacy-stack removal
025   Regenerated integration tests and final Maven gates
```

Repair 025A is the next task. Canonical Task 025 remains blocked. The seven earlier Task 025 integration-test drafts remain quarantined under evidence and must not be restored wholesale.

Binding coordinator assumptions:

```text
legacy Username Recovery production records requiring migration: false
canonical Username Recovery TTL: 600 seconds
quarantined Task 025 drafts authorized for restore: false
```

Repair 025A may now be packaged and authorized. Repair 025B, 025C, 025D, and canonical 025 remain unauthorized.
