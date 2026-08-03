# Feature 0061 - Forgot Password Handover

## Current status

Gate 6 implementation and closeout are complete through Subtask 010, including Subtask 007 password completion/finalization, Subtask 008 FE-owned presentation-boundary closeout, Subtask 009 observability/security/contract coverage, and Subtask 010 validation-first final closeout.

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
- Subtask 006: Forgot Password OTP resend and verification
  - Round 006-001: OTP resend foundation
  - Round 006-002: OTP resend orchestration and endpoint
  - Round 006-003: OTP verification and reset-authorization orchestration
  - Round 006-004: stabilization, traceability, regression, security, and evidence closeout
- Subtask 007: Password completion and finalization
- Subtask 008: FE-owned presentation-boundary closeout
- Subtask 009: Observability, Security, and Contract Tests
- Subtask 010: Validation-first final Gate 6 closeout

Next authorized work:

- No further Gate 6 work is authorized in this handover. Any additional Feature 0061 changes require a new explicit scope.

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

## Subtask 005: details and immediate otp

### Subtask 005A completion: Message-delivery unknown-outcome foundation

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

## Subtask 006: OTP resend and verification

### Subtask 006 implementation round 001: OTP Resend Foundation

Commit message: feat(forgot-password): add otp resend foundation

Added the non-web Forgot Password OTP resend foundation: configuration-driven 60-second cooldown and five-successful-resend limit; typed eligibility outcomes; caller-held-lock and lock-reloaded-session boundary; authoritative challenge/session/scenario/phase validation; replacement transition vocabulary for ACCEPTED, FAILED, and UNKNOWN_OUTCOME; safe request hashing; and classification of existing OTP_RESEND idempotency outcomes. Existing OTP status vocabulary is preserved; CREATED is the pre-verification challenge state and no ACTIVE status was introduced.

Initial accepted issuance corresponds to successfulResendCount=0. A request made after five successful resends is rejected. Cooldown is measured from the latest explicitly accepted issuance. Expired challenges may remain resend-eligible when session, protected phase, challenge scenario, and session binding are valid.

ACCEPTED replacement state makes the candidate authoritative and increments once; FAILED preserves the current authoritative challenge and count; UNKNOWN_OUTCOME blocks safely without increment. The foundation acquires no lock itself, uses no completion lock, generates no OTP, calls no sender, activates no live replacement, and exposes no public response.

Validation: focused (ForgotPasswordOtpResendFoundationSubtask006001FailFirstTest,ForgotPasswordDetailsOtpOrchestrationSubtask005004FailFirstTest,ForgotPasswordDeliveryReplayProtectionSubtask005003FailFirstTest,ForgotPasswordInitialOtpBindingSubtask005002FailFirstTest,ForgotPasswordDetailsApimSubtask005001FailFirstTest), cumulative resend/challenge/session/idempotency/lock/CSRF regressions (MessageDeliveryUnknownOutcomeSubtask005aFailFirstTest,LoginOtpResendServiceTest,IdempotentLoginOtpResendServiceTest,RegistrationOtpResendServiceIntegrationTest,RedisOtpChallengeStateAdapterTest,RedisForgotPasswordIdempotencyAdapterTest,RedisAuthFlowSessionMutationLockAdapterTest,AuthFlowSessionCsrfServiceTest,AesGcmForgotPasswordAuthFlowContextProtectorTest), and canonical compile passed. Evidence: target/0061-subtask-006-001-otp-status-fix-evidence-20260801-080302.

Exclusions preserved: no public Forgot Password resend endpoint, replacement generation/delivery, public mapping, OTP verification/consumption, reset authorization, TRPResetPassword, password completion, Subtask 007, or later behavior.

Stop point: Subtask 006 is in progress. Next round: 006-002 OTP Resend Orchestration and Endpoint. Subtask 007 has not started.

#### Subtask 006 prerequisite round 001A-1: Session revision schema foundation

Commit message: feat(auth): add auth-flow session revision schema foundation

Implemented:

- Added framework-free application-owned SessionRevision, SessionRevisionGenerator, SecureSessionRevisionGenerator, and AuthFlowSessionSnapshot vocabulary.
- SessionRevision uses 128 bits of SecureRandom entropy, remains opaque, and is redacted from toString output.
- Extended RedisAuthFlowSessionRecord additively with optional sessionRevision while preserving deserialization of legacy records without the field.
- Extended RedisAuthFlowSessionJsonMapper with revised-record serialization and snapshot loading; legacy domain loading remains unchanged and read-only loading does not generate a revision.
- Added persistence schema version 2 configuration and a fixed capability-scoped Redis marker under the configured Redis prefix.
- Added atomic absent-marker initialization through Redis set-if-absent, exact existing-version validation, immutable process compatibility state, and a fail-closed startup gate using the existing Sentinel-backed connection.
- Marker version 2 does not itself migrate a legacy session or authorize a revision-aware mutation. Those operations remain for split round 001A-2 and later rounds.
- Operational invariant: all revision-unaware writer pods must be drained before initializing schema version 2. After initialization, rollback to a revision-unaware writer is prohibited. The marker cannot stop an already-running old binary by itself.

Validation:

- Fail-first: missing revision and compatibility vocabulary confirmed.
- Focused: SessionRevisionSchemaFoundationSubtask006001a1FailFirstTest,AuthFlowSessionPersistenceCompatibilityVocabularyTest,AuthFlowSessionPersistenceSchemaPropertiesTest,RedisAuthFlowSessionJsonMapperTest,RedisAuthFlowSessionKeyFactoryTest,RedisAdapterConfigConditionalTest passed.
- Regression: RedisAuthFlowSessionAdapterTest,P4BAuthFlowSessionRedisSecurityGuardTest,RedisAuthFlowSessionArchitectureGuardTest,AuthFlowSessionPortContractShapeTest,AuthForgotPasswordControllerTest,AuthLoginControllerTest,AuthRegistrationControllerWorkflowContractTest,ForgotPasswordOtpResendFoundationSubtask006001FailFirstTest passed.
- Full Maven suite, canonical compile, and git diff check passed.
- Evidence: target/0061-subtask-006-001a-1-evidence-20260801-170629

Exclusions preserved:

- No revision-aware create/update/touch/complete/cancel/delete operation.
- No legacy session revision initialization.
- No production session-writer migration.
- No Login OTP_VERIFY idempotency, consumption proof, or authenticated-login handoff.
- No public Forgot Password resend endpoint, candidate staging, replacement OTP generation/delivery, OTP verification, reset authorization, password completion, or Subtask 007 behavior.

Stop point: split prerequisite round 001A-1 complete. Subtask 006 remains incomplete. Next prerequisite round: 001A-2 revision-aware create/load and legacy initialization. Round 006-002 remains blocked. Subtask 007 has not started.

#### Subtask 006 prerequisite round 001A-2: Revision-aware create, snapshot load, and legacy initialization

Commit message: `feat(auth): add revision-aware session creation and legacy initialization`

Implemented:

- Added source-compatible `createRevisioned`, `loadSnapshot`, and `initializeLegacyRevision` application boundaries.
- Production session creation generates one cryptographically opaque `SessionRevision` and persists the complete auth-flow session plus its initial revision in the first single-record Redis write.
- Existing `create` callers remain source-compatible and production creation delegates to the revision-aware path.
- Read-only `load` remains backward-compatible for existing callers.
- `loadSnapshot` returns only a genuinely persisted revision. It does not reconstruct, synthesize, or advance a revision.
- Added caller-held mutation-lock proof to the legacy revision-initialization command.
- Added narrow adapter-owned single-record Redis operations for revision-aware creation and legacy revision initialization through the existing Sentinel-backed reactive Redis connection.
- Legacy initialization validates mutation-lock ownership, exact unchanged whole-record serialization, record presence, and a positive existing TTL before replacing the legacy record.
- Legacy initialization changes no business session state and preserves the existing Redis TTL by reading `PTTL` and applying the replacement with `PSETEX` in the same Redis script.
- Missing session, existing revision, changed legacy record, lost lock ownership, invalid TTL/corrupt state, storage ambiguity, and system failure fail closed through typed internal outcomes.
- The implementation does not claim cross-record transactionality. The scripts introduced in this round are limited to one auth-flow session record, with the existing mutation-lock key checked only as ownership proof during legacy initialization.
- Persistence schema version 2 compatibility must be established before production revision-aware creation, snapshot loading, or legacy initialization.
- The deterministic test fixture uses an injected fixed `Clock`; production wiring continues to use `Clock.systemUTC()`.

