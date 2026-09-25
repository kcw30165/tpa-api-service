package com.bct.ngtpa.apiservice.application.dto.session.authenticated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Fail-first: {@code SessionRevision} does not exist yet at the AuthenticatedSession layer.
 * Deliberately a NEW type, not a reuse of {@code authflow.SessionRevision}, per the generic-port
 * decision recorded in fund-price-session-contract.md -- the two layers must not share a class that
 * would couple AuthFlowSession's package to AuthenticatedSession's.
 *
 * <p>Format mirrors the proven {@code authflow.SessionRevision}: 32-character lowercase hex,
 * confirmed in script 043's investigation.
 */
class SessionRevisionTest {

  @Test
  void acceptsValidThirtyTwoCharacterLowercaseHex() {
    var revision = new SessionRevision("0123456789abcdef0123456789abcdef");
    assertThat(revision.value()).hasSize(33 - 1);
  }

  @Test
  void nullValueThrows() {
    assertThatThrownBy(() -> new SessionRevision(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void blankValueThrows() {
    assertThatThrownBy(() -> new SessionRevision("   "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void wrongLengthThrows() {
    assertThatThrownBy(() -> new SessionRevision("abc"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void uppercaseHexThrows() {
    assertThatThrownBy(() -> new SessionRevision("0123456789ABCDEF0123456789ABCDEF"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nonHexCharacterThrows() {
    assertThatThrownBy(() -> new SessionRevision("g123456789abcdef0123456789abcdef"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void toStringRedactsValue() {
    var revision = new SessionRevision("0123456789abcdef0123456789abcdef");
    assertThat(revision.toString()).doesNotContain("0123456789abcdef0123456789abcdef");
  }

  @Test
  void distinctRevisionsAreNotEqual() {
    var a = new SessionRevision("0123456789abcdef0123456789abcdef");
    var b = new SessionRevision("00000000000000000000000000000000".substring(0, 32));
    assertThat(a).isNotEqualTo(b);
  }
}
