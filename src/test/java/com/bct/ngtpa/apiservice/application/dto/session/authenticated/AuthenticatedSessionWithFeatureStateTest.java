package com.bct.ngtpa.apiservice.application.dto.session.authenticated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Fund Comparison M5.2: AuthenticatedSession.withFeatureState replaces exactly one entry. */
class AuthenticatedSessionWithFeatureStateTest {

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
  void addsAnEntryWhenTheKeyIsAbsent() {
    var updated = session(Map.of()).withFeatureState("feature.a", "v1");

    assertThat(updated.featureState()).containsExactly(Map.entry("feature.a", "v1"));
  }

  @Test
  void replacesOnlyTheGivenKeyAndKeepsOtherEntries() {
    var updated =
        session(Map.of("feature.a", "old", "feature.b", "keep"))
            .withFeatureState("feature.a", "new");

    assertThat(updated.featureState())
        .containsOnly(Map.entry("feature.a", "new"), Map.entry("feature.b", "keep"));
  }

  @Test
  void keepsEveryOtherFieldUnchanged() {
    var original = session(Map.of());

    var updated = original.withFeatureState("feature.a", "v1");

    assertThat(updated)
        .usingRecursiveComparison()
        .ignoringFields("featureState")
        .isEqualTo(original);
  }

  @Test
  void rejectsBlankKeyAndNullValue() {
    var session = session(Map.of());

    assertThatThrownBy(() -> session.withFeatureState(null, "v"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> session.withFeatureState(" ", "v"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> session.withFeatureState("feature.a", null))
        .isInstanceOf(NullPointerException.class);
  }
}
