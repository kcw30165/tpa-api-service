package com.bct.ngtpa.apiservice.application.usecase.passwordvalidation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.security.ratelimit.*;
import com.bct.ngtpa.apiservice.application.port.out.RateLimitPort;
import com.bct.ngtpa.apiservice.infrastructure.security.passwordvalidation.HmacPasswordValidationBucketIdDeriver;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class PasswordValidationThrottleServiceTest {
  private final ActorContext actor =
      new ActorContext("synthetic-member", ActorUserRole.ROLE_USER_MBR);
  private final HmacPasswordValidationBucketIdDeriver key =
      new HmacPasswordValidationBucketIdDeriver(new byte[32]);

  @Test
  void usesOneOpaqueKeyedUsernameBucketAcrossCallsWithoutIdentityNormalization() {
    List<RateLimitCommand> calls = new ArrayList<>();
    RateLimitPort port =
        c -> {
          calls.add(c);
          return Mono.just(RateLimitResult.allowed(c.bucket(), c.maxAttempts(), 9, Instant.EPOCH));
        };
    var service = new PasswordValidationThrottleService(port, key, 10, 60);
    assertThat(service.check(actor, "one").block().status())
        .isEqualTo(PasswordValidationThrottleService.Status.ADMITTED);
    service.check(actor, "two").block();
    service.check(new ActorContext("Synthetic-member", actor.actorUserRole()), "three").block();
    assertThat(calls.getFirst().bucket())
        .isEqualTo(RateLimitBucket.PASSWORD_VALIDATION_BY_USERNAME);
    assertThat(calls.getFirst().bucketId())
        .matches("[0-9a-f]{64}")
        .doesNotContain(actor.actorUserId());
    assertThat(calls.get(1).bucketId()).isEqualTo(calls.getFirst().bucketId());
    assertThat(calls.get(2).bucketId()).isNotEqualTo(calls.getFirst().bucketId());
    assertThat(calls.getFirst().maxAttempts()).isEqualTo(10);
    assertThat(calls.getFirst().windowSeconds()).isEqualTo(60);
    assertThat(calls.getFirst().enabled()).isTrue();
    byte[] otherKey = new byte[32];
    otherKey[0] = 1;
    new PasswordValidationThrottleService(
            port, new HmacPasswordValidationBucketIdDeriver(otherKey), 10, 60)
        .check(actor, "four")
        .block();
    assertThat(calls.getLast().bucketId()).isNotEqualTo(calls.getFirst().bucketId());
  }

  @Test
  void blockedCarriesRetryAfterAndConfiguredPolicy() {
    List<RateLimitCommand> calls = new ArrayList<>();
    var service =
        new PasswordValidationThrottleService(
            c -> {
              calls.add(c);
              return Mono.just(RateLimitResult.blocked(c.bucket(), 3, 17, Instant.EPOCH));
            },
            key,
            3,
            20);
    var result = service.check(actor, "test").block();
    assertThat(result.status()).isEqualTo(PasswordValidationThrottleService.Status.BLOCKED);
    assertThat(result.retryAfterSeconds()).isEqualTo(17);
    assertThat(calls.getFirst().maxAttempts()).isEqualTo(3);
    assertThat(calls.getFirst().windowSeconds()).isEqualTo(20);
  }

  @Test
  void invalidIdentitySynchronousFailureAndMissingKeyCannotAdmit() {
    RateLimitPort never =
        c -> {
          throw new IllegalStateException("synthetic");
        };
    var service = new PasswordValidationThrottleService(never, key, 10, 60);
    assertThat(service.check(null, "test").block().status())
        .isEqualTo(PasswordValidationThrottleService.Status.UNAVAILABLE);
    assertThat(service.check(new ActorContext(" ", actor.actorUserRole()), "test").block().status())
        .isEqualTo(PasswordValidationThrottleService.Status.UNAVAILABLE);
    assertThat(service.check(actor, "test").block().status())
        .isEqualTo(PasswordValidationThrottleService.Status.UNAVAILABLE);
    assertThat(
            new PasswordValidationThrottleService(
                    never, new HmacPasswordValidationBucketIdDeriver(null), 10, 60)
                .check(actor, "test")
                .block()
                .status())
        .isEqualTo(PasswordValidationThrottleService.Status.UNAVAILABLE);
    assertThatThrownBy(() -> new PasswordValidationThrottleService(never, key, 10, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void errorsEmptyDisabledAndMissingKeyFailClosedWithoutRetry() {
    for (Mono<RateLimitResult> response :
        List.of(
            Mono.<RateLimitResult>error(new IllegalStateException("synthetic")),
            Mono.<RateLimitResult>empty(),
            Mono.just(RateLimitResult.disabled(RateLimitBucket.PASSWORD_VALIDATION_BY_USERNAME)))) {
      var port = mock(RateLimitPort.class);
      when(port.checkAndConsume(any())).thenReturn(response);
      assertThat(
              new PasswordValidationThrottleService(port, key, 10, 60)
                  .check(actor, "test")
                  .block()
                  .status())
          .isEqualTo(PasswordValidationThrottleService.Status.UNAVAILABLE);
      verify(port, times(1)).checkAndConsume(any());
    }
    RateLimitPort never =
        c -> {
          throw new AssertionError("unconfigured limiter called");
        };
    assertThat(
            new PasswordValidationThrottleService(
                    never, new HmacPasswordValidationBucketIdDeriver(new byte[0]), 10, 60)
                .check(actor, "test")
                .block()
                .status())
        .isEqualTo(PasswordValidationThrottleService.Status.UNAVAILABLE);
    assertThatThrownBy(() -> new PasswordValidationThrottleService(never, key, 0, 60))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
