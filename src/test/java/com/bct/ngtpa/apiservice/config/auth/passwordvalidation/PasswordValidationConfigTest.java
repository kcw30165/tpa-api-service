package com.bct.ngtpa.apiservice.config.auth.passwordvalidation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.security.ratelimit.*;
import com.bct.ngtpa.apiservice.application.port.out.RateLimitPort;
import com.bct.ngtpa.apiservice.application.usecase.passwordvalidation.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Mono;

class PasswordValidationConfigTest {
  private final List<RateLimitCommand> calls = new ArrayList<>();
  private final ApplicationContextRunner context = new ApplicationContextRunner()
      .withUserConfiguration(PasswordValidationConfig.class)
      .withBean(RateLimitPort.class, () -> c -> {
        calls.add(c);
        return Mono.just(RateLimitResult.allowed(c.bucket(), c.maxAttempts(), 0, Instant.EPOCH));
      }).withBean(AuthenticatedSessionConcurrencyService.class,
          () -> mock(AuthenticatedSessionConcurrencyService.class));

  @Test
  void absentSecretFailsClosedWithoutBreakingExistingStartup() {
    context.run(c -> {
      assertThat(c).hasNotFailed().hasSingleBean(PasswordValidationGrantService.class);
      assertThat(c.getBean(PasswordValidationThrottleService.class)
          .check(new ActorContext("synthetic-member", ActorUserRole.ROLE_USER_MBR), "test").block().status())
          .isEqualTo(PasswordValidationThrottleService.Status.UNAVAILABLE);
      assertThat(calls).isEmpty();
    });
  }

  @Test
  void defaultsAndExplicitPolicyAreBound() {
    String secret = Base64.getEncoder().encodeToString(new byte[32]);
    context.withPropertyValues("password-validation.rate-limit.secret="+secret).run(c -> {
      c.getBean(PasswordValidationThrottleService.class)
          .check(new ActorContext("synthetic-member", ActorUserRole.ROLE_USER_MBR), "test").block();
      assertThat(calls.getLast().maxAttempts()).isEqualTo(10);
      assertThat(calls.getLast().windowSeconds()).isEqualTo(60);
    });
    context.withPropertyValues("password-validation.rate-limit.secret="+secret,
        "password-validation.rate-limit.max-attempts=3", "password-validation.rate-limit.window-seconds=20")
        .run(c -> {
          c.getBean(PasswordValidationThrottleService.class)
              .check(new ActorContext("synthetic-member", ActorUserRole.ROLE_USER_MBR), "test").block();
          assertThat(calls.getLast().maxAttempts()).isEqualTo(3);
          assertThat(calls.getLast().windowSeconds()).isEqualTo(20);
        });
  }

  @Test
  void invalidPolicyOrSecretCannotEnableAdmission() {
    context.withPropertyValues("password-validation.rate-limit.max-attempts=0")
        .run(c -> assertThat(c).hasFailed());
    context.withPropertyValues("password-validation.rate-limit.window-seconds=0")
        .run(c -> assertThat(c).hasFailed());
    context.withPropertyValues("password-validation.rate-limit.secret=bad")
        .run(c -> assertThat(c).hasFailed());
  }
}
