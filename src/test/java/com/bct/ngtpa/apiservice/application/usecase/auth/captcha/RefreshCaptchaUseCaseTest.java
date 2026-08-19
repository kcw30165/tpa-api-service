package com.bct.ngtpa.apiservice.application.usecase.auth.captcha;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.CaptchaPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.RegistrationAuthWorkflowPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaAnswerHash;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaImage;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.GeneratedCaptcha;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.RefreshCaptchaChallengeCommand;
import com.bct.ngtpa.apiservice.application.port.in.RefreshCaptchaUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaAnswerHasherPort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaChallengePort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaImageGeneratorPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RefreshCaptchaUseCaseTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-07-07T12:00:00Z"), ZoneOffset.UTC);

  private final CaptchaPolicy policy = new CaptchaPolicy(8, "ABC123", false, 123, 9);
  private final RecordingAuthWorkflowPolicyPort policyPort =
      new RecordingAuthWorkflowPolicyPort(policy);
  private final RecordingCaptchaImageGeneratorPort imageGeneratorPort =
      new RecordingCaptchaImageGeneratorPort(
          new GeneratedCaptcha("AbC12312", new CaptchaImage("base64-image", "image/png")));
  private final RecordingCaptchaAnswerHasherPort hasherPort =
      new RecordingCaptchaAnswerHasherPort();
  private final RecordingCaptchaChallengePort challengePort = new RecordingCaptchaChallengePort();
  private final SequenceCaptchaChallengeIdGenerator idGenerator =
      new SequenceCaptchaChallengeIdGenerator(
          List.of(
              new CaptchaChallengeId("cap_abcdefghijklmnop"),
              new CaptchaChallengeId("cap_bcdefghijklmnopq")));

  private final RefreshCaptchaUseCase useCase =
      new RefreshCaptchaService(
          policyPort, imageGeneratorPort, hasherPort, challengePort, idGenerator, FIXED_CLOCK);

  @Test
  void refreshCaptchaCreatesNewChallengeWhenOldMissing() {
    challengePort.loadedChallenge = Optional.empty();

    StepVerifier.create(
            useCase.refresh(
                new RefreshCaptchaChallengeCommand(
                    new CaptchaChallengeId("cap_oldabcdefghijklmn"), "corr")))
        .assertNext(
            result -> {
              assertThat(result.captchaChallengeId())
                  .isEqualTo(new CaptchaChallengeId("cap_abcdefghijklmnop"));
              assertThat(result.image().imageBase64()).isEqualTo("base64-image");
              assertThat(result.expiresInSeconds()).isEqualTo(123);
            })
        .verifyComplete();

    assertThat(challengePort.markRefreshedCalled).isFalse();
  }

  @Test
  void refreshCaptchaMarksOldChallengeRefreshedWhenPresent() {
    challengePort.loadedChallenge =
        Optional.of(
            new CaptchaChallenge(
                new CaptchaChallengeId("cap_oldabcdefghijklmn"),
                new CaptchaAnswerHash(
                    "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
                CaptchaStatus.CREATED,
                0,
                5,
                Instant.parse("2026-07-07T11:59:00Z"),
                Instant.parse("2026-07-07T12:05:00Z"),
                null,
                null,
                null,
                null));

    StepVerifier.create(
            useCase.refresh(
                new RefreshCaptchaChallengeCommand(
                    new CaptchaChallengeId("cap_oldabcdefghijklmn"), "corr")))
        .assertNext(
            result ->
                assertThat(result.captchaChallengeId())
                    .isEqualTo(new CaptchaChallengeId("cap_abcdefghijklmnop")))
        .verifyComplete();

    assertThat(challengePort.markRefreshedCalled).isTrue();
  }

  @Test
  void refreshCaptchaDoesNotModifyConsumedOldChallenge() {
    challengePort.loadedChallenge =
        Optional.of(
            new CaptchaChallenge(
                new CaptchaChallengeId("cap_oldabcdefghijklmn"),
                null,
                CaptchaStatus.CONSUMED,
                0,
                5,
                Instant.parse("2026-07-07T11:59:00Z"),
                Instant.parse("2026-07-07T12:05:00Z"),
                Instant.parse("2026-07-07T11:59:10Z"),
                Instant.parse("2026-07-07T11:59:20Z"),
                null,
                null));

    StepVerifier.create(
            useCase.refresh(
                new RefreshCaptchaChallengeCommand(
                    new CaptchaChallengeId("cap_oldabcdefghijklmn"), "corr")))
        .expectNextCount(1)
        .verifyComplete();

    assertThat(challengePort.markRefreshedCalled).isFalse();
  }

  @Test
  void refreshCaptchaReturnsNewChallengeIdImageAndExpiryOnly() {
    StepVerifier.create(useCase.refresh(new RefreshCaptchaChallengeCommand(null, "corr")))
        .assertNext(
            result -> {
              assertThat(result.captchaChallengeId())
                  .isEqualTo(new CaptchaChallengeId("cap_abcdefghijklmnop"));
              assertThat(result.image().mimeType()).isEqualTo("image/png");
              assertThat(result.expiresInSeconds()).isEqualTo(123);
              assertThat(result.toString())
                  .doesNotContain("AbC12312", "0123456789abcdef", "cap_abcdefghijklmnop");
            })
        .verifyComplete();
  }

  @Test
  void refreshCaptchaUsesConfiguredPolicyForNewChallenge() {
    StepVerifier.create(useCase.refresh(new RefreshCaptchaChallengeCommand(null, "corr")))
        .expectNextCount(1)
        .verifyComplete();

    assertThat(imageGeneratorPort.policy).isEqualTo(policy);
    assertThat(hasherPort.caseSensitive).isFalse();
    assertThat(challengePort.createdChallenges.get(0).maxAttempts()).isEqualTo(9);
    assertThat(challengePort.createdChallenges.get(0).expiresAt())
        .isEqualTo(Instant.parse("2026-07-07T12:02:03Z"));
  }

  private static final class SequenceCaptchaChallengeIdGenerator
      implements CaptchaChallengeIdGenerator {
    private final List<CaptchaChallengeId> ids;
    private int index;

    private SequenceCaptchaChallengeIdGenerator(List<CaptchaChallengeId> ids) {
      this.ids = ids;
    }

    @Override
    public CaptchaChallengeId nextId() {
      return ids.get(index++);
    }
  }

  private static final class RecordingAuthWorkflowPolicyPort implements AuthWorkflowPolicyPort {
    private final RegistrationAuthWorkflowPolicy registrationPolicy;

    private RecordingAuthWorkflowPolicyPort(CaptchaPolicy captchaPolicy) {
      this.registrationPolicy =
          new RegistrationAuthWorkflowPolicy(null, captchaPolicy, null, null, null);
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

  private static final class RecordingCaptchaImageGeneratorPort
      implements CaptchaImageGeneratorPort {
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
    private boolean caseSensitive;

    @Override
    public CaptchaAnswerHash hash(String answer, boolean caseSensitive) {
      this.caseSensitive = caseSensitive;
      return new CaptchaAnswerHash(
          "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    }

    @Override
    public boolean matches(
        String submittedAnswer, CaptchaAnswerHash expectedHash, boolean caseSensitive) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class RecordingCaptchaChallengePort implements CaptchaChallengePort {
    private Optional<CaptchaChallenge> loadedChallenge = Optional.empty();
    private final List<CaptchaChallenge> createdChallenges = new ArrayList<>();
    private boolean markRefreshedCalled;

    @Override
    public Mono<CaptchaChallenge> create(CaptchaChallenge challenge) {
      createdChallenges.add(challenge);
      return Mono.just(challenge);
    }

    @Override
    public Mono<Optional<CaptchaChallenge>> load(CaptchaChallengeId captchaChallengeId) {
      return Mono.just(loadedChallenge);
    }

    @Override
    public Mono<CaptchaChallenge> markVerified(
        CaptchaChallengeId captchaChallengeId, Instant verifiedAt) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Mono<CaptchaChallenge> incrementFailedAttempt(
        CaptchaChallengeId captchaChallengeId, Instant attemptedAt) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Mono<CaptchaChallenge> markLocked(
        CaptchaChallengeId captchaChallengeId, Instant lockedAt) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Mono<CaptchaChallenge> markConsumed(
        CaptchaChallengeId captchaChallengeId, Instant consumedAt) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Mono<CaptchaChallenge> markRefreshed(
        CaptchaChallengeId captchaChallengeId, Instant refreshedAt) {
      markRefreshedCalled = true;
      return Mono.just(loadedChallenge.orElseThrow());
    }

    @Override
    public Mono<Void> delete(CaptchaChallengeId captchaChallengeId) {
      return Mono.empty();
    }
  }
}
