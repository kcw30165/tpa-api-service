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

### 0043-006-026-login-start-stabilization-and-evidence

- Status: completed after architecture-guard correction in 0043-006-028-fix-login-endpoint-architecture-guard-method
- Added mandatory X-Request-Id validation and response-header echo for POST /api/v1/auth/login.
- Added canonical err.request.id.missing targeting X-Request-Id.
- Added reactive failure containment mapped to err.workflow.system.unavailable without exception leakage.
- Verified all StartLoginStatus/API/HTTP mappings, fresh and replay cookie behavior, response security, clean architecture, APIM, and registration regressions.
- Evidence pack: target/0043-006-026-evidence.md

### 0043-006-027-fix-login-endpoint-request-id-architecture-guard

- Status: superseded and completed by 0043-006-028-fix-login-endpoint-architecture-guard-method
- Intended correction was valid, but the script expected one exact formatting of the negative AssertJ assertion and stopped before Maven.

### 0043-007-001-trp-get-account-list-application-contract

- Status: completed
- Restarted subtask 007 from the d954 reset baseline; discarded backup-branch 007-001/002 logic is not reused.
- Added the transport-neutral TRPGetAccountList command with cust-id, cust-type, search-key=cust-id, and search-value from protected pre-auth values.
- Added raw account-item contract for env, trust-code, policy-no, sch-type, integer cert-no, term-status, and term-complete-date.
- Account formatting, malformed-record omission, AccountRef generation, authenticated-session creation, and OTP-verification orchestration remain later scripts.
- Result exposes AVAILABLE or UNAVAILABLE only; no raw downstream error code crosses the application port.
- Production constructors added merely for tests: none
- Fail-first evidence: target/0043-007-001-fail-first.log
- Commit message: feat(auth): add TRPGetAccountList application contract

### 0043-007-002-trp-get-account-list-apim-adapter

- Status: completed
- Added the corrected TRPGetAccountList shared-envelope adapter using the existing APIM facade, certificate service, payload crypto, credential resolution, request-ID metadata, and SAFE_BUSINESS_CALL retry classification.
- Request JSON is exactly cust-id, cust-type, search-key, and search-value; response JSON maps env, trust-code, policy-no, sch-type, integer cert-no, term-status, and term-complete-date.
- Known downstream messages err.tpa.no.active.account and err.tpa.no.valid.login.ac are configuration-owned and map to the safe UNAVAILABLE application outcome.
- Empty success data, mixed error/data, unknown downstream messages, malformed envelopes, and transport/crypto/mapping failures return the safe unavailable outcome without exposing APIM details.
- Identifier formatting, term-pair business validation, malformed-record omission, and AccountRef generation remain 007-003/004.
- Production constructors added merely for tests: none
- Fail-first evidence: target/0043-007-002-fail-first.log
- Commit message: feat(auth): implement TRPGetAccountList shared APIM adapter

### 0043-007-003-account-record-validation-and-formatting

- Status: completed after YAML-anchor correction in 0043-007-004-fix-account-identifiers-yaml-anchor
- Added configurable policy width 11, certificate width 4, and numeric left-pad character under auth-workflows.login.account-identifiers.
- Added strict policy/certificate validation, left-zero formatting, nullable schemeType preservation, and strict termination-pair validation using dd/MM/yyyy input.
- Raw APIM records remain permissive so malformed records are omitted individually when valid records remain.
- Returned-but-zero-valid records produce ZERO_VALID_RECORDS, distinct from APIM UNAVAILABLE.
- Fail-first evidence: target/0043-007-003-fail-first.log

### 0043-007-005-session-scoped-account-ref-generation

- Status: completed
- Added LoginAccountRef and a dedicated LoginAccountRefGeneratorPort with a SecureRandom URL-safe implementation.
- Account references use configurable prefix acc, 24 bytes of entropy, and bounded collision retries; they are not derived from username, environment, trust, policy, or certificate data.
- Added one-to-one ordered assignment from validated accounts to LoginSessionAccount mappings, preserving raw and formatted identifiers for the later encrypted authenticated-session context.
- Assignment fails closed if uniqueness cannot be established and rejects an empty account list.
- No APIM, Redis, cookie, CSRF, pre-auth deletion, or authenticated-session persistence logic was added; those remain later session/OTP-verification orchestration work.
- Production constructors added merely for tests: none
- Fail-first evidence: target/0043-007-005-fail-first.log
- Subtask 007 non-fix implementation scripts completed: 007-001, 007-002, 007-003, 007-005.
- Commit message: feat(auth): generate session-scoped account references

### 0043-008-001-login-otp-send-application-contract

- Status: completed
- Added the login OTP-send input/result contract and SendLoginOtpUseCase boundary.
- Input requires auth-flow session, CSRF, opaque destination ID, idempotency key, origin/referer, Accept-Language, and request correlation; AccountRef is absent before authentication.
- Added protected-context-only destination resolution with no fallback to the first or sole destination. APIM mobile and public sms map to the shared SMS delivery channel.
- Extended existing shared OtpScenario, MessageDeliveryScenario, and MessageDeliveryType vocabularies with LOGIN/LOGIN_OTP; no parallel LoginOtpPort, OTP store, counter, status, or message-delivery infrastructure was introduced.
- OTP-send result deliberately excludes csrfToken, session ID, destination ID, raw destination, and OTP code.
- Session load/CSRF/origin/idempotency orchestration, challenge creation, delivery, replay, and web endpoint remain subsequent 008 scripts.
- Production constructors added merely for tests: none
- Fail-first evidence: target/0043-008-001-fail-first.log
- Commit message: feat(auth): add login OTP send application contract

### 0043-008-002-login-otp-send-orchestration

- Status: completed
- Added framework-neutral login OTP send orchestration using narrow protected-context loading and session-binding capabilities plus the existing OtpChallengePort and MessageDeliveryPort.
- Valid flow order is protected context/security validation, opaque destination resolution, shared LOGIN challenge creation, LOGIN_OTP delivery, then active-challenge binding.
- Raw OTP exists only between challenge creation and delivery template variables and is never returned or logged.
- Delivery failure invalidates the newly created challenge and does not bind it to the pre-auth session.
- Login resend remains unlimited at this stage by passing maxResendAttempts=0 when resend-limit-enabled=false; common challenge lifecycle remains authoritative.
- Concrete protected-context loader, session-binding adapter, origin/CSRF wiring, idempotency/replay, and HTTP endpoint remain subsequent scripts.
- Production constructors added merely for tests: none
- Fail-first evidence: target/0043-008-002-fail-first.log
- Commit message: feat(auth): orchestrate login OTP challenge and delivery

### 0043-008-003-login-protected-context-and-session-binding-adapters

- Status: completed after production sequencing correction in 0043-008-005-fix-login-otp-session-binding-sequencing
- LOGIN protected-context loading and active-challenge/status binding now pass targeted and cumulative gates.
- Status: completed
- Classification: production reactive sequencing defect plus one stale OTP-scenario characterization test.
- Root cause: LoginOtpSessionBindingAdapter constructed lifecycle.updateStatus eagerly inside Mono.then. Its lifecycle load occurred before updateActiveOtpChallenge committed, so the stale status mutation overwrote activeOtpChallengeId with null.
- Production correction: defer updateStatus with Mono.defer so the second lifecycle load occurs only after the challenge-binding update completes.
- Test correction: OtpChallengeModelTest now recognizes the intentional shared LOGIN scenario.
- No compatibility constructor or direct session repository access was added.
- Targeted command: mvn -Dtest=LoginOtpSessionBindingAdapterTest,DefaultLoginProtectedAuthFlowContextLoaderTest,Task0043LoginOtpSessionAdaptersArchitectureTest,LoginOtpSendServiceTest,OtpChallengeModelTest test
- OTP regression command: mvn -Dtest=$P32RuntimeRateLimitComponentGuardTest,MessageDeliveryModelTest,RateLimitDestinationHashArchitectureGuardTest,DefaultOtpChallengeServiceCreateInvalidateTest,DefaultOtpChallengeServiceReplaceTest,RegistrationOtpChallengeLifecycleIntegrationTest,RegistrationOtpResendServiceIntegrationTest,RegistrationOtpVerificationLifecycleIntegrationTest test
- Login regression command: mvn -Dtest=StartLoginServiceTest,LoginStartEvidenceIntegrationTest,TrpGetAccountListAdapterTest,LoginAccountRecordValidationServiceTest,LoginAccountReferenceAssignmentServiceTest test
- Registration regression command: mvn -DtestRegistrationContractStabilizationIntegrationTest,RegistrationRedisSensitiveDataSafetyIntegrationTest
- Commit message: feat(auth): bind login OTP to protected pre-auth session

### 0043-008-006-login-otp-send-idempotency-and-replay
- Status: completed
- Added transport-neutral OTP-send reservation, terminal transition, and safe completed-replay contracts.
- Request hash domain-separates LOGIN_OTP_SEND and hashes only session identity plus opaque destination ID; CSRF, raw destination, origin/referer, language, and provider data are excluded.
- Only STARTED invokes challenge/delivery orchestration. Completed replay returns masked destination and timing metadata without repeating side effects.
- Deterministic non-success outcomes mark failed; unexpected or indeterminate exceptions mark unknown.
- Raw OTP, destination, session ID, CSRF, and provider details are absent from replay snapshots and diagnostic strings.
- Concrete Redis adapter/wiring and HTTP endpoint remain subsequent scripts.
- Production constructors added merely for tests: none
- Fail-first evidence: target/0043-008-006-fail-first.log
- Commit message: feat(auth): add login OTP send idempotency and safe replay

### 0043-008-007-login-otp-send-redis-idempotency-adapter
- Status: completed
- Added atomic Redis SETNX reservation for login OTP send under the dedicated opaque namespace {prefix}:auth:idempotency:login:otp-send:{idempotencyKey}.
- Redis values contain request hash, lifecycle status, safe replay metadata, and timestamps only.
- Existing records classify duplicate processing, completed replay, key reuse, failed, and unknown outcomes without repeating side effects.
- TTL remains owned by auth-workflows.login.idempotency.otp-send-ttl-seconds and is validated by existing login policy binding.
- No session ID, destination ID/address, CSRF, OTP code, or provider response is stored in keys or wire values.
- HTTP endpoint and final SendLoginOtpUseCase composition remain the next script so no fabricated login message-template configuration is introduced here.
- Production constructors added merely for tests: none
- Fail-first evidence: target/0043-008-007-fail-first.log
- Commit message: feat(auth): persist login OTP send idempotency in Redis

### 0043-008-008-login-otp-send-endpoint-and-composition
- Status: completed after architecture-test correction in 0043-008-009-fix-login-otp-response-architecture-guard
- Added POST /api/v1/auth/login/otp/send and final Redis-idempotent SendLoginOtpUseCase composition.
- Added login-owned SMS/email OTP templates; registration templates are not reused.
- Fresh success and completed replay return VERIFY_OTP with masked destination and timing metadata only.
- Fail-first evidence: target/0043-008-008-fail-first.log
- Commit message: feat(auth): expose idempotent login OTP send endpoint

