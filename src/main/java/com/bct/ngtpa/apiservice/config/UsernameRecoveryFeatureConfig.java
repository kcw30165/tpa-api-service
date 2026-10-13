package com.bct.ngtpa.apiservice.config;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.mapper.UsernameRecoveryCancelWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.mapper.UsernameRecoveryRequestWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.mapper.UsernameRecoveryStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.policy.UsernameRecoveryCancelResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.policy.UsernameRecoveryRequestResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.policy.UsernameRecoveryStartResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.out.json.JacksonTransientJsonAdapter;
import com.bct.ngtpa.apiservice.adapter.out.security.usernamerecovery.AesGcmUsernameRecoveryAuthFlowContextProtectorAdapter;
import com.bct.ngtpa.apiservice.adapter.out.security.usernamerecovery.UsernameRecoveryAuthFlowContextProtectionProperties;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookiePolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieSameSite;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationIdentityFactory;
import com.bct.ngtpa.apiservice.application.port.in.CancelUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.RequestUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.StartUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort;
import com.bct.ngtpa.apiservice.application.port.out.NoticeTemplateRegistryPort;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryEffectiveDestinationPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryLookupPort;
import com.bct.ngtpa.apiservice.application.service.auth.captcha.RegistrationCaptchaGateService;
import com.bct.ngtpa.apiservice.application.service.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContextBinding;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.IdempotentCancelUsernameRecoveryService;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.IdempotentRequestUsernameRecoveryService;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.IdempotentStartUsernameRecoveryService;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryCancelProtectedOperation;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryRequestProtectedOperation;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartProtectedOperation;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartValidator;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyResponseEnvelope;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.cancel.UsernameRecoveryCancelExecutionBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.cancel.UsernameRecoveryCancelReplayCodec;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.request.UsernameRecoveryRequestExecutionBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.request.UsernameRecoveryRequestReplayCodec;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.UsernameRecoveryStartExecutionBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.UsernameRecoveryStartProtectedReplayPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.UsernameRecoveryStartReplayCodec;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenGenerator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenHasher;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationService;
import com.bct.ngtpa.apiservice.infrastructure.security.CorsProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Repair 024A Spring composition for the Username Recovery controller prerequisites.
 *
 * <p>Wires the Username Recovery runtime beans around the generic auth-flow envelope, typed
 * protected auth-flow context, Redis-backed idempotency port, CSRF validation/reservation services,
 * the three use-case services, the stateless web mappers and response policies, and the Spring-free
 * {@link UsernameRecoveryCookieWriter} with the exact UR cookie policy (path {@code
 * /api/v1/auth/username-recovery}, Max-Age = UR session TTL, HttpOnly per cookie, Secure from
 * {@code USERNAME_RECOVERY_COOKIE_SECURE} defaulting to {@code true}, SameSite Strict, no Domain).
 * Every bean is declared {@code @ConditionalOnMissingBean} so a later task (024/025) may override
 * any single bean without a duplicate-definition failure.
 *
 * <p>Location note: this class lives in the global {@code config} package because it legitimately
 * composes {@code adapter.out} Redis adapters; the committed {@code adapter.in must not depend on
 * adapter.out} architecture rule prevents placing it under {@code adapter/in/web/auth/config}.
 */
@Configuration
public class UsernameRecoveryFeatureConfig {

  private static final String USERNAME_RECOVERY_COOKIE_PATH = "/api/v1/auth/username-recovery";

  @Bean
  @ConditionalOnMissingBean
  public UsernameRecoveryAuthFlowContextProtectionProperties
      usernameRecoveryAuthFlowContextProtectionProperties(
          @Value("${auth-workflows.username-recovery.context-protection.enabled:true}")
              boolean enabled,
          @Value(
                  "${auth-workflows.username-recovery.context-protection.key-version:${USERNAME_RECOVERY_CONTEXT_KEY_VERSION:v1}}")
              String keyVersion,
          @Value(
                  "${auth-workflows.username-recovery.context-protection.secret:${USERNAME_RECOVERY_CONTEXT_SECRET:}}")
              String secret) {
    return new UsernameRecoveryAuthFlowContextProtectionProperties(enabled, keyVersion, secret);
  }

  @Bean
  @ConditionalOnMissingBean(UsernameRecoveryAuthFlowContextProtectorPort.class)
  public UsernameRecoveryAuthFlowContextProtectorPort usernameRecoveryAuthFlowContextProtectorPort(
      UsernameRecoveryAuthFlowContextProtectionProperties properties) {
    return new AesGcmUsernameRecoveryAuthFlowContextProtectorAdapter(
        properties.keyVersion(), properties.secret());
  }

  @Bean
  @ConditionalOnMissingBean
  public UsernameRecoveryAuthFlowContextBinding usernameRecoveryAuthFlowContextBinding(
      UsernameRecoveryAuthFlowContextProtectorPort protector) {
    return new UsernameRecoveryAuthFlowContextBinding(protector);
  }

