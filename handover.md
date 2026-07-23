# Handover

## Task 0043 - Login Integration

### 0043-001-001-baseline-characterization-and-architecture-guards

- Status: completed
- Scope: test-only characterization and architecture guards
- Production code changed by this script: no
- Added tests:
  - Task0043RegistrationOtpPolicyCharacterizationTest
  - Task0043AuthenticationFoundationArchitectureGuardTest
- Targeted command: mvn -Dtest=Task0043RegistrationOtpPolicyCharacterizationTest,Task0043AuthenticationFoundationArchitectureGuardTest,RegistrationOtpChallengeAdapterVerifyGuardTest,RegistrationOtpRateLimitAndDeliveryOwnershipTest,RegistrationOtpResendCooldownGuardTest,RegistrationOtpResendLifecycleOwnershipTest test
- Regression command: mvn -Dtest=RegistrationOtpChallengeLifecycleIntegrationTest,RegistrationOtpResendServiceIntegrationTest,RegistrationOtpVerificationLifecycleIntegrationTest,RegistrationContractStabilizationIntegrationTest,RegistrationRedisSensitiveDataSafetyIntegrationTest test
- Preservation: registration endpoints, JSON, OTP lifecycle, CSRF/cookies, Redis production keys, APIM calls, and portal context were not modified.
- Deferred: shared auth-flow contracts and compatibility adapters belong to 0043-002-001.
- Commit message: test(auth): characterize registration foundations before login integration

### 0043-002-002-fix-registration-compatibility-verification-accessor

- Status: completed
- Correction: Task0043RegistrationCompatibilityAdapterTest now uses the actual record accessor remainingVerifyAttempts().
- Root cause: 0043-002-001 used nonexistent remainingAttempts(), so Maven stopped before its handover update.
- Original fail-first evidence remains valid: target/0043-002-001-fail-first.log.
- Targeted command: mvn -Dtest=Task0043SharedAuthFlowContractArchitectureTest,Task0043RegistrationCompatibilityAdapterTest,Task0043RegistrationOtpPolicyCharacterizationTest,Task0043AuthenticationFoundationArchitectureGuardTest test
- Regression command: mvn -Dtest=RegistrationOtpChallengeLifecycleIntegrationTest,RegistrationOtpResendServiceIntegrationTest,RegistrationOtpVerificationLifecycleIntegrationTest,RegistrationContractStabilizationIntegrationTest,RegistrationRedisSensitiveDataSafetyIntegrationTest test
- Production behavior changed by this correction: no
- Commit message: test(auth): fix shared compatibility verification assertion

### 0043-002-004-fix-shared-otp-port-method-contract

- Status: completed
- Root cause: AuthFlowOtpPort declared create/resend/verify, while the existing production capability and RegistrationOtpPort use createOtp/resendOtp/verifyOtp.
- Correction: aligned the shared port method contract to the existing production API; no constructor was added or changed.
- Production constructors added for tests: none
- Original 0043-002-003 fail-first evidence remains valid: target/0043-002-003-fail-first.log
- Targeted command: mvn -Dtest=Task0043RegistrationSharedPortMigrationTest,Task0043SharedAuthFlowContractArchitectureTest,Task0043RegistrationOtpPolicyCharacterizationTest,Task0043AuthenticationFoundationArchitectureGuardTest test
- Regression command: mvn -Dtest=RegistrationOtpChallengeLifecycleIntegrationTest,RegistrationOtpResendServiceIntegrationTest,RegistrationOtpVerificationLifecycleIntegrationTest,RegistrationContractStabilizationIntegrationTest,RegistrationRedisSensitiveDataSafetyIntegrationTest test
- Preservation: registration OTP adapter implementation, constructors, endpoint contracts, OTP behavior, Redis state, cookies, CSRF, and public mappings remain unchanged.
- Commit message: fix(auth): align shared OTP port with existing registration contract

### 0043-003-006-fix-obsolete-registration-shared-port-migration-test

