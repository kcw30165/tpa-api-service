package com.bct.ngtpa.apiservice.application.dto.session.authenticated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Fail-first: {@code ProtectedAuthenticatedSession} does not yet carry a top-level plaintext {@code
 * sessionRevision} field. This is the exact field the Lua CAS script ({@code
 * redis/authenticated-session/update-if-current.lua}) reads via {@code decoded['sessionRevision']}
 * (confirmed in script 049's dump) -- it must be a top-level sibling of {@code status}/{@code
 * createdAt}, not nested inside {@code data.attributes}.
 */
class ProtectedAuthenticatedSessionRevisionFieldTest {

  private static final Instant NOW = Instant.parse("2026-07-20T00:00:00Z");
  private static final SessionRevision REVISION = new SessionRevision("b".repeat(32));

  private ProtectedAuthenticatedSession.Data data() {
    return new ProtectedAuthenticatedSession.Data(
        Map.of(
            ProtectedAuthenticatedSession.CIPHERTEXT, "c",
            ProtectedAuthenticatedSession.ALGORITHM, "AES/GCM/NoPadding",
            ProtectedAuthenticatedSession.NONCE, "n",
            ProtectedAuthenticatedSession.AUTHENTICATION_TAG, "t",
            ProtectedAuthenticatedSession.KEY_VERSION, "v1"));
  }

  private ProtectedAuthenticatedSession envelope(SessionRevision revision) {
    return new ProtectedAuthenticatedSession(
        "sid_abcdefghijklmnopqrstuvwx",
        AuthenticatedSessionStatus.ACTIVE,
        data(),
        NOW,
        NOW,
        NOW.plusSeconds(900),
        NOW.plusSeconds(28800),
        revision);
  }

  @Test
  void carriesTheSuppliedRevision() {
    assertThat(envelope(REVISION).sessionRevision()).isEqualTo(REVISION);
  }

  @Test
  void nullRevisionThrows() {
    assertThatThrownBy(() -> envelope(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void revisionIsNotStoredInsideTheEncryptedAttributesMap() {
    // The revision must be a top-level sibling field, not nested in data.attributes -- the Lua
    // script decodes the top level of the stored JSON directly, per confirmed script bodies.
    assertThat(envelope(REVISION).data().attributes()).doesNotContainKey("sessionRevision");
  }
}