  @Bean
  @ConditionalOnMissingBean
  public CsrfTokenValidationService csrfTokenValidationService(CsrfTokenHasher csrfTokenHasher) {
    return new CsrfTokenValidationService(csrfTokenHasher);
  }

  @Bean
  @ConditionalOnMissingBean
  public UsernameRecoveryStartValidator usernameRecoveryStartValidator() {
    return new UsernameRecoveryStartValidator();
  }

  @Bean
  @ConditionalOnMissingBean
  public UsernameRecoveryIdempotencyIntegrationPolicy usernameRecoveryIdempotencyIntegrationPolicy(
      IdempotencyOperationIdentityFactory identities) {
    return new UsernameRecoveryIdempotencyIntegrationPolicy(identities);
  }

  @Bean
  @ConditionalOnMissingBean(TransientJsonPort.class)
  public TransientJsonPort transientJsonPort(ObjectMapper objectMapper) {
    return new JacksonTransientJsonAdapter(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public UsernameRecoveryIdempotencyResponseEnvelope usernameRecoveryIdempotencyResponseEnvelope(
      TransientJsonPort json) {
    return new UsernameRecoveryIdempotencyResponseEnvelope(json);
  }

  @Bean
  @ConditionalOnMissingBean
  public UsernameRecoveryStartReplayCodec usernameRecoveryStartReplayCodec(
      UsernameRecoveryIdempotencyResponseEnvelope envelope,
      UsernameRecoveryStartProtectedReplayPort protectedReplay,
      Clock clock) {
    return new UsernameRecoveryStartReplayCodec(envelope, protectedReplay, clock);
  }

  @Bean
  @ConditionalOnMissingBean
  public UsernameRecoveryStartExecutionBridge usernameRecoveryStartExecutionBridge(
      TransientJsonPort json, UsernameRecoveryStartReplayCodec codec) {
    return new UsernameRecoveryStartExecutionBridge(json, codec);
  }

  @Bean
  @ConditionalOnMissingBean
  public UsernameRecoveryStartProtectedOperation usernameRecoveryStartProtectedOperation(
      RegistrationCaptchaGateService captchaGateService,
      UsernameRecoveryLookupPort lookupPort,
      AuthFlowSessionPort authFlowSessionPort,
      UsernameRecoveryAuthFlowContextBinding contextBinding,
      CsrfTokenGenerator csrfTokenGenerator,
      CsrfTokenHasher csrfTokenHasher,
      Clock clock,
      AuthWorkflowProperties properties) {
    return new UsernameRecoveryStartProtectedOperation(
        captchaGateService,
        lookupPort,
        authFlowSessionPort,
        contextBinding,
        csrfTokenGenerator,
        csrfTokenHasher,
        clock,
        usernameRecoverySessionTtlSeconds(properties));
  }

  @Bean
  @ConditionalOnMissingBean(StartUsernameRecoveryUseCase.class)
  public StartUsernameRecoveryUseCase startUsernameRecoveryUseCase(
      IdempotencyExecutor executor,
      UsernameRecoveryStartProtectedOperation operation,
      UsernameRecoveryStartExecutionBridge responses,
      UsernameRecoveryIdempotencyIntegrationPolicy policy,
      UsernameRecoveryStartValidator validator,
      AuthWorkflowProperties properties) {
    return new IdempotentStartUsernameRecoveryService(
        executor,
        operation,
        responses,
        policy,
        validator,
        Duration.ofSeconds(usernameRecoverySessionTtlSeconds(properties)));
  }

  @Bean
  @ConditionalOnMissingBean
  public UsernameRecoveryRequestReplayCodec usernameRecoveryRequestReplayCodec(
      UsernameRecoveryIdempotencyResponseEnvelope envelope) {
    return new UsernameRecoveryRequestReplayCodec(envelope);
  }

  @Bean
  @ConditionalOnMissingBean
  public UsernameRecoveryRequestExecutionBridge usernameRecoveryRequestExecutionBridge(
      TransientJsonPort json, UsernameRecoveryRequestReplayCodec codec) {
    return new UsernameRecoveryRequestExecutionBridge(json, codec);
  }

  @Bean
  @ConditionalOnMissingBean
  public UsernameRecoveryRequestProtectedOperation usernameRecoveryRequestProtectedOperation(
      UsernameRecoveryAuthFlowContextBinding contextBinding,
      CsrfTokenValidationService csrfTokenValidationService,
      UsernameRecoveryEffectiveDestinationPort effectiveDestinationPort,
      AuthFlowSessionPort authFlowSessionPort,
      MessageDeliveryPort messageDeliveryPort,
      MaskingService maskingService,
      CorsProperties corsProperties,
      NoticeTemplateRegistryPort noticeTemplateRegistryPort) {
    return new UsernameRecoveryRequestProtectedOperation(
        contextBinding,
        csrfTokenValidationService,
        effectiveDestinationPort,
        authFlowSessionPort,
        messageDeliveryPort,
        maskingService,
        corsProperties.getAllowedOrigins(),
        noticeTemplateRegistryPort);
  }

  @Bean
  @ConditionalOnMissingBean(RequestUsernameRecoveryUseCase.class)
  public RequestUsernameRecoveryUseCase requestUsernameRecoveryUseCase(
      IdempotencyExecutor executor,
      UsernameRecoveryRequestProtectedOperation operation,
      UsernameRecoveryRequestExecutionBridge responses,
      UsernameRecoveryIdempotencyIntegrationPolicy policy,
      Clock clock,
      AuthWorkflowProperties properties) {
    return new IdempotentRequestUsernameRecoveryService(
        executor,
        operation,
        responses,
        policy,
        clock,
        Duration.ofSeconds(usernameRecoverySessionTtlSeconds(properties)));
  }

  @Bean
  @ConditionalOnMissingBean
  public UsernameRecoveryCancelReplayCodec usernameRecoveryCancelReplayCodec(
      UsernameRecoveryIdempotencyResponseEnvelope envelope) {
    return new UsernameRecoveryCancelReplayCodec(envelope);
  }

  @Bean
  @ConditionalOnMissingBean
  public UsernameRecoveryCancelExecutionBridge usernameRecoveryCancelExecutionBridge(
      TransientJsonPort json, UsernameRecoveryCancelReplayCodec codec) {
    return new UsernameRecoveryCancelExecutionBridge(json, codec);
  }

  @Bean
  @ConditionalOnMissingBean
  public UsernameRecoveryCancelProtectedOperation usernameRecoveryCancelProtectedOperation(
      AuthFlowSessionPort authFlowSessionPort,
      UsernameRecoveryAuthFlowContextBinding contextBinding,
      CsrfTokenValidationService csrfTokenValidationService,
      CorsProperties corsProperties) {
    return new UsernameRecoveryCancelProtectedOperation(
        authFlowSessionPort,
        contextBinding,
        csrfTokenValidationService,
        corsProperties.getAllowedOrigins());
  }

  @Bean
  @ConditionalOnMissingBean(CancelUsernameRecoveryUseCase.class)
  public CancelUsernameRecoveryUseCase cancelUsernameRecoveryUseCase(
      IdempotencyExecutor executor,
      UsernameRecoveryCancelProtectedOperation operation,
      UsernameRecoveryCancelExecutionBridge responses,
      UsernameRecoveryIdempotencyIntegrationPolicy policy,
      Clock clock,
      AuthWorkflowProperties properties) {
    return new IdempotentCancelUsernameRecoveryService(
        executor,
        operation,
        responses,
        policy,
        clock,
        Duration.ofSeconds(usernameRecoverySessionTtlSeconds(properties)));
  }

  @Bean
  @ConditionalOnMissingBean
  public UsernameRecoveryStartWebMapper usernameRecoveryStartWebMapper() {
    return new UsernameRecoveryStartWebMapper();
  }

  @Bean
  @ConditionalOnMissingBean
  public UsernameRecoveryRequestWebMapper usernameRecoveryRequestWebMapper() {
    return new UsernameRecoveryRequestWebMapper();
  }

  @Bean
  @ConditionalOnMissingBean
  public UsernameRecoveryCancelWebMapper usernameRecoveryCancelWebMapper() {
    return new UsernameRecoveryCancelWebMapper();
  }

  @Bean
  @ConditionalOnMissingBean
  public UsernameRecoveryStartResponsePolicy usernameRecoveryStartResponsePolicy() {
    return new UsernameRecoveryStartResponsePolicy();
  }

  @Bean
  @ConditionalOnMissingBean
  public UsernameRecoveryRequestResponsePolicy usernameRecoveryRequestResponsePolicy() {
    return new UsernameRecoveryRequestResponsePolicy();
  }

  @Bean
  @ConditionalOnMissingBean
  public UsernameRecoveryCancelResponsePolicy usernameRecoveryCancelResponsePolicy() {
    return new UsernameRecoveryCancelResponsePolicy();
  }

  @Bean
  @ConditionalOnMissingBean
  public UsernameRecoveryCookieWriter usernameRecoveryCookieWriter(
      AuthWorkflowProperties properties,
      @Value("${USERNAME_RECOVERY_COOKIE_SECURE:true}") boolean secure) {
    int ttlSeconds = usernameRecoverySessionTtlSeconds(properties);
    SessionCookiePolicy authFlowPolicy =
        new SessionCookiePolicy(
            "auth_flow_sid",
            USERNAME_RECOVERY_COOKIE_PATH,
            ttlSeconds,
            true,
            secure,
            SessionCookieSameSite.NONE);
    return new UsernameRecoveryCookieWriter(authFlowPolicy);
  }

  private static int usernameRecoverySessionTtlSeconds(AuthWorkflowProperties properties) {
    return requiredUsernameRecovery(properties).getSession().getTtlSeconds();
  }

  private static AuthWorkflowProperties.UsernameRecovery requiredUsernameRecovery(
      AuthWorkflowProperties properties) {
    if (properties.getUsernameRecovery() == null) {
      throw new IllegalStateException("auth-workflows.username-recovery configuration is required");
    }
    return properties.getUsernameRecovery();
  }
}
