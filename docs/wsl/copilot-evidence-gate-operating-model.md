# Copilot Evidence Gate Operating Model

## Purpose

This document defines explicit, non-overlapping completion states for Copilot A, Copilot B, and Nick. A phase completion statement must name a WSL gate and must not use an unscoped `PASS`.

## Gate ownership and evidence

### Gate A: Investigation ready

Copilot B may report Gate A only after repository paths, dependencies, constraints, and deterministic implementation evidence are mapped. Gate A does not imply implementation, compilation, runtime startup, functional behavior, CI validation, or deployment acceptance.

### Gate B: Implementation complete

Copilot B may report Gate B only after the expected pre-state matched, changes were applied, formatting passed, production compile passed, test compilation passed, prescribed targeted tests passed when required, preservation and diff audits passed, a commit was created, and `handover.md` was updated.

### Gate C: Local technical validation

Gate C requires a direct application or local-container start, retained file log, and Actuator health success. Gate C is separate from Gate B.

### Gate D: Local functional validation

Gate D requires scripted curl assertions and successful required integrations. External physical effects, including SMS delivery, remain a separate Nick confirmation and cannot be inferred from HTTP or compile evidence.

### Gate E: CI validation

Gate E is owned by Jenkins. Jenkins must independently reproduce the required compilation, tests, scans, and image build. Local evidence cannot declare Gate E complete.

### Gate F: Tanzu dev acceptance

Gate F requires Jenkins deployment of the immutable artifact, deployment health verification, and Nick's confirmation of expected dev business behavior.

## Required completion statement

Every completion statement must include:

- exact gate name;
- exact Git commit;
- authoritative evidence manifest path;
- reviewed scope;
- outstanding gates and deferred validation;
- reviewer or owner when human confirmation is required.

Example:

```text
Gate B: IMPLEMENTATION_COMPLETE
Commit: <full commit hash>
Evidence manifest: target/evidence/<timestamp>-<task>/manifest.txt
Reviewed: source change, compile, test-compile, targeted tests, preservation, diff
Outstanding: Gate C, Gate D, Gate E, Gate F
```

## Merge-request evidence rule

Use a concise evidence summary in GitLab. Do not attach full local evidence archives by default. The summary may reference authoritative local paths and Jenkins results, but must not include credentials, session data, tokens, OTPs, account references, identity values, TLS private keys, or populated environment files.

## Phase 08 boundary

WSL Phase 07 defines gate language and local evidence discipline. WSL Phase 08 makes Jenkins an independent Gate E authority. Phase 07 does not claim Jenkins reproduction, protected-merge enforcement, Sonar stability, GitLab status reporting, or Jenkins image-build authority.
