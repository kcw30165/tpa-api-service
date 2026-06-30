package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SelectedTrust;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.UsernameCheckState;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RedisAuthFlowSessionJsonMapperTest {

    private final RedisAuthFlowSessionJsonMapper mapper = new RedisAuthFlowSessionJsonMapper();

    @Test
    void loadsExistingAuthFlowSessionRoundTrip() {
        var session = sampleSession();

        var json = mapper.toJson(session);
        var roundTrip = mapper.fromJson(json);

        assertThat(roundTrip.authFlowSessionId().value()).isEqualTo("afs_abc123");
        assertThat(roundTrip.scenario()).isEqualTo(AuthFlowScenario.REGISTRATION);
        assertThat(roundTrip.status()).isEqualTo(AuthFlowStatus.OTP_SENT);
        assertThat(roundTrip.csrfTokenHash()).isEqualTo("csrf_hash_value");
        assertThat(roundTrip.activeOtpChallengeId()).isEqualTo("otp_abc");
        assertThat(roundTrip.data().contactType()).isEqualTo("EMAIL");
        assertThat(roundTrip.data().maskedDestination()).isEqualTo("m****r@example.com");
        assertThat(roundTrip.data().usernameCheck().selectedTrust().trustCode()).isEqualTo("TL");
        assertThat(roundTrip.createdAt()).isEqualTo(Instant.parse("2026-06-29T00:00:00Z"));
        assertThat(roundTrip.updatedAt()).isEqualTo(Instant.parse("2026-06-29T00:01:00Z"));
        assertThat(roundTrip.expiresAt()).isEqualTo(Instant.parse("2026-06-29T00:30:00Z"));
    }

    @Test
    void storedJsonDoesNotContainRawSecretFields() {
        var json = mapper.toJson(sampleSession());

        assertThat(json)
                .doesNotContain("rawCsrfToken")
                .doesNotContain("csrfToken\"")
                .doesNotContain("auth_flow_sid")
                .doesNotContain("XSRF-TOKEN")
                .doesNotContain("otpCode")
                .doesNotContain("rawOtp")
                .doesNotContain("password")
                .doesNotContain("confirmPassword")
                .doesNotContain("email")
                .doesNotContain("mobile")
                .doesNotContain("phone")
                .doesNotContain("hkid")
                .doesNotContain("passport")
                .doesNotContain("policyNo")
                .doesNotContain("certNo");
        assertThat(json).contains("csrfTokenHash", "activeOtpChallengeId", "maskedDestination");
    }

    @Test
    void malformedRedisJsonFailsSafely() {
        assertThatThrownBy(() -> mapper.fromJson("{not-json"))
                .isInstanceOf(CacheException.class)
                .hasMessage("Auth flow session state deserialization failed")
                .hasMessageNotContaining("{not-json");
    }

    @Test
    void authFlowStatusDoesNotUseAmbiguousFailureStates() {
        assertThat(java.util.Arrays.stream(AuthFlowStatus.values()).map(Enum::name))
                .doesNotContain("FAILED", "UNKNOWN", "ERROR");
    }

    private static AuthFlowSession sampleSession() {
        var checkedAt = Instant.parse("2026-06-29T00:05:00Z");
        var data = new AuthFlowSessionData(
                "EMAIL",
                "m****r@example.com",
                new UsernameCheckState(
                        new SelectedTrust("TL", "ENV1", "Scheme A", "scheme-a", 10),
                        "user_secret",
                        checkedAt,
                        checkedAt.plusSeconds(600)),
                Map.of("safe", "value"));
        return new AuthFlowSession(
                new AuthFlowSessionId("afs_abc123"),
                AuthFlowScenario.REGISTRATION,
                AuthFlowStatus.OTP_SENT,
                "csrf_hash_value",
                "otp_abc",
                data,
                Instant.parse("2026-06-29T00:00:00Z"),
                Instant.parse("2026-06-29T00:01:00Z"),
                Instant.parse("2026-06-29T00:30:00Z"));
    }
}