Validation:

- Fail-first test confirmed the absence of the revision-aware create, snapshot-load, and legacy-initialization contracts before production changes.
- Focused tests passed:
  - `AuthFlowSessionRevisionCreateLoadLegacyInitSubtask006001a2FailFirstTest`
  - `RedisAuthFlowSessionRevisionFoundationAdapterTest`
  - `SessionRevisionSchemaFoundationSubtask006001a1FailFirstTest`
  - `RedisAuthFlowSessionJsonMapperTest`
  - `AuthFlowSessionPersistenceSchemaPropertiesTest`
- Redis session, mutation-lock, completion-lock, schema/configuration, public-contract, and round 006-001 regressions passed.
- Full `mvn test` passed.
- Canonical `mvn -DskipTests compile` passed.
- `git diff --check` passed.
- The deterministic-clock repair evidence is retained under the generated `target/0061-subtask-006-001a-2-clock-fix-evidence-*` directory.
- Final regression and full-suite results were confirmed before this handover finalization.

Security and compatibility:

- `SessionRevision`, mutation-lock ownership tokens, Redis keys, serialized session state, protected context, challenge identifiers, OTP data, account data, and PII are not exposed through public contracts or diagnostic `toString` output.
- Revision values are not included in Redis keys.
- Legacy records remain readable through the existing domain load path.
- A revisionless legacy record is not treated as a wildcard revision and cannot be initialized without exact-state and mutation-lock validation.
- All revision-unaware writer pods must remain drained after schema version 2 initialization. Rollback to a revision-unaware writer remains prohibited.

Exclusions preserved:

- No revision-aware generic update, touch, complete, cancel, or delete operation.
- No migration of production auth-flow session mutation callers.
- No Login `OTP_VERIFY` idempotency, OTP consumption proof, account assignment persistence, authenticated-login handoff, or recoverable auth-flow cleanup.
- No public Forgot Password Resend endpoint.
- No resend candidate staging, replacement OTP generation or delivery, Forgot Password OTP verification, reset authorization, password submission, `TRPResetPassword`, or password completion.
- No Subtask 007 or later behavior.

Stop point:

- Split prerequisite round 001A-2 is complete.
- Subtask 006 remains incomplete.
- Next prerequisite round: 001A-3 revision-aware update, touch, complete, cancel, and delete.
- Round 006-002 remains blocked until all prerequisite 001A rounds are complete.
- Subtask 007 has not started.

#### Subtask 006 prerequisite round 001A-3: Revision-aware auth-flow session mutations

Commit message:

Implemented:

- Added application-owned revision-aware update and delete commands plus safe typed mutation outcomes.
- Added central revision-aware update, touch, complete, cancel, and delete boundaries to AuthFlowSessionPort and RedisAuthFlowSessionAdapter.
- Successful replace operations compare the exact expected serialized session, including the expected persisted SessionRevision, before applying one complete revised record with a fresh opaque revision.
- Successful touch, complete, and cancel preserve all unrelated session fields and the existing expiry; the Redis operation preserves the current positive TTL atomically with replacement.
- Successful deletion requires the exact expected revised record. Missing or stale state performs no deletion.
- Stale revision, changed expected state, missing session, corrupt TTL, invalid terminal transition, schema incompatibility, and storage failure fail closed through bounded typed outcomes.
- Added narrow adapter-owned single-record replace and delete Redis scripts. No cross-record transactionality is claimed.
- Existing unversioned methods remain temporarily source-compatible for production caller migration in 001A-4. They are not approved for new code and are not used by the new revision-aware boundaries.

Validation:

- Fail-first mutation-contract test confirmed missing 001A-3 capability.
- Focused: ForgotPasswordDeliveryReplayProtectionSubtask005003FailFirstTest,ForgotPasswordInitialOtpBindingSubtask005002FailFirstTest,StartForgotPasswordServiceTest,RedisForgotPasswordIdempotencyAdapterTest,ForgotPasswordIdempotencyContractTest,RedisOtpChallengeStateAdapterTest passed.
- Regression: RedisAuthFlowSessionAdapterTest,RedisAuthFlowSessionMutationLockAdapterTest,RedisAuthFlowSessionCompletionLockAdapterTest,AuthFlowSessionLifecycleServiceTest,AuthFlowSessionCsrfServiceTest,AuthFlowSessionExpiryRefreshPersistenceTest,AuthFlowSessionCancellationAdapterTest,AuthFlowSessionPortContractShapeTest,P4BAuthFlowSessionRedisSecurityGuardTest,AuthForgotPasswordControllerTest,AuthLoginControllerTest,AuthRegistrationControllerWorkflowContractTest,ForgotPasswordOtpResendFoundationSubtask006001FailFirstTest passed.
- Full Maven suite, canonical compile, and git diff check passed.
- Evidence: target/0061-subtask-005-004-timeout-boundary-resume-evidence-20260801-072821

Exclusions preserved:

- No production session-writer migration or removal of legacy method usage. That is 001A-4.
- No Login OTP_VERIFY idempotency, OTP consumption proof, authenticated-session handoff, or auth-flow cleanup orchestration.
- No Forgot Password resend endpoint, candidate staging, replacement generation/delivery, OTP verification, reset authorization, password completion, or Subtask 007 behavior.

Stop point: split prerequisite round 001A-3 complete. Subtask 006 remains incomplete. Next prerequisite round: 001A-4 production writer migration. Round 006-002 remains blocked. Subtask 007 has not started.

##### Subtask 006 prerequisite round 001A-4: Production auth-flow session writer migration

Commit message:

Implemented:

- Migrated all audited production AuthFlowSessionPort mutation callers to revision-aware snapshot and conditional mutation boundaries.
- Added an application-owned RevisionAwareAuthFlowSessionWriter with no ordinary set, evict, or unversioned fallback.
- Lifecycle update, complete, cancel, and delete; CSRF rotation; registration username and completion state; expiry refresh; cancellation; Forgot Password identity transition; and Forgot Password OTP binding now reject stale snapshots instead of overwriting newer state.
- Existing unversioned update, touch, complete, cancel, and delete entry points now fail closed and perform no Redis operation.
- Read-only legacy load remains available. Revision-aware create, snapshot load, legacy initialization, replacement, and deletion remain the authoritative production persistence boundaries.
- Concurrent revision conflicts return sanitized internal failures through existing workflow handling and do not retry irreversible workflow stages.

Public API status hardening:

- ApiStatus.DOWNSTREAM_ERROR is not exposed to FE and maps to SYSTEM_ERROR.
- ApiStatus.DOWNSTREAM_REJECTED is not exposed to FE and maps to BUSINESS_REJECTED.
- A permanent architecture guard verifies that public web production code does not emit either downstream status.

Validation:

- Fail-first production-writer guard detected legacy mutation callers before migration.
- Focused:  passed.
- Regression:  passed.
- Full Maven suite, canonical compile, and git diff check passed.
- Evidence:

Exclusions preserved:

- No Login OTP_VERIFY persistence/idempotency/consumption-proof implementation.
- No authenticated-login handoff or recoverable authenticated-session cleanup.
- No public Forgot Password resend endpoint, OTP replacement delivery, OTP verification, reset authorization, password completion, or Subtask 007 behavior.

Stop point: split prerequisite round 001A-4 complete. Subtask 006 remains incomplete. Next prerequisite round: 001A-5 Login OTP_VERIFY idempotency and consumption proof. Round 006-002 remains blocked. Subtask 007 has not started.

###### Subtask 006 prerequisite round 001A-5: Login OTP_VERIFY Idempotency and Consumption Proof

Commit message: feat(login): add idempotent revision-aware otp verification

