package com.bct.ngtpa.apiservice.config.auth.session;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisAuthenticatedSessionCasUpdateAdapter;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionRevisionGenerator;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.SecureAuthenticatedSessionRevisionGenerator;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionCasUpdatePort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/**
 * Wires the generic {@link AuthenticatedSessionCasUpdatePort} and the reload-per-attempt {@link
 * AuthenticatedSessionConcurrencyService} that wraps it.
 *
 * <p>These beans were built, tested, and committed (the CAS port's Redis adapter and the
 * concurrency service itself) but were never previously connected to the Spring context --
 * confirmed absent from every config-package file in this codebase. The Fund Price mode use case is
 * the first production consumer of this generic port, per fund-price-session-contract.md's
 * "Mutation mechanism (confirmed)" section.
 */
@Configuration
public class AuthenticatedSessionConcurrencyConfig {

  @Bean
  AuthenticatedSessionCasUpdatePort authenticatedSessionCasUpdatePort(
      ReactiveStringRedisTemplate redis, RedisCacheKeyFactory keys) {
    return new RedisAuthenticatedSessionCasUpdateAdapter(redis, keys);
  }

  @Bean
  AuthenticatedSessionRevisionGenerator authenticatedSessionRevisionGenerator() {
    return new SecureAuthenticatedSessionRevisionGenerator();
  }

  @Bean
  AuthenticatedSessionConcurrencyService authenticatedSessionConcurrencyService(
      AuthenticatedSessionPort sessions,
      AuthenticatedSessionProtectorPort protector,
      AuthenticatedSessionCasUpdatePort casPort,
      AuthenticatedSessionRevisionGenerator revisions,
      Clock clock) {
    return new AuthenticatedSessionConcurrencyService(
        sessions, protector, casPort, revisions, clock);
  }
}
