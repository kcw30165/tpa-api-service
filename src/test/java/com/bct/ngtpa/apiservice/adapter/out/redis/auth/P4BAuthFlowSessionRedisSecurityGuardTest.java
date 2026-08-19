package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class P4BAuthFlowSessionRedisSecurityGuardTest {

  @Test
  void p4bRedisKeyUsesOpaqueAuthFlowSessionIdOnly() {
    var key =
        RedisAuthFlowSessionKeyFactory.keyFor(
            new RedisCacheKeyFactory("ngtpa"), new AuthFlowSessionId("afs_abc123"));

    assertThat(key).isEqualTo("ngtpa:auth:flow-session:afs_abc123");
    assertThat(key)
        .doesNotContain("auth_flow_sid")
        .doesNotContain("XSRF-TOKEN")
        .doesNotContain("csrf_")
        .doesNotContain("member@example.com")
        .doesNotContain("91234567")
        .doesNotContain("username")
        .doesNotContain("password")
        .doesNotContain("policy")
        .doesNotContain("cert");
  }

  @Test
  void p4bRedisJsonStoresHashAndMaskedStateOnly() {
    var session =
        new AuthFlowSession(
            new AuthFlowSessionId("afs_abc123"),
            AuthFlowScenario.REGISTRATION,
            AuthFlowStatus.OTP_SENT,
            "hash_secret",
            "otp_secret",
            new AuthFlowSessionData(
                "EMAIL", "m****r@example.com", java.util.Map.of("safe", "value")),
            Instant.parse("2026-06-29T00:00:00Z"),
            Instant.parse("2026-06-29T00:00:00Z"),
            Instant.parse("2026-06-29T00:30:00Z"));

    var json = new RedisAuthFlowSessionJsonMapper().toJson(session);

    assertThat(json).contains("csrfTokenHash", "activeOtpChallengeId", "maskedDestination");
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
  }
}
