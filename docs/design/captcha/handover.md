# Captcha Refactor Handover

## Phase 01 agreed context

- Approved lifecycle: `CREATED -> VERIFIED`, followed later by protected-flow `VERIFIED -> CONSUMED`.
- Re-verifying `VERIFIED` must return HTTP 422 with `VALIDATION_FAILED`, `success=false`, `err.captcha.verification.invalid`, target `captchaAnswer`, and `nextStep=NONE`.
- Re-verification must not compare the answer, mutate status or attempts, extend TTL, or consume the challenge.
- The response must not reveal the stored lifecycle state.
- `AuthCaptchaController` must be decoupled from `RegistrationHttpResponses` in a later production phase.
- Phase 01 changes documentation and fail-first tests only. No production implementation is authorized.