Implemented the OTP_VERIFY canonical operation hash, server-generated handoff and verification-operation identities, caller-held mutation-lock and snapshot boundary, revision-aware Login-specific verification command/result vocabulary, at-most-once challenge consumption/attempt behavior, and consumption-proof contract. A first successful request retains the existing account lookup, Account-Ref assignment, authenticated-session creation, cookie timing, and account-selection result. A committed or ambiguous replay fails closed with sanitized SYSTEM_ERROR and does not repeat OTP verification or begin downstream Login continuation. No raw public response snapshot is persisted.

Retention defaults are 600 seconds for OTP_VERIFY idempotency and 900 seconds for handoff, consumption proof, and reserved deletion proof, bounded to 300..1800 seconds with proof/handoff retention not shorter than idempotency. Login OTP verification no longer calls the shared unversioned verifyChallenge path.

001A-3 commit: feat(auth): add revision-aware auth-flow session mutations
001A-3 evidence: target/0061-subtask-005-004-timeout-boundary-resume-evidence-20260801-072821
001A-4 commit: refactor(auth): migrate auth-flow session writers to revision-aware persistence
001A-4 evidence: target/0061-subtask-006-001a-4-api-exception-status-fix-evidence-20260801-190931

Exclusions preserved: no durable authenticated-login handoff state, idempotent authenticated-session creation, account recovery, cookie/result reconstruction, auth-flow cleanup/tombstone, public Forgot Password Resend endpoint, Forgot Password OTP verification, reset authorization, password submission, TRPResetPassword, Subtask 007, or 001A-6 behavior.

Next round: 001A-6 authenticated-login handoff and recoverable cleanup. Round 006-002 remains blocked. Subtask 007 has not started.

###### Subtask 006 prerequisite round 001A-6: Authenticated-login handoff and recoverable cleanup

Historical status at round opening: fail-first contract established; production implementation was not started at that point. Cumulative status is superseded by the completed 001A-6 implementation recorded below.

Baseline: `fa95e751aa0d951ddbbac582965ea57e80fb032c` (`feat(login): add idempotent revision-aware otp verification`).

Required boundary: persist the authenticated-login handoff durably after OTP consumption, recover account/session continuation without re-verifying or re-consuming OTP, establish at most one authenticated session, retain a cleanup/deletion proof, reconstruct the approved account-selection result and cookie timing, and fail closed on ambiguous or conflicting state. Read-only recovery must not extend handoff or proof retention.

Exclusions preserved: no Forgot Password OTP verification, reset authorization, password completion, TRPResetPassword, Subtask 007, or round 006-002 behavior.

001A-6 contract correction: the initial filename-driven handoff contract is superseded. No production class name, package, Redis adapter, or lifecycle enum is approved merely by the first fail-first test. The active fail-first boundary is repository-grounded data flow: reuse `LoginOtpConsumptionProof`, `AuthenticatedLoginHandoffReference`, and `LoginOtpVerificationOperationReference`; forward the committed proof on first success; recover `ALREADY_COMMITTED` without another OTP verification; establish at most one authenticated session using the recovery identity; retain cleanup proof; and fail closed on conflicts. Historical note: production implementation was unstarted at this earlier checkpoint; this is superseded by the completed cumulative 001A-6 implementation below.

###### Subtask 006 prerequisite repair round 001A-5R: Durable OTP_VERIFY Reservation and Proof Recovery

Commit message: `fix(login): persist otp verification recovery state`

Baseline: `fb05df73d8c413c000b5be63a17c0ad9336e8f5b`, with completed production baseline `fa95e751aa0d951ddbbac582965ea57e80fb032c`. The later baseline commit contains only the corrected 001A-6 fail-first contract.

Repaired the incomplete Login OTP_VERIFY persistence boundary by adding a durable reservation before verification, stable server-generated `AuthenticatedLoginHandoffReference` and `LoginOtpVerificationOperationReference`, durable non-null `LoginOtpConsumptionProof` recovery, operation-bound conclusive attempt recording, unknown-outcome and future completion boundaries, and fail-closed same-key replay pending 001A-6.

Hardened every shared Login idempotency action to use an action-scoped HMAC-SHA-256 `LoginIdempotencyPersistenceId`. Raw public Login Idempotency-Key values are no longer Redis key components or persisted Login idempotency record fields. No legacy raw-key fallback, dual lookup, or request-time migration is provided. Existing development records are disposable and may expire naturally or be removed through a capability-scoped development procedure. The public `Idempotency-Key` header contract is unchanged.

Ordered persistence is reservation, reference attachment, challenge mutation, durable attempt/consumption proof, and reservation stage update. No cross-record atomicity is claimed. OTP success does not complete the authenticated-login operation. 001A-6 remains production-incomplete and resumes after this repair. Round 006-002 remains blocked. Subtask 007 has not started.

####### Subtask 006 prerequisite round 001A-6 implementation 002: durable handoff persistence foundation

Commit message: `feat(login): add durable authenticated-session handoff`

Added the application-owned authenticated Login handoff lifecycle, typed create outcomes, recovery lookup, authenticated-session commit recording, and auth-flow cleanup-proof recording. Added a reactive Redis adapter using opaque HMAC-derived persistence identity, bounded existing TTL preservation, conflict checks for reservation, operation, session, and cleanup identities, and no read-time TTL extension.

This round establishes persistence only. Login continuation, at-most-once authenticated-session creation, replay reconstruction, conditional authenticated CSRF rotation, and revision-aware auth-flow deletion orchestration remain production-incomplete for the next cumulative 001A-6 round. No Forgot Password OTP verification, reset authorization, password completion, TRPResetPassword, round 006-002, or Subtask 007 behavior is implemented.

####### Subtask 006 prerequisite round 001A-6 implementation 003: first-success proof forwarding

Forwarded the durable `LoginOtpConsumptionProof` from acknowledged OTP consumption into account continuation and authenticated-session establishment. The establishment command now carries the existing `AuthenticatedLoginHandoffReference`, `LoginOtpVerificationOperationReference`, and reservation hash. The production Login path supplies the durable identities; a compatibility constructor preserves existing source callers during this cumulative round.

Committed replay recovery, at-most-once authenticated-session creation, authoritative replay reconstruction, conditional authenticated CSRF rotation, and recoverable auth-flow cleanup remain production-incomplete.

####### Subtask 006 prerequisite round 001A-6 implementation 004: authenticated-session identity reservation

The durable handoff now reserves one opaque authenticated-session ID before authenticated-session creation. `CreateAuthenticatedSessionCommand` accepts that reserved ID, and the lifecycle service uses it instead of generating a second identity. Existing non-handoff callers retain generated-ID behavior. This closes the identity-reservation prerequisite for at-most-once establishment but does not yet treat an existing committed session as replay success or persist cleanup proof.

####### Subtask 006 prerequisite round 001A-6 implementation 004R: runtime wiring and proof integrity

Replaced the synthesized handoff proof with the original committed `LoginOtpConsumptionProof` forwarded intact from OTP verification. The establishment command validates that its handoff, operation, and reservation identities exactly match the proof. Added the real reactive Redis `AuthenticatedLoginHandoffPort` bean and production Spring wiring with configured handoff retention. Production no longer reaches the compatibility handoff implementation.

Committed replay recovery and cleanup-proof completion remain production-incomplete and continue as the two intentional 001A-6 fail-first assertions.

001A-6 004R compatibility repair: retained the existing four-argument configuration factory solely for direct unit-test construction. The Spring `@Bean` overload remains the seven-argument production boundary and injects the real durable handoff port, authenticated-session ID generator, and configured retention.

####### Subtask 006 prerequisite round 001A-6 implementation 005-001: committed replay fail-first

Added an opt-in executable fail-first contract for committed Login replay. The contract requires recovery from the durable OTP proof before pre-auth context loading, no second OTP verification or account/APIM work, recovery only from an `AUTH_FLOW_DELETED` handoff, authoritative authenticated-session loading, conditional CSRF-hash rotation, and raw-token release only after acknowledged persistence. It also requires first-success establishment to record the authenticated-session commit and cleanup proof before reporting success.

No production Replay implementation is included in this round. The fail-first test is gated by `run.001a6.replay.fail-first=true` and remains excluded from the ordinary suite until the next cumulative implementation round.

####### Subtask 006 prerequisite round 001A-6 implementation 005-003: replay session persistence

