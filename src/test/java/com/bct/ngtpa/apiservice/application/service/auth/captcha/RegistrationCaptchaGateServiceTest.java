package com.bct.ngtpa.apiservice.application.service.auth.captcha;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaAnswerHash;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaConsumeOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.ConsumeCaptchaChallengeCommand;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaChallengePort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RegistrationCaptchaGateServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-07T00:00:00Z");
    private static final CaptchaChallengeId CHALLENGE_ID = new CaptchaChallengeId("cap_abcdefghijklmnop");
    private static final String ANSWER_HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void verifiedCaptchaIsConsumed() {
        var port = new FakeCaptchaChallengePort(challenge(CaptchaStatus.VERIFIED, NOW.plusSeconds(300)));
        var service = new RegistrationCaptchaGateService(port, Clock.fixed(NOW, ZoneOffset.UTC));

        StepVerifier.create(service.consume(new ConsumeCaptchaChallengeCommand(CHALLENGE_ID, NOW)))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(CaptchaConsumeOutcome.CONSUMED);
                    assertThat(result.captchaChallengeId()).isEqualTo(CHALLENGE_ID);
                })
                .verifyComplete();

        assertThat(port.markConsumedCalls).isEqualTo(1);
    }

    @Test
    void createdCaptchaIsNotVerified() {
        assertOutcome(CaptchaStatus.CREATED, NOW.plusSeconds(300), CaptchaConsumeOutcome.NOT_VERIFIED);
    }

    @Test
    void expiredCaptchaIsExpired() {
        assertOutcome(CaptchaStatus.VERIFIED, NOW.minusSeconds(1), CaptchaConsumeOutcome.EXPIRED);
    }

    @Test
    void lockedCaptchaIsLocked() {
        assertOutcome(CaptchaStatus.LOCKED, NOW.plusSeconds(300), CaptchaConsumeOutcome.LOCKED);
    }

    @Test
    void consumedCaptchaIsAlreadyConsumed() {
        assertOutcome(CaptchaStatus.CONSUMED, NOW.plusSeconds(300), CaptchaConsumeOutcome.ALREADY_CONSUMED);
    }

    @Test
    void refreshedCaptchaIsRefreshed() {
        assertOutcome(CaptchaStatus.REFRESHED, NOW.plusSeconds(300), CaptchaConsumeOutcome.REFRESHED);
    }

    @Test
    void missingCaptchaIsNotFound() {
        var port = new FakeCaptchaChallengePort(null);
        var service = new RegistrationCaptchaGateService(port, Clock.fixed(NOW, ZoneOffset.UTC));

        StepVerifier.create(service.consume(new ConsumeCaptchaChallengeCommand(CHALLENGE_ID, NOW)))
                .assertNext(result -> assertThat(result.outcome()).isEqualTo(CaptchaConsumeOutcome.NOT_FOUND))
                .verifyComplete();

        assertThat(port.markConsumedCalls).isZero();
    }

    @Test
    void consumedCaptchaCannotBeConsumedTwice() {
        var port = new FakeCaptchaChallengePort(challenge(CaptchaStatus.VERIFIED, NOW.plusSeconds(300)));
        var service = new RegistrationCaptchaGateService(port, Clock.fixed(NOW, ZoneOffset.UTC));

        StepVerifier.create(service.consume(new ConsumeCaptchaChallengeCommand(CHALLENGE_ID, NOW)))
                .assertNext(result -> assertThat(result.outcome()).isEqualTo(CaptchaConsumeOutcome.CONSUMED))
                .verifyComplete();

        StepVerifier.create(service.consume(new ConsumeCaptchaChallengeCommand(CHALLENGE_ID, NOW.plusSeconds(5))))
                .assertNext(result -> assertThat(result.outcome()).isEqualTo(CaptchaConsumeOutcome.ALREADY_CONSUMED))
                .verifyComplete();
    }

    private void assertOutcome(CaptchaStatus status, Instant expiresAt, CaptchaConsumeOutcome outcome) {
        var port = new FakeCaptchaChallengePort(challenge(status, expiresAt));
        var service = new RegistrationCaptchaGateService(port, Clock.fixed(NOW, ZoneOffset.UTC));

        StepVerifier.create(service.consume(new ConsumeCaptchaChallengeCommand(CHALLENGE_ID, NOW)))
                .assertNext(result -> assertThat(result.outcome()).isEqualTo(outcome))
                .verifyComplete();

        assertThat(port.markConsumedCalls).isZero();
    }

    private static CaptchaChallenge challenge(CaptchaStatus status, Instant expiresAt) {
        return new CaptchaChallenge(
                CHALLENGE_ID,
                status == CaptchaStatus.CONSUMED || status == CaptchaStatus.REFRESHED ? null
                        : new CaptchaAnswerHash(ANSWER_HASH),
                status,
                0,
                5,
                NOW.minusSeconds(30),
                expiresAt,
                status == CaptchaStatus.VERIFIED ? NOW.minusSeconds(10) : null,
                status == CaptchaStatus.CONSUMED ? NOW.minusSeconds(5) : null,
                status == CaptchaStatus.REFRESHED ? NOW.minusSeconds(5) : null,
                status == CaptchaStatus.LOCKED ? NOW.minusSeconds(5) : null);
    }

    private static final class FakeCaptchaChallengePort implements CaptchaChallengePort {
        private CaptchaChallenge challenge;
        private int markConsumedCalls;

        private FakeCaptchaChallengePort(CaptchaChallenge challenge) {
            this.challenge = challenge;
        }

        @Override
        public Mono<CaptchaChallenge> create(CaptchaChallenge challenge) {
            this.challenge = challenge;
            return Mono.just(challenge);
        }

        @Override
        public Mono<Optional<CaptchaChallenge>> load(CaptchaChallengeId captchaChallengeId) {
            return Mono.just(Optional.ofNullable(challenge));
        }

        @Override
        public Mono<CaptchaChallenge> markVerified(CaptchaChallengeId captchaChallengeId, Instant verifiedAt) {
            return Mono.just(challenge);
        }

        @Override
        public Mono<CaptchaChallenge> incrementFailedAttempt(CaptchaChallengeId captchaChallengeId,
                Instant attemptedAt) {
            return Mono.just(challenge);
        }

        @Override
        public Mono<CaptchaChallenge> markLocked(CaptchaChallengeId captchaChallengeId, Instant lockedAt) {
            return Mono.just(challenge);
        }

        @Override
        public Mono<CaptchaChallenge> markConsumed(CaptchaChallengeId captchaChallengeId, Instant consumedAt) {
            markConsumedCalls++;
            challenge = new CaptchaChallenge(
                    challenge.captchaChallengeId(),
                    null,
                    CaptchaStatus.CONSUMED,
                    challenge.failedAttemptCount(),
                    challenge.maxAttempts(),
                    challenge.createdAt(),
                    challenge.expiresAt(),
                    challenge.verifiedAt(),
                    consumedAt,
                    challenge.refreshedAt(),
                    challenge.lockedAt());
            return Mono.just(challenge);
        }

        @Override
        public Mono<CaptchaChallenge> markRefreshed(CaptchaChallengeId captchaChallengeId, Instant refreshedAt) {
            return Mono.just(challenge);
        }

        @Override
        public Mono<Void> delete(CaptchaChallengeId captchaChallengeId) {
            challenge = null;
            return Mono.empty();
        }
    }
}