package com.bct.ngtpa.apiservice.application.dto.session.authenticated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Fund Comparison M5.1: the generic featureState slot on AuthenticatedSession. */
class AuthenticatedSessionFeatureStateTest {

  private static final Instant NOW = Instant.parse("2026-10-20T00:00:00Z");

  private static AuthenticatedSession session(Map<String, String> featureState) {
    return new AuthenticatedSession(
        new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx"),
        new ActorContext("member-1", ActorUserRole.ROLE_USER_MBR),
        List.of(),
        AuthenticatedSessionStatus.ACTIVE,
        "hash",
        NOW.minusSeconds(60),
        NOW.minusSeconds(60),
        NOW.plusSeconds(900),
        NOW.plusSeconds(28800),
        new SessionRevision("a".repeat(32)),
        null,
        featureState);
  }

  @Test
  void nullFeatureStateBecomesAnEmptyMap() {
    assertThat(session(null).featureState()).isEmpty();
  }

  @Test
  void featureStateIsCopiedAndImmutable() {
    Map<String, String> source = new HashMap<>();
    source.put("fund-comparison.options", "v1");
    var session = session(source);
    source.put("other", "v2");

    assertThat(session.featureState()).containsExactly(Map.entry("fund-comparison.options", "v1"));
    var featureState = session.featureState();
    assertThatThrownBy(() -> featureState.put("x", "y"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void preFeatureStateConstructorStartsWithAnEmptyMap() {
    var session =
        new AuthenticatedSession(
            new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx"),
            new ActorContext("member-1", ActorUserRole.ROLE_USER_MBR),
            List.of(),
            AuthenticatedSessionStatus.ACTIVE,
            "hash",
            NOW.minusSeconds(60),
            NOW.minusSeconds(60),
            NOW.plusSeconds(900),
            NOW.plusSeconds(28800),
            new SessionRevision("a".repeat(32)),
            null);

    assertThat(session.featureState()).isEmpty();
  }

  @Test
  void toStringDoesNotExposeFeatureStateValues() {
    var session = session(Map.of("fund-comparison.options", "secret-value"));

    assertThat(session.toString()).doesNotContain("secret-value");
  }
}