- Status: completed
- Removed obsolete Task0043RegistrationSharedPortMigrationTest because it required RegistrationOtpPort to extend AuthFlowOtpPort.
- Authoritative architecture: OtpChallengePort and MessageDeliveryPort are the shared technical cores; registration ports remain registration-specific facades.
- RegistrationSharedCoreMigrationArchitectureTest is the active migration guard.
- Production code changed by this correction: no
- Production constructors added or changed for tests: none
- Targeted command: mvn -Dtest=RegistrationSharedCoreMigrationArchitectureTest,Task0043OtpResendLimitPolicyTest,Task0043SharedAuthFlowContractArchitectureTest,Task0043RegistrationOtpPolicyCharacterizationTest,Task0043AuthenticationFoundationArchitectureGuardTest,RegistrationOtpChallengeAdapterVerifyGuardTest,RegistrationOtpResendCooldownGuardTest test
- Regression command: mvn -Dtest=RegistrationOtpChallengeLifecycleIntegrationTest,RegistrationOtpResendServiceIntegrationTest,RegistrationOtpVerificationLifecycleIntegrationTest,RegistrationContractStabilizationIntegrationTest,RegistrationRedisSensitiveDataSafetyIntegrationTest test
- Commit message: test(auth): align registration migration guard with shared core architecture

### 0043-004-001-apim-business-retry-classification-and-configuration

- Status: completed
- Added ApimRetryEligibility: SAFE_BUSINESS_CALL, IDEMPOTENT_MUTATION, NON_RETRYABLE
- Added apim.retry.business-call max-attempts and backoff-milliseconds to existing ApimProperties/application.yml
- max-attempts means total business-call attempts including the first call
- ApimWebClientFacade transport retry is intentionally deferred to 0043-004-002
- Existing token/certificate recovery behavior is unchanged
- Production constructors added or changed for tests: none
- Fail-first evidence: target/0043-004-001-fail-first.log
- Targeted command: mvn -Dtest=ApimBusinessRetryPolicyTest,ApimRetryClassificationArchitectureTest,ApimPropertiesBindingTest,ApimWebClientFacadeRetryTest test
- Regression command: mvn -Dtest=RegistrationContractStabilizationIntegrationTest,RegistrationLoggingLookupStabilizationIntegrationTest,RegistrationCompletionResilienceIntegrationTest test
- Commit message: feat(apim): add business-call retry classification and configuration

### 0043-004-009-replace-stale-otp-resend-policy-test-method

- Status: completed
- Replaced the complete stale test method using brace-balanced method boundaries.
- The test now verifies explicit resendLimitEnabled propagation and rejects the discarded -1 sentinel expression.
- Production code changed by this correction: no
- Production constructors added or changed for tests: none
- Targeted command: mvn -Dtest=Task0043OtpResendLimitPolicyTest,DefaultOtpChallengeServiceReplaceTest,DefaultOtpChallengeServiceCreateInvalidateTest,ApimBusinessCallRetryIntegrationTest,ApimBusinessRetryPolicyTest,ApimRetryClassificationArchitectureTest,ApimWebClientFacadeRetryTest,ApimPropertiesBindingTest test
- Regression command: mvn -Dtest=RegistrationOtpChallengeLifecycleIntegrationTest,RegistrationOtpResendServiceIntegrationTest,RegistrationOtpVerificationLifecycleIntegrationTest,RegistrationContractStabilizationIntegrationTest,RegistrationLoggingLookupStabilizationIntegrationTest,RegistrationCompletionResilienceIntegrationTest test
- Commit message: test(auth): replace stale OTP unlimited-sentinel assertion

### 0043-005-002-fix-login-foundation-yaml-key-assertions

- Status: completed
- Root cause: Task0043LoginFoundationTest asserted flattened names session-idle-timeout-seconds and account-identifier-width, while YAML correctly nests idle-timeout-seconds under session and identifier-width under account.
- Production code changed by this correction: no
- Production constructors added or changed for tests: none
- Targeted command: mvn -Dtest=Task0043LoginFoundationTest,AuthWorkflowPropertiesBindingTest,Task0043OtpResendLimitPolicyTest test
- Regression command: mvn -Dtest=RegistrationContractStabilizationIntegrationTest,RegistrationOtpResendServiceIntegrationTest,RegistrationOtpVerificationLifecycleIntegrationTest,ApimBusinessCallRetryIntegrationTest test
- Commit message: test(auth): align login foundation YAML assertions

