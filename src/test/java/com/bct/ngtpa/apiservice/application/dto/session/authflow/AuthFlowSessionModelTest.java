package com.bct.ngtpa.apiservice.application.dto.session.authflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthFlowSessionModelTest {
  @Test
  void authFlowScenarioDefinesExpectedValues() {
    assertThat(AuthFlowScenario.values())
        .containsExactly(
            AuthFlowScenario.REGISTRATION,
            AuthFlowScenario.LOGIN,
            AuthFlowScenario.FORGOT_PASSWORD,
            AuthFlowScenario.USERNAME_RECOVERY);
    assertThat(Arrays.stream(AuthFlowScenario.values()).map(Enum::name))
        .doesNotContain("CHANGE_PASSWORD");
  }

  @Test
  void authFlowStatusDefinesExpectedRegistrationLifecycleValues() {
    assertThat(AuthFlowStatus.values())
        .containsExactly(
            AuthFlowStatus.STARTED,
            AuthFlowStatus.OTP_SENT,
            AuthFlowStatus.OTP_VERIFIED,
            AuthFlowStatus.TRUST_SELECTION_READY,
            AuthFlowStatus.USERNAME_VERIFIED,
            AuthFlowStatus.IDENTITY_VERIFIED,
            AuthFlowStatus.RESET_AUTHORIZED,
            AuthFlowStatus.COMPLETING,
            AuthFlowStatus.COMPLETED,
            AuthFlowStatus.CANCELLED,
            AuthFlowStatus.LOCKED,
            AuthFlowStatus.UNKNOWN_OUTCOME_BLOCKED,
            AuthFlowStatus.EXPIRED);
    assertThat(Arrays.stream(AuthFlowStatus.values()).map(Enum::name))
        .doesNotContain("TRUST_SELECTED", "SELECT_REMAINING_TRUST", "FAILED", "UNKNOWN", "ERROR");
  }

  @Test
  void authFlowSessionIdWrapsOpaqueValue() {
    var id = new AuthFlowSessionId("afs_abc123");
    assertThat(id.value()).isEqualTo("afs_abc123");
    assertThat(id.toString()).doesNotContain("afs_abc123");
    assertThatThrownBy(() -> new AuthFlowSessionId(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("AuthFlowSessionId is invalid");
    assertThatThrownBy(() -> new AuthFlowSessionId(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void authFlowSessionCarriesCoreFields() {
    var createdAt = Instant.parse("2026-06-29T00:00:00Z");
    var updatedAt = Instant.parse("2026-06-29T00:01:00Z");
    var expiresAt = Instant.parse("2026-06-29T00:30:00Z");
    var data = new AuthFlowSessionData("EMAIL", "m****r@example.com", Map.of("safe", "value"));
    var session =
        new AuthFlowSession(
            new AuthFlowSessionId("afs_abc123"),
            AuthFlowScenario.REGISTRATION,
            AuthFlowStatus.OTP_SENT,
            "hash_value",
            "otp_abc",
            data,
            createdAt,
            updatedAt,
            expiresAt);
    assertThat(session.authFlowSessionId().value()).isEqualTo("afs_abc123");
    assertThat(session.scenario()).isEqualTo(AuthFlowScenario.REGISTRATION);
    assertThat(session.status()).isEqualTo(AuthFlowStatus.OTP_SENT);
    assertThat(session.csrfTokenHash()).isEqualTo("hash_value");
    assertThat(session.activeOtpChallengeId()).isEqualTo("otp_abc");
    assertThat(session.data()).isSameAs(data);
    assertThat(session.createdAt()).isEqualTo(createdAt);
    assertThat(session.updatedAt()).isEqualTo(updatedAt);
    assertThat(session.expiresAt()).isEqualTo(expiresAt);
  }

  @Test
  void authFlowSessionCanRepresentEmptyData() {
    var now = Instant.parse("2026-06-29T00:00:00Z");
    var session =
        new AuthFlowSession(
            new AuthFlowSessionId("afs_empty"),
            AuthFlowScenario.LOGIN,
            AuthFlowStatus.STARTED,
            null,
            null,
            null,
            now,
            now,
            now.plusSeconds(900));
    assertThat(session.data()).isNotNull();
    assertThat(session.data().attributes()).isEmpty();
  }

  @Test
  void authFlowSessionDataCarriesMaskedDestinationOnly() {
    var data = new AuthFlowSessionData("EMAIL", "m****r@example.com", Map.of());
    assertThat(data.contactType()).isEqualTo("EMAIL");
    assertThat(data.maskedDestination()).isEqualTo("m****r@example.com");
    assertThat(recordComponentNames(AuthFlowSessionData.class))
        .doesNotContain("email", "mobile", "phone", "contactValue", "rawDestination");
  }

  @Test
  void usernameCheckStateCarriesSelectedTrustAndUsername() {
    var checkedAt = Instant.parse("2026-06-29T00:05:00Z");
    var state =
        new UsernameCheckState(
            new SelectedTrust("TL", "ENV1"),
            "abc1@",
            checkedAt,
            checkedAt.plusSeconds(600));
    assertThat(state.selectedTrust().trustCode()).isEqualTo("TL");
    assertThat(state.selectedTrust().accountEnv()).isEqualTo("ENV1");
    assertThat(state.username()).isEqualTo("abc1@");
    assertThat(recordComponentNames(UsernameCheckState.class))
        .doesNotContain("password", "confirmPassword");
    assertThat(state.toString()).doesNotContain("abc1@");
  }

  @Test
  void authFlowSessionDoesNotExposeRawSecretFields() {
    assertThat(recordComponentNames(AuthFlowSession.class))
        .doesNotContain(
            "rawCsrfToken",
            "csrfToken",
            "cookie",
            "authFlowCookie",
            "otpCode",
            "rawOtp",
            "password",
            "confirmPassword",
            "email",
            "mobile",
            "phone",
            "hkid",
            "passport",
            "policyNo",
            "certNo");
  }

  @Test
  void authFlowSessionToStringDoesNotExposeSensitiveValues() {
    var now = Instant.parse("2026-06-29T00:00:00Z");
    var state =
        new UsernameCheckState(
            new SelectedTrust("TL", "ENV1"),
            "user_secret",
            now,
            now.plusSeconds(300));
    var data = new AuthFlowSessionData("EMAIL", "m****r@example.com", Map.of());
    var session =
        new AuthFlowSession(
            new AuthFlowSessionId("afs_secret"),
            AuthFlowScenario.REGISTRATION,
            AuthFlowStatus.USERNAME_VERIFIED,
            "csrf_hash_secret",
            "otp_secret",
            data,
            now,
            now,
            now.plusSeconds(1800));
    assertThat(session.toString())
        .doesNotContain("afs_secret", "csrf_hash_secret", "otp_secret", "user_secret");
  }

  @Test
  void authFlowSessionCanDetectExpired() {
    var now = Instant.parse("2026-06-29T00:00:00Z");
    var session = sessionWithStatus(AuthFlowStatus.OTP_SENT, now.plusSeconds(1800));
    assertThat(session.isExpired(now.plusSeconds(1799))).isFalse();
    assertThat(session.isExpired(now.plusSeconds(1800))).isTrue();
    assertThat(session.isExpired(now.plusSeconds(1801))).isTrue();
  }

  @Test
  void authFlowSessionCanDetectTerminalStatus() {
    var expiresAt = Instant.parse("2026-06-29T00:30:00Z");
    assertThat(sessionWithStatus(AuthFlowStatus.COMPLETED, expiresAt).isTerminal()).isTrue();
    assertThat(sessionWithStatus(AuthFlowStatus.CANCELLED, expiresAt).isTerminal()).isTrue();
    assertThat(sessionWithStatus(AuthFlowStatus.LOCKED, expiresAt).isTerminal()).isTrue();
    assertThat(sessionWithStatus(AuthFlowStatus.UNKNOWN_OUTCOME_BLOCKED, expiresAt).isTerminal())
        .isTrue();
    assertThat(sessionWithStatus(AuthFlowStatus.EXPIRED, expiresAt).isTerminal()).isTrue();
    assertThat(sessionWithStatus(AuthFlowStatus.STARTED, expiresAt).isTerminal()).isFalse();
    assertThat(sessionWithStatus(AuthFlowStatus.OTP_SENT, expiresAt).isTerminal()).isFalse();
    assertThat(sessionWithStatus(AuthFlowStatus.OTP_VERIFIED, expiresAt).isTerminal()).isFalse();
    assertThat(sessionWithStatus(AuthFlowStatus.IDENTITY_VERIFIED, expiresAt).isTerminal())
        .isFalse();
    assertThat(sessionWithStatus(AuthFlowStatus.RESET_AUTHORIZED, expiresAt).isTerminal())
        .isFalse();
    assertThat(sessionWithStatus(AuthFlowStatus.USERNAME_VERIFIED, expiresAt).isTerminal())
        .isFalse();
    assertThat(sessionWithStatus(AuthFlowStatus.COMPLETING, expiresAt).isTerminal()).isFalse();
  }

  @Test
  void authFlowSessionDataDefensivelyCopiesAttributes() {
    var mutable = new java.util.LinkedHashMap<String, String>();
    mutable.put("safe", "value");
    var data = new AuthFlowSessionData(null, null, mutable);
    mutable.put("safe", "changed");
    assertThat(data.attributes()).containsEntry("safe", "value");
    assertThatThrownBy(() -> data.attributes().put("other", "value"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private static AuthFlowSession sessionWithStatus(AuthFlowStatus status, Instant expiresAt) {
    var createdAt = Instant.parse("2026-06-29T00:00:00Z");
    return new AuthFlowSession(
        new AuthFlowSessionId("afs_" + status.name().toLowerCase()),
        AuthFlowScenario.REGISTRATION,
        status,
        null,
        null,
        AuthFlowSessionData.empty(),
        createdAt,
        createdAt,
        expiresAt);
  }

  private static java.util.List<String> recordComponentNames(Class<?> type) {
    return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
  }
}
