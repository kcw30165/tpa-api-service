# Feature 0061 - Forgot Password Handover

## Current status

Gate 6 implementation is in progress.

Completed implementation subtasks:

- Subtask 001: Shared workflow and public-response boundary
- Subtask 002: Forgot Password workflow foundation
- Subtask 003: Username Start and TRPChkCustId integration
- Subtask 004: Protected Identity Verification
- Subtask 005A: Message-delivery unknown-outcome foundation
- Subtask 005: Details Verification and Immediate OTP Issuance
  - Round 005-001: Details validation and TRPSearchRegedMember APIM
  - Round 005-002: Initial OTP foundation and session binding
  - Round 005-003: OTP delivery commit state and replay protection
  - Round 005-004: Details and initial OTP orchestration and stabilization

Next authorized work:

- Subtask 006: OTP Resend and Verification

Subtask 007 has not started.

## Subtask 001

Commit message:

feat(auth): prepare workflow contracts for forgot password

Implemented:

- Added required workflow next-step values.
- Added PASSWORD_RESET_SUCCESS terminal behavior.
- Added shared public ApiStatus normalization.
- Feature 0061 must never expose DOWNSTREAM_REJECTED.
- Login and Registration contracts were preserved.
- Legacy YAML page metadata remains internal and is not returned in API responses.

## Subtask 002

Commit message:

feat(auth): add forgot password workflow foundation

Implemented:

- Added Forgot Password workflow configuration.
- Added encrypted Forgot Password protected context.
- Added protected-context loading and validation.
- Added reset-authorization and idempotency domain models.
- Added Forgot Password workflow-state foundations.
- Identity mappings are:
  - GPN -> GPN
  - GPFS -> GPFS
  - STAFF -> STAFF_NO
  - ID -> ID
- Removed unused speculative Forgot Password Spring wiring.
- ActorContext evolution remains deferred.

## Subtask 003

Commit message:

feat(forgot-password): add username start and customer check

Implemented:

- Added POST Forgot Password Start operation.
- Reused and extended TRPChkCustId.
- Preserved Registration interpretation of isExist.
- Forgot Password interprets isExist=true as username confirmed.
- Protected customer ID, TAS user ID, and mapped identity type.
- Created FORGOT_PASSWORD session with shared AuthFlowStatus.STARTED.
- Returned raw CSRF in the response body.
- Issued auth_flow_sid.
- Returned nextStep ENTER_ID.
- Added Forgot Password Start idempotency and safe replay.
- Added bounded SAFE_BUSINESS_CALL APIM retry handling.
- Sanitized SYSTEM_ERROR responses so raw downstream messages are not exposed.
- No pageId or pageRef is returned.
- No DOWNSTREAM_REJECTED is returned to FE.

Validation:

- Canonical Maven compile passed.
- Focused Subtask 003 Maven/Surefire tests passed:
  33 tests, 0 failures, 0 errors, 0 skipped.

## State compatibility decision

Active Forgot Password sessions continue to persist:

AuthFlowStatus.STARTED

Detailed Feature 0061 progression is stored inside encrypted protected
context.

Subtask 004 will introduce protected phases:

- USERNAME_VERIFIED
- IDENTITY_VERIFIED

Subtask 004 must not persist the shared AuthFlowStatus.IDENTITY_VERIFIED
enum value.

## Deferred work

- ActorContext future shape retains actorUserId and adds actorUserName,
  actorUserRole, and actorUserType.
- This change is not part of the current Feature 0061 implementation.
- TRPSearchRegedMember is not implemented yet.
- OTP issue/resend/verification is not implemented yet.
- TRPResetPassword is not implemented yet.
- Forgot Password page and final error YAML are not implemented yet.

## Current stop point

Subtask 003 is complete and committed.

Next authorized work:

Subtask 004: Protected Identity Verification

Do not implement Subtask 005 or later work.

## Subtask 004: Protected Identity Verification

Commit message: feat(forgot-password): add protected identity verification

Implemented:

