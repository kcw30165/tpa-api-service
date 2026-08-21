package com.bct.ngtpa.apiservice.config.auth.session;

import com.bct.ngtpa.apiservice.application.port.in.RefreshCsrfTokenUseCase;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.service.security.csrf.OriginRefererValidator;
import com.bct.ngtpa.apiservice.application.usecase.auth.csrf.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.*;
import com.bct.ngtpa.apiservice.infrastructure.security.CorsProperties;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CsrfTokenRefreshApplicationConfig {
  @Bean
  AuthenticatedSessionCsrfRefreshService authenticatedSessionCsrfRefreshService(
      AuthenticatedSessionPort sessions,
      AuthenticatedSessionProtectorPort protector,
      AuthenticatedSessionCsrfRotationPort rotations,
      CsrfTokenGenerator tokens,
      CsrfTokenHasher hasher,
      Clock clock) {
    return new AuthenticatedSessionCsrfRefreshService(
        sessions, protector, rotations, tokens, hasher, clock);
  }

  @Bean
  PreAuthSessionCsrfRefreshService preAuthSessionCsrfRefreshService(
      AuthFlowSessionCsrfService authFlowSessionCsrfService, Clock clock) {
    return new PreAuthSessionCsrfRefreshService(authFlowSessionCsrfService, clock);
  }

  @Bean
  RefreshCsrfTokenUseCase refreshCsrfTokenUseCase(
      AuthenticatedSessionCsrfRefreshService authenticated,
      PreAuthSessionCsrfRefreshService preAuth,
      CorsProperties cors,
      Clock clock) {
    return new RefreshCsrfTokenService(
        authenticated, preAuth, new OriginRefererValidator(), cors.getAllowedOrigins(), clock);
  }
}
