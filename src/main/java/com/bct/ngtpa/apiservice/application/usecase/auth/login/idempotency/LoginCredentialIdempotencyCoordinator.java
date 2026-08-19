package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency;

import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.*;
import com.bct.ngtpa.apiservice.application.port.out.LoginCredentialIdempotencyPort;
import java.time.*;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class LoginCredentialIdempotencyCoordinator {
  private final LoginCredentialIdempotencyPort port;
  private final Clock clock;
  private final Duration ttl;

  public LoginCredentialIdempotencyCoordinator(
      LoginCredentialIdempotencyPort port, Clock clock, Duration ttl) {
    this.port = Objects.requireNonNull(port);
    this.clock = Objects.requireNonNull(clock);
    if (ttl == null || ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("ttl must be positive");
    }
    this.ttl = ttl;
  }

  public Mono<LoginIdempotencyStartResult> reserve(
      LoginIdempotencyKey key, LoginCredentialRequestHash hash) {
    return port.startProcessing(
        new LoginIdempotencyStartCommand(
            LoginIdempotencyAction.CREDENTIALS, key, hash, clock.instant(), ttl));
  }

  public Mono<LoginIdempotencyRecord> complete(
      LoginIdempotencyKey key,
      LoginCredentialRequestHash hash,
      LoginIdempotencyReplaySnapshot snapshot) {
    return port.markCompleted(
        new LoginIdempotencyCompleteCommand(
            LoginIdempotencyAction.CREDENTIALS, key, hash, snapshot, clock.instant(), ttl));
  }

  public Mono<LoginIdempotencyRecord> markFailed(
      LoginIdempotencyKey key, LoginCredentialRequestHash hash, String reasonCode) {
    return port.markFailed(
        new LoginIdempotencyFailCommand(
            LoginIdempotencyAction.CREDENTIALS, key, hash, reasonCode, clock.instant(), ttl));
  }

  public Mono<LoginIdempotencyRecord> markUnknown(
      LoginIdempotencyKey key, LoginCredentialRequestHash hash) {
    return port.markUnknownOutcome(
        new LoginIdempotencyUnknownOutcomeCommand(
            LoginIdempotencyAction.CREDENTIALS, key, hash, clock.instant(), ttl));
  }

  public Duration ttl() {
    return ttl;
  }
}
