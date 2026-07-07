package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaAnswerHash;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaStatus;
import com.bct.ngtpa.apiservice.exception.CacheException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

class RedisCaptchaChallengeJsonMapperTest {

    private final RedisCaptchaChallengeJsonMapper jsonMapper = new RedisCaptchaChallengeJsonMapper();

    @Test
    void jsonMapperRoundTripsCreatedChallenge() {
        var challenge = createdChallenge();

        String json = jsonMapper.toJson(challenge);
        CaptchaChallenge restored = jsonMapper.fromJson(json);

        assertThat(restored).isEqualTo(challenge);
    }

    @Test
    void jsonMapperRoundTripsVerifiedConsumedLockedRefreshedChallenge() {
        assertThat(jsonMapper.fromJson(jsonMapper.toJson(new CaptchaChallenge(
                        new CaptchaChallengeId("cap_abcdefghijklmnop"),
                        new CaptchaAnswerHash("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
                        CaptchaStatus.VERIFIED,
                        1,
                        5,
                        Instant.parse("2026-07-07T00:00:00Z"),
                        Instant.parse("2026-07-07T00:05:00Z"),
                        Instant.parse("2026-07-07T00:01:00Z"),
                        null,
                        null,
                        null))))
                .extracting(CaptchaChallenge::status, CaptchaChallenge::verifiedAt)
                .containsExactly(CaptchaStatus.VERIFIED, Instant.parse("2026-07-07T00:01:00Z"));

        assertThat(jsonMapper.fromJson(jsonMapper.toJson(new CaptchaChallenge(
                        new CaptchaChallengeId("cap_bcdefghijklmnopq"),
                        null,
                        CaptchaStatus.CONSUMED,
                        1,
                        5,
                        Instant.parse("2026-07-07T00:00:00Z"),
                        Instant.parse("2026-07-07T00:05:00Z"),
                        Instant.parse("2026-07-07T00:01:00Z"),
                        Instant.parse("2026-07-07T00:02:00Z"),
                        null,
                        null))))
                .extracting(CaptchaChallenge::status, CaptchaChallenge::consumedAt)
                .containsExactly(CaptchaStatus.CONSUMED, Instant.parse("2026-07-07T00:02:00Z"));

        assertThat(jsonMapper.fromJson(jsonMapper.toJson(new CaptchaChallenge(
                        new CaptchaChallengeId("cap_cdefghijklmnopqr"),
                        new CaptchaAnswerHash("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"),
                        CaptchaStatus.LOCKED,
                        5,
                        5,
                        Instant.parse("2026-07-07T00:00:00Z"),
                        Instant.parse("2026-07-07T00:05:00Z"),
                        null,
                        null,
                        null,
                        Instant.parse("2026-07-07T00:03:00Z")))))
                .extracting(CaptchaChallenge::status, CaptchaChallenge::lockedAt)
                .containsExactly(CaptchaStatus.LOCKED, Instant.parse("2026-07-07T00:03:00Z"));

        assertThat(jsonMapper.fromJson(jsonMapper.toJson(new CaptchaChallenge(
                        new CaptchaChallengeId("cap_defghijklmnopqrs"),
                        null,
                        CaptchaStatus.REFRESHED,
                        0,
                        5,
                        Instant.parse("2026-07-07T00:00:00Z"),
                        Instant.parse("2026-07-07T00:05:00Z"),
                        null,
                        null,
                        Instant.parse("2026-07-07T00:04:00Z"),
                        null))))
                .extracting(CaptchaChallenge::status, CaptchaChallenge::refreshedAt)
                .containsExactly(CaptchaStatus.REFRESHED, Instant.parse("2026-07-07T00:04:00Z"));
    }

    @Test
    void storedJsonDoesNotContainForbiddenRawFieldNames() {
        String json = jsonMapper.toJson(createdChallenge());

        assertThat(json)
                .contains("answerHash", "captchaChallengeId", "failedAttemptCount", "maxAttempts")
                .doesNotContain("\"answer\"")
                .doesNotContain("plainAnswer")
                .doesNotContain("expectedAnswer")
                .doesNotContain("captchaText")
                .doesNotContain("captchaCode")
                .doesNotContain("imageBase64")
                .doesNotContain("imageBytes")
                .doesNotContain("pngBytes")
                .doesNotContain("email")
                .doesNotContain("mobile")
                .doesNotContain("identityValue")
                .doesNotContain("dateOfBirth")
                .doesNotContain("passport")
                .doesNotContain("hkid")
                .doesNotContain("auth_flow_sid")
                .doesNotContain("csrfToken")
                .doesNotContain("otpCode")
                .doesNotContain("cookie")
                .doesNotContain("authorization");
    }

    @Test
    void malformedRedisJsonFailsSafely() {
        assertThatThrownBy(() -> jsonMapper.fromJson("{bad-json"))
                .isInstanceOf(CacheException.class)
                .hasMessage("Captcha challenge state deserialization failed")
                .hasMessageNotContaining("{bad-json");
    }

    private static CaptchaChallenge createdChallenge() {
        return new CaptchaChallenge(
                new CaptchaChallengeId("cap_abcdefghijklmnop"),
                new CaptchaAnswerHash("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
                CaptchaStatus.CREATED,
                0,
                5,
                Instant.parse("2026-07-07T00:00:00Z"),
                Instant.parse("2026-07-07T00:05:00Z"),
                null,
                null,
                null,
                null);
    }
}