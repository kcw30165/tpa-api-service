# Phase 07: Copilot Evidence and Completion Gates

## Objective

Make Copilot A, Copilot B, and Nick use explicit and non-overlapping completion states.

## Gate model

### Gate A: Investigation ready

- Repository and code paths mapped.
- Dependencies and preservation constraints identified.
- Evidence is sufficient for deterministic implementation.

### Gate B: Implementation complete

- Expected pre-state matched.
- Source changes applied.
- Formatting passed.
- Production compile passed.
- Test compilation passed.
- Prescribed targeted tests passed when required.
- Diff and preservation audits passed.
- Commit created.
- `handover.md` updated.

### Gate C: Local technical validation

- Application or container starts.
- File log retained.
- Actuator health passes.

### Gate D: Local functional validation

- Curl assertions pass.
- Required integrations behave as expected.
- External physical effects are separately confirmed by Nick.

### Gate E: CI validation

- Jenkins independently reproduces required compile, tests, scans, and image build.

### Gate F: Tanzu dev acceptance

- Jenkins deploys the immutable artifact.
- Deployment health check passes.
- Nick confirms the expected dev business behavior.

## Rules

- Copilot B cannot declare Gate D complete from compile evidence.
- Copilot A approval must state which gates were reviewed.
- Evidence summaries must link to exact commit and evidence manifest.
- Full local evidence archives should not be attached to GitLab by default.
- A concise merge-request evidence summary may reference authoritative results.

## Acceptance criteria

- Every phase completion statement names its gate.
- No generic `PASS` is used without scope.
- `handover.md` records gate state and outstanding validation.