### 0043-008-010-login-otp-resend-application-contract-and-orchestration
- Status: completed
- Added login OTP resend input/result contracts, protected-context capability, replacement-binding capability, and framework-neutral orchestration.
- Resend requires a security-validated OTP_SENT login context with an active challenge; concrete loader/adapter wiring follows separately.
- Reuses OtpChallengePort.replaceChallenge and MessageDeliveryPort with LOGIN/LOGIN_OTP; no parallel OTP store or delivery stack was added.
- Cooldown, expiry, lock, and non-replaceable outcomes have no delivery or session-binding side effects.
- Successful replacement delivers the raw code once and binds the new challenge only after delivery acceptance.
- Existing handover content was preserved byte-for-byte and this section appended.
- Fail-first evidence: target/0043-008-010-fail-first.log
- Commit message: feat(auth): add login OTP resend application orchestration

### 0043-008-011-login-otp-resend-context-and-binding-adapters
- Status: completed
- Added concrete OTP_SENT LOGIN protected-context loading through existing lifecycle, CSRF/security validation, and LoginPreAuthContextProtectorPort.
- Resend requires a usable LOGIN session, OTP_SENT status, and an active OTP challenge.
- Destination is recovered only from the protected context and matched to the session's selected masked destination.
- Added replacement binding that verifies the expected old challenge before updating to the new challenge through the existing lifecycle.
- Session remains OTP_SENT; no direct Redis, duplicate crypto, or duplicate CSRF logic was introduced.
- Existing handover content was preserved byte-for-byte and this section appended.
- Fail-first evidence: target/0043-008-011-fail-first.log
- Commit message: feat(auth): validate and bind login OTP resend sessions

### 0043-008-014-align-login-otp-with-shared-and-registration-architecture
- Status: completed after resend correction in 0043-008-015-fix-login-otp-resend-null-safe-result-and-guard-path
- Removed the application/dto/auth/login/otp production Java tree.
- Login OTP send and resend workflow contracts now live beside their use cases, matching registration workflow ownership.
- Removed LoginOtpChannel and reused generic OtpChannel.
- Moved OTP-send idempotency records into the existing login idempotency family and extended the single LoginIdempotencyAction vocabulary with OTP_SEND, OTP_RESEND, and OTP_VERIFY.
- Added Task0043LoginOtpPackageOwnershipArchitectureTest to prevent the login OTP DTO tree from returning.
- Fail-first evidence: target/0043-008-014-fail-first.log
- Commit message: refactor(auth): align login OTP package ownership with registration

### 0043-008-016-login-otp-resend-idempotency-endpoint-and-composition
- Status: completed after UTF-8 and stale-output corrections through 0043-008-018-fix-stale-maven-output-after-resend-resume
- Completed POST /api/v1/auth/login/otp/resend with auth_flow_sid, X-XSRF-TOKEN, Idempotency-Key, X-Request-Id, Accept-Language, Origin, and Referer handling.
- Reused the existing Redis-backed login OTP idempotency lifecycle, generalized by LoginIdempotencyAction so OTP_SEND and OTP_RESEND use separate namespaces.
- Only a new OTP_RESEND reservation can replace, deliver, and bind a challenge; completed replay is side-effect free.
- Safe replay contains masked destination and timing metadata only; cooldown stays on VERIFY_OTP and emits Retry-After when available.
- No login OTP DTO tree, login-specific channel, duplicate OTP lifecycle, duplicate idempotency port, or second action enum was introduced.
- Fail-first evidence: target/evidence/0043-008-016-fail-first.log
- Commit message: feat(auth): complete idempotent login OTP resend endpoint

### 0043-008-017-fix-windows-python-encoding-and-complete-resend
- Status: completed after stale Maven output correction in 0043-008-018-fix-stale-maven-output-after-resend-resume
- Classified the CP950 UnicodeDecodeError as a script portability defect.
- Updated implicit pathlib text reads in 008-016 to use UTF-8 before resuming the cumulative implementation.
- Java production behavior was not changed by the encoding correction.
- Commit message: fix(auth): complete login OTP resend with UTF-8 script handling

### 0043-008-025-rebuild-maven-outputs-and-complete-login-otp-gates
- Status: completed
- Classification: stale/incomplete Maven compiler output; no Java source defect in MutationResponse or LoginOtpVerifyWebMapperTest.
- Evidence: MutationResponse.java existed in src/main/java, while testCompile reported that MutationResponse.class could not be accessed.
- Correction: removed target/classes, target/test-classes, generated source directories, maven-status, and Surefire output together; then rebuilt production and test classes with mvn -DskipTests test-compile.
- Verified MutationResponse.class, LoginOtpVerifyWebMapperTest.class, and Task0043AuthenticationFoundationArchitectureGuardTest.class before running tests.
- Ran login OTP verification/architecture, login OTP send/resend preservation, and registration OTP preservation gates.
- Production source changed by this script: no.
- Existing handover content was preserved byte-for-byte and this entry was appended only after all gates passed.
- Commit message: test(build): rebuild Maven outputs for login OTP gates

### 0043-008-026-login-cancellation-and-preauth-cleanup
- Status: completed after test-fixture correction in 0043-008-027-fix-login-cancellation-csrf-test-fixture
- Added POST /api/v1/auth/login/cancel with LOGIN scenario, CSRF, and origin/referer validation.
- Reused AuthFlowSessionPort, AuthFlowSessionCsrfService, AuthFlowSessionCancellationPort, and OtpChallengePort.
- Active OTP challenges are invalidated with USER_CANCELLED_LOGIN before pre-auth cancellation with USER_CANCELLED.
- Missing, already-cancelled, and already-terminal sessions return idempotent cleanup and clear auth_flow_sid plus XSRF cookies.
- Successful cleanup returns nextStep=RETURN_TO_LOGIN.
- No login-specific cancellation repository, OTP store, Redis access, or application/dto/auth/login/otp tree was introduced.
- Fail-first evidence: target/0043-008-026-fail-first.log
- Commit message: feat(auth): cancel login pre-auth flow and clean up OTP

### 0043-008-028-align-login-otp-web-package-with-registration
- Status: completed after residual-import correction in 0043-008-029-fix-login-otp-web-migration-residual-imports
- Removed adapter/in/web/auth/login/otp and aligned login web ownership with registration's dto and mapper package structure.
- Moved login OTP request/response records to adapter/in/web/auth/login/dto and web mappers to adapter/in/web/auth/login/mapper.
- No generic OTP web DTO family was added; OtpChallengePort and application.dto.auth.otp remain the generic technical OTP core.
- Added a durable architecture guard preventing the login/otp inbound web package from returning.
- Fail-first evidence: target/0043-008-028-fail-first.log
- Commit message: refactor(auth): align login OTP web package ownership

### 0043-008-030-login-otp-success-account-continuation
- Status: completed after fixture and account-contract corrections through 0043-008-032-fix-stale-permissive-account-record-contract-test
- After generic OTP verification succeeds, the flow marks the LOGIN pre-auth session OTP_VERIFIED and uses protected normalizedUsername/custType to call the existing GetLoginAccountsUseCase.
- Reused LoginAccountRecordValidationService and LoginAccountReferenceAssignmentService; malformed records are omitted and APIM order is preserved.
- Distinct outcomes are ACCOUNTS_READY, ACCOUNT_LIST_UNAVAILABLE, and ZERO_VALID_ACCOUNTS.
- Successful public response returns nextStep=SELECT_ACCOUNT and exposes only opaque accountRef values.
- Authenticated-session creation, portal cookie transition, and pre-auth deletion remain deferred to subtasks 009-011.
- Fail-first evidence: target/0043-008-030-fail-first.log
- Commit message: feat(auth): continue verified login into account retrieval

### 0043-008-031-fix-stale-login-otp-valid-account-fixture
- Status: completed through 0043-008-032-fix-stale-permissive-account-record-contract-test
- Corrected the valid OTP fixture to return one valid account and assert the generated opaque AccountRef.
- Production behavior changed: no.
- Commit message: test(auth): align valid OTP fixture with account continuation

### 0043-008-033-final-login-otp-stabilization-and-evidence
- Status: completed
- Finalized subtask 008 Login OTP lifecycle evidence across send, resend, verification failure paths, verification-attempt exhaustion, RESTART_FLOW, cancellation, successful verification, account retrieval, account validation, and opaque AccountRef assignment.
- Verified public successful verification returns SELECT_ACCOUNT and exposes AccountRef only.
- Verified OtpChallengePort remains the shared technical OTP core and that no login-specific OTP DTO tree, inbound otp package, OTP store, or channel vocabulary exists.
- Verified authenticated-session runtime implementation remains deferred to subtask 009.
- Ran clean-architecture, rate-limit/destination-hash, account-list, APIM adapter, sensitive-data, and registration-preservation gates.
- Evidence index: target/0043-008-033-evidence/evidence.md
- Evidence logs: target/0043-008-033-evidence/01-login-otp-send.log through target/0043-008-033-evidence/07-registration-preservation.log
- Existing handover content was preserved byte-for-byte and this section appended only after all gates passed.
- Subtask 008 status: complete.
- Next implementation: 0043-009-001-authenticated-session-domain-model.sh
- Commit message: test(auth): finalize login OTP lifecycle evidence

### 0043-008-036-fix-final-evidence-run-gate-local-binding
- Status: completed
- Classification: final-evidence shell variable-binding defect; no production-code defect.
- Root cause: with set -u enabled, run_gate declared name, tests, and log in one local command and expanded ${name} before Bash had assigned name, producing an unbound-variable failure.
- Correction: run_gate now binds name, tests, and log in three sequential local declarations.
- The script also preserves the canonical LoginOtpSendService and consolidated protected-context/session-binding evidence class names.
- The corrected 008-033 evidence script completed all seven gates and generated target/0043-008-033-evidence/evidence.md.
- Existing handover content was preserved byte-for-byte; the 008-033 completion remains intact and this correction was appended afterward.
- Production source changed: no.
- Commit message: test(auth): fix final OTP evidence gate binding

### 0043-009-001-authenticated-session-domain-model
- Status: completed
- Added a framework-neutral authenticated-session domain model distinct from pre-auth AuthFlowSession.
- AuthenticatedSession contains AuthenticatedSessionId, the existing ActorContext, ordered account mappings, ACTIVE/LOGGED_OUT/REVOKED/EXPIRED status, CSRF hash, created/last-accessed timestamps, and idle/absolute expiry.
- ActorContext remains actorUserId plus actorUserRole only; actorType was not introduced.
- AuthenticatedSessionAccount contains AccountRef, accountEnv, policyNo, certNo, trustCode, schemeType, termStatus, and termCompletionDate.
- Session/account identifiers and diagnostic strings are redacted; duplicate AccountRefs and invalid termination pairs fail closed.
- No Redis adapter, persistence port, crypto, Spring wiring, cookies, or PortalAccessContext resolution was introduced; those remain 009-002 and later subtasks.
- Updated the old P4B placeholder assertion while retaining the absence of AuthenticatedSessionPort and Redis adapter.
- Existing handover content was preserved byte-for-byte and this section appended after all gates passed.
- Fail-first evidence: target/0043-009-001-fail-first.log
- Commit message: eat(auth): add authenticated session domain model