- Added encrypted protected phases USERNAME_VERIFIED and IDENTITY_VERIFIED.
- Complete Subtask 003 contexts without phase default to USERNAME_VERIFIED; incomplete contexts fail closed and missing phase is never IDENTITY_VERIFIED.
- Added POST /api/v1/auth/forgot-password/identity-check with auth_flow_sid, X-XSRF-TOKEN, Origin/Referer, X-Request-Id, and Accept-Language transport handling.
- Added exact public identity mapping GPN, GPFS, STAFF_NO to STAFFNO, and ID, with trim-only identity-value comparison.
- Added a capability-neutral Redis auth-flow mutation lock with atomic set-if-absent lease acquisition, opaque ownership token, and ownership-safe compare-and-delete release.
- Identity Check acquires the lock before reload, fails closed without waiting, reloads session under lock, rejects repeated IDENTITY_VERIFIED transitions, persists protected IDENTITY_VERIFIED while shared status remains STARTED, refreshes expiry only after success, and releases in finally.
- Existing AuthFlowSessionCompletionLockPort and completion behavior were not modified.

Validation:

- Fail-first: mvn -Dtest=ForgotPasswordIdentitySubtask004FailFirstTest test, expected missing-capability failure verified.
- Focused: mvn -Dtest=ForgotPasswordIdentitySubtask004FailFirstTest,AesGcmForgotPasswordAuthFlowContextProtectorTest,StartForgotPasswordServiceTest,AuthForgotPasswordControllerTest,ForgotPasswordStartWebMapperTest,RedisAuthFlowSessionCompletionLockAdapterTest test, passed.
- Regression: mvn -Dtest=AuthForgotPasswordControllerTest,StartForgotPasswordServiceTest,AesGcmForgotPasswordAuthFlowContextProtectorTest,DefaultForgotPasswordProtectedAuthFlowContextLoaderTest,SlidingForgotPasswordProtectedAuthFlowContextLoaderTest,RedisAuthFlowSessionAdapterTest,RedisAuthFlowSessionCompletionLockAdapterTest,AuthFlowSessionCsrfServiceTest,OriginRefererValidatorTest,AuthLoginControllerTest,AuthRegistrationControllerWorkflowContractTest,AuthRegistrationOtpResendControllerTest,AuthRegistrationOtpVerifyControllerTest,AuthRegistrationUsernameControllerTest test, passed.
- Compile: mvn -DskipTests compile, passed.
- Evidence: target/0061-subtask-004-evidence-20260731-144513

Exclusions preserved: no TRPSearchRegedMember, DOB/contact/details verification, OTP work, reset authorization issuance, TRPResetPassword, password completion, Forgot Password page YAML, ActorContext change, Account-Ref requirement, pageId/pageRef, or Subtask 005 behavior.

Stop point: Subtask 004 complete. Do not begin Subtask 005.

## Subtask 005A completion: Message-delivery unknown-outcome foundation

Commit message: feat(auth): distinguish unknown message delivery outcomes

Implemented:

- Extended SenderStatus and MessageDeliveryStatus with UNKNOWN_OUTCOME while preserving ACCEPTED and FAILED.
- FAILED remains a confirmed not-accepted outcome for local pre-invocation validation/configuration failures and explicit provider rejection.
- Null, empty, unrecognized, timeout, connection-loss, and other post-invocation indeterminate outcomes map to UNKNOWN_OUTCOME.
- MessageDeliveryAdapter returns sanitized shared results and does not expose provider text.
- SMS and SMTP post-invocation errors map to UNKNOWN_OUTCOME; local configuration failures remain FAILED.
- Login initial-send and resend handle UNKNOWN_OUTCOME explicitly and do not bind a successful delivery state.
- Login idempotency records UNKNOWN_OUTCOME, preventing blind duplicate send.
- Existing successful Login and Registration response contracts remain unchanged.
- No automatic provider retry was added.

Validation:

- Original fail-first: target/0061-subtask-005a-evidence-20260731-162930/01-fail-first.log, expected missing UNKNOWN_OUTCOME compilation failure verified.
- Corrected focused suite: target/0061-subtask-005a-fix-evidence-20260731-164040/03-focused-passing.log, 59 tests passed.
- Security-focused command: mvn -Dtest=EmailDeliverySecurityGuardTest,SmsDeliverySecurityGuardTest,SmtpEmailSenderAdapterTest,SmsSenderAdapterTest,MessageDeliveryUnknownOutcomeSubtask005aFailFirstTest,MessageDeliveryServiceTest test, passed.
- Full 005A regression command: mvn -Dtest=AuthLoginControllerTest,LoginOtpSendWebMapperTest,LoginOtpSendServiceTest,LoginOtpResendServiceTest,AuthRegistrationControllerWorkflowContractTest,AuthRegistrationOtpResendControllerTest,AuthRegistrationOtpVerifyControllerTest,RegistrationOtpResendWebMapperTest,RegistrationOtpVerifyWebMapperTest,RegistrationStartWorkflowIntegrationTest,RegistrationOtpResendServiceIntegrationTest,MessageDeliveryServiceTest,MessageDeliveryFoundationIntegrationTest,ScenarioSpecificMessageDestinationOverrideTest,SmsDeliverySecurityGuardTest,EmailDeliverySecurityGuardTest,RedisLoginOtpSendIdempotencyAdapterTest,RedisRegistrationIdempotencyAdapterTest,AuthFlowSessionCsrfServiceTest,OriginRefererValidatorTest test, passed.
- Compile command: mvn -DskipTests compile, passed.
- Final evidence: target/0061-subtask-005a-security-guard-fix-evidence-20260731-164813

