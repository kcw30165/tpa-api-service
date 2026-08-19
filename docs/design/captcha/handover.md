# Captcha Refactor Handover

## Approved architecture through Phase 06

- Only `CREATED` is verification-eligible.
- Correct verification performs atomic `CREATED -> VERIFIED` with TTL preservation.
- Re-verifying `VERIFIED` is rejected uniformly with HTTP 422, `VALIDATION_FAILED`, `err.captcha.verification.invalid`, target `captchaAnswer`, and `nextStep=NONE`.
- Verification never consumes and a rejected repeated verification leaves one legitimate consumption available.
- Protected consumption is a separate atomic `VERIFIED -> CONSUMED` capability with at most one winner.
- Registration and Username Recovery use `RegistrationCaptchaGateService`, submit no answer, and stop side effects after rejection.
- `AuthCaptchaController` is decoupled from `RegistrationHttpResponses`; standalone errors are captcha-owned or generic.

## Phase 07 documentation

- Canonical lifecycle and public contract: `captcha-verification-lifecycle-contract.md`.
- Migration, operations, and traceability: `captcha-migration-and-traceability.md`.
- Supporting Registration, Username Recovery, and security documentation delegates to the canonical contract.
- Historical plans, prompts, and evidence remain unchanged.

## Remaining Phase 08 scope

Phase 08 is limited to final cumulative validation and merge-request evidence. It must not reopen settled lifecycle, HTTP, error ownership, atomicity, or consumer-boundary design without new evidence and explicit architecture approval.

## Approved commit subjects

- `test(captcha): define fail-first verified replay contract`
- `fix(captcha): reject verification outside created state`
- `fix(captcha): enforce atomic verification transitions`
- `refactor(captcha): introduce captcha HTTP response mapping`
- `refactor(captcha): decouple public errors from registration`
- `fix(captcha): make challenge consumption atomic`
- `test(captcha): protect one-time consumer behavior`
- Phase 07 after approval: `docs(captcha): finalize lifecycle and migration guidance`

## Non-functional warnings and evidence limits

Git may report LF-to-CRLF conversion warnings on Windows checkouts. Those warnings are not validation failures when `git diff --check` passes. Historical evidence records the repository state at execution time and is not rewritten by documentation cleanup.