### 0043-006-001-credential-authentication-application-contract

- Status: completed
- Added CredentialAuthenticationPort and transport-neutral login credential command/result contracts.
- Result supports an opaque actor reference and a selectable OTP destination list even when only one destination exists.
- Account-Ref and authenticated session are deliberately absent from the login credential command.
- Credentials are redacted by command toString and are not persisted by this contract.
- Added AuthenticateLoginCredentialsUseCase as the application boundary; no web or APIM implementation is included.
- Compatibility/test-only constructors added: none
- Fail-first evidence: target/0043-006-001-fail-first.log
- Targeted command: mvn -Dtest=Task0043CredentialAuthenticationContractTest,Task0043LoginFoundationTest test
- Regression command: mvn -Dtest=RegistrationContractStabilizationIntegrationTest,RegistrationOtpResendServiceIntegrationTest,ApimBusinessCallRetryIntegrationTest test
- Deferred: TRPMemberLogin APIM adapter in 0043-006-002
- Commit message: feat(auth): add credential authentication application contract

### 0043-006-003-fix-login-workflow-vocabulary-and-error-key-validation

- Status: completed
- Classification 1: stale test expectation. WorkflowNextStep is shared and now legitimately includes SELECT_OTP_DESTINATION and SELECT_ACCOUNT; the vocabulary test was renamed and aligned.
- Classification 2: production validator conflict. WorkflowKeyValidator rejected the approved public code err.login.incorrect.password because of its generic password-token guard.
- Validator correction is narrowly scoped to err.login.* public codes; arbitrary password-like keys remain rejected.
- Production constructors added or changed for tests: none
- Targeted command: mvn -Dtest=WorkflowVocabularyTest,WorkflowKeyValidatorLoginErrorPrefixTest,WorkflowKeyValidatorRegistrationErrorPrefixTest,Task0043LoginFoundationTest,TrpMemberLoginContractMapperTest,TrpMemberLoginAdapterTest,SharedApimTrpMemberLoginTransportArchitectureTest,Task0043CredentialAuthenticationContractTest test
- Regression command: mvn -Dtest=ApimBusinessCallRetryIntegrationTest,ApimWebClientFacadeRetryTest,RegistrationContractStabilizationIntegrationTest,RegistrationOtpChallengeLifecycleIntegrationTest,RegistrationOtpResendServiceIntegrationTest,RegistrationOtpVerificationLifecycleIntegrationTest test
- Commit message: fix(auth): align login workflow vocabulary and public error validation

### 0043-006-004-login-credential-policy-and-protected-preauth-context

- Status: completed after corrections through 0043-006-008-fix-login-foundation-fixture-postcondition
- Added configured username trim/ASCII lowercase validation, exact-password preservation, opaque destination IDs, masking, and approved protected login context.
- Added dedicated AES-256-GCM complete-context protection with no registration-secret fallback.
- Production constructors added merely for tests: none
- Fail-first evidence: target/0043-006-004-fail-first.log

### 0043-006-005-fix-login-preauth-policy-test-and-instant-codec

- Status: completed after corrections through 0043-006-008-fix-login-foundation-fixture-postcondition
- Corrected the ASCII normalization fixture and encoded credentialVerifiedAt explicitly as ISO-8601 inside the protected payload.

### 0043-006-006-fix-preauth-destination-order-preservation

- Status: completed after corrections through 0043-006-008-fix-login-foundation-fixture-postcondition
- Preserved validated APIM destination order using an immutable LinkedHashMap copy.

### 0043-006-007-fix-login-foundation-required-credential-policy-fixture

- Status: completed after postcondition correction in 0043-006-008-fix-login-foundation-fixture-postcondition
- Updated Task0043LoginFoundationTest with all newly required configured credential-policy values.
- Production validation remained unchanged.

