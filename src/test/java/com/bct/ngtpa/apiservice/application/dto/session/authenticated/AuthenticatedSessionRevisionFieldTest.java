package com.bct.ngtpa.apiservice.application.dto.session.authenticated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Fail-first: {@code AuthenticatedSession} does not yet carry a {@code SessionRevision} field.
 *
 * <p>Deliberately isolated in a NEW test class rather than editing the 18 existing files that
 * construct {@code AuthenticatedSession}/{@code ProtectedAuthenticatedSession} (script 050
 * confirmed 29 construction sites across those 18 files) -- those are updated in a later,
 * purely-mechanical stage. This stage only proves the model-level change itself.
 *
 * <p>Per script 049's finding: {@code SessionRevision} is lifecycle/concurrency metadata, not
 * sensitive data -- it must NOT be added to {@code AesGcmAuthenticatedSessionProtector}'s
 * {@code SensitiveContext}. It rides alongside {@code status}/{@code createdAt} on the plaintext
 * side, exactly like every other lifecycle field.
 */
class AuthenticatedSessionRevisionFieldTest {

  private static final Instant NOW = Instant.parse("2026-07-20T00:00:00Z");
  private static final SessionRevision REVISION = new SessionRevision("a".repeat(32));

  private AuthenticatedSession session(SessionRevision revision) {
    return new AuthenticatedSession(
        new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx"),
        new ActorContext("member", ActorUserRole.ROLE_USER_MBR),
        List.of(),
        AuthenticatedSessionStatus.ACTIVE,
        "csrf_hash",
        NOW,
        NOW,
        NOW.plusSeconds(1800),
        NOW.plusSeconds(28800),
        revision,
        null);
  }

  @Test
  void carriesTheSuppliedRevision() {
    assertThat(session(REVISION).revision()).isEqualTo(REVISION);
  }

  @Test
  void nullRevisionThrows() {
    assertThatThrownBy(() -> session(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void toStringDoesNotLeakTheRevisionValue() {
    assertThat(session(REVISION).toString()).doesNotContain(REVISION.value());
  }
}
