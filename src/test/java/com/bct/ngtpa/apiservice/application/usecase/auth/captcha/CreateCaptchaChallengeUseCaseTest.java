package com.bct.ngtpa.apiservice.application.usecase.auth.captcha;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.CaptchaPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.RegistrationAuthWorkflowPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaAnswerHash;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaImage;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CreateCaptchaChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.GeneratedCaptcha;
import com.bct.ngtpa.apiservice.application.port.in.CreateCaptchaChallengeUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaAnswerHasherPort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaChallengePort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaImageGeneratorPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CreateCaptchaChallengeUseCaseTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-07T12:00:00Z"), ZoneOffset.UTC);

    private final CaptchaPolicy policy = new CaptchaPolicy(8, "ABC123", false, 123, 9);
    private final RecordingAuthWorkflowPolicyPort policyPort = new RecordingAuthWorkflowPolicyPort(policy);
    private final RecordingCaptchaImageGeneratorPort imageGeneratorPort =
            new RecordingCaptchaImageGeneratorPort(new GeneratedCaptcha("AbC12312", new CaptchaImage("base64-image", "image/png")));
    private final RecordingCaptchaAnswerHasherPort hasherPort = new RecordingCaptchaAnswerHasherPort();
    private final RecordingCaptchaChallengePort challengePort = new RecordingCaptchaChallengePort();
    private final FixedCaptchaChallengeIdGenerator idGenerator =
            new FixedCaptchaChallengeIdGenerator(new CaptchaChallengeId("cap_abcdefghijklmnop"));

    private final CreateCaptchaChallengeUseCase useCase = new CreateCaptchaChallengeService(
            policyPort,
            imageGeneratorPort,
            hasherPort,
            challengePort,
            idGenerator,
            FIXED_CLOCK);

    @Test
    void createCaptchaUsesConfiguredPolicyFromAuthWorkflow() {
        StepVerifier.create(useCase.create(new CreateCaptchaChallengeCommand("corr", "en")))
                .assertNext(result -> {
                    assertThat(result.expiresInSeconds()).isEqualTo(123);
                    assertThat(result.captchaChallengeId()).isEqualTo(new CaptchaChallengeId("cap_abcdefghijklmnop"));
                })
                .verifyComplete();

        assertThat(imageGeneratorPort.policy).isEqualTo(policy);
        assertThat(challengePort.createdChallenge.maxAttempts()).isEqualTo(9);
        assertThat(challengePort.createdChallenge.createdAt()).isEqualTo(Instant.parse("2026-07-07T12:00:00Z"));
        assertThat(challengePort.createdChallenge.expiresAt()).isEqualTo(Instant.parse("2026-07-07T12:02:03Z"));
        assertThat(challengePort.createdChallenge.toString())
            .doesNotContain("AbC12312")
            .doesNotContain("0123456789abcdef")
            .doesNotContain("cap_abcdefghijklmnop");
    }

    @Test
    void createCaptchaGeneratesHashesAndPersistsChallengeWithoutPlainAnswer() {
        StepVerifier.create(useCase.create(new CreateCaptchaChallengeCommand("corr", "en")))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(hasherPort.answer).isEqualTo("AbC12312");
        assertThat(hasherPort.caseSensitive).isFalse();
        assertThat(challengePort.createdChallenge.answerHash())
                .isEqualTo(new CaptchaAnswerHash("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"));
        assertThat(challengePort.createdChallenge.status()).isEqualTo(CaptchaStatus.CREATED);
        assertThat(challengePort.createdChallenge.toString())
                .doesNotContain("AbC12312")
                .doesNotContain("base64-image");
    }

    @Test
    void createCaptchaReturnsChallengeIdImageAndExpiryOnly() {
        StepVerifier.create(useCase.create(new CreateCaptchaChallengeCommand("corr", "en")))
                .assertNext(result -> {
                    assertThat(result.captchaChallengeId()).isEqualTo(new CaptchaChallengeId("cap_abcdefghijklmnop"));
                    assertThat(result.image().imageBase64()).isEqualTo("base64-image");
                    assertThat(result.image().mimeType()).isEqualTo("image/png");
                    assertThat(result.expiresInSeconds()).isEqualTo(123);
                })
                .verifyComplete();
    }

    @Test
    void createCaptchaToStringDoesNotExposeImageOrChallengeId() {
        StepVerifier.create(useCase.create(new CreateCaptchaChallengeCommand("corr", "en")))
                .assertNext(result -> assertThat(result.toString())
                        .doesNotContain("cap_abcdefghijklmnop")
                        .doesNotContain("base64-image")
                        .doesNotContain("AbC12312")
                        .doesNotContain("0123456789abcdef"))
                .verifyComplete();
    }

    private record FixedCaptchaChallengeIdGenerator(CaptchaChallengeId captchaChallengeId)
            implements CaptchaChallengeIdGenerator {
        @Override
        public CaptchaChallengeId nextId() {
            return captchaChallengeId;
        }
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

    private static final class RecordingCaptchaImageGeneratorPort implements CaptchaImageGeneratorPort {
        private final GeneratedCaptcha generatedCaptcha;
        private CaptchaPolicy policy;

        private RecordingCaptchaImageGeneratorPort(GeneratedCaptcha generatedCaptcha) {
            this.generatedCaptcha = generatedCaptcha;
        }

        @Override
        public Mono<GeneratedCaptcha> generate(CaptchaPolicy policy) {
            this.policy = policy;
            return Mono.just(generatedCaptcha);
        }
    }

    private static final class RecordingCaptchaAnswerHasherPort implements CaptchaAnswerHasherPort {
        private String answer;
        private boolean caseSensitive;

        @Override
        public CaptchaAnswerHash hash(String answer, boolean caseSensitive) {
            this.answer = answer;
            this.caseSensitive = caseSensitive;
            return new CaptchaAnswerHash("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        }

        @Override
        public boolean matches(String submittedAnswer, CaptchaAnswerHash expectedHash, boolean caseSensitive) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingCaptchaChallengePort implements CaptchaChallengePort {
        private CaptchaChallenge createdChallenge;

        @Override
        public Mono<CaptchaChallenge> create(CaptchaChallenge challenge) {
            this.createdChallenge = challenge;
            return Mono.just(challenge);
        }

        @Override
        public Mono<Optional<CaptchaChallenge>> load(CaptchaChallengeId captchaChallengeId) {
            return Mono.just(Optional.empty());
        }

        @Override
        public Mono<CaptchaChallenge> markVerified(CaptchaChallengeId captchaChallengeId, Instant verifiedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<CaptchaChallenge> incrementFailedAttempt(CaptchaChallengeId captchaChallengeId, Instant attemptedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<CaptchaChallenge> markLocked(CaptchaChallengeId captchaChallengeId, Instant lockedAt) {
            throw new UnsupportedOperationException();
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
    }
}