### 0043-009-002-authenticated-session-persistence-contract-and-protected-wire-model
- Status: completed after test corrections through 0043-009-004-fix-protected-session-redaction-assertion
- Added framework-neutral AuthenticatedSessionPort operations for store, load, touch, revoke, and delete.
- Added opaque, redacted persistence commands with positive TTL and explicit timestamps/correlation IDs.
- Added ProtectedAuthenticatedSession as a versioned AEAD wire envelope containing ciphertext, nonce, and authentication tag only.
- Added AuthenticatedSessionProtectorPort for domain-to-protected-wire conversion without coupling the application layer to a crypto implementation.
- No Redis adapter, Spring configuration, ObjectMapper contract, cookie handling, PortalAccessContext resolution, or authenticated-session lifecycle orchestration was introduced.
- Fail-first evidence: target/0043-009-002-fail-first.log
- Commit message: feat(auth): add authenticated session persistence contract

### 0043-009-003-fix-protected-session-test-port-import
- Status: completed through 0043-009-004-fix-protected-session-redaction-assertion
- Added the missing AuthenticatedSessionProtectorPort import to ProtectedAuthenticatedSessionTest.
- Production behavior changed: no.
- Commit message: test(auth): import authenticated session protector port

### 0043-009-004-fix-protected-session-redaction-assertion
- Status: completed
- Classification: stale redaction assertion; production ProtectedAuthenticatedSession.toString() was already correct.
- Root cause: the test rejected the safe field labels ciphertext and nonce even though their values were rendered as <redacted>.
- Correction: the test now requires all protected-wire labels to be present with <redacted> and verifies distinct secret fixture values are absent.
- Existing handover content was preserved byte-for-byte; missing 009-002/003 records and this correction were appended only after all gates passed.
- Commit message: test(auth): assert protected session values are redacted

### 0043-009-005-redis-authenticated-session-adapter
- Status: completed after corrective stabilization through 0043-009-007-fix-stale-redis-adapter-deferred-architecture-test
- Added RedisAuthenticatedSessionAdapter behind AuthenticatedSessionPort using the existing CachePort and RedisCacheKeyFactory infrastructure.
- Keys use {prefix}:auth:authenticated-session:{opaque sid}; values contain only the serialized ProtectedAuthenticatedSession envelope.
- Store and touch use caller-calculated positive TTL; load returns the protected envelope; revoke/delete remove the opaque session key idempotently.
- Added a dedicated protected-envelope JSON mapper; domain actor/account/CSRF fields never appear in the Redis adapter contract.
- Reuses the project's Sentinel-compatible Redis infrastructure through CachePort; no application-layer Redis dependency or separate Redis client was introduced.
- Spring wiring, AES-GCM protector implementation, lifecycle orchestration, cookies, and PortalAccessContext resolution remain later scripts.
- Fail-first evidence: target/0043-009-005-fail-first.log
- Commit message: feat(auth): persist protected authenticated sessions in Redis

### 0043-009-006-fix-redis-authenticated-session-cache-test-double
- Status: completed through 0043-009-007-fix-stale-redis-adapter-deferred-architecture-test
- Corrected RedisAuthenticatedSessionAdapterTest.MemoryCache to implement CachePort.evict as Mono<Boolean>.
- Production behavior changed: no.
- Commit message: test(auth): align authenticated session cache test double

### 0043-009-007-fix-stale-redis-adapter-deferred-architecture-test
- Status: completed
- Classification: stale stage-specific architecture assertion; production Redis adapter behavior was unchanged.
- Root cause: the 009-002 architecture test correctly required the Redis adapter to be absent at the contract-only stage, but 009-005 intentionally introduced that adapter and did not advance the older assertion.
- Correction: the test now requires RedisAuthenticatedSessionAdapter and RedisAuthenticatedSessionJsonMapper to exist while continuing to require AuthenticatedSessionConfig to remain deferred.
- Existing handover content was preserved byte-for-byte; missing 009-005/006 records and this correction were appended only after all gates passed.
- Commit message: test(auth): align authenticated session architecture stage

### 0043-009-008-aes-gcm-authenticated-session-protector
- Status: completed after explicit temporal-codec correction in 0043-009-010-fix-authenticated-session-explicit-time-codec
- Added infrastructure-owned AES-256-GCM implementation of AuthenticatedSessionProtectorPort with fresh nonce, authentication tag, and version-bound AAD.
- Commit message: feat(auth): protect authenticated session context with AES-GCM

### 0043-009-009-fix-aes-gcm-throwable-assertion-compatibility
- Status: completed through 0043-009-010-fix-authenticated-session-explicit-time-codec
- Corrected AssertJ throwable assertions to use one hasMessageNotContaining value per invocation.
- Production behavior changed: no.
- Commit message: test(auth): align AES-GCM throwable assertions with AssertJ API

### 0043-009-010-fix-authenticated-session-explicit-time-codec
- Status: completed
- Classification: production serialization defect in the new authenticated-session protector.
- Root cause: the protector serialized AuthenticatedSession directly, making Instant and LocalDate handling depend on a Jackson Java-time module that is not available in the project runtime mapper.
- Correction: added adapter-private WireSession and WireAccount records that encode all Instant and LocalDate values explicitly as ISO-8601 strings and reconstruct the domain model after authenticated decryption.
- AES-GCM key, nonce, AAD, authentication-tag, redaction, tamper detection, and Redis protected-envelope boundaries remain unchanged.
- Existing handover content was preserved byte-for-byte; missing 009-008/009 records and this correction were appended only after all gates passed.
- Commit message: fix(auth): encode authenticated session temporal fields explicitly

### 0043-009-011-authenticated-session-protection-and-redis-composition
- Status: completed
- Added Spring composition for AuthenticatedSessionPort, RedisAuthenticatedSessionJsonMapper, and AuthenticatedSessionProtectorPort outside the application layer.
- Added validated auth-workflows.login.session.protection properties requiring a version and dedicated Base64-encoded 256-bit key.
- AesGcmAuthenticatedSessionProtector receives a copied application ObjectMapper; Redis persistence continues through the existing Sentinel-compatible CachePort and RedisCacheKeyFactory.
- No second Redis client, Redis connection factory, hardcoded key, registration-secret fallback, cookie handling, lifecycle orchestration, or PortalAccessContext resolution was introduced.
- Deployment must supply AUTH_WORKFLOWS_LOGIN_SESSION_PROTECTION_BASE64_KEY or equivalent externalized configuration.
- Existing handover content was preserved byte-for-byte and this section appended after all gates passed.
- Fail-first evidence: target/0043-009-011-fail-first.log
- Commit message: feat(auth): compose protected authenticated session persistence

### 0043-009-012-authenticated-session-lifecycle-service
- Status: completed
- Added framework-neutral authenticated-session create, load-and-touch, expiry cleanup, and revoke orchestration.
- Added a dedicated SecureAuthenticatedSessionIdGenerator producing opaque sid_ values from 24 random bytes.
- Create protects the complete domain model and stores it with the earlier of idle and absolute expiry.
- Load-and-touch decrypts, rejects missing/revoked/expired sessions, deletes expired state, and refreshes idle expiry without extending absolute expiry.
- No web filter, cookie/header transport, PortalAccessContext resolution, or successful-login transition was introduced; account resolution remains next.
- Existing handover content was preserved byte-for-byte and this section appended after all gates passed.
- Fail-first evidence: target/0043-009-012-fail-first.log
- Commit message: feat(auth): add authenticated session lifecycle service

### 0043-009-013-authenticated-account-context-resolution
- Status: completed after architecture stabilization through 0043-009-016-fix-lifecycle-stage-after-account-context-resolution
- Added authenticated-session-backed PortalAccessContextPort resolution using selected AccountRef plus authenticated session ID.
- Resolution loads and touches the active session, matches AccountRef only inside that session, and maps the existing ActorContext plus selected account into PortalAccessContext.
- Missing, malformed, inactive, expired, revoked, and cross-session or unknown AccountRef inputs fail closed through one non-sensitive error.
- Web transport and successful-login transition remain deferred.
- Fail-first evidence: target/0043-009-013-fail-first.log
- Commit message: feat(auth): resolve portal context from authenticated session

### 0043-009-014-fix-account-context-architecture-test-throws
- Status: completed through 0043-009-016-fix-lifecycle-stage-after-account-context-resolution
- Added the missing checked-exception declaration to the account-context source-reading architecture test.
- Production behavior changed: no.
- Commit message: test(auth): declare account context architecture IO failure

### 0043-009-015-fix-account-context-architecture-semantic-assertions
- Status: completed through 0043-009-016-fix-lifecycle-stage-after-account-context-resolution
- Replaced formatting-sensitive account-context source assertions with whitespace-normalized semantic checks and verified the error message in its owning exception class.
- Production behavior changed: no.
- Commit message: test(auth): make account context architecture assertions semantic

### 0043-009-016-fix-lifecycle-stage-after-account-context-resolution
- Status: completed
- Classification: stale stage-specific lifecycle architecture assertion; production session lifecycle and account-context behavior were unchanged.
- Root cause: the 009-012 lifecycle test correctly required AuthenticatedAccountContextResolver to be absent before account resolution, but 009-013 intentionally introduced that resolver.
- Correction: the lifecycle guard now requires AuthenticatedAccountContextResolver to exist while continuing to require AuthenticatedSessionCookieWriter and web transport to remain deferred.
- Existing handover content was preserved byte-for-byte; missing 009-013/014/015 records and this correction were appended only after all gates passed.
- Commit message: test(auth): advance authenticated session lifecycle stage guard

### 0043-009-017-final-authenticated-session-foundation-stabilization-and-evidence
- Status: completed
- Finalized subtask 009 authenticated-session foundation evidence across domain invariants, protected persistence contracts, Redis adapter behavior, AES-256-GCM protection, Spring composition, idle/absolute lifecycle enforcement, opaque sid generation, and selected AccountRef resolution into PortalAccessContext.
- Verified ActorContext remains actorUserId plus actorUserRole only; actorType was not introduced.
- Verified Redis values remain protected envelopes, AccountRef resolution is session-scoped, and missing/expired/revoked/unknown context fails closed.
- Verified Login OTP continuation, account validation, AccountRef assignment, registration OTP, and Redis sensitive-data behavior remain preserved.
- Login-success transition, pre-auth cleanup, sid_xxx web transport, and runtime replacement of the temporary portal-context adapter remain deferred to subsequent subtasks.
- Evidence index: target/0043-009-017-evidence/evidence.md
- Evidence logs: target/0043-009-017-evidence/01-domain-model.log through target/0043-009-017-evidence/07-login-registration-preservation.log
- Existing handover content was preserved byte-for-byte and this section appended only after all gates passed.
- Subtask 009 status: complete.
- Commit message: test(auth): finalize authenticated session foundation evidence

