package com.bct.ngtpa.apiservice.application.usecase.auth.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.CaptchaPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.RegistrationAuthWorkflowPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaAnswerHash;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaVerificationOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.VerifyCaptchaChallengeCommand;
import com.bct.ngtpa.apiservice.application.port.in.VerifyCaptchaUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaAnswerHasherPort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaChallengePort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class VerifyCaptchaUseCaseTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-07T12:00:00Z"), ZoneOffset.UTC);
    private static final CaptchaChallengeId ID = new CaptchaChallengeId("cap_abcdefghijklmnop");
    private static final CaptchaAnswerHash HASH = new CaptchaAnswerHash(
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

    private final RecordingAuthWorkflowPolicyPort caseInsensitivePolicy = new RecordingAuthWorkflowPolicyPort(
            new CaptchaPolicy(8, "ABC123", false, 123, 5));
    private final RecordingCaptchaAnswerHasherPort hasherPort = new RecordingCaptchaAnswerHasherPort();
    private final RecordingCaptchaChallengePort challengePort = new RecordingCaptchaChallengePort();
    private final VerifyCaptchaUseCase useCase = new VerifyCaptchaService(caseInsensitivePolicy, hasherPort,
            challengePort, FIXED_CLOCK);

    @Test
    void verifyCaptchaReturnsVerifiedForCorrectAnswer() {
        challengePort.loadedChallenge = Optional
                .of(createdChallenge(CaptchaStatus.CREATED, 0, Instant.parse("2026-07-07T12:05:00Z")));
        hasherPort.matchesResult = true;

        StepVerifier.create(useCase.verify(new VerifyCaptchaChallengeCommand(ID, "aBc123", "corr")))
                .assertNext(result -> {
                    assertThat(result.verified()).isTrue();
                    assertThat(result.outcome()).isEqualTo(CaptchaVerificationOutcome.VERIFIED);
                    assertThat(result.captchaChallengeId()).isEqualTo(ID);
                    assertThat(result.remainingAttempts()).isEqualTo(5);
                    assertThat(result.toString()).doesNotContain("aBc123", HASH.value(), ID.value());
                })
                .verifyComplete();

        assertThat(challengePort.markVerifiedCalled).isTrue();
        assertThat(challengePort.incrementFailedAttemptCalled).isFalse();
    }
    @Test
    void verifyCaptchaRejectsAlreadyVerifiedChallengeForOriginalCorrectAnswerWithoutMutation() {
        CaptchaChallenge original = verifiedChallenge();
        challengePort.loadedChallenge = Optional.of(original);

        StepVerifier.create(useCase.verify(new VerifyCaptchaChallengeCommand(ID, "aBc123", "corr")))
                .assertNext(result -> {
                    assertThat(result.verified()).isFalse();
                    assertThat(result.outcome()).isNotEqualTo(CaptchaVerificationOutcome.VERIFIED);
                    assertThat(result.captchaChallengeId()).isNull();
                })
                .verifyComplete();

        assertVerifiedRejectionPreservesState(original);
    }

    @Test
    void verifyCaptchaRejectsAlreadyVerifiedChallengeForIncorrectAnswerWithSameOutcome() {
        CaptchaChallenge original = verifiedChallenge();
        challengePort.loadedChallenge = Optional.of(original);

        StepVerifier.create(useCase.verify(new VerifyCaptchaChallengeCommand(ID, "wrong", "corr")))
                .assertNext(result -> {
                    assertThat(result.verified()).isFalse();
                    assertThat(result.outcome()).isNotEqualTo(CaptchaVerificationOutcome.VERIFIED);
                    assertThat(result.captchaChallengeId()).isNull();
                })
                .verifyComplete();

        assertVerifiedRejectionPreservesState(original);
    }

    private void assertVerifiedRejectionPreservesState(CaptchaChallenge original) {
        assertThat(challengePort.loadedChallenge).contains(original);
        assertThat(challengePort.loadedChallenge.orElseThrow().status()).isEqualTo(CaptchaStatus.VERIFIED);
        assertThat(challengePort.loadedChallenge.orElseThrow().failedAttemptCount()).isEqualTo(1);
        assertThat(challengePort.loadedChallenge.orElseThrow().expiresAt())
                .isEqualTo(Instant.parse("2026-07-07T12:05:00Z"));
        assertThat(hasherPort.matchesCalled).isFalse();
        assertThat(challengePort.markVerifiedCalled).isFalse();
        assertThat(challengePort.incrementFailedAttemptCalled).isFalse();
        assertThat(challengePort.markLockedCalled).isFalse();
    }

    private CaptchaChallenge verifiedChallenge() {
        return new CaptchaChallenge(
                ID,
                HASH,
                CaptchaStatus.VERIFIED,
                1,
                5,
                Instant.parse("2026-07-07T11:59:00Z"),
                Instant.parse("2026-07-07T12:05:00Z"),
                Instant.parse("2026-07-07T12:00:10Z"),
                null,
                null,
                null);
    }


    @Test
    void verifyCaptchaInvalidAnswerIncrementsFailedAttempt() {
        challengePort.loadedChallenge = Optional
                .of(createdChallenge(CaptchaStatus.CREATED, 0, Instant.parse("2026-07-07T12:05:00Z")));
        challengePort.incrementedChallenge = createdChallenge(CaptchaStatus.CREATED, 1,
                Instant.parse("2026-07-07T12:05:00Z"));
        hasherPort.matchesResult = false;

        StepVerifier.create(useCase.verify(new VerifyCaptchaChallengeCommand(ID, "wrong", "corr")))
                .assertNext(result -> {
                    assertThat(result.verified()).isFalse();
                    assertThat(result.outcome()).isEqualTo(CaptchaVerificationOutcome.INVALID);
                    assertThat(result.remainingAttempts()).isEqualTo(4);
                })
                .verifyComplete();
    }

    @Test
    void verifyCaptchaInvalidAnswerLocksAtMaxAttempts() {
        challengePort.loadedChallenge = Optional
                .of(createdChallenge(CaptchaStatus.CREATED, 4, Instant.parse("2026-07-07T12:05:00Z")));
        challengePort.incrementedChallenge = createdChallenge(CaptchaStatus.CREATED, 5,
                Instant.parse("2026-07-07T12:05:00Z"));
        challengePort.lockedChallenge = new CaptchaChallenge(
                ID,
                HASH,
                CaptchaStatus.LOCKED,
                5,
                5,
                Instant.parse("2026-07-07T11:59:00Z"),
                Instant.parse("2026-07-07T12:05:00Z"),
                null,
                null,
                null,
                Instant.parse("2026-07-07T12:00:00Z"));
        hasherPort.matchesResult = false;

        StepVerifier.create(useCase.verify(new VerifyCaptchaChallengeCommand(ID, "wrong", "corr")))
                .assertNext(result -> {
                    assertThat(result.verified()).isFalse();
                    assertThat(result.outcome()).isEqualTo(CaptchaVerificationOutcome.LOCKED);
                    assertThat(result.remainingAttempts()).isZero();
                })
                .verifyComplete();

        assertThat(challengePort.markLockedCalled).isTrue();
    }

    @Test
    void verifyCaptchaExpiredChallengeReturnsExpiredWithoutMatchingAnswer() {
        challengePort.loadedChallenge = Optional
                .of(createdChallenge(CaptchaStatus.CREATED, 0, Instant.parse("2026-07-07T11:59:59Z")));

        StepVerifier.create(useCase.verify(new VerifyCaptchaChallengeCommand(ID, "anything", "corr")))
                .assertNext(result -> {
                    assertThat(result.verified()).isFalse();
                    assertThat(result.outcome()).isEqualTo(CaptchaVerificationOutcome.EXPIRED);
                })
                .verifyComplete();

        assertThat(hasherPort.matchesCalled).isFalse();
        assertThat(challengePort.incrementFailedAttemptCalled).isFalse();
    }

    @Test
    void verifyCaptchaLockedChallengeReturnsLocked() {
        challengePort.loadedChallenge = Optional.of(new CaptchaChallenge(
                ID,
                HASH,
                CaptchaStatus.LOCKED,
                5,
                5,
                Instant.parse("2026-07-07T11:59:00Z"),
                Instant.parse("2026-07-07T12:05:00Z"),
                null,
                null,
                null,
                Instant.parse("2026-07-07T12:00:00Z")));

        StepVerifier.create(useCase.verify(new VerifyCaptchaChallengeCommand(ID, "anything", "corr")))
                .assertNext(result -> assertThat(result.outcome()).isEqualTo(CaptchaVerificationOutcome.LOCKED))
                .verifyComplete();

        assertThat(hasherPort.matchesCalled).isFalse();
    }

    @Test
    void verifyCaptchaConsumedChallengeReturnsConsumed() {
        challengePort.loadedChallenge = Optional.of(new CaptchaChallenge(
                ID,
                null,
                CaptchaStatus.CONSUMED,
                0,
                5,
                Instant.parse("2026-07-07T11:59:00Z"),
                Instant.parse("2026-07-07T12:05:00Z"),
                Instant.parse("2026-07-07T12:00:10Z"),
                Instant.parse("2026-07-07T12:00:20Z"),
                null,
                null));

        StepVerifier.create(useCase.verify(new VerifyCaptchaChallengeCommand(ID, "anything", "corr")))
                .assertNext(result -> assertThat(result.outcome()).isEqualTo(CaptchaVerificationOutcome.CONSUMED))
                .verifyComplete();
    }

    @Test
    void verifyCaptchaRefreshedChallengeReturnsRefreshed() {
        challengePort.loadedChallenge = Optional.of(new CaptchaChallenge(
                ID,
                null,
                CaptchaStatus.REFRESHED,
                0,
                5,
                Instant.parse("2026-07-07T11:59:00Z"),
                Instant.parse("2026-07-07T12:05:00Z"),
                null,
                null,
                Instant.parse("2026-07-07T12:00:20Z"),
                null));

        StepVerifier.create(useCase.verify(new VerifyCaptchaChallengeCommand(ID, "anything", "corr")))
                .assertNext(result -> assertThat(result.outcome()).isEqualTo(CaptchaVerificationOutcome.REFRESHED))
                .verifyComplete();
    }

    @Test
    void verifyCaptchaNotFoundReturnsNotFound() {
        challengePort.loadedChallenge = Optional.empty();

        StepVerifier.create(useCase.verify(new VerifyCaptchaChallengeCommand(ID, "anything", "corr")))
                .assertNext(result -> {
                    assertThat(result.verified()).isFalse();
                    assertThat(result.outcome()).isEqualTo(CaptchaVerificationOutcome.NOT_FOUND);
                    assertThat(result.remainingAttempts()).isZero();
                })
                .verifyComplete();

        assertThat(hasherPort.matchesCalled).isFalse();
    }

    @Test
    void verifyCaptchaRejectsBlankAnswerWithoutPersistingAttemptIfChosen() {
        assertThatThrownBy(() -> new VerifyCaptchaChallengeCommand(ID, " ", "corr"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("VerifyCaptchaChallengeCommand answer is invalid");
    }

    @Test
    void verifyCaptchaUsesCaseSensitivePolicy() {
        var sensitiveUseCase = new VerifyCaptchaService(
                new RecordingAuthWorkflowPolicyPort(new CaptchaPolicy(8, "ABC123", true, 123, 5)),
                hasherPort,
                challengePort,
                FIXED_CLOCK);
        challengePort.loadedChallenge = Optional
                .of(createdChallenge(CaptchaStatus.CREATED, 0, Instant.parse("2026-07-07T12:05:00Z")));
        hasherPort.matchesResult = true;

        StepVerifier.create(sensitiveUseCase.verify(new VerifyCaptchaChallengeCommand(ID, "AbC123", "corr")))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(hasherPort.caseSensitive).isTrue();

        challengePort.reset();
        challengePort.loadedChallenge = Optional
                .of(createdChallenge(CaptchaStatus.CREATED, 0, Instant.parse("2026-07-07T12:05:00Z")));
        hasherPort.reset();
        hasherPort.matchesResult = true;

        StepVerifier.create(useCase.verify(new VerifyCaptchaChallengeCommand(ID, "AbC123", "corr")))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(hasherPort.caseSensitive).isFalse();
    }

    private static CaptchaChallenge createdChallenge(CaptchaStatus status, int failedAttemptCount, Instant expiresAt) {
        return new CaptchaChallenge(
                ID,
                HASH,
                status,
                failedAttemptCount,
                5,
                Instant.parse("2026-07-07T11:59:00Z"),
                expiresAt,
                null,
                null,
                null,
                null);
    }

    private static final class RecordingAuthWorkflowPolicyPort implements AuthWorkflowPolicyPort {
        private final RegistrationAuthWorkflowPolicy registrationPolicy;

        private RecordingAuthWorkflowPolicyPort(CaptchaPolicy captchaPolicy) {
            this.registrationPolicy = new RegistrationAuthWorkflowPolicy(null, captchaPolicy, null, null, null);
        }

        @Override
        public RegistrationAuthWorkflowPolicy registrationPolicy() {
            return registrationPolicy;
        }

        @Override
        public Optional<String> resolveIdentityTypeSearchScope(String identityType) {
            return Optional.empty();
        }
    }

    private static final class RecordingCaptchaAnswerHasherPort implements CaptchaAnswerHasherPort {
        private boolean matchesCalled;
        private boolean matchesResult;
        private boolean caseSensitive;

        @Override
        public CaptchaAnswerHash hash(String answer, boolean caseSensitive) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean matches(String submittedAnswer, CaptchaAnswerHash expectedHash, boolean caseSensitive) {
            this.matchesCalled = true;
            this.caseSensitive = caseSensitive;
            return matchesResult;
        }

        private void reset() {
            this.matchesCalled = false;
            this.caseSensitive = false;
        }
    }

    private static final class RecordingCaptchaChallengePort implements CaptchaChallengePort {
        private Optional<CaptchaChallenge> loadedChallenge = Optional.empty();
        private CaptchaChallenge incrementedChallenge;
        private CaptchaChallenge lockedChallenge;
        private boolean markVerifiedCalled;
        private boolean incrementFailedAttemptCalled;
        private boolean markLockedCalled;

        @Override
        public Mono<CaptchaChallenge> create(CaptchaChallenge challenge) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<Optional<CaptchaChallenge>> load(CaptchaChallengeId captchaChallengeId) {
            return Mono.just(loadedChallenge);
        }

        @Override
        public Mono<CaptchaChallenge> markVerified(CaptchaChallengeId captchaChallengeId, Instant verifiedAt) {
            this.markVerifiedCalled = true;
            var existing = loadedChallenge.orElseThrow();
            return Mono.just(new CaptchaChallenge(
                    existing.captchaChallengeId(),
                    existing.answerHash(),
                    CaptchaStatus.VERIFIED,
                    existing.failedAttemptCount(),
                    existing.maxAttempts(),
                    existing.createdAt(),
                    existing.expiresAt(),
                    verifiedAt,
                    existing.consumedAt(),
                    existing.refreshedAt(),
                    existing.lockedAt()));
        }

        @Override
        public Mono<CaptchaChallenge> incrementFailedAttempt(CaptchaChallengeId captchaChallengeId,
                Instant attemptedAt) {
            this.incrementFailedAttemptCalled = true;
            return Mono.just(incrementedChallenge);
        }

        @Override
        public Mono<CaptchaChallenge> markLocked(CaptchaChallengeId captchaChallengeId, Instant lockedAt) {
            this.markLockedCalled = true;
            return Mono.just(lockedChallenge);
        }

        @Override
        public Mono<CaptchaChallenge> markConsumed(CaptchaChallengeId captchaChallengeId, Instant consumedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<CaptchaChallenge> markRefreshed(CaptchaChallengeId captchaChallengeId, Instant refreshedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<Void> delete(CaptchaChallengeId captchaChallengeId) {
            return Mono.empty();
        }

        private void reset() {
            loadedChallenge = Optional.empty();
            incrementedChallenge = null;
            lockedChallenge = null;
            markVerifiedCalled = false;
            incrementFailedAttemptCalled = false;
            markLockedCalled = false;
        }
    }
}