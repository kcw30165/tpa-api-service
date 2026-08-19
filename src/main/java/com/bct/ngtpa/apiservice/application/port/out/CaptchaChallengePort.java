package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaConsumptionMutationResult;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaVerificationMutationResult;
import java.time.Instant;
import java.util.Optional;
import reactor.core.publisher.Mono;

public interface CaptchaChallengePort {

  Mono<CaptchaChallenge> create(CaptchaChallenge challenge);

  Mono<Optional<CaptchaChallenge>> load(CaptchaChallengeId captchaChallengeId);

  default Mono<CaptchaVerificationMutationResult> applyVerificationAttempt(
      CaptchaChallenge expected, boolean answerMatched, Instant attemptedAt) {
    if (answerMatched) {
      return markVerified(expected.captchaChallengeId(), attemptedAt)
          .map(
              updated ->
                  CaptchaVerificationMutationResult.of(
                      CaptchaVerificationMutationResult.Outcome.VERIFIED, updated));
    }
    return incrementFailedAttempt(expected.captchaChallengeId(), attemptedAt)
        .flatMap(
            updated ->
                updated.failedAttemptCount() >= updated.maxAttempts()
                    ? markLocked(updated.captchaChallengeId(), attemptedAt)
                        .map(
                            locked ->
                                CaptchaVerificationMutationResult.of(
                                    CaptchaVerificationMutationResult.Outcome.LOCKED, locked))
                    : Mono.just(
                        CaptchaVerificationMutationResult.of(
                            CaptchaVerificationMutationResult.Outcome.INVALID, updated)));
  }

  Mono<CaptchaChallenge> markVerified(CaptchaChallengeId captchaChallengeId, Instant verifiedAt);

  Mono<CaptchaChallenge> incrementFailedAttempt(
      CaptchaChallengeId captchaChallengeId, Instant attemptedAt);

  Mono<CaptchaChallenge> markLocked(CaptchaChallengeId captchaChallengeId, Instant lockedAt);

  default Mono<CaptchaConsumptionMutationResult> applyConsumptionAttempt(
      CaptchaChallenge expected, Instant consumedAt) {
    return markConsumed(expected.captchaChallengeId(), consumedAt)
        .map(
            updated ->
                CaptchaConsumptionMutationResult.of(
                    CaptchaConsumptionMutationResult.Outcome.CONSUMED, updated));
  }

  Mono<CaptchaChallenge> markConsumed(CaptchaChallengeId captchaChallengeId, Instant consumedAt);

  Mono<CaptchaChallenge> markRefreshed(CaptchaChallengeId captchaChallengeId, Instant refreshedAt);

  Mono<Void> delete(CaptchaChallengeId captchaChallengeId);
}