### 0043-009-018-fix-final-evidence-actortype-guard-scope
- Status: completed
- Classification: final-evidence static-guard scope defect; no production-code defect.
- Root cause: 009-017 searched raw text for actorType and matched package-info.java, whose documentation intentionally states that the authenticated-session model contains no actorType field.
- Correction: the guard now excludes package-info.java and detects structural actorType fields, accessors, parameters, assignments, and actor-type configuration keys in authenticated-session production Java sources.
- The corrected 009-017 evidence script completed all seven evidence gates and generated target/0043-009-017-evidence/evidence.md.
- Existing handover content was preserved byte-for-byte; the 009-017 completion remains intact and this correction was appended afterward.
- Production source changed: no.
- Commit message: test(auth): scope authenticated session actorType guard

### 0043-010-001-login-authenticated-session-transition-contract
- Status: completed
- Added framework-neutral EstablishAuthenticatedLoginSessionUseCase command/result contract for the post-OTP transition.
- The command carries the verified pre-auth session identity, existing ActorContext, ordered LoginSessionAccount mappings, CSRF hash, and correlation ID; all diagnostics redact actor, account, session, and CSRF values.
- Added LoginAuthenticatedSessionAccountMapper that preserves opaque AccountRef plus validated/formatted session fields and deliberately discards raw policy/certificate identifiers.
- Success returns only the opaque authenticated session ID plus auth-flow/XSRF cleanup flags; failure results cannot carry a session ID.
- Authenticated-session creation ordering, pre-auth deletion, OTP verification integration, cookie/header transport, and idempotent transition handling remain subsequent 010 scripts.
- Existing handover content was preserved byte-for-byte and this section appended only after all gates passed.
- Fail-first evidence: target/0043-010-001-fail-first.log
- Commit message: feat(auth): add login authenticated session transition contract

### 0043-010-002-login-authenticated-session-transition-orchestration
- Status: completed after architecture-test stabilization through 0043-010-004-fix-transition-architecture-port-name-collision
- Added safe transition orchestration behind EstablishAuthenticatedLoginSessionUseCase using narrow create, pre-auth delete, and authenticated-session revoke capabilities.
- Ordering is create protected authenticated session first, then delete the verified pre-auth session; creation failure preserves pre-auth state.
- If pre-auth cleanup fails after creation, the new authenticated session is revoked and no session ID or cookie-cleanup instruction is exposed.
- LoginSessionAccount mappings are converted through LoginAuthenticatedSessionAccountMapper before persistence, so raw policy and certificate identifiers are not retained.
- Fail-first evidence: target/0043-010-002-fail-first.log
- Commit message: feat(auth): establish authenticated login session safely

### 0043-010-003-fix-transition-orchestration-architecture-test-throws
- Status: completed through 0043-010-004-fix-transition-architecture-port-name-collision
- Added the missing checked-exception declaration to the transition source-reading architecture test.
- Production behavior changed: no.
- Commit message: test(auth): declare transition architecture IO failure

### 0043-010-004-fix-transition-architecture-port-name-collision
- Status: completed
- Classification: architecture-test substring collision; production transition orchestration was unchanged.
- Root cause: the test prohibited the raw text AuthFlowSessionPort, which also appears as the suffix of the intended narrow capability DeleteAuthFlowSessionPort.
- Correction: the guard now rejects actual broad-port imports, fields, and constructor dependencies while explicitly allowing DeleteAuthFlowSessionPort.
- Existing handover content was preserved byte-for-byte; missing 010-002/003 records and this correction were appended only after all gates passed.
- Commit message: test(auth): distinguish narrow pre-auth cleanup capability

### 0043-010-005-login-authenticated-session-transition-composition
- Status: completed after focused policy-validation correction in 0043-010-006-fix-session-composition-policy-validation-scope
- Composed SecureAuthenticatedSessionIdGenerator and AuthenticatedSessionLifecycleService using the existing login session idle timeout, absolute TTL, Clock, protected persistence port, and protector.
- Adapted AuthFlowSessionPort to the narrow DeleteAuthFlowSessionPort capability only at the Spring composition boundary.
- Composed EstablishAuthenticatedLoginSessionUseCase with the lifecycle service as the narrow create and revoke implementation and the pre-auth delete adapter as cleanup.
- Reused the existing Sentinel-compatible Redis persistence and AES-GCM protection composition.
- Fail-first evidence: target/0043-010-005-fail-first.log
- Commit message: feat(auth): compose login authenticated session transition

### 0043-010-006-fix-session-composition-policy-validation-scope
- Status: completed
- Classification: production composition validation-scope defect.
- Root cause: authenticatedSessionLifecycleService called validateLoginPolicy(), coupling session bean creation to unrelated credential, OTP, idempotency, template, and account settings. The focused composition test therefore failed on username-min-length before session durations could be composed.
- Correction: validate only the configuration owned by this bean: login presence, positive idle timeout, positive absolute TTL, and idle timeout not exceeding absolute TTL.
- Added a regression test proving unrelated login-policy fields are not required and invalid session-duration relationships still fail fast.
- Existing handover content was preserved byte-for-byte; the missing 010-005 completion and this correction were appended only after all gates passed.
- Commit message: fix(auth): scope authenticated session policy validation

### 0043-010-007-login-protected-context-csrf-hash-handoff
- Status: completed after corrective stabilization through 0043-010-009-fix-csrf-hash-architecture-variable-assertion
- Extended validated protected-login context results with the persisted CSRF-token hash needed for authenticated-session creation.
- The loader exposes the hash only after CSRF, session, workflow-state, and encrypted-context validation succeeds; invalid results carry no hash and diagnostics redact it.
- Fail-first evidence: target/0043-010-007-fail-first.log
- Commit message: feat(auth): hand off verified login CSRF hash

### 0043-010-008-fix-login-csrf-hash-loader-variable-anchor
- Status: completed through 0043-010-009-fix-csrf-hash-architecture-variable-assertion
- Corrected the production loader handoff to use s.csrfTokenHash() from the validated loaded AuthFlowSession.
- Commit message: fix(auth): bind persisted login CSRF hash from loaded session

### 0043-010-009-fix-csrf-hash-architecture-variable-assertion
- Status: completed
- Classification: stale architecture-test variable-name assertion; production CSRF hash handoff was already correct.
- Root cause: the test expected session.csrfTokenHash(), while DefaultLoginProtectedAuthFlowContextLoader names the validated loaded AuthFlowSession variable s.
- Correction: normalize source whitespace and assert the complete valid-result factory call using s.csrfTokenHash(), thereby proving the hash comes from the validated loaded session rather than the submitted raw token.
- Existing handover content was preserved byte-for-byte; missing 010-007/008 records and this correction were appended only after all gates passed.
- Production source changed: no.
- Commit message: test(auth): align CSRF hash handoff architecture assertion

### 0043-010-010-login-otp-success-authenticated-session-transition
- Status: completed after test-contract correction in 0043-010-011-fix-login-otp-session-transition-test-contracts
- Integrated successful Login OTP verification and validated account assignment with EstablishAuthenticatedLoginSessionUseCase.
- ActorContext is created from validated protected login context using normalizedUsername and userRole only; no actorType is introduced.
- The transition receives the verified AuthFlowSessionId, assigned LoginSessionAccount list, persisted CSRF hash, and correlation ID.
- ACCOUNTS_READY requires and internally carries an opaque authenticatedSessionId for the following web-transport step; the public mapper continues to expose only AccountRef values.
- Transition failure returns SESSION_CREATION_FAILED without accounts, session ID, or cleanup instruction.
- Fail-first evidence: target/0043-010-010-fail-first.log
- Commit message: feat(auth): establish session after successful login OTP

### 0043-010-011-fix-login-otp-session-transition-test-contracts
- Status: completed
- Classification: test-fixture and cumulative constructor-migration defects; authenticated-session transition production orchestration remains authoritative.
- Corrected the transition fixture to use OtpVerificationOutcome and the canonical four-component VerifyOtpChallengeResult.
- Constructed GetLoginAccountsUseCase through its LoginAccountAuthorizationPort dependency instead of treating the concrete use case as a functional interface.
- Used the actual LoginAccountRecordValidationService width/pad constructor and LoginAccountReferenceAssignmentService generator/max-attempts constructor.
- Migrated every direct LoginOtpVerifyService test caller to the canonical transition dependency without adding a production compatibility constructor.
- Existing handover content was preserved byte-for-byte; the missing 010-010 completion and this correction were appended only after all gates passed.
- Production source changed: no.
- Commit message: test(auth): align login OTP transition fixtures with canonical contracts

### 0043-010-012-sid-xxx-authenticated-transport-and-cookie-transition
- Status: completed after test-API correction in 0043-010-013-fix-authenticated-transport-httpheaders-assertion
- Added the canonical authenticated-session response transport header sid_xxx through RequestHeaderContextKeys.AUTHENTICATED_SESSION_HEADER.
- POST /api/v1/auth/login/otp/verify publishes sid_xxx only for ACCOUNTS_READY with an authenticated session ID.
- Successful transition clears auth_flow_sid and the pre-auth XSRF cookie; failure paths never publish sid_xxx.
- The authenticated session ID remains absent from the JSON response body; the account-selection payload continues to expose AccountRef only.
- Fail-first evidence: target/0043-010-012-sid-xxx-authenticated-transport-and-cookie-transition-fail-first.log
- Commit message: feat(auth): publish authenticated sid transport after OTP verification

### 0043-010-013-fix-authenticated-transport-httpheaders-assertion
- Status: completed
- Classification: test-compilation defect; production sid_xxx transport and cookie transition were unchanged.
- Root cause: Spring HttpHeaders in the project version does not expose Map.containsKey(String).
- Correction: the negative transport assertion now uses getFirst(AUTHENTICATED_SESSION_HEADER).isNull(), which directly verifies that failure responses do not publish sid_xxx.
- Existing handover content was preserved byte-for-byte; the missing 010-012 completion and this correction were appended only after all gates passed.
- Production source changed: no.
- Commit message: test(auth): align sid transport assertion with HttpHeaders API