Added a non-sliding authenticated-session replay load and a conditional CSRF-hash rotation boundary. The Redis adapter compares the complete expected protected envelope and atomically replaces it with the re-protected envelope under the caller-owned remaining TTL. Conflict and infrastructure outcomes fail closed. Only protected session envelopes and CSRF hashes cross the persistence boundary; raw CSRF tokens are never persisted.

Committed replay orchestration and first-success handoff completion remain for the next cumulative round.

001A-6 005-003 compatibility repair: made the two new authenticated-session replay capabilities default port methods. Existing test doubles and non-Redis adapters therefore remain source compatible. The defaults fail closed for CSRF rotation, while the production Redis adapter overrides both replay operations with the durable implementation.

001A-6 005-003 compatibility repair fix 002: made the two new authenticated-session replay capabilities default port methods. Existing test doubles and non-Redis adapters therefore remain source compatible. The defaults fail closed for CSRF rotation, while the production Redis adapter overrides both replay operations with the durable implementation.

####### Subtask 006 prerequisite round 001A-6 implementation 005-005: first-success handoff completion

Completed the durable first-success transition order. After authenticated-session creation is acknowledged, establishment records `AUTHENTICATED_SESSION_COMMITTED`; only then does it delete the pre-auth auth-flow session; only after deletion acknowledgement does it record an opaque cleanup proof and transition the handoff to `AUTH_FLOW_DELETED`. The authenticated credentials and cookie-clear result are returned only after the cleanup-proof write is acknowledged.

Failure at any post-creation stage returns the existing sanitized cleanup failure and attempts to revoke the new authenticated session. The durable handoff remains at the last acknowledged state for fail-closed diagnosis and later recovery. Compatibility constructors remain source-compatible. Committed replay orchestration is still excluded from this round.

####### Subtask 006 prerequisite round 001A-6 implementation 005-007: committed replay orchestration

Completed committed Login replay from the durable OTP consumption proof before pre-auth context loading. Replay requires an unexpired `AUTH_FLOW_DELETED` handoff with matching operation and reservation identities, loads the exact protected authenticated session without sliding expiry, verifies handoff/session identity, reconstructs the account-selection response from encrypted authoritative session accounts, conditionally rotates only the authenticated CSRF hash, and releases the raw replacement token only after the compare-and-replace acknowledgement.

Replay does not re-run OTP verification, APIM account lookup, account validation, Account-Ref assignment, authenticated-session creation, or auth-flow deletion. Missing, expired, conflicting, tampered, unusable, or concurrently changed state fails closed with the existing sanitized system failure.

001A-6 005-007 compatibility repair 001: replaced the invalid lambda used for the multi-method `AuthenticatedLoginHandoffPort` with an explicit fail-closed compatibility implementation. The active `ALREADY_COMMITTED` branch now uses the pre-auth-independent two-argument recovery method; the superseded three-argument contract is retained only as a non-executable traceability comment for the earlier architecture guard.

001A-6 005-008 architecture repair 001: restored the original five-operation `AuthenticatedSessionPort`, moved committed-replay load and conditional CSRF rotation into the dedicated `AuthenticatedLoginReplaySessionPort`, and moved direct Redis/Lua composition into a replay-specific adapter and configuration. `LoginOtpVerifyService` now depends only on `RecoverCommittedLoginUseCase`; the replay service owns durable handoff validation, protected-session reconstruction, account reconstruction, and conditional CSRF rotation. Existing Task 0043 composition boundaries remain intact.

001A-6 005-008 no-legacy guard repair 005: preserved the permanent first-request continuation trace `continueToAccounts(c, x)` next to the active proof-aware continuation call. The executable call remains `continueToAccounts(c, x, r.proof())`, so the original committed proof remains authoritative and no legacy OTP verification path is restored. The guard itself was not changed.

001A-6 005-008 replay contract architecture alignment fix 006: aligned the opt-in replay contract with the approved architecture repair. The contract now verifies orchestration in `LoginOtpVerifyService`, durable recovery in `RecoverCommittedLoginService`, and conditional persistence through `AuthenticatedLoginReplaySessionPort`. It no longer requires replay methods on the protected five-operation `AuthenticatedSessionPort` or direct session persistence inside the Login OTP verification service.

####### Subtask 006 prerequisite repair round 001A-7R: Reactive mutation-lock release stabilization

Baseline: 48be523d6e80aa71d202b9eea7cda93bbfc36f4d (`feat(login): add durable authenticated-session handoff`).

Replaced three fire-and-forget Forgot Password mutation-lock release subscriptions with cleanup composed into the returned Reactor publisher through `Mono.usingWhen`. Success, error, and cancellation now participate in asynchronous release without `subscribe()` or `block()`. Release errors remain sanitized; protected or irreversible work is not retried. No completion lock, nested lock, public contract, persistence model, or workflow behavior changed.

Validation: fail-first defect reproduced; focused repair, Forgot Password, Login, and Registration regressions passed; full Maven suite and canonical compile passed; architecture, security/scope, and diff audits passed. Evidence: `target/0061-subtask-006-001a-7r-fix-001-evidence-20260802-061723`.

Exclusions preserved: no public Forgot Password Resend endpoint; no resend generation/delivery; no Forgot Password OTP verification; no reset authorization; no password submission or TRPResetPassword; no ActorContext or Account-Ref change; no Subtask 007 behavior. Commit this repair, then regenerate cumulative 001A-7 against the repair commit. Round 006-002 remains blocked. Subtask 006 remains incomplete. Subtask 007 has not started.

####### Subtask 006 prerequisite repair round 001A-7R2: Registration Completion-Lock Reactive Release Stabilization

Baseline: 2fe9c7fd12c7e577990aa50189cdb77a7ed9a1d0 (`fix(auth): compose mutation-lock release reactively`).

Replaced the detached `CompleteRegistrationService#acquire(...)` completion-lock release with `Mono.usingWhen` success, error, and cancellation cleanup using the original ownership-safe handle. Acquisition, contention, protected Registration ordering, idempotency, session transitions, and public behavior remain unchanged. Protected work is never retried. Protected-work errors remain primary if cleanup fails. Successful work waits for acknowledged release; false, empty, and ambiguous release fail closed.

Validation included fail-first architecture coverage, focused Registration completion and completion-lock tests, Registration OTP/idempotency, Login, Forgot Password, prior 001A-7R, production-wide reactive terminal audit, full Maven suite, canonical compile, and diff checks. Evidence: `target/0061-subtask-006-001a-7r2-fix-004-evidence-20260802-065826`.

Cumulative 001A-7 remains blocked until this repair is committed and validation is regenerated. Round 006-002 remains blocked and not started. Subtask 006 remains incomplete. Subtask 007 has not started.

####### Subtask 006 prerequisite round 001A-7: Cumulative Validation and Handover

Baseline: 48ce4b017a195542d1843a10fd4e6947cd0c2339 (`fix(auth): compose registration completion-lock release reactively`).

Validated cumulative capabilities, including committed repairs 001A-7R and 001A-7R2:

- 001A-1 through 001A-4 establish schema marker version 2, persisted opaque session revisions, lock-protected legacy initialization, conditional revision-aware lifecycle mutations, fail-closed unversioned mutations, and production writer migration.
- 001A-5 and 001A-5R establish action-scoped HMAC Login idempotency persistence identities, durable OTP_VERIFY reservation, stable recovery references, conclusive attempt recording, and non-null consumption-proof recovery. Raw public Idempotency-Key values are not persistence identities.
- 001A-6 establishes one reserved authenticated-session identity per handoff, commit-before-cleanup ordering, acknowledged cleanup proof before credential return, pre-auth-independent committed replay, authoritative encrypted account reconstruction, non-sliding replay load, and conditional authenticated CSRF-hash rotation with raw-token release only after persistence acknowledgement.

Architecture, reactive repair preservation, and limitations:

- AuthenticatedSessionPort retains its approved five-operation boundary; replay loading and conditional CSRF rotation remain in AuthenticatedLoginReplaySessionPort.
- LoginOtpVerifyService depends on RecoverCommittedLoginUseCase; RecoverCommittedLoginService owns replay orchestration; Redis and narrow single-record conditional scripts remain adapter-owned. No cross-record Lua transaction is claimed.
- Missing, expired, tampered, conflicting, ambiguous, or concurrently changed recovery state fails closed.
- Schema version 2 rollout requires all revision-unaware writer pods to be drained; rollback to a revision-unaware writer remains prohibited.

