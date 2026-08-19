package com.bct.ngtpa.apiservice.application.service.auth.otp;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChannel;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpScenario;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpStatus;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class Sha256OtpCodeHasherTest {

  private final OtpCodeHasher hasher = new Sha256OtpCodeHasher();

  @Test
  void hashDoesNotEqualRawOtp() {
    String rawOtp = "123456";

    String hash = hasher.hash(rawOtp);

    assertThat(hash).isNotBlank();
    assertThat(hash).isNotEqualTo(rawOtp);
  }

  @Test
  void hashIsStableForSameRawOtp() {
    assertThat(hasher.hash("123456")).isEqualTo(hasher.hash("123456"));
  }

  @Test
  void matchesReturnsTrueForCorrectRawOtp() {
    String storedHash = hasher.hash("123456");

    assertThat(hasher.matches("123456", storedHash)).isTrue();
  }

  @Test
  void matchesReturnsFalseForIncorrectRawOtp() {
    String storedHash = hasher.hash("123456");

    assertThat(hasher.matches("000000", storedHash)).isFalse();
  }

  @Test
  void matchesHandlesBlankInputsSafely() {
    String storedHash = hasher.hash("123456");

    assertThat(hasher.matches("", storedHash)).isFalse();
    assertThat(hasher.matches("   ", storedHash)).isFalse();
    assertThat(hasher.matches(null, storedHash)).isFalse();
    assertThat(hasher.matches("123456", "")).isFalse();
    assertThat(hasher.matches("123456", "   ")).isFalse();
    assertThat(hasher.matches("123456", null)).isFalse();
  }

  @Test
  void otpChallengeStoresHashNotRawCode() {
    String rawOtp = "123456";
    OtpChallenge challenge =
        new OtpChallenge(
            "otp-001",
            OtpScenario.REGISTRATION,
            "workflow-001",
            OtpChannel.SMS,
            "destination-hash",
            hasher.hash(rawOtp),
            OtpStatus.CREATED,
            0,
            5,
            0,
            5,
            Instant.parse("2026-06-26T10:15:30Z"),
            Instant.parse("2026-06-26T10:20:30Z"),
            Instant.parse("2026-06-26T10:15:30Z"),
            null,
            null);

    assertThat(challenge.codeHash()).isNotEqualTo(rawOtp);

    List<String> componentNames =
        Arrays.stream(OtpChallenge.class.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();

    assertThat(componentNames).doesNotContain("rawCode", "code", "otpCode", "plainCode");
    assertThat(
            Arrays.stream(OtpChallenge.class.getMethods())
                .map(method -> method.getName())
                .filter(name -> List.of("rawCode", "code", "otpCode", "plainCode").contains(name))
                .toList())
        .isEmpty();
  }
}