### 0043-010-014-final-login-authenticated-transition-stabilization-and-evidence
- Status: completed
- Finalized subtask 010 evidence for the verified-OTP to authenticated-session transition.
- Verified AccountRef assignment precedes authenticated-session establishment, authenticated-session creation precedes pre-auth deletion, and cleanup failure revokes the new authenticated session.
- Verified successful OTP verification publishes the opaque authenticated session through sid_xxx, clears auth_flow_sid and the pre-auth XSRF cookie, and keeps the session ID out of the JSON body and diagnostics.
- Verified failure paths never publish sid_xxx and do not expose accounts or authenticated-session identity.
- Verified protected Redis persistence, idle/absolute session lifecycle, session-scoped AccountRef resolution, Login OTP preservation, registration preservation, and clean-architecture boundaries.
- Evidence index: target/0043-010-014-evidence/evidence.md
- Evidence logs: target/0043-010-014-evidence/01-transition-contract-and-orchestration.log through target/0043-010-014-evidence/07-registration-and-architecture-preservation.log
- Existing handover content was preserved byte-for-byte and this section appended only after all gates passed.
- Subtask 010 status: complete.
- Deferred: authenticated request filtering, runtime replacement of temporary PortalAccessContext, logout/revocation transport, and removal of legacy Session-Id belong to later subtasks.
- Commit message: test(auth): finalize authenticated login transition evidence

### 0043-010-016-fix-final-evidence-transition-capability-names
- Status: completed
- Classification: final-evidence variable-name defect; production transition orchestration was unchanged.
- Corrected static checks to match the concrete service fields: create.create, cleanup.delete, and revoke.revoke.
- Verified create-before-cleanup ordering and cleanup-failure revocation through the existing behavioral and architecture tests.
- Evidence index: target/0043-010-014-evidence/evidence.md
- Production source changed: no.
- Commit message: test(auth): align transition evidence with concrete capability fields

### 0043-011-001-authenticated-request-context-filter
- Status: completed after cumulative-test stabilization through 0043-011-003-fix-authenticated-filter-cumulative-tests
- Added Account-Ref plus sid_xxx authenticated request-context filtering through the two-argument PortalAccessContextPort boundary.
- Requests with Account-Ref but without sid_xxx fail closed; unscoped requests without Account-Ref pass through.
- Fail-first evidence: target/0043-011-001-authenticated-request-context-filter-fail-first.log
- Commit message: feat(auth): resolve authenticated request context from sid transport

### 0043-011-002-fix-authenticated-filter-attribute-assertion
- Status: completed through 0043-011-003-fix-authenticated-filter-cumulative-tests
- Disambiguated the generic exchange attribute assertion with an explicit PortalAccessContext cast.
- Production source changed: no.
- Commit message: test(auth): disambiguate authenticated context attribute assertion

### 0043-011-003-fix-authenticated-filter-cumulative-tests
- Status: completed
- Classification: stale cumulative filter fixtures plus formatting-sensitive architecture assertion; production filter behavior was unchanged.
- Added sid_xxx to existing account-scoped filter requests so they exercise the authenticated two-argument contract.
- Normalized whitespace before asserting the two-argument resolver invocation.
- No legacy fallback to Session-Id or one-argument resolution was added.
- Production source changed: no.
- Commit message: test(auth): align portal filter tests with sid authenticated context

### 0043-011-004-runtime-authenticated-portal-context-composition
- Status: completed
- Added a primary PortalAccessContextPort bean backed by AuthenticatedAccountContextResolver and the existing LoadAndTouchAuthenticatedSessionUseCase lifecycle boundary.
- Runtime selected-account resolution now uses protected Redis authenticated-session state, enforces session status/expiry, refreshes idle access, and restricts AccountRef lookup to the loaded session.
- PortalAccessContextWebFilter retains the project-standard Lombok @RequiredArgsConstructor style and continues to depend only on PortalAccessContextPort.
- TemporaryPortalAccessContextAdapter remains present only for the following isolation/removal stage; @Primary makes authenticated resolution authoritative for ordinary injection in this stage.
- No Redis, crypto, or lifecycle implementation dependency was introduced into the inbound web filter.
- Existing handover content was preserved byte-for-byte and this section appended only after all gates passed.
- Fail-first evidence: target/0043-011-004-runtime-authenticated-portal-context-composition-fail-first.log
- Commit message: feat(auth): compose authenticated portal context resolution

### 0043-011-005-isolate-temporary-context-and-remove-legacy-session-header
- Status: completed after guard stabilization through 0043-011-007-fix-legacy-header-reference-guard-migration
- Removed the retired Session-Id constant/wire vocabulary and removed TemporaryPortalAccessContextAdapter from component scanning.
- sid_xxx and AuthenticatedAccountContextResolver are authoritative at runtime.
- Commit message: refactor(auth): isolate temporary context and remove legacy session header

### 0043-011-006-fix-legacy-session-header-guard-name-collision
- Status: completed through 0043-011-007-fix-legacy-header-reference-guard-migration
- Scoped the constant guard to the exact retired declaration rather than a suffix shared by the canonical constant.
- Production source changed: no.
- Commit message: test(auth): distinguish canonical sid header from legacy constant

### 0043-011-007-fix-legacy-header-reference-guard-migration
- Status: completed
- Classification: test migration overreach; production legacy-header removal was unchanged.
- Root cause: 011-005 globally replaced RequestHeaderContextKeys.SESSION_ID_HEADER in tests, including the architecture guard itself, causing the guard to prohibit the required canonical AUTHENTICATED_SESSION_HEADER.
- Correction: restored the exact retired-reference check using a split string literal, preventing future bulk migrations from rewriting the guard target.
- Production source changed: no.
- Commit message: test(auth): restore exact legacy header reference guard

### 0043-011-008-authenticated-request-failure-http-contract
- Status: completed after cumulative stabilization through 0043-011-010-fix-authenticated-filter-typed-failure-expectation
- Added a typed authenticated portal-context failure shared by the request filter and session-backed resolver.
- Added pre-controller WebExceptionHandler mapping to HTTP 401 and err.security.authentication.required without exposing session/account details.
- Fail-first evidence: target/0043-011-008-authenticated-request-failure-http-contract-fail-first.log
- Commit message: feat(auth): map authenticated context failures to HTTP contract

### 0043-011-009-fix-authenticated-failure-mutation-factory-arity
- Status: completed through 0043-011-010-fix-authenticated-filter-typed-failure-expectation
- Corrected the handler to use MutationResponse.failure(status, result, errors).
- Commit message: fix(auth): use canonical mutation failure factory

### 0043-011-010-fix-authenticated-filter-typed-failure-expectation
- Status: completed
- Classification: stale cumulative test expectation; production typed failure and HTTP contract were unchanged.
- Root cause: the authenticated filter test still expected the temporary IllegalStateException after 011-008 deliberately standardized filter and resolver failures on AuthenticatedPortalContextResolutionException.
- Correction: the missing-sid_xxx test now expects the typed authenticated-context exception handled by AuthenticatedPortalContextWebExceptionHandler.
- Existing handover content was preserved byte-for-byte; missing 011-008/009 records and this correction were appended only after all gates passed.
- Production source changed: no.
- Commit message: test(auth): expect typed authenticated context failure

### 0043-011-011-final-authenticated-request-context-stabilization-and-evidence
- Status: completed
- Finalized subtask 011 evidence for authenticated Account-Ref plus sid_xxx request-context resolution.
- Verified filter fail-closed behavior, protected-session load/touch, session-scoped AccountRef lookup, exchange/Reactor context propagation, and generic HTTP 401 handling.
- Verified AuthenticatedAccountContextResolver is the primary runtime PortalAccessContextPort implementation and TemporaryPortalAccessContextAdapter is isolated from component scanning.
- Verified legacy Session-Id is absent and sid_xxx is the sole authenticated-session transport.
- Verified failure responses do not disclose session identity, account existence, AccountRef, policy/certificate identifiers, or internal exception messages.
- Verified login transition, protected session lifecycle, personal-information context use, registration preservation, Redis safety, and architecture boundaries.
- Evidence index: target/0043-011-011-evidence/evidence.md
- Evidence logs: target/0043-011-011-evidence/01-authenticated-request-filter.log through target/0043-011-011-evidence/07-page-and-registration-preservation.log
- Existing handover content was preserved byte-for-byte and this section appended only after all gates passed.
- Subtask 011 status: complete.
- Deferred: logout/revocation endpoint and authenticated-session termination transport belong to the next phase.
- Commit message: test(auth): finalize authenticated request context evidence

### 0043-012-001-authenticated-logout-usecase-contract
- Status: completed
- Defined a framework-neutral AuthenticatedLogoutUseCase with redacted command and terminal-only result contracts.
- Public outcomes are LOGGED_OUT, ALREADY_LOGGED_OUT, and FAILED; successful repeat logout remains representable without exposing session existence.
- AuthenticatedLogoutResult carries no session ID, actor, account, Redis, cookie, or transport details.
- The existing RevokeAuthenticatedSessionUseCase remains the lower-level lifecycle capability to be orchestrated in the next main script.
- Main-script progress: 1/5; four main scripts remain for subtask 012.
- Existing handover content was preserved byte-for-byte and this section appended only after all gates passed.
- Fail-first evidence: target/0043-012-001-authenticated-logout-usecase-contract-fail-first.log
- Commit message: feat(auth): define authenticated logout use case contract

### 0043-012-002-authenticated-logout-orchestration
- Status: completed
- Added AuthenticatedLogoutService orchestration over the existing RevokeAuthenticatedSessionUseCase capability.
- Revocation uses the fixed non-sensitive reason USER_LOGOUT and propagates only the optional correlation ID.
- REVOKED maps to LOGGED_OUT; NOT_FOUND and EXPIRED map to idempotent ALREADY_LOGGED_OUT; FAILED, unexpected states, and reactive errors map to FAILED.
- The service exposes no session existence details beyond the two successful public outcomes and has no web, Redis, persistence-port, crypto, or serialization dependency.
- Main-script progress: 2/5; three main scripts remain for subtask 012.
- Existing handover content was preserved byte-for-byte and this section appended only after all gates passed.
- Fail-first evidence: target/0043-012-002-authenticated-logout-orchestration-fail-first.log
- Commit message: feat(auth): orchestrate authenticated session logout

### 0043-012-003-authenticated-logout-runtime-composition
- Status: completed
- Composed AuthenticatedLogoutUseCase in AuthenticatedSessionConfig using AuthenticatedLogoutService and the existing RevokeAuthenticatedSessionUseCase capability.
- Runtime wiring remains at the application capability boundary; the logout bean does not access AuthenticatedSessionPort, Redis, CachePort, protection, or serialization directly.
- Existing authenticated-session lifecycle, transition, portal-context, login transport, and registration composition remain preserved.
- Main-script progress: 3/5; two main scripts remain for subtask 012.
- Existing handover content was preserved byte-for-byte and this section appended only after all gates passed.
- Fail-first evidence: target/0043-012-003-authenticated-logout-runtime-composition-fail-first.log
- Commit message: feat(auth): compose authenticated logout runtime

