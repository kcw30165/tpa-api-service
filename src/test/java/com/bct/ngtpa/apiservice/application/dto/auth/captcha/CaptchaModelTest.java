package com.bct.ngtpa.apiservice.application.dto.auth.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CaptchaModelTest {

  private static final String HASH =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

  @Test
  void captchaStatusDefinesConfirmedLifecycleValues() {
    assertThat(CaptchaStatus.values())
        .containsExactly(
            CaptchaStatus.CREATED,
            CaptchaStatus.VERIFIED,
            CaptchaStatus.CONSUMED,
            CaptchaStatus.REFRESHED,
            CaptchaStatus.EXPIRED,
            CaptchaStatus.LOCKED);
    assertThat(Arrays.stream(CaptchaStatus.values()).map(Enum::name))
        .doesNotContain("FAILED", "ERROR", "UNKNOWN");
  }

  @Test
  void captchaChallengeIdAcceptsOpaqueValues() {
    assertThat(new CaptchaChallengeId("cap_abcdefghijklmnop").value())
        .isEqualTo("cap_abcdefghijklmnop");
    assertThat(new CaptchaChallengeId("cap_ABCdef1234567890_-").value())
        .isEqualTo("cap_ABCdef1234567890_-");
  }

  @Test
  void captchaChallengeIdRejectsUnsafeValuesWithoutEchoing() {
    for (String value :
        Arrays.asList(
            "",
            "   ",
            "cap:abc",
            "cap/abc",
            "member@example.com",
            "91234567",
            "csrf_abc",
            "otp_abc",
            "auth_flow_sid=afs_secret",
            "Bearer abc",
            "Cookie abc")) {
      assertThatThrownBy(() -> new CaptchaChallengeId(value))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("CaptchaChallengeId is invalid")
          .satisfies(
              ex -> {
                if (!value.isBlank()) {
                  assertThat(ex.getMessage()).doesNotContain(value);
                }
              });
    }
  }

  @Test
  void captchaAnswerHashRequiresSha256Hex() {
    assertThat(new CaptchaAnswerHash(HASH).value()).isEqualTo(HASH);

    for (String value :
        Arrays.asList("", "abc", "XYZ", "member@example.com", "91234567", "csrf_abc")) {
      assertThatThrownBy(() -> new CaptchaAnswerHash(value))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("CaptchaAnswerHash is invalid")
          .satisfies(
              ex -> {
                if (!value.isBlank()) {
                  assertThat(ex.getMessage()).doesNotContain(value);
                }
              });
    }
  }

  @Test
  void captchaChallengeRequiresHashNotPlainAnswer() {
    assertThat(recordComponentNames(CaptchaChallenge.class))
        .contains("captchaChallengeId", "answerHash")
        .doesNotContain("answer", "plainAnswer", "expectedAnswer", "captchaText", "captchaCode");
  }

  @Test
  void captchaChallengeRejectsInvalidExpiry() {
    Instant createdAt = Instant.parse("2026-07-07T00:00:00Z");
    assertThatThrownBy(
            () ->
                new CaptchaChallenge(
                    new CaptchaChallengeId("cap_abcdefghijklmnop"),
                    new CaptchaAnswerHash(HASH),
                    CaptchaStatus.CREATED,
                    0,
                    5,
                    createdAt,
                    createdAt,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("CaptchaChallenge expiry is invalid");
  }

  @Test
  void captchaModelsToStringRedactSensitiveValues() {
    Instant createdAt = Instant.parse("2026-07-07T00:00:00Z");
    Instant expiresAt = createdAt.plusSeconds(300);

    var challengeId = new CaptchaChallengeId("cap_secret_abcdefghijklmnop");
    var answerHash = new CaptchaAnswerHash(HASH);
    var challenge =
        new CaptchaChallenge(
            challengeId,
            answerHash,
            CaptchaStatus.CREATED,
            0,
            5,
            createdAt,
            expiresAt,
            null,
            null,
            null,
            null);
    var image = new CaptchaImage("iVBORw0KGgoAAAANSUhEUgAAAAUA", "image/png");
    var createResult = new CreateCaptchaChallengeResult(challengeId, image, 300);
    var verifyCommand = new VerifyCaptchaChallengeCommand(challengeId, "AbC123x", "req-123");
    var generated = new GeneratedCaptcha("AbC123x", image);

    assertThat(challenge.toString()).doesNotContain("cap_secret", HASH);
    assertThat(challengeId.toString()).doesNotContain("cap_secret");
    assertThat(answerHash.toString()).doesNotContain(HASH);
    assertThat(image.toString()).doesNotContain("iVBORw0KGgoAAAANSUhEUgAAAAUA");
    assertThat(createResult.toString())
        .doesNotContain("cap_secret", "iVBORw0KGgoAAAANSUhEUgAAAAUA");
    assertThat(verifyCommand.toString()).doesNotContain("AbC123x", "cap_secret");
    assertThat(generated.toString()).doesNotContain("AbC123x", "iVBORw0KGgoAAAANSUhEUgAAAAUA");
  }

  @Test
  void createCaptchaChallengeResultDoesNotExposeAnswerOrHash() {
    assertThat(recordComponentNames(CreateCaptchaChallengeResult.class))
        .containsExactly("captchaChallengeId", "image", "expiresInSeconds")
        .doesNotContain("answer", "answerHash");
  }

  @Test
  void verifyCaptchaChallengeCommandToStringDoesNotExposeAnswer() {
    assertThat(
            new VerifyCaptchaChallengeCommand(
                    new CaptchaChallengeId("cap_abcdefghijklmnop"), "AbC123x", "req-123")
                .toString())
        .doesNotContain("AbC123x");
  }

  @Test
  void verifyCaptchaChallengeResultSupportsConfirmedResponseFields() {
    var id = new CaptchaChallengeId("cap_abcdefghijklmnop");
    var verified =
        new VerifyCaptchaChallengeResult(CaptchaVerificationOutcome.VERIFIED, true, id, 5);
    var invalid =
        new VerifyCaptchaChallengeResult(CaptchaVerificationOutcome.INVALID, false, id, 4);

    assertThat(verified.verified()).isTrue();
    assertThat(verified.captchaChallengeId()).isEqualTo(id);
    assertThat(invalid.verified()).isFalse();
    assertThat(invalid.remainingAttempts()).isEqualTo(4);
  }

  @Test
  void refreshCaptchaChallengeResultDoesNotExposeAnswerOrHash() {
    assertThat(recordComponentNames(RefreshCaptchaChallengeResult.class))
        .containsExactly("captchaChallengeId", "image", "expiresInSeconds")
        .doesNotContain("answer", "answerHash");
  }

  @Test
  void consumeCaptchaChallengeOutcomesSupportRegistrationStartGate() {
    assertThat(CaptchaConsumeOutcome.values())
        .containsExactly(
            CaptchaConsumeOutcome.CONSUMED,
            CaptchaConsumeOutcome.NOT_FOUND,
            CaptchaConsumeOutcome.NOT_VERIFIED,
            CaptchaConsumeOutcome.EXPIRED,
            CaptchaConsumeOutcome.LOCKED,
            CaptchaConsumeOutcome.ALREADY_CONSUMED,
            CaptchaConsumeOutcome.REFRESHED);
  }

  private static java.util.List<String> recordComponentNames(Class<?> type) {
    return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
  }
}
