# Captcha Verification Refactor Handover

## Architecture decisions

- Verification and consumption are separate operations.
- Verification is permitted only from `CREATED` and transitions to `VERIFIED`.
- Consumption is performed later by a protected flow and transitions `VERIFIED` to `CONSUMED` atomically.
- Re-verifying `VERIFIED` must not compare the answer, mutate state, increment attempts, extend TTL, or consume.
- Approved public contract for re-verifying `VERIFIED`:
  - HTTP 422;
  - `VALIDATION_FAILED`;
  - `success=false`;
  - error `err.captcha.verification.invalid`;
  - target `captchaAnswer`;
  - next step `NONE`.
- The public response must not reveal the precise stored lifecycle state.
- `AuthCaptchaController` must be decoupled from `RegistrationHttpResponses` in a later production phase.

## Current stage

Phase 01 prompt has been prepared for the M365 Copilot implementation agent. Phase 01 freezes the contract in documentation, adds fail-first tests, captures the baseline and expected failures, and produces a self-contained bundle report. It explicitly prohibits production changes.

## Phase 01 prompt artifact

`captcha-refactor-phase-01-prompt-001.md`

## Required Phase 01 review input

The architect must receive and review:

`target/evidence/<explicit-timestamp>-captcha-refactor-phase-01/captcha-refactor-phase-01-bundle-report.md`

The bundle must include sequential evidence files, exact Maven exit codes, fail-first signatures, Git diff summaries, and final status.

## Artifact versioning

All prompts and scripts use sequential versioned filenames. Existing versions must not be overwritten.