Exclusions preserved: no TRPSearchRegedMember, Forgot Password details verification, Forgot Password initial OTP orchestration, destination selection, separate Send OTP operation, resend, verification, reset authorization, TRPResetPassword, password completion, ActorContext change, Account-Ref requirement, Subtask 005, or Subtask 006 behavior.

Stop point: Subtask 005A complete. Do not begin Subtask 005 in this implementation round.

### Subtask 005 implementation round 001: Details validation and TRPSearchRegedMember APIM

Commit message: feat(forgot-password): verify member details through apim

Implemented:

- Added DOB validation using the existing dd/MM/yyyy project convention.
- Enforced exactly one of email or mobile with FE targets dateOfBirth, email, and mobile.
- Added application details-verification command/result/use-case foundation.
- Added POST /ws/NGTPA/v1/TRPSearchRegedMember request, response data, envelope, mapper, shared transport, and adapter.
- Request mapping uses protected identity type/value and customer ID from the encrypted Forgot Password context; FE does not retransmit identity fields.
- Reused the shared APIM facade, credential resolution, certificate, encryption/decryption, X-Request-Id metadata, and SAFE_BUSINESS_CALL retry classification.
- Recognized err.tpa.member.not.found as the business-rejection result; all other non-empty errors and malformed, empty, duplicate, or mismatched success data fail technically.
- Added mutation-lock acquisition, mandatory session reload under lock, FORGOT_PASSWORD/STARTED checks, CSRF and Origin/Referer validation, and required protected IDENTITY_VERIFIED phase.
- Added no controller response claiming VERIFY_OTP and no OTP generation, delivery, binding, OTP_SENT phase, or commit-state behavior.

Validation:

- Fail-first: mvn -Dtest=ForgotPasswordDetailsApimSubtask005001FailFirstTest test, expected missing-capability failure verified.
- Focused: mvn -Dtest=ForgotPasswordDetailsApimSubtask005001FailFirstTest,TrpSearchUnregMemberApimMapperTest,TrpSearchUnregMemberApimResultMapperTest,ForgotPasswordIdentitySubtask004FailFirstTest test, passed.
- Regression: mvn -Dtest=AuthForgotPasswordControllerTest,StartForgotPasswordServiceTest,AesGcmForgotPasswordAuthFlowContextProtectorTest,DefaultForgotPasswordProtectedAuthFlowContextLoaderTest,SlidingForgotPasswordProtectedAuthFlowContextLoaderTest,RedisAuthFlowSessionMutationLockAdapterTest,RedisAuthFlowSessionCompletionLockAdapterTest,AuthFlowSessionCsrfServiceTest,OriginRefererValidatorTest,AuthLoginControllerTest,AuthRegistrationControllerWorkflowContractTest,ApimWebClientFacadeRetryTest,MessageDeliveryUnknownOutcomeSubtask005aFailFirstTest test, passed.
- Compile: mvn -DskipTests compile, passed.
- Evidence: target/0061-subtask-005-001-evidence-20260731-194648

Stop point: Subtask 005 implementation round 001 is complete. Subtask 005 is not complete. Next cumulative round is 005-002 initial OTP binding. Subtask 006 has not started.

### Subtask 005 implementation round 002: Initial OTP foundation and session binding

Commit message: feat(forgot-password): add initial otp binding foundation

Implemented:

