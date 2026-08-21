# Captcha Migration, Operations, and Traceability

## Frontend migration

- Treat repeated verify of a verified challenge as HTTP 422, not success.
- Use `err.captcha.verification.invalid` for standalone lifecycle-ineligible verification and target `captchaAnswer`.
- Do not branch on internal challenge state.
- Retain the verified challenge ID for the later protected request.
- Do not submit the answer to Registration or Username Recovery.
- Request a new challenge only according to the approved user journey.

## Backend consumer migration

- Consume through `RegistrationCaptchaGateService` or an approved successor application gate.
- Never call Redis adapters directly and never re-check an answer.
- Treat conflict and missing state as non-success.
- Perform no downstream side effect after rejection.
- Completed idempotency replay must not consume again.

## Operations and rollback

The approved implementation introduces no new Redis key format and no new TTL policy. Existing ephemeral records require no bulk data migration and may age out under their current TTL. Deployment must preserve compatibility across the lifecycle and public HTTP contract. A rollback to behavior that accepts repeated verification or performs non-atomic consumption is incompatible and must not be performed without a new architecture decision and evidence.

## Traceability

| Requirement | Implementation component | Focused test or guard | Approved evidence | Current documentation |
|---|---|---|---|---|
| `CREATED`-only verification | `VerifyCaptchaService` | `VerifyCaptchaUseCaseTest` | Phase 02 | Canonical lifecycle, Verification versus consumption |
| `VERIFIED` replay rejection | `VerifyCaptchaService` | `VerifyCaptchaUseCaseTest` | Phases 01 and 02 | Canonical lifecycle, Standalone API contract |
| Atomic verification | `RedisCaptchaChallengeAdapter` | `RedisCaptchaVerificationAtomicityTest` | Phase 03 | Atomic persistence contract |
| Bounded verification conflict | `VerifyCaptchaService` | `VerifyCaptchaConflictTerminationTest` | Phase 03 | Atomic persistence contract |
| Captcha-owned HTTP boundary | `CaptchaHttpResponses` | `AuthCaptchaControllerTest` | Phase 04 | Standalone API contract |
| HTTP 422 contract | `CaptchaHttpResponses` and `CaptchaWebMapper` | `AuthCaptchaControllerTest`, `CaptchaWebMapperTest` | Phase 04 | Standalone API contract |
| Public-error decoupling | `CaptchaWebMapper` | `CaptchaPublicErrorOwnershipGuardTest` | Phase 05 | Public error ownership matrix |
| Atomic one-time consumption | `RedisCaptchaChallengeAdapter`, `RegistrationCaptchaGateService` | `RedisCaptchaConsumptionAtomicityTest`, `CaptchaConsumptionAtomicityGuardTest` | Phase 06 atomic repair | Atomic persistence contract, Consumer responsibilities |
| Registration preservation | `StartRegistrationService` | `StartRegistrationCaptchaGateTest` | Phase 06 | Consumer responsibilities |
| Username Recovery preservation | `StartUsernameRecoveryService` | `StartUsernameRecoveryServiceTest` | Phase 06 | Consumer responsibilities |
| Answer isolation | consumer commands and guards | `CaptchaConsumerRegressionGuardTest` | Phase 06 | Frontend and backend migration |
| TTL preservation | Redis conditional replacement | verification and consumption atomicity tests | Phases 03 and 06 | Atomic persistence contract |
| State concealment | standalone mapper and errors | `CaptchaPublicErrorOwnershipGuardTest` | Phase 05 | Standalone API and error matrix |
