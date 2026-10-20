package com.bct.ngtpa.apiservice.infrastructure.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.SessionRevision;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Fund Comparison M5.1: featureState is stored inside the encrypted session payload. */
class AesGcmAuthenticatedSessionProtectorFeatureStateTest {

  private static final Instant NOW = Instant.parse("2026-10-20T00:00:00Z");

  private final AesGcmAuthenticatedSessionProtector protector =
      new AesGcmAuthenticatedSessionProtector(
          Base64.getEncoder().encodeToString(new byte[32]),
          new ObjectMapper().findAndRegisterModules());

  private static AuthenticatedSession session(Map<String, String> featureState) {
    return new AuthenticatedSession(
        new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx"),
        new ActorContext("member01", ActorUserRole.ROLE_USER_MBR),
        List.of(),
        AuthenticatedSessionStatus.ACTIVE,
        "csrf_hash",
        NOW,
        NOW,
        NOW.plusSeconds(1800),
        NOW.plusSeconds(28800),
        new SessionRevision("0".repeat(32)),
        null,
        featureState);
  }

  @Test
  void roundTripPreservesFeatureState() {
    var original = session(Map.of("fund-comparison.options", "encoded-entry"));

    var restored = protector.unprotect(protector.protect(original));

    assertThat(restored.featureState())
        .containsExactly(Map.entry("fund-comparison.options", "encoded-entry"));
    assertThat(restored).isEqualTo(original);
  }

  @Test
  void emptyFeatureStateRoundTripsAsEmpty() {
    var restored = protector.unprotect(protector.protect(session(Map.of())));

    assertThat(restored.featureState()).isEmpty();
  }

  @Test
  void featureStateIsNotReadableOutsideTheCiphertext() {
    var envelope = protector.protect(session(Map.of("fund-comparison.options", "secret-value")));

    assertThat(envelope.toString()).doesNotContain("secret-value");
    assertThat(envelope.data().attributes().values())
        .noneMatch(value -> value.contains("secret-value"));
  }
}