- Added FORGOT_PASSWORD to the existing OTP and message-delivery scenario vocabulary and FORGOT_PASSWORD_OTP to message type vocabulary.
- Added configured initial OTP policy foundation: six numeric digits, 300-second validity, 60-second resend cooldown, five verification attempts, five allowed resends, and previous-code invalidation policy.
- Added approved English and zh_HK SMS/email templates with English fallback and required placeholder validation.
- Added Forgot Password destination masking as a minimal adapter around the unchanged Login-compatible masker.
- Added ForgotPasswordOtpSessionBindingPort and ForgotPasswordOtpSessionBindingAdapter without changing LoginOtpSessionBindingPort.
- Added protected OTP_SENT and OTP_DELIVERY_UNKNOWN phases.
- Accepted binding requires evidence of a caller-held mutation lock, reloads the session, requires FORGOT_PASSWORD/STARTED/IDENTITY_VERIFIED, validates the persisted non-expired FORGOT_PASSWORD challenge, stores the active challenge, encrypted full destination, masked destination, expiry and resend-available timestamps, writes OTP_SENT, and preserves shared STARTED.
- FAILED performs no binding and retains IDENTITY_VERIFIED.
- UNKNOWN_OUTCOME binds no usable challenge, writes protected OTP_DELIVERY_UNKNOWN, and preserves shared STARTED.
- No commit-state record, completed replay reconstruction, endpoint orchestration, public VERIFY_OTP success response, resend, verification, or Subtask 006 behavior was added.

Validation:

- Fail-first: mvn -Dtest=ForgotPasswordInitialOtpBindingSubtask005002FailFirstTest test, expected missing-capability failure verified.
- Focused: mvn -Dtest=ForgotPasswordInitialOtpBindingSubtask005002FailFirstTest,ForgotPasswordWorkflowPropertiesValidationTest,DefaultMessageTemplateRendererTest,LoginOtpSessionBindingAdapterTest,OtpChallengeModelTest,RedisOtpChallengeJsonMapperTest test, passed.
- Regression: mvn -Dtest=ForgotPasswordDetailsApimSubtask005001FailFirstTest,StartForgotPasswordServiceTest,ForgotPasswordIdentitySubtask004FailFirstTest,AesGcmForgotPasswordAuthFlowContextProtectorTest,RedisAuthFlowSessionMutationLockAdapterTest,MessageDeliveryUnknownOutcomeSubtask005aFailFirstTest,MessageDeliveryServiceTest,LoginOtpSendServiceTest,LoginOtpResendServiceTest,LoginOtpSessionBindingAdapterTest,RegistrationOtpChallengeLifecycleIntegrationTest,RegistrationOtpResendServiceIntegrationTest,RedisOtpChallengeStateAdapterTest,AuthFlowSessionCsrfServiceTest,OriginRefererValidatorTest test, passed.
- Compile: mvn -DskipTests compile, passed.
- Evidence: target/0061-subtask-005-002-evidence-20260731-214255

Stop point: Subtask 005 implementation round 002 is complete. Subtask 005 remains incomplete. Next cumulative round is 005-003 delivery replay protection. Subtask 006 has not started.

### Subtask 005 implementation round 003: OTP delivery commit state and replay protection

Commit message: feat(forgot-password): add otp delivery replay protection

Implemented:

- Added a server-only pendingOpaqueCommitRef to PROCESSING DETAILS_AND_OTP idempotency records.
- Existing persisted idempotency records remain backward-compatible with a null pending reference.
- Added conditional Redis attachment of the pending reference before OTP delivery.
- Added cryptographically random opaque references with at least 128 bits of entropy.
- Added ForgotPasswordOtpCommitState containing only auth-flow session ID, OTP challenge ID, committed-at timestamp, and challenge-expires-at timestamp.
- Added capability-scoped Redis commit-state key, record, JSON mapper, adapter, and wiring.
- Redis keys contain only the configured prefix, fixed capability namespace, and opaque reference.
- Added bounded retention using the later of challenge expiry plus configured grace and DETAILS_AND_OTP idempotency expiry.
- Added recoverable ordering in which commit state must exist before idempotency becomes COMPLETED.
- Added active replay reconstruction using authoritative session and challenge state.
- Added expired replay classification for the approved RESEND_OTP recovery contract.
- Missing, malformed, or inconsistent commit/session/challenge state fails closed without APIM, OTP generation, sender invocation, or replacement challenge.
- Preserved FAILED and UNKNOWN_OUTCOME as non-COMPLETED outcomes without commit state.
- Preserved Login and Registration behavior.

Validation:

- Clean focused command passed 27 tests with zero failures and zero errors.
- Cumulative regression command passed.
- Full Maven test suite passed.
- Final compile passed.
- git diff --check passed.
- Evidence: target/0061-subtask-005-003-final-evidence-20260731-233212

Exclusions preserved:

- No final details endpoint orchestration or public initial-issuance success response.
- No resend implementation.
- No OTP verification.
- No reset authorization.
- No TRPResetPassword or password completion.
- No Account-Ref or ActorContext change.
- No public DOWNSTREAM_REJECTED, pageId, or pageRef.
- No round 005-004 or Subtask 006 behavior.

