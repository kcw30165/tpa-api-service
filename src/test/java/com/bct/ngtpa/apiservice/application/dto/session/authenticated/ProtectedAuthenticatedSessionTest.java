package com.bct.ngtpa.apiservice.application.dto.session.authenticated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionProtectorPort;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProtectedAuthenticatedSessionTest {
  @Test
  void hybridWireValueExposesLifecycleMetadataAndRedactsProtectedAttributes() {
    var value =
        ProtectedAuthenticatedSessionTestFixtures.envelope(
            "v1", "cipher-secret-value", "nonce-secret-value", "tag-secret-value");

    assertThat(value.authenticatedSessionId()).startsWith("sid_");
    assertThat(value.status()).isEqualTo(AuthenticatedSessionStatus.ACTIVE);
    assertThat(value.createdAt()).isNotNull();
    assertThat(value.updatedAt()).isNotNull();
    assertThat(value.idleExpiresAt()).isAfter(value.updatedAt());
    assertThat(value.absoluteExpiresAt()).isAfter(value.idleExpiresAt());
    assertThat(value.keyVersion()).isEqualTo("v1");
    assertThat(value.algorithm()).isEqualTo("AES/GCM/NoPadding");
    assertThat(value.toString())
        .contains("authenticatedSessionId=<redacted>", "status=ACTIVE", "data=<protected>")
        .doesNotContain("cipher-secret-value", "nonce-secret-value", "tag-secret-value");
  }

  @Test
  void blankCryptoAttributesFailClosed() {
    Instant created = Instant.parse("2026-01-01T00:00:00Z");
    assertThatThrownBy(
            () ->
                new ProtectedAuthenticatedSession(
                    "sid_abcdefghijklmnopqrstuvwx",
                    AuthenticatedSessionStatus.ACTIVE,
                    new ProtectedAuthenticatedSession.Data(
                        Map.of(
                            ProtectedAuthenticatedSession.CIPHERTEXT_ATTRIBUTE, " ",
                            ProtectedAuthenticatedSession.ALGORITHM_ATTRIBUTE, "AES/GCM/NoPadding",
                            ProtectedAuthenticatedSession.NONCE_ATTRIBUTE, "nonce",
                            ProtectedAuthenticatedSession.AUTHENTICATION_TAG, "tag",
                            ProtectedAuthenticatedSession.KEY_VERSION, "v1")),
                    created,
                    created,
                    created.plusSeconds(900),
                    created.plusSeconds(3600),
                    new SessionRevision("0".repeat(32))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void invalidLifecycleMetadataFailsClosed() {
    Instant created = Instant.parse("2026-01-01T00:00:00Z");
    assertThatThrownBy(
            () ->
                new ProtectedAuthenticatedSession(
                    "sid_abcdefghijklmnopqrstuvwx",
                    AuthenticatedSessionStatus.ACTIVE,
                    new ProtectedAuthenticatedSession.Data(
                        Map.of(
                            ProtectedAuthenticatedSession.CIPHERTEXT_ATTRIBUTE, "cipher",
                            ProtectedAuthenticatedSession.ALGORITHM_ATTRIBUTE, "AES/GCM/NoPadding",
                            ProtectedAuthenticatedSession.NONCE_ATTRIBUTE, "nonce",
                            ProtectedAuthenticatedSession.AUTHENTICATION_TAG, "tag",
                            ProtectedAuthenticatedSession.KEY_VERSION, "v1")),
                    created,
                    created.minusSeconds(1),
                    created.plusSeconds(900),
                    created.plusSeconds(3600),
                    new SessionRevision("0".repeat(32))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void protectionPortIsInfrastructureNeutral() {
    assertThat(AuthenticatedSessionProtectorPort.class.getDeclaredMethods())
        .extracting(java.lang.reflect.Method::getName)
        .containsExactlyInAnyOrder("protect", "unprotect");
  }
}
