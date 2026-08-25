# Phase 03: Worktree-Aware Copilot Script Workflow

## Objective

Remove manual script movement and prevent execution against the wrong worktree by introducing a controlled inbox, launcher, metadata contract, and archive.

## Design

The browser download directory remains a Windows-visible inbox. A WSL launcher imports a selected script and validates its declared target.

Each generated script must declare:

```text
Repository identifier
Task identifier
Expected worktree or branch
Expected starting commit
Stage type
Script sequence
Expected prior script or commit when applicable
```

## Launcher behavior

1. Copy the downloaded script into `~/copilot/inbox`.
2. Normalize line endings without modifying the original file.
3. Parse metadata.
4. Resolve the registered worktree.
5. Verify repository identity, branch, and expected commit.
6. Verify allowed dirty-state conditions.
7. Create the timestamped evidence directory before execution.
8. Copy the executed script into the evidence directory.
9. Execute with native WSL Bash.
10. Archive the script with result metadata.
11. Refuse duplicate or already-completed execution.

## Refusal conditions

- Unknown repository
- Unknown worktree
- Branch mismatch
- Starting commit mismatch
- Unexpected dirty files
- Missing prior confirmed change
- Missing required command
- Script already completed
- Attempt to read or archive protected secret files

## Evidence

```text
target/evidence/<timestamp>-<task>/
├── 001-script-metadata.txt
├── 002-pre-state.txt
├── 003-executed-script.sh
├── 004-stage-results.txt
├── 005-git-diff.txt
├── 006-exit-codes.txt
└── 007-final-status.txt
```

## Acceptance criteria

- One download location supports both active worktrees.
- No manual file movement is required.
- Wrong-worktree execution is blocked.
- Script, evidence, branch, and commit remain traceable.
- Completed scripts are archived automatically.