### 0043-012-004-authenticated-logout-http-endpoint
- Status: completed
- Added POST /api/v1/auth/logout using the canonical sid_xxx request header and AuthenticatedLogoutUseCase boundary.
- Valid sid_xxx values are revoked through the application use case; missing, blank, malformed, absent, expired, or already terminated sessions return the same idempotent HTTP 200 success envelope.
- LOGGED_OUT and ALREADY_LOGGED_OUT are intentionally indistinguishable in the public JSON response; neither session identity nor prior existence is exposed.
- Logout failure returns HTTP 503 with a generic err.system.unexpected MutationResponse and no infrastructure details.
- Responses use Cache-Control: no-store; the BFF does not echo sid_xxx in the response body or response headers. FE must discard its sid_xxx transport value after the logout response.
- Main-script progress: 4/5; one main script remains for subtask 012.
- Existing handover content was preserved byte-for-byte and this section appended only after all gates passed.
- Fail-first evidence: target/0043-012-004-authenticated-logout-http-endpoint-fail-first.log
- Commit message: feat(auth): expose idempotent authenticated logout endpoint

### 0043-012-005-final-authenticated-logout-stabilization-and-evidence
- Status: completed
- Finalized subtask 012 evidence for authenticated-session logout and revocation.
- Verified framework-neutral logout contracts, revocation orchestration, runtime composition, POST /api/v1/auth/logout, sid_xxx request transport, idempotent public success, generic failure handling, and Cache-Control no-store.
- Verified missing, malformed, absent, expired, and already terminated sessions do not expose session existence and do not invoke unsafe fallback behavior.
- Verified logout responses and diagnostics expose no session ID, actor/account context, policy/certificate identifiers, Redis details, or internal failure cause.
- Verified session lifecycle/revocation, request-context fail-closed behavior, login transition, personal-information context, registration preservation, and Redis sensitive-data safety.
- Evidence index: target/0043-012-005-evidence/evidence.md
- Evidence logs: target/0043-012-005-evidence/01-logout-contract.log through target/0043-012-005-evidence/07-request-and-registration-preservation.log
- Existing handover content was preserved byte-for-byte and this section appended only after all gates passed.
- Main-script progress: 5/5; zero main scripts remain for subtask 012.
- Subtask 012 status: complete.
- Commit message: test(auth): finalize authenticated logout evidence

### 0043-012-008-fix-account-continuation-architecture-test-throws
- Status: completed
- Classification: architecture-test compilation defect; no production behavior change.
- Added the required checked-exception declaration to authenticatedSessionTransitionIsDelivered because the test reads LoginOtpVerifyService through Files.readString(...).
- Preserved the delivered authenticated-session transition assertions from 0043-012-007.
- Ran the corrected architecture test, transition/foundation preservation tests, and the complete Maven test suite.
- Full-suite evidence: target/0043-012-008-fix-account-continuation-architecture-test-throws-full-suite.log
- Main-script progress remains 5/5; zero main scripts remain for subtask 012. Corrective scripts do not change the main-script total.
- Existing handover content was preserved byte-for-byte and this section appended only after all gates passed.
- Production source changed: no.
- Commit message: test(auth): declare account continuation architecture IO failure

### 0043-012-009-fix-get-login-accounts-runtime-composition
- Status: completed
- Classification: production runtime-composition defect discovered by Spring Boot startup; Java production and test compilation had already succeeded.
- Root cause: LoginOtpVerifyApplicationConfig requires GetLoginAccountsUseCase, while TrpGetAccountListAdapterConfig exposed LoginAccountAuthorizationPort but did not compose the canonical application use case.
- Added an explicit outer-layer @Bean that constructs GetLoginAccountsUseCase from the existing LoginAccountAuthorizationPort.
- Kept GetLoginAccountsUseCase framework-neutral; no @Component/@Service annotation and no compatibility constructor were added.
- Reused the existing TRPGetAccountList adapter, shared APIM transport, crypto, retry, and error mapping.
- Added fail-first runtime-composition architecture coverage and ran account-list, OTP verification, authenticated-transition, clean-architecture, registration-preservation, and clean test-compilation gates.
- Fail-first evidence: target/0043-012-009-fix-get-login-accounts-runtime-composition-fail-first.log (created before mvn clean; retain externally if clean removes target).
- Main-script progress remains 5/5; zero main scripts remain for subtask 012. Corrective scripts do not change the main-script total.
- Production source changed: yes, Spring composition only.
- Existing handover content was preserved; this section was appended only after all gates passed.
- Commit message: fix(auth): compose login account lookup use case

### 0043-012-010-fix-authenticated-logout-web-mapper-composition
- Status: completed
- Classification: production runtime web-composition defect discovered by Spring Boot startup; production and test compilation had already succeeded.
- Root cause: AuthLogoutController requires AuthenticatedLogoutWebMapper, but the mapper was neither component-scanned nor explicitly registered as a Spring bean.
- Added AuthenticatedLogoutWebConfig in the inbound web adapter configuration boundary and explicitly composed AuthenticatedLogoutWebMapper.
- Kept the mapper free of @Component/@Service annotations and kept logout application/runtime composition unchanged.
- Added fail-first Spring context coverage and ran logout endpoint, logout orchestration, authenticated-session lifecycle, request-context, login transport, and clean test-compilation gates.
- Main-script progress remains 5/5; zero main scripts remain for subtask 012. Corrective scripts do not change the main-script total.
- Production source changed: yes, inbound web composition only.
- Existing handover content was preserved byte-for-byte and this section appended only after all gates passed.
- Commit message: fix(auth): compose authenticated logout web mapper

### 0043-012-012-fix-message-delivery-reactive-error-contract
- Status: completed
- Classification: production reactive-contract regression introduced during the message-delivery corrective rewrite.
- Wrapped command validation, template rendering, and sender command construction in Mono.defer so validation/rendering failures are emitted reactively to subscribers rather than thrown before subscription.
- Restored canonical IllegalArgumentException messages for null/missing command fields and EMAIL subject validation.
- Preserved sender errors unchanged and retained the rule that rendering failures dispatch no SMS or email.
- Added no production constructor; MessageDeliveryAdapter retains exactly its two established constructors.
- Full-suite evidence: target/0043-012-012-fix-message-delivery-reactive-error-contract-full-suite.log
- Main-script progress remains 5/5; zero main scripts remain. Corrective scripts do not change the total.
- Existing handover content was preserved byte-for-byte and this section appended only after all gates passed.
- Commit message: fix(auth): preserve reactive message delivery errors

### 0043-012-014-fix-login-otp-binding-package-and-test-contracts
- Status: completed
- Classification: corrective-script package/import and duplicate-test-fixture defects; the selected-destination production fix remains unchanged.
- Corrected UpdateAuthFlowDestinationCommand, UpdateAuthFlowOtpCommand, and UpdateAuthFlowStatusCommand imports to application.usecase.session.authflow.
- Removed the invalid duplicate LoginOtpSelectedDestinationPersistenceTest introduced by 012-013.
- Extended the existing LoginOtpSessionBindingAdapterTest using its canonical AuthFlowSession, AuthFlowSessionPort, SessionTtlCalculator, and four-argument lifecycle constructor fixtures.
- The cumulative regression now verifies challenge binding, OTP_SENT transition, selected contact type, and selected masked destination in session data.
- Full-suite evidence: target/0043-012-014-fix-login-otp-binding-package-and-test-contracts-full-suite.log
- Main-script progress remains 5/5; zero main scripts remain. Corrective scripts do not change the total.
- Existing handover content was preserved byte-for-byte and this section appended only after all gates passed.
- Commit message: fix(auth): align OTP binding with auth-flow lifecycle contracts

### 0043-012-015-fix-login-otp-send-destination-handoff-anchor
- Status: completed
- Classification: corrective-script production-call migration defect.
- Runtime evidence proved the diagnosis: OTP send returned success and Redis stored OTP_SENT plus an active challenge, but AuthFlowSessionData.contactType and maskedDestination remained null. Resend and verify logged MISSING_CONTEXT and returned AUTH_FLOW_INVALID.
- Root cause of the incomplete correction: 012-013 migrated LoginOtpSessionBindingCommand calls to eight arguments first, then attempted a formatting-specific replacement for the old call; the replacement anchor no longer matched, leaving null/null in the production send and resend calls.
- Corrected both production calls using balanced Java invocation parsing and explicit eight-argument replacement.
- Initial send now passes destination channel and masked destination. Resend passes the selected protected-context channel and masked destination.
- Existing safe diagnostics remain unchanged.
- Full-suite evidence: target/0043-012-015-fix-login-otp-send-destination-handoff-anchor-full-suite.log
- Main-script progress remains 5/5; zero main scripts remain. Corrective scripts do not change the total.
- Commit message: fix(auth): hand off selected destination during OTP send

### 0043-012-016-rename-authenticated-session-header
- Status: completed
- Renamed the post-login authenticated-session HTTP header from sid_xxx to X-Authenticated-Session-Id.
- Retained RequestHeaderContextKeys.AUTHENTICATED_SESSION_HEADER as the canonical Java constant and changed only its wire value.
- Retained the opaque authenticated-session value format sid_<URL-safe-random-value>; no actor, account, policy, certificate, or trust data is encoded in the value.
- Kept auth_flow_sid as the distinct pre-auth cookie and did not reintroduce the retired Session-Id header.
- Updated production filters/controllers, runtime request context, tests, architecture guards, active documentation, and request fixtures consistently.
- No compatibility fallback accepts sid_xxx; callers must migrate to X-Authenticated-Session-Id.
- Renamed-file evidence: target/evidence/rename-authenticated-session-header-renamed-files.txt
- Full-suite evidence: target/0043-012-016-rename-authenticated-session-header-full-suite.log
- Main-script progress remains 5/5; zero main scripts remain. Corrective scripts do not change the total.
- Existing handover content was preserved byte-for-byte and this section appended only after all gates passed.
- Commit message: refactor(auth): use explicit authenticated session header

### 0043-012-018-make-authenticated-header-migration-resumable
- Status: completed
- Classification: corrective-script resumability defect; the source rename had already completed before the earlier evidence guard failed.
- Changed 012-016 so a rerun succeeds when no sid_xxx references remain and RequestHeaderContextKeys already contains X-Authenticated-Session-Id.
- The migration still fails if neither source changes nor the canonical header is present.
- Resumed compilation, authenticated transport tests, architecture guards, preservation tests, and the full Maven suite through the corrected 012-016 script.
- Retained the authenticated-session value prefix sid_ and the pre-auth auth_flow_sid cookie.
- Main-script progress remains 5/5; zero main scripts remain. Corrective scripts do not change the total.
- Commit message: test(auth): make authenticated header migration resumable

