package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartCommand;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyFingerprint;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Task 019 semantic fingerprint boundary: a deterministic, unlogged SHA-256 hex digest
 * over the normalized START request semantics (identity type/value, DOB, exactly-one contact,
 * captcha challenge id, and the START action namespace). Raw identity, DOB, contact, and captcha
 * values never appear in any string output and are never retained.
 *
 * <p>Design references: {@code
 * docs/design/6-username-recovery/username-recovery-session-redis-design.md} section 1 (recovery
 * idempotency hash; the captcha answer is never included) and section 8 (safe persistence); {@code
 * docs/design/6-username-recovery/username-recovery-api-contract-design.md} section 8
 * (UR-START-014/015 fingerprint-driven in-progress and key-reused classification).
 */
class UsernameRecoveryStartFingerprintGeneratorTest {

  private static final String CAPTCHA = "cap_f67f65980e944119b8b81c74d9a1834a";
  private static final String IDEM_KEY = "idem_12345678";
  private static final Locale LANGUAGE = Locale.ENGLISH;

  private final UsernameRecoveryStartFingerprintGenerator generator =
      new UsernameRecoveryStartFingerprintGenerator();

  @Test
  void derivesDeterministicSha256HexFingerprint() {
    UsernameRecoveryStartCommand command =
        emailCommand("STAFFNO", "3030609", "04/07/1978", "HULLCS.QGLYBCY@UC.KJI", null);

    UsernameRecoveryIdempotencyFingerprint first = generator.derive(command);
    UsernameRecoveryIdempotencyFingerprint second = generator.derive(command);

    assertThat(first).isEqualTo(second);
    assertThat(first.value()).matches("^[a-f0-9]{64}$");
  }

  @Test
  void changesWhenIdentityOrDobOrContactChanges() {
    var base =
        generator.derive(
            emailCommand("STAFFNO", "3030609", "04/07/1978", "HULLCS.QGLYBCY@UC.KJI", null));
    var otherIdentity =
        generator.derive(
            emailCommand("STAFFNO", "3011825", "04/07/1978", "HULLCS.QGLYBCY@UC.KJI", null));
    var otherDob =
        generator.derive(
            emailCommand("STAFFNO", "3030609", "15/07/1969", "HULLCS.QGLYBCY@UC.KJI", null));
    var otherContact =
        generator.derive(emailCommand("STAFFNO", "3030609", "04/07/1978", null, "66290597"));

    assertThat(otherIdentity).isNotEqualTo(base);
    assertThat(otherDob).isNotEqualTo(base);
    assertThat(otherContact).isNotEqualTo(base);
  }

  @Test
  void includesCaptchaChallengeIdButNeverTheAnswer() {
    var withChallenge =
        generator.derive(
            emailCommand("STAFFNO", "3030609", "04/07/1978", "HULLCS.QGLYBCY@UC.KJI", null));
    var otherChallenge =
        generator.derive(
            new UsernameRecoveryStartCommand(
                MemberIdentityType.STAFFNO,
                "3030609",
                "04/07/1978",
                "HULLCS.QGLYBCY@UC.KJI",
                null,
                "cap_00000000000000000000000000000000",
                LANGUAGE,
                IDEM_KEY));

    assertThat(otherChallenge).isNotEqualTo(withChallenge);
  }

  @Test
  void normalizesContactBeforeHashing() {
    var upperEmail =
        generator.derive(
            emailCommand("STAFFNO", "3030609", "04/07/1978", "HULLCS.QGLYBCY@UC.KJI", null));
    var lowerEmail =
        generator.derive(
            emailCommand("STAFFNO", "3030609", "04/07/1978", "hullcs.qglybcy@uc.kji", null));

    assertThat(lowerEmail).isEqualTo(upperEmail);
  }

  @Test
  void changesWhenNormalizedLanguageChanges() {
    UsernameRecoveryStartCommand english =
        emailCommand("STAFFNO", "3030609", "04/07/1978", "HULLCS.QGLYBCY@UC.KJI", null);
    UsernameRecoveryStartCommand chinese =
        new UsernameRecoveryStartCommand(
            english.identityType(),
            english.identityValue(),
            english.dateOfBirth(),
            english.email(),
            english.mobile(),
            english.captchaChallengeId(),
            Locale.TRADITIONAL_CHINESE,
            english.idempotencyKey());

    assertThat(generator.derive(chinese)).isNotEqualTo(generator.derive(english));
  }

  @Test
  void fingerprintToStringNeverDisclosesTheDigest() {
    UsernameRecoveryIdempotencyFingerprint fingerprint =
        generator.derive(
            emailCommand("STAFFNO", "3030609", "04/07/1978", "HULLCS.QGLYBCY@UC.KJI", null));

    assertThat(fingerprint.toString()).doesNotContain(fingerprint.value());
  }

  @Test
  void rejectsNullCommand() {
    assertThatThrownBy(() -> generator.derive(null)).isInstanceOf(NullPointerException.class);
  }

  private static UsernameRecoveryStartCommand emailCommand(
      String identityType, String identityValue, String dateOfBirth, String email, String mobile) {
    return new UsernameRecoveryStartCommand(
        MemberIdentityType.valueOf(identityType),
        identityValue,
        dateOfBirth,
        email,
        mobile,
        CAPTCHA,
        LANGUAGE,
        IDEM_KEY);
  }
}
