package com.bct.ngtpa.apiservice.config.auth.session;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisAuthenticatedSessionAdapter;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisAuthenticatedSessionJsonMapper;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.otp.verify.LoginOtpVerifyRetentionProperties;
import com.bct.ngtpa.apiservice.application.port.in.EstablishAuthenticatedLoginSessionUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedLoginHandoffPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.application.port.out.PortalAccessContextPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.session.DeleteAuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.session.EstablishAuthenticatedLoginSessionService;
import com.bct.ngtpa.apiservice.application.usecase.auth.logout.AuthenticatedLogoutService;
import com.bct.ngtpa.apiservice.application.usecase.auth.logout.AuthenticatedLogoutUseCase;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedAccountContextResolver;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionLifecycleService;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionValidationService;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.LoadAndTouchAuthenticatedSessionUseCase;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.LogoutAuthenticatedSessionUseCase;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.SecureAuthenticatedSessionIdGenerator;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.ValidateAuthenticatedSessionUseCase;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenGenerator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenHasher;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.RevisionAwareAuthFlowSessionWriter;
import com.bct.ngtpa.apiservice.infrastructure.security.session.AesGcmAuthenticatedSessionProtector;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(AuthenticatedSessionProtectionProperties.class)
public class AuthenticatedSessionConfig {
  @Bean
  RedisAuthenticatedSessionJsonMapper redisAuthenticatedSessionJsonMapper() {
    return new RedisAuthenticatedSessionJsonMapper();
  }

  @Bean
  AuthenticatedSessionPort authenticatedSessionPort(
      CachePort cachePort,
      RedisCacheKeyFactory keyFactory,
      RedisAuthenticatedSessionJsonMapper jsonMapper) {
    return new RedisAuthenticatedSessionAdapter(cachePort, keyFactory, jsonMapper);
  }

  @Bean
  AuthenticatedSessionProtectorPort authenticatedSessionProtector(
      AuthenticatedSessionProtectionProperties properties, ObjectMapper objectMapper) {
    properties.validate();
    return new AesGcmAuthenticatedSessionProtector(properties.getBase64Key(), objectMapper.copy());
  }

  @Bean
  SecureAuthenticatedSessionIdGenerator authenticatedSessionIdGenerator() {
    return new SecureAuthenticatedSessionIdGenerator();
  }

  @Bean
  AuthenticatedSessionLifecycleService authenticatedSessionLifecycleService(
      AuthenticatedSessionPort authenticatedSessionPort,
      AuthenticatedSessionProtectorPort authenticatedSessionProtector,
      SecureAuthenticatedSessionIdGenerator authenticatedSessionIdGenerator,
      Clock clock,
      AuthWorkflowProperties properties) {
    if (properties.getLogin() == null) {
      throw new IllegalStateException("auth-workflows.login configuration is required");
    }
    var session = properties.getLogin().getSession();
    if (session.getIdleTimeoutSeconds() <= 0) {
      throw new IllegalStateException(
          "auth-workflows.login.session.idle-timeout-seconds must be greater than zero");
    }
    if (session.getAbsoluteTimeoutSeconds() <= 0) {
      throw new IllegalStateException(
          "auth-workflows.login.session.absolute-timeout-seconds must be greater than zero");
    }
    if (session.getTerminalRetentionSeconds() <= 0) {
      throw new IllegalStateException(
          "auth-workflows.login.session.terminal-retention-seconds must be greater than zero");
    }
    if (session.getIdleTimeoutSeconds() > session.getAbsoluteTimeoutSeconds()) {
      throw new IllegalStateException(
          "auth-workflows.login.session.idle-timeout-seconds must not exceed absolute-timeout-seconds");
    }
    return new AuthenticatedSessionLifecycleService(
        authenticatedSessionPort,
        authenticatedSessionProtector,
        authenticatedSessionIdGenerator,
        Duration.ofSeconds(session.getIdleTimeoutSeconds()),
        Duration.ofSeconds(session.getAbsoluteTimeoutSeconds()),
        Duration.ofSeconds(session.getTerminalRetentionSeconds()),
        clock);
  }

  @Bean
  @Primary
  PortalAccessContextPort authenticatedPortalAccessContextPort(
      LoadAndTouchAuthenticatedSessionUseCase lifecycle) {
    return new AuthenticatedAccountContextResolver(lifecycle);
  }

  @Bean
  DeleteAuthFlowSessionPort deleteAuthFlowSessionPort(
      AuthFlowSessionPort authFlowSessionPort, Clock clock) {
    RevisionAwareAuthFlowSessionWriter writer =
        new RevisionAwareAuthFlowSessionWriter(authFlowSessionPort);
    return authFlowSessionId -> writer.complete(authFlowSessionId, clock.instant()).then();
  }

  /* Source-compatible direct-construction boundary retained for existing tests.
   * Production Spring wiring continues to use the injected Clock overload. */
  DeleteAuthFlowSessionPort deleteAuthFlowSessionPort(AuthFlowSessionPort authFlowSessionPort) {
    RevisionAwareAuthFlowSessionWriter writer =
        new RevisionAwareAuthFlowSessionWriter(authFlowSessionPort);
    return authFlowSessionId ->
        writer.complete(authFlowSessionId, Clock.systemUTC().instant()).then();
  }

  @Bean
  AuthenticatedLogoutUseCase authenticatedLogoutUseCase(LogoutAuthenticatedSessionUseCase logout) {
    return new AuthenticatedLogoutService(logout);
  }

  /*
   * Source-compatible direct-construction boundary retained for existing unit
   * tests.
   * Spring selects the @Bean overload below, which injects the durable Redis
   * handoff.
   */
  EstablishAuthenticatedLoginSessionUseCase establishAuthenticatedLoginSessionUseCase(
      AuthenticatedSessionLifecycleService lifecycle,
      DeleteAuthFlowSessionPort cleanup,
      CsrfTokenGenerator csrfTokenGenerator,
      CsrfTokenHasher csrfTokenHasher) {
    return new EstablishAuthenticatedLoginSessionService(
        lifecycle, cleanup, lifecycle, csrfTokenGenerator, csrfTokenHasher);
  }

  @Bean
  EstablishAuthenticatedLoginSessionUseCase establishAuthenticatedLoginSessionUseCase(
      AuthenticatedSessionLifecycleService lifecycle,
      DeleteAuthFlowSessionPort cleanup,
      CsrfTokenGenerator csrfTokenGenerator,
      CsrfTokenHasher csrfTokenHasher,
      AuthenticatedLoginHandoffPort handoffs,
      SecureAuthenticatedSessionIdGenerator authenticatedSessionIdGenerator,
      LoginOtpVerifyRetentionProperties retention) {
    retention.validate();
    return new EstablishAuthenticatedLoginSessionService(
        lifecycle,
        cleanup,
        lifecycle,
        csrfTokenGenerator,
        csrfTokenHasher,
        handoffs,
        authenticatedSessionIdGenerator,
        Duration.ofSeconds(retention.getAuthenticatedLoginHandoffRetentionSeconds()));
  }

  @Bean
  ValidateAuthenticatedSessionUseCase validateAuthenticatedSessionUseCase(
      AuthenticatedSessionLifecycleService lifecycle, CsrfTokenHasher csrfTokenHasher) {
    return new AuthenticatedSessionValidationService(lifecycle, csrfTokenHasher);
  }
}
