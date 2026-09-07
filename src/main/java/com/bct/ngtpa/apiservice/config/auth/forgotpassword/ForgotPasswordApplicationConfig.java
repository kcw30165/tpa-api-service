package com.bct.ngtpa.apiservice.config.auth.forgotpassword;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordCancelWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordIdentityWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.session.config.AuthFlowSessionProperties;
import com.bct.ngtpa.apiservice.adapter.out.json.JacksonTransientJsonAdapter;
import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.session.SessionConcurrencyMode;
import com.bct.ngtpa.apiservice.application.dto.session.SessionConcurrencyPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionOnNewFlowPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionTtlPolicy;
import com.bct.ngtpa.apiservice.application.idempotency.ActionTaggedIdempotencyResponseEnvelope;
import com.bct.ngtpa.apiservice.application.idempotency.CanonicalIdempotencyFingerprintFactory;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationIdentityFactory;
import com.bct.ngtpa.apiservice.application.port.in.CancelForgotPasswordUseCase;
import com.bct.ngtpa.apiservice.application.port.in.CompleteForgotPasswordUseCase;
import com.bct.ngtpa.apiservice.application.port.in.CreateAuthFlowSessionCsrfUseCase;
import com.bct.ngtpa.apiservice.application.port.in.EvaluateForgotPasswordOtpResendUseCase;
import com.bct.ngtpa.apiservice.application.port.in.ProcessForgotPasswordDetailsOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.in.ResendForgotPasswordOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.in.StartForgotPasswordUseCase;
import com.bct.ngtpa.apiservice.application.port.in.VerifyForgotPasswordOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionCancellationPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionMutationLockPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordCompletionPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordOtpSessionBindingPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordUsernameLookupPort;
import com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengeStatePort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.cancel.CancelForgotPasswordService;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.CompleteForgotPasswordService;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.ForgotPasswordPasswordValidator;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.ForgotPasswordDetailsOtpLeasePolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.ForgotPasswordDetailsOtpProtectedOperation;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.ForgotPasswordUnderLockDetailsVerifier;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.IdempotentForgotPasswordDetailsOtpService;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.detailsotp.DefaultForgotPasswordDetailsOtpProtectedReplayResolver;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.detailsotp.ForgotPasswordDetailsOtpExecutionBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.detailsotp.ForgotPasswordDetailsOtpReplayCodec;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.start.ForgotPasswordStartExecutionBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.start.ForgotPasswordStartProtectedReplayPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.start.ForgotPasswordStartReplayCodec;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordDestinationMasker;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordInitialOtpPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpCommitCoordinator;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpCommitRetentionPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpReplayResolver;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpTemplates;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.SecureForgotPasswordOtpCommitRefGenerator;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ForgotPasswordOtpResendEligibilityValidator;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ForgotPasswordOtpResendFoundationService;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ForgotPasswordOtpResendPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify.VerifyForgotPasswordOtpService;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.ForgotPasswordStartProtectedOperation;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.IdempotentStartForgotPasswordService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionConcurrencyService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionCsrfService;
import com.bct.ngtpa.apiservice.infrastructure.security.CorsProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ForgotPasswordApplicationConfig {
  @Bean
  public Map<String, MemberIdentityType> forgotPasswordTasIdentityTypeMapping(
      AuthWorkflowProperties properties) {
    properties.validateForgotPasswordPolicy();
    Map<String, MemberIdentityType> mapping = new LinkedHashMap<>();
    properties
        .getForgotPassword()
        .getTasToPublicIdentityTypeMapping()
        .forEach(
            (tasType, publicType) -> mapping.put(normalize(tasType), toIdentityType(publicType)));
    return Map.copyOf(mapping);
  }

  @Bean
  public StartForgotPasswordUseCase startForgotPasswordUseCase(
      IdempotencyExecutor idempotencyExecutor,
      ForgotPasswordStartProtectedReplayPort protectedReplay,
      ForgotPasswordUsernameLookupPort lookupPort,
      ForgotPasswordAuthFlowContextProtectorPort contextProtector,
      AuthFlowSessionConcurrencyService concurrencyService,
      CreateAuthFlowSessionCsrfUseCase csrfUseCase,
      Clock clock,
      ObjectMapper objectMapper,
      AuthWorkflowProperties workflows,
      AuthFlowSessionProperties sessionProperties,
      Map<String, MemberIdentityType> forgotPasswordTasIdentityTypeMapping) {
    workflows.validateForgotPasswordPolicy();
    var scenario = sessionProperties.getScenarios().getForgotPassword();
    var forgotPassword = workflows.getForgotPassword();
    var operation =
        new ForgotPasswordStartProtectedOperation(
            lookupPort,
            contextProtector,
            concurrencyService,
            csrfUseCase,
            clock,
            new SessionTtlPolicy(
                scenario.isEnabled(), scenario.isRequired(), scenario.getTtlSeconds()),
            new SessionConcurrencyPolicy(
                SessionConcurrencyMode.valueOf(sessionProperties.getConcurrency().getMode().name()),
                SessionOnNewFlowPolicy.valueOf(
                    sessionProperties.getConcurrency().getOnNewFlow().name())),
            sessionProperties.getSessionId().getPrefix(),
            sessionProperties.getSessionId().getEntropyBytes(),
            forgotPassword.getUsername().isTrim(),
            forgotPassword.getUsername().isCaseInsensitive(),
            "ASCII_LOWERCASE".equalsIgnoreCase(forgotPassword.getUsername().getNormalization()),
            forgotPasswordTasIdentityTypeMapping);
    var json = new JacksonTransientJsonAdapter(objectMapper);
    var codec =
        new ForgotPasswordStartReplayCodec(
            new ActionTaggedIdempotencyResponseEnvelope(json), protectedReplay, csrfUseCase, clock);
    var responses = new ForgotPasswordStartExecutionBridge(json, codec, scenario.getTtlSeconds());
    var policy =
        new ForgotPasswordIdempotencyIntegrationPolicy(
            new IdempotencyOperationIdentityFactory(),
            new CanonicalIdempotencyFingerprintFactory());
    return new IdempotentStartForgotPasswordService(
        idempotencyExecutor, operation, responses, policy);
  }

  @Bean
  public com.bct.ngtpa.apiservice.application.port.in.VerifyForgotPasswordIdentityUseCase
      verifyForgotPasswordIdentityUseCase(
          com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context
                  .ForgotPasswordProtectedAuthFlowContextLoader
              loader,
          ForgotPasswordAuthFlowContextProtectorPort protector,
          com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort sessions,
          com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionMutationLockPort locks,
          com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionExpiryRefreshPort expiry,
          Clock clock) {
    return new com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.identity
        .VerifyForgotPasswordIdentityService(
        loader, protector, sessions, locks, expiry, clock, java.time.Duration.ofSeconds(10));
  }

  @Bean
  public CompleteForgotPasswordUseCase completeForgotPasswordUseCase(
      AuthFlowSessionPort sessions,
      AuthFlowSessionMutationLockPort locks,
      ForgotPasswordAuthFlowContextProtectorPort protector,
      ForgotPasswordCompletionPort completion,
      ForgotPasswordIdempotencyPort idempotency,
      AuthWorkflowPolicyPort policies,
      Clock clock,
      AuthWorkflowProperties properties,
      CorsProperties corsProperties) {
    properties.validateForgotPasswordPolicy();
    return new CompleteForgotPasswordService(
        sessions,
        locks,
        protector,
        completion,
        idempotency,
        new com.bct.ngtpa.apiservice.application.service.auth.forgotpassword
            .ForgotPasswordResetAuthorizationValidator(),
        new ForgotPasswordPasswordValidator(policies),
        clock,
        Duration.ofSeconds(properties.getForgotPassword().getIdempotency().getCompleteTtlSeconds()),
        Duration.ofSeconds(10),
        corsProperties == null ? List.of() : corsProperties.getAllowedOrigins());
  }

  @Bean
  public com.bct.ngtpa.apiservice.application.port.in.VerifyForgotPasswordDetailsUseCase
      verifyForgotPasswordDetailsUseCase(
          com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort sessions,
          com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionMutationLockPort locks,
          com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context
                  .ForgotPasswordProtectedAuthFlowContextLoader
              loader,
          com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordDetailsLookupPort lookup,
          com.bct.ngtpa.apiservice.application.usecase.session.authflow
                  .AuthFlowSessionLifecycleService
              lifecycleService,
          com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort
              protector,
          Clock clock) {
    return new com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details
        .VerifyForgotPasswordDetailsService(
        sessions, locks, loader, lookup, lifecycleService, protector, clock);
  }

  @Bean
  public com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordOtpSessionBindingPort
      forgotPasswordOtpSessionBindingPort(
          com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort sessions,
          com.bct.ngtpa.apiservice.application.port.out.OtpChallengeStatePort challenges,
          ForgotPasswordAuthFlowContextProtectorPort protector) {
    return new com.bct.ngtpa.apiservice.adapter.out.session.forgotpassword
        .ForgotPasswordOtpSessionBindingAdapter(sessions, challenges, protector);
  }

  @Bean
  public ForgotPasswordOtpTemplates forgotPasswordOtpTemplates() {
    return ForgotPasswordOtpTemplates.approved();
  }

  @Bean
  public ForgotPasswordIdentityWebMapper forgotPasswordIdentityWebMapper() {
    return new ForgotPasswordIdentityWebMapper();
  }

  private static MemberIdentityType toIdentityType(String publicType) {
    String normalized = normalize(publicType);
    return MemberIdentityType.valueOf("STAFF_NO".equals(normalized) ? "STAFFNO" : normalized);
  }

  private static String normalize(String value) {
    return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
  }

  @Bean
  public com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp
          .ForgotPasswordDetailsOtpLeasePolicy
      forgotPasswordDetailsOtpLeasePolicy(
          AuthWorkflowProperties workflows,
          com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties apim,
          java.util.List<
                  com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryTimeoutBound>
              deliveryTimeoutBounds,
          com.bct.ngtpa.apiservice.adapter.out.redis.config.RedisCacheProperties redis) {
    var d = workflows.getForgotPassword().getDetailsAndOtp();
    int sender =
        deliveryTimeoutBounds.stream()
            .mapToInt(
                com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryTimeoutBound
                    ::timeoutMillis)
            .max()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "message delivery timeout bounds must be configured"));
    return new com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp
        .ForgotPasswordDetailsOtpLeasePolicy(
        d.getMutationLockLeaseSeconds(),
        d.getMutationLockRedisAllowanceSeconds(),
        d.getMutationLockSafetyMarginSeconds(),
        apim.getRetry().getBusinessCall().getMaxAttempts(),
        apim.getTimeoutMilliseconds(),
        apim.getRetry().getBusinessCall().getBackoffMilliseconds(),
        sender,
        redis.getTimeoutMilliseconds());
  }

  @Bean
  public com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp
          .ForgotPasswordUnderLockDetailsVerifier
      forgotPasswordUnderLockDetailsVerifier(
          com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordDetailsLookupPort p) {
    return new com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp
        .ForgotPasswordUnderLockDetailsVerifier(p);
  }

  @Bean
  public SecureForgotPasswordOtpCommitRefGenerator forgotPasswordOtpCommitRefGenerator() {
    return new SecureForgotPasswordOtpCommitRefGenerator();
  }

  @Bean
  public ForgotPasswordOtpCommitRetentionPolicy forgotPasswordOtpCommitRetentionPolicy(
      @Value("${auth-workflows.forgot-password.otp-commit.expired-replay-grace-seconds:300}")
          int expiredReplayGraceSeconds,
      @Value("${auth-workflows.forgot-password.otp-commit.maximum-retention-seconds:1800}")
          int maximumRetentionSeconds) {
    return new ForgotPasswordOtpCommitRetentionPolicy(
        Duration.ofSeconds(expiredReplayGraceSeconds), Duration.ofSeconds(maximumRetentionSeconds));
  }

  @Bean
  public ForgotPasswordOtpCommitCoordinator forgotPasswordOtpCommitCoordinator(
      ForgotPasswordIdempotencyPort idempotency,
      com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordOtpCommitStatePort commitStates,
      ForgotPasswordOtpCommitRetentionPolicy retentionPolicy) {
    return new ForgotPasswordOtpCommitCoordinator(idempotency, commitStates, retentionPolicy);
  }

  @Bean
  public ForgotPasswordOtpReplayResolver forgotPasswordOtpReplayResolver(
      com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordOtpCommitStatePort commitStates,
      AuthFlowSessionPort sessions,
      OtpChallengeStatePort challenges,
      Clock clock,
      ForgotPasswordInitialOtpPolicy otpPolicy) {
    return new ForgotPasswordOtpReplayResolver(
        commitStates, sessions, challenges, clock, otpPolicy.resendCooldownSeconds());
  }

  @Bean
  public ProcessForgotPasswordDetailsOtpUseCase processForgotPasswordDetailsOtpUseCase(
      IdempotencyExecutor idempotencyExecutor,
      SecureForgotPasswordOtpCommitRefGenerator refs,
      AuthFlowSessionMutationLockPort locks,
      AuthFlowSessionPort sessions,
      ForgotPasswordAuthFlowContextProtectorPort protector,
      ForgotPasswordUnderLockDetailsVerifier details,
      OtpChallengePort otp,
      MessageDeliveryPort delivery,
      ForgotPasswordDestinationMasker masker,
      ForgotPasswordOtpSessionBindingPort binding,
      com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordOtpCommitStatePort commitStates,
      ForgotPasswordOtpCommitRetentionPolicy retention,
      ForgotPasswordOtpReplayResolver replay,
      ForgotPasswordDetailsOtpLeasePolicy leasePolicy,
      Clock clock,
      ObjectMapper objectMapper,
      AuthWorkflowProperties properties) {
    properties.validateForgotPasswordPolicy();
    Duration ttl =
        Duration.ofSeconds(
            properties.getForgotPassword().getIdempotency().getDetailsAndOtpTtlSeconds());
    var protectedOperation =
        new ForgotPasswordDetailsOtpProtectedOperation(
            refs,
            locks,
            sessions,
            protector,
            details,
            otp,
            delivery,
            masker,
            binding,
            commitStates,
            retention,
            leasePolicy,
            clock,
            ttl);
    var json = new JacksonTransientJsonAdapter(objectMapper);
    var protectedReplay = new DefaultForgotPasswordDetailsOtpProtectedReplayResolver(replay);
    var codec =
        new ForgotPasswordDetailsOtpReplayCodec(
            new ActionTaggedIdempotencyResponseEnvelope(json), protectedReplay);
    var responses = new ForgotPasswordDetailsOtpExecutionBridge(json, codec);
    var policy =
        new ForgotPasswordIdempotencyIntegrationPolicy(
            new IdempotencyOperationIdentityFactory(),
            new CanonicalIdempotencyFingerprintFactory());
    return new IdempotentForgotPasswordDetailsOtpService(
        idempotencyExecutor, protectedOperation, responses, policy, ttl);
  }

  @Bean
  public com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper
          .ForgotPasswordDetailsOtpWebMapper
      forgotPasswordDetailsOtpWebMapper() {
    return new com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper
        .ForgotPasswordDetailsOtpWebMapper();
  }

  @Bean
  public com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper
          .ForgotPasswordOtpVerifyWebMapper
      forgotPasswordOtpVerifyWebMapper() {
    return new com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper
        .ForgotPasswordOtpVerifyWebMapper();
  }

  @Bean
  public com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper
          .ForgotPasswordOtpResendWebMapper
      forgotPasswordOtpResendWebMapper() {
    return new com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper
        .ForgotPasswordOtpResendWebMapper();
  }

  @Bean
  public ForgotPasswordOtpResendPolicy forgotPasswordOtpResendPolicy(
      AuthWorkflowProperties properties) {
    var otp = properties.getForgotPassword().getOtp();
    return new ForgotPasswordOtpResendPolicy(
        otp.getResendCooldownSeconds(), otp.getMaxResendAttempts());
  }

  @Bean
  public ForgotPasswordOtpResendEligibilityValidator forgotPasswordOtpResendEligibilityValidator(
      ForgotPasswordOtpResendPolicy policy) {
    return new ForgotPasswordOtpResendEligibilityValidator(policy);
  }

  @Bean
  public EvaluateForgotPasswordOtpResendUseCase evaluateForgotPasswordOtpResendUseCase(
      ForgotPasswordOtpResendEligibilityValidator validator) {
    return new ForgotPasswordOtpResendFoundationService(validator);
  }

  @Bean
  public ForgotPasswordInitialOtpPolicy forgotPasswordInitialOtpPolicy(
      AuthWorkflowProperties properties) {
    var otp = properties.getForgotPassword().getOtp();
    return new ForgotPasswordInitialOtpPolicy(
        otp.getLength(),
        otp.isNumericOnly(),
        otp.getTtlSeconds(),
        otp.getResendCooldownSeconds(),
        otp.getMaxVerifyAttempts(),
        otp.getMaxResendAttempts(),
        otp.isInvalidatePreviousOnResend());
  }

  @Bean
  public ForgotPasswordDestinationMasker forgotPasswordDestinationMasker() {
    return new ForgotPasswordDestinationMasker();
  }

  @Bean
  public ForgotPasswordCancelWebMapper forgotPasswordCancelWebMapper() {
    return new ForgotPasswordCancelWebMapper();
  }

  @Bean
  public CancelForgotPasswordUseCase cancelForgotPasswordUseCase(
      AuthFlowSessionPort sessions,
      AuthFlowSessionCsrfService csrfService,
      OtpChallengePort otpChallenges,
      AuthFlowSessionCancellationPort cancellationPort,
      Clock clock,
      CorsProperties corsProperties) {
    return new CancelForgotPasswordService(
        sessions,
        csrfService,
        otpChallenges,
        cancellationPort,
        clock,
        corsProperties == null ? List.of() : corsProperties.getAllowedOrigins());
  }

  @Bean
  public ResendForgotPasswordOtpUseCase resendForgotPasswordOtpUseCase(
      IdempotencyExecutor idempotencyExecutor,
      SecureForgotPasswordOtpCommitRefGenerator refs,
      AuthFlowSessionMutationLockPort locks,
      AuthFlowSessionPort sessions,
      ForgotPasswordAuthFlowContextProtectorPort protector,
      EvaluateForgotPasswordOtpResendUseCase foundation,
      OtpChallengePort otp,
      OtpChallengeStatePort challengeState,
      MessageDeliveryPort delivery,
      ForgotPasswordDestinationMasker masker,
      com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordOtpCommitStatePort commitStates,
      ForgotPasswordOtpCommitRetentionPolicy retention,
      ForgotPasswordOtpReplayResolver replay,
      ForgotPasswordOtpTemplates templates,
      ForgotPasswordInitialOtpPolicy otpPolicy,
      Clock clock,
      ObjectMapper objectMapper,
      AuthWorkflowProperties properties,
      CorsProperties corsProperties) {
    properties.validateForgotPasswordPolicy();
    Duration ttl =
        Duration.ofSeconds(
            properties.getForgotPassword().getIdempotency().getOtpResendTtlSeconds());
    var protectedOperation =
        new com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend
            .ForgotPasswordOtpResendProtectedOperation(
            refs,
            locks,
            sessions,
            protector,
            foundation,
            otp,
            challengeState,
            delivery,
            masker,
            commitStates,
            retention,
            templates,
            otpPolicy,
            clock,
            ttl,
            Duration.ofSeconds(10),
            corsProperties == null ? List.of() : corsProperties.getAllowedOrigins());
    var json = new JacksonTransientJsonAdapter(objectMapper);
    var protectedReplay =
        new com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.otpresend
            .DefaultForgotPasswordOtpResendProtectedReplayResolver(replay);
    var codec =
        new com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.otpresend
            .ForgotPasswordOtpResendReplayCodec(
            new ActionTaggedIdempotencyResponseEnvelope(json), protectedReplay);
    var responses =
        new com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.otpresend
            .ForgotPasswordOtpResendExecutionBridge(json, codec);
    var policy =
        new ForgotPasswordIdempotencyIntegrationPolicy(
            new IdempotencyOperationIdentityFactory(),
            new CanonicalIdempotencyFingerprintFactory());
    return new com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend
        .IdempotentResendForgotPasswordOtpService(
        idempotencyExecutor, protectedOperation, responses, policy, ttl);
  }

  @Bean
  public VerifyForgotPasswordOtpUseCase verifyForgotPasswordOtpUseCase(
      com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context
              .ForgotPasswordProtectedAuthFlowContextLoader
          loader,
      ForgotPasswordAuthFlowContextProtectorPort protector,
      com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort sessions,
      com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionMutationLockPort locks,
      com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionExpiryRefreshPort expiry,
      OtpChallengePort otp,
      ForgotPasswordIdempotencyPort idempotency,
      Clock clock,
      AuthWorkflowProperties properties) {
    properties.validateForgotPasswordPolicy();
    return new VerifyForgotPasswordOtpService(
        loader,
        protector,
        sessions,
        locks,
        expiry,
        otp,
        idempotency,
        clock,
        Duration.ofSeconds(10),
        Duration.ofSeconds(properties.getForgotPassword().getResetAuthorization().getTtlSeconds()));
  }
}
