package com.bct.ngtpa.apiservice.application.dto.session.authenticated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuthenticatedSessionCasUpdateCommandTest {

  private static final AuthenticatedSessionId ID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  private static final SessionRevision EXPECTED = new SessionRevision("a".repeat(32));
  private static final SessionRevision REPLACEMENT = new SessionRevision("b".repeat(32));

  private static ProtectedAuthenticatedSession protectedSession() {
    var data =
        new ProtectedAuthenticatedSession.Data(
            java.util.Map.of(
                ProtectedAuthenticatedSession.CIPHERTEXT, "c",
                ProtectedAuthenticatedSession.ALGORITHM, "AES/GCM/NoPadding",
                ProtectedAuthenticatedSession.NONCE, "n",
                ProtectedAuthenticatedSession.AUTHENTICATION_TAG, "t",
                ProtectedAuthenticatedSession.KEY_VERSION, "v1"));
    Instant now = Instant.parse("2026-09-23T00:00:00Z");
    return new ProtectedAuthenticatedSession(
        ID.value(),
        AuthenticatedSessionStatus.ACTIVE,
        data,
        now,
        now,
        now.plusSeconds(900),
        now.plusSeconds(28800),
        new SessionRevision("0".repeat(32)));
  }

  @Test
  void constructsWithAllValidFields() {
    var command =
        new AuthenticatedSessionCasUpdateCommand(
            ID, EXPECTED, protectedSession(), REPLACEMENT, "req-1");
    assertThat(command.authenticatedSessionId()).isEqualTo(ID);
    assertThat(command.expectedRevision()).isEqualTo(EXPECTED);
    assertThat(command.replacementRevision()).isEqualTo(REPLACEMENT);
  }

  @Test
  void expectedAndReplacementRevisionMustDiffer() {
    assertThatThrownBy(
            () ->
                new AuthenticatedSessionCasUpdateCommand(
                    ID, EXPECTED, protectedSession(), EXPECTED, "req-1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nullSessionIdThrows() {
    assertThatThrownBy(
            () ->
                new AuthenticatedSessionCasUpdateCommand(
                    null, EXPECTED, protectedSession(), REPLACEMENT, "req-1"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void nullExpectedRevisionThrows() {
    assertThatThrownBy(
            () ->
                new AuthenticatedSessionCasUpdateCommand(
                    ID, null, protectedSession(), REPLACEMENT, "req-1"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void nullReplacementProtectedSessionThrows() {
    assertThatThrownBy(
            () -> new AuthenticatedSessionCasUpdateCommand(ID, EXPECTED, null, REPLACEMENT, "req-1"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void blankCorrelationIdIsNormalizedToNull() {
    var command =
        new AuthenticatedSessionCasUpdateCommand(ID, EXPECTED, protectedSession(), REPLACEMENT, "  ");
    assertThat(command.correlationId()).isNull();
  }

  @Test
  void toStringDoesNotLeakSensitiveMaterial() {
    var command =
        new AuthenticatedSessionCasUpdateCommand(
            ID, EXPECTED, protectedSession(), REPLACEMENT, "req-1");
    assertThat(command.toString()).doesNotContain(ID.value());
  }
}