### 0043-012-020-fix-login-start-stabilization-http-expectations
- Status: completed
- Classification: stale contract-test expectation after the approved credential-rejection HTTP correction.
- Updated LoginStartWebContractStabilizationTest so incorrect password, incorrect username, invalid account, locked account, and previously failed credential submissions expect HTTP 401 instead of HTTP 422.
- Preserved BUSINESS_REJECTED and the existing safe BFF-owned error codes; DOWNSTREAM_ERROR remains reserved for genuine downstream technical failure.
- Production source changed by this correction: no.
- Main-script progress remains 5/5; zero main scripts remain.
- Commit message: test(auth): align login credential contract with HTTP 401

### 0043-013-001-authenticated-session-cookie-foundation
- Status: completed
- Subtask 013 main-script progress: 1/5; four main scripts remain.
- Added login.session cookie policy for portal_sid and authenticated XSRF-TOKEN under the existing AuthWorkflowProperties/YAML tree.
- Added plain inbound-web cookie writers and explicit Spring composition; portal_sid is always HttpOnly and XSRF-TOKEN is intentionally readable for double-submit CSRF.
- Cookie defaults use Path=/api, Secure=true, SameSite=None, and max-age aligned to the 900-second authenticated-session default.
- This stage intentionally does not cut over runtime transport; X-Authenticated-Session-Id remains active until the atomic verify/filter/logout migration stages.
- Fail-first evidence: target/evidence/0043-013-001-authenticated-session-cookie-foundation-fail-first.log
- Production constructors added merely for tests: none.
- Commit message: feat(auth): add authenticated session cookie foundation

### 0043-013-006-fix-established-fixtures-by-call-shape
- Status: completed
- Classification: corrective fixture-only migration for 013-002; subtask 013 main-script progress remains 2/5 with three main scripts remaining.
- Matched the two remaining one-argument established calls by Java call shape, independent of whitespace, line wrapping, indentation, and fully qualified type names.
- Captured exact before/after call-shape evidence in target/evidence/0043-013-006-fix-established-fixtures-by-call-shape-call-shapes.txt before compiling.
- Production contracts remain strict; no compatibility constructor or factory was added.
- Commit message: test(auth): fix remaining established result call shapes

### 0043-013-007-fix-call-shape-evidence-path-and-resume
- Status: completed
- Classification: evidence-path and resumability defect in corrective script 013-006.
- 013-006 had already migrated both test fixtures before failing because it wrote 0043-013-006-call-shapes.txt but attempted to display 0043-013-006-fix-established-fixtures-by-call-shape-call-shapes.txt.
- Aligned the evidence writer and display path, made the fixture migration idempotent, preserved prior evidence, and resumed compilation and targeted tests.
- Production contracts remain unchanged and strict.
- Subtask 013 main-script progress remains 2/5; three main scripts remain.
- Commit message: test(auth): fix CSRF fixture evidence path and resume

### 0043-013-009-fix-authenticated-xsrf-cookie-path-assertion
- Status: completed
- Classification: stale path-insensitive cookie assertion after 013-008; subtask 013 main-script progress remains 3/5 with two main scripts remaining.
- XSRF-TOKEN is intentionally present twice in the OTP-success response: the pre-auth cookie at /api/v1/auth is cleared with Max-Age=0, while the fresh authenticated cookie at /api is active.
- Updated the transport test to select and verify both cookies by path instead of using getFirst(cookieName), whose result depends on insertion order.
- Production behavior changed by this correction: no.
- Commit message: test(auth): distinguish pre-auth and authenticated XSRF cookies

### 0043-013-012-fix-ambiguous-context-assertions
- Status: completed
- Classification: corrective test-compilation fix for 013-011; subtask 013 main-script progress remains 4/5 with one main script remaining.
- Added explicit PortalAccessContext casts to ServerWebExchange.getAttribute results in both portal-context filter tests.
- This removes Eclipse/JDT and javac AssertJ overload ambiguity between object and predicate assertions.
- Production behavior changed by this correction: no.
- Updated-file evidence: target/evidence/0043-013-012-updated-files.txt
- Commit message: test(auth): disambiguate portal context assertions

### 0043-013-015-fix-stale-header-transport-architecture-guards
- Status: completed
- Classification: corrective full-suite architecture-guard migration after the portal_sid cutover; subtask 013 main-script progress remains 5/5 with zero main scripts remaining.
- Updated five stale guards that still required X-Authenticated-Session-Id or AUTHENTICATED_SESSION_HEADER.
- Guards now require portal_sid cookie consumption, authenticated X-XSRF-TOKEN validation, authenticated cookie publication after OTP verification, and cookie clearing during logout.
- Retained transition ordering, thin controller boundaries, two-argument Account-Ref resolution, and absence of header fallback.
- Complete Maven suite evidence: target/evidence/0043-013-015-fix-stale-header-transport-architecture-guards-full-suite.log
- Production behavior changed by this correction: no.
- Commit message: test(auth): align architecture guards with portal cookie transport

### 0043-014-001-return-authenticated-csrf-in-verify-response
- Status: completed
- Subtask 014 main-script progress: 1/3; two main scripts remain.
- Successful Login OTP verification now returns the freshly rotated authenticated CSRF token as result.data.csrfToken together with SELECT_ACCOUNT and opaque AccountRef values.
- Failure, missing-input, invalid OTP, exhaustion, account-list, and system outcomes never expose csrfToken.
- The authenticated session ID remains absent from the JSON response and continues to be delivered only through the HttpOnly portal_sid cookie.
- LoginOtpVerifyDataResponse redacts csrfToken from diagnostics.
- The authenticated XSRF-TOKEN cookie remains temporarily present in this stage and will be removed atomically in 0043-014-002.
- Fail-first evidence: target/evidence/0043-014-001-return-authenticated-csrf-in-verify-response-fail-first.log
- Commit message: feat(auth): return authenticated CSRF token after OTP verification

### 0043-014-008-fix-exact-verify-xsrf-cookie-assertion
- Status: completed
- Classification: corrective exact cumulative test migration; subtask 014 main-script progress remains 2/3 with one main script remaining.
- Root cause: the cumulative test asserts the XSRF cookies using properties.getCsrf().getCookieName() rather than a literal XSRF-TOKEN string, so the previous generic migration could not locate the assertion.
- Removed the stale active authenticated XSRF-TOKEN cookie expectation at /api.
- The successful verification response now requires exactly one XSRF response cookie: the expired pre-auth cleanup cookie at the configured pre-auth cookie path.
- Preserved the authenticated contract: portal_sid HttpOnly cookie plus result.data.csrfToken plus X-XSRF-TOKEN request header.
- Production behavior changed by this correction: no.
- Commit message: test(auth): remove stale authenticated XSRF cookie assertion

### 0043-014-012-fix-portal-sid-architecture-guard-configuration-boundary
- Status: completed
- Classification: corrective architecture-test boundary alignment; subtask 014 remains complete at 3/3 main scripts.
- Root cause: the architecture guard expected the literal portal_sid inside AuthenticatedSessionCookieWriter, but the writer correctly obtains the cookie name from AuthWorkflowProperties.AuthenticatedCookie.
- The guard now verifies the configurable cookie-policy dependency, forced HttpOnly behavior, and portal_sid as the application.yml default through AUTH_WORKFLOWS_LOGIN_SESSION_COOKIE_NAME.
- Preserved the final authenticated contract: configurable HttpOnly portal_sid cookie plus result.data.csrfToken plus X-XSRF-TOKEN request header.
- Production behavior changed by this correction: no.
- Commit message: test(auth): verify portal sid through configuration boundary

### 0043-014-009-final-csrf-response-transport-stabilization-and-readme
- Status: completed
- Subtask 014 main-script progress: 3/3; main scripts remaining: 0.
- Final authenticated transport: portal_sid HttpOnly cookie plus csrfToken returned in successful OTP-verification response data plus X-XSRF-TOKEN on subsequent authenticated requests.
- Removed authenticated XSRF-TOKEN cookie writer, properties, YAML, publication, logout clearing, and stale tests.
- Preserved pre-auth auth_flow_sid and response-body CSRF-token behavior.
- Full Maven suite passed; evidence: target/0043-014-009-evidence/evidence.md
- README.md updated with the final browser-session and CSRF contract.
- Commit message: test(auth): finalize body CSRF transport and documentation

### 0043-015-001-hybrid-authenticated-session-envelope
- Status: completed
- Subtask 015 main-script progress: 1/5; four main scripts remain.
- Introduced the hybrid authenticated-session Redis envelope with readable lifecycle metadata authenticated as AES-GCM AAD and encrypted sensitive context under data.attributes.
- Commit message: refactor(auth): store authenticated lifecycle metadata in hybrid envelope

### 0043-015-005-complete-hybrid-serialization-without-script-resume
- Status: completed
- Classification: state-independent corrective implementation; previous failed corrective scripts were not resumed.
- Redis lifecycle metadata is serialized explicitly as ISO-8601 strings through an adapter-private wire model.
- Malformed encrypted envelope content is normalized to a non-sensitive unprotect failure while configured-key validation remains explicit.
- Evidence: target/evidence/0043-015-005-complete-hybrid-serialization-without-script-resume-test-compile.log and target/evidence/0043-015-005-complete-hybrid-serialization-without-script-resume-targeted.log
- Commit message: fix(auth): complete hybrid session serialization directly

### 0043-015-006-session-timeout-configuration-and-sliding-lifecycle
- Status: completed
- Subtask 015 main-script progress: 2/5; three main scripts remain.
- Renamed auth-workflows.login.session.ttl-seconds to absolute-timeout-seconds with environment variable AUTH_WORKFLOWS_LOGIN_SESSION_ABSOLUTE_TIMEOUT_SECONDS.
- Retained idle-timeout-seconds as the sliding inactivity timeout and added terminal-retention-seconds for later LOGGED_OUT/REVOKED/EXPIRED persistence.
- Sliding touch remains idleExpiresAt=min(now+idleTimeout, absoluteExpiresAt), and Redis TTL remains the duration from now to the refreshed idle expiry.
- No deprecated property alias was added; pre-auth AuthFlowSession, OTP, and captcha ttl-seconds properties remain unchanged.
- Terminal status transitions are deliberately deferred to the next main script.
- Evidence: target/evidence/0043-015-006-session-timeout-configuration-and-sliding-lifecycle-test-compile.log and target/evidence/0043-015-006-session-timeout-configuration-and-sliding-lifecycle-targeted.log
- Commit message: refactor(auth): clarify authenticated session timeout policies

### 0043-015-007-fix-retired-timeout-key-guard-scope
- Status: completed
- Classification: corrective static-guard scope fix for 015-006; subtask 015 main-script progress remains 2/5 with three main scripts remaining.
- Root cause: the retired environment-name postcondition scanned src/test and matched the architecture test that intentionally asserted the old key is absent from application.yml.
- Scoped retired-key source scans to src/main and split the architecture-test literal while preserving its runtime negative assertion.
- Resumed and completed the authenticated-session timeout migration and its validation gates.
- Production behavior changed by this correction: no.
- Commit message: test(auth): scope retired session timeout key guard

