# Phase 09: Build-Once Immutable Artifact Promotion

## Objective

Build the application image once and promote the same immutable digest through dev, SIT, UAT, production, and DR.

## Target flow

```text
Approved source commit
  -> CI validation
  -> one image build
  -> Harbor immutable tag and digest
  -> automatic dev deployment
  -> approved SIT promotion
  -> approved UAT promotion
  -> approved production promotion
  -> DR deployment of production-approved digest
```

## Required changes

- Separate build actions from deploy actions.
- Stop rebuilding for SIT, UAT, production, and DR.
- Store the image digest as the promotion authority.
- Keep readable tags for operators, but deploy by immutable digest or enforce immutable tags.
- Record source commit, Jenkins build, image digest, checks, target environment, approver, and deployment result.
- Use environment-specific Helm values and Secrets at runtime.
- Retain current environment branches during initial migration if needed, but make downstream branches select existing artifacts.
- Make DR use the approved production digest.

## Acceptance criteria

- One artifact is used in all environments.
- No downstream environment executes Docker build.
- Runtime configuration differs without changing the image.
- Promotion approvals are auditable.
- Rollback selects a previously approved digest.