### 0043-006-009-login-credential-idempotency-foundation
- Status: completed
- Added login credential idempotency key, request hash, lifecycle records, coordinator, and Redis adapter through the shared AuthFlowIdempotencyPort contract.
- Atomic reservation uses Redis SETNX with configured auth-workflows.login.idempotency.start-ttl-seconds.
- Request hash includes normalized username and exact password but exposes/stores only SHA-256 digest.
- Redis key contains only configured prefix, login action namespace, and opaque idempotency key.
- Replay snapshot is safe metadata only and cannot carry cookie, CSRF, credentials, destination, username, or session ID.
- Endpoint execution/completion and protected replay commitment are deferred to 0043-006-010.
- Production constructors added merely for tests: none
- Fail-first evidence: target/0043-006-009-fail-first.log
- Commit message: feat(auth): add login credential idempotency foundation

### 0043-006-010-login-preauth-session-establishment-orchestration

- Status: completed after test-double correction in 0043-006-011-fix-login-start-test-doubles
- Added StartLoginUseCase orchestration from credential policy/idempotency through TRPMemberLogin, protected LOGIN AuthFlowSession creation, and CSRF hash binding.
- Only STARTED reservations call APIM or create sessions; other idempotency outcomes are side-effect free.
- AuthFlowSession attributes contain only the encrypted login-context envelope.
- Production constructors added merely for tests: none
- Fail-first evidence: target/0043-006-010-fail-first.log

### 0043-006-012-extract-create-auth-flow-session-csrf-interface

- Status: completed after corrective scripts through 0043-006-015-fix-login-start-architecture-test-for-csrf-interface
- Added narrow functional CreateAuthFlowSessionCsrfUseCase; AuthFlowSessionCsrfService implements it while retaining rotate and validate operations.
- Login-start production wiring and tests depend on the capability interface.
- Fail-first evidence: target/0043-006-012-fail-first.log

### 0043-006-014-fix-residual-concrete-csrf-dependency-check

- Status: completed after stale architecture-test correction in 0043-006-015-fix-login-start-architecture-test-for-csrf-interface
- Removed residual direct concrete CSRF dependencies from login-start production and test code.

### 0043-006-016-fix-login-clean-architecture-boundaries

- Status: completed after stale-path guard correction in 0043-006-017-fix-csrf-capability-config-path-guard
- Moved login Spring composition from application.usecase to config.auth.login.LoginApplicationConfig.
- Removed application-layer dependencies on Spring and inbound adapter configuration.
- Removed the login APIM transport dependency on the crypto implementation exception package.
- Renamed the private execute helper to authenticateAndEstablish.
- Fail-first evidence: target/0043-006-016-fail-first.log

### 0043-006-018-protected-login-start-replay-foundation

- Status: completed after test import correction in 0043-006-019-fix-login-replay-test-import
- Added sensitive LoginStartReplayPayload and infrastructure-neutral replay protection/storage ports.
- Added AES-256-GCM replay protection, opaque commitment references, and protected Redis replay storage.
- LoginIdempotencyReplaySnapshot remains safe metadata only.
- Spring composition remains outside the application layer.
- Fail-first evidence: target/0043-006-018-fail-first.log

### 0043-006-023-fix-replay-architecture-postcondition-scope

- Status: completed after replay-fixture correction in 0043-006-024-fix-completed-replay-commit-ref-fixture
- Replay transition architecture checks are scoped per guarded Java method.

### 0043-006-025-login-web-endpoint-cookie-and-response-mapping
- Status: completed
- Added POST /api/v1/auth/login with required Idempotency-Key handling plus X-Request-Id and Accept-Language propagation.
- Fresh success and valid completed replay write auth_flow_sid through the shared configured AuthFlowCookieWriter.
- Success returns SELECT_OTP_DESTINATION with csrfToken, opaque/masked destinations, expiresInSeconds, failCount, and actions=[].
- Response body excludes authFlowSessionId, replayCommitRef, password, raw destination, and ciphertext.
- Added explicit HTTP mappings for credential rejection, idempotency conflict, downstream unavailability, and unknown/session outcome.
- Controller remains thin and depends only on StartLoginUseCase, mapper, and cookie writer.
- Production constructors added merely for tests: none
- Fail-first evidence: target/0043-006-025-fail-first.log
- Commit message: feat(auth): expose login start endpoint and cookie contract
