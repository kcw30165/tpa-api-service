package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChannel;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpScenario;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpStatus;
import com.bct.ngtpa.apiservice.application.service.auth.otp.OtpCodeHasher;
import com.bct.ngtpa.apiservice.application.service.auth.otp.Sha256OtpCodeHasher;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RedisOtpChallengeJsonMapperTest {

  private final OtpCodeHasher hasher = new Sha256OtpCodeHasher();
  private final RedisOtpChallengeJsonMapper mapper = new RedisOtpChallengeJsonMapper();

  @Test
  void savesOtpChallengeWithHashNotRawCode() {
    OtpChallenge challenge = sampleChallenge();

    String json = mapper.toJson(challenge);

    assertThat(json).contains("\"codeHash\"");
    assertThat(json).contains(challenge.codeHash());
    assertThat(json)
        .doesNotContain("123456")
        .doesNotContain("rawCode")
        .doesNotContain("otpCode")
        .doesNotContain("plainCode");
  }

  @Test
  void loadsOtpChallengeRoundTrip() {
    OtpChallenge challenge = sampleChallenge();

    String json = mapper.toJson(challenge);

    assertThat(mapper.fromJson(json)).isEqualTo(challenge);
  }

  @Test
  void malformedRedisJsonFailsSafely() {
    assertThatThrownBy(() -> mapper.fromJson("{bad-json"))
        .isInstanceOf(CacheException.class)
        .hasMessage("OTP challenge state deserialization failed")
        .hasMessageNotContaining("{bad-json");
  }

  private OtpChallenge sampleChallenge() {
    return new OtpChallenge(
        "otp_123",
        OtpScenario.REGISTRATION,
        "workflow-123",
        OtpChannel.SMS,
        "destination-hash",
        hasher.hash("123456"),
        OtpStatus.CREATED,
        0,
        5,
        0,
        3,
        Instant.parse("2026-06-26T10:15:30Z"),
        Instant.parse("2026-06-26T10:20:30Z"),
        Instant.parse("2026-06-26T10:15:30Z"),
        null,
        null);
  }
}
