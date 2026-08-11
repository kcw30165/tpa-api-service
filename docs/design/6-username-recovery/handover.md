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
## Repair 025A completion

Repair 025A was independently approved and committed as:

```text
21f5c414ea1295483de6380b091c6853fb8a1641
refactor(username-recovery): add workflow state sidecar contract
```

The protected `UsernameRecoveryWorkflowState` sidecar and revision are located in `application.dto.session.usernamerecovery`, use canonical `AuthFlowSessionId`, and pass focused 60/60 and cumulative 68/68 tests including all clean-architecture rules.

## Current bounded task

Repair 025B is split into small rounds. Only **025B-1**, Redis record, JSON mapper, and key factory, is authorized. The Redis adapter, CAS/TTL behavior, orchestration migration, legacy removal, and canonical Task 025 remain blocked.

Execution budget for 025B-1:

```text
10 minutes broad discovery
20 minutes to first compile
45 minutes active agent time excluding Maven
one corrective attempt per issue
```

