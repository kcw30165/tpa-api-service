package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp;

import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.*;
import com.bct.ngtpa.apiservice.application.port.in.SendLoginOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.out.LoginOtpSendIdempotencyPort;
import java.time.*;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class IdempotentLoginOtpSendService implements SendLoginOtpUseCase {
  private final SendLoginOtpUseCase delegate;
  private final LoginOtpSendIdempotencyPort idempotency;
  private final LoginOtpSendRequestHashGenerator hashes;
  private final Clock clock;
  private final Duration ttl;

  public IdempotentLoginOtpSendService(
      SendLoginOtpUseCase delegate,
      LoginOtpSendIdempotencyPort idempotency,
      LoginOtpSendRequestHashGenerator hashes,
      Clock clock,
      Duration ttl) {
    this.delegate = Objects.requireNonNull(delegate);
    this.idempotency = Objects.requireNonNull(idempotency);
    this.hashes = Objects.requireNonNull(hashes);
    this.clock = Objects.requireNonNull(clock);
    this.ttl = Objects.requireNonNull(ttl);
  }

  public Mono<SendLoginOtpResult> send(SendLoginOtpCommand c) {
    var hash = hashes.generate(c);
    return idempotency
        .reserve(
            new LoginOtpSendReserveCommand(
                LoginIdempotencyAction.OTP_SEND, c.idempotencyKey(), hash, clock.instant(), ttl))
        .flatMap(
            r ->
                switch (r.outcome()) {
                  case STARTED -> executeStarted(c, hash);
                  case COMPLETED_REPLAY -> Mono.just(replay(r.replay()));
                  case DUPLICATE_IN_PROGRESS -> simple(SendLoginOtpStatus.DUPLICATE_IN_PROGRESS);
                  case KEY_REUSED -> simple(SendLoginOtpStatus.KEY_REUSED);
                  case FAILED_EXISTING -> simple(SendLoginOtpStatus.FAILED_EXISTING);
                  case UNKNOWN_OUTCOME -> simple(SendLoginOtpStatus.UNKNOWN_OUTCOME);
                });
  }

  private Mono<SendLoginOtpResult> executeStarted(
      SendLoginOtpCommand c, LoginOtpSendRequestHash hash) {
    return delegate
        .send(c)
        .flatMap(
            r -> {
              if (r.status() == SendLoginOtpStatus.SENT) {
                var snap =
                    new LoginOtpSendReplaySnapshot(
                        r.maskedDestination(), r.expiresInSeconds(), r.resendCooldownSeconds());
                return idempotency
                    .complete(
                        new LoginOtpSendCompleteCommand(
                            LoginIdempotencyAction.OTP_SEND,
                            c.idempotencyKey(),
                            hash,
                            snap,
                            clock.instant(),
                            ttl))
                    .thenReturn(r);
              }
              if (r.status() == SendLoginOtpStatus.UNKNOWN_OUTCOME) {
                return idempotency
                    .unknown(
                        new LoginOtpSendTerminalCommand(
                            LoginIdempotencyAction.OTP_SEND,
                            c.idempotencyKey(),
                            hash,
                            "INDETERMINATE",
                            clock.instant(),
                            ttl))
                    .thenReturn(r);
              }
              return idempotency
                  .fail(
                      new LoginOtpSendTerminalCommand(
                          LoginIdempotencyAction.OTP_SEND,
                          c.idempotencyKey(),
                          hash,
                          r.status().name(),
                          clock.instant(),
                          ttl))
                  .thenReturn(r);
            })
        .onErrorResume(
            e ->
                idempotency
                    .unknown(
                        new LoginOtpSendTerminalCommand(
                            LoginIdempotencyAction.OTP_SEND,
                            c.idempotencyKey(),
                            hash,
                            "INDETERMINATE",
                            clock.instant(),
                            ttl))
                    .thenReturn(
                        new SendLoginOtpResult(
                            SendLoginOtpStatus.UNKNOWN_OUTCOME, null, null, null, null)));
  }

  private SendLoginOtpResult replay(LoginOtpSendReplaySnapshot s) {
    return new SendLoginOtpResult(
        SendLoginOtpStatus.COMPLETED_REPLAY,
        s.maskedDestination(),
        s.expiresInSeconds(),
        s.resendCooldownSeconds(),
        null);
  }

  private Mono<SendLoginOtpResult> simple(SendLoginOtpStatus s) {
    return Mono.just(new SendLoginOtpResult(s, null, null, null, null));
  }
}