Stop point: Subtask 005 round 003 is complete. Subtask 005 remains incomplete. Next cumulative round is 005-004 orchestration and stabilization. Subtask 006 has not started.

### Subtask 005 implementation round 004: Details and initial OTP orchestration and stabilization

Commit message: feat(forgot-password): complete details and initial otp flow

Implemented:

- Added POST /api/v1/auth/forgot-password/details using auth_flow_sid, X-XSRF-TOKEN, Origin/Referer, X-Request-Id, Accept-Language, and Idempotency-Key. Account-Ref is not required.
- Added the public details request containing only dateOfBirth and exactly one of email or mobile.
- Composed the committed details-verification, OTP-binding, commit-state, and idempotency replay capabilities into the protected DETAILS_AND_OTP flow.
- Added a framework-free under-lock details verifier shared by the existing details-verification use case and the round 005-004 orchestrator.
- The orchestrator reserves DETAILS_AND_OTP idempotency and attaches or reuses pendingOpaqueCommitRef before irreversible delivery.
- The orchestrator acquires the generic auth-flow mutation lock exactly once, reloads the session under that lock, preserves shared STARTED, and requires protected IDENTITY_VERIFIED before details verification.
- No AuthFlowSessionCompletionLockPort or nested mutation-lock acquisition is used.
- Added a dedicated details-and-OTP mutation-lock lease with a default of 60 seconds.
- The minimum lease is calculated dynamically from APIM maximum attempts, APIM timeout, retry backoff intervals, the maximum configured sender timeout, Redis persistence allowance, and safety margin.
- Added startup validation for the approved lease, Redis allowance, safety-margin, and 120-second maximum bounds.
- Added application-owned MessageDeliveryTimeoutBound values published by the email and SMS adapters, avoiding provider configuration leakage across the adapter boundary.
- Added monotonic local lease-budget checks before protected state persistence.
- Insufficient lease budget fails closed without accepted binding, commit-state persistence, idempotency completion, provider retry, challenge replacement, or fresh issuance.
- The accepted path creates one FORGOT_PASSWORD challenge, invokes the sender once, binds OTP_SENT, persists commit state, and only then marks idempotency COMPLETED.
- Confirmed delivery failure returns a sanitized SYSTEM_ERROR, leaves the protected phase at IDENTITY_VERIFIED, creates no usable binding or commit state, and records FAILED.
- UNKNOWN_OUTCOME returns a sanitized SYSTEM_ERROR, blocks the session with OTP_DELIVERY_UNKNOWN when safely persistable, creates no usable challenge or completed commit state, and records UNKNOWN_OUTCOME.
- Active completed replay returns VERIFY_OTP with server-derived current expiry and cooldown and performs no APIM, OTP-generation, sender, binding, TTL reset, counter reset, or challenge replacement.
- Expired completed replay returns BUSINESS_REJECTED with RESEND_OTP and err.forgot-password.otp.expired while preserving STARTED, OTP_SENT, the expired challenge, completed idempotency, and the original opaque reference.
- Public responses expose no raw DOB, contact, OTP, template, TAS/APIM/provider data, session ID, challenge ID, opaque reference, cookie, CSRF token, pageId, pageRef, or DOWNSTREAM_REJECTED.

Validation:

- Fail-first: missing 005-004 lease and monotonic-budget capabilities were verified.
- Focused round 005-001 through 005-004 tests passed.
- Cumulative Forgot Password, 005A, Login, Registration, APIM, OTP, message-delivery, Redis, CSRF, mutation-lock, and completion-lock regressions passed.
- Email and SMS adapter architecture guards passed after introducing the application-owned delivery-timeout boundary.
- Full Maven suite passed.
- Compile: mvn -DskipTests compile, passed.
- Git diff check passed.
- Evidence: target/0061-subtask-005-004-timeout-boundary-resume-evidence-20260801-072821

Exclusions preserved:

- No destination-selection operation.
- No separate Send OTP endpoint.
- No OTP resend implementation.
- No OTP verification.
- No fifth-attempt or sixth-resend terminal behavior.
- No reset authorization.
- No TRPResetPassword.
- No password completion.
- No final Forgot Password page or error YAML.
- No ActorContext change.
- No Account-Ref requirement.
- No public DOWNSTREAM_REJECTED, pageId, or pageRef.
- No Subtask 006 or later behavior.

Stop point: Subtask 005 complete. Subtask 006 has not started.
