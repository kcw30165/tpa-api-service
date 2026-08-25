# Phase 02: Proof Repository Migration

## Objective

Create one WSL-native proof clone of the BFF repository and prove that it can replace the active Windows checkout without deleting or modifying the Windows copy.

## Prerequisites

- Phase 01 passed.
- GitLab is reachable from WSL.
- Java 21 and Maven 3.9.15 are active.
- Docker works from WSL.

## Activities

1. Inspect `.gitattributes`, `.gitignore`, Git LFS requirements, submodules, and repository hooks in the existing repository.
2. Verify WSL Git identity and credential behavior.
3. Clone the BFF repository into:

```text
~/projects/orso/ngtpa/api-service
```

4. Verify branch and commit parity against the existing Windows checkout.
5. Create a WSL-native task worktree under:

```text
~/projects/orso/ngtpa/api-service-worktrees/<task-name>
```

6. Copy only required local, ignored development assets through a controlled script:
   - Redis CA certificate
   - Redis client certificate
   - Redis private key
   - Non-committed local configuration
7. Never copy `.git`, `target`, evidence archives, IDE caches, or populated secret files blindly.
8. Run Maven compile and test compilation.
9. Open the WSL repository through VS Code Remote WSL.
10. Reboot or run `wsl --shutdown`, then verify persistence.

## Evidence

```text
target/evidence/<timestamp>-phase02/
├── 001-source-repository-state.txt
├── 002-gitattributes-audit.txt
├── 003-clone-result.txt
├── 004-branch-and-commit-parity.txt
├── 005-worktree-list.txt
├── 006-local-asset-manifest.txt
├── 007-compile-command.txt
├── 008-compile-result.txt
├── 009-test-compile-command.txt
├── 010-test-compile-result.txt
├── 011-restart-persistence.txt
└── 012-final-status.txt
```

## Acceptance criteria

- WSL clone and worktree use expected branches and commits.
- Maven compile passes.
- Maven test compilation passes.
- VS Code opens the repository through WSL.
- Required ignored local assets exist with restrictive permissions.
- No secret or private key enters evidence.
- Existing Windows checkout remains usable as rollback.

## Rollback

Delete only the WSL proof clone and worktrees. Continue using the existing Windows checkout.
