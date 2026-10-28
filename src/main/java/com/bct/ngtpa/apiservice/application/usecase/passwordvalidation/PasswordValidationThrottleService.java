package com.bct.ngtpa.apiservice.application.usecase.passwordvalidation;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.security.ratelimit.*;
import com.bct.ngtpa.apiservice.application.port.out.RateLimitPort;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import reactor.core.publisher.Mono;

public final class PasswordValidationThrottleService {
  public enum Status { ADMITTED, BLOCKED, UNAVAILABLE }
  public record Decision(Status status, int retryAfterSeconds) {}

  private final RateLimitPort limiter;
  private final byte[] secret;
  private final int limit;
  private final int windowSeconds;

  public PasswordValidationThrottleService(
      RateLimitPort limiter, byte[] secret, int limit, int windowSeconds) {
    this.limiter = Objects.requireNonNull(limiter);
    this.secret = secret == null ? new byte[0] : secret.clone();
    if (limit <= 0 || windowSeconds <= 0) {
      throw new IllegalArgumentException("Invalid password-validation rate policy");
    }
    this.limit = limit;
    this.windowSeconds = windowSeconds;
  }

  public Mono<Decision> check(ActorContext authenticatedActor, String correlationId) {
    return Mono.defer(() -> {
      if (secret.length < 32) {
        return Mono.just(new Decision(Status.UNAVAILABLE, 0));
      }
      String username = Objects.requireNonNull(authenticatedActor).actorUserId();
      if (username == null || username.isBlank()) {
        return Mono.just(new Decision(Status.UNAVAILABLE, 0));
      }
      return limiter.checkAndConsume(new RateLimitCommand(
          RateLimitBucket.PASSWORD_VALIDATION_BY_USERNAME, bucketId(username), true,
          limit, windowSeconds, correlationId)).map(result -> switch (result.status()) {
            case ALLOWED -> new Decision(Status.ADMITTED, 0);
            case BLOCKED -> new Decision(Status.BLOCKED, result.retryAfterSeconds());
            case DISABLED -> new Decision(Status.UNAVAILABLE, 0);
          });
    }).defaultIfEmpty(new Decision(Status.UNAVAILABLE, 0))
        .onErrorReturn(new Decision(Status.UNAVAILABLE, 0));
  }

  private String bucketId(String username) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(
          ("ngtpa:password-validation:username:v1:" + username).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("Password-validation limiter identity unavailable");
    }
  }
}
