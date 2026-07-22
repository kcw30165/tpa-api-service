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
