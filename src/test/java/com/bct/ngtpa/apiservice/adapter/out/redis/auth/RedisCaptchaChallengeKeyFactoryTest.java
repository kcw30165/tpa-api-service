package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import org.junit.jupiter.api.Test;

class RedisCaptchaChallengeKeyFactoryTest {

    @Test
    void buildsCaptchaChallengeKey() {
        var key = RedisCaptchaChallengeKeyFactory.keyFor(
                new RedisCacheKeyFactory("ngtpa"),
                new CaptchaChallengeId("cap_abcdefghijklmnop"));

        assertThat(key).isEqualTo("ngtpa:auth:captcha:cap_abcdefghijklmnop");
        assertThat(key)
                .doesNotContain("answer")
                .doesNotContain("answerHash")
                .doesNotContain("imageBase64")
                .doesNotContain("auth_flow_sid")
                .doesNotContain("XSRF-TOKEN")
                .doesNotContain("csrf_")
                .doesNotContain("otp_")
                .doesNotContain("member@example.com")
                .doesNotContain("91234567")
                .doesNotContain("password")
                .doesNotContain("policy")
                .doesNotContain("cert");
    }

    @Test
    void keyFactoryRejectsUnsafeCaptchaChallengeIdWithoutEchoing() {
        assertThatThrownBy(() -> new CaptchaChallengeId("cap_unsafe value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CaptchaChallengeId is invalid")
                .hasMessageNotContaining("cap_unsafe value");
    }
}