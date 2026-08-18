# Captcha Refactor Handover

## Phase 01 agreed context

- Approved lifecycle: `CREATED -> VERIFIED`, followed later by protected-flow `VERIFIED -> CONSUMED`.
- Re-verifying `VERIFIED` must return HTTP 422 with `VALIDATION_FAILED`, `success=false`, `err.captcha.verification.invalid`, target `captchaAnswer`, and `nextStep=NONE`.
- Re-verification must not compare the answer, mutate status or attempts, extend TTL, or consume the challenge.
- The response must not reveal the stored lifecycle state.
- `AuthCaptchaController` must be decoupled from `RegistrationHttpResponses` in a later production phase.
- Phase 01 changes documentation and fail-first tests only. No production implementation is authorized.

## Phase 03 implementation

- Redis verification uses an exact-value conditional Lua replacement with `KEEPTTL`.
- Correct verification and wrong-answer increment/lock are one atomic write each.
- Conflicts reload current state; a correct-answer loser cannot report fresh success, and wrong-answer conflicts retry against current CREATED state.
- Consumption remains a separate operation and public HTTP behavior remains unchanged.