Validation results:

- Grouped 001A-1/2, 001A-3/4, 001A-5/5R, opt-in 001A-6, and 001A-7R/001A-7R2 repair suites: PASS.
- Login, Registration, and Forgot Password cumulative regressions: PASS.
- Full `mvn test`: PASS.
- Canonical `mvn -DskipTests compile`: PASS.
- Architecture and security/public-boundary audits: PASS.
- `git diff --check`: PASS.
- Evidence directory: `target/0061-subtask-006-001a-7-evidence-20260802-074929`.

Exclusions preserved: no public Forgot Password Resend endpoint; no resend candidate creation or delivery; no Forgot Password OTP verification; no reset authorization; no password submission, TRPResetPassword, or password completion; no final page/error YAML; no ActorContext change; no Account-Ref requirement for Forgot Password; and no Subtask 007 or later behavior.

Prerequisite 001A is complete. The next authorized round is 006-002 OTP Resend Orchestration and Endpoint. Subtask 006 remains incomplete. Subtask 007 has not started.

### Subtask 006 implementation round 002: OTP Resend Orchestration and Endpoint

Baseline: 48ce4b017a195542d1843a10fd4e6947cd0c2339 (`fix(auth): compose registration completion-lock release reactively`).

Implemented cumulative resend scope for Forgot Password OTP:

- Added `ResendForgotPasswordOtpUseCase` and `ResendForgotPasswordOtpService` as the Forgot Password-specific resend orchestration path.
- Added public `POST /api/v1/auth/forgot-password/otp/resend` in `AuthForgotPasswordOtpController` using `auth_flow_sid`, `X-XSRF-TOKEN`, `Origin`/`Referer`, `X-Request-Id`, `Accept-Language`, and `Idempotency-Key`, with no `Account-Ref` and no request body.
- Added `ForgotPasswordOtpResendWebMapper` to normalize public mutation responses to the approved workflow/result contract and keep downstream delivery statuses internal.
- Wired resend beans in `ForgotPasswordApplicationConfig` and enabled forgot-password idempotency pending commit references for `OTP_RESEND` in `RedisForgotPasswordIdempotencyAdapter`.

Behavior and ownership notes:

- Resend orchestration acquires one auth-flow mutation lock, reloads the authoritative session snapshot under the lock, validates CSRF and Origin/Referer against the reloaded session, then evaluates resend eligibility using the existing 006-001 foundation.
- The current authoritative OTP challenge remains authoritative until message delivery is accepted. Resend creates a candidate challenge first, stages pending resend metadata in the auth-flow session, then transitions authority only after accepted delivery.
- Failed delivery invalidates only the candidate challenge and clears pending resend metadata. Unknown delivery preserves the original authoritative challenge and moves the protected Forgot Password phase to `OTP_DELIVERY_UNKNOWN`.
- Completed idempotent replay resolves from the persisted opaque resend commit reference and reconstructs the verify-OTP payload through `ForgotPasswordOtpReplayResolver` without re-sending.

Validation results:

- Fail-first architecture tests for the missing resend slice: PASS after implementation.
- Focused resend validation: `runTests` on `ForgotPasswordOtpResendOrchestrationSubtask006002FailFirstTest` and `AuthForgotPasswordOtpResendEndpointArchitectureFailFirstTest`: PASS.
- Narrow diagnostics on all touched production files: PASS after one local resend-service repair.
- Adjacent broader validation from surefire reports: `RedisForgotPasswordIdempotencyAdapterTest`, `AuthForgotPasswordControllerTest`, and `ForgotPasswordWorkflowPropertiesValidationTest`: PASS.
- Full `mvn test`: NOT RUN in this round.
- Canonical `mvn -DskipTests compile`: NOT RUN in this round.
- `git diff --check`: NOT conclusively captured in this round because the terminal reused prior Maven output.
- Evidence directory: `target/0061-subtask-006-002-evidence-20260802-161431`.

Exclusions preserved: no Forgot Password OTP verification flow; no reset authorization consumption; no password submission or `TRPResetPassword`; no Subtask 007 behavior; no ActorContext change; and no `Account-Ref` requirement for Forgot Password resend.

### Subtask 006 implementation round 003: OTP Verification and Reset Authorization Orchestration

Baseline: 48ce4b017a195542d1843a10fd4e6947cd0c2339 (`fix(auth): compose registration completion-lock release reactively`).

Implemented cumulative verify scope for Forgot Password OTP:

- Added `VerifyForgotPasswordOtpUseCase` and `VerifyForgotPasswordOtpService` as the Forgot Password-specific OTP verification path.
- Added public `POST /api/v1/auth/forgot-password/otp/verify` in `AuthForgotPasswordOtpController` using `auth_flow_sid`, `X-XSRF-TOKEN`, `Origin`/`Referer`, `X-Request-Id`, `Accept-Language`, and `Idempotency-Key`, with a request body containing only `code`.
- Added `ForgotPasswordOtpVerifyWebMapper` and the password-setup response DTO so successful verification returns the standard mutation envelope with `nextStep=PASSWORD_SETUP` and an empty data object.
- Added server-side reset-authorization issuance only inside the protected Forgot Password context via `ForgotPasswordResetAuthorization`; no reset token, cookie, or other client-visible authorization artifact is published.
- Added protected-context update support in `ForgotPasswordAuthFlowContext` and wired verify beans in `ForgotPasswordApplicationConfig`.

Behavior and ownership notes:

- Verification acquires the auth-flow mutation lock, reloads the authoritative session under the lock, requires `FORGOT_PASSWORD` plus shared `STARTED`, validates CSRF and `Origin`/`Referer`, and requires protected phase `OTP_SENT` with an active authoritative challenge.
- Successful verification marks the shared auth-flow session `RESET_AUTHORIZED`, clears `activeOtpChallengeId`, persists the protected reset authorization encrypted in the existing Forgot Password context, and refreshes auth-flow expiry.
- Completed idempotent replay reconstructs only the safe workflow outcome (`PASSWORD_SETUP` or the relevant OTP failure step) from forgot-password idempotency state; it does not expose session identifiers, CSRF material, OTP codes, or reset-authorization internals.
- Invalid, expired, locked, invalidated, already-verified, or missing OTP challenges remain internal verification outcomes and map to the existing public workflow contract without exposing downstream or protected details.
- The only browser security context remains `auth_flow_sid` plus `X-XSRF-TOKEN`; reset authorization stays bound to the existing server-side pre-auth session.

Validation results:

- Fail-first verify coverage was added first in `AuthForgotPasswordOtpVerifyControllerTest` and `VerifyForgotPasswordOtpServiceTest`.
- Focused verify validation after implementation: `runTests` on `AuthForgotPasswordOtpVerifyControllerTest` and `VerifyForgotPasswordOtpServiceTest`: PASS.
- Adjacent forgot-password regression validation: `runTests` on controller, protected-context loader, reset-authorization validator, protector, idempotency contract, resend architecture, and verify tests: PASS.
- Full `mvn test`: PASS (user-confirmed after round 006-003 implementation).

Exclusions preserved: no reset-authorization publication in the response body, cookie, or any other client-visible transport; no reset-authorization consumption; no password submission or `TRPResetPassword`; no final page/error YAML; no ActorContext change; and no `Account-Ref` requirement for Forgot Password verify.

### Subtask 006 implementation round 004: Stabilization, traceability, regression, security, and evidence closeout

Scope:

- Closeout only. This round did not authorize or implement password submission, reset-authorization consumption, `TRPResetPassword`, Subtask 007 behavior, or any later Forgot Password flow surface.

Implemented stabilization and traceability updates:

- Corrected forgot-password OTP verify idempotency hashing so the canonical `OTP_VERIFY` request hash excludes both OTP value and auth-flow session identity.
- Aligned forgot-password OTP verify under-lock reload to use the revisioned auth-flow snapshot path.
- Added concrete forgot-password resend controller and service coverage for cookie/header mapping, idempotency-key enforcement, mutation-lock release, and missing-session fail-closed behavior.
- Added forgot-password OTP reactive architecture guard coverage to preserve single mutation-lock acquisition, `Mono.usingWhen(...)` release, revisioned snapshot reload, and the absence of blocking or completion-lock usage in resend and verify orchestration.
- Added resend web-mapper closeout coverage and retained the approved public contract shape with no secret, OTP, session, CSRF, pending-reference, or reset-authorization leakage.

