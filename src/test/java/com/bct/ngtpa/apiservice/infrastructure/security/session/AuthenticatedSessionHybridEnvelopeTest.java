package com.bct.ngtpa.apiservice.infrastructure.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.SessionRevision;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthenticatedSessionHybridEnvelopeTest {
  private final AesGcmAuthenticatedSessionProtector protector =
      new AesGcmAuthenticatedSessionProtector(
          Base64.getEncoder().encodeToString(new byte[32]), new ObjectMapper());

  @Test
  void lifecycleMetadataIsReadableAndSensitiveContextIsEncrypted() {
    ProtectedAuthenticatedSession envelope = protector.protect(session());

    assertThat(envelope.authenticatedSessionId()).startsWith("sid_");
    assertThat(envelope.status()).isEqualTo(AuthenticatedSessionStatus.ACTIVE);
    assertThat(envelope.createdAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    assertThat(envelope.updatedAt()).isEqualTo(Instant.parse("2026-01-01T00:01:00Z"));
    assertThat(envelope.idleExpiresAt()).isEqualTo(Instant.parse("2026-01-01T00:16:00Z"));
    assertThat(envelope.absoluteExpiresAt()).isEqualTo(Instant.parse("2026-01-01T08:00:00Z"));
    assertThat(envelope.data().attributes())
        .containsOnlyKeys(
            ProtectedAuthenticatedSession.CIPHERTEXT_ATTRIBUTE,
            ProtectedAuthenticatedSession.ALGORITHM_ATTRIBUTE,
            ProtectedAuthenticatedSession.NONCE_ATTRIBUTE,
            ProtectedAuthenticatedSession.AUTHENTICATION_TAG,
            ProtectedAuthenticatedSession.KEY_VERSION);
    assertThat(envelope.data().attributes().toString())
        .doesNotContain("member01", "00000008802", "csrf_hash");

    AuthenticatedSession restored = protector.unprotect(envelope);
    assertThat(restored).isEqualTo(session());
  }

  @Test
  void readableMetadataIsAuthenticatedAsAad() {
    ProtectedAuthenticatedSession envelope = protector.protect(session());
    ProtectedAuthenticatedSession tampered =
        new ProtectedAuthenticatedSession(
            envelope.authenticatedSessionId(),
            AuthenticatedSessionStatus.LOGGED_OUT,
            envelope.data(),
            envelope.createdAt(),
            envelope.updatedAt(),
            envelope.idleExpiresAt(),
            envelope.absoluteExpiresAt(),
            new SessionRevision("0".repeat(32)));

    assertThatThrownBy(() -> protector.unprotect(tampered))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unable to unprotect authenticated session");
  }

  private AuthenticatedSession session() {
    return new AuthenticatedSession(
        new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx"),
        new ActorContext("member01", ActorUserRole.ROLE_USER_MBR),
        List.of(
            new AuthenticatedSessionAccount(
                new AuthenticatedAccountRef("acc_abcdefghijklmnopqrstuvwx"),
                "DB",
                "00000008802",
                "0001",
                "trust",
                "scheme",
                null,
                null)),
        AuthenticatedSessionStatus.ACTIVE,
        "csrf_hash",
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-01T00:01:00Z"),
        Instant.parse("2026-01-01T00:16:00Z"),
        Instant.parse("2026-01-01T08:00:00Z"),
        new SessionRevision("0".repeat(32)),
        null);
  }
}
