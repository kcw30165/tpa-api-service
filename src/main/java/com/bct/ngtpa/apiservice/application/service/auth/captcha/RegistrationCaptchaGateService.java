package com.bct.ngtpa.apiservice.application.service.auth.captcha;

import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaConsumeOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.ConsumeCaptchaChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.ConsumeCaptchaChallengeResult;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaChallengePort;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import reactor.core.publisher.Mono;

public class RegistrationCaptchaGateService {

  private final CaptchaChallengePort captchaChallengePort;
  private final Clock clock;

  public RegistrationCaptchaGateService(CaptchaChallengePort captchaChallengePort, Clock clock) {
    this.captchaChallengePort =
        Objects.requireNonNull(captchaChallengePort, "captchaChallengePort must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  public Mono<ConsumeCaptchaChallengeResult> consume(ConsumeCaptchaChallengeCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    Instant now = clock.instant();
    return captchaChallengePort
        .load(command.captchaChallengeId())
        .flatMap(
            optional -> {
              if (optional.isEmpty()) {
                return Mono.just(result(command, CaptchaConsumeOutcome.NOT_FOUND));
              }
              CaptchaChallenge challenge = optional.get();
              if (!challenge.expiresAt().isAfter(now)
                  || challenge.status() == CaptchaStatus.EXPIRED) {
                return Mono.just(result(command, CaptchaConsumeOutcome.EXPIRED));
              }
              return switch (challenge.status()) {
                case CREATED -> Mono.just(result(command, CaptchaConsumeOutcome.NOT_VERIFIED));
                case VERIFIED -> consumeVerified(command, challenge);
                case CONSUMED -> Mono.just(result(command, CaptchaConsumeOutcome.ALREADY_CONSUMED));
                case REFRESHED -> Mono.just(result(command, CaptchaConsumeOutcome.REFRESHED));
                case LOCKED -> Mono.just(result(command, CaptchaConsumeOutcome.LOCKED));
                case EXPIRED -> Mono.just(result(command, CaptchaConsumeOutcome.EXPIRED));
              };
            });
  }

  private Mono<ConsumeCaptchaChallengeResult> consumeVerified(
      ConsumeCaptchaChallengeCommand command, CaptchaChallenge expected) {
    return captchaChallengePort
        .applyConsumptionAttempt(expected, command.consumedAt())
        .flatMap(
            mutation ->
                switch (mutation.outcome()) {
                  case CONSUMED -> Mono.just(result(command, CaptchaConsumeOutcome.CONSUMED));
                  case NOT_FOUND -> Mono.just(result(command, CaptchaConsumeOutcome.NOT_FOUND));
                  case STATE_CHANGED -> reloadAfterConflict(command);
                });
  }

  private Mono<ConsumeCaptchaChallengeResult> reloadAfterConflict(
      ConsumeCaptchaChallengeCommand command) {
    return captchaChallengePort
        .load(command.captchaChallengeId())
        .map(
            current ->
                current
                    .map(challenge -> result(command, outcomeForCurrent(challenge)))
                    .orElseGet(() -> result(command, CaptchaConsumeOutcome.NOT_FOUND)));
  }

  private CaptchaConsumeOutcome outcomeForCurrent(CaptchaChallenge challenge) {
    Instant now = clock.instant();
    if (!challenge.expiresAt().isAfter(now) || challenge.status() == CaptchaStatus.EXPIRED) {
      return CaptchaConsumeOutcome.EXPIRED;
    }
    return switch (challenge.status()) {
      case CREATED, VERIFIED -> CaptchaConsumeOutcome.NOT_VERIFIED;
      case CONSUMED -> CaptchaConsumeOutcome.ALREADY_CONSUMED;
      case REFRESHED -> CaptchaConsumeOutcome.REFRESHED;
      case LOCKED -> CaptchaConsumeOutcome.LOCKED;
      case EXPIRED -> CaptchaConsumeOutcome.EXPIRED;
    };
  }

  private ConsumeCaptchaChallengeResult result(
      ConsumeCaptchaChallengeCommand command, CaptchaConsumeOutcome outcome) {
    return new ConsumeCaptchaChallengeResult(outcome, command.captchaChallengeId());
  }
}
