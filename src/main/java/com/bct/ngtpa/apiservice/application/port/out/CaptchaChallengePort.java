package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import java.time.Instant;
import java.util.Optional;
import reactor.core.publisher.Mono;

public interface CaptchaChallengePort {

    Mono<CaptchaChallenge> create(CaptchaChallenge challenge);

    Mono<Optional<CaptchaChallenge>> load(CaptchaChallengeId captchaChallengeId);

    Mono<CaptchaChallenge> markVerified(CaptchaChallengeId captchaChallengeId, Instant verifiedAt);

    Mono<CaptchaChallenge> incrementFailedAttempt(CaptchaChallengeId captchaChallengeId, Instant attemptedAt);

    Mono<CaptchaChallenge> markLocked(CaptchaChallengeId captchaChallengeId, Instant lockedAt);

    Mono<CaptchaChallenge> markConsumed(CaptchaChallengeId captchaChallengeId, Instant consumedAt);

    Mono<CaptchaChallenge> markRefreshed(CaptchaChallengeId captchaChallengeId, Instant refreshedAt);

    Mono<Void> delete(CaptchaChallengeId captchaChallengeId);
}