Validation results:

- Focused resend controller, resend service, reactive architecture, verify controller, verify service, and verify idempotency-guard validation: PASS.
- Discoverable forgot-password OTP cumulative batch for 006-001, 006-002, 006-003, and 006-004 closeout slices: PASS.
- Narrow diagnostics on all touched production and test files: PASS.
- `git diff --check`: PASS.
- Full `mvn test`: PASS (user-confirmed manual run after 006-004 closeout).
- Evidence directory: `target/0061-subtask-006-004-evidence-20260802-182952`.

Exclusions preserved:

- No reset-authorization publication or consumption.
- No password submission.
- No `TRPResetPassword`.
- No final Forgot Password page or error YAML.
- No ActorContext change.
- No `Account-Ref` requirement for Forgot Password.
- No Subtask 007 or later behavior.

Stop point: Subtask 006 is complete through round 006-004. The next authorized work is Subtask 007 password completion and finalization. Subtask 007 starts below.

## Subtask 007 implementation round 001: Password completion and finalization

Commit message:

feat(forgot-password): complete password reset finalization flow

Implemented:

- Added the public `POST /api/v1/auth/forgot-password/complete` endpoint with `auth_flow_sid`, `X-XSRF-TOKEN`, `Origin`/`Referer`, `X-Request-Id`, `Accept-Language`, and `Idempotency-Key` handling, plus a request body containing only `password` and `confirmPassword`.
- Added the completion application boundary with `CompleteForgotPasswordUseCase`, `CompleteForgotPasswordCommand`, `CompleteForgotPasswordResult`, and `CompleteForgotPasswordStatus`.
- Added forgot-password-specific structural password validation that reuses the repository-approved registration `PasswordPolicy` contract through `AuthWorkflowPolicyPort` without coupling to registration web or APIM code.
- Extended the protected Forgot Password phase vocabulary with `PASSWORD_SETUP` and updated OTP verification so successful OTP verification transitions the protected context into password setup before completion.
- Added reset-authorization validation under the mutation lock using the existing protected-context validator and fail-closed session reload semantics.
- Added COMPLETE idempotency reservation, replay, and unknown-outcome blocking using the forgot-password idempotency port and a constant request hash that excludes password material and auth-flow session identity.
- Added one mutation-lock lifecycle for completion using the existing auth-flow mutation lock and revision-aware session reload under lock.
- Added APIM `TRPResetPassword` integration under `adapter/out/apim/forgotpassword` with request mapping restricted to protected `cust-id` and submitted password, `X-Request-Id` propagation only, safe request `toString()` redaction, and exact success/rejection interpretation.
- Recognized `err.tpa.password.invalid` as a replayable business rejection targeting `password` without authorization consumption or cookie clearing.
- Recognized `err.tpa.member.not.found` as a terminal business rejection that closes the flow, clears pre-auth cookies, and returns `RETURN_TO_LOGIN`.
- Persisted unknown downstream outcomes as irreversible COMPLETE unknown outcome state, blocked later retries across idempotency keys, preserved pre-auth cookies, and returned sanitized system-unavailable failure.
- Added conditional reset-authorization consumption on success only.
- Added revision-aware terminal finalization and replay support for completed and cleanup-pending outcomes using persisted replay snapshots.
- Added cookie clearing on terminal success/member-not-found responses and preserved cookies for recoverable or unknown-outcome cases.

Validation:

- Failing test added or updated first: `ApimForgotPasswordCompletionAdapterTest`, `CompleteForgotPasswordServiceTest`, and `AuthForgotPasswordCompleteControllerTest`.
- Expected failure reason: the repository had no `TRPResetPassword` adapter, no completion use case/controller slice, no `PASSWORD_SETUP` protected phase, and no forgot-password password-completion orchestration.
- Targeted failure result: focused tests initially failed with missing symbols for `CompleteForgotPasswordService`, `CompleteForgotPasswordCommand`, `ForgotPasswordPasswordValidator`, and `PASSWORD_SETUP`; the first post-implementation happy-path service run then exposed a fixture defect with `INVALID_AUTH_FLOW` returned instead of `SUCCESS` until protected context attributes were populated correctly.
- Production change made after failing test: implemented the APIM completion adapter, completion use case/service, password validator, completion controller/mapper/DTOs, config wiring, protected phase transition, replay handling, and unknown-outcome blocking.
- Targeted test pass result: `runTests` on `ApimForgotPasswordCompletionAdapterTest`, `CompleteForgotPasswordServiceTest`, and `AuthForgotPasswordCompleteControllerTest`: PASS.
- Broader validation result: `runTests` on `ApimForgotPasswordCompletionAdapterTest`, `VerifyForgotPasswordOtpServiceTest`, `CompleteForgotPasswordServiceTest`, and `AuthForgotPasswordCompleteControllerTest`: PASS with 14 passed and 0 failed.
- Surefire report evidence:
  - `com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.ApimForgotPasswordCompletionAdapterTest`: 3 tests, 0 failures, 0 errors.
  - `com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify.VerifyForgotPasswordOtpServiceTest`: 2 tests, 0 failures, 0 errors.
  - `com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.CompleteForgotPasswordServiceTest`: 5 tests, 0 failures, 0 errors.
  - `com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthForgotPasswordCompleteControllerTest`: 3 tests, 0 failures, 0 errors.
- Full `mvn test`: NOT RUN after Subtask 007; focused completion and adjacent forgot-password validation were run instead.
- Canonical `mvn -DskipTests compile`: NOT RUN after Subtask 007 because the focused tests already compiled and exercised the touched slice.
- Additional evidence note: `docs/auth/0061-subtask-007-forgot-password-completion.md`.

Exclusions preserved:

- No authenticated-session creation or automatic Login after reset.
- No post-login Change Password flow.
- No `Account-Ref` requirement for Forgot Password completion.
- No `ActorContext` change.
- No Subtask 008, Subtask 009, or Subtask 010 behavior.

Stop point: Subtask 007 is complete. Later Forgot Password subtasks remained out of scope for that round.

## Subtask 008 implementation round 001: FE-owned presentation-boundary closeout

Commit message:

Not committed in this round.

Implemented:

- Preserved the authoritative boundary that all pages, page metadata, labels, headings, descriptions, instructions, button text, customer-facing messages, and localization remain FE-owned.
- Hardened forgot-password public web mappers so BFF responses expose only machine-readable statuses, HTTP-mapped statuses, next steps, error codes, exact FE field targets where applicable, approved action identifiers, and previously approved endpoint data.
- Removed public forgot-password use of synthetic page-level error targets such as `PAGE`; global forgot-password failures now return an empty `errors[].targets` list, while exact field targets such as `username`, `identityType`, `identityValue`, `email`, `code`, and `password` remain intact.
- Ensured forgot-password `messages` remain empty across the covered mutation responses.
- Ensured forgot-password `errors[].message` remains null or absent at the public boundary and no localized display text is emitted from the BFF.
- Preserved `Accept-Language` only for approved non-response behavior such as downstream handling and OTP delivery/template behavior; it does not alter public forgot-password status, nextStep, targets, codes, actions, or response structure.
- Preserved Login and Registration contracts and made no changes to workflow progression, OTP behavior, idempotency semantics, reset authorization, locking, revision handling, `TRPResetPassword`, or unknown-outcome behavior.

Validation:

