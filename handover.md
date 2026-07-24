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