### 0043-015-008-fix-login-foundation-terminal-retention-fixture
- Status: completed
- Classification: stale Login foundation fixture after adding the required terminal-retention policy; subtask 015 main-script progress remains 2/5 with three main scripts remaining.
- Added terminalRetentionSeconds=300 to Task0043LoginFoundationTest.validProperties so full login-policy validation reaches the intended assertions.
- Production validation remains fail-fast and unchanged; no production default, compatibility setter, or constructor was added for the test.
- Resumed and completed the authenticated-session timeout migration and its targeted/regression gates.
- Production behavior changed by this correction: no.
- Commit message: test(auth): complete authenticated session timeout fixture

### 0043-015-009-fix-login-foundation-absolute-timeout-expectation
- Status: completed
- Classification: stale Login foundation error-message assertion after renaming the authenticated-session absolute lifetime property; subtask 015 main-script progress remains 2/5 with three main scripts remaining.
- Updated the expected validation key from session.ttl-seconds to session.absolute-timeout-seconds.
- Production validation and timeout semantics remain unchanged; no compatibility alias was added.
- Resumed and completed the timeout migration and its targeted/regression gates.
- Production behavior changed by this correction: no.
- Commit message: test(auth): align login foundation with absolute timeout key

### 0043-015-015-fix-transition-config-production-timeout-message
- Status: completed
- Classification: stale production validation message after renaming the authenticated-session absolute lifetime property; subtask 015 main-script progress remains 3/5 with two main scripts remaining.
- Updated AuthenticatedSessionConfig duration-relationship failure from ttl-seconds to absolute-timeout-seconds.
- Validation behavior remains fail-fast and unchanged; only the configuration terminology was corrected.
- No deprecated property alias, compatibility constructor, or test-only production path was added.
- Evidence: target/evidence/0043-015-015-fix-transition-config-production-timeout-message-targeted.log and target/evidence/0043-015-015-fix-transition-config-production-timeout-message-regression.log
- Commit message: fix(auth): align session duration validation terminology

### 0043-015-016-authenticated-logout-orchestration-logged-out-transition
- Status: completed
- Subtask 015 main-script progress: 4/5; one main script remains.
- Migrated normal user logout from RevokeAuthenticatedSessionUseCase to the narrow LogoutAuthenticatedSessionUseCase.
- AuthenticatedLogoutService now issues LogoutActiveAuthenticatedSessionCommand and maps a newly persisted LOGGED_OUT session to LOGGED_OUT; status-only LOGGED_OUT, REVOKED, EXPIRED, and NOT_FOUND results remain idempotent ALREADY_LOGGED_OUT outcomes.
- Security/admin invalidation and failed-transition compensation continue using RevokeAuthenticatedSessionUseCase and persist REVOKED.
- Runtime composition remains at the lifecycle capability boundary and does not access Redis or AuthenticatedSessionPort directly.
- Evidence: target/evidence/0043-015-016-authenticated-logout-orchestration-logged-out-transition-fail-first.log, target/evidence/0043-015-016-authenticated-logout-orchestration-logged-out-transition-targeted.log, and target/evidence/0043-015-016-authenticated-logout-orchestration-logged-out-transition-regression.log
- Commit message: refactor(auth): route user logout through logged-out lifecycle

### 0043-015-017-final-authenticated-session-lifecycle-stabilization-and-readme
- Status: completed
- Subtask 015 main-script progress: 5/5; zero main scripts remain.
- Finalized the hybrid authenticated-session Redis envelope with readable lifecycle metadata authenticated as AES-GCM AAD and encrypted actor/account/CSRF context under data.attributes.
- Finalized sliding idle timeout, fixed absolute timeout, and terminal retention configuration; the retired authenticated-session ttl-seconds property has no fallback.
- Finalized persisted ACTIVE to LOGGED_OUT, REVOKED, and EXPIRED transitions; normal user logout updates the protected session to LOGGED_OUT rather than deleting it.
- Verified missing portal_sid logout remains side-effect-free and idempotent, while all terminal sessions fail closed for later authenticated requests.
- Updated README.md with the final portal_sid, response-body CSRF, hybrid Redis envelope, timeout, and terminal lifecycle contract.
- Full Maven suite passed. Evidence index: target/0043-015-017-evidence/evidence.md
- Commit message: test(auth): finalize authenticated session lifecycle evidence

### 0043-015-018-fix-final-stale-authenticated-session-architecture-guards
- Status: completed
- Classification: stale architecture guards after the approved hybrid persistence model, timeout-property rename, and logout lifecycle split; subtask 015 remains complete at 5/5 main scripts.
- Updated the persistence contract guard from the old root version/ciphertext/nonce/tag envelope to the hybrid lifecycle metadata plus data.attributes crypto envelope.
- Scoped domain neutrality to the actual authenticated-session domain model and excluded ProtectedAuthenticatedSession, which is intentionally the persistence boundary DTO; the domain remains free of Spring, cache-client, HTTP, JSON, and crypto dependencies.
- Updated transition composition to require getAbsoluteTimeoutSeconds and getTerminalRetentionSeconds instead of retired getTtlSeconds, while preserving Clock, protected ports, create/revoke transition composition, and dedicated normal logout composition.
- Added all three guards to the final 015-017 evidence gates and resumed the full final stabilization script.
- Production behavior changed by this correction: no.
- Evidence: target/evidence/0043-015-018-fix-final-stale-authenticated-session-architecture-guards-targeted.log and target/0043-015-017-evidence/evidence.md
- Commit message: test(auth): align session architecture guards with hybrid lifecycle

### 0043-015-019-fix-domain-architecture-whitespace-sensitive-status-guard
- Status: completed
- Classification: formatting-sensitive architecture assertion in the 015-018 domain guard; subtask 015 remains complete at 5/5 main scripts.
- Root cause: AuthenticatedSession.java uses compact formatting with status==AuthenticatedSessionStatus.ACTIVE, while the guard required spaces around the equality operator.
- Normalized the session source before asserting ACTIVE status, idle-expiry, and absolute-expiry usability semantics.
- Corrected both the cumulative test source and the 015-018 generator so reruns cannot restore the fragile assertion.
- Production behavior changed by this correction: no.
- Evidence: target/evidence/0043-015-019-fix-domain-architecture-whitespace-sensitive-status-guard-targeted.log and target/0043-015-017-evidence/evidence.md
- Commit message: test(auth): make session status guard whitespace neutral

### 0043-016-001-expand-login-otp-account-selection-response
- Status: completed
- Task 0043 corrective subtask 016 progress: 1/3; two main scripts remain.
- Expanded successful POST /api/v1/auth/login/otp/verify account-selection entries to expose accountRef, formatted policyNo, formatted certNo, accountEnv, trust, and schemeType.
- The public trust field is mapped from the existing internal trustCode; no duplicate account lookup or model was introduced.
- Raw policy/certificate identifiers, termination fields, actor context, and authenticated-session identity remain absent from the JSON response.
- Preserved APIM account order, AccountRef assignment, authenticated-session establishment, portal_sid publication, and response-body CSRF transport.
- Fail-first evidence: target/evidence/0043-016-001-expand-login-otp-account-selection-response-fail-first.log
- Targeted evidence: target/evidence/0043-016-001-expand-login-otp-account-selection-response-targeted.log
- Commit message: feat(auth): expose account details after login OTP verification

### 0043-016-006-fix-business-error-architecture-test-expectation

- Aligned the Task 0043 business-error architecture guard with the implemented compatible mixed-envelope mapping.
- Wrong-password plus exactly one `success=false` item preserves `fail-count` through `data.get(0).failCount()`.
- Contradictory known-error envelopes remain `UNAVAILABLE`.
- Production Login behavior was not changed.
- Commit message: `test(auth): align login business error architecture expectation`

### 0043-016-007-final-login-apim-error-mapping-audit-and-evidence

- Status: completed
- Corrective subtask 016 progress: 3/3; zero main scripts remain.
- Finalized TRPMemberLogin APIM-to-Java mappings for wrong password, unregistered username, locked account, and no-valid-account outcomes.
- Confirmed compatible wrong-password mixed envelopes preserve fail-count internally; contradictory envelopes fail closed as unavailable.
- Updated login-error-matrix.md to use the confirmed APIM codes and HTTP 401/423 contracts and removed the typoed downstream code.
- Verified raw APIM/TAS error codes do not cross the public API boundary.
- Focused and complete Maven suites passed.
- Evidence: target/0043-016-007-evidence/evidence.md
- Commit message: `test(auth): finalize login APIM error mapping evidence`

### 0043-017-002-fix-authenticated-csrf-typed-failure-expectation

- Status: completed.
- Classification: stale test expectation after 0043-017-001 introduced a typed authenticated-CSRF failure.
- Updated the CSRF-mismatch assertion to expect AuthenticatedCsrfValidationException instead of AuthenticatedPortalContextResolutionException.
- This preserves the required distinction: unusable session maps later to HTTP 401 authentication required, while a usable session with invalid CSRF maps later to HTTP 403 flow-specific CSRF invalid.
- Production behavior changed by this correction: no.
- Evidence: target/evidence/0043-017-002-fix-authenticated-csrf-typed-failure-expectation-targeted.log and target/evidence/0043-017-002-fix-authenticated-csrf-typed-failure-expectation-regression.log
- Commit message: test(auth): expect typed authenticated CSRF failure

### 0043-017-003-login-and-logout-csrf-http-mappings

- Status: completed.
- Centralized CSRF verification remains owned by CsrfTokenValidationService; this stage adds flow-specific HTTP/error mappings.
- Invalid or unavailable sessions remain HTTP 401 with err.security.authentication.required.
- A usable Login pre-auth session with missing/incorrect CSRF now returns HTTP 403, VALIDATION_FAILED, err.login.csrf.invalid, source SERVER.
- A usable authenticated session with missing/incorrect logout CSRF now returns HTTP 403, VALIDATION_FAILED, err.logout.csrf.invalid, source SERVER.
- Registration's existing HTTP 403 err.registration.csrf.invalid behavior is preserved.
- Commit message: fix(auth): map invalid CSRF to flow-specific HTTP 403
- Evidence: target/0043-017-003-login-and-logout-csrf-http-mappings-evidence/evidence.md

### 0043-017-005-fix-stale-csrf-service-test-expectations

- Status: completed.
- Corrected stale Login OTP send/resend service-test expectations from AUTH_FLOW_INVALID to CSRF_INVALID for explicitly CSRF-invalid protected contexts.
- Preserved the existing no-side-effect assertions.
- Task 0043 CSRF focused tests pass; unrelated repository-wide README and account-projection failures remain separate follow-up work.
- Commit message: test(auth): align Login OTP service tests with typed CSRF outcomes
- Evidence: target/0043-017-005-fix-stale-csrf-service-test-expectations-evidence/evidence.md