- Failing test added first: `ForgotPasswordPublicResponseContractSubtask008FailFirstTest`.
- Expected failure reason: forgot-password web mappers still exposed forbidden `PAGE` targets and the start-flow mapping could surface customer-facing display text in public errors.
- Targeted failure result: the new Subtask 008 contract test failed first on a leaked non-null public error message; the first repair rerun then exposed one local null-target sanitization bug, which was fixed before proceeding.
- Production change made after failing test: sanitized forgot-password web-error mapping so global targets are empty, `errors[].message` remains null, and only exact FE field targets remain where already approved.
- Targeted test pass result: `runTests` on `ForgotPasswordPublicResponseContractSubtask008FailFirstTest`: PASS (3 tests, 0 failures).
- Focused forgot-password web regression result: `runTests` on `ForgotPasswordPublicResponseContractSubtask008FailFirstTest`, `ForgotPasswordStartWebMapperTest`, `ForgotPasswordOtpResendWebMapperTest`, `AuthForgotPasswordControllerTest`, `AuthForgotPasswordOtpResendControllerTest`, `AuthForgotPasswordOtpVerifyControllerTest`, and `AuthForgotPasswordCompleteControllerTest`: PASS (32 tests, 0 failures).
- Forgot-password architecture guard result: `runTests` on `ForgotPasswordOtpReactiveArchitectureGuardTest`, `ForgotPasswordOtpVerifyIdempotencyHashArchitectureGuardTest`, and `AuthForgotPasswordOtpResendEndpointArchitectureFailFirstTest`: PASS (9 tests, 0 failures).
- Full `mvn test`: PASS (manual user-confirmed run after Subtask 008; the prior broader Maven suite run also completed with 2845 tests, 0 failures, 0 errors, 12 skipped).
- Surefire inspection after broader validation found no `Failures` or `Errors` entries in `target/surefire-reports/*.txt`.
- Canonical `mvn -DskipTests compile`: PASS.
- `git diff --check`: PASS.

Exclusions preserved:

- No frontend code.
- No page YAML or customer-facing translations.
- No forgot-password page, `pageId`, `pageRef`, labels, descriptions, button text, or localized display content in public BFF responses.
- No workflow, OTP, idempotency, reset-authorization, locking, revision, `TRPResetPassword`, or unknown-outcome semantic changes.
- No Subtask 009 or Subtask 010 behavior.

Stop point: Subtask 008 is complete. The next authorized work is Subtask 009. Later Forgot Password subtasks remain out of scope for this round.

## Subtask 009: Observability, Security, and Contract Tests

Commit message:

Not committed in this round.

Implemented:

- Added focused forgot-password controller contract tests for the identity-check and details endpoints to verify presentation-neutral envelopes, required cookie/XSRF guards, request-id echo, and confidentiality redaction.
- Hardened ForgotPasswordDetailsRequest so record toString() no longer leaks date of birth, email, or mobile values in logs or assertion output.
- Added X-Request-Id response echo on forgot-password identity-check and details endpoints to align those controllers with the repository request-correlation direction.
- Added architecture coverage to keep forgot-password controllers free of Account-Ref coupling and to preserve required protected-endpoint headers.
- Added an architecture guard proving identity/details/session-binding request paths reload auth-flow state through loadSnapshot(...) before revision-aware mutation.
- Migrated VerifyForgotPasswordIdentityService, ForgotPasswordDetailsOtpOrchestrationService, and ForgotPasswordOtpSessionBindingAdapter from legacy sessions.load(...) reloads to loadSnapshot(...).
- Wired shared SecurityAuditEventPort publication into DefaultForgotPasswordProtectedAuthFlowContextLoader so protected forgot-password identity/details/otp/complete flows emit structured low-cardinality audit events when CSRF or Origin/Referer validation rejects the request.
- Preserved FE-owned presentation boundaries from Subtask 008: no page metadata, no customer-facing localized messages, empty messages, and no populated errors[].message values were introduced.
- Preserved existing forgot-password anti-abuse thresholds and workflow semantics; no new rate-limit values, progression changes, or Subtask 010 behavior were introduced.

Validation:

- Failing test added first: AuthForgotPasswordDetailsControllerTest, AuthForgotPasswordIdentityControllerTest, ForgotPasswordRevisionAwareSessionReloadContractSubtask009FailFirstTest, and DefaultForgotPasswordProtectedAuthFlowContextLoaderTest.
- Expected failure reason: details request logging leaked sensitive fields, identity/details controllers did not consistently echo X-Request-Id, request-path session reloads still used sessions.load(...), and protected forgot-password CSRF/origin rejection paths emitted no shared security audit event.
- Targeted test command: runTests on AuthForgotPasswordDetailsControllerTest and AuthForgotPasswordIdentityControllerTest, then runTests on ForgotPasswordRevisionAwareSessionReloadContractSubtask009FailFirstTest, then runTests on DefaultForgotPasswordProtectedAuthFlowContextLoaderTest.
- Targeted failure result: initial focused runs failed on unredacted ForgotPasswordDetailsRequest.toString(), missing request-id echo coverage, legacy sessions.load(...) assertions, and missing protected-context audit publication.
- Production change made after failing test: redacted ForgotPasswordDetailsRequest, added request-id echo to forgot-password identity/details controllers, switched request-path reloads to loadSnapshot(...), and published SecurityAuditEventPort events from the shared forgot-password protected-context loader on CSRF/origin rejection.
- Targeted test pass result: runTests on DefaultForgotPasswordProtectedAuthFlowContextLoaderTest: PASS (5 tests, 0 failures). The earlier targeted controller and architecture tests also passed after the corresponding repairs.
- Broader validation command/result: runTests for the full repository: PASS (3474 tests, 0 failures). Canonical mvn -DskipTests compile: PASS. git diff --check: PASS aside from existing line-ending warnings emitted by Git for CRLF normalization.
- Tests not run and why: no separate terminal mvn test rerun was used for the final full-suite evidence because the repository-local runTests runner has been more reliable than terminal Maven output for complete result capture in this workspace.

Exclusions preserved:

- No frontend page ownership changes.
- No new rate-limit or anti-abuse thresholds.
- No workflow progression, OTP, reset-authorization, or password-reset business semantic changes beyond the revision-aware reload correction.
- No new audit platform, metrics backend, tracing backend, or public response fields.

Stop point: Subtask 009 is complete. The next authorized work is Subtask 010. Later Forgot Password subtasks remain out of scope for this round.

## Subtask 010: Validation-first final Gate 6 closeout

Commit message:

Not committed in this round.

Implemented:

- No new business behavior was added in this round.
- Reconciled the approved Feature 0061 forgot-password decisions against production symbols, configuration, direct canonical Maven validation, focused executable regressions, architecture guards, confidentiality controls, and the tracked evidence bundle.
- Re-verified the FE-owned presentation boundary: no forgot-password page metadata, `pageId`, `pageRef`, labels, localized display content, populated `messages`, or populated `errors[].message` values were introduced at the public boundary.
- Re-verified the protected forgot-password phases, idempotency, revision-aware reload, Redis persistence, APIM completion boundary, request-correlation echo, `Accept-Language` handling, and no-`Account-Ref` pre-login constraints against the current production symbols.
- Re-ran the mandatory canonical commands directly and recorded the outputs under `target/0061-subtask-010-evidence-20260803-153344`.
- Added final tracked closeout evidence and traceability documentation in `docs/auth/0061-subtask-010-gate6-closeout.md`.

Validation:

- Failing test added first: not applicable because this round made no production behavior change.
- Expected failure reason: not applicable.
- Targeted test command: direct `mvn test` with the canonical environment.
- Targeted failure result: not applicable.
- Production change made after failing test: documentation and evidence only.
- Targeted test pass result: direct `mvn test` completed with `Tests run: 2860, Failures: 0, Errors: 0, Skipped: 12`, `BUILD SUCCESS`, and `EXIT_CODE=0`.
- Broader validation result:
  - direct `mvn -DskipTests compile`: PASS
  - direct `git diff --check`: PASS
  - focused forgot-password plus shared regression batches: PASS
  - repository-wide reactive audit: only `AuthFlowSessionPersistenceCompatibilityStartupGate` uses `.block(timeout)` at startup; no forgot-password request-path blocking was found
- Tests not run and why: none of the mandatory canonical commands were skipped.
- Evidence directory: `target/0061-subtask-010-evidence-20260803-153344`.
- Additional evidence note: `docs/auth/0061-subtask-010-gate6-closeout.md`.

Closeout verdict:

- Gate 6 closeout criteria are satisfied for Feature 0061 in this repository state.

Stop point: Subtask 010 is complete. Feature 0061 Gate 6 closeout is complete for the validated repository state captured by this handover.
# Feature 0061 Subtask 010 Gate 6 Closeout

## Scope

Subtask 010 was a validation-first final Gate 6 closeout round. It did not authorize new forgot-password business behavior. The round was limited to reconciling approved Feature 0061 decisions against production symbols, configuration, executable tests, architecture guards, confidentiality/security controls, and tracked evidence.

## Final Verdict

- Gate 6 closeout criteria are satisfied for the validated repository state.
- No production behavior was changed in this round.
- The mandatory direct canonical commands completed successfully.
- The forgot-password public boundary remains FE-owned and presentation-neutral.
- The forgot-password runtime slice remains reactive on request paths.

## Canonical Command Evidence

Evidence bundle: `target/0061-subtask-010-evidence-20260803-153344`

Mandatory direct command results:

| Command | Evidence file | Result |
|---|---|---|
| `mvn test` | `target/0061-subtask-010-evidence-20260803-153344/25-full-direct-maven-test.txt` | PASS: `Tests run: 2860, Failures: 0, Errors: 0, Skipped: 12`, `BUILD SUCCESS`, `EXIT_CODE=0` |
| `mvn -DskipTests compile` | `target/0061-subtask-010-evidence-20260803-153344/08-initial-compile.txt` | PASS: `BUILD SUCCESS`, `EXIT_CODE=0` |
| `git diff --check` | `target/0061-subtask-010-evidence-20260803-153344/27-git-diff-check.txt` | PASS: `EXIT_CODE=0` |

Surefire inspection outcome:

- `target/surefire-reports/` was generated by the direct canonical `mvn test` run and contains the repository test reports for the validated state.

## Repository-Wide Audit Summary

Reactive audit:

- Repository-wide search for `.block(`, `.blockOptional(`, `toFuture().get()`, and `.subscribe(` found one production match only.
- The only match is the startup-only compatibility gate in `src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/compatibility/AuthFlowSessionPersistenceCompatibilityStartupGate.java`.
- No forgot-password request-path blocking was found.

Public boundary audit:

- Forgot-password public responses remain machine-readable and FE-owned.
- No public forgot-password `pageId`, `pageRef`, labels, localized display content, populated `messages`, or populated `errors[].message` values were introduced.

Security and confidentiality audit:

- Protected forgot-password steps remain guarded by auth-flow cookie, XSRF, and Origin/Referer validation.
- Request correlation remains based on `X-Request-Id` echo and propagation.
- `Accept-Language` remains the canonical language input.
- Pre-login forgot-password flows remain free of `Account-Ref` requirements.
- Redis/session/APIM handling remains behind the approved boundaries with focused guard coverage and no public secret leakage.

## Final Traceability Matrix

| Approved requirement or decision | Production symbols and config | Executable proof | Audit or evidence |
|---|---|---|---|
| Start flow remains pre-login, idempotent, correlated, and language-aware without `Account-Ref` | `AuthForgotPasswordController`, `StartForgotPasswordService`, `ForgotPasswordApplicationConfig`, `application.yml` `auth-workflows.forgot-password` | `AuthForgotPasswordControllerTest`, `StartForgotPasswordServiceTest` | Direct suite evidence in `25-full-direct-maven-test.txt` |
| Identity-check stays protected, fail-closed, and revision-aware | `AuthForgotPasswordIdentityController`, `VerifyForgotPasswordIdentityService`, `DefaultForgotPasswordProtectedAuthFlowContextLoader` | `AuthForgotPasswordIdentityControllerTest`, `ForgotPasswordRevisionAwareSessionReloadContractSubtask009FailFirstTest` | Protected-context audit in `DefaultForgotPasswordProtectedAuthFlowContextLoader` and direct suite evidence |
| Details verification and immediate OTP stay behind protected session state and approved config | `AuthForgotPasswordDetailsController`, `VerifyForgotPasswordDetailsService`, `ForgotPasswordDetailsOtpOrchestrationService`, `application.yml` `details-and-otp` | `AuthForgotPasswordDetailsControllerTest`, `ForgotPasswordDetailsApimSubtask005001FailFirstTest`, `ForgotPasswordDetailsOtpOrchestrationSubtask005004FailFirstTest` | Direct suite evidence and focused cumulative forgot-password regressions |
| OTP resend and verify remain reactive, locked, idempotent, and revision-aware | `AuthForgotPasswordOtpController`, `ResendForgotPasswordOtpService`, `VerifyForgotPasswordOtpService`, `application.yml` `otp` and `idempotency` | `AuthForgotPasswordOtpResendControllerTest`, `AuthForgotPasswordOtpVerifyControllerTest`, `ForgotPasswordOtpReactiveArchitectureGuardTest`, `ForgotPasswordOtpVerifyIdempotencyHashArchitectureGuardTest` | Reactive audit found no forgot-password request-path blocking |
| Completion remains behind protected reset authorization and APIM `TRPResetPassword` boundary | `AuthForgotPasswordCompleteController`, `CompleteForgotPasswordService`, `ApimForgotPasswordCompletionAdapter`, `application.yml` `reset-authorization` | `AuthForgotPasswordCompleteControllerTest`, `CompleteForgotPasswordServiceTest`, `ApimForgotPasswordCompletionAdapterTest` | Direct suite evidence and tracked Subtask 007 evidence |
| FE owns presentation: no page metadata, labels, localized display text, non-empty `messages`, or public `errors[].message` | Forgot-password web mappers including `ForgotPasswordStartWebMapper` and `ForgotPasswordCompleteWebMapper` | `ForgotPasswordPublicResponseContractSubtask008FailFirstTest`, `FrontendOwnedLocalizationMessageFreeErrorsTest`, `FrontendOwnedLocalizationPageContractTest` | Public boundary audit and direct suite evidence |
| Protected-step security and observability remain enforced | Forgot-password controllers, `DefaultForgotPasswordProtectedAuthFlowContextLoader`, shared request-header/request logging filters | `ForgotPasswordEndpointSecurityContractSubtask009Test`, `AuthForgotPasswordIdentityControllerTest`, `AuthForgotPasswordDetailsControllerTest`, `RequestHeaderContextWebFilterTest` | Security/confidentiality audit and direct suite evidence |
| Redis/session persistence remains internal, revision-aware, and confidentiality-safe | Redis forgot-password/session adapters, `DefaultForgotPasswordProtectedAuthFlowContextLoader`, `application.yml` `otp-commit` and context-protection settings | `RedisForgotPasswordIdempotencyAdapterTest`, `P4BAuthFlowSessionRedisSecurityGuardTest`, `DefaultForgotPasswordProtectedAuthFlowContextLoaderTest` | Reactive audit plus direct suite evidence |
| APIM details remain isolated to outbound adapters | `adapter/out/apim/forgotpassword/ApimForgotPasswordCompletionAdapter` and related mappers/DTOs | `ApimForgotPasswordCompletionAdapterTest`, `ApimRequestLoggingExchangeFilterTest` | Direct suite evidence and package-boundary review |
| Final repository state satisfies mandatory Gate 6 validation | direct Maven test, direct compile, direct diff check | `mvn test`, `mvn -DskipTests compile`, `git diff --check` | `25-full-direct-maven-test.txt`, `08-initial-compile.txt`, `27-git-diff-check.txt` |

## Focused Supplemental Validation

The direct canonical Maven run is the authoritative final proof. Additional focused executable batches were kept as closeout evidence for the forgot-password slice and adjacent preserved areas:

- forgot-password plus shared APIM-adjacent batch: PASS, 39 passed
- shared Redis, revision, and idempotency batch: PASS, 20 passed
- login preservation batch: PASS, 21 passed
- registration preservation batch: PASS, 31 passed
- forgot-password protected-context/security loader batch: PASS, 21 passed
- architecture, idempotency, and security-guard batch: PASS, 20 passed

## TDD Evidence Statement

Strict TDD fail-first flow does not apply to Subtask 010 itself because this round introduced no production behavior change. The closeout relies on existing fail-first tests from Subtasks 003 through 009 plus direct canonical validation of the final repository state.

## Conclusion

Feature 0061 Gate 6 is closed for the validated repository state captured by `target/0061-subtask-010-evidence-20260803-153344`. Any further forgot-password change should start as a new explicitly authorized scope rather than extending this closeout round